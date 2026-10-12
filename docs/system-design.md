# System design

## Requirements
Functional: create (single/multi/template), list with pagination and filters, read/read-all/archive/unread-count, preferences, real-time in-app push, email, delivery tracking, retries, DLQ with replay. Non-functional: at-least-once, no lost accepted notifications, user isolation, observable, runs entirely locally.

## Capacity assumptions (local scale vs reference)
The reference article assumes ~250M notifications/day (~3k/s average, ~17k/s peak). This build targets a laptop: a few hundred notifications/s is the realistic ceiling of a single api + single worker + single-node Kafka/Postgres, and was **not load-tested**.

| Quantity | Estimate |
|---|---|
| Row size (notification) | ~0.5-1 KB incl. indexes |
| 1M notifications/day | ~0.5-1 GB/day in `notifications`, ~same in `outbox_events` until purged |
| Partitions | 3 per topic (max 3 parallel consumers per group) |

Note: `outbox_events` rows are kept after publishing (audit); add a purge job for `PUBLISHED` rows older than N days before running at volume.

## Scaling path
1. api: stateless except WebSocket sessions; run N instances behind an LB (see gateway note in architecture.md). Relay is safe with N instances (`SKIP LOCKED`).
2. worker: add instances up to the partition count; raise partitions beforehand (key ordering changes only when partition count changes).
3. Postgres: read replicas for history; partition `notifications` by time; keyset pagination (`(created_at,id) < (?,?)`) replaces offset paging past ~10k rows per user.
4. Kafka: RF 3, `min.insync.replicas=2`, per-channel topics.

## Trade-offs
| Decision | Gain | Cost |
|---|---|---|
| Outbox polling | Simple, no extra infra | ≤500 ms added latency, polling load |
| Offset paging | Simple, supports page jumps | Slower on deep pages |
| Non-blocking retry topics | Partition never blocked by a failing record | Retried events can be reordered |
| Idempotency by DB constraint | Correct under concurrency | Needs a DB round trip per request |
| Gateway in api | Fewer parts | Coupled scaling |
| Preferences evaluated at creation | Single decision point | Later preference changes don't affect in-flight events |
