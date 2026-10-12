package com.example.notify.api.web.error;

/** Maps to HTTP 404 (NOT_FOUND). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
