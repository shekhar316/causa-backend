# AGENTS.md — Agent (Coding) Mode

This file provides coding-specific guidance for agents working in this repository.

## Non-Obvious Coding Rules

**ID generation**: Always use `IdUtils.generateAlertId()` / `generateDiagnosticId()` / `generateConfigurationId()` — never `UUID.randomUUID()`. IDs are `VARCHAR(21)` in the DB schema.

**Logging — mandatory pattern**:
1. Declare `private static final CausaLogger log = CausaLogger.getLogger(MyClass.class);`
2. All message strings must be constants in `LogMessages` nested class matching the package (e.g. `LogMessages.Alert.*`). Never inline string literals in log calls.
3. Chain `.field("key", value)` for context, `.exception(e)` for errors, always end with `.log()`.

**Domain object construction**: Use the inner `Builder` — never add a public constructor with parameters. Domain objects are `final` and immutable; all fields validated with `Objects.requireNonNull` in the private constructor.

**Layer import discipline**:
- `core/` classes must **not** import from `infrastructure/`, `api/`, or `llm/` packages.
- `api/` controllers inject `core/services/` interfaces only — never infrastructure or LLM classes directly.
- `infrastructure/` implements `core/ports/` interfaces; entity mappers live in `infrastructure/persistence/mappers/`.

**JSONB list queries**: Write native SQL via `EntityManager` when filtering on JSONB columns. Panache's JPQL `find()` will fail at parse time with PostgreSQL JSONB operators.

**New exception types**: Add a `@ServerExceptionMapper` handler in `GlobalExceptionMapper`. Extend the exception hierarchy in `common/exceptions/`. Do not add try/catch in controllers.

**Async diagnostics**: `DiagnosticServiceImpl.triggerDiagnostics()` returns immediately after persisting PENDING state; the full MCP→LLM pipeline runs on a background `ExecutorService` thread. Do not make this synchronous.

**Constants over literals**: All API paths (`/api/v1/...`), field names, and status strings have constants in `common/constants/`. Check there before creating new string literals.

**Validation assertions**: Use AssertJ (`assertThat(...).isEqualTo(...)`) for value checks; use JUnit 5 `assertEquals` / `assertSame` / `assertTrue` for identity/status checks (both styles coexist by convention).
