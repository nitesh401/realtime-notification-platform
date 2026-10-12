package com.example.notify.worker.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dead_letters")
public class DeadLetter {
    @Id
    private UUID id;
    @Column(name = "notification_id")
    private UUID notificationId;
    @Column(name = "partition_key")
    private String partitionKey;
    @Column(name = "source_topic", nullable = false)
    private String sourceTopic;
    @Column(nullable = false)
    private String payload;
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeadLetterStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "replayed_at")
    private Instant replayedAt;

    protected DeadLetter() { }

    public DeadLetter(UUID notificationId, String partitionKey, String sourceTopic, String payload, String reason) {
        this.id = UUID.randomUUID();
        this.notificationId = notificationId;
        this.partitionKey = partitionKey;
        this.sourceTopic = sourceTopic;
        this.payload = payload;
        this.reason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 500));
        this.status = DeadLetterStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void markReplayed() {
        this.status = DeadLetterStatus.REPLAYED;
        this.replayedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getNotificationId() { return notificationId; }
    public String getPartitionKey() { return partitionKey; }
    public String getSourceTopic() { return sourceTopic; }
    public String getPayload() { return payload; }
    public String getReason() { return reason; }
    public DeadLetterStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
