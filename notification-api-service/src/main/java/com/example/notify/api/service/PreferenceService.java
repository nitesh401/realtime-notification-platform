package com.example.notify.api.service;

import com.example.notify.api.domain.NotificationPreference;
import com.example.notify.api.domain.NotificationPreferenceRepository;
import com.example.notify.api.web.dto.PreferencesDto;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferenceService {

    private final NotificationPreferenceRepository repository;

    public PreferenceService(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PreferencesDto get(UUID userId) {
        return PreferencesDto.from(repository.findById(userId).orElseGet(() -> NotificationPreference.defaults(userId)));
    }

    @Transactional
    public PreferencesDto update(UUID userId, PreferencesDto dto) {
        NotificationPreference prefs = repository.findById(userId).orElseGet(() -> NotificationPreference.defaults(userId));
        prefs.update(dto.inAppEnabled(), dto.emailEnabled(), dto.infoEnabled(), dto.successEnabled(),
                dto.warningEnabled(), dto.errorEnabled());
        return PreferencesDto.from(repository.save(prefs));
    }
}
