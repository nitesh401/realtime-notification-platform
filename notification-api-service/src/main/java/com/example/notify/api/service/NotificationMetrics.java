package com.example.notify.api.service;

import com.example.notify.common.DeliveryStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Thin wrapper so metric names live in one place. All are exposed at /actuator/metrics and /actuator/prometheus. */
@Component
public class NotificationMetrics {

    private final MeterRegistry registry;

    public NotificationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void created(DeliveryStatus initialStatus) {
        registry.counter("notifications.created", "status", initialStatus.name()).increment();
    }

    public void outboxPublished() {
        registry.counter("outbox.published").increment();
    }

    public void outboxPublishFailed() {
        registry.counter("outbox.publish.failures").increment();
    }

    public void realtime(String outcome) {
        registry.counter("realtime.push", "outcome", outcome).increment();
    }

    public void statusApplied(DeliveryStatus status, boolean applied) {
        registry.counter("notifications.status.updates", "status", status.name(), "applied", String.valueOf(applied)).increment();
    }
}
