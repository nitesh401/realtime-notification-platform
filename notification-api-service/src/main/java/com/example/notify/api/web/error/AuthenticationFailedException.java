package com.example.notify.api.web.error;

/** Maps to HTTP 401 (UNAUTHORIZED). */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) { super(message); }
}
