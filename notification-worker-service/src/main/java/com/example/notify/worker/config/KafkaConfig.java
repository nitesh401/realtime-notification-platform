package com.example.notify.worker.config;

import com.example.notify.common.Topics;
import java.time.Clock;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaConfig {

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(Topics.PARTITIONS).replicas(1).build();
    }

    // Declared in both services so startup order does not matter; creation is idempotent.
    @Bean NewTopic notificationEventsTopic() { return topic(Topics.NOTIFICATION_EVENTS); }
    @Bean NewTopic realtimeTopic() { return topic(Topics.REALTIME); }
    @Bean NewTopic realtimeResultTopic() { return topic(Topics.REALTIME_RESULT); }
    @Bean NewTopic statusTopic() { return topic(Topics.STATUS); }
    @Bean NewTopic realtimeResultDlt() { return topic(Topics.REALTIME_RESULT + Topics.DLT_SUFFIX); }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Error handler for the NON-retry-topic listener (realtime results). The notification.events listener
     * uses @RetryableTopic, which installs its own non-blocking retry/DLT machinery.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + Topics.DLT_SUFFIX, -1));
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(4);
        backOff.setInitialInterval(500);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(5000);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
