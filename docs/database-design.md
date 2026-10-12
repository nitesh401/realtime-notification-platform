# Database design

PostgreSQL 16, one database, **two schemas** with separate login roles (`api_svc`, `worker_svc`). Flyway migrations: `notification-api-service/src/main/resources/db/migration`, `notification-worker-service/.../db/migration`. Hibernate runs with `ddl-auto: validate`.

```mermaid
erDiagram
  users ||--o| notification_preferences : has
  users ||--o{ notifications : "receives (recipient_id)"
  users ||--o{ notifications : "creates (created_by)"
  notification_templates ||--o{ notifications : "optional template_id (by value)"
  notifications ||--o| outbox_events : "aggregate_id (by value)"
  delivery_records ||--o{ delivery_attempts : has
  delivery_records }o..|| notifications : "notification_id (no FK: other schema)"
  dead_letters }o..o| notifications : "notification_id (no FK)"
  users {
    uuid id PK
    string username UK
    string email
    string password_hash
    string role
    timestamptz created_at
  }
  notification_preferences {
    uuid user_id PK, FK
    bool in_app_enabled
    bool email_enabled
    bool info_enabled
    bool success_enabled
    bool warning_enabled
    bool error_enabled
    timestamptz updated_at
    bigint version
  }
  notification_templates {
    string id PK
    string subject_template
    text body_template
    string category
    bool mandatory
  }
  notifications {
    uuid id PK
    uuid recipient_id FK
    uuid created_by FK
    string category
    string subject
    text body
    string template_id
    bool mandatory
    bool channel_in_app
    bool channel_email
    string delivery_status
    timestamptz status_updated_at
    string idempotency_key
    timestamptz read_at
    timestamptz archived_at
    timestamptz created_at
    bigint version
  }
  outbox_events {
    uuid id PK
    uuid aggregate_id
    string topic
    string partition_key
    text payload
    string status
    int attempts
    timestamptz next_attempt_at
    string last_error
    timestamptz published_at
  }
  delivery_records {
    uuid id PK
    uuid notification_id
    uuid event_id
    uuid recipient_id
    string channel
    string status
    int attempt_count
    string last_error
    timestamptz sent_at
    timestamptz delivered_at
    bigint version
  }
  delivery_attempts {
    uuid id PK
    uuid delivery_id FK
    int attempt_no
    string outcome
    string error
    timestamptz started_at
    timestamptz finished_at
  }
  dead_letters {
    uuid id PK
    uuid notification_id
    string partition_key
    string source_topic
    text payload
    string reason
    string status
    timestamptz created_at
    timestamptz replayed_at
  }
```

## Entities
| Table (schema) | PK | Required / status fields | Constraints | Notes |
|---|---|---|---|---|
| users (api) | `id` uuid | username, email, password_hash (BCrypt), role, created_at | `username` unique | local identity mechanism |
| notification_preferences (api) | `user_id` (FK users, cascade) | six booleans, `version` | – | missing row = all enabled |
| notification_templates (api) | `id` varchar | subject/body templates, category, mandatory | – | seeded by `V2` |
| notifications (api) | `id` uuid | recipient, creator, category, subject, body, channel flags, `delivery_status`, `status_updated_at`, timestamps, `version` | **unique partial** `(created_by, recipient_id, idempotency_key) WHERE key IS NOT NULL` | `read_at`/`archived_at` = user state |
| outbox_events (api) | `id` uuid (= event id) | topic, partition_key, payload, `status` PENDING/PUBLISHED/FAILED, attempts, next_attempt_at | – | written in the same tx as the notification |
| delivery_records (worker) | `id` uuid | notification_id, channel, `status`, attempt_count, `version` | **unique** `(notification_id, channel)` | idempotency anchor |
| delivery_attempts (worker) | `id` uuid | delivery_id FK, attempt_no, outcome SUCCESS/FAILURE/HANDOFF, started/finished | – | full attempt history |
| dead_letters (worker) | `id` uuid | source_topic, payload, status PENDING/REPLAYED | – | notification_id null for malformed |

Idempotency records are folded into `notifications` (the unique index) instead of a separate table: the key *is* a property of the logical notification and the constraint makes the check race-free.

## Indexes
* `idx_notifications_recipient_created (recipient_id, created_at DESC, id DESC)`: serves "my notifications, newest first" and keyset pagination.
* `idx_notifications_unread (recipient_id) WHERE read_at IS NULL AND archived_at IS NULL AND channel_in_app`: tiny partial index for the badge count / unread filter (`unread-count`).
* `uq_notifications_idempotency`: duplicate prevention.
* `idx_outbox_pending (created_at) WHERE status='PENDING'`: relay scans only pending rows.
* `uq_delivery_notification_channel`, `idx_delivery_status` (partial on non-terminal states), `idx_attempts_delivery`, `idx_dead_letters_status`.

## Verify locally
```bash
docker compose exec postgres psql -U postgres -d notifications -c "\dt api.*" -c "\dt worker.*"
docker compose exec postgres psql -U postgres -d notifications -c "select status,count(*) from api.outbox_events group by 1"
```
