# Dynamic MCP Configuration System — PLAN.md

## Overview

Replace all hardcoded and env-var-based MCP server configuration with a file-driven registry
system driven by `mcp.json`. The config file is the single source of truth for every MCP
server the RCA engine knows about. At startup the application loads and validates `mcp.json`,
builds one `McpClient` per server entry, and exposes a single `McpRegistry.collectData(...)`
call that the diagnostic pipeline invokes instead of the current hand-rolled per-server logic.

**Scope of change:**

| Area | Change |
|---|---|
| `mcp.json` | New canonical config file (replaces McpConfig mapping) |
| `McpConfigLoader` | New startup bean — locates, reads, validates `mcp.json` |
| `McpRegistry` | New component — owns all MCP clients, drives data collection |
| `McpClient` | New per-server client — health check + tool call |
| `DiagnosticContext` | Replaced with generic `List<McpToolResult>` |
| `McpContextCollector` | Deleted — replaced entirely by `McpRegistry` |
| `McpConfig` (Quarkus mapping) | Deleted |
| `application.yml` | Remove all `causa.mcp.*` server entries; add `causa.mcp.config-path` |
| `context_data` table | V2 migration — replace `context_type`+`content` with 4 MCP columns |
| `ContextDataEntity` | Updated to match new schema |
| `DiagnosticServiceImpl` | Replace `mcpContextCollector.collectContext()` call |
| `configmap.yaml` / `.env.example` | Remove old MCP env vars; add `MCP_CONFIG_PATH` |
| `rca-prompt-template.yml` | Preamble update — acknowledge dynamic labeled sections |

**What does NOT change:**

- Webhook flow (`POST /api/webhook` → `DiagnosticService.triggerDiagnostics`)
- Alert model, LLM integration, validation pipeline, RCA prompt builder
- All other config mappings (`AppConfig`, `AlertConfig`, `LLMConfig`, etc.)
- Database tables other than `context_data`

---

## Architectural Decisions

### McpRegistry vs McpFactory

`McpRegistry` is chosen. A *factory* creates objects and returns them; a *registry* owns a
named collection of live objects and provides lookup/dispatch. This component holds all
`McpClient` instances for the lifetime of the application and dispatches calls to them —
registry semantics are the correct fit.

### Sequential vs Parallel data collection

Sequential per-server collection is used. Rationale:

1. The existing pipeline is already sequential; parallel adds thread management complexity.
2. Each MCP server call is already a blocking HTTP call with its own timeout.
3. A single server failure must not cancel sibling calls — sequential makes this trivial.
4. Parallel can be added later as a pure performance optimisation without API change.

### File path resolution

Two-tier strategy consistent with existing resource loading patterns in the project:

1. **External path (prod):** `MCP_CONFIG_PATH` env var → resolved as a filesystem path.
2. **Classpath fallback (dev):** `src/main/resources/mcp.json` — ships inside the JAR.

The `application.yml` property `causa.mcp.config-path: ${MCP_CONFIG_PATH:}` bridges the env
var into the Quarkus config system. An empty value triggers the classpath fallback.

### mcp.json schema

The existing `mcp_settings.json` schema (used by IBM Bob Shell) is adopted as-is with two
additions required by the engine:

- `healthCheckUrl` — explicit URL for health checks (separate from the `url` field, needed
  because Cryostat exposes health on a different port)
- `timeoutMs` — per-server HTTP timeout (replaces the old per-server McpConfig fields)
- `args` on each tool entry — static arguments + `${placeholder}` dynamic substitution

The top-level key remains `mcpServers` to stay compatible with Claude Desktop and Bob Shell
without modification.

### DiagnosticContext replacement

The existing 30-field named `DiagnosticContext` is replaced with a generic structure:

```
DiagnosticContext
  └── List<McpToolResult>
        └── McpToolResult { serverName, toolName, dataType, data }
```

The `toString()` method iterates the list dynamically. The existing platform/pod/namespace
identity fields are retained (they come from the Alert, not from MCP servers).

