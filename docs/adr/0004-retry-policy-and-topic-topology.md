# ADR-0004: Retry policy and Kafka topic topology

## Status

Accepted. Raised to fidelity by the [state-machine & retry-topology prototype](https://github.com/luizwalber/reliable-webhooks/issues/2) (three candidate topologies, see `prototypes/wf2-state-machine-retry-topology/README.md` on the merged history), locked in [wayfinder ticket #9](https://github.com/luizwalber/reliable-webhooks/issues/9).

## Context

The brief fixed the shape (Kafka main topic + staggered retry topics by delay band + a DLQ topic, exponential backoff + jitter, a max-attempt limit) but not the concrete topic count, delay values, or how a worker decides where to republish. The prototype sketched three candidates: (A) a small fixed set of delay-band topics, (B) one topic per attempt number, (C) a single delayed-retry topic with a `not_before` header. Candidate A won — cheapest to reason about and monitor, only 5 topics ever, and delay-banding (not exact per-attempt delay) is an acceptable trade-off for a demo.

## Decision

- **Topics**: `webhook.delivery.main`, `webhook.delivery.retry.30s`, `webhook.delivery.retry.5m`, `webhook.delivery.retry.30m`, `webhook.delivery.dlq` — fixed set, topic names describe delay bands but are decoupled from the actual configured delay values.
- **Delays**: configurable via Spring property (e.g. `webhook.retry.delays`), with fast demo-profile defaults (~10s / 30s / 2m) instead of the topic names' illustrative values — the full retry ladder is observable in a few minutes, not 30+.
- **Jitter**: ±20%, applied once when a retry is scheduled, stored as the Delivery's `nextAttemptAt`.
- **Max attempts**: 4 total (1 immediate + 3 retries) before a Delivery goes `DEAD`.
- **Kafka message**: value represents a Delivery-attempt (`deliveryId`, `eventId`, `endpointId`, `attemptNumber`); key = `endpointId` — reinforces per-endpoint ordering/isolation, consistent with the circuit breaker being keyed the same way ([ADR-0006](0006-circuit-breaker.md)).
- **Worker mechanics**: the consumer reads `nextAttemptAt` from a message header and sleeps the consuming thread until due before processing — a documented demo-scale simplification. A production system would need a real delay-queue mechanism (e.g. a scheduler service or Kafka Streams punctuator) instead of blocking a consumer thread.

## Consequences

- Only 5 topics, ever — cheap to monitor, no dynamic topic creation.
- Delay is banded, not exact: attempt 3 always waits the same ~2m regardless of a computed backoff curve. Acceptable; the backoff *story* (delay grows per attempt) is still visible.
- Sleep-until-due ties up a consumer thread per in-flight retry — fine at demo concurrency, explicitly not production-ready.
- `openapi.yaml`'s Delivery/Attempt schemas already carry `nextAttemptAt`, `attemptCount`, and `topic` consistent with this design — no spec changes were needed when this ADR was settled.
