# ADR-0010: Simulated-consumer shape

## Status

Accepted. Settled in [wayfinder ticket #13](https://github.com/luizwalber/reliable-webhooks/issues/13).

## Context

The demo needs a way to show retry/DLQ/circuit-breaker behavior without depending on a real third-party endpoint being flaky on cue. The brief implied "simulated consumers" but not their shape: a new first-class resource type, or something reusing what already exists.

## Decision

- **No new resource type** — a simulated consumer is just a normal Endpoint (via the existing `POST /v1/endpoints`) whose URL points at a standalone simulator service/container.
- **Route-per-behavior**: the simulator exposes `/simulate/success`, `/simulate/error-500`, `/simulate/timeout`, `/simulate/intermittent`. "Choosing a behavior" means registering that specific route as an Endpoint URL — enables demoing several endpoints with different behaviors simultaneously, which is the multi-endpoint isolation story the fan-out model ([ADR-0001](0001-fan-out-and-delivery-resource.md)) has been building toward.
- **Entirely out-of-band**: the simulator is not documented in `openapi.yaml` — it's internal demo infrastructure, not part of the public contract.
- **Zero new frontend UI**: registering a simulated endpoint uses the exact same endpoint-registration flow as any real URL. No simulator control panel.

## Consequences

- No `openapi.yaml` changes required for this decision.
- The simulator is one more Docker Compose service ([ADR-0011](0011-docker-compose-topology.md)), internal-only (no host-exposed port), called only by the backend's delivery workers, never directly by a human or the frontend.
- Demo scripts/READMEs can just say "register an endpoint pointing at `http://simulator:PORT/simulate/error-500`" — no bespoke simulator API to document.
