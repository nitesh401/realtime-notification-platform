# API guide

Base URL `http://localhost:8080`. Interactive docs: `/swagger-ui.html` (click **Authorize**, paste the token). All bodies are JSON. Unknown JSON fields are rejected (`400 MALFORMED_REQUEST`). Every response carries `X-Correlation-Id` (send your own, 1-64 chars `[A-Za-z0-9._-]`, or one is generated).

## Error format (all endpoints)
```json
{ "timestamp":"2026-10-12T10:00:00Z", "status":400, "code":"VALIDATION_FAILED",
  "message":"Request validation failed", "path":"/api/v1/notifications",
  "correlationId":"6f1c...", "details":["subject: size must be between 0 and 255"] }
```
| Status | code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED`, `BAD_REQUEST`, `MALFORMED_REQUEST` | invalid body, missing template variables, unknown recipients/fields |
| 401 | `UNAUTHORIZED` | missing/invalid/expired token, bad login |
| 403 | `FORBIDDEN` | non-admin notifying others or sending `mandatory` |
| 404 | `NOT_FOUND` | unknown id **or someone else's notification** |
| 409 | `CONFLICT`, `CONCURRENT_MODIFICATION` | duplicate username, optimistic-lock clash |
| 429 | `RATE_LIMITED` | per-address limit (120/min API, 20/min auth) |
| 500 | `INTERNAL_ERROR` | unexpected; quote the correlationId |

## Auth
`POST /api/v1/auth/register` `{"username":"carol","email":"carol@example.test","password":"at-least-10-chars"}` → `201`
`POST /api/v1/auth/login` `{"username":"carol","password":"…"}` → `200`
```json
{ "token":"<jwt>", "expiresAt":"2026-10-12T11:00:00Z", "user":{"id":"…","username":"carol","email":"carol@example.test","role":"USER"} }
```
Usernames: 3-32 chars `[a-z0-9_]`. Self-registration is always role `USER`.

## Notifications (Bearer token required)
### POST /api/v1/notifications
Optional header `Idempotency-Key` (1-100 chars). Body (all optional unless noted):
```json
{ "recipients":["alice"], "templateId":"ORDER_SHIPPED", "variables":{"orderId":"A-17","eta":"Friday"},
  "subject":"…", "body":"…", "category":"INFO", "channels":["IN_APP","EMAIL"], "mandatory":false }
```
Rules: send either `templateId` **or** `subject`+`body`+`category`. `recipients` omitted = yourself; others require role ADMIN. `channels` default `["IN_APP"]`. `mandatory:true` requires ADMIN (templates flagged mandatory, e.g. `PASSWORD_CHANGED`, always bypass preferences).
`201` created / `200` when the key was already used:
```json
{ "duplicate":false, "notifications":[ { "id":"…","category":"INFO","subject":"Order A-17 has shipped","body":"…",
  "status":"PENDING","read":false,"readAt":null,"archived":false,"mandatory":false,"templateId":"ORDER_SHIPPED","createdAt":"…" } ] }
```
`status`: `PENDING` | `PROCESSING` | `SENT` | `DELIVERED` | `RETRYING` | `FAILED` | `DEAD_LETTERED` | `SUPPRESSED` (preferences removed every channel; nothing is delivered).

### GET /api/v1/notifications
Query: `page` (0), `size` (20, max 100), `category`, `status=ALL|READ|UNREAD`, `includeArchived=false`. Returns `{content:[…], page, size, totalElements, totalPages}` newest first, only in-app notifications.
### GET /api/v1/notifications/{id} · PATCH /api/v1/notifications/{id}/read · DELETE /api/v1/notifications/{id}
Get / mark read (idempotent) / archive (`204`). Another user's id → `404`.
### PATCH /api/v1/notifications/read-all → `{"updated":3}`
### GET /api/v1/notifications/unread-count → `{"count":2}`

## Preferences
`GET /api/v1/preferences`, `PUT /api/v1/preferences` (all six booleans required):
`{"inAppEnabled":true,"emailEnabled":false,"infoEnabled":true,"successEnabled":true,"warningEnabled":true,"errorEnabled":true}`

## Templates
`GET /api/v1/templates` → `[{"id":"WELCOME","subject":"Welcome, {{name}}!","body":"…","category":"SUCCESS","mandatory":false,"variables":["name"]}]`
Seeded: `WELCOME`, `ORDER_SHIPPED`, `PAYMENT_FAILED`, `PASSWORD_CHANGED` (mandatory).

## WebSocket (STOMP 1.2 over plain WebSocket)
`ws://localhost:8080/ws`. CONNECT frame header `Authorization: Bearer <jwt>`. Subscribe to `/user/queue/notifications` (the only allowed subscription). Frames: `{id,category,subject,body,createdAt}`. Acknowledge with `SEND /app/ack {"notificationId":"…"}` (moves in-app delivery to `DELIVERED`). Invalid token, other destinations → ERROR frame + disconnect. Clients should de-duplicate by `id`.

## Worker admin (port 8081, header `X-Admin-Key: <WORKER_ADMIN_KEY>`)
`GET /admin/dead-letters` · `POST /admin/dead-letters/{id}/replay`. Actuator: `/actuator/health`, `/actuator/prometheus` on both services (api: ADMIN token required except health/info).
