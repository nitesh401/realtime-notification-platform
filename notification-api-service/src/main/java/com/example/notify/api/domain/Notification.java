package com.example.notify.api.domain;

import com.example.notify.common.Category;
import com.example.notify.common.DeliveryStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    private UUID id;
    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;
    @Column(nullable = false)
    private String subject;
    @Column(nullable = false)
    private String body;
    @Column(name = "template_id")
    private String templateId;
    @Column(nullable = false)
    private boolean mandatory;
    @Column(name = "channel_in_app", nullable = false)
    private boolean channelInApp;
    @Column(name = "channel_email", nullable = false)
    private boolean channelEmail;
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false)
    private DeliveryStatus deliveryStatus;
    @Column(name = "status_updated_at", nullable = false)
    private Instant statusUpdatedAt;
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    @Column(name = "correlation_id")
    private String correlationId;
    @Column(name = "read_at")
    private Instant readAt;
    @Column(name = "archived_at")
    private Instant archivedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    protected Notification() { }

    public Notification(UUID recipientId, UUID createdBy, Category category, String subject, String body,
                        String templateId, boolean mandatory, boolean channelInApp, boolean channelEmail,
                        String idempotencyKey, String correlationId) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.recipientId = recipientId;
        this.createdBy = createdBy;
        this.category = category;
        this.subject = subject;
        this.body = body;
        this.templateId = templateId;
        this.mandatory = mandatory;
        this.channelInApp = channelInApp;
        this.channelEmail = channelEmail;
        this.deliveryStatus = (channelInApp || channelEmail) ? DeliveryStatus.PENDING : DeliveryStatus.SUPPRESSED;
        this.statusUpdatedAt = now;
        this.idempotencyKey = idempotencyKey;
        this.correlationId = correlationId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Idempotent: a second call keeps the first read timestamp. */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
            updatedAt = now;
        }
    }

    public void archive(Instant now) {
        if (archivedAt == null) {
            archivedAt = now;
            updatedAt = now;
        }
    }

    public UUID getId() { return id; }
    public UUID getRecipientId() { return recipientId; }
    public UUID getCreatedBy() { return createdBy; }
    public Category getCategory() { return category; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getTemplateId() { return templateId; }
    public boolean isMandatory() { return mandatory; }
    public boolean isChannelInApp() { return channelInApp; }
    public boolean isChannelEmail() { return channelEmail; }
    public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public Instant getStatusUpdatedAt() { return statusUpdatedAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getCorrelationId() { return correlationId; }
    public Instant getReadAt() { return readAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
