package com.example.notify.api.service;

import com.example.notify.api.domain.Notification;
import com.example.notify.api.domain.NotificationRepository;
import com.example.notify.api.domain.NotificationTemplate;
import com.example.notify.api.domain.NotificationTemplateRepository;
import com.example.notify.api.domain.User;
import com.example.notify.api.domain.UserRepository;
import com.example.notify.api.security.AuthUser;
import com.example.notify.api.web.dto.CreateNotificationRequest;
import com.example.notify.api.web.dto.CreateNotificationResponse;
import com.example.notify.api.web.dto.NotificationDto;
import com.example.notify.api.web.error.BadRequestException;
import com.example.notify.api.web.error.ForbiddenOperationException;
import com.example.notify.api.web.error.NotFoundException;
import com.example.notify.common.Category;
import com.example.notify.common.Channel;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Orchestrates creation: authorisation, recipient resolution, template rendering, idempotency.
 * Intentionally NOT @Transactional: the transaction lives in NotificationPersistence so a unique-constraint
 * violation from a concurrent duplicate request can be caught here, after that transaction has rolled back.
 */
@Service
public class NotificationCreationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCreationService.class);
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9._:-]{1,100}$");

    private final UserRepository users;
    private final NotificationTemplateRepository templates;
    private final NotificationRepository notifications;
    private final TemplateRenderer renderer;
    private final NotificationPersistence persistence;

    public NotificationCreationService(UserRepository users, NotificationTemplateRepository templates,
                                       NotificationRepository notifications, TemplateRenderer renderer,
                                       NotificationPersistence persistence) {
        this.users = users;
        this.templates = templates;
        this.notifications = notifications;
        this.renderer = renderer;
        this.persistence = persistence;
    }

    public CreateNotificationResponse create(AuthUser actor, CreateNotificationRequest req, String idempotencyKey,
                                             String correlationId) {
        if (idempotencyKey != null && !KEY.matcher(idempotencyKey).matches()) {
            throw new BadRequestException("Idempotency-Key must be 1-100 characters: letters, digits, . _ : -");
        }
        if (idempotencyKey != null) {
            List<Notification> existing = notifications.findByCreatedByAndIdempotencyKey(actor.id(), idempotencyKey);
            if (!existing.isEmpty()) {
                return respond(existing, true);
            }
        }

        List<User> recipients = resolveRecipients(actor, req);
        boolean wantsMandatory = Boolean.TRUE.equals(req.mandatory());
        if (wantsMandatory && !actor.admin()) {
            throw new ForbiddenOperationException("Only administrators can send mandatory notifications");
        }

        String subject;
        String body;
        Category category;
        boolean mandatory = wantsMandatory;
        String templateId = null;

        if (req.templateId() != null) {
            if (req.subject() != null || req.body() != null) {
                throw new BadRequestException("Provide either templateId or subject/body, not both");
            }
            NotificationTemplate template = templates.findById(req.templateId())
                    .orElseThrow(() -> new NotFoundException("Template not found: " + req.templateId()));
            TemplateRenderer.Rendered rendered = renderer.render(template, req.variables());
            subject = rendered.subject();
            body = rendered.body();
            category = req.category() != null ? req.category() : template.getCategory();
            mandatory = mandatory || template.isMandatory(); // server-side policy, not client input
            templateId = template.getId();
        } else {
            if (isBlank(req.subject()) || isBlank(req.body())) {
                throw new BadRequestException("subject and body are required when no templateId is given");
            }
            if (req.category() == null) {
                throw new BadRequestException("category is required when no templateId is given");
            }
            subject = req.subject().replaceAll("[\\r\\n]+", " ").trim();
            body = req.body();
            category = req.category();
        }

        Set<Channel> channels = (req.channels() == null || req.channels().isEmpty())
                ? EnumSet.of(Channel.IN_APP) : EnumSet.copyOf(req.channels());

        NotificationPersistence.Command cmd = new NotificationPersistence.Command(actor.id(), recipients, category,
                subject, body, templateId, mandatory, channels, idempotencyKey, correlationId);
        try {
            return respond(persistence.persist(cmd), false);
        } catch (DataIntegrityViolationException e) {
            if (idempotencyKey == null) {
                throw e;
            }
            // A concurrent request with the same key won the race; return what it created.
            List<Notification> winner = notifications.findByCreatedByAndIdempotencyKey(actor.id(), idempotencyKey);
            if (winner.isEmpty()) {
                throw e;
            }
            log.info("Idempotency race resolved: returning notifications created by the concurrent request");
            return respond(winner, true);
        }
    }

    private List<User> resolveRecipients(AuthUser actor, CreateNotificationRequest req) {
        Set<String> names = new LinkedHashSet<>();
        if (req.recipients() == null || req.recipients().isEmpty()) {
            names.add(actor.username());
        } else {
            req.recipients().forEach(r -> names.add(r.toLowerCase()));
        }
        if (!actor.admin() && !(names.size() == 1 && names.contains(actor.username()))) {
            throw new ForbiddenOperationException("Only administrators can send notifications to other users");
        }
        List<User> found = users.findAllByUsernameIn(names);
        if (found.size() != names.size()) {
            throw new BadRequestException("One or more recipients do not exist");
        }
        return found;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static CreateNotificationResponse respond(List<Notification> list, boolean duplicate) {
        return new CreateNotificationResponse(list.stream().map(NotificationDto::from).toList(), duplicate);
    }
}
