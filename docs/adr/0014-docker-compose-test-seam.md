# ADR-0014: Docker Compose-based local test seam (replaces Testcontainers-managed containers)

## Status

Accepted. Supersedes the container-orchestration mechanism implied by spec issue #17's Testing Decisions ("MockMvc + Testcontainers... to keep the seam count at one") — the seam itself (MockMvc against real Postgres + Redis, no mocks) is unchanged; only how those containers come up changed.

## Context

`AbstractIntegrationTest` originally used Testcontainers (`PostgreSQLContainer`, a `GenericContainer` for Redis) to start Postgres and Redis per test run. This never worked in the development environment used for this project:

- On native Windows, `docker-java` (the library Testcontainers uses to talk to the Docker Engine API) consistently received a synthetic, all-zeroed "safe" response from Docker Desktop, on every transport tried (the default zero-dependency transport and the `httpclient5` transport), over both the named pipe and a manually-exposed TCP endpoint, in-process and forked, before and after a full Docker Desktop restart.
- The `docker` CLI, `docker compose` CLI, `curl`, and even a plain `java.net.http.HttpClient` all got correct responses from the exact same Docker Desktop instance throughout — ruling out a broken Docker install.
- Installing a full Ubuntu WSL2 distro and running the same Maven build there reproduced the *identical* symptom against Docker Desktop's WSL2 integration socket — ruling out "Windows npipe quirk" specifically. Same Docker Desktop, different OS, different transport, same synthetic response.
- The developer's Docker Desktop account is a personal (non-organization) account, ruling out an org-managed Hardened Desktop / restricted-API policy as the cause.

The common thread across every failed attempt: `docker-java` specifically never got a real response from Docker Desktop, while every other client (including plain HTTP clients) always did. Whatever Docker Desktop does differently for `docker-java`'s request shape was never isolated, and further debugging had no more untried angles worth the time — see the closed investigation in this project's chat history for the full blow-by-blow.

## Decision

Stop asking `docker-java`/Testcontainers to manage containers for local test runs. Bring up Postgres and Redis with a plain `docker compose` CLI invocation instead — the one thing that worked reliably in every environment tried:

```
docker compose -f back_java/docker-compose.test.yml up -d
```

`docker-compose.test.yml` pins Postgres and Redis to the exact host ports and credentials `application.yml` already expects (`5432`, `6379`, `reliable_webhooks`/`reliable_webhooks`), so no dynamic property wiring is needed — `AbstractIntegrationTest` just runs `@SpringBootTest` and lets Spring connect normally.

This file is distinct from the eventual runtime `docker-compose.yml` described in [ADR-0011](0011-docker-compose-topology.md) (6 services, the whole app) — this one is test-only and `back_java`-scoped.

## Consequences

- **Lost**: per-test-run container isolation and dynamic port allocation. Postgres/Redis are long-lived across test runs unless explicitly torn down (`docker compose -f back_java/docker-compose.test.yml down -v`); data accumulates between runs, and within a single run across test classes. This was a real, not theoretical, cost: the first green run under this ADR surfaced two tests that had silently relied on Testcontainers' per-test-class database isolation — `eventRepository.count()`/`findAll()` asserting against the *entire* table, which only ever held that one test's rows before. Fixed by scoping those assertions to the specific idempotency key each test created, which is the correct shape regardless of container strategy. Any new test asserting repository-wide counts/lists instead of filtering by what it itself created will hit the same failure mode.
- **Lost**: the "one command, fully automatic" experience Testcontainers gives — `docker compose up -d` is a manual step before `mvn test`, not wired into the Maven lifecycle. Kept manual deliberately, to avoid adding another layer of tooling on top of an already-debugged-to-exhaustion Docker interaction.
- **Gained**: a test suite that actually runs, on this project's real development environment, using only the Docker code paths confirmed to work everywhere (the CLI, never `docker-java`).
- `spring-boot-testcontainers`, `org.testcontainers:junit-jupiter`, and `org.testcontainers:postgresql` are removed from `pom.xml` — unused once nothing calls into `docker-java`.
- CI (not yet set up) should run on Linux runners where this whole class of problem may not reproduce; if it doesn't, CI could reasonably use real Testcontainers instead of this Compose file. That divergence between local and CI test infra would be a new decision, not assumed here.
