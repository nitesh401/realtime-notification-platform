package com.example.notify.worker.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notify.common.Category;
import com.example.notify.common.Channel;
import com.example.notify.common.DeliveryStatus;
import com.example.notify.common.NotificationEvent;
import com.example.notify.worker.domain.DeliveryRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock DeliveryStore store;
    @Mock EmailSender email;
    @Mock KafkaPublisher publisher;

    private DeliveryService service;
    private NotificationEvent event;
    private DeliveryRecord record;

    @BeforeEach
    void setUp() {
        service = new DeliveryService(store, email, publisher, new WorkerMetrics(new SimpleMeterRegistry()), Clock.systemUTC());
        event = new NotificationEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "alice", "alice@example.test",
                Category.INFO, "Hi", "Body", EnumSet.of(Channel.EMAIL), false, "corr", Instant.now());
        record = new DeliveryRecord(event, Channel.EMAIL);
    }

    @Test
    void duplicateEventForAlreadySentChannelDoesNotSendAgain() {
        record.markSent(Instant.now(), "smtp-accepted");
        when(store.find(event.notificationId(), Channel.EMAIL)).thenReturn(Optional.of(record));
        when(store.forNotification(event.notificationId())).thenReturn(java.util.List.of(record));

        service.process(event);

        verify(email, never()).send(anyString(), anyString(), anyString());
        verify(store, never()).beginAttempt(any());
        verify(publisher).sendStatus(eq(event.notificationId()), eq(DeliveryStatus.SENT), any());
    }

    @Test
    void successfulEmailIsRecordedAsSent() {
        when(store.find(event.notificationId(), Channel.EMAIL)).thenReturn(Optional.of(record));
        when(store.beginAttempt(record.getId())).thenReturn(1);
        when(store.forNotification(event.notificationId())).thenReturn(java.util.List.of(record));

        service.process(event);

        verify(email).send("alice@example.test", "Hi", "Body");
        verify(store).completeSent(eq(record.getId()), eq(1), any(), anyString());
    }

    @Test
    void failedEmailIsRecordedAndRaisedSoKafkaRetriesIt() {
        when(store.find(event.notificationId(), Channel.EMAIL)).thenReturn(Optional.of(record));
        when(store.beginAttempt(record.getId())).thenReturn(1);
        when(store.forNotification(event.notificationId())).thenReturn(java.util.List.of(record));
        doThrow(new EmailDeliveryException("smtp down")).when(email).send(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.process(event)).isInstanceOf(DeliveryFailedException.class);

        verify(store).completeFailure(eq(record.getId()), eq(1), any(), anyString());
        verify(store, never()).completeSent(any(), anyInt(), any(), anyString());
        verify(publisher).sendStatus(eq(event.notificationId()), any(), any()); // status still published
    }
}
