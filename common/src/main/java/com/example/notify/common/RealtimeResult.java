package com.example.notify.common;

import java.time.Instant;
import java.util.UUID;

/** Gateway -> worker feedback about an in-app push, or a client acknowledgement. */
public record RealtimeResult(
        UUID notificationId,
        UUID recipientId,
        Outcome outcome,
        String detail,
        Instant occurredAt) {

    public enum Outcome {
        /** Pushed to at least one live WebSocket session. */
        PUSHED,
        /** Recipient had no live session; notification stays durable and is fetched on next load/reconnect. */
        NO_ACTIVE_SESSION,
        /** The browser client confirmed receipt (STOMP /app/ack). */
        ACKED
    }
}
