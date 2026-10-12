# Design reference review: AlgoMaster "Design a Scalable Notification Service"

**Accessibility:** the article at <https://algomaster.io/learn/system-design-interviews/design-notification-service> was fetched successfully (page dated "Updated June 23, 2026"). Findings below are from that text; anything not in it is labelled *general principle*.

## What the article says (summary, own words)
* **Requirements:** multi-channel (email, SMS, push, in-app), transactional/promotional/system types, scheduled delivery, per-user rate limiting of promotions, retries; non-functionals: scale (assumed 250M notifications/day, ~17k/s peak), HA, at-least-once (exactly-once "for certain use cases"), low latency.
* **Components:** Notification Service (validate/authenticate, entry point), User Preference Service, Scheduler Service, Notification Queue (Kafka/RabbitMQ/SQS), per-channel Channel Processors, and storage (relational for logs/status, NoSQL for preferences, blob for attachments).
* **Flow:** request → validate → fetch preferences → optional scheduling → template-based formatting → one topic per channel → processors consume and call providers → delivery status logged in `notification_logs`.
* **Follow-ups:** exponential-backoff retries then a dead-letter queue with manual reprocessing; horizontal scaling, sharding/partitioning, caching preferences; replication; monitoring; OAuth/RBAC and rate limiting; archiving.

## Adopted
| Idea | Where |
|---|---|
| Thin API in front, queue as buffer, async channel processors | api → Kafka → worker |
| Preferences consulted before dispatch; opt-outs respected | `ChannelResolver` at creation time |
| Templates per message | `notification_templates`, `TemplateRenderer` |
| At-least-once delivery | outbox + manual offsets + idempotent consumers |
| Retry with exponential backoff, then DLQ + admin reprocessing | `@RetryableTopic` + `dead_letters` + `/admin/dead-letters/{id}/replay` |
| In-app via WebSocket to active sessions | STOMP gateway in the api service |
| Delivery log/status in a relational store | `delivery_records`, `delivery_attempts` |
| Metrics for success/failure/latency | Micrometer counters + timer |

## Simplified for a local build
* **One shared channel topic** (`notification.events`) with per-channel work done inside the worker, instead of one topic and processor per channel. Reason: two channels; per-channel topics add operational surface without teaching more. Extension: split by channel when you add SMS/push with different scaling.
* **Preferences in PostgreSQL** (not a NoSQL store or a separate service). Reason: tiny volume, strong consistency, one fewer moving part.
* **Preference service folded into the api** (not a separate microservice).
* **Realtime gateway integrated into the api service** (allowed by the brief); trade-offs in architecture.md.
* **No caching layer** for preferences (a primary-key lookup is cheap at this scale).
* **Fixed-window in-memory rate limiter** per client address, not per-user promotional quotas.
* **Single-node Kafka**, single Postgres.

## Out of scope (production concerns that remain)
Scheduled delivery; per-user daily frequency caps for promotional messages; SMS/push providers and device-token management; blob storage for attachments; multi-region/multi-AZ replication; sharding and time-partitioned log tables; archiving; OAuth/OIDC identity provider; centralised logging and alerting stack; per-channel priority lanes; delivery webhooks/bounce handling from real email providers.

## Differences worth noting
* The article's "success from provider = delivered" is deliberately **not** copied: here SMTP acceptance is `SENT`, and `DELIVERED` exists only for in-app with a browser acknowledgement.
* The article does not address the database/Kafka dual write; this project adds a transactional outbox.
* The article's scale numbers are used only as an order-of-magnitude reference in [system-design.md](system-design.md).
