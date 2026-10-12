package com.example.notify.api.web.error;

/** Maps to HTTP 403 (FORBIDDEN). */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) { super(message); }
}
