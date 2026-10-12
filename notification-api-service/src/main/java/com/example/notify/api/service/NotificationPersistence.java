package com.example.notify.api.service;

import com.example.notify.api.domain.Notification;
import com.example.notify.api.domain.NotificationPreference;
import com.example.notify.api.domain.NotificationPreferenceRepository;
import com.example.notify.api.domain.NotificationRepository;
import com.example.notify.api.domain.OutboxEvent;
import com.example.notify.api.domain.OutboxRepository;
import com.example.notify.api.domain.User;
import com.example.notify.common.Category;
import com.example.notify.common.Channel;
import com.example.notify.common.NotificationEvent;
import com.example.notify.common.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single transaction that writes a notification AND its outbox row. Either both commit or neither does,
 * which is what removes the database/Kafka dual-write problem: Kafka is only ever touched by the relay,
 * after commit, from the outbox table.
 */
@Service
public class NotificationPersistence {

    public record Command(UUID createdBy, List<User> recipients, Category category, String subject, String body,
                          String templateId, boolean mandatory, Set<Channel> requestedChannels,
                          String idempotencyKey, String correlationId) { }

    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final OutboxRepository outbox;
    private final ChannelResolver resolver;
    private final ObjectMapper mapper;
    private final NotificationMetrics metrics;

    public NotificationPersistence(NotificationRepository notifications, NotificationPreferenceRepository preferences,
                                   OutboxRepository outbox, ChannelResolver resolver, ObjectMapper mapper,
                                   NotificationMetrics metrics) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.outbox = outbox;
        this.resolver = resolver;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    @Transactional
    public List<Notification> persist(Command cmd) {
        List<Notification> saved = new ArrayList<>();
        for (User recipient : cmd.recipients()) {
            NotificationPreference prefs = preferences.findById(recipient.getId())
                    .orElseGet(() -> NotificationPreference.defaults(recipient.getId()));
            Set<Channel> channels = resolver.resolve(cmd.requestedChannels(), cmd.category(), cmd.mandatory(), prefs);

            Notification n = notifications.save(new Notification(recipient.getId(), cmd.createdBy(), cmd.category(),
                    cmd.subject(), cmd.body(), cmd.templateId(), cmd.mandatory(),
                    channels.contains(Channel.IN_APP), channels.contains(Channel.EMAIL),
                    cmd.idempotencyKey(), cmd.correlationId()));
            saved.add(n);
            metrics.created(n.getDeliveryStatus());

            if (!channels.isEmpty()) {
                UUID eventId = UUID.randomUUID();
                NotificationEvent event = new NotificationEvent(eventId, n.getId(), recipient.getId(),
                        recipient.getUsername(), recipient.getEmail(), n.getCategory(), n.getSubject(), n.getBody(),
                        channels, n.isMandatory(), cmd.correlationId(), Instant.now());
                outbox.save(new OutboxEvent(eventId, n.getId(), Topics.NOTIFICATION_EVENTS,
                        recipient.getId().toString(), toJson(event)));
            }
        }
        notifications.flush(); // surface unique-constraint violations (idempotency races) inside this method
        return saved;
    }

    private String toJson(NotificationEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise notification event", e);
        }
    }
}
