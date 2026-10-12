package com.example.notify.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real PostgreSQL + real Kafka (Testcontainers). Containers follow the singleton pattern so every
 * test class shares one Spring context and one set of containers. Requires a running Docker daemon.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.jwt.secret", () -> "test-only-secret-test-only-secret-0123456789");
        registry.add("app.rate-limit.general-per-minute", () -> "100000");
        registry.add("app.rate-limit.auth-per-minute", () -> "100000");
        registry.add("app.outbox.poll-interval-ms", () -> "200");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:8080");
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;

    /** A registered user: id, username and bearer token. */
    protected record TestUser(UUID id, String username, String token) { }

    protected TestUser register() throws Exception {
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String body = mapper.writeValueAsString(java.util.Map.of("username", username,
                "email", username + "@example.test", "password", "correct-horse-battery"));
        String json = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(json);
        return new TestUser(UUID.fromString(node.get("user").get("id").asText()), username, node.get("token").asText());
    }
}
