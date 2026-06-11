package com.example.goride.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class CurrentUser {
    public Long requireUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Authenticated JWT principal is required");
        }
        return Long.parseLong(jwt.getSubject());
    }

    public Long requireUserId(Principal principal) {
        if (principal instanceof Authentication authentication) {
            return requireUserId(authentication);
        }
        throw new IllegalStateException("Authenticated principal is required");
    }
}
