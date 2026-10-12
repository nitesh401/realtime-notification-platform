package com.example.notify.api.web.controller;

import com.example.notify.api.security.AuthUser;
import com.example.notify.api.service.PreferenceService;
import com.example.notify.api.web.dto.PreferencesDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preferences")
public class PreferenceController {

    private final PreferenceService service;

    public PreferenceController(PreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public PreferencesDto get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(AuthUser.from(jwt).id());
    }

    @PutMapping
    public PreferencesDto put(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreferencesDto body) {
        return service.update(AuthUser.from(jwt).id(), body);
    }
}
