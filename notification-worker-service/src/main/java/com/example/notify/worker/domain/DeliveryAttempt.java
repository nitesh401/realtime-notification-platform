package com.example.notify.worker.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttempt {
    @Id
    private UUID id;
    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;
    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptOutcome outcome;
    private String error;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    protected DeliveryAttempt() { }

    public DeliveryAttempt(UUID deliveryId, int attemptNo, AttemptOutcome outcome, String error, Instant startedAt, Instant finishedAt) {
        this.id = UUID.randomUUID();
        this.deliveryId = deliveryId;
        this.attemptNo = attemptNo;
        this.outcome = outcome;
        this.error = error;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public UUID getId() { return id; }
    public UUID getDeliveryId() { return deliveryId; }
    public int getAttemptNo() { return attemptNo; }
    public AttemptOutcome getOutcome() { return outcome; }
    public String getError() { return error; }
}
