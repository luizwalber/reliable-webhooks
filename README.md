# Reliable Webhooks

A reliable webhook-delivery service — Java 21 / Spring Boot backend, transactional outbox, ingest-time idempotency, retry/DLQ, per-endpoint circuit breaking, HMAC-signed deliveries. See `CONTEXT.md` for the domain glossary and `docs/adr/` for every architecture decision behind it.

## Running the whole stack

`docker-compose.yml` (repo root) brings up the backend, Postgres, Redis, Kafka, and the [simulator](docs/adr/0010-simulated-consumers.md) with one command (`frontend` is deferred until `front_react/` has a real app — see [ADR-0011](docs/adr/0011-docker-compose-topology.md)):

```bash
cp .env.example .env   # first time only
docker compose up -d --build
```

The backend's `/v1` API is reachable at `http://localhost:8080/v1` (or whatever `BACKEND_PORT` you set in `.env`). Postgres, Redis, Kafka, and the simulator stay internal to the compose network — nothing outside the app needs to reach them directly; use `docker compose exec <service> ...` if you want to inspect one.

### Demoing retry/DLQ/circuit-breaker behavior

The simulator isn't a special resource — register it like any other Endpoint, pointing at one of its fixed behavior routes:

```bash
curl -X POST http://localhost:8080/v1/endpoints \
  -H "Content-Type: application/json" \
  -d '{"url":"http://simulator:4000/simulate/error-500"}'
```

Available routes: `/simulate/success`, `/simulate/error-500`, `/simulate/timeout`, `/simulate/intermittent` (alternates success/failure). No simulator-specific API to learn — see [ADR-0010](docs/adr/0010-simulated-consumers.md).

### Smoke-testing the compose environment

```bash
./scripts/smoke-test.sh
```

Brings the stack up for real, registers Endpoints against the simulator, ingests Events, and confirms a Delivery reaches `DELIVERED` and (after the retry ladder exhausts) a separate one reaches `DEAD` — an end-to-end proof the whole topology works together, not just that each container starts. Tears the stack down afterward. Requires only `docker compose` and `curl`.

## Backend (`back_java/`)

### Configuration and secrets

Local config lives in `back_java/.env`, loaded automatically by Docker Compose. It is **gitignored and never committed** — set it up once:

```bash
cd back_java
cp .env.example .env
```

`.env.example` **is** committed. That's intentional, not an oversight: right now it holds no real secrets, only placeholder credentials for the local `docker-compose.test.yml` Postgres instance (matching `application.yml`'s built-in defaults, so the app and tests run out of the box even without copying `.env` at all). Committing it documents exactly which variables a deployment needs to set.

**This changes once real secrets exist** (production database credentials, signing keys, third-party API tokens, etc.): at that point `.env.example` keeps only variable *names* with empty or obviously-fake placeholder values, and the real `.env` — like today — stays local-only and gitignored. Nothing about that workflow needs to change; only the values `.env.example` is allowed to carry.

### Running the tests

```bash
cd back_java
docker compose -f docker-compose.test.yml up -d   # Postgres + Redis
mvn test
```

See `docs/adr/0014-docker-compose-test-seam.md` for why this (rather than Testcontainers) is the local test seam.
