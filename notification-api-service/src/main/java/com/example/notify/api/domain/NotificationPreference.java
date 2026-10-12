package com.example.notify.api.domain;

import com.example.notify.common.Category;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {
    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;
    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;
    @Column(name = "info_enabled", nullable = false)
    private boolean infoEnabled = true;
    @Column(name = "success_enabled", nullable = false)
    private boolean successEnabled = true;
    @Column(name = "warning_enabled", nullable = false)
    private boolean warningEnabled = true;
    @Column(name = "error_enabled", nullable = false)
    private boolean errorEnabled = true;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Version
    private Long version;

    protected NotificationPreference() { }

    public NotificationPreference(UUID userId) { this.userId = userId; }

    public static NotificationPreference defaults(UUID userId) { return new NotificationPreference(userId); }

    public boolean isCategoryEnabled(Category category) {
        return switch (category) {
            case INFO -> infoEnabled;
            case SUCCESS -> successEnabled;
            case WARNING -> warningEnabled;
            case ERROR -> errorEnabled;
        };
    }

    public void update(boolean inApp, boolean email, boolean info, boolean success, boolean warning, boolean error) {
        this.inAppEnabled = inApp;
        this.emailEnabled = email;
        this.infoEnabled = info;
        this.successEnabled = success;
        this.warningEnabled = warning;
        this.errorEnabled = error;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public boolean isInAppEnabled() { return inAppEnabled; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public boolean isInfoEnabled() { return infoEnabled; }
    public boolean isSuccessEnabled() { return successEnabled; }
    public boolean isWarningEnabled() { return warningEnabled; }
    public boolean isErrorEnabled() { return errorEnabled; }
}
