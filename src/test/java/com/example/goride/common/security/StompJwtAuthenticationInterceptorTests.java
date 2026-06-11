package com.example.goride.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StompJwtAuthenticationInterceptorTests {
    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final MessageChannel channel = mock(MessageChannel.class);
    private final MessageHandler handler = mock(MessageHandler.class);
    private final StompSubscriptionAuthorizer subscriptionAuthorizer = mock(StompSubscriptionAuthorizer.class);
    private final StompJwtAuthenticationInterceptor interceptor = new StompJwtAuthenticationInterceptor(
            jwtDecoder,
            List.of(subscriptionAuthorizer)
    );

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesConnectFrameWithBearerToken() {
        Jwt jwt = jwt("42", "DRIVER");
        when(jwtDecoder.decode("access-token")).thenReturn(jwt);
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, "Bearer access-token", null);

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(accessor.getUser()).isInstanceOf(JwtAuthenticationToken.class);
        JwtAuthenticationToken authentication = (JwtAuthenticationToken) accessor.getUser();
        assertThat(authentication.getName()).isEqualTo("42");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_DRIVER");
    }

    @Test
    void rejectsConnectFrameWithoutBearerToken() {
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, null, null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("STOMP Authorization Bearer token is required");
    }

    @Test
    void rejectsConnectFrameWithInvalidToken() {
        when(jwtDecoder.decode("bad-token")).thenThrow(new JwtException("expired"));
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, "Bearer bad-token", null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid STOMP access token");
    }

    @Test
    void setsSecurityContextForAuthenticatedSendFrame() {
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt("42", "DRIVER"),
                List.of(new SimpleGrantedAuthority("ROLE_DRIVER")),
                "42"
        );
        Message<byte[]> message = stompMessage(StompCommand.SEND, null, authentication);

        Message<?> result = interceptor.beforeHandle(message, channel, handler);

        assertThat(result).isSameAs(message);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);

        interceptor.afterMessageHandled(result, channel, handler, null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(subscriptionAuthorizer);
    }

    @Test
    void delegatesAuthenticatedSubscribeFrameToSubscriptionAuthorizers() {
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt("42", "PASSENGER"),
                List.of(new SimpleGrantedAuthority("ROLE_PASSENGER")),
                "42"
        );
        Message<byte[]> message = stompMessage(
                StompCommand.SUBSCRIBE,
                null,
                authentication,
                "/topic/trip/99/status"
        );

        interceptor.beforeHandle(message, channel, handler);

        verify(subscriptionAuthorizer).authorize(authentication, "/topic/trip/99/status");
    }

    @Test
    void rejectsSendFrameWithoutAuthenticatedPrincipal() {
        Message<byte[]> message = stompMessage(StompCommand.SEND, null, null);

        assertThatThrownBy(() -> interceptor.beforeHandle(message, channel, handler))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Authenticated STOMP principal is required");
    }

    private Message<byte[]> stompMessage(
            StompCommand command,
            String authorization,
            JwtAuthenticationToken user
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> stompMessage(
            StompCommand command,
            String authorization,
            JwtAuthenticationToken user,
            String destination
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Jwt jwt(String subject, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .claim("roles", List.of(role))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .build();
    }
}
