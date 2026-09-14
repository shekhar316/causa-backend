# AGENTS.md

This file provides guidance to agents when working with code in this repository.

## Stack
- **Java 21**, Quarkus 3.36.1, Maven (use `./mvnw`)
- LangChain4J (not Quarkus LangChain4J extension) for LLM integration
- Hibernate ORM + Panache (repository pattern, **not** ActiveRecord), PostgreSQL with pgvector
- Flyway for schema migrations — Hibernate DDL is set to `none` (never add `ddl-generation`)
- Jakarta EE (REST via RESTEasy Reactive, CDI, Bean Validation)

## Build & Test Commands
```bash
./mvnw clean compile -DskipTests        # compile only
./mvnw clean test                        # run all unit tests
./mvnw test -Dtest=AlertsControllerTest  # run a single test class
./mvnw test -Dtest=AlertsControllerTest#shouldReturn200WhenAlertFound  # single test method
./mvnw quarkus:dev                       # dev mode (requires no DB config — Dev Services spins up pgvector/pg17)
./mvnw clean package -DskipTests        # build JAR
```
- **JaCoCo enforces 70% instruction coverage** — tests will fail if coverage drops below this threshold.
- Test logs are silenced to console; full output goes to `target/test-logs/test.log`.
- Integration tests (`*IT.java`) are skipped by default (`skipITs=true`); only run during native builds.

## Architecture
Strict hexagonal (ports-and-adapters) layering — never import infrastructure or API packages from core:
```
api/            → JAX-RS controllers, DTOs, mappers (thin layer; no business logic)
core/domain/    → Immutable domain objects (builder pattern, no Lombok, no JPA annotations)
core/ports/     → Interfaces (AlertRepository, DiagnosticRepository, PromptSender)
core/services/  → Business logic; services are interfaces with impl/ sub-package
infrastructure/ → JPA entities, Panache repositories, health checks (implements core ports)
llm/            → LangChain4J wiring (ChatModelFactory, prompt senders)
mcp/            → MCP context collectors (external tool calls)
config/         → Quarkus @ConfigMapping classes
common/         → Shared constants, utils, exceptions, logging
```

## Critical Patterns

**IDs**: Use [`IdUtils`](src/main/java/com/causa/common/utils/IdUtils.java) for all entity ID generation. IDs are `{prefix}_{16-char}` (e.g. `alrt_`, `diag_`, `cnfg_`), stored as `VARCHAR(21)`. Never use UUID.

**Logging**: Use [`CausaLogger`](src/main/java/com/causa/common/logging/CausaLogger.java) (not SLF4J/JBoss Logger directly). All log message strings must be defined in [`LogMessages`](src/main/java/com/causa/common/logging/LogMessages.java) — **no magic strings** policy.
```java
private static final CausaLogger log = CausaLogger.getLogger(MyClass.class);
log.info(LogMessages.MyClass.SOME_EVENT).field("key", value).log();
```

**Domain objects**: Immutable finals with private constructors and inner `Builder` class. No Lombok. No JPA annotations on domain classes — those live exclusively in `infrastructure/persistence/entity/`.

**JSONB queries**: List queries with JSONB operators (e.g. `workload_info->>'namespace'`) must use **native SQL** via `EntityManager`, not JPQL/Panache `find()` — JPQL cannot parse PostgreSQL JSONB operators.

**Config properties**: All custom `causa.*` properties are in [`application.yml`](src/main/resources/application.yml). Every property has an env-var override (`${ENV_VAR:default}`). `quarkus.config.mapping.validate-unknown=false` is set to suppress warnings for custom properties.

**Exception handling**: Domain exceptions (`AlertException`, `DiagnosticException`) are caught by [`GlobalExceptionMapper`](src/main/java/com/causa/common/exceptions/GlobalExceptionMapper.java) using `@ServerExceptionMapper`. Add new exception types there.

**JSON/Map conversion**: Use [`JsonUtils`](src/main/java/com/causa/common/utils/JsonUtils.java) — `convertJsonStringToMap()` for runtime parsing, `mapToJsonNode()` / `jsonNodeToMap()` for JSONB entity mapping. The `ConfigPropertyJsonConverter` inner class handles `Map<String,String>` config injection automatically.

**Encryption**: Sensitive config values use AES-256-GCM via [`EncryptionUtils`](src/main/java/com/causa/common/utils/EncryptionUtils.java). The key is set via `CAUSA_ENCRYPTION_KEY` (Base64-encoded 32-byte key).

## Database
- Schema exclusively managed by Flyway (`src/main/resources/db/migration/`). Do not modify Hibernate DDL settings.
- Dev mode uses Quarkus Dev Services: auto-spins `pgvector/pgvector:pg17` with post-init script `src/main/resources/db/dev-init.sql` to enable the vector extension.
- `BaseEntity` provides `createdAt`/`updatedAt` via `@CreationTimestamp`/`@UpdateTimestamp`. All entities extend it.
- Hibernate `CamelCaseToUnderscoresNamingStrategy` is active — Java field `alertName` maps to column `alert_name`.

## Test Style
- Unit tests use `@ExtendWith(MockitoExtension.class)` + constructor injection of mocks (no `@InjectMocks`).
- Tests are structured with `@Nested` inner classes per endpoint/scenario, `@DisplayName` on every method.
- Use AssertJ (`assertThat`) and JUnit 5 assertions — both are available.
- No `@QuarkusTest` on unit tests (no running container needed); `@QuarkusTest` is reserved for integration tests.
