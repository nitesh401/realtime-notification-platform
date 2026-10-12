package com.example.notify.api.domain;

import com.example.notify.common.Category;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification_templates")
public class NotificationTemplate {
    @Id
    private String id;
    @Column(name = "subject_template", nullable = false)
    private String subjectTemplate;
    @Column(name = "body_template", nullable = false)
    private String bodyTemplate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;
    @Column(nullable = false)
    private boolean mandatory;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationTemplate() { }

    public NotificationTemplate(String id, String subjectTemplate, String bodyTemplate, Category category, boolean mandatory) {
        this.id = id;
        this.subjectTemplate = subjectTemplate;
        this.bodyTemplate = bodyTemplate;
        this.category = category;
        this.mandatory = mandatory;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getSubjectTemplate() { return subjectTemplate; }
    public String getBodyTemplate() { return bodyTemplate; }
    public Category getCategory() { return category; }
    public boolean isMandatory() { return mandatory; }
}
