package com.example.notify.worker.messaging;

/** A record that can never succeed (malformed/invalid). Goes straight to the dead-letter topic, no retries. */
public class NonRetryableEventException extends RuntimeException {
    public NonRetryableEventException(String message, Throwable cause) { super(message, cause); }
    public NonRetryableEventException(String message) { super(message); }
}
