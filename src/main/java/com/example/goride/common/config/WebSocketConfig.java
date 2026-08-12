package com.example.goride.common.config;

import com.example.goride.auth.config.CorsProperties;
import com.example.goride.common.security.StompJwtAuthenticationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final StompJwtAuthenticationInterceptor stompJwtAuthenticationInterceptor;
    private final CorsProperties corsProperties;

    public WebSocketConfig(
            StompJwtAuthenticationInterceptor stompJwtAuthenticationInterceptor,
            CorsProperties corsProperties
    ) {
        this.stompJwtAuthenticationInterceptor = stompJwtAuthenticationInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompJwtAuthenticationInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        configureOrigins(registry.addEndpoint("/ws")).withSockJS();
        configureOrigins(registry.addEndpoint("/ws-native"));
    }

    private StompWebSocketEndpointRegistration configureOrigins(
            StompWebSocketEndpointRegistration registration
    ) {
        if (!corsProperties.allowedOrigins().isEmpty()) {
            registration.setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
        }
        if (!corsProperties.allowedOriginPatterns().isEmpty()) {
            registration.setAllowedOriginPatterns(corsProperties.allowedOriginPatterns().toArray(String[]::new));
        }
        return registration;
    }
}
