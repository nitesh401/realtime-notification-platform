package com.example.notify.api.realtime;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Authentication and destination-level authorisation for STOMP.
 *  - CONNECT must carry "Authorization: Bearer <jwt>"; the principal name becomes the user id.
 *  - SUBSCRIBE is allowed ONLY to /user/queue/notifications. Spring resolves that to the caller's own queue, so
 *    there is no destination a user could name to read someone else's stream, and everything else is rejected.
 *  - SEND is allowed ONLY to /app/ack.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    public static final String USER_QUEUE = "/user/queue/notifications";
    public static final String ACK_DESTINATION = "/app/ack";

    private static final Logger log = LoggerFactory.getLogger(StompAuthInterceptor.class);

    private final JwtDecoder decoder;

    public StompAuthInterceptor(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            requireUser(accessor);
            if (!USER_QUEUE.equals(accessor.getDestination())) {
                throw new MessageDeliveryException("Subscription to this destination is not permitted");
            }
        } else if (StompCommand.SEND.equals(command)) {
            requireUser(accessor);
            if (!ACK_DESTINATION.equals(accessor.getDestination())) {
                throw new MessageDeliveryException("Sending to this destination is not permitted");
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        List<String> headers = accessor.getNativeHeader("Authorization");
        String header = (headers == null || headers.isEmpty()) ? null : headers.get(0);
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Authentication required");
        }
        try {
            Jwt jwt = decoder.decode(header.substring(7));
            accessor.setUser(new UsernamePasswordAuthenticationToken(jwt.getSubject(), null, List.of()));
        } catch (JwtException e) {
            log.debug("Rejected STOMP CONNECT: invalid token"); // never log the token itself
            throw new MessageDeliveryException("Invalid or expired token");
        }
    }

    private static void requireUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessageDeliveryException("Not authenticated");
        }
    }
}
