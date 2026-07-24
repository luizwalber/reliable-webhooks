# ADR-0011: Docker Compose service topology

## Status

Accepted (decision recorded; `docker-compose.yml` itself is an implementation-phase deliverable, not written by this ADR). Settled in [wayfinder ticket #15](https://github.com/luizwalber/reliable-webhooks/issues/15) — the last ticket on the wayfinder map.

## Context

The brief requires the entire environment (app, Kafka, Postgres, Redis, simulated consumers) to come up with a single `docker-compose up`. The concrete service list, image versions, startup ordering, and exposed ports weren't pinned down. Deferred until the backend/frontend code (and Dockerfiles) exist, since a compose file written against nonexistent images would be untestable.

## Decision

- **Kafka in KRaft mode** — no Zookeeper container (the currently-supported, modern Kafka mode; Zookeeper mode is legacy).
- **Exactly 6 services**:
  - `postgres` — `postgres:18`
  - `redis` — `redis:7`
  - `kafka` — `apache/kafka`, KRaft, single broker (multi-broker HA is out of scope for this project)
  - `backend` — Java 21 / Spring Boot, built from `back_java/`
  - `frontend` — React/TypeScript, built from `front_react/`
  - `simulator` — the standalone service from [ADR-0010](0010-simulated-consumers.md)
- **Startup ordering**: `backend` waits on `postgres`, `redis`, and `kafka` via `depends_on: condition: service_healthy` (not the default "container started," which doesn't guarantee readiness). `frontend` and `simulator` have no hard startup dependencies.
- **Exposed ports**: only `backend` (the `/v1` API plus actuator/health) and `frontend` (dev server) get host port mappings. `postgres`, `redis`, `kafka`, and `simulator` stay internal-only to the compose network — nothing outside the app needs to reach them directly for the demo to work (a human wanting to inspect Postgres/Redis/Kafka directly can still `docker compose exec`).

## Consequences

- 6 containers total, no Zookeeper — simpler operationally than a Zookeeper-based Kafka setup.
- `backend` won't accept traffic until its infra dependencies are actually healthy, avoiding the classic "container up but connection refused" race on `docker-compose up`.
- Writing the actual `docker-compose.yml` is now unblocked as soon as `back_java/` and `front_react/` have runnable Dockerfiles — it's next on the suggested build order (see `CONTEXT.md` / the map's Notes), not before.
