# Research: Outbox Poller Mechanics (Scheduled Poll vs CDC/Debezium)

Ticket: [luizwalber/reliable-webhooks#3](https://github.com/luizwalber/reliable-webhooks/issues/3)

## Question

For a portfolio-scale Spring Boot + Postgres setup, what are the trade-offs between a simple
scheduled poller (interval/batch size) and CDC (e.g. Debezium) for reading the transactional
outbox and publishing to Kafka?

## Scheduled Poller Mechanics

- Standard shape (per microservices.io and common Spring practice): a `@Scheduled(fixedDelay = ...)`
  task periodically runs `SELECT ... FROM outbox WHERE published_at IS NULL ORDER BY created_at
  LIMIT :batchSize`, publishes each row to Kafka, then marks it published (or deletes it) in the
  same or a follow-up transaction. ("Pattern: Polling publisher", microservices.io)
- **Concurrency/locking**: with more than one poller instance (or multiple threads), two pollers
  can select the same unpublished row and double-publish unless rows are claimed atomically.
  Postgres's row-locking clause is designed for exactly this:
  - `SELECT ... FOR UPDATE SKIP LOCKED` — locks the selected rows for update, and rows already
    locked by another concurrent transaction are silently skipped rather than causing the query
    to block or fail. Introduced in Postgres 9.5. This turns the outbox table into a safe
    multi-consumer job queue: each poller instance gets a disjoint batch of rows.
    (PostgreSQL docs, `SELECT` reference: https://www.postgresql.org/docs/current/sql-select.html)
  - Contrast with plain `FOR UPDATE` (blocks/waits on locked rows) or `NOWAIT` (errors instead of
    waiting) — `SKIP LOCKED` is the only one of the three that keeps a batch poller from stalling
    or double-processing under concurrency.
  - Example query:
    ```sql
    SELECT id, aggregate_type, aggregate_id, event_type, payload
    FROM outbox
    WHERE published_at IS NULL
    ORDER BY created_at
    LIMIT 100
    FOR UPDATE SKIP LOCKED;
    ```
- **Interval vs batch-size trade-off**:
  - Shorter interval (e.g. 500ms–1s) → lower end-to-end latency, more frequent (mostly-empty)
    queries against Postgres.
  - Longer interval (e.g. 5s+) → higher latency, less DB pressure.
  - Larger batch size → fewer poll cycles needed to drain a backlog, but longer-held row locks per
    cycle and bigger single Kafka-publish bursts.
  - At demo/portfolio scale (single-digit to low-hundreds of events, not sustained high
    throughput), the interval is essentially the only latency lever that matters, and batch size
    (e.g. 50–100 rows) only matters for burst catch-up — normal steady state is 0–1 rows per poll.
  - "Polling introduces slight latency depending on the polling interval. Too frequent polling can
    stress the database; too infrequent increases the delay." (search synthesis over
    community Spring+outbox writeups, consistent with microservices.io's own framing that polling
    trades latency for simplicity)
- Delivery semantics are at-least-once regardless of interval/batch tuning: a publish can succeed
  against Kafka but fail before the row is marked published, causing a redelivery on the next
  poll. Consumers must be idempotent either way (true for CDC too).

## CDC/Debezium Mechanics

- Debezium's Postgres connector performs **transaction log tailing**: it reads Postgres's
  write-ahead log (WAL) via **logical decoding** rather than querying the table. This requires:
  - `wal_level = logical` in `postgresql.conf`
  - `max_wal_senders >= 1` and `max_replication_slots >= 1`
  - A dedicated **replication slot**, which Postgres uses to guarantee WAL retention for the
    connector even while it's offline — but an unused/stalled slot causes WAL (and catalog) bloat
    that has to be monitored.
  (Debezium docs, "Debezium connector for PostgreSQL": https://debezium.io/documentation/reference/stable/connectors/postgresql.html;
  WAL config requirement corroborated via Red Hat's build of Debezium user guide)
- On top of the raw CDC stream, Debezium ships an **Outbox Event Router SMT**
  (`io.debezium.transforms.outbox.EventRouter`) — a Kafka Connect Single Message Transform applied
  to the Debezium-produced change events. With default config it:
  - Reads an `aggregatetype` column from the outbox row and routes the Kafka record to a topic
    named `outbox.event.<aggregatetype>`.
  - Uses `aggregateid` as the outgoing Kafka record key (preserves per-aggregate ordering within a
    partition).
  - Uses the outbox row's `payload` column as the Kafka record value.
  - Default payload serialization is JSON; the source `payload` column should be `jsonb` for that
    path.
  (Debezium docs, "Outbox Event Router": https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html;
  confirmed via Confluent's mirrored EventRouter SMT reference:
  https://docs.confluent.io/kafka-connectors/transforms/current/eventrouter.html)
- Required infra to run this at all: a **Kafka Connect worker process** running the Debezium
  Postgres connector plugin, connector JSON config (source DB creds, `table.include.list` scoped
  to the outbox table — otherwise the SMT errors on non-outbox rows it wasn't meant to see), and
  the SMT chain config. Optionally a schema registry if using Avro/Protobuf instead of default JSON.
- Net effect: near-real-time propagation (no poll interval to wait out) and zero added query load
  on the primary table (reads WAL, not the table) — at the cost of running and operating an
  entirely separate service (Kafka Connect) plus Postgres-level replication-slot lifecycle
  management.

## Cost Comparison

| | Scheduled Poller | Debezium/CDC |
|---|---|---|
| New containers in `docker-compose` | 0 (lives inside the existing Spring Boot app) | Kafka Connect worker + Debezium connector config (and Kafka Connect itself needs the Connect REST API to register the connector — usually a `curl`/init step) |
| Postgres config changes | None | `wal_level=logical`, replication slot provisioning, ongoing slot monitoring |
| Added latency | = polling interval (tunable, e.g. 500ms–5s) | Near-real-time (sub-second, log-tailing) |
| Added DB query load | One `SELECT ... FOR UPDATE SKIP LOCKED` per interval — negligible at portfolio scale (0–1 matching rows most cycles) | ~None (reads WAL stream, not the table) |
| Operational surface to explain/demo | One `@Scheduled` method, plain SQL, easy to read in 30 seconds | Kafka Connect + SMT config + replication slot — several new concepts a reviewer has to parse before reaching the actual reliability story (retry/DLQ/circuit-breaker) |
| Failure modes to reason about | Missed schedule tick, stuck lock, batch size tuning | All of the above, plus replication slot lag/bloat, connector crash/restart semantics, Connect worker offset management |
| What it's built to prove | You understand outbox delivery semantics, row-claiming under concurrency, at-least-once handling | You can operate a CDC pipeline — a different (adjacent) skill from the reliability/retry/DLQ story this project is about |

## Recommendation (for this project)

**Use a Spring `@Scheduled` polling publisher with `SELECT ... FOR UPDATE SKIP LOCKED`, not
Debezium/CDC, for Reliable Webhooks.**

Reasoning:

1. **Scale doesn't justify it.** This is a portfolio demo generating a handful to a few hundred
   events, not a production system with real throughput or latency SLAs. The poller's extra query
   load and interval-bound latency (pick something in the 500ms–2s range for a snappy demo) are
   immaterial at this scale — CDC's efficiency advantage only starts to matter well beyond where
   this project lives.
2. **One-command `docker-compose up` stays intact.** The poller needs zero new containers — it's
   a method inside the Spring Boot app already in the stack. Debezium requires standing up a
   Kafka Connect worker, registering a connector via its REST API, and flipping Postgres into
   logical-replication mode — that's 2-3 more moving parts and startup-ordering constraints
   (Kafka Connect must come up after Kafka and Postgres, then the connector must be registered
   before events flow) added purely for a mechanism that isn't the point of the demo.
3. **It keeps the reviewer's attention on what the project is actually demonstrating.** The
   portfolio value here is the *reliability chain* — outbox write → publish → consumer retry →
   DLQ → circuit breaker. A visible, readable `@Scheduled` poller with an explicit
   `FOR UPDATE SKIP LOCKED` claim query is something a reviewer can read top-to-bottom in under a
   minute and immediately see the concurrency-safety reasoning. Debezium's SMT/Connect
   configuration is a legitimate, valuable pattern, but it's a *different* skill being
   demonstrated (CDC pipeline operations) that would compete with, not reinforce, the retry/DLQ/
   circuit-breaker story this project is built to showcase.
4. **Genuine trade-off, worth naming explicitly in the repo (e.g. in an ADR or README callout):**
   CDC is the better choice once you have real throughput, need strict low-latency propagation, or
   want to avoid any polling load on a busy primary — and it's worth stating that outgrowing the
   poller and swapping in Debezium later is a natural, well-understood evolution path, not a dead
   end. That framing itself is worth more to a reviewer than actually running Debezium for a
   3-event demo would be.

Concrete defaults to implement: `fixedDelay = 1000ms` (or make it configurable), `LIMIT 50`
per batch, `SELECT ... FOR UPDATE SKIP LOCKED` on an indexed `published_at IS NULL` (or status)
column, mark-published in the same transaction as the Kafka send acknowledgment (or use a
separate "processing" state with a timeout to handle crash-recovery, per standard outbox-relay
practice).

## References

- Postgres `SELECT` reference (locking clauses incl. `FOR UPDATE SKIP LOCKED`):
  https://www.postgresql.org/docs/current/sql-select.html
- Debezium: Outbox Event Router SMT reference:
  https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html
- Debezium: Postgres connector (logical decoding, `wal_level=logical`, replication slots):
  https://debezium.io/documentation/reference/stable/connectors/postgresql.html
- Confluent mirror of the Debezium EventRouter SMT docs:
  https://docs.confluent.io/kafka-connectors/transforms/current/eventrouter.html
- microservices.io — Pattern: Transactional outbox:
  https://microservices.io/patterns/data/transactional-outbox.html
- microservices.io — Pattern: Polling publisher:
  https://microservices.io/patterns/data/polling-publisher.html
- microservices.io — Pattern: Transaction log tailing:
  https://microservices.io/patterns/data/transaction-log-tailing.html
