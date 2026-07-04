package com.example.goride.booking.security;

import com.example.goride.booking.repository.TripRepository;
import com.example.goride.common.security.StompSubscriptionAuthorizer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TripTopicSubscriptionAuthorizer implements StompSubscriptionAuthorizer {
    private static final Pattern TRIP_TOPIC_PATTERN =
            Pattern.compile("^/topic/trip/(\\d+)/(status|location|messages)$");

    private final TripRepository tripRepository;

    public TripTopicSubscriptionAuthorizer(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    @Override
    public void authorize(Authentication authentication, String destination) {
        if (!StringUtils.hasText(destination)) {
            return;
        }

        Matcher matcher = TRIP_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return;
        }

        if (hasAdminRole(authentication)) {
            return;
        }

        Long tripId = Long.valueOf(matcher.group(1));
        Long userId = parseUserId(authentication);
        if (!tripRepository.existsAccessibleTripTopicByUserId(tripId, userId)) {
            throw new AccessDeniedException("User is not allowed to subscribe to this trip topic");
        }
    }

    private boolean hasAdminRole(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private Long parseUserId(Authentication authentication) {
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("Authenticated STOMP principal subject is invalid", exception);
        }
    }
}
