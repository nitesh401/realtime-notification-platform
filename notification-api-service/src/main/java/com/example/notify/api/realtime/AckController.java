package com.example.notify.api.realtime;

import com.example.notify.common.RealtimeResult;
import com.example.notify.common.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.Principal;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * Receives the browser's delivery acknowledgement and forwards it to the worker, which is the only component
 * that may move a delivery to DELIVERED. The recipient id comes from the authenticated principal, so a client
 * cannot acknowledge on behalf of someone else (the worker also verifies it matches the delivery's recipient).
 */
@Controller
public class AckController {

    private static final Logger log = LoggerFactory.getLogger(AckController.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AckController(KafkaTemplate<String, String> kafka, ObjectMapper mapper, Clock clock) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.clock = clock;
    }

    @MessageMapping("/ack")
    public void ack(AckMessage message, Principal principal) throws JsonProcessingException {
        if (principal == null || message == null || message.notificationId() == null) {
            return;
        }
        RealtimeResult result = new RealtimeResult(message.notificationId(), UUID.fromString(principal.getName()),
                RealtimeResult.Outcome.ACKED, "client-ack", clock.instant());
        // Best effort: if this publish fails the delivery simply stays SENT, never wrongly DELIVERED.
        kafka.send(Topics.REALTIME_RESULT, message.notificationId().toString(), mapper.writeValueAsString(result))
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.warn("Could not publish client ack for notification {}", message.notificationId());
                    }
                });
    }
}