### Context table schema (V2 migration)

`context_type` (VARCHAR) and `content` (TEXT) columns are dropped. Four new columns replace
them: `mcp_server_name`, `tool_name`, `data_type`, `data_payload` (JSONB). The
`container_name` column is retained for indexed lookups. `embedding` and `context_metadata`
are retained unchanged.

---

## Sub-Tasks

---

### Sub-Task 1 — `mcp.json` schema, sample file, and constants

**Status:** `[ ] pending`

**Intent:**
Define and ship the canonical `mcp.json` schema. This is the foundation every other sub-task
depends on. The schema must be compatible with Bob Shell / Claude Desktop (top-level
`mcpServers` object) while adding the engine-specific fields the registry needs.

**Expected Outcomes:**
- `src/main/resources/mcp.json` exists with all 7 servers from `mcp_settings.json`.
- Each server entry has: `type`, `url`, `healthCheckUrl`, `timeoutMs`, `platforms`,
  `required`, `headers`, `alwaysAllow` (list of objects with `name`, `dataType`, and optional
  `args`).
- The `dataType` field on each tool entry is the human-readable LLM section label. It must
  match the corresponding constant in `ContextConstants` exactly (e.g., `"GC ANALYSIS
  (Cryostat JFR)"`). This is what `DiagnosticContext.toString()` uses as the section header
  and what the RCA prompt's guidance blocks reference by name.
- Tool `args` entries use `${podName}`, `${namespace}`, `${containerName}` placeholders for
  dynamic values and plain string literals for static values.
- `McpConstants` gains a new inner class `Config` with field name string constants
  (`FIELD_MCP_SERVERS`, `FIELD_URL`, `FIELD_HEALTH_CHECK_URL`, `FIELD_DATA_TYPE`, etc.) and a
  `Placeholders` class with `POD_NAME`, `NAMESPACE`, `CONTAINER_NAME` constants.

**Todo List:**
1. Read `deployment/kubernetes/base/mcp_settings.json` — use it as the base for all 7
   server entries.
2. Create `src/main/resources/mcp.json` with the extended schema. For each server add
   `healthCheckUrl`, `timeoutMs`. Expand each `alwaysAllow` entry from a plain string to an
   object `{ "name": "toolName", "dataType": "...", "args": { ... } }`:
   - `kubernetes`: `pods_get` → dataType `"POD STATUS"`, args `name: ${podName}`,
     `namespace: ${namespace}`
   - `kubernetes`: `pods_log` → dataType `"POD LOGS (recent)"`, args `name: ${podName}`,
     `namespace: ${namespace}`, `tailLines: 25`
   - `kubernetes`: `pods_log` previous → dataType `"PREVIOUS CONTAINER LOGS (pre-crash)"`,
     args `name: ${podName}`, `namespace: ${namespace}`, `tailLines: 25`, `previous: true`
   - `kubernetes`: `events_list` → dataType `"POD EVENTS"`, args
     `fieldSelector: ${fieldSelector}`, `namespace: ${namespace}`
   - `kruize`: cost tool → dataType `"RESOURCE COST RECOMMENDATIONS (Kruize)"`, args
     `containerName: ${containerName}`, `namespace: ${namespace}`
   - `kruize`: perf tool → dataType `"RESOURCE PERFORMANCE RECOMMENDATIONS (Kruize)"`, args
     `containerName: ${containerName}`, `namespace: ${namespace}`
   - `cryostat`: `get_gc_analysis` → dataType `"GC ANALYSIS (Cryostat JFR)"`, args
     `pod_name: ${podName}`
   - `cryostat`: `get_memory_analysis` → dataType `"MEMORY ANALYSIS (Cryostat JFR)"`, args
     `pod_name: ${podName}`
   - `cryostat`: `get_thread_analysis` → dataType `"THREAD ANALYSIS (Cryostat JFR)"`, args
     `pod_name: ${podName}`
   - `cryostat`: `get_exception_analysis` → dataType `"EXCEPTION ANALYSIS (Cryostat JFR)"`,
     args `pod_name: ${podName}`
   - `cryostat`: `get_container_analysis` → dataType `"CONTAINER RESOURCE ANALYSIS (Cryostat
     JFR)"`, args `pod_name: ${podName}`
   - `quarkus`: `fetch_raw_metrics_from_endpoint` → dataType `"QUARKUS RAW METRICS (Quarkus
     MCP)"`, args `pod_name: ${podName}`, `namespace: ${namespace}`
   - `async-profiler`: each tool → dataType matching the corresponding
     `SECTION_ASYNC_PROFILER_*` constant in `ContextConstants`
   - `filesystem`: tools → dataType `"LIBERTY APPLICATION LOGS (Filesystem MCP)"`, no args
   - `jmx`: each tool → dataType matching the corresponding `SECTION_VM_*` constant in
     `ContextConstants`
