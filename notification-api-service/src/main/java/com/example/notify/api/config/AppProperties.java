package com.example.notify.api.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, RateLimit rateLimit, Outbox outbox, Seed seed) {

    public record Jwt(String secret, Duration ttl) { }

    public record Cors(List<String> allowedOrigins) { }

    public record RateLimit(int generalPerMinute, int authPerMinute) { }

    public record Outbox(long pollIntervalMs, int batchSize, int maxAttempts, Duration sendTimeout) { }

    public record Seed(boolean enabled, String password) { }
}
