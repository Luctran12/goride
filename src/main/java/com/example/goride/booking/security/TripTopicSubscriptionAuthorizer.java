package com.example.goride.booking.security;

import com.example.goride.booking.repository.TripRepository;
import com.example.goride.common.security.StompSubscriptionAuthorizer;
import com.example.goride.matching.service.OfferedTripAccessService;
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
            Pattern.compile("^/topic/trip/(\\d+)/(status|location|messages|message-read)$");

    private final TripRepository tripRepository;
    private final OfferedTripAccessService offeredTripAccessService;

    public TripTopicSubscriptionAuthorizer(
            TripRepository tripRepository,
            OfferedTripAccessService offeredTripAccessService
    ) {
        this.tripRepository = tripRepository;
        this.offeredTripAccessService = offeredTripAccessService;
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
        String topicType = matcher.group(2);
        Long userId = parseUserId(authentication);
        if (tripRepository.existsAccessibleTripTopicByUserId(tripId, userId)) {
            return;
        }
        if ("status".equals(topicType)
                && hasRole(authentication, "ROLE_DRIVER")
                && offeredTripAccessService.hasActiveOffer(tripId, userId)) {
            return;
        }
        throw new AccessDeniedException("User is not allowed to subscribe to this trip topic");
    }

    private boolean hasAdminRole(Authentication authentication) {
        return hasRole(authentication, "ROLE_ADMIN");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }

    private Long parseUserId(Authentication authentication) {
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("Authenticated STOMP principal subject is invalid", exception);
        }
    }
}