3. Add `Config` and `Placeholders` inner classes to
   `src/main/java/com/causa/common/constants/McpConstants.java`. No other changes to that
   file.

**Relevant Context:**
- `deployment/kubernetes/base/mcp_settings.json` — authoritative server list and tool names
- `src/main/java/com/causa/common/constants/McpConstants.java` — existing constants file,
  must be extended, never duplicated
- `src/main/java/com/causa/mcp/McpContextCollector.java` lines 296–448 — exact argument
  shapes used per tool today; match these when writing the `args` maps in `mcp.json`

---

### Sub-Task 2 — Configuration model classes

**Status:** `[ ] pending`

**Intent:**
Implement the Java classes that represent the parsed `mcp.json` document. These are plain
immutable POJOs used by `McpConfigLoader` (Sub-Task 3) and `McpRegistry` (Sub-Task 4). They
must be Jackson-deserializable from the schema defined in Sub-Task 1.

**Expected Outcomes:**
- `McpFileConfig` record/class — top-level: holds `Map<String, McpServerConfig> mcpServers`.
- `McpServerConfig` record/class — per-server: `type`, `url`, `healthCheckUrl`, `timeoutMs`,
  `platforms` (List<String>), `required` (boolean), `headers` (Map<String,String>),
  `alwaysAllow` (List<McpToolConfig>).
- `McpToolConfig` record/class — per-tool: `name`, `dataType` (String — the LLM section
  label), `args` (Map<String,String>, nullable).
- All three classes live in `src/main/java/com/causa/mcp/` package.
- All three are serializable (Jackson annotations for field name mapping where needed).
- No Lombok. No JPA annotations. Minimal — only what the registry needs.

**Relevant Context:**
- Package `com.causa.mcp` — existing package, add new classes here
- `com.causa.core.domain.*` — reference immutable domain pattern (private finals, builder or
  record, no setters on domain objects). Config model classes can use records for brevity.
- `com.causa.common.utils.JsonUtils` — reuse for JSON parsing

---

### Sub-Task 3 — `McpConfigLoader` startup bean

**Status:** `[ ] pending`

**Intent:**
Implement the startup bean that locates, reads, and validates `mcp.json` at boot time.
Produces a validated `McpFileConfig` instance that is injected into `McpRegistry`. Fails fast
on missing or malformed config.

**Expected Outcomes:**
- `McpConfigLoader` is an `@ApplicationScoped` bean in package `com.causa.mcp`.
- It reads `causa.mcp.config-path` from Quarkus config (bound to `MCP_CONFIG_PATH` env var).
- Resolution logic:
  - If the property is non-blank: open the file at that filesystem path.
  - If blank: load `mcp.json` from the classpath root (fallback for dev).
- On load failure (file not found, malformed JSON, empty `mcpServers` map): throw a
  descriptive `RuntimeException` — Quarkus will halt startup and log it.
- On success: produces a CDI-injectable `McpFileConfig` via an `@Produces @ApplicationScoped`
  method (or equivalent Quarkus producer pattern).
- A startup log line records the resolved path and server count (using `CausaLogger` +
  `LogMessages`).
