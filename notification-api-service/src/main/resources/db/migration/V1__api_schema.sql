-- Schema owned exclusively by notification-api-service (schema "api").

CREATE TABLE users (
    id            uuid PRIMARY KEY,
    username      varchar(64)  NOT NULL UNIQUE,
    email         varchar(255) NOT NULL,
    password_hash varchar(100) NOT NULL,
    role          varchar(16)  NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE notification_templates (
    id               varchar(64)  PRIMARY KEY,
    subject_template varchar(255) NOT NULL,
    body_template    text         NOT NULL,
    category         varchar(16)  NOT NULL,
    mandatory        boolean      NOT NULL DEFAULT false,
    created_at       timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE notification_preferences (
    user_id         uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    in_app_enabled  boolean     NOT NULL DEFAULT true,
    email_enabled   boolean     NOT NULL DEFAULT true,
    info_enabled    boolean     NOT NULL DEFAULT true,
    success_enabled boolean     NOT NULL DEFAULT true,
    warning_enabled boolean     NOT NULL DEFAULT true,
    error_enabled   boolean     NOT NULL DEFAULT true,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         bigint      NOT NULL DEFAULT 0
);

CREATE TABLE notifications (
    id                uuid PRIMARY KEY,
    recipient_id      uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_by        uuid         NOT NULL REFERENCES users (id),
    category          varchar(16)  NOT NULL,
    subject           varchar(255) NOT NULL,
    body              text         NOT NULL,
    template_id       varchar(64),
    mandatory         boolean      NOT NULL DEFAULT false,
    channel_in_app    boolean      NOT NULL,
    channel_email     boolean      NOT NULL,
    delivery_status   varchar(16)  NOT NULL,
    status_updated_at timestamptz  NOT NULL,
    idempotency_key   varchar(100),
    correlation_id    varchar(64),
    read_at           timestamptz,
    archived_at       timestamptz,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    version           bigint       NOT NULL DEFAULT 0
);

-- Logical duplicate prevention: same creator + recipient + idempotency key can only exist once.
CREATE UNIQUE INDEX uq_notifications_idempotency
    ON notifications (created_by, recipient_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Recipient history pagination (newest first).
CREATE INDEX idx_notifications_recipient_created
    ON notifications (recipient_id, created_at DESC, id DESC);

-- Unread badge / unread filter: tiny partial index, only rows that can be unread.
CREATE INDEX idx_notifications_unread
    ON notifications (recipient_id)
    WHERE read_at IS NULL AND archived_at IS NULL AND channel_in_app;

CREATE TABLE outbox_events (
    id              uuid PRIMARY KEY,
    aggregate_id    uuid         NOT NULL,
    topic           varchar(128) NOT NULL,
    partition_key   varchar(64)  NOT NULL,
    payload         text         NOT NULL,
    status          varchar(16)  NOT NULL,
    attempts        integer      NOT NULL DEFAULT 0,
    next_attempt_at timestamptz  NOT NULL DEFAULT now(),
    last_error      varchar(500),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    published_at    timestamptz
);

-- The relay only ever scans pending rows ordered by creation time.
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';
