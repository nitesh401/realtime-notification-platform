package com.example.notify.api.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.notify.api.config.AppProperties;
import com.example.notify.api.domain.OutboxEvent;
import com.example.notify.api.domain.OutboxRepository;
import com.example.notify.api.domain.OutboxStatus;
import com.example.notify.api.service.NotificationMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock OutboxRepository repository;
    @Mock KafkaTemplate<String, String> kafka;
    @Mock NotificationMetrics metrics;

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("x".repeat(32), Duration.ofHours(1)),
                new AppProperties.Cors(List.of()),
                new AppProperties.RateLimit(100, 100),
                new AppProperties.Outbox(500, 10, 3, Duration.ofSeconds(1)),
                new AppProperties.Seed(false, ""));
        publisher = new OutboxPublisher(repository, kafka, props, metrics, clock);
    }

    private OutboxEvent event() {
        UUID id = UUID.randomUUID();
        return new OutboxEvent(id, UUID.randomUUID(), "notification.events", "key", "{}");
    }

    @Test
    void successfulPublishMarksEventPublished() {
        OutboxEvent e = event();
        when(repository.lockDue(10)).thenReturn(List.of(e));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));

        assertThat(publisher.publishBatch()).isEqualTo(1);
        assertThat(e.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(e.getPublishedAt()).isEqualTo(clock.instant());
    }

    @Test
    void failedPublishKeepsEventPendingWithBackoffAndLaterSucceeds() {
        OutboxEvent e = event();
        when(repository.lockDue(10)).thenReturn(List.of(e));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>failedFuture(new RuntimeException("broker down")))
                .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));

        assertThat(publisher.publishBatch()).isZero();
        assertThat(e.getStatus()).isEqualTo(OutboxStatus.PENDING);   // not lost
        assertThat(e.getAttempts()).isEqualTo(1);
        assertThat(e.getNextAttemptAt()).isAfter(clock.instant());   // backoff scheduled
        assertThat(e.getLastError()).contains("broker down");

        assertThat(publisher.publishBatch()).isEqualTo(1);            // Kafka is back
        assertThat(e.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void firstFailureStopsTheBatchToPreserveOrdering() {
        OutboxEvent first = event();
        OutboxEvent second = event();
        when(repository.lockDue(10)).thenReturn(List.of(first, second));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>failedFuture(new RuntimeException("down")));

        publisher.publishBatch();

        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(second.getAttempts()).isZero();
        assertThat(second.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        OutboxEvent e = event();
        when(repository.lockDue(10)).thenReturn(List.of(e));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>failedFuture(new RuntimeException("down")));

        publisher.publishBatch();
        publisher.publishBatch();
        publisher.publishBatch();

        assertThat(e.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }
}
