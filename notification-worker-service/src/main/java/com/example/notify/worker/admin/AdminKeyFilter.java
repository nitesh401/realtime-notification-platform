package com.example.notify.worker.admin;

import com.example.notify.worker.config.WorkerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Protects /admin/** with a shared key from WORKER_ADMIN_KEY. If the key is not configured, admin is disabled. */
@Component
public class AdminKeyFilter extends OncePerRequestFilter {

    private final WorkerProperties props;

    public AdminKeyFilter(WorkerProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/admin");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String configured = props.adminKey();
        String supplied = request.getHeader("X-Admin-Key");
        boolean ok = configured != null && !configured.isBlank() && supplied != null
                && MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin access denied");
            return;
        }
        chain.doFilter(request, response);
    }
}
