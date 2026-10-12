package com.example.notify.api.service;

import com.example.notify.api.domain.Role;
import com.example.notify.api.domain.User;
import com.example.notify.api.domain.UserRepository;
import com.example.notify.api.security.JwtService;
import com.example.notify.api.web.dto.AuthResponse;
import com.example.notify.api.web.dto.LoginRequest;
import com.example.notify.api.web.dto.RegisterRequest;
import com.example.notify.api.web.dto.UserDto;
import com.example.notify.api.web.error.AuthenticationFailedException;
import com.example.notify.api.web.error.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    /** Self-registration always yields role USER; roles cannot be supplied by the client. */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByUsername(req.username())) {
            throw new ConflictException("Username is already taken");
        }
        try {
            User user = users.saveAndFlush(new User(req.username(), req.email().toLowerCase(),
                    encoder.encode(req.password()), Role.USER));
            return toResponse(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Username is already taken");
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByUsername(req.username().toLowerCase()).orElse(null);
        // Same error for unknown user and wrong password so usernames cannot be enumerated.
        if (user == null || !encoder.matches(req.password(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("Invalid username or password");
        }
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        JwtService.IssuedToken token = jwt.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(), UserDto.from(user));
    }
}
