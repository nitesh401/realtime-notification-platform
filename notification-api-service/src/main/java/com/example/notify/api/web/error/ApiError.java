package com.example.notify.api.web.error;

import java.time.Instant;
import java.util.List;

/** The single error body shape returned by every endpoint. Never contains stack traces or internals. */
public record ApiError(Instant timestamp, int status, String code, String message, String path,
                       String correlationId, List<String> details) { }
