package com.example.notify.api.web.dto;

import java.util.List;

/** duplicate=true means an earlier request with the same Idempotency-Key already created these notifications. */
public record CreateNotificationResponse(List<NotificationDto> notifications, boolean duplicate) { }
