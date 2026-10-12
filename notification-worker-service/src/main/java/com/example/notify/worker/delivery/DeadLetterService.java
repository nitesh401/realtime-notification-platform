package com.example.notify.worker.delivery;

import com.example.notify.common.DeliveryStatus;
import com.example.notify.common.NotificationEvent;
import com.example.notify.common.Topics;
import com.example.notify.worker.domain.DeadLetter;
import com.example.notify.worker.domain.DeadLetterRepository;
import com.example.notify.worker.domain.DeadLetterStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadLetterService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

    private final DeadLetterRepository deadLetters;
    private final DeliveryStore store;
    private final KafkaPublisher publisher;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final WorkerMetrics metrics;

    public DeadLetterService(DeadLetterRepository deadLetters, DeliveryStore store, KafkaPublisher publisher,
                             KafkaTemplate<String, String> kafka, ObjectMapper mapper, WorkerMetrics metrics) {
        this.deadLetters = deadLetters;
        this.store = store;
        this.publisher = publisher;
        this.kafka = kafka;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    /** Handles a record that exhausted its retries or could never be processed (e.g. malformed JSON). */
    public void handle(String payload, String topic, String reason) {
        NotificationEvent event = null;
        try {
            event = mapper.readValue(payload, NotificationEvent.class);
            if (event.notificationId() == null || event.recipientId() == null) {
                event = null;
            }
        } catch (Exception ignored) {
            // malformed: stored without a notification id; it cannot be replayed
        }
        UUID nid = event == null ? null : event.notificationId();
        deadLetters.save(new DeadLetter(nid, event == null ? null : event.recipientId().toString(), topic, payload, reason));
        metrics.deadLettered();
        // Log identifiers and the reason only; the payload (recipient email, body) is never logged.
        log.error("Dead-lettered record (notificationId={}, topic={}, reason={})", nid, topic, reason);
        if (nid != null) {
            store.markDeadLettered(nid);
            publisher.sendStatus(nid, aggregateOf(nid), "dead-lettered");
        }
    }

    private DeliveryStatus aggregateOf(UUID nid) {
        return StatusAggregator.aggregate(store.forNotification(nid).stream().map(d -> d.getStatus()).toList());
    }

    @Transactional(readOnly = true)
    public List<DeadLetter> pending() {
        return deadLetters.findByStatusOrderByCreatedAtAsc(DeadLetterStatus.PENDING, PageRequest.of(0, 100));
    }

    /** Controlled reprocessing: republish the original event; it re-enters the normal flow with a fresh retry budget. */
    @Transactional
    public boolean replay(UUID id) throws Exception {
        DeadLetter dl = deadLetters.findById(id).orElse(null);
        if (dl == null || dl.getStatus() != DeadLetterStatus.PENDING || dl.getNotificationId() == null) {
            return false;
        }
        kafka.send(Topics.NOTIFICATION_EVENTS, dl.getPartitionKey(), dl.getPayload()).get(5, TimeUnit.SECONDS);
        dl.markReplayed();
        log.info("Replayed dead letter {} (notificationId={})", dl.getId(), dl.getNotificationId());
        return true;
    }
}
