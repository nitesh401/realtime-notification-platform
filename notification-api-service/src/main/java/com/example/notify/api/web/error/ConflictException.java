package com.example.notify.api.web.error;

/** Maps to HTTP 409 (CONFLICT). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
