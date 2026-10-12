package com.example.notify.api.web.dto;

import java.time.Instant;

public record AuthResponse(String token, Instant expiresAt, UserDto user) { }
