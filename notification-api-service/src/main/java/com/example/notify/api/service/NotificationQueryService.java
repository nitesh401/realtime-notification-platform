package com.example.notify.api.service;

import com.example.notify.api.domain.Notification;
import com.example.notify.api.domain.NotificationRepository;
import com.example.notify.api.web.dto.NotificationDto;
import com.example.notify.api.web.dto.PageDto;
import com.example.notify.api.web.error.NotFoundException;
import com.example.notify.common.Category;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read/state-change operations on a user's own notifications. Every lookup is scoped by recipient id. */
@Service
public class NotificationQueryService {

    public enum ReadFilter { ALL, READ, UNREAD }

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationQueryService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageDto<NotificationDto> list(UUID userId, Category category, ReadFilter readFilter,
                                         boolean includeArchived, int page, int size) {
        Specification<Notification> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("recipientId"), userId));
            p.add(cb.isTrue(root.get("channelInApp")));
            if (!includeArchived) {
                p.add(cb.isNull(root.get("archivedAt")));
            }
            if (category != null) {
                p.add(cb.equal(root.get("category"), category));
            }
            if (readFilter == ReadFilter.READ) {
                p.add(cb.isNotNull(root.get("readAt")));
            } else if (readFilter == ReadFilter.UNREAD) {
                p.add(cb.isNull(root.get("readAt")));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageDto.from(repository.findAll(spec, pageable).map(NotificationDto::from));
    }

    @Transactional(readOnly = true)
    public NotificationDto get(UUID userId, UUID id) {
        return NotificationDto.from(owned(userId, id));
    }

    @Transactional
    public NotificationDto markRead(UUID userId, UUID id) {
        Notification n = owned(userId, id);
        n.markRead(clock.instant());
        return NotificationDto.from(n);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repository.markAllRead(userId, clock.instant());
    }

    @Transactional
    public void archive(UUID userId, UUID id) {
        owned(userId, id).archive(clock.instant());
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countUnread(userId);
    }

    /** "Not found" (not "forbidden") for other users' ids, so existence is not leaked. */
    private Notification owned(UUID userId, UUID id) {
        return repository.findByIdAndRecipientId(id, userId)
                .filter(Notification::isChannelInApp)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
    }
}
