CREATE TABLE products (
    sku                VARCHAR(64) PRIMARY KEY,
    name               VARCHAR(255) NOT NULL,
    available_quantity INT          NOT NULL CHECK (available_quantity >= 0),
    version            BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE reservations (
    id         BIGSERIAL PRIMARY KEY,
    order_id   UUID        NOT NULL,
    sku        VARCHAR(64) NOT NULL REFERENCES products (sku),
    quantity   INT         NOT NULL CHECK (quantity > 0),
    status     VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (order_id, sku)
);
CREATE INDEX idx_reservations_order ON reservations (order_id);

-- Transactional outbox
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   VARCHAR(64)  NOT NULL,
    topic          VARCHAR(128) NOT NULL,
    event_type     VARCHAR(64)  NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_id);

-- Idempotent consumers
CREATE TABLE processed_events (
    event_id     UUID        NOT NULL,
    consumer     VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, consumer)
);
