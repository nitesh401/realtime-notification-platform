package com.example.notify.common;

import java.time.Instant;
import java.util.UUID;

/** Worker -> gateway instruction: push this notification to the recipient's live WebSocket sessions. */
public record RealtimeCommand(
        UUID eventId,
        UUID notificationId,
        UUID recipientId,
        Category category,
        String subject,
        String body,
        Instant createdAt,
        String correlationId) {
}
