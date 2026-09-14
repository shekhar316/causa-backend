# AGENTS.md — Ask Mode

This file provides documentation context for agents answering questions about this repository.

## Non-Obvious Documentation Context

**`core/` is not a "service layer"** — it's a full hexagonal core with domain objects, port interfaces, and service implementations. The `impl/` sub-package under `core/services/` contains the concrete service implementations.

**LangChain4J is used directly** (not via `quarkus-langchain4j` extension). `ChatModelFactory` creates models imperatively; `UnifiedPromptSender` routes to either the LangChain4J model or the Bob Shell (`bob` CLI) depending on configuration.

**`mcp/` does not expose MCP tools** — it *consumes* external MCP servers (Kubernetes MCP, Kruize MCP, Cryostat MCP, etc.) over HTTP to collect diagnostic context data.

**Diagnostic pipeline is async**: `POST /api/v1/alerts/webhook` returns 202 immediately; actual RCA runs on a background thread. The `GET /api/v1/diagnostics/{id}` endpoint reflects async status (`PENDING` → `PROCESSING` → `COMPLETED`/`FAILED`).

**Two validation sub-pipelines exist**:
1. Rule-based: YAML rulesets in `src/main/resources/rulesets/` evaluated by `RuleEngine`
2. LLM-based: assertion extraction + parallel LLM analysis in `core/services/validation/`

**Dev mode needs no configuration** — `quarkus:dev` auto-starts a pgvector PostgreSQL container via Dev Services. Only `LLM_PROVIDER` and related env vars need to be set for LLM functionality.

**`application.yml` profiles**: `%dev` profile activates Dev Services and defaults to `vertex-ai-anthropic`; `%prod` requires explicit `CAUSA_DB_URL`, `CAUSA_DB_USERNAME`, `CAUSA_DB_PASSWORD` env vars.
