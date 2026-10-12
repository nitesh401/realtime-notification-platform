package com.example.notify.api.web.controller;

import com.example.notify.api.security.AuthUser;
import com.example.notify.api.service.NotificationCreationService;
import com.example.notify.api.service.NotificationQueryService;
import com.example.notify.api.web.dto.CreateNotificationRequest;
import com.example.notify.api.web.dto.CreateNotificationResponse;
import com.example.notify.api.web.dto.NotificationDto;
import com.example.notify.api.web.dto.PageDto;
import com.example.notify.api.web.dto.UnreadCountDto;
import com.example.notify.api.web.dto.UpdatedCountDto;
import com.example.notify.common.Category;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationCreationService creation;
    private final NotificationQueryService queries;

    public NotificationController(NotificationCreationService creation, NotificationQueryService queries) {
        this.creation = creation;
        this.queries = queries;
    }

    /** 201 when created, 200 with duplicate=true when the Idempotency-Key was already used by this caller. */
    @PostMapping
    public ResponseEntity<CreateNotificationResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateNotificationRequest request) {
        CreateNotificationResponse response = creation.create(AuthUser.from(jwt), request, idempotencyKey,
                MDC.get("correlationId"));
        return ResponseEntity.status(response.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public PageDto<NotificationDto> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Category category,
            @RequestParam(defaultValue = "ALL") NotificationQueryService.ReadFilter status,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return queries.list(AuthUser.from(jwt).id(), category, status, includeArchived, page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCountDto unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCountDto(queries.unreadCount(AuthUser.from(jwt).id()));
    }

    @PatchMapping("/read-all")
    public UpdatedCountDto readAll(@AuthenticationPrincipal Jwt jwt) {
        return new UpdatedCountDto(queries.markAllRead(AuthUser.from(jwt).id()));
    }

    @GetMapping("/{id}")
    public NotificationDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return queries.get(AuthUser.from(jwt).id(), id);
    }

    @PatchMapping("/{id}/read")
    public NotificationDto markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return queries.markRead(AuthUser.from(jwt).id(), id);
    }

    /** Archives (soft-deletes) the notification; it disappears from lists but stays in the audit trail. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        queries.archive(AuthUser.from(jwt).id(), id);
        return ResponseEntity.noContent().build();
    }
}
