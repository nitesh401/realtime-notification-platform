package com.example.notify.api.web.dto;

import com.example.notify.api.domain.Notification;
import com.example.notify.common.Category;
import com.example.notify.common.DeliveryStatus;
import java.time.Instant;
import java.util.UUID;

public record NotificationDto(UUID id, Category category, String subject, String body, DeliveryStatus status,
                              boolean read, Instant readAt, boolean archived, boolean mandatory, String templateId,
                              Instant createdAt) {
    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getCategory(), n.getSubject(), n.getBody(), n.getDeliveryStatus(),
                n.getReadAt() != null, n.getReadAt(), n.getArchivedAt() != null, n.isMandatory(), n.getTemplateId(),
                n.getCreatedAt());
    }
}
