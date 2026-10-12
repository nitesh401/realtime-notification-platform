# Real-Time Notification Platform

A locally runnable notification system: **REST API → PostgreSQL + transactional outbox → Kafka → worker → email (MailHog) / WebSocket push → live browser UI**, with retries, dead-lettering, idempotency and delivery tracking.

> **Verification status (read this first):** this code was written in a sandbox that had Java 21 but **no Maven, no Docker and no network**. The only things actually executed there: the `common` module compiled, the frontend JS passed `node --check`, and every Java file was syntax-parsed. **Nothing was built with Maven, no tests were run, and the containers were never started.** Expect to fix a few compile/test issues on first build; see [docs/local-testing.md](docs/local-testing.md) for what to run and what to look at.

## What's inside

| Module | Port | Responsibility |
|---|---|---|
| `notification-api-service` | 8080 | REST API, JWT auth, preferences, templates, idempotency, transactional outbox + relay, **WebSocket gateway** (STOMP), demo UI, Swagger |
| `notification-worker-service` | 8081 | Kafka consumers, email dispatch, retry topics, dead-letter handling + replay, per-channel delivery tracking |
| `common` | – | Shared event contracts (Kafka payloads) and enums |
| infra (compose) | 5432 / 9092 / 1025 / 8025 | PostgreSQL 16, Kafka 3.8 (KRaft, single node), MailHog |

Redis is intentionally **not** used (see [docs/architecture.md](docs/architecture.md#why-no-redis)). SMS/push are extension points only.

## Prerequisites
Java 21, Maven 3.9+, Docker Desktop (or Docker Engine + Compose v2).

```powershell
# Windows PowerShell
java -version        # 21.x
mvn -v               # 3.9+, using Java 21
docker --version; docker compose version
```
```bash
# macOS / Linux
java -version && mvn -v && docker --version && docker compose version
```

## Quick start (everything in Docker)

```powershell
cd realtime-notification-platform
Copy-Item .env.example .env
# Generate a JWT secret and paste it into .env as JWT_SECRET=...
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])
notepad .env

docker compose up -d --build        # first build downloads Maven deps; later runs are cached
docker compose ps                   # wait until postgres/kafka are healthy and the apps are up
docker compose logs -f notification-api notification-worker
```
```bash
cp .env.example .env && sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48)|" .env
docker compose up -d --build
```

* Demo UI: <http://localhost:8080/> · Swagger UI: <http://localhost:8080/swagger-ui.html> · MailHog: <http://localhost:8025>
* Health: <http://localhost:8080/actuator/health/readiness>, <http://localhost:8081/actuator/health/readiness>
* Demo users (when `SEED_DEMO_USERS=true`): `alice`, `bob`, `admin`, `bounce` — password = your `DEMO_PASSWORD`. `admin` may notify other users. `bounce`'s email (`@fail.example.test`) always fails, which demonstrates retries/dead-lettering.

Database migrations (Flyway) run automatically when each service starts; the `api` and `worker` schemas and their DB roles are created by `docker/postgres/init/01-init.sh` on first start of an empty volume. (If you ever change DB passwords in `.env`, run `docker compose down -v` once so the init script runs again.)

## Development mode (infra in Docker, apps from Maven)

```powershell
docker compose up -d postgres kafka mailhog
mvn -q clean package -DskipTests

# load .env into the current PowerShell session
Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object { $k,$v = $_ -split '=',2; Set-Item "env:$k" $v }

# terminal 1
java -jar notification-api-service/target/notification-api-service-1.0.0.jar
# terminal 2
java -jar notification-worker-service/target/notification-worker-service-1.0.0.jar
```
```bash
set -a; . ./.env; set +a
docker compose up -d postgres kafka mailhog && mvn -q clean package -DskipTests
java -jar notification-api-service/target/notification-api-service-1.0.0.jar &
java -jar notification-worker-service/target/notification-worker-service-1.0.0.jar &
```

## Try it

1. Open <http://localhost:8080/>, sign in as `alice`. The badge is zero and the pill says **live**.
2. **Developer panel** → pick a template or write a message → tick *In-app* (and *Email* to see it in MailHog) → **Send**. The notification appears in the bell with a toast, no refresh.
3. Send again with the same *Idempotency-Key*: "Duplicate request … nothing new created".
4. Watch Kafka processing: `docker compose logs -f notification-worker`; delivery status moves `PENDING → PROCESSING → SENT → DELIVERED` (DELIVERED once the browser acks).
5. Failure demos (retries, dead-letter, Kafka/DB outage, replay): [docs/failure-scenarios.md](docs/failure-scenarios.md).

curl example:
```bash
TOKEN=$(curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"<DEMO_PASSWORD>"}' | jq -r .token)
curl -s localhost:8080/api/v1/notifications -H "Authorization: Bearer $TOKEN" \
  -H 'Idempotency-Key: demo-1' -H 'Content-Type: application/json' \
  -d '{"subject":"Hello","body":"From curl","category":"INFO","channels":["IN_APP","EMAIL"]}'
```

## Tests
```powershell
mvn -q test                       # unit tests only need Java; integration tests need Docker running
mvn -q -pl notification-api-service test
```
Details and per-requirement test mapping: [docs/local-testing.md](docs/local-testing.md).

## Shut down / clean up
```powershell
docker compose down            # stop, keep data
docker compose down -v         # stop and DELETE the Postgres and Kafka volumes
```

## Documentation
[architecture](docs/architecture.md) · [system design](docs/system-design.md) · [API guide](docs/api-guide.md) · [database design](docs/database-design.md) · [failure scenarios](docs/failure-scenarios.md) · [local testing](docs/local-testing.md) · [design reference review](docs/design-reference-review.md)

## Essential vs optional
Essential: PostgreSQL, Kafka, api + worker. Optional: MailHog (email channel falls back to retry/dead-letter without it), Swagger UI, Prometheus endpoints, the demo UI. Not included: Redis, SMS, push.
