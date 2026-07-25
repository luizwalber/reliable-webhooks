-- Full resource schema per docs/adr/0001, 0002, 0003, 0005, 0007, 0008.
-- Only `events`, `endpoints`, and `outbox` are written to by this slice
-- (docs/adr/0002-idempotency-and-delivery-guarantees, spec #17).
-- `deliveries` and `attempts` are created now so later slices (outbox
-- poller + delivery workers) are additive migrations, not a schema rewrite.

CREATE TABLE events (
    id               UUID PRIMARY KEY,
    event_type       VARCHAR(255),
    producer_id      VARCHAR(255) NOT NULL,
    idempotency_key  VARCHAR(255) NOT NULL,
    state            VARCHAR(20)  NOT NULL,
    payload          JSONB        NOT NULL,
    received_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE endpoints (
    id                     UUID PRIMARY KEY,
    url                    TEXT         NOT NULL,
    description            TEXT,
    secret                 VARCHAR(255) NOT NULL,
    circuit_breaker_state  VARCHAR(20)  NOT NULL DEFAULT 'CLOSED',
    success_count          INTEGER      NOT NULL DEFAULT 0,
    dead_count             INTEGER      NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE deliveries (
    id               UUID PRIMARY KEY,
    event_id         UUID         NOT NULL REFERENCES events(id),
    endpoint_id      UUID         NOT NULL REFERENCES endpoints(id),
    state            VARCHAR(20)  NOT NULL,
    attempt_count    INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_deliveries_event_id ON deliveries(event_id);
CREATE INDEX idx_deliveries_endpoint_id ON deliveries(endpoint_id);
CREATE INDEX idx_deliveries_state ON deliveries(state);

CREATE TABLE attempts (
    id                UUID PRIMARY KEY,
    delivery_id       UUID         NOT NULL REFERENCES deliveries(id),
    attempt_number    INTEGER      NOT NULL,
    outcome           VARCHAR(20),
    http_status_code  INTEGER,
    topic             VARCHAR(255),
    started_at        TIMESTAMPTZ  NOT NULL,
    finished_at       TIMESTAMPTZ
);

CREATE INDEX idx_attempts_delivery_id ON attempts(delivery_id);

CREATE TABLE outbox (
    id            UUID PRIMARY KEY,
    event_id      UUID         NOT NULL REFERENCES events(id),
    payload       JSONB        NOT NULL,
    published     BOOLEAN      NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox(published) WHERE NOT published;
