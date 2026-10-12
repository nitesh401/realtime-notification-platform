package com.example.notify.api.domain;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    private UUID id;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(nullable = false)
    private String topic;
    @Column(name = "partition_key", nullable = false)
    private String partitionKey;
    @Column(nullable = false)
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "last_error")
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() { }

    /** The id is chosen by the caller because it doubles as the Kafka event id inside the payload. */
    public OutboxEvent(UUID id, UUID aggregateId, String topic, String partitionKey, String payload) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;
    }

    public void markPublished(Instant now) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = now;
        this.lastError = null;
    }

    /** Records a failed publish: exponential backoff capped at 60s; gives up (FAILED) after maxAttempts. */
    public void recordFailure(String error, int maxAttempts, Instant now) {
        this.attempts++;
        this.lastError = error == null ? "unknown" : error.substring(0, Math.min(error.length(), 500));
        if (attempts >= maxAttempts) {
            this.status = OutboxStatus.FAILED;
        } else {
            long seconds = Math.min(60L, 1L << Math.min(attempts, 6));
            this.nextAttemptAt = now.plus(Duration.ofSeconds(seconds));
        }
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getTopic() { return topic; }
    public String getPartitionKey() { return partitionKey; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public Instant getPublishedAt() { return publishedAt; }
}
