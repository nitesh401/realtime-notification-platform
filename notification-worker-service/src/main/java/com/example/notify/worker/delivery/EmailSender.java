package com.example.notify.worker.delivery;

/** Email channel port. The only implementation talks SMTP to MailHog locally; swap for a real relay in production. */
public interface EmailSender {
    /** @throws EmailDeliveryException on any failure, so the caller can record the attempt and retry. */
    void send(String to, String subject, String body);
}
