package com.example.notify.api.messaging;

import com.example.notify.api.domain.NotificationRepository;
import com.example.notify.api.service.NotificationMetrics;
import com.example.notify.common.StatusEvent;
import com.example.notify.common.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Applies aggregated delivery status from the worker to the notification row; idempotent and order-safe. */
@Component
public class StatusEventListener {

    private static final Logger log = LoggerFactory.getLogger(StatusEventListener.class);

    private final NotificationRepository repository;
    private final ObjectMapper mapper;
    private final NotificationMetrics metrics;

    public StatusEventListener(NotificationRepository repository, ObjectMapper mapper, NotificationMetrics metrics) {
        this.repository = repository;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    @KafkaListener(topics = Topics.STATUS, groupId = "notification-api-status")
    @Transactional
    public void onStatus(String payload) {
        StatusEvent event;
        try {
            event = mapper.readValue(payload, StatusEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Dropping malformed status event");
            return;
        }
        int updated = repository.applyStatus(event.notificationId(), event.status(), event.occurredAt());
        metrics.statusApplied(event.status(), updated > 0);
    }
}
