# Dynamic MCP Configuration System — Implementation Plan

## Context

`causa-backend` is a Quarkus/Java 21 RCA engine (hexagonal architecture: `api → core/{domain,ports,services} → infrastructure/adapters`). Today, all MCP (Model Context Protocol) server integration is static and code-driven:

- `com.causa.config.McpConfig` — a `@ConfigMapping(prefix="causa.mcp")` interface with **one hand-written nested interface per server** (kubernetes, kruize, cryostat, filesystem, quarkus, asyncProfiler, jmx).
- `com.causa.mcp.McpContextCollector` (1536 lines) — a hand-rolled JSON-RPC 2.0 + SSE client over raw `java.net.http.HttpClient`, with a **hardcoded, fixed sequence of tool calls per server**, including hardcoded argument-building (pod name/namespace/container baked into Java), a hardcoded retry loop for Cryostat (retry until `RECORDING_CREATED`), and a hardcoded dependency chain for Async-Profiler (tool #1's response yields a `recording_id` consumed by later calls).
- `com.causa.mcp.LibertyLogsContextCollector` — **duplicates** the same JSON-RPC/SSE protocol plumbing independently for the Filesystem/VM path.
- `com.causa.core.services.HealthCheckService` — duplicates config injection again and has 6 near-identical hardcoded per-server health-check methods.
- Adding a single new MCP server today requires editing 6+ files and a pod restart (documented step-by-step in `docs/tunables/mcp.md`).
- A prototype file `deployment/kubernetes/base/mcp_settings.json` already exists in a Claude-Desktop/Bob-style `mcpServers` schema (type/url/headers/platforms/required/alwaysAllow) but **is not read by any code today** — it's a dead reference artifact. Critically, it only lists *which* tools are allowed to be called (`alwaysAllow`), not *what arguments* to pass them, *in what order*, or *how results chain together* — that knowledge lives entirely in Java today.
- `DiagnosticServiceImpl` depends on the **concrete** `McpContextCollector` class directly (no port interface) — unlike the LLM side, which goes through `core.ports.llm.PromptSender`. This asymmetry is a known gap to close.
- The `context_data` table / `ContextDataEntity` exists in the DB schema but is **completely unused** — no repository, no service, nothing reads or writes it.

**Goal:** replace all of this with a single file-driven `mcp.json` that is rich enough to describe *not just which servers/tools exist, but how to invoke each tool* (argument sources, retry policy, result-chaining) — so that adding a new server, or a new tool on an existing server, requires editing `mcp.json` only, with no new Java class in the common case. This is loaded once at startup, backing a new `McpRegistry`/`McpClient` pair, while preserving the exact current behavior and LLM prompt output for all 7 existing servers, and with zero behavioral change to the `POST /api/v1/webhooks/alerts` → diagnostic → LLM flow.

Architecture decisions already made with the user:
1. **mcp.json extension style**: flat additive fields (not a nested vendor block) — the existing prototype's field names (`type`, `url`, `headers`, `platforms`, `required`, `alwaysAllow`) are kept exactly as-is (so it stays a valid, standards-recognizable `mcpServers` file for Claude Desktop/Bob/Cursor, which ignore unknown keys); new fields are purely additive.
2. **Context table**: repurpose the dead `context_data` table (additive columns via a new Flyway migration) rather than create a new table.
3. **Hexagonal port**: introduce `com.causa.core.ports.mcp.McpContextProvider`, mirroring `PromptSender`, closing the LLM/MCP dependency-style asymmetry.
4. **Tool-argument provisioning**: `mcp.json` gains a per-server `tools` array — one entry per tool *invocation* — that declares the argument sources (via a small `${placeholder}` templating vocabulary), optional retry-until-condition, and optional result-chaining (one tool's extracted output feeding a later tool's arguments). This is what makes "add a server/tool via config only" actually true, instead of only being true for the allow-list while argument-building silently remained hardcoded Java.
5. **(This revision) LLM interpretation knowledge for dynamically-added data**: separately from *collecting* data, the RCA prompt template and per-domain skill files contain hand-written prose telling the LLM *how to interpret* each known server's output (exact metric names, thresholds, e.g. "heapUtilisationPct ≥ 85% — heap nearly exhausted"). That knowledge is rightly static/hand-tuned for the 7 known servers and is **not** migrated into JSON. But it means a server/tool added purely via `mcp.json` has no such guidance anywhere. The fix: `mcp.json` gains an optional `description` field (per-tool and per-server) that flows into the LLM-visible context specifically for data the mapper doesn't already know how to place — see §1.2/§1.3/§7.3 below.

This plan was produced after three parallel codebase explorations (webhook/diagnostic pipeline, existing MCP config/client code, tech stack/conventions/constants), a dedicated design pass, and a source-verification pass. All file paths, class names, and existing behaviors cited below were confirmed against the actual source, not assumed. Argument key names in the worked JSON examples below (e.g. exact Kubernetes/Kruize argument keys) are representative based on the confirmed method names and `McpConstants.Arguments.*` constants; the implementer should confirm exact key strings against `McpConstants` when transcribing this into the real file, since the literal argument-building code itself wasn't dumped in full during exploration.

---

## 1. `mcp.json` Schema

**Canonical file**: `deployment/kubernetes/base/mcp_settings.json` stays the one and only source file — promoted from "dead prototype" to "live config", referenced unmodified by both dev (relative path) and the K8s ConfigMap generator (no copy, no drift).

### 1.1 Per-server fields — kept as-is

`type`, `url`, `headers` (`Map<String,String>`, empty by default), `platforms` (`List<String>`), `required` (`boolean`), `alwaysAllow` (`List<String>` — the permission allow-list, unchanged meaning: "these tool names may be invoked without further confirmation").

### 1.2 Per-server fields — new

| Field | Type | Purpose |
|---|---|---|
| `healthCheck` | `{ "url": string, "timeoutMs": int }` | Explicit health-probe URL (Cryostat's health API is on a different port than its MCP `url`) + timeout. |
| `timeoutMs` | `int` | Default HTTP timeout for `initialize`/`tools/call` against this server. |
| `metadata` | `Map<String,Object>`, optional | Server-wide tunables referenced by argument templates: e.g. `retryDelayMs`/`maxRetries` (Cryostat defaults), `libertyLogsDir` (Filesystem), `metricsBaseUrl` (Quarkus). |
| `tools` | `List<ToolInvocation>`, **new — fixes the argument-provisioning gap** | The ordered, declarative invocation plan for this server: which tools to call, with what arguments, in what order, with what retry/chaining behavior. Every name here must also appear in `alwaysAllow` (validated at load time — fail fast on mismatch). |
| `description` | string, optional, **new — fixes the LLM-interpretation gap (§7.3)** | A short, plain-English summary of what this server/system is (e.g. `"Async Profiler MCP — JVM profiling via async-profiler agent"`). Used only as a fallback label for data the LLM-context mapper doesn't already have static prompt knowledge about (see §7.3) — never overrides the hand-tuned prompt text for the 7 known servers. |

### 1.3 `ToolInvocation` object (each entry of `tools`)

| Field | Type | Required? | Meaning |
|---|---|---|---|
| `name` | string | yes | The MCP tool name to call. Must be present in the server's `alwaysAllow`. |
| `dataType` | string | yes | A stable label for this invocation's output (e.g. `POD_STATUS`, `GC_ANALYSIS`, `POD_LOGS_PREVIOUS`) — this is what ends up in `McpDataEntry.dataType()`/the persisted `data_type` column, and is also the key the LLM-context mapper (§7.3) uses to place known results into the legacy `DiagnosticContext` fields. Lets the *same* tool name be called twice with different semantics (e.g. `pods_log` for current vs. previous logs) without any Java branching. |
| `arguments` | `Map<String,String>`, optional | Argument key → a template string. Templates are resolved with plain `${token}` substitution (see §1.4) — no expression language, no new dependency. |
| `dependsOn` | string, optional | Name of an earlier invocation in this same `tools` list whose extracted value this one needs. If that value never resolved (dependency failed, or produced nothing), this invocation is **skipped** — no network call, no output entry — exactly matching today's "gated on recordingId != null" behavior for Async-Profiler. |
| `resultKey` | string, optional | The variable name under which this invocation's *extracted* value becomes available to later invocations' `${resultKey}` templates. |
| `extractStrategy` | string, optional | Name of a small built-in extractor function (e.g. `LATEST_RECORDING_ID`) for cases too specific for a plain regex (see §5.3). |
| `extractPattern` | string, optional | A regex with one capture group, applied to the tool's text payload, used when `extractStrategy` is absent. |
| `retryUntilContains` | string, optional | If set, the call is retried (up to `maxRetries`, waiting `retryDelayMs` between attempts) while the response text contains this substring — replicates Cryostat's retry-until-`RECORDING_CREATED` logic generically. |
| `maxRetries` / `retryDelayMs` | int, optional | Per-invocation override; falls back to the server's `metadata.maxRetries`/`metadata.retryDelayMs`, then a hardcoded default (3 / 5000ms) if neither is set. |
| `description` | string, optional, **new — fixes the LLM-interpretation gap (§7.3)** | Plain-English guidance for this specific data type (e.g. `"Heap utilisation percentage; values above ~85% indicate the heap is nearly exhausted"`). Same fallback-only role as the server-level `description` — see §7.3. |

### 1.4 Argument templating vocabulary

`${token}` is resolved, in this order, against:
1. **Request-scoped fields** from `McpCollectionRequest`: `podName`, `namespace`, `containerName`, `clusterName`, `workloadType`, `alertId`, `alertTimestamp`.
2. **Server metadata**: `metadata.<key>` reads from that server's own `metadata` map (e.g. `${metadata.libertyLogsDir}`).
3. **Chained results**: any `resultKey` produced earlier in the same server's `tools` list (e.g. `${recordingId}`).

Unresolvable tokens resolve to an empty string (not an error) — matches today's permissive, best-effort collection style (a missing pod name shouldn't crash the whole server's collection, just produce a less-populated tool call).

### 1.5 Worked examples

**Static arguments (kubernetes)** — `description` fields included for schema completeness/documentation, even though for this already-known server they are unused at runtime (its interpretation already lives in the hand-tuned prompt template, §7.3):
```json
"kubernetes": {
  "type": "streamable-http", "url": "http://kubernetes-mcp-server:8080/mcp", "headers": {},
  "platforms": ["cluster"], "required": true,
  "alwaysAllow": ["pods_get", "pods_log", "events_list"],
  "healthCheck": { "url": "http://kubernetes-mcp-server:8080/healthz", "timeoutMs": 5000 },
  "timeoutMs": 5000,
  "description": "Kubernetes MCP — live pod status, events, and container logs from the cluster API",
  "tools": [
    { "name": "pods_get",   "dataType": "POD_STATUS", "description": "Current pod phase, container statuses, restart counts, and resource requests/limits.", "arguments": { "name": "${podName}", "namespace": "${namespace}" } },
    { "name": "events_list","dataType": "POD_EVENTS",  "arguments": { "namespace": "${namespace}", "fieldSelector": "involvedObject.name=${podName}" } },
    { "name": "pods_log",   "dataType": "POD_LOGS",          "arguments": { "name": "${podName}", "namespace": "${namespace}", "container": "${containerName}", "previous": "false" } },
    { "name": "pods_log",   "dataType": "POD_LOGS_PREVIOUS", "arguments": { "name": "${podName}", "namespace": "${namespace}", "container": "${containerName}", "previous": "true" } }
  ]
}
```

**Retry-until-condition (cryostat)**:
```json
"cryostat": {
  "type": "streamable-http", "url": "http://cryostat-mcp:8000/mcp", "headers": {},
  "platforms": ["cluster"], "required": false,
  "alwaysAllow": ["get_gc_analysis", "get_memory_analysis", "get_thread_analysis", "get_exception_analysis", "get_container_analysis"],
  "healthCheck": { "url": "http://cryostat-mcp-api:8080/healthz", "timeoutMs": 15000 },
  "timeoutMs": 15000,
  "metadata": { "retryDelayMs": 5000, "maxRetries": 3 },
  "tools": [
    { "name": "get_gc_analysis",        "dataType": "GC_ANALYSIS",        "arguments": { "pod_name": "${podName}" }, "retryUntilContains": "RECORDING_CREATED" },
    { "name": "get_memory_analysis",    "dataType": "MEMORY_ANALYSIS",    "arguments": { "pod_name": "${podName}" }, "retryUntilContains": "RECORDING_CREATED" },
    { "name": "get_thread_analysis",    "dataType": "THREAD_ANALYSIS",    "arguments": { "pod_name": "${podName}" }, "retryUntilContains": "RECORDING_CREATED" },
    { "name": "get_exception_analysis", "dataType": "EXCEPTION_ANALYSIS", "arguments": { "pod_name": "${podName}" }, "retryUntilContains": "RECORDING_CREATED" },
    { "name": "get_container_analysis", "dataType": "CONTAINER_ANALYSIS", "arguments": { "pod_name": "${podName}" }, "retryUntilContains": "RECORDING_CREATED" }
  ]
}
```
(`maxRetries`/`retryDelayMs` omitted per-tool here — they fall back to the server-level `metadata` block above.)

**Result-chaining (async-profiler)**:
```json
"async-profiler": {
  "type": "streamable-http", "url": "http://async-profiler-mcp-server:8080/mcp", "headers": {},
  "platforms": ["cluster"], "required": false,
  "alwaysAllow": ["list_profiled_pods", "get_pod_jvm_status", "get_jvm_statistics", "get_recording", "get_recording_report", "get_jfr_summary", "get_flame_graph"],
  "healthCheck": { "url": "http://async-profiler-mcp-server:8080/healthz", "timeoutMs": 15000 },
  "timeoutMs": 15000,
  "tools": [
    { "name": "list_profiled_pods",   "dataType": "PROFILED_PODS",       "resultKey": "recordingId", "extractStrategy": "LATEST_RECORDING_ID" },
    { "name": "get_pod_jvm_status",   "dataType": "AP_JVM_STATUS",       "arguments": { "pod_name": "${podName}" } },
    { "name": "get_jvm_statistics",   "dataType": "AP_JVM_STATISTICS",   "arguments": { "pod_name": "${podName}" } },
    { "name": "get_recording",        "dataType": "AP_RECORDING",        "dependsOn": "list_profiled_pods", "arguments": { "recording_id": "${recordingId}", "format": "json" } },
    { "name": "get_flame_graph",      "dataType": "AP_FLAME_GRAPH",      "dependsOn": "list_profiled_pods", "arguments": { "recording_id": "${recordingId}", "format": "json" } },
    { "name": "get_recording_report", "dataType": "AP_RECORDING_REPORT", "arguments": { "pod": "${podName}", "container": "${containerName}", "namespace": "${namespace}", "last": "5m" } },
    { "name": "get_jfr_summary",      "dataType": "AP_JFR_SUMMARY",      "arguments": { "pod": "${podName}", "container": "${containerName}", "namespace": "${namespace}", "last": "5m" } }
  ]
}
```

**No-arg tools (jmx)** — every entry simply omits `arguments`:
```json
"jmx": {
  "...": "...",
  "tools": [
    { "name": "getHeapStatus", "dataType": "JMX_HEAP_STATUS" },
    { "name": "getGcActivity", "dataType": "JMX_GC_ACTIVITY" },
    { "name": "getThreadState", "dataType": "JMX_THREAD_STATE" },
    { "name": "getGcPressureAnalysis", "dataType": "JMX_GC_PRESSURE" },
    { "name": "getMemoryLeakIndicators", "dataType": "JMX_MEMORY_LEAK_INDICATORS" },
    { "name": "getThreadContentionAnalysis", "dataType": "JMX_THREAD_CONTENTION" },
    { "name": "getJvmRuntimeInfo", "dataType": "JMX_RUNTIME_INFO" }
  ]
}
```

**kruize** and **quarkus** follow the same static-argument shape as kubernetes (kruize: `getCostOptimizedRecommendations`/`getPerformanceOptimizedRecommendations` with `{"containerName": "${containerName}"}`; quarkus: `fetch_raw_metrics_from_endpoint` with `{"metricsUrl": "${metadata.metricsBaseUrl}"}`).

**filesystem is a deliberate partial exception**: its `tools` array declares `list_directory_with_sizes` (arguments `{"path": "${metadata.libertyLogsDir}"}`) so the directory listing is config-driven like everything else, but the actual per-file `read_text_file` calls stay in `LibertyLogsContextCollector` (§5.2) because *which files to read* is only known after parsing the directory listing at runtime (verbosegc/FFDC/messages.log filenames vary) — that isn't expressible as a fixed config entry, and forcing it to be would mean inventing a much larger dynamic-iteration mini-language for one server. `LibertyLogsContextCollector` calls `mcpClient.callTool("read_text_file", args)` directly for each discovered file, reusing the centralized `McpClient` (so there's still zero protocol-code duplication — only the *file discovery* loop stays bespoke Java, which is genuine Liberty-log domain logic, not MCP plumbing).

The complete, final `mcp.json` (all 7 servers, each with a full `tools` array in this shape) is a direct deliverable of implementation, following the patterns above exactly — not repeated in full here to keep this plan scannable, but every server's `tools` array is a straightforward transcription of the current hardcoded Java call sequence already inventoried in §5.3 of the earlier design pass.

---

## 2. File Path Resolution

Delete `application.yml` lines 141–175 (the whole `causa.mcp:` block). Replace with:

```yaml
causa:
  mcp:
    config-path: ${MCP_CONFIG_PATH:deployment/kubernetes/base/mcp_settings.json}
```
```yaml
"%dev":
  causa:
    mcp:
      config-path: ${MCP_CONFIG_PATH:deployment/kubernetes/base/mcp_settings.json}
"%test":
  causa:
    mcp:
      config-path: ${MCP_CONFIG_PATH:deployment/kubernetes/base/mcp_settings.json}
"%prod":
  causa:
    mcp:
      config-path: ${MCP_CONFIG_PATH:/etc/rca/mcp.json}
```

The explicit `${MCP_CONFIG_PATH:default}` form (not SmallRye's auto-derived `CAUSA_MCP_CONFIG_PATH`) is deliberate — it's the same pattern already used throughout `application.yml` (e.g. `CAUSA_DB_URL`, `LLM_PROVIDER`) whenever the project needs a specific external env var name.

- **Dev**: repo-relative path to the same canonical file — zero setup, no mount, no copy, no drift between local runs and what ships to K8s.
- **Prod**: `/etc/rca/mcp.json`, mounted from a new ConfigMap (§8).
- Both overridable by literally setting `MCP_CONFIG_PATH`.

**Fail-fast**: a new `McpConfigLoadException` (unchecked, `com.causa.common.exceptions`) is thrown by the loader on missing file / invalid JSON / Bean Validation failure / a `tools[].name` not present in that server's `alwaysAllow` (cross-field check, done manually in the loader since it spans two fields). This is allowed to **propagate out of the CDI startup observer**, aborting Quarkus boot — a deliberate, documented departure from the existing `ConfigStartup.java` pattern (which catches and logs a non-fatal warning), because MCP config is load-bearing for the whole diagnostic pipeline.

---

## 3. Supporting Model/DTO Classes

**Config model** (Jackson-deserialized external JSON — adapter-facing, lives under `com.causa.mcp.config`, not `core.domain` or `config`):

- `com.causa.mcp.config.McpSettings` — record, root: `Map<String, @Valid McpServerConfig> mcpServers` (`@NotEmpty`).
- `com.causa.mcp.config.McpServerConfig` — record: `type, url (@NotBlank), headers, platforms (@NotEmpty), required, alwaysAllow (@NotEmpty), healthCheck (@Valid), timeoutMs (@Positive), metadata, description, tools (List<@Valid McpToolConfig>)`. Null-safe defaults for `headers`/`metadata`/`tools` (`Map.of()`/`List.of()`) via compact canonical constructor.
- `com.causa.mcp.config.McpToolConfig` — record: `name (@NotBlank), dataType (@NotBlank), description, arguments (Map<String,String>), dependsOn, resultKey, extractStrategy, extractPattern, retryUntilContains, maxRetries, retryDelayMs`.
- `com.causa.mcp.config.HealthCheckConfig` — record: `url (@NotBlank), timeoutMs (@Positive)`.
- `com.causa.mcp.config.McpSettingsLoader` — `@ApplicationScoped`, injects the existing `ObjectMapper` bean + `Validator` bean (both already used elsewhere, e.g. `DiagnosticServiceImpl`). Reads the file at `causa.mcp.config-path`, deserializes, runs Bean Validation, then the manual `tools[].name ⊆ alwaysAllow` cross-check, throwing `McpConfigLoadException` with a clear message on any failure.

Reuses Jakarta Bean Validation (already a dependency via `quarkus-hibernate-validator`) — **no new JSON-Schema-validation library, no expression-language dependency** (the `${token}` templating is plain string substitution, resolved in Java, not a third-party engine).

**Adapter-internal protocol/result types** (`com.causa.mcp`):
- `McpToolResult` — record: `serverName, toolName, dataType, description, success, textPayload, rawResult (JsonNode), errorMessage`. `description` is copied straight from the `McpToolConfig`/`McpServerConfig` (tool-level if present, else server-level, else `null`) at invocation time by `McpToolSequenceExecutor`, so nothing downstream needs to re-look-up config.

**Domain-facing types** (`com.causa.core.domain` — cross the new port boundary, so framework-agnostic, matching the immutable-domain-object convention):
- `McpCollectionRequest` — record: `platform, alertId, podName, namespace, containerName, alertTimestamp`. Built from `Alert.getWorkloadInfo()` (confirmed fields: `podName, containerName, namespace, clusterName, workloadType`) plus the existing `causa.cluster.target-cluster-type` platform property.
- `McpDataEntry` — record: `mcpServerName, toolName, dataType, description, payload, success`.
- `McpCollectionResult` — immutable builder-pattern class (not a record, since it needs derived query behavior like `findByServerAndDataType`) wrapping `platform` + `List<McpDataEntry> entries`.

---

## 4. `McpRegistry`

**Name: `McpRegistry`** (not `McpFactory`). A factory manufactures transient instances on demand with no retained state; here we need **one long-lived `McpClient` per configured server**, constructed once at startup and reused for the app's lifetime, plus registry-style lookups (`getClient(name)`, filter-by-platform). That's registry semantics, not factory semantics. The name also matches the codebase's existing "single source of truth collection" vocabulary (`ConfigConstants`'s config-key registry).

- Package: `com.causa.mcp` (adapter layer). File: `src/main/java/com/causa/mcp/McpRegistry.java`.
- `@ApplicationScoped`, populated via an explicit `void init(McpSettings settings)` call (not eager CDI construction) — mirrors `ConfigService`'s explicit `loadFromDbAndEnv()` init-after-construction pattern, and keeps the door open for a second, differently-configured `McpRegistry`-shaped instance later (future multi-flow extensibility) without any interface change.
- Internal state: `Map<String, McpClient>` (`ConcurrentHashMap`, written once in `init()`, read-only afterward — safe across the async pipeline's background threads).
- `collectData(McpCollectionRequest request)`:
  1. Filter clients whose `platforms` contains `request.platform()`.
  2. Fan out **in parallel** using `Executors.newVirtualThreadPerTaskExecutor()`, created fresh per call (try-with-resources — `ExecutorService` is `AutoCloseable`). Justification: today's collection is implicitly sequential with zero concurrency; these are independent blocking I/O calls (`HttpClient` synchronous `send`), and Java 21 virtual threads are the natural fit with no thread-pool-sizing tuning needed. No prior precedent in the repo (verified: no existing `newVirtualThreadPerTaskExecutor` usage), but a standard, low-risk Java 21 idiom.
  3. For each server, call the single generic `McpToolSequenceExecutor.execute(client, client.getConfig(), request)` (§5.3) — **no per-server-name branching in Java any more**; every server, including Cryostat and Async-Profiler, is driven by the same config-interpreting executor. This is what closes the gap the user flagged: the *only* place that still needs a Java code change for a new server is if that server needs genuinely dynamic, discovered-at-runtime arguments (like Filesystem's per-file reads) — everything else is `mcp.json`-only.
  4. Aggregate the returned `List<McpToolResult>` per server into `McpDataEntry` records, combine into one `McpCollectionResult`.
- **`required` handling** (unchanged from prior analysis): verified that **no code path today** actually fails the pipeline when any MCP server is unreachable — every call site swallows its own exception and leaves a field `null` (confirmed in `McpContextCollector`/`LibertyLogsContextCollector`; corroborated by `docs/tunables/mcp.md`: "a down server never blocks the others"). So `required=true` does **not** introduce a new hard-failure mode. It drives: (a) log severity (`.error()` vs `.warn()`); (b) `HealthCheckService` component classification, unchanged from today's `DEGRADED`-not-`DOWN` behavior.
- Public surface: `init(McpSettings)`, `collectData(McpCollectionRequest)`, `getClient(String name)`, `allClients()`, `isInitialized()`.

**Startup wiring**: new `com.causa.mcp.McpStartup` (`@ApplicationScoped`), `@Observes @Priority(AppConstants.StartupConstants.MCP_PRIORITY) StartupEvent` — add `MCP_PRIORITY = 25` to `AppConstants.StartupConstants` (between `CONFIG_PRIORITY=20` and where LLM init would sit). Calls `mcpSettingsLoader.load()` then `mcpRegistry.init(settings)`; does **not** catch exceptions (see §2 fail-fast).

---

## 5. `McpClient` and the Generic Tool-Sequence Executor

### 5.1 `McpClient` — package `com.causa.mcp`, file `McpClient.java`

One plain instance per server, constructed by `McpRegistry.init(...)` — not a CDI bean itself (N runtime instances keyed by config-driven names).

**Centralizes** the JSON-RPC 2.0 + SSE protocol code verified to be **duplicated verbatim** today between `McpContextCollector` (`initializeMcpSession`, `sendInitializedNotification`, `callMcpTool`, `terminateMcpSession`, `parseSSEResponse`, `extractTextFromContent`) and `LibertyLogsContextCollector`'s private copies of the same methods:
- `initializeSession()`, `terminateSession(sessionId)`, `callToolRaw(sessionId, toolName, args)`, `parseSse(body)`, `extractTextFromContent(result)`.
- `McpToolResult callTool(toolName, dataType, args)` — high-level convenience combining the above, catching all exceptions internally (`success=false`), preserving today's per-call swallow-and-log semantics but with logging centralized instead of copy-pasted.
- **Allow-list guard**: `callTool` checks `toolName` against `McpServerConfig.alwaysAllow()` before any network call; rejects with a clear `errorMessage` if absent. This situation cannot occur today (tool names are compile-time constants) but becomes possible once tool names are data.
- `ComponentHealthDto checkHealth()` — GETs `healthCheck().url()` with `healthCheck().timeoutMs()`, same shape `HealthCheckService` already expects.
- Owns one `HttpClient` instance (mirrors today's one-per-collector pattern), and exposes `getConfig()`/`getServerName()` for the registry/executor.

`LibertyLogsContextCollector` (edit, not delete) **keeps** its unique Liberty-log business logic (directory-listing parsing, time-window filtering, size thresholds, verbosegc/FFDC/messages.log regexes) but **loses** its duplicated protocol code — it becomes a thin post-processor that looks up the `filesystem` client via `McpRegistry.getClient("filesystem")`, calls `mcpClient.callTool(...)` directly for each discovered file (§1.5), and applies its existing filtering to the results. Its `libertyLogsDir`/`alertWindowMinutes` now come from `McpServerConfig.metadata()` instead of the deleted `McpConfig.FilesystemConfig`.

### 5.2 What replaces `ToolInvocationStrategy` (superseded by this revision)

The earlier design's per-server `ToolInvocationStrategy` interface + 5 bespoke implementation classes (`Default`, `Cryostat`, `AsyncProfiler`, plus argument-building ones for Kubernetes/Kruize) is **replaced entirely** by one generic, config-interpreting class, since `mcp.json` itself now carries everything those classes used to hardcode:

**`com.causa.mcp.McpToolSequenceExecutor`** — `List<McpToolResult> execute(McpClient client, McpServerConfig config, McpCollectionRequest request)`:
1. `Map<String,String> extracted = new HashMap<>();` (chained-value store for this server's run).
2. For each `McpToolConfig toolConfig : config.tools()`, **in array order** (order = execution order — this is how sequencing/dependency ordering is expressed, matching JSON's natural ordering and avoiding a separate "order" field):
   a. If `dependsOn` is set and `extracted` has no non-blank value for the referenced `resultKey` → **skip** (no call, no output entry) — replicates today's "gated on recordingId != null" behavior.
   b. Resolve `arguments` via `${token}` substitution against request fields → `metadata.*` → `extracted` (§1.4), building an `ObjectNode`.
   c. If `retryUntilContains` is set: call in a loop (`maxRetries`/`retryDelayMs`, falling back to `config.metadata()` values, then hardcoded defaults 3/5000ms) while the response text contains that substring; otherwise a single call.
   d. Wrap the response into an `McpToolResult` tagged with `toolConfig.dataType()`.
   e. If `resultKey` is set, extract a value (§5.3) and store it in `extracted` for later invocations.
   f. Append to the output list.
3. Return the list.

This one class is reused for **every** server — Kubernetes, Kruize, Quarkus, JMX, Cryostat, Async-Profiler alike — because all their previously-bespoke behavior (arguments, retry, chaining) is now data. Filesystem's declared `tools` entries (just the directory listing) also go through it; the dynamic per-file reads remain `LibertyLogsContextCollector`'s job, calling `McpClient` directly (§5.1).

### 5.3 Result extraction — `com.causa.mcp.ResultExtractors`

A small registry, `Map<String, Function<McpToolResult,String>>`, for extraction logic too specific to express as a single regex:
- `"LATEST_RECORDING_ID"` — ports today's exact `extractLatestRecordingId` logic (parses the tool's JSON response, picks the most recent profiling session's ID) verbatim into one named function.
- Default (no `extractStrategy` given): apply `extractPattern` as a regex with one capture group against `textPayload()`; return the captured group, or `null` if no match.

This keeps the common case (a single regex) fully declarative, while acknowledging that "pick the latest item from a list" is genuine logic that deserves one small named Java function rather than an ad hoc JSON expression language — a deliberately narrow escape hatch, not a general-purpose scripting layer.

---

## 6. Context Table

**Migration** — new file `src/main/resources/db/migration/V2__add_mcp_columns_to_context_data.sql` (never edit `V1__initial_schema.sql`):

```sql
ALTER TABLE context_data
    ADD COLUMN mcp_server_name VARCHAR(64),
    ADD COLUMN tool_name       VARCHAR(128),
    ADD COLUMN data_type       VARCHAR(64),
    ADD COLUMN data_payload    JSONB;

CREATE INDEX IF NOT EXISTS idx_context_data_server_tool ON context_data (mcp_server_name, tool_name);
CREATE INDEX IF NOT EXISTS idx_context_data_type        ON context_data (data_type);
```

`content`, `container_name`, and `context_type` are all `NOT NULL` in `ContextDataEntity` today (verified). Rather than relaxing those constraints, the persistence write path satisfies them directly: `content` ← the entry's extracted text payload (`""` if absent); `context_type` ← the same value as the new `data_type` (a harmless redundant copy); `container_name` ← `alert.getWorkloadInfo().containerName()` (fallback `"UNKNOWN"` only if blank). Keeps the migration purely additive — 4 new nullable columns + 2 indexes, no constraint changes.

**Entity**: `ContextDataEntity` (edit) — add `mcpServerName` (`@Column(length=64)`), `toolName` (`@Column(length=128)`), `dataType` (`@Column(length=64)`), `dataPayload` (`@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb")`, `JsonNode` — JSONB requires native SQL type handling per existing convention, same pattern as `contextMetadata`).

**Domain + port + repository** (new — none exist today):
- `com.causa.core.domain.ContextData` — immutable builder-pattern class (mirrors `Diagnostic`'s style): `id, alertId, mcpServerName, toolName, dataType, dataPayload, containerName, contextType, createdAt`.
- `com.causa.core.ports.ContextRepository` — interface: `save(ContextData)`, `findByServerAndTool(alertId, serverName, toolName)`, `findByDataType(alertId, dataType)`, `findByAlertId(alertId)`.
- `com.causa.infrastructure.persistence.repositories.ContextRepositoryImpl` — Panache-based, mirrors `DiagnosticRepositoryImpl`.
- `IdUtils` (edit) — add `generateContextDataId()` → `"ctxd_" + randomAlphanumeric16()` (the `ctxd_` prefix is already documented in `IdUtils`'s Javadoc table but has no generator method yet).

**Persistence wiring**: new `com.causa.core.services.ContextPersistenceService` (+ `impl.ContextPersistenceServiceImpl`), method `persist(String alertId, McpCollectionResult result)` — maps each `McpDataEntry` to a `ContextData` and saves it. Called from the new adapter (§7.2) immediately after `mcpRegistry.collectData(...)` returns and before mapping into the legacy `DiagnosticContext` — keeps `DiagnosticServiceImpl` itself free of any new MCP-specific knowledge. Persistence failures are caught-and-logged (best-effort — a DB write failure for the observability trail must never abort the diagnostic pipeline).

---

## 7. Integration Point

**New port** — `src/main/java/com/causa/core/ports/mcp/McpContextProvider.java` (+ `package-info.java`, copied/adapted from `com.causa.core.ports.llm`'s):

```java
public interface McpContextProvider {
    DiagnosticContext collectContext(Alert alert);
}
```

Deliberately kept **identical in shape** to today's `McpContextCollector.collectContext(Alert)`, so `core.services` keeps depending only on the existing stable `Alert`/`DiagnosticContext` types — all the new `McpRegistry`/`McpCollectionRequest`/`McpCollectionResult` machinery stays behind this port as an adapter-internal detail.

**New adapter** — `com.causa.mcp.McpDiagnosticContextAdapter` (`@ApplicationScoped`, implements `McpContextProvider`, tagged "Secondary Adapter (Outbound)" like `LangChainPromptSender`): builds an `McpCollectionRequest` from the alert + platform property, calls `mcpRegistry.collectData(...)`, calls `contextPersistenceService.persist(...)`, then maps the result into a `DiagnosticContext` via `com.causa.mcp.DiagnosticContextMapper`.

### 7.3 Mapping the generic result back into `DiagnosticContext` — and closing the LLM-interpretation gap

**The gap this section addresses**: `rca-prompt-template.yml` and the per-domain skill files (`src/main/resources/skills/*/SKILL.md`) contain hand-written, hand-tuned prose telling the LLM *how to read* each known server's data — exact metric names, thresholds (`"heapUtilisationPct ≥ 85% — heap nearly exhausted"`), cross-references between sections (`"corroborate with POD EVENTS OOMKilling"`), and Kruize notification-code lookup tables. This is genuine prompt-engineering expertise, not boilerplate — it is **not** migrated into `mcp.json`, and it is **not regenerated dynamically**, because doing either would mean either dumbing down carefully-tuned domain guidance into generic JSON strings, or silently changing prompt behavior for the 7 known servers (a direct violation of the "no LLM prompt regression" constraint). Both the prompt template and the skill files stay exactly as they are, untouched by this change, for every server/tool the mapping table in §7.3 already recognizes.

The actual gap is narrower than "the LLM knows nothing about new servers": it's specifically "a server/tool added **purely via `mcp.json`**, with no matching prompt-template or skill-file edit, has no interpretation guidance anywhere." That's exactly the case the new `additionalContext` overflow section (below) is for — and it's why `description` was added to the schema (§1.2/§1.3): it's the one piece of interpretation guidance that *can* reasonably travel with the config, because it's short, factual, and about to be read once per diagnostic run rather than tuned over many incidents like the static template is.

**Mapping mechanism**: `DiagnosticContextMapper` (stateless) holds a `Map<String, BiConsumer<DiagnosticContext.Builder,String>>` keyed by `"<serverName>:<dataType>"` — using the config-declared `dataType` strings (chosen, when this is transcribed into the real `mcp.json`, to line up 1:1 with the ~30 existing `DiagnosticContext` fields, e.g. `POD_STATUS`, `POD_LOGS`, `POD_LOGS_PREVIOUS`, `GC_ANALYSIS`, ...). Because `dataType` is an explicit config value rather than something inferred from tool name + code branching, this table needs no knowledge of `McpConstants.Tools.*` — the config file and the mapper only need to agree on `dataType` strings, a documentation concern, not a code-coupling one. For every entry that **is** in this table, the existing static prompt/skill knowledge already covers it — the entry's `description` field (if present) is ignored, by design.

**Overflow path (the actual fix)**: any `McpDataEntry` whose `"server:dataType"` key is **not** in the table is appended to a new, purely additive `DiagnosticContext` field (`additionalContext`), rendered in a new trailing section only when non-blank (matching the existing precedent already used for the optional Quarkus/Async-Profiler sections — conditionally appended, not an always-on "Not available" placeholder). Each overflow entry is rendered as:
```
[<serverName> / <dataType>]
<tool-level description, else server-level description, else nothing>
<payload>
```
so the LLM gets at least the config-authored one-line explanation alongside any genuinely new data, instead of an unexplained blob of text it has to guess about. This guarantees: **`toString()` output for all 7 currently-known servers is byte-for-byte unchanged**, while a future config-only server addition surfaces in the LLM prompt *with* a minimal explanation, not just raw data, without any code or prompt-template change.

**Explicitly out of scope for this change** (future extensibility note, not implemented now, per requirement #7): the skill-file mechanism (`SkillsConfiguration`, `LLM_SKILLS_DIR`) is a natural place to eventually let a server declare a richer, versioned interpretation guide the same way Kruize/Kubernetes/Quarkus/Async-Profiler do today (e.g. a future optional `skillRef` field on the server config pointing at a skill file) — but wiring that dynamically is a larger change than this task's scope, and the lightweight `description` fallback is sufficient to avoid the LLM receiving *zero* guidance on new data.

`DiagnosticContext` changes (additive only): new `additionalContext` field/builder/getter, one new conditional `appendSection` call at the end of both `appendClusterSections`/`appendVmSections`, one new `ContextConstants.SECTION_ADDITIONAL_CONTEXT` constant. No changes to `RcaPromptBuilder`, `PromptConstants`, or any `.yml` prompt template — `additionalContext` flows through the existing single `{{context}}`/`{{diagnostic_context}}` placeholder exactly like every other section, since it's just more text inside `DiagnosticContext.toString()`.

**`DiagnosticServiceImpl`** (`src/main/java/com/causa/core/services/impl/DiagnosticServiceImpl.java`) — the only change is the field type and constructor parameter:
```diff
- import com.causa.mcp.McpContextCollector;
+ import com.causa.core.ports.mcp.McpContextProvider;
...
- private final McpContextCollector mcpContextCollector;
+ private final McpContextProvider mcpContextProvider;
...
- return mcpContextCollector.collectContext(alert);
+ return mcpContextProvider.collectContext(alert);
```
CDI resolves `McpContextProvider` → `McpDiagnosticContextAdapter` automatically (single implementer, same zero-config pattern as `PromptSender` → `UnifiedPromptSender`). `contextForLLM = diagnosticContext.toString()` is untouched — the LLM boundary contract does not change.

---

## 8. Deletions, Rewrites, Deployment

- **Delete** `src/main/java/com/causa/config/McpConfig.java` entirely (all 3 consumers are being rewritten).
- **Keep** `McpConstants` (tool name / JSON-RPC / SSE constants — still needed by `McpClient`'s protocol layer, and useful as the vocabulary reference when authoring `mcp.json`'s `tools[].name` values); light dead-code sweep only if something is genuinely unused after the rewrite.
- **Rewrite** `HealthCheckService`: remove the `McpConfig` param + 12 duplicate `@ConfigProperty` fields + 6 hardcoded `checkMcpXHealth()` methods (~500 lines); replace with one loop over `mcpRegistry.allClients()` filtered by platform, calling `client.checkHealth()`, using the existing `HealthCheckConstants.ComponentNames.MCP_*` constants for naming. `HealthCheckServiceTest.java` needs a matching rework (flagged, not detailed here).
- **`configmap.yaml`**: remove all `CAUSA_MCP_*` entries (lines 53–83) — config is now file-based, no replacement env var needed.
- **`kustomization.yaml`**: add a `configMapGenerator` sourced directly from the canonical file (no copy): `files: [mcp.json=mcp_settings.json]` — content-hash suffix also auto-restarts pods on config change.
- **`deployment.yaml`**: add a volume + volumeMount for the generated ConfigMap at `/etc/rca` (`readOnly: true`), matching the prod default config-path — mirrors the existing `deployment-adc-patch.yaml` volume-mount precedent for GCP ADC credentials.
- **Overlays** (`deployment/kubernetes/overlays/{kind,openshift}/configmap-patch.yaml`): verify during implementation there are no `CAUSA_MCP_*` references to strip (they patch the `causa-config` ConfigMap, not the new one, so likely unaffected).

---

## 9. Dev-Mode Documentation

- **`docs/tunables/mcp.md`** — full rewrite: config now lives entirely in `mcp.json` (not env vars); the full annotated field reference (§1) including the new `tools[]` schema and templating vocabulary; one worked example per server (all 7); a "how to add a new server/tool" walkthrough showing that a purely-config-driven server needs zero Java, while a Filesystem-style dynamic-discovery server needs a small bespoke post-processor; the `MCP_CONFIG_PATH` override with exact dev/prod defaults.
- **`docs/development/getting-started.md`** — short addition: MCP config loads from `deployment/kubernetes/base/mcp_settings.json` automatically in dev (run `./mvnw quarkus:dev` from the repo root, since the path is filesystem-relative to the JVM's working directory, not classpath-relative); override with `MCP_CONFIG_PATH` for a personal subset.
- Net dev flow: `./mvnw quarkus:dev` → nothing else required. No volume mount, no ConfigMap, no env var.

---

## 10. Architectural Justification (recap)

- **`McpRegistry` over `McpFactory`**: retained live per-server state + registry-style lookups don't fit factory semantics (§4).
- **Declarative `tools[]` over per-server Java strategy classes**: the earlier design still hardcoded argument-building/retry/chaining in Java per server, which only partially solved "config-driven MCP" (it solved *which tools*, not *how to call them*). Moving arguments, retry, and result-chaining into `mcp.json` via a small, dependency-free `${token}` templating vocabulary means genuinely new/simple servers need zero Java, and even today's two "special" servers (Cryostat's retry, Async-Profiler's chaining) become ordinary config — the only remaining Java exception (Filesystem's dynamic per-file discovery) is *inherently* runtime-dependent, not a design shortcut.
- **Parallel across servers, sequential within**: unchanged rationale — independent I/O across servers via virtual threads; strict in-order execution of one server's `tools[]` array preserves any dependency chain exactly.
- **`${MCP_CONFIG_PATH:default}` explicit interpolation**: required because the mandated env var name doesn't match SmallRye's auto-derived name — same pattern used everywhere else in this file.
- **Flat additive `mcp.json`, no expression-language dependency**: preserves drop-in compatibility with Claude Desktop/Bob/Cursor; the templating is plain Java string substitution, not a new parsing/expression-language dependency (no SpEL, no JSONPath library) — consistent with "no new frameworks unless necessary."
- **`context_data` reuse**: already has the right general shape and is unused; purely additive migration is lower-risk than a parallel new table.
- **New `core.ports.mcp` port**: closes the `PromptSender`-vs-`McpContextCollector` asymmetry directly; `DiagnosticServiceImpl` never imports a concrete adapter package, exactly as it already correctly avoids doing for `com.causa.llm`.
- **Future multi-flow support**: because `McpRegistry` is populated via an explicit `init(McpSettings)` call rather than eager CDI construction from one global `@ConfigMapping`, a second registry/port implementation pointed at a different `mcp.json` is a small addition later, not a redesign.
- **Config-carried `description`, not a prompt-template rewrite**: the RCA prompt template and skill files encode genuine, hand-tuned domain expertise (thresholds, cross-references, notification-code tables) for the 7 known servers — migrating that into JSON would be a large, risky rewrite for no benefit, since it never regresses for known servers anyway. A short optional `description` per server/tool, surfaced only in the new `additionalContext` overflow section, gives the LLM *something* to go on for genuinely new, config-only servers without touching the tuned prompt at all.

---

## Critical Files

**New:**
- `src/main/java/com/causa/mcp/McpRegistry.java`, `McpClient.java`, `McpStartup.java`, `McpToolSequenceExecutor.java`, `ResultExtractors.java`, `McpToolResult.java`, `McpDiagnosticContextAdapter.java`, `DiagnosticContextMapper.java`
- `src/main/java/com/causa/mcp/config/McpSettings.java`, `McpServerConfig.java`, `McpToolConfig.java`, `HealthCheckConfig.java`, `McpSettingsLoader.java`
- `src/main/java/com/causa/core/ports/mcp/McpContextProvider.java` (+ `package-info.java`)
- `src/main/java/com/causa/core/domain/McpCollectionRequest.java`, `McpDataEntry.java`, `McpCollectionResult.java`, `ContextData.java`
- `src/main/java/com/causa/core/ports/ContextRepository.java`
- `src/main/java/com/causa/infrastructure/persistence/repositories/ContextRepositoryImpl.java`
- `src/main/java/com/causa/core/services/ContextPersistenceService.java` (+ `impl.ContextPersistenceServiceImpl`)
- `src/main/java/com/causa/common/exceptions/McpConfigLoadException.java`
- `src/main/resources/db/migration/V2__add_mcp_columns_to_context_data.sql`

**Edited:**
- `src/main/java/com/causa/core/services/impl/DiagnosticServiceImpl.java` (field type + constructor only)
- `src/main/java/com/causa/core/services/HealthCheckService.java` (rewrite MCP portion)
- `src/main/java/com/causa/mcp/LibertyLogsContextCollector.java` (strip duplicated protocol code, call `McpClient` directly for dynamic file reads)
- `src/main/java/com/causa/infrastructure/persistence/entity/ContextDataEntity.java` (add 4 fields)
- `src/main/java/com/causa/core/domain/DiagnosticContext.java` (additive `additionalContext` field/section)
- `src/main/java/com/causa/common/constants/ContextConstants.java` (new section constant)
- `src/main/java/com/causa/common/constants/AppConstants.java` (add `MCP_PRIORITY = 25`)
- `src/main/java/com/causa/common/utils/IdUtils.java` (add `generateContextDataId()`)
- `src/main/resources/application.yml` (remove `causa.mcp.*` block, add `causa.mcp.config-path`)
- `deployment/kubernetes/base/{mcp_settings.json,configmap.yaml,deployment.yaml,kustomization.yaml}` — `mcp_settings.json` gets the full 7-server rewrite with `tools[]` per §1.5.

**Deleted:**
- `src/main/java/com/causa/config/McpConfig.java`
- The `ToolInvocationStrategy` interface and its bespoke per-server implementations are **not created** in this revision (superseded by `McpToolSequenceExecutor` before ever being written).

**Deliverable for this task**: write the above — including the fully worked-out `mcp.json` schema and examples — as `plan-claude.md` at the repo root once approved (this is what the user explicitly asked for: a plan document, not the implementation itself). Implementation is a separate, later step.

---

## Verification (once implemented)

1. `./mvnw test` — existing `McpContextCollectorTest`/`HealthCheckServiceTest` need rework; new tests for `McpSettingsLoader` (Bean Validation + `tools[].name ⊆ alwaysAllow` rejection cases), `McpToolSequenceExecutor` (templating resolution, retry-until-condition, dependsOn-skip, result-chaining), and `DiagnosticContextMapper` (mapping completeness for all ~30 known combinations).
2. `./mvnw quarkus:dev` from repo root — confirm `mcp.json` loads with zero extra setup; confirm boot fails fast with a clear error if the file is deleted/corrupted or a `tools[].name` isn't in `alwaysAllow` (manual negative tests).
3. Send a synthetic webhook to `POST /api/v1/webhooks/alerts` in dev — confirm `contextForLLM` text output is unchanged for all 7 known servers versus a pre-change capture, and confirm rows land in `context_data` queryable by `mcp_server_name`/`tool_name`/`data_type`.
4. `mvn verify` — confirm the 70% JaCoCo instruction-coverage gate still passes given the new classes.
5. `kubectl kustomize deployment/kubernetes/base` (or `oc`) — confirm the generated ConfigMap + volume mount render correctly.
