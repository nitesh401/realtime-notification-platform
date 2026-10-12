package com.example.notify.worker.messaging;

import com.example.notify.common.NotificationEvent;
import com.example.notify.common.Topics;
import com.example.notify.worker.delivery.DeadLetterService;
import com.example.notify.worker.delivery.DeliveryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Main consumer. Failure handling uses Spring Kafka non-blocking retries: a failed record is republished to
 * notification.events-retry-0/1/... with exponential backoff (so the main partition keeps moving), and after
 * the last attempt to notification.events-dlt, handled by onDeadLetter. Offsets are committed per record
 * after the listener returns, so a crash mid-processing re-delivers the record (at-least-once).
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final ObjectMapper mapper;
    private final DeliveryService deliveryService;
    private final DeadLetterService deadLetterService;

    public NotificationEventListener(ObjectMapper mapper, DeliveryService deliveryService, DeadLetterService deadLetterService) {
        this.mapper = mapper;
        this.deliveryService = deliveryService;
        this.deadLetterService = deadLetterService;
    }

    @RetryableTopic(
            attempts = "${worker.retry.attempts}",
            backoff = @Backoff(delayExpression = "${worker.retry.delay-ms}",
                    multiplierExpression = "${worker.retry.multiplier}",
                    maxDelayExpression = "${worker.retry.max-delay-ms}"),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            exclude = NonRetryableEventException.class,
            numPartitions = "3",
            replicationFactor = "1")
    @KafkaListener(topics = Topics.NOTIFICATION_EVENTS, groupId = "notification-worker")
    public void onEvent(String payload) {
        NotificationEvent event;
        try {
            event = mapper.readValue(payload, NotificationEvent.class);
        } catch (JsonProcessingException e) {
            throw new NonRetryableEventException("Malformed notification event", e);
        }
        if (event.notificationId() == null || event.recipientId() == null || event.channels() == null
                || event.channels().isEmpty()) {
            throw new NonRetryableEventException("Notification event is missing required fields");
        }
        if (event.correlationId() != null) {
            MDC.put("correlationId", event.correlationId());
        }
        try {
            deliveryService.process(event);
        } finally {
            MDC.remove("correlationId");
        }
    }

    @DltHandler
    public void onDeadLetter(String payload,
                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                             @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String reason) {
        log.warn("Event reached the dead-letter topic {}", topic);
        deadLetterService.handle(payload, topic, reason);
    }
}
