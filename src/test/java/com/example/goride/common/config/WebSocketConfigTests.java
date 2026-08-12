package com.example.goride.common.config;

import com.example.goride.auth.config.CorsProperties;
import com.example.goride.common.security.StompJwtAuthenticationInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

class WebSocketConfigTests {
    @Test
    void registersSockJsAndNativeWebSocketEndpoints() {
        StompJwtAuthenticationInterceptor interceptor = mock(StompJwtAuthenticationInterceptor.class);
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration sockJsEndpoint = mock(StompWebSocketEndpointRegistration.class);
        StompWebSocketEndpointRegistration nativeEndpoint = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(sockJsEndpoint);
        when(sockJsEndpoint.setAllowedOrigins("http://localhost:5173")).thenReturn(sockJsEndpoint);
        when(registry.addEndpoint("/ws-native")).thenReturn(nativeEndpoint);
        when(nativeEndpoint.setAllowedOrigins("http://localhost:5173")).thenReturn(nativeEndpoint);

        CorsProperties cors = new CorsProperties(
                List.of("http://localhost:5173"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                3600
        );
        new WebSocketConfig(interceptor, cors).registerStompEndpoints(registry);

        verify(sockJsEndpoint).withSockJS();
        verify(nativeEndpoint).setAllowedOrigins("http://localhost:5173");
    }
}
