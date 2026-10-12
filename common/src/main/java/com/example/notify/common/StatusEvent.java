package com.example.notify.common;

import java.time.Instant;
import java.util.UUID;

/** Worker -> api: aggregated delivery status. occurredAt lets the api ignore out-of-order/stale updates. */
public record StatusEvent(UUID notificationId, DeliveryStatus status, String detail, Instant occurredAt) {
}
