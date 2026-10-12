package com.example.notify.worker.messaging;

import com.example.notify.common.RealtimeResult;
import com.example.notify.common.Topics;
import com.example.notify.worker.delivery.DeliveryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Gateway push results and browser acks -> in-app delivery state (SENT / DELIVERED). Idempotent. */
@Component
public class RealtimeResultListener {

    private static final Logger log = LoggerFactory.getLogger(RealtimeResultListener.class);

    private final ObjectMapper mapper;
    private final DeliveryService deliveryService;

    public RealtimeResultListener(ObjectMapper mapper, DeliveryService deliveryService) {
        this.mapper = mapper;
        this.deliveryService = deliveryService;
    }

    @KafkaListener(topics = Topics.REALTIME_RESULT, groupId = "notification-worker-results")
    public void onResult(String payload) {
        RealtimeResult result;
        try {
            result = mapper.readValue(payload, RealtimeResult.class);
        } catch (JsonProcessingException e) {
            log.error("Dropping malformed realtime result");
            return;
        }
        if (result.notificationId() == null || result.outcome() == null) {
            log.error("Dropping incomplete realtime result");
            return;
        }
        deliveryService.onRealtimeResult(result);
    }
}
