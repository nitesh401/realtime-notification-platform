package com.example.notify.common;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Event published by the API (via the outbox) for every notification that has at least one channel to deliver.
 * eventId is the outbox row id: it is stable across publish retries, which makes duplicates detectable.
 * It carries the recipient email only because the worker owns no user data; see docs/architecture.md.
 */
public record NotificationEvent(
        UUID eventId,
        UUID notificationId,
        UUID recipientId,
        String recipientUsername,
        String recipientEmail,
        Category category,
        String subject,
        String body,
        Set<Channel> channels,
        boolean mandatory,
        String correlationId,
        Instant createdAt) {
}
