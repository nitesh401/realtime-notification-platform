package com.example.notify.api.web.error;

/** Maps to HTTP 400 (BAD_REQUEST). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