- `application.yml` gains exactly one new line under `causa.mcp`: `config-path:
  ${MCP_CONFIG_PATH:}`.
- All MCP server-specific properties (`causa.mcp.kubernetes`, `causa.mcp.kruize`, etc.) are
  removed from `application.yml`.

**Relevant Context:**
- `src/main/java/com/causa/config/AppStartup.java` — pattern for startup event observer
- `src/main/java/com/causa/config/ConfigStartup.java` — pattern for CDI producer at startup
- `src/main/resources/application.yml` lines 141–175 — the block to remove
- `src/main/java/com/causa/common/utils/JsonUtils.java` — use for JSON parsing
- `src/main/java/com/causa/common/logging/LogMessages.java` — add new `Mcp` startup messages
  here; never use magic strings

---

### Sub-Task 4 — `McpClient` and `McpRegistry`

**Status:** `[ ] pending`

**Intent:**
Implement the two core components. `McpClient` encapsulates all protocol-level communication
with a single MCP server (session init, tool call, SSE parsing). `McpRegistry` owns one
`McpClient` per configured server and provides the `collectData(DiagnosticContext.Builder,
Alert)` method that drives the full collection loop.

**Expected Outcomes:**

`McpClient` (in `com.causa.mcp`):
- Constructed with an `McpServerConfig` — knows its own URL, timeout, and allowed tools.
- Exposes `isHealthy()` — HTTP GET to `healthCheckUrl`, returns false (not throws) if
  unavailable.
- Exposes `callTool(toolName, arguments)` — initiates MCP session, calls the tool, returns
  the raw text result. Throws on protocol error; callers decide how to handle.
- The MCP protocol implementation (session init, `notifications/initialized`, SSE parsing,
  JSON-RPC assembly) is extracted verbatim from the existing
  `McpContextCollector.initializeMcpSession` and `McpContextCollector.callMcpTool` methods —
  no behaviour change, just relocation.
- The `HttpClient` is constructed once per `McpClient` instance (not shared globally).

`McpRegistry` (in `com.causa.mcp`):
- `@ApplicationScoped` CDI bean.
- Injected with `McpFileConfig` (produced by `McpConfigLoader`).
- Constructs one `McpClient` per entry in `mcpServers` at `@PostConstruct`.
- Exposes `collectData(DiagnosticContext.Builder builder, Alert alert, String platform)`:
  - Filters servers by `platforms` matching the current platform.
  - Skips optional servers whose `isHealthy()` returns false (required servers: proceed
    regardless, log a warning if unhealthy).
  - For each server, for each tool in `alwaysAllow`:
    - Resolves `${placeholder}` values from the `Alert` object using a small private helper.
    - Calls `McpClient.callTool(toolName, resolvedArgs)`.
    - Wraps the result in `McpToolResult { serverName, toolName, dataType, data }`.
    - Appends to the builder via `builder.addResult(mcpToolResult)`.
  - Logs per-tool success and per-tool failures (warn, not error — optional tools can fail).

`McpToolResult` (in `com.causa.core.domain` — referenced from core services):
- Immutable record/class: `serverName` (String), `toolName` (String), `dataType` (String —
  copied from `McpToolConfig.dataType` at collection time; this is the LLM-visible section
  label), `data` (String, nullable on failure).

`Placeholder resolution` helper (private to `McpRegistry`):
- Knows `${podName}` → `alert.getWorkloadInfo().podName()`
- Knows `${namespace}` → `alert.getWorkloadInfo().namespace()`
- Knows `${containerName}` → `alert.getWorkloadInfo().containerName()`
- Knows `${fieldSelector}` → `"involvedObject.name=" + podName` (Kubernetes events)
- Unknown placeholders: passed through as-is (safe — MCP server will reject or ignore).
- Constants for placeholder strings live in `McpConstants.Placeholders`.

