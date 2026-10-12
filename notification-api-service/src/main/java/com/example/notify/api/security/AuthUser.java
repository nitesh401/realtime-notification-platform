package com.example.notify.api.security;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated caller, always derived from the verified JWT - never from request input. */
public record AuthUser(UUID id, String username, boolean admin) {

    public static AuthUser from(Jwt jwt) {
        return new AuthUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("username"),
                "ADMIN".equals(jwt.getClaimAsString("role")));
    }
}
