package com.example.goride.common.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.List;

@Component
public class StompJwtAuthenticationInterceptor implements ExecutorChannelInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final List<StompSubscriptionAuthorizer> subscriptionAuthorizers;
    private final JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();

    public StompJwtAuthenticationInterceptor(
            JwtDecoder jwtDecoder,
            List<StompSubscriptionAuthorizer> subscriptionAuthorizers
    ) {
        this.jwtDecoder = jwtDecoder;
        this.subscriptionAuthorizers = List.copyOf(subscriptionAuthorizers);
        this.authoritiesConverter.setAuthoritiesClaimName("roles");
        this.authoritiesConverter.setAuthorityPrefix("ROLE_");
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            Authentication authentication = authenticate(accessor);
            accessor.setUser(authentication);
        }

        return message;
    }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && requiresAuthenticatedPrincipal(accessor.getCommand())) {
            Authentication authentication = requireAuthentication(accessor.getUser());
            authorizeSubscription(accessor, authentication);
            setSecurityContext(authentication);
        }
        return message;
    }

    @Override
    public void afterMessageHandled(
            Message<?> message,
            MessageChannel channel,
            MessageHandler handler,
            Exception exception
    ) {
        SecurityContextHolder.clearContext();
    }

    @Override
    public void afterSendCompletion(
            Message<?> message,
            MessageChannel channel,
            boolean sent,
            Exception exception
    ) {
        SecurityContextHolder.clearContext();
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String token = resolveBearerToken(accessor);
        try {
            Jwt jwt = jwtDecoder.decode(token);
            return new JwtAuthenticationToken(
                    jwt,
                    authoritiesConverter.convert(jwt),
                    jwt.getSubject()
            );
        } catch (JwtException exception) {
            throw new BadCredentialsException("Invalid STOMP access token", exception);
        }
    }

    private String resolveBearerToken(StompHeaderAccessor accessor) {
        String authorization = firstNativeHeader(accessor, "Authorization", "authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BadCredentialsException("STOMP Authorization Bearer token is required");
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new BadCredentialsException("STOMP Authorization Bearer token is required");
        }
        return token;
    }

    private String firstNativeHeader(StompHeaderAccessor accessor, String... names) {
        for (String name : names) {
            String value = accessor.getFirstNativeHeader(name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private Authentication requireAuthentication(Principal principal) {
        if (principal instanceof Authentication authentication && authentication.isAuthenticated()) {
            return authentication;
        }
        throw new AccessDeniedException("Authenticated STOMP principal is required");
    }

    private boolean requiresAuthenticatedPrincipal(StompCommand command) {
        return command == StompCommand.SEND || command == StompCommand.SUBSCRIBE;
    }

    private void authorizeSubscription(StompHeaderAccessor accessor, Authentication authentication) {
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return;
        }
        subscriptionAuthorizers.forEach(authorizer ->
                authorizer.authorize(authentication, accessor.getDestination())
        );
    }

    private void setSecurityContext(Authentication authentication) {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
    }
}
