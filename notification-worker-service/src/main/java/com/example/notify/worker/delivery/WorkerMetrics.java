package com.example.notify.worker.delivery;

import com.example.notify.common.Channel;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class WorkerMetrics {

    private final MeterRegistry registry;

    public WorkerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void sent(Channel channel, Duration latencySinceCreated) {
        registry.counter("delivery.sent", "channel", channel.name()).increment();
        if (latencySinceCreated != null && !latencySinceCreated.isNegative()) {
            registry.timer("delivery.latency", "channel", channel.name()).record(latencySinceCreated);
        }
    }

    public void attemptFailed(Channel channel) {
        registry.counter("delivery.attempt.failures", "channel", channel.name()).increment();
    }

    public void deadLettered() {
        registry.counter("delivery.dead.lettered").increment();
    }

    public void duplicateSkipped(Channel channel) {
        registry.counter("delivery.duplicates.skipped", "channel", channel.name()).increment();
    }
}
