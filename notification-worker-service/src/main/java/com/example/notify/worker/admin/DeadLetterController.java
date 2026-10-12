package com.example.notify.worker.admin;

import com.example.notify.worker.delivery.DeadLetterService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/dead-letters")
public class DeadLetterController {

    public record DeadLetterView(UUID id, UUID notificationId, String sourceTopic, String reason, Instant createdAt) { }

    private final DeadLetterService service;

    public DeadLetterController(DeadLetterService service) {
        this.service = service;
    }

    /** Payloads are intentionally not returned (they contain recipient data). */
    @GetMapping
    public List<DeadLetterView> pending() {
        return service.pending().stream()
                .map(d -> new DeadLetterView(d.getId(), d.getNotificationId(), d.getSourceTopic(), d.getReason(), d.getCreatedAt()))
                .toList();
    }

    @PostMapping("/{id}/replay")
    public Map<String, Object> replay(@PathVariable UUID id) throws Exception {
        if (!service.replay(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No replayable pending dead letter with this id");
        }
        return Map.of("replayed", true, "id", id);
    }
}
