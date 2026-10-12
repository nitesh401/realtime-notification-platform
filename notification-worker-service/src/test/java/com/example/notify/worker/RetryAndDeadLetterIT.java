package com.example.notify.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.notify.common.Category;
import com.example.notify.common.Channel;
import com.example.notify.common.DeliveryStatus;
import com.example.notify.common.NotificationEvent;
import com.example.notify.common.RealtimeResult;
import com.example.notify.common.Topics;
import com.example.notify.worker.delivery.EmailDeliveryException;
import com.example.notify.worker.delivery.EmailSender;
import com.example.notify.worker.domain.AttemptOutcome;
import com.example.notify.worker.domain.DeadLetterRepository;
import com.example.notify.worker.domain.DeliveryAttemptRepository;
import com.example.notify.worker.domain.DeliveryRecord;
import com.example.notify.worker.domain.DeliveryRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Real Kafka + PostgreSQL via Testcontainers; only the SMTP boundary is mocked so failures are deterministic.
 * Retry budget is shortened: 3 total attempts with ~200ms backoff.
 */
@SpringBootTest(properties = {"worker.retry.attempts=3", "worker.retry.delay-ms=200", "worker.retry.multiplier=1.5",
        "worker.retry.max-delay-ms=1000", "worker.admin-key=test-admin-key"})
class RetryAndDeadLetterIT {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @MockitoBean EmailSender emailSender;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper mapper;
    @Autowired DeliveryRecordRepository deliveries;
    @Autowired DeliveryAttemptRepository attempts;
    @Autowired DeadLetterRepository deadLetters;

    @BeforeEach
    void resetMock() {
        Mockito.reset(emailSender);
    }

    private NotificationEvent event(Set<Channel> channels) {
        return new NotificationEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "alice", "alice@example.test",
                Category.INFO, "Subject", "Body", channels, false, "corr-" + UUID.randomUUID(), Instant.now());
    }

    private void publish(NotificationEvent e) throws Exception {
        kafka.send(Topics.NOTIFICATION_EVENTS, e.recipientId().toString(), mapper.writeValueAsString(e)).get();
    }

    private DeliveryRecord delivery(UUID nid, Channel c) {
        return deliveries.findByNotificationIdAndChannel(nid, c).orElse(null);
    }

    @Test
    void successfulEmailIsMarkedSent() throws Exception {
        NotificationEvent e = event(EnumSet.of(Channel.EMAIL));
        doNothing().when(emailSender).send(anyString(), anyString(), anyString());
        publish(e);
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            DeliveryRecord d = delivery(e.notificationId(), Channel.EMAIL);
            assertThat(d).isNotNull();
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.SENT);
        });
        verify(emailSender, times(1)).send(anyString(), anyString(), anyString());
    }

    // ---- requirement 4: duplicate event does not trigger duplicate logical processing ----
    @Test
    void duplicateKafkaEventIsProcessedOnlyOnce() throws Exception {
        NotificationEvent e = event(EnumSet.of(Channel.EMAIL));
        publish(e);
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(delivery(e.notificationId(), Channel.EMAIL)).extracting(DeliveryRecord::getStatus).isEqualTo(DeliveryStatus.SENT));
        publish(e); // exact duplicate (e.g. outbox republish after a crash)
        publish(e);
        Thread.sleep(3000); // give the consumer time to (not) act
        verify(emailSender, times(1)).send(anyString(), anyString(), anyString());
        assertThat(deliveries.findByNotificationId(e.notificationId())).hasSize(1);
    }

    // ---- requirement 5: failed delivery retried within the configured limit ----
    @Test
    void failedDeliveryIsRetriedAndEventuallySucceeds() throws Exception {
        NotificationEvent e = event(EnumSet.of(Channel.EMAIL));
        doThrow(new EmailDeliveryException("smtp down")).doThrow(new EmailDeliveryException("smtp down"))
                .doNothing().when(emailSender).send(anyString(), anyString(), anyString());
        publish(e);
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(delivery(e.notificationId(), Channel.EMAIL)).extracting(DeliveryRecord::getStatus).isEqualTo(DeliveryStatus.SENT));
        DeliveryRecord d = delivery(e.notificationId(), Channel.EMAIL);
        assertThat(d.getAttemptCount()).isEqualTo(3);
        assertThat(attempts.findByDeliveryIdOrderByAttemptNo(d.getId()))
                .extracting(a -> a.getOutcome())
                .containsExactly(AttemptOutcome.FAILURE, AttemptOutcome.FAILURE, AttemptOutcome.SUCCESS);
        verify(emailSender, times(3)).send(anyString(), anyString(), anyString());
    }

    // ---- requirement 6: exhausted retries reach the dead-letter flow ----
    @Test
    void exhaustedRetriesAreDeadLettered() throws Exception {
        NotificationEvent e = event(EnumSet.of(Channel.EMAIL));
        doThrow(new EmailDeliveryException("permanent")).when(emailSender).send(anyString(), anyString(), anyString());
        publish(e);
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(delivery(e.notificationId(), Channel.EMAIL)).extracting(DeliveryRecord::getStatus).isEqualTo(DeliveryStatus.DEAD_LETTERED));
        verify(emailSender, times(3)).send(anyString(), anyString(), anyString()); // exactly the configured limit
        assertThat(deadLetters.findAll()).anyMatch(d -> e.notificationId().equals(d.getNotificationId()));
    }

    @Test
    void malformedMessageGoesStraightToDeadLetterWithoutRetries() throws Exception {
        String marker = "not-json-" + UUID.randomUUID();
        kafka.send(Topics.NOTIFICATION_EVENTS, "k", marker).get();
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(deadLetters.findAll()).anyMatch(d -> marker.equals(d.getPayload()) && d.getNotificationId() == null));
        verify(emailSender, Mockito.never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void inAppDeliveryFollowsHandoffPushAndAck() throws Exception {
        NotificationEvent e = event(EnumSet.of(Channel.IN_APP));
        publish(e);
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            DeliveryRecord d = delivery(e.notificationId(), Channel.IN_APP);
            assertThat(d).isNotNull();
            assertThat(attempts.findByDeliveryIdOrderByAttemptNo(d.getId())).extracting(a -> a.getOutcome()).containsExactly(AttemptOutcome.HANDOFF);
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PROCESSING); // a Kafka publish is NOT a delivery
        });

        sendResult(e, UUID.randomUUID(), RealtimeResult.Outcome.ACKED); // wrong recipient: ignored
        sendResult(e, e.recipientId(), RealtimeResult.Outcome.PUSHED);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(delivery(e.notificationId(), Channel.IN_APP).getStatus()).isEqualTo(DeliveryStatus.SENT));

        sendResult(e, e.recipientId(), RealtimeResult.Outcome.ACKED);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(delivery(e.notificationId(), Channel.IN_APP).getStatus()).isEqualTo(DeliveryStatus.DELIVERED));
    }

    private void sendResult(NotificationEvent e, UUID recipient, RealtimeResult.Outcome outcome) throws Exception {
        RealtimeResult r = new RealtimeResult(e.notificationId(), recipient, outcome, "test", Instant.now());
        kafka.send(Topics.REALTIME_RESULT, e.notificationId().toString(), mapper.writeValueAsString(r)).get();
    }
}
