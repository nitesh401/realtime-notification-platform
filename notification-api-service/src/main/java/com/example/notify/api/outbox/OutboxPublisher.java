package com.example.notify.api.outbox;

import com.example.notify.api.config.AppProperties;
import com.example.notify.api.domain.OutboxEvent;
import com.example.notify.api.domain.OutboxRepository;
import com.example.notify.api.service.NotificationMetrics;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes due outbox rows to Kafka and marks them PUBLISHED, all inside one DB transaction holding row locks.
 * Crash between "Kafka acked" and "commit" => the row stays PENDING and is published again: at-least-once.
 * Consumers deduplicate (see worker), so the duplicate is harmless.
 * On the first failure the batch stops, so a recipient's later events are never published ahead of an earlier
 * one that is waiting for its retry (ordering is preserved per outbox, at the cost of head-of-line blocking).
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final AppProperties props;
    private final NotificationMetrics metrics;
    private final Clock clock;

    public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafka, AppProperties props,
                           NotificationMetrics metrics, Clock clock) {
        this.repository = repository;
        this.kafka = kafka;
        this.props = props;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public int publishBatch() {
        List<OutboxEvent> due = repository.lockDue(props.outbox().batchSize());
        int published = 0;
        for (OutboxEvent event : due) {
            try {
                kafka.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                        .get(props.outbox().sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                event.markPublished(clock.instant());
                metrics.outboxPublished();
                published++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.recordFailure("interrupted", props.outbox().maxAttempts(), clock.instant());
                break;
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                event.recordFailure(cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                        props.outbox().maxAttempts(), clock.instant());
                metrics.outboxPublishFailed();
                log.warn("Outbox publish failed (eventId={}, attempt={}, status={}): {}", event.getId(),
                        event.getAttempts(), event.getStatus(), cause.getClass().getSimpleName());
                break;
            }
        }
        return published;
    }
}
