package com.example.notify.api.web.dto;

import com.example.notify.api.domain.NotificationPreference;
import jakarta.validation.constraints.NotNull;

public record PreferencesDto(
        @NotNull Boolean inAppEnabled,
        @NotNull Boolean emailEnabled,
        @NotNull Boolean infoEnabled,
        @NotNull Boolean successEnabled,
        @NotNull Boolean warningEnabled,
        @NotNull Boolean errorEnabled) {

    public static PreferencesDto from(NotificationPreference p) {
        return new PreferencesDto(p.isInAppEnabled(), p.isEmailEnabled(), p.isInfoEnabled(), p.isSuccessEnabled(),
                p.isWarningEnabled(), p.isErrorEnabled());
    }
}
