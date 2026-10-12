package com.example.notify.api.web.dto;

import com.example.notify.api.domain.User;
import java.util.UUID;

public record UserDto(UUID id, String username, String email, String role) {
    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getRole().name());
    }
}
