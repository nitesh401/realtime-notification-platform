-- Schema owned exclusively by notification-worker-service (schema "worker").
-- It deliberately has NO foreign keys into the api schema: services are independently deployable and
-- only share events, never tables.

CREATE TABLE delivery_records (
    id              uuid PRIMARY KEY,
    notification_id uuid         NOT NULL,
    event_id        uuid         NOT NULL,
    recipient_id    uuid         NOT NULL,
    channel         varchar(16)  NOT NULL,
    status          varchar(16)  NOT NULL,
    attempt_count   integer      NOT NULL DEFAULT 0,
    last_error      varchar(500),
    detail          varchar(100),
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    sent_at         timestamptz,
    delivered_at    timestamptz,
    version         bigint       NOT NULL DEFAULT 0
);

-- One delivery per notification per channel: the idempotency anchor for duplicate/replayed events.
CREATE UNIQUE INDEX uq_delivery_notification_channel ON delivery_records (notification_id, channel);
CREATE INDEX idx_delivery_status ON delivery_records (status) WHERE status IN ('RETRYING', 'PROCESSING', 'DEAD_LETTERED');

CREATE TABLE delivery_attempts (
    id          uuid PRIMARY KEY,
    delivery_id uuid        NOT NULL REFERENCES delivery_records (id) ON DELETE CASCADE,
    attempt_no  integer     NOT NULL,
    outcome     varchar(16) NOT NULL,
    error       varchar(500),
    started_at  timestamptz NOT NULL,
    finished_at timestamptz NOT NULL
);
CREATE INDEX idx_attempts_delivery ON delivery_attempts (delivery_id, attempt_no);

CREATE TABLE dead_letters (
    id              uuid PRIMARY KEY,
    notification_id uuid,
    partition_key   varchar(64),
    source_topic    varchar(200) NOT NULL,
    payload         text         NOT NULL,
    reason          varchar(500),
    status          varchar(16)  NOT NULL,
    created_at      timestamptz  NOT NULL,
    replayed_at     timestamptz
);
CREATE INDEX idx_dead_letters_status ON dead_letters (status, created_at);
