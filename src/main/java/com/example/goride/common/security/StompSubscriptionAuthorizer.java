package com.example.goride.common.security;

import org.springframework.security.core.Authentication;

public interface StompSubscriptionAuthorizer {
    void authorize(Authentication authentication, String destination);
}
