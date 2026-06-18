package com.example.goride.common.config;

import com.example.goride.common.security.StompJwtAuthenticationInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigTests {
    @Test
    void registersSockJsAndNativeWebSocketEndpoints() {
        StompJwtAuthenticationInterceptor interceptor = mock(StompJwtAuthenticationInterceptor.class);
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration sockJsEndpoint = mock(StompWebSocketEndpointRegistration.class);
        StompWebSocketEndpointRegistration nativeEndpoint = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(sockJsEndpoint);
        when(sockJsEndpoint.setAllowedOriginPatterns("*")).thenReturn(sockJsEndpoint);
        when(registry.addEndpoint("/ws-native")).thenReturn(nativeEndpoint);
        when(nativeEndpoint.setAllowedOriginPatterns("*")).thenReturn(nativeEndpoint);

        new WebSocketConfig(interceptor).registerStompEndpoints(registry);

        verify(sockJsEndpoint).withSockJS();
        verify(nativeEndpoint).setAllowedOriginPatterns("*");
    }
}
