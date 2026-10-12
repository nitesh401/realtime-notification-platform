# Local testing

## Honest status
The test suite was **written but not executed** in the authoring sandbox (no Maven/Docker/network). Run it yourself and treat first-run failures as normal debugging, not as evidence the design is wrong. Unit tests need only Java; integration tests start PostgreSQL and Kafka with Testcontainers and therefore need Docker running.

## Commands
```powershell
docker info                                  # Docker must be running for integration tests
mvn -q clean verify                          # everything
mvn -q test -Dtest='*Test'                   # unit tests only (no Docker)
mvn -q -pl notification-api-service test -Dtest=NotificationApiIT
mvn -q -pl notification-worker-service test -Dtest=RetryAndDeadLetterIT
```
First run pulls `postgres:16-alpine` and `apache/kafka:3.8.0`.

## Requirement → test map
| # | Requirement | Test |
|---|---|---|
| 1 | Same idempotency key → one logical notification (also under concurrency) | `NotificationApiIT.sameIdempotencyKey…`, `concurrentRequestsWithSameKey…` |
| 2 | Unauthorised users cannot read others' notifications | `otherUsersCannotReadOrModifyMyNotification`, `unauthenticatedRequests…` |
| 3 | Failed Kafka publish doesn't lose outbox event | `OutboxPublisherTest.failedPublishKeepsEventPending…`, `NotificationApiIT.outboxRow…Published` |
| 4 | Duplicate Kafka event → no duplicate processing | `RetryAndDeadLetterIT.duplicateKafkaEventIsProcessedOnlyOnce`, `DeliveryServiceTest.duplicateEvent…` |
| 5 | Failed delivery retried within the limit | `failedDeliveryIsRetriedAndEventuallySucceeds` (3 attempts) |
| 6 | Exhausted retries → dead-letter | `exhaustedRetriesAreDeadLettered`, `malformedMessageGoesStraightToDeadLetter…` |
| 7 | Available after disconnect/reconnect | `notificationsRemainAvailableWithoutAnyWebSocketConnection` |
| 8 | Read cannot modify another user's notification | `otherUsersCannotReadOrModifyMyNotification` |
| – | WebSocket auth / destination authorisation / live delivery | `WebSocketIT` (3 tests) |
| – | Preferences, mandatory bypass, templates | `ChannelResolverTest`, `preferencesSuppress…`, `TemplateRendererTest`, `templateRenderingReportsMissingVariables` |
| – | In-app SENT/DELIVERED, aggregate status | `inAppDeliveryFollowsHandoffPushAndAck`, `StatusAggregatorTest` |

Not covered by automated tests: controller slice tests with mocked services, repository-only tests (repositories are exercised through the ITs), outage scenarios (manual, see failure-scenarios.md), real SMTP.

## Manual end-to-end checklist
1. `docker compose up -d --build`, all containers healthy.
2. Two browser windows: alice and admin. As admin send to `alice` (Developer panel, recipients `alice`): alice sees it live, badge increments.
3. Refresh alice's page: still there (durable). Mark read: badge decrements.
4. Email ticked: MailHog <http://localhost:8025> shows the message (HTML-escaped).
5. Preferences: disable Email for alice, send again with Email: no mail, status still `SENT`.
6. Run the failure scenarios.

## Useful checks
```bash
curl -s localhost:8080/actuator/health/readiness
curl -s localhost:8080/actuator/prometheus -H "Authorization: Bearer $ADMIN_TOKEN" | grep -E "notifications_created|outbox_|realtime_push"
curl -s localhost:8081/actuator/prometheus | grep -E "delivery_|kafka_consumer_fetch_manager_records_lag"
```
Metrics: `notifications.created`, `outbox.published`, `outbox.publish.failures`, `realtime.push`, `notifications.status.updates` (api); `delivery.sent`, `delivery.latency`, `delivery.attempt.failures`, `delivery.dead.lettered`, `delivery.duplicates.skipped` (worker). Consumer lag comes from Spring Kafka's built-in Micrometer binding (`kafka.consumer.fetch.manager.records.lag`).
