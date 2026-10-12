package com.example.notify.worker.domain;

import com.example.notify.common.Channel;
import com.example.notify.common.DeliveryStatus;
import com.example.notify.common.NotificationEvent;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** One row per (notification, channel). Business state of the notification lives in the api service. */
@Entity
@Table(name = "delivery_records")
public class DeliveryRecord {
    @Id
    private UUID id;
    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "last_error")
    private String lastError;
    private String detail;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    @Version
    private Long version;

    protected DeliveryRecord() { }

    public DeliveryRecord(NotificationEvent event, Channel channel) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.notificationId = event.notificationId();
        this.eventId = event.eventId();
        this.recipientId = event.recipientId();
        this.channel = channel;
        this.status = DeliveryStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Starts an attempt and returns its number. */
    public int beginAttempt() {
        this.status = DeliveryStatus.PROCESSING;
        this.attemptCount++;
        this.updatedAt = Instant.now();
        return attemptCount;
    }

    public void markSent(Instant now, String detail) {
        this.status = DeliveryStatus.SENT;
        this.sentAt = now;
        this.detail = detail;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void markRetrying(String error) {
        this.status = DeliveryStatus.RETRYING;
        this.lastError = error;
        this.updatedAt = Instant.now();
    }

    public void markDelivered(Instant now) {
        this.status = DeliveryStatus.DELIVERED;
        this.deliveredAt = now;
        this.updatedAt = now;
    }

    public void markDeadLettered() {
        this.status = DeliveryStatus.DEAD_LETTERED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getNotificationId() { return notificationId; }
    public UUID getEventId() { return eventId; }
    public UUID getRecipientId() { return recipientId; }
    public Channel getChannel() { return channel; }
    public DeliveryStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public String getLastError() { return lastError; }
    public String getDetail() { return detail; }
    public Instant getSentAt() { return sentAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
}
