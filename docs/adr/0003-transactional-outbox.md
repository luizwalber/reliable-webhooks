# ADR-0003: Transactional outbox via scheduled poller, not CDC

## Status

Accepted. Settled in [wayfinder research ticket #3](https://github.com/luizwalber/reliable-webhooks/issues/3).

## Context

The brief fixed the transactional-outbox pattern (write the Event and an outbox row in the same DB transaction as the business op, then publish to Kafka out-of-band) but not the publication mechanism: a polling loop against Postgres, or CDC via Debezium reading the WAL.

## Decision

A `@Scheduled` poller (`fixedDelay=1000`ms as the default) selects unpublished outbox rows with `SELECT ... FOR UPDATE SKIP LOCKED`, publishes each to Kafka, then marks it published — not Debezium/CDC.

Rationale: Debezium adds a Kafka Connect deployment, WAL-level configuration, and operational surface area disproportionate to this project's demo scale and single-instance backend. The poller is a few dozen lines of Spring code, trivially testable, and `FOR UPDATE SKIP LOCKED` already gives safe concurrent polling if the backend ever runs more than one instance.

## Consequences

- A crash between "publish to Kafka" and "mark row published" produces a duplicate Kafka message — an accepted, not designed-around, possibility. This is exactly why delivery-time dedup is the real duplicate-prevention mechanism, not the outbox itself (see [ADR-0002](0002-idempotency-and-delivery-guarantees.md)).
- Publish latency is bounded by the poll interval (~1s default), not real-time — acceptable for a demo, called out as a trade-off versus CDC's near-zero latency.
- No Kafka Connect / Debezium container needed in Docker Compose (see [ADR-0011](0011-docker-compose-topology.md)).
