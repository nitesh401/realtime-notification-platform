package com.example.notify.worker.delivery;

import com.example.notify.common.Channel;
import com.example.notify.common.DeliveryStatus;
import com.example.notify.common.NotificationEvent;
import com.example.notify.common.RealtimeCommand;
import com.example.notify.common.RealtimeResult;
import com.example.notify.worker.domain.DeliveryRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Orchestrates delivery of one event across its channels. Idempotency comes from the unique
 * (notification_id, channel) delivery record: a duplicate or replayed event finds the record already
 * SENT/DELIVERED and does nothing. Not @Transactional on purpose: each state change commits before the next
 * external side effect (SMTP, Kafka), so a crash never loses the knowledge that an attempt happened.
 */
@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final DeliveryStore store;
    private final EmailSender emailSender;
    private final KafkaPublisher publisher;
    private final WorkerMetrics metrics;
    private final Clock clock;

    public DeliveryService(DeliveryStore store, EmailSender emailSender, KafkaPublisher publisher,
                           WorkerMetrics metrics, Clock clock) {
        this.store = store;
        this.emailSender = emailSender;
        this.publisher = publisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    public void process(NotificationEvent event) {
        RuntimeException firstFailure = null;
        for (Channel channel : event.channels()) {
            try {
                deliver(event, channel);
            } catch (RuntimeException e) {
                log.warn("Delivery attempt failed (notificationId={}, channel={}): {}", event.notificationId(), channel,
                        e.getClass().getSimpleName());
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        publishAggregate(event.notificationId()); // always, so the api converges even on duplicates/retries
        if (firstFailure != null) {
            throw new DeliveryFailedException("At least one channel failed", firstFailure);
        }
    }

    private void deliver(NotificationEvent event, Channel channel) {
        DeliveryRecord delivery = getOrCreate(event, channel);
        DeliveryStatus status = delivery.getStatus();
        if (status == DeliveryStatus.SENT || status == DeliveryStatus.DELIVERED
                || (channel == Channel.IN_APP && status == DeliveryStatus.PROCESSING)) {
            metrics.duplicateSkipped(channel);
            return; // duplicate event, or in-app handoff already in flight
        }
        int attemptNo = store.beginAttempt(delivery.getId());
        Instant started = clock.instant();
        try {
            if (channel == Channel.EMAIL) {
                emailSender.send(event.recipientEmail(), event.subject(), event.body());
            } else {
                publisher.sendRealtime(new RealtimeCommand(event.eventId(), event.notificationId(), event.recipientId(),
                        event.category(), event.subject(), event.body(), event.createdAt(), event.correlationId()));
            }
        } catch (RuntimeException e) {
            metrics.attemptFailed(channel);
            store.completeFailure(delivery.getId(), attemptNo, started, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
        if (channel == Channel.EMAIL) {
            store.completeSent(delivery.getId(), attemptNo, started, "smtp-accepted");
            metrics.sent(channel, Duration.between(event.createdAt(), clock.instant()));
        } else {
            store.completeHandoff(delivery.getId(), attemptNo, started);
        }
    }

    private DeliveryRecord getOrCreate(NotificationEvent event, Channel channel) {
        return store.find(event.notificationId(), channel).orElseGet(() -> {
            try {
                return store.create(event, channel);
            } catch (DataIntegrityViolationException race) {
                return store.find(event.notificationId(), channel).orElseThrow(() -> race);
            }
        });
    }

    /** Called for gateway results and client acks. */
    public void onRealtimeResult(RealtimeResult result) {
        if (store.applyRealtimeResult(result)) {
            if (result.outcome() != RealtimeResult.Outcome.ACKED) {
                metrics.sent(Channel.IN_APP, null);
            }
            publishAggregate(result.notificationId());
        }
    }

    public void publishAggregate(UUID notificationId) {
        List<DeliveryStatus> statuses = store.forNotification(notificationId).stream().map(DeliveryRecord::getStatus).toList();
        DeliveryStatus aggregate = StatusAggregator.aggregate(statuses);
        publisher.sendStatus(notificationId, aggregate, null);
    }
}
