package com.example.notify.api.realtime;

import com.example.notify.common.Category;
import java.time.Instant;
import java.util.UUID;

/** The JSON frame delivered to /user/queue/notifications. */
public record NotificationPush(UUID id, Category category, String subject, String body, Instant createdAt) { }
