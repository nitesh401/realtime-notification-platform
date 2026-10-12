package com.example.notify.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.notify.common.Category;
import com.example.notify.common.RealtimeCommand;
import com.example.notify.common.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

class WebSocketIT extends AbstractIntegrationTest {

    @LocalServerPort int port;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper appMapper;

    private WebSocketStompClient client() {
        WebSocketStompClient c = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        c.setMessageConverter(converter);
        return c;
    }

    private static final class Session extends StompSessionHandlerAdapter {
        final CountDownLatch errorFrame = new CountDownLatch(1);
        final AtomicBoolean connected = new AtomicBoolean();
        final CountDownLatch transportError = new CountDownLatch(1);
        @Override public void afterConnected(StompSession session, StompHeaders headers) { connected.set(true); }
        @Override public void handleFrame(StompHeaders headers, Object payload) { errorFrame.countDown(); }
        @Override public void handleTransportError(StompSession session, Throwable exception) { transportError.countDown(); }
    }

    private StompSession connect(String token, Session handler) throws Exception {
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        return client().connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers, handler)
                .get(15, TimeUnit.SECONDS);
    }

    private BlockingQueue<Map<?, ?>> subscribeToQueue(StompSession session) {
        BlockingQueue<Map<?, ?>> queue = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/notifications", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { queue.add((Map<?, ?>) payload); }
        });
        return queue;
    }

    private void pushCommand(UUID recipient, String subject) throws Exception {
        RealtimeCommand command = new RealtimeCommand(UUID.randomUUID(), UUID.randomUUID(), recipient, Category.INFO,
                subject, "body", Instant.now(), "test");
        kafka.send(Topics.REALTIME, recipient.toString(), appMapper.writeValueAsString(command)).get(10, TimeUnit.SECONDS);
    }

    @Test
    void connectedUserReceivesPushAndOtherUsersDoNot() throws Exception {
        TestUser alice = register();
        TestUser bob = register();
        StompSession aliceSession = connect(alice.token(), new Session());
        StompSession bobSession = connect(bob.token(), new Session());
        BlockingQueue<Map<?, ?>> aliceQueue = subscribeToQueue(aliceSession);
        BlockingQueue<Map<?, ?>> bobQueue = subscribeToQueue(bobSession);

        // The SUBSCRIBE frame is asynchronous, so keep pushing until the first frame lands (duplicates are harmless).
        Map<?, ?> received = null;
        for (int i = 0; i < 20 && received == null; i++) {
            pushCommand(alice.id(), "live-" + i);
            received = aliceQueue.poll(1, TimeUnit.SECONDS);
        }
        assertThat(received).isNotNull();
        assertThat(received.get("subject").toString()).startsWith("live-");
        assertThat(bobQueue.poll(2, TimeUnit.SECONDS)).isNull(); // bob never sees alice's stream
        aliceSession.disconnect();
        bobSession.disconnect();
    }

    @Test
    void connectWithoutValidTokenIsRejected() throws Exception {
        for (String token : new String[]{null, "garbage.token.value"}) {
            Session handler = new Session();
            try {
                connect(token, handler);
            } catch (Exception expected) {
                // connection future failing is an acceptable form of rejection
            }
            boolean rejected = handler.errorFrame.await(10, TimeUnit.SECONDS) || handler.transportError.await(1, TimeUnit.SECONDS)
                    || !handler.connected.get();
            assertThat(rejected).isTrue();
            assertThat(handler.connected.get()).isFalse();
        }
    }

    @Test
    void subscribingToAnyOtherDestinationIsRejected() throws Exception {
        TestUser alice = register();
        TestUser bob = register();
        for (String destination : new String[]{"/topic/notifications", "/queue/notifications",
                "/user/" + bob.id() + "/queue/notifications", "/user/queue/other"}) {
            Session handler = new Session();
            StompSession session = connect(alice.token(), handler);
            session.subscribe(destination, new StompFrameHandler() {
                @Override public Type getPayloadType(StompHeaders headers) { return Map.class; }
                @Override public void handleFrame(StompHeaders headers, Object payload) { }
            });
            assertThat(handler.errorFrame.await(10, TimeUnit.SECONDS)).as("rejected: " + destination).isTrue();
        }
    }
}
