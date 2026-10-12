package com.example.notify.api.messaging;

import com.example.notify.api.realtime.NotificationPush;
import com.example.notify.api.service.NotificationMetrics;
import com.example.notify.common.RealtimeCommand;
import com.example.notify.common.RealtimeResult;
import com.example.notify.common.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

/**
 * The realtime gateway: consumes push commands, delivers to the recipient's live WebSocket sessions on THIS
 * instance and reports the outcome back to the worker. The push is not a delivery guarantee; the notification
 * is already durable in PostgreSQL and is fetched via REST on reconnect.
 *
 * Multi-instance note: with one shared consumer group only one gateway instance gets each command, so it may
 * not hold the user's session. Scaling out needs a broadcast group per instance (or a user->instance routing
 * key). Documented in docs/architecture.md; the local single-instance setup is unaffected.
 */
@Component
public class RealtimeCommandListener {

    private static final Logger log = LoggerFactory.getLogger(RealtimeCommandListener.class);

    private final SimpMessagingTemplate messaging;
    private final SimpUserRegistry userRegistry;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final NotificationMetrics metrics;

    public RealtimeCommandListener(SimpMessagingTemplate messaging, SimpUserRegistry userRegistry,
                                   KafkaTemplate<String, String> kafka, ObjectMapper mapper, Clock clock,
                                   NotificationMetrics metrics) {
        this.messaging = messaging;
        this.userRegistry = userRegistry;
        this.kafka = kafka;
        this.mapper = mapper;
        this.clock = clock;
        this.metrics = metrics;
    }

    @KafkaListener(topics = Topics.REALTIME, groupId = "notification-api-realtime")
    public void onCommand(String payload) throws Exception {
        RealtimeCommand command;
        try {
            command = mapper.readValue(payload, RealtimeCommand.class);
        } catch (JsonProcessingException e) {
            log.error("Dropping malformed realtime command (cannot be processed, no retry)");
            return; // skipping is correct: retrying a malformed record can never succeed
        }
        String user = command.recipientId().toString();
        SimpUser simpUser = userRegistry.getUser(user);
        boolean connected = simpUser != null && !simpUser.getSessions().isEmpty();

        if (connected) {
            messaging.convertAndSendToUser(user, "/queue/notifications", new NotificationPush(command.notificationId(),
                    command.category(), command.subject(), command.body(), command.createdAt()));
        }
        RealtimeResult.Outcome outcome = connected ? RealtimeResult.Outcome.PUSHED : RealtimeResult.Outcome.NO_ACTIVE_SESSION;
        metrics.realtime(outcome.name());
        publishResult(new RealtimeResult(command.notificationId(), command.recipientId(), outcome,
                connected ? "pushed" : "no-active-session", clock.instant()));
    }

    private void publishResult(RealtimeResult result) throws JsonProcessingException, ExecutionException,
            InterruptedException, java.util.concurrent.TimeoutException {
        // Waiting for the ack makes a failure visible: the listener then retries (a duplicate push is harmless,
        // the browser de-duplicates by notification id).
        kafka.send(Topics.REALTIME_RESULT, result.notificationId().toString(), mapper.writeValueAsString(result))
                .get(5, TimeUnit.SECONDS);
    }
}