**Relevant Context:**
- `McpContextCollector.initializeMcpSession()` lines 453–500 — extract verbatim
- `McpContextCollector.callMcpTool()` lines 528–569 — extract verbatim
- `McpContextCollector.parseSSEResponse()` lines 576–585 — extract verbatim
- `McpContextCollector.extractTextFromContent()` lines 714–741 — extract verbatim
- `McpContextCollector.callCryostatToolWithRetry()` — the Cryostat retry loop is
  currently hardcoded in `McpContextCollector`; extract it into `McpClient` as an optional
  retry-aware path triggered by `McpServerConfig.maxRetries > 0`.
- `McpConstants` — all existing JSON-RPC, header, and path constants must be reused; do not
  duplicate them

---

### Sub-Task 5 — Replace `DiagnosticContext` with generic structure

**Status:** `[ ] pending`

**Intent:**
Replace the 30-field named `DiagnosticContext` with a leaner generic model backed by
`List<McpToolResult>`. Retain the platform/pod/namespace identity fields. Rewrite `toString()`
to iterate the list dynamically so the LLM prompt receives the same structured text as before.

**Expected Outcomes:**
- `DiagnosticContext` retains: `platform`, `workloadName`, `podName`, `containerName`,
  `namespace`.
- All 25+ named data fields (`podStatus`, `gcAnalysis`, `heapStatus`, etc.) are removed.
- A new field `List<McpToolResult> results` replaces all of them.
- `builder.addResult(McpToolResult)` appends to the list; all per-field builder setters are
  removed.
- `hasAnyContext()` returns `!results.isEmpty()`.
- `hasCryostatContext()`, `hasKubernetesContext()`, etc. are removed (they are no longer
  needed externally — the pipeline uses `hasAnyContext()`).
- `toString()` iterates `results` and for each entry uses `result.dataType()` as the section
  header label — identical to the existing `ContextConstants` section header string values.
  This means the LLM receives sections like `--- GC ANALYSIS (Cryostat JFR) ---` exactly as
  before. The section label is now config-driven (comes from `mcp.json` `dataType` field)
  rather than hardcoded in Java.
  Format per entry:
  ```
  --- <dataType> ---
  <data or "No Data Available">
  ```
- `ContextConstants` — the existing named section header constants (`SECTION_POD_STATUS`,
  `SECTION_GC_ANALYSIS`, etc.) are retained but their usage in `DiagnosticContext` is removed.
  They are still the canonical source of truth for the `dataType` strings written into
  `mcp.json`. Add a comment to each constant noting it is the canonical `dataType` value for
  the corresponding tool entry.
- `ContextConstants` — add new section header constant for the generic format; also remove
  constants for deleted named sections. Existing constants referenced elsewhere (e.g.
  `HEADER`, `SEPARATOR_*`, `NOT_AVAILABLE`) are untouched.

**Relevant Context:**
- `src/main/java/com/causa/core/domain/DiagnosticContext.java` — full replacement
- `src/main/java/com/causa/common/constants/ContextConstants.java` — prune unused section
  header constants, add the new generic section format constant
- `src/main/java/com/causa/core/services/impl/DiagnosticServiceImpl.java` lines 179–185 —
  uses `hasKubernetesContext()`, `hasKruizeContext()`, `hasCryostatContext()` in log
  statements; update to `hasAnyContext()` or log result count instead
- `src/main/java/com/causa/core/services/rules/impl/DiagnosticContextSignalExtractor.java` —
  check whether it references named getters; update if so

---

### Sub-Task 5b — Prompt template update for dynamic context sections

**Status:** `[ ] pending`

**Intent:**
The RCA prompt template contains numbered, named guidance blocks for each known data source
(e.g. `### 5. JFR_ANALYSIS (Java Flight Recorder from Cryostat)`). These guidance blocks are
correct and must be kept — the LLM uses them to interpret the data in each labeled section.
However, the numbered preamble list that precedes them (`INPUT SIGNALS (1. POD_STATUS, 2.
PROMETHEUS_METRICS, ...`) is hardcoded to specific tool names and needs a one-line framing
change so it accurately describes the dynamic, label-driven context format the new system
produces.

