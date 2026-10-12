package com.example.notify.api.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polling relay. A database outage only skips a cycle; nothing is lost because nothing was consumed. */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxPublisher publisher;

    public OutboxRelay(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms}")
    public void poll() {
        try {
            publisher.publishBatch();
        } catch (Exception e) {
            log.error("Outbox relay cycle failed; will retry on next poll: {}", e.getClass().getSimpleName());
        }
    }
}
