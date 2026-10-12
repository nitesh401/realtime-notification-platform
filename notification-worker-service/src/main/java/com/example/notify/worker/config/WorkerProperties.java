package com.example.notify.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "worker")
public record WorkerProperties(String adminKey, Email email) {
    public record Email(String from, String simulateFailureSuffix) { }
}