**Expected Outcomes:**
- `src/main/resources/prompts/rca-prompt-template.yml` user prompt:
  - The `## INPUT SIGNALS (some may be missing)` numbered list at the top of the user prompt
    is replaced with a framing paragraph:
    ```
    ## INPUT SIGNALS

    The context below contains labeled sections. Each section header identifies its source.
    Interpret each section according to its label and the guidance below. Sections not present
    in the context are absent for this incident — note their absence in `llm_notes` and lower
    confidence accordingly.
    ```
  - All existing per-section guidance blocks (`### 1. POD_STATUS`, `### 5. JFR_ANALYSIS`,
    `### 7. QUARKUS_RAW_METRICS`, `### 8. ASYNC_PROFILER_DATA`, etc.) are kept verbatim.
    They continue to serve as named interpretation guides the LLM applies when a matching
    section label appears in the context.
  - The `{{context}}` placeholder, output format, workflow steps, and constraints are
    untouched.
- No other prompt templates are modified.

**Why this is the minimal correct change:**
The LLM already handles missing sections via the `(some may be missing)` instruction. The
only thing that breaks under the new system is the numbered `1. POD_STATUS ... 6. KRUIZE`
list at the very top — it implies a fixed schema, which is no longer true. Replacing it with
a single framing paragraph is the minimal change that makes the prompt accurate without
losing any interpretive guidance.

**Relevant Context:**
- `src/main/resources/prompts/rca-prompt-template.yml` lines 19–33 — the numbered preamble
  list to replace
- `src/main/resources/prompts/rca-prompt-template.yml` lines 35–93 — the per-section
  guidance blocks to keep untouched
- `src/main/java/com/causa/core/services/PromptTemplateLoader.java` — `render()` method does
  a single `{{context}}` substitution; no code change needed

---

### Sub-Task 6 — Wire `McpRegistry` into the diagnostic pipeline

**Status:** `[ ] pending`

**Intent:**
Replace the `McpContextCollector` injection in `DiagnosticServiceImpl` with `McpRegistry`.
Delete `McpContextCollector` and `LibertyLogsContextCollector` (Liberty logs collection is now
driven by the `filesystem` server entry in `mcp.json` via the registry). Delete `McpConfig`
interface. Update `application.yml` to remove the old `causa.mcp.*` block.

**Expected Outcomes:**
- `DiagnosticServiceImpl`:
  - `McpContextCollector mcpContextCollector` field replaced with `McpRegistry mcpRegistry`.
  - `collectContext(Alert)` method body becomes:
    ```java
    DiagnosticContext.Builder builder = DiagnosticContext.builder()
        .platform(platform)
        .podName(alert.getWorkloadInfo().podName())
        ... // identity fields
    mcpRegistry.collectData(builder, alert, platform);
    return builder.build();
    ```
  - Log statement after collection: log `diagnosticContext.getResults().size()` instead of
    the removed `hasK8sContext` / `hasKruizeContext` boolean fields.
- `McpContextCollector.java` — deleted.
- `LibertyLogsContextCollector.java` — deleted (filesystem MCP tools handle this via
  `mcp.json`).
- `McpConfig.java` — deleted.
- `configmap.yaml`: remove all `CAUSA_MCP_*` keys; add `MCP_CONFIG_PATH:
  "/etc/causa/mcp.json"`.
- `deployment.yaml`: add a volumeMount for `mcp.json` ConfigMap entry at
  `/etc/causa/mcp.json`.
- `.env.example` (VM): remove `CAUSA_MCP_*` lines; add `MCP_CONFIG_PATH=/opt/causa/mcp.json`
  with a comment.
- The platform value (`cluster` or `vm`) continues to come from
  `causa.cluster.target-cluster-type` (unchanged).

**Relevant Context:**
- `DiagnosticServiceImpl` lines 67–99 (constructor injection) and lines 282–288
  (`collectContext` method)
