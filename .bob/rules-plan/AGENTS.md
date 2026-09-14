# AGENTS.md — Plan Mode

This file provides architectural constraints for agents planning changes in this repository.

## Non-Obvious Architectural Constraints

**Hexagonal layer boundary is a hard constraint**: `core/` has zero dependencies on `infrastructure/`, `api/`, or `llm/`. Violating this collapses the architecture. Any new feature that touches persistence must go through a `core/ports/` interface.

**Schema is forward-only**: Flyway `out-of-order=false` and `validate-on-migrate=true`. New schema changes must be new numbered migration files (`V2__...sql`, etc.). Never modify `V1__initial_schema.sql`.

**Hibernate must not manage DDL**: `quarkus.hibernate-orm.database.generation=none` is intentional. Any attempt to use `create`, `update`, or `drop-and-create` will conflict with Flyway-managed schema.

**Domain objects are immutable by design**: Adding mutable state or setters to `core/domain/` classes breaks the diagnostic pipeline's thread-safety contract (the async background thread reads these objects concurrently).

**JaCoCo 70% instruction coverage gate**: Any new code paths must have tests or the build fails. This is enforced in the `verify` phase.

**LLM provider is runtime-configurable**: `ChatModelFactory.chatModel()` reads config at call time. Adding a new provider means adding a `case` in that factory switch — no new CDI producers or beans. The `UnifiedPromptSender` routes `bob` provider calls to the Bob Shell CLI subprocess.

**JSONB columns require native SQL for filtering**: Planning list/search APIs over `workload_info` or `alert_metadata` JSONB columns requires native SQL queries — JPQL cannot handle PostgreSQL JSONB operators.

**Alert cooldown is in-memory only**: `AlertServiceImpl` uses an in-memory `ConcurrentHashMap` for cooldown tracking. This does not survive restarts and is not shared across replicas — design accordingly.

**pgvector extension**: The `hibernate-vector` dependency expects the `vector` PostgreSQL extension to exist. Dev Services initializes it via `db/dev-init.sql`; production requires it pre-installed on the cluster PostgreSQL.
