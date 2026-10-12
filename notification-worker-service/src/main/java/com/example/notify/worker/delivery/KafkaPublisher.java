package com.example.notify.worker.delivery;

import com.example.notify.common.RealtimeCommand;
import com.example.notify.common.StatusEvent;
import com.example.notify.common.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import com.example.notify.common.DeliveryStatus;

/**
 * Synchronous publishes (wait for the broker ack) for worker->gateway commands and worker->api status.
 * A failure throws, which makes the event be retried: these publishes happen inside event processing, so
 * Kafka's own redelivery is the safety net and no outbox is needed on this side.
 */
@Component
public class KafkaPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final Clock clock;

    public KafkaPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper, Clock clock) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.clock = clock;
    }

    public void sendRealtime(RealtimeCommand command) {
        send(Topics.REALTIME, command.recipientId().toString(), command);
    }

    public void sendStatus(UUID notificationId, DeliveryStatus status, String detail) {
        send(Topics.STATUS, notificationId.toString(), new StatusEvent(notificationId, status, detail, clock.instant()));
    }

    private void send(String topic, String key, Object payload) {
        try {
            kafka.send(topic, key, mapper.writeValueAsString(payload)).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing to " + topic, e);
        } catch (JsonProcessingException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("Could not publish to " + topic, e);
        }
    }
}
