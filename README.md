# Reliable Webhooks

A reliable webhook-delivery service — Java 21 / Spring Boot backend, transactional outbox, ingest-time idempotency, retry/DLQ, per-endpoint circuit breaking, HMAC-signed deliveries. See `CONTEXT.md` for the domain glossary and `docs/adr/` for every architecture decision behind it.

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
