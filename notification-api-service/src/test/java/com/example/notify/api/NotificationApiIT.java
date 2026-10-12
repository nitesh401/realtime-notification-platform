package com.example.notify.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.notify.api.domain.NotificationRepository;
import com.example.notify.api.domain.OutboxRepository;
import com.example.notify.api.domain.OutboxStatus;
import com.example.notify.api.messaging.StatusEventListener;
import com.example.notify.common.DeliveryStatus;
import com.example.notify.common.StatusEvent;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class NotificationApiIT extends AbstractIntegrationTest {

    @Autowired NotificationRepository notifications;
    @Autowired OutboxRepository outbox;
    @Autowired StatusEventListener statusListener;

    private static final String SIMPLE = "{\"subject\":\"Hello\",\"body\":\"World\",\"category\":\"INFO\"}";

    private MvcResult create(TestUser user, String json, String idempotencyKey) throws Exception {
        var request = post("/api/v1/notifications").header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request).andReturn();
    }

    private String firstId(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString()).get("notifications").get(0).get("id").asText();
    }

    // ---- requirement 1: idempotency ----
    @Test
    void sameIdempotencyKeyDoesNotCreateDuplicateNotifications() throws Exception {
        TestUser alice = register();
        MvcResult first = create(alice, SIMPLE, "key-1");
        MvcResult second = create(alice, SIMPLE, "key-1");

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(second.getResponse().getContentAsString()).get("duplicate").asBoolean()).isTrue();
        assertThat(firstId(second)).isEqualTo(firstId(first));
        assertThat(notifications.findByCreatedByAndIdempotencyKey(alice.id(), "key-1")).hasSize(1);
    }

    @Test
    void concurrentRequestsWithSameKeyCreateExactlyOneNotification() throws Exception {
        TestUser alice = register();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                Callable<Integer> call = () -> create(alice, SIMPLE, "race-1").getResponse().getStatus();
                futures.add(pool.submit(call));
            }
            for (Future<Integer> f : futures) {
                assertThat(f.get()).isIn(200, 201);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(notifications.findByCreatedByAndIdempotencyKey(alice.id(), "race-1")).hasSize(1);
    }

    // ---- requirements 2 and 8: isolation ----
    @Test
    void otherUsersCannotReadOrModifyMyNotification() throws Exception {
        TestUser alice = register();
        TestUser bob = register();
        String id = firstId(create(alice, SIMPLE, null));

        mvc.perform(get("/api/v1/notifications/" + id).header("Authorization", "Bearer " + bob.token()))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/notifications/" + id + "/read").header("Authorization", "Bearer " + bob.token()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/notifications/" + id).header("Authorization", "Bearer " + bob.token()))
                .andExpect(status().isNotFound());

        assertThat(notifications.findById(UUID.fromString(id)).orElseThrow().getReadAt()).isNull();
        assertThat(notifications.findById(UUID.fromString(id)).orElseThrow().getArchivedAt()).isNull();
        mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + bob.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void unauthenticatedRequestsAreRejectedWithConsistentErrorBody() throws Exception {
        mvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUserCannotNotifyOthersOrSendMandatory() throws Exception {
        TestUser alice = register();
        TestUser bob = register();
        String toBob = "{\"recipients\":[\"" + bob.username() + "\"],\"subject\":\"x\",\"body\":\"y\",\"category\":\"INFO\"}";
        assertThat(create(alice, toBob, null).getResponse().getStatus()).isEqualTo(403);
        String mandatory = "{\"subject\":\"x\",\"body\":\"y\",\"category\":\"INFO\",\"mandatory\":true}";
        assertThat(create(alice, mandatory, null).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void unknownFieldsAreRejectedSoClientsCannotSetServerOwnedState() throws Exception {
        TestUser alice = register();
        String json = "{\"subject\":\"x\",\"body\":\"y\",\"category\":\"INFO\",\"status\":\"DELIVERED\",\"recipientId\":\"" + UUID.randomUUID() + "\"}";
        MvcResult r = create(alice, json, null);
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(mapper.readTree(r.getResponse().getContentAsString()).get("code").asText()).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void validationErrorsUseTheStandardShapeWithCorrelationId() throws Exception {
        TestUser alice = register();
        MvcResult r = mvc.perform(post("/api/v1/notifications").header("Authorization", "Bearer " + alice.token())
                .header("X-Correlation-Id", "trace-123").contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"" + "a".repeat(300) + "\",\"body\":\"y\",\"category\":\"INFO\"}")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        JsonNode body = mapper.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.get("correlationId").asText()).isEqualTo("trace-123");
        assertThat(r.getResponse().getHeader("X-Correlation-Id")).isEqualTo("trace-123");
    }

    // ---- requirement 7: durability ----
    @Test
    void notificationsRemainAvailableWithoutAnyWebSocketConnection() throws Exception {
        TestUser alice = register();
        for (int i = 0; i < 3; i++) {
            create(alice, SIMPLE, null);
        }
        mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + alice.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + alice.token()))
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void readAndReadAllAffectOnlyTheCaller() throws Exception {
        TestUser alice = register();
        String id = firstId(create(alice, SIMPLE, null));
        create(alice, SIMPLE, null);

        mvc.perform(patch("/api/v1/notifications/" + id + "/read").header("Authorization", "Bearer " + alice.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + alice.token()))
                .andExpect(jsonPath("$.count").value(1));
        mvc.perform(patch("/api/v1/notifications/read-all").header("Authorization", "Bearer " + alice.token()))
                .andExpect(jsonPath("$.updated").value(1));
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + alice.token()))
                .andExpect(jsonPath("$.count").value(0));
    }

    // ---- preferences / mandatory ----
    @Test
    void preferencesSuppressOptionalButNotMandatoryNotifications() throws Exception {
        TestUser alice = register();
        mvc.perform(put("/api/v1/preferences").header("Authorization", "Bearer " + alice.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inAppEnabled\":false,\"emailEnabled\":true,\"infoEnabled\":true,\"successEnabled\":true,\"warningEnabled\":true,\"errorEnabled\":true}"))
                .andExpect(status().isOk());

        MvcResult optional = create(alice, SIMPLE, null);
        assertThat(mapper.readTree(optional.getResponse().getContentAsString()).get("notifications").get(0).get("status").asText())
                .isEqualTo("SUPPRESSED");

        MvcResult security = create(alice, "{\"templateId\":\"PASSWORD_CHANGED\",\"variables\":{\"device\":\"Chrome\"}}", null);
        JsonNode n = mapper.readTree(security.getResponse().getContentAsString()).get("notifications").get(0);
        assertThat(n.get("status").asText()).isEqualTo("PENDING");
        assertThat(n.get("mandatory").asBoolean()).isTrue();
        mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + alice.token()))
                .andExpect(jsonPath("$.totalElements").value(1)); // only the mandatory one is visible in-app
    }

    @Test
    void templateRenderingReportsMissingVariables() throws Exception {
        TestUser alice = register();
        MvcResult bad = create(alice, "{\"templateId\":\"ORDER_SHIPPED\",\"variables\":{\"orderId\":\"A1\"}}", null);
        assertThat(bad.getResponse().getStatus()).isEqualTo(400);
        assertThat(bad.getResponse().getContentAsString()).contains("eta");

        MvcResult ok = create(alice, "{\"templateId\":\"ORDER_SHIPPED\",\"variables\":{\"orderId\":\"A1\",\"eta\":\"Friday\"}}", null);
        assertThat(ok.getResponse().getStatus()).isEqualTo(201);
        assertThat(ok.getResponse().getContentAsString()).contains("Order A1 has shipped");
    }

    @Test
    void templatesAreListed() throws Exception {
        TestUser alice = register();
        mvc.perform(get("/api/v1/templates").header("Authorization", "Bearer " + alice.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='WELCOME')]").exists());
    }

    // ---- requirement 3 (integration side): outbox row is written atomically and then published ----
    @Test
    void outboxRowIsWrittenWithTheNotificationAndEventuallyPublished() throws Exception {
        TestUser alice = register();
        UUID id = UUID.fromString(firstId(create(alice, SIMPLE, null)));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(outbox.findAll()).anyMatch(e -> e.getAggregateId().equals(id) && e.getStatus() == OutboxStatus.PUBLISHED));
    }

    // ---- status events from the worker: idempotent and order-safe ----
    @Test
    void staleStatusEventsAreIgnored() throws Exception {
        TestUser alice = register();
        String id = firstId(create(alice, SIMPLE, null));
        Instant base = Instant.now().plusSeconds(60);

        statusListener.onStatus(mapper.writeValueAsString(new StatusEvent(UUID.fromString(id), DeliveryStatus.SENT, "x", base.plusSeconds(10))));
        statusListener.onStatus(mapper.writeValueAsString(new StatusEvent(UUID.fromString(id), DeliveryStatus.RETRYING, "late", base)));
        assertThat(notifications.findById(UUID.fromString(id)).orElseThrow().getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);

        statusListener.onStatus(mapper.writeValueAsString(new StatusEvent(UUID.fromString(id), DeliveryStatus.DELIVERED, "ack", base.plusSeconds(20))));
        assertThat(notifications.findById(UUID.fromString(id)).orElseThrow().getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);

        statusListener.onStatus("{not json"); // malformed events are dropped, not thrown
    }

    @Test
    void archivedNotificationsDisappearFromTheDefaultList() throws Exception {
        TestUser alice = register();
        String id = firstId(create(alice, SIMPLE, null));
        mvc.perform(delete("/api/v1/notifications/" + id).header("Authorization", "Bearer " + alice.token()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + alice.token()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/notifications?includeArchived=true").header("Authorization", "Bearer " + alice.token()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void registrationRejectsDuplicateUsernamesAndLoginRejectsBadPasswords() throws Exception {
        TestUser alice = register();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", alice.username(), "email", "a@example.test", "password", "another-long-password"))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", alice.username(), "password", "wrong-password-here"))))
                .andExpect(status().isUnauthorized());
    }
}
