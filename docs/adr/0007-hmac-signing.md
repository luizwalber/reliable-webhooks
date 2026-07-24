# ADR-0007: HMAC signing scheme

## Status

Accepted. Settled in [wayfinder ticket #7](https://github.com/luizwalber/reliable-webhooks/issues/7).

## Context

HMAC-signed payloads were fixed by the brief; the concrete algorithm, header format, replay handling, and secret lifecycle were not.

## Decision

- **Algorithm & signed content**: HMAC-SHA256 over the canonical string `{timestamp}.{raw body}` — the timestamp is cryptographically bound into the signature, not sent unsigned.
- **Headers**: `X-Webhook-Signature: t=<unix-ts>,v1=sha256=<hex>` (Stripe-style, versioned scheme prefix, allows future algorithm changes without breaking the header shape). The delivery/event identifier rides separately in `X-Webhook-Delivery-Id` — a distinct concern (dedup, see [ADR-0002](0002-idempotency-and-delivery-guarantees.md)) from authenticity.
- **Replay protection**: 5-minute tolerance window, documented as consumer-side guidance only — the platform can't enforce how a third-party consumer verifies signatures; this is advisory in the README/OpenAPI docs.
- **Secret provisioning**: a random secret is generated server-side at endpoint registration and shown once in the frontend UI. Stored retrievably (not hashed) — the platform needs the plaintext to sign every outbound delivery; encryption-at-rest is a data-model implementation detail, not a contract decision. Rotation is explicitly out of scope for this phase: one secret per endpoint, no rotation/versioning UI.

## Consequences

- Endpoint registration's response schema must return the secret exactly once (`EndpointCreated` in `openapi.yaml`) — it's never retrievable again via the API.
- The Endpoint entity needs a retrievable (not hashed) secret column in the data model.
- No secret-rotation code path to build; a compromised secret requires deleting and re-registering the endpoint.
