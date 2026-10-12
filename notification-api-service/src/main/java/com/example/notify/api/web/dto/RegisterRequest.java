package com.example.notify.api.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9_]{3,32}$", message = "3-32 chars: lowercase letters, digits, underscore") String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 10, max = 72, message = "must be 10-72 characters") String password) { }
