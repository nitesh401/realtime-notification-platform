package com.example.notify.api.web.filter;

import com.example.notify.api.config.AppProperties;
import com.example.notify.api.web.error.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window, in-memory rate limiter keyed by client address. Deliberately simple and local: it is per
 * instance. A production deployment would enforce this at a gateway or with a shared store (e.g. Redis).
 * X-Forwarded-For is intentionally ignored because it is client-controlled unless a trusted proxy sets it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int MAX_TRACKED_KEYS = 50_000;

    private static final class Window {
        long start;
        int count;
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AppProperties props;
    private final ObjectMapper mapper;

    public RateLimitFilter(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean auth = request.getRequestURI().startsWith("/api/v1/auth");
        int limit = auth ? props.rateLimit().authPerMinute() : props.rateLimit().generalPerMinute();
        String key = (auth ? "auth:" : "api:") + request.getRemoteAddr();
        if (allow(key, limit)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), 429, "RATE_LIMITED",
                "Too many requests", request.getRequestURI(), MDC.get(CorrelationIdFilter.MDC_KEY), List.of()));
    }

    boolean allow(String key, int limit) {
        long now = System.currentTimeMillis();
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.values().removeIf(w -> now - w.start >= WINDOW_MILLIS);
        }
        Window w = windows.computeIfAbsent(key, k -> new Window());
        synchronized (w) {
            if (now - w.start >= WINDOW_MILLIS) {
                w.start = now;
                w.count = 0;
            }
            w.count++;
            return w.count <= limit;
        }
    }
}
