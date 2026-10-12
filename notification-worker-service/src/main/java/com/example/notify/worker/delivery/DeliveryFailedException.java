package com.example.notify.worker.delivery;

/** Thrown after all channels were attempted when at least one failed: triggers the Kafka retry topic flow. */
public class DeliveryFailedException extends RuntimeException {
    public DeliveryFailedException(String message, Throwable cause) { super(message, cause); }
}