- `DiagnosticServiceImpl` lines 179–185 — log fields referencing `hasK8sContext` etc.
- `src/main/java/com/causa/mcp/LibertyLogsContextCollector.java` — full file to delete
- `src/main/java/com/causa/config/McpConfig.java` — full file to delete
- `deployment/kubernetes/base/configmap.yaml` — update
- `deployment/kubernetes/base/deployment.yaml` — add volumeMount and volume
- `deployment/vm/.env.example` — update

---

### Sub-Task 7 — Context table schema migration and entity update

**Status:** `[ ] pending`

**Intent:**
Add a Flyway V2 migration that evolves `context_data` to the MCP-centric schema. Update the
JPA entity and any service/repository code that persists context data.

**Expected Outcomes:**

Migration file `src/main/resources/db/migration/V2__mcp_context_schema.sql`:
- `ALTER TABLE context_data DROP COLUMN IF EXISTS context_type;`
- `ALTER TABLE context_data DROP COLUMN IF EXISTS content;`
- `ALTER TABLE context_data ADD COLUMN mcp_server_name VARCHAR(255) NOT NULL DEFAULT '';`
- `ALTER TABLE context_data ADD COLUMN tool_name VARCHAR(255) NOT NULL DEFAULT '';`
- `ALTER TABLE context_data ADD COLUMN data_type VARCHAR(128) NOT NULL DEFAULT '';`
- `ALTER TABLE context_data ADD COLUMN data_payload JSONB;`
- Index on `(mcp_server_name, tool_name, data_type)` for future search queries.
- Remove the `DEFAULT ''` after adding columns (the default is only to satisfy NOT NULL for
  existing rows; new rows will always supply values).

`ContextDataEntity`:
- Remove `contextType` (String) and `content` (String) fields and their getters/setters.
- Add `mcpServerName` (String), `toolName` (String), `dataType` (String), `dataPayload`
  (JsonNode, mapped as `@JdbcTypeCode(SqlTypes.JSON)` `@Column(columnDefinition = "jsonb")`).

`DiagnosticServiceImpl` / persistence layer:
- Identify any code that currently saves `ContextDataEntity` rows. If such code exists, update
  it to populate the new columns. If no context persistence code exists yet (the current code
  only logs context, it does not persist it), add minimal persistence of
  `McpToolResult` records after `mcpRegistry.collectData()` returns.

**Relevant Context:**
- `src/main/resources/db/migration/V1__initial_schema.sql` lines 98–121 — schema to evolve
- `src/main/java/com/causa/infrastructure/persistence/entity/ContextDataEntity.java` —
  entity to update
- AGENTS.md constraint: schema is forward-only; never modify V1 migration
- AGENTS.md constraint: Hibernate DDL is `none` — only Flyway may change schema

---

### Sub-Task 8 — Tests

**Status:** `[ ] pending`

**Intent:**
Write unit tests for the new components to satisfy the 70% JaCoCo coverage gate and verify
correct behaviour of the new system.

**Expected Outcomes:**
- `McpClientTest` — tests `isHealthy()` (UP and DOWN), `callTool()` success, `callTool()`
  failure returns null/throws as documented. Uses a mock `HttpClient` or Mockito stubbing.
- `McpRegistryTest` — tests `collectData()`:
  - Servers filtered by platform correctly.
  - Optional unhealthy server is skipped; required unhealthy server proceeds with warning.
  - Placeholder resolution for each known placeholder type.
  - Results list is populated correctly.
- `McpConfigLoaderTest` — tests file path resolution (env var set, env var absent →
  classpath fallback) and fail-fast on malformed JSON.
- `DiagnosticContextTest` — tests new `toString()` output contains server name, tool name,
  and data for each `McpToolResult` in the list.
- All tests follow existing project style: `@ExtendWith(MockitoExtension.class)`, constructor
  injection, `@Nested` + `@DisplayName`, AssertJ assertions.

**Relevant Context:**
- `src/test/java/com/causa/` — test root; follow existing test package structure
- AGENTS.md: JaCoCo 70% instruction coverage gate — new code paths must have tests
- Existing test examples in `src/test/java/com/causa/api/controllers/AlertsControllerTest.java`

