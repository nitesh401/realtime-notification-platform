package com.example.notify.worker.delivery;

import com.example.notify.common.Channel;
import com.example.notify.common.DeliveryStatus;
import com.example.notify.common.NotificationEvent;
import com.example.notify.common.RealtimeResult;
import com.example.notify.worker.domain.AttemptOutcome;
import com.example.notify.worker.domain.DeliveryAttempt;
import com.example.notify.worker.domain.DeliveryAttemptRepository;
import com.example.notify.worker.domain.DeliveryRecord;
import com.example.notify.worker.domain.DeliveryRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Short, separate transactions: every state change is committed before the next side effect happens. */
@Component
public class DeliveryStore {

    private final DeliveryRecordRepository records;
    private final DeliveryAttemptRepository attempts;
    private final Clock clock;

    public DeliveryStore(DeliveryRecordRepository records, DeliveryAttemptRepository attempts, Clock clock) {
        this.records = records;
        this.attempts = attempts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<DeliveryRecord> find(UUID notificationId, Channel channel) {
        return records.findByNotificationIdAndChannel(notificationId, channel);
    }

    /** May throw DataIntegrityViolationException if a concurrent consumer created it first. */
    @Transactional
    public DeliveryRecord create(NotificationEvent event, Channel channel) {
        return records.saveAndFlush(new DeliveryRecord(event, channel));
    }

    @Transactional
    public int beginAttempt(UUID deliveryId) {
        DeliveryRecord d = records.findById(deliveryId).orElseThrow();
        int no = d.beginAttempt();
        records.save(d);
        return no;
    }

    @Transactional
    public void completeSent(UUID deliveryId, int attemptNo, Instant started, String detail) {
        DeliveryRecord d = records.findById(deliveryId).orElseThrow();
        Instant now = clock.instant();
        d.markSent(now, detail);
        attempts.save(new DeliveryAttempt(deliveryId, attemptNo, AttemptOutcome.SUCCESS, null, started, now));
    }

    @Transactional
    public void completeHandoff(UUID deliveryId, int attemptNo, Instant started) {
        attempts.save(new DeliveryAttempt(deliveryId, attemptNo, AttemptOutcome.HANDOFF, null, started, clock.instant()));
    }

    @Transactional
    public void completeFailure(UUID deliveryId, int attemptNo, Instant started, String error) {
        DeliveryRecord d = records.findById(deliveryId).orElseThrow();
        String safe = error == null ? "unknown" : error.substring(0, Math.min(error.length(), 500));
        d.markRetrying(safe);
        attempts.save(new DeliveryAttempt(deliveryId, attemptNo, AttemptOutcome.FAILURE, safe, started, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<DeliveryRecord> forNotification(UUID notificationId) {
        return records.findByNotificationId(notificationId);
    }

    /**
     * Applies a gateway result / client ack to the in-app delivery. Returns true if state changed.
     * Transitions: PROCESSING|RETRYING -> SENT (pushed or stored), PROCESSING|SENT -> DELIVERED (ack, recipient must match).
     */
    @Transactional
    public boolean applyRealtimeResult(RealtimeResult result) {
        Optional<DeliveryRecord> found = records.findByNotificationIdAndChannel(result.notificationId(), Channel.IN_APP);
        if (found.isEmpty()) {
            return false;
        }
        DeliveryRecord d = found.get();
        Instant now = clock.instant();
        switch (result.outcome()) {
            case PUSHED, NO_ACTIVE_SESSION -> {
                if (d.getStatus() == DeliveryStatus.PROCESSING || d.getStatus() == DeliveryStatus.RETRYING) {
                    d.markSent(now, result.outcome() == RealtimeResult.Outcome.PUSHED ? "pushed" : "no-active-session");
                    return true;
                }
            }
            case ACKED -> {
                boolean owner = d.getRecipientId().equals(result.recipientId());
                if (owner && (d.getStatus() == DeliveryStatus.PROCESSING || d.getStatus() == DeliveryStatus.SENT)) {
                    d.markDelivered(now);
                    return true;
                }
            }
        }
        return false;
    }

    /** Dead-letters everything that never succeeded; an in-app handoff still awaiting its result is left alone. */
    @Transactional
    public int markDeadLettered(UUID notificationId) {
        int changed = 0;
        for (DeliveryRecord d : records.findByNotificationId(notificationId)) {
            DeliveryStatus s = d.getStatus();
            boolean unfinished = s == DeliveryStatus.PENDING || s == DeliveryStatus.RETRYING
                    || (s == DeliveryStatus.PROCESSING && d.getChannel() == Channel.EMAIL);
            if (unfinished) {
                d.markDeadLettered();
                changed++;
            }
        }
        return changed;
    }
}
