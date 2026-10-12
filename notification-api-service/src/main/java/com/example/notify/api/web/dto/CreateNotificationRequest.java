package com.example.notify.api.web.dto;

import com.example.notify.common.Category;
import com.example.notify.common.Channel;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Whitelist DTO: only these fields are accepted (unknown JSON properties are rejected), so a client can never
 * set status, owner, timestamps or ids. Recipients are usernames; omit to send to yourself.
 */
public record CreateNotificationRequest(
        @Size(max = 100) List<@Size(min = 1, max = 64) String> recipients,
        @Size(max = 64) String templateId,
        @Size(max = 20) Map<@Size(max = 32) String, @Size(max = 500) String> variables,
        @Size(max = 255) String subject,
        @Size(max = 4000) String body,
        Category category,
        Set<Channel> channels,
        Boolean mandatory) { }