---

### Sub-Task 9 — Documentation update

**Status:** `[ ] pending`

**Intent:**
Update all developer-facing documentation to reflect the new file-driven configuration
approach. Ensure developers can run the system locally and production operators understand
the volume mount contract.

**Expected Outcomes:**
- `deployment/kubernetes/base/mcp_settings.json` — renamed or replaced by a symlink note
  pointing to `src/main/resources/mcp.json` (or kept as a separate file for Claude/Bob
  compatibility; add a comment header explaining it is the same schema).
- `deployment/vm/.env.example` — updated with `MCP_CONFIG_PATH` and a comment block
  explaining the two-tier resolution strategy.
- Root `README.md` (if present) — update the MCP configuration section.
- `src/main/resources/mcp.json` — add a top-level comment block (JSON does not support
  comments, so use a `"_comment"` key or a companion `mcp.schema.md` file) explaining each
  field and the placeholder syntax.
- A companion `src/main/resources/mcp.schema.md` file documents:
  - Full schema reference with all fields including `dataType`
  - Placeholder list (`${podName}`, `${namespace}`, `${containerName}`, `${fieldSelector}`)
  - How to add a new MCP server (update `mcp.json` only, no code change needed)
  - How to add prompt guidance for a new server (add a `### N. SECTION_NAME` block to
    `rca-prompt-template.yml` matching the `dataType` value; the system works without it but
    LLM interpretation improves with it)
  - The `dataType` field is the LLM-visible section label and must match the guidance block
    name in the prompt if per-section LLM guidance is desired
  - Dev mode: run with `./mvnw quarkus:dev` — no `MCP_CONFIG_PATH` needed
  - Prod K8s: mount `mcp.json` as ConfigMap volume at `/etc/causa/mcp.json`; set
    `MCP_CONFIG_PATH=/etc/causa/mcp.json` in the ConfigMap
  - Prod VM: place file at `/opt/causa/mcp.json`; set `MCP_CONFIG_PATH=/opt/causa/mcp.json`
    in `.env`

**Relevant Context:**
- `deployment/kubernetes/base/configmap.yaml` — updated in Sub-Task 6
- `deployment/kubernetes/base/deployment.yaml` — updated in Sub-Task 6
- `deployment/vm/.env.example` — updated in Sub-Task 6

---

## Dependency Order

```
Sub-Task 1 (schema + constants)
    └── Sub-Task 2 (model classes)
            └── Sub-Task 3 (McpConfigLoader)
                    └── Sub-Task 4 (McpClient + McpRegistry)
                            ├── Sub-Task 5 (replace DiagnosticContext)
                            │       └── Sub-Task 5b (prompt template update)
                            └── Sub-Task 7 (DB migration + entity)
                                    └── Sub-Task 6 (wire into pipeline)
                                            └── Sub-Task 8 (tests)
                                                    └── Sub-Task 9 (docs)
```

Sub-Tasks 5 and 7 can proceed in parallel after Sub-Task 4 is complete.
Sub-Task 5b must follow Sub-Task 5 (section label format is finalised there).
Sub-Task 9 can be done alongside Sub-Task 8.

---

## Key Constraints (from AGENTS.md)

- `core/` must not import from `infrastructure/`, `api/`, or `llm/`.
  - `McpRegistry`, `McpClient`, `McpConfigLoader`, and model classes live in `com.causa.mcp`
    (infrastructure-adjacent, but not `core/`). `DiagnosticContext` and `McpToolResult`
    live in `com.causa.core.domain` if referenced from core services.
- Schema is forward-only: V2 migration only; never modify V1.
- Hibernate DDL is `none` — all schema changes via Flyway only.
- All IDs generated by `IdUtils`. New `context_data` rows use `ctxd_` prefix.
- All constants in `com.causa.common.constants` — no magic strings anywhere.
- All log messages defined in `LogMessages` — no inline strings in log calls.
- No Lombok on domain or entity classes.
- JaCoCo 70% gate — new code must be covered by tests.
