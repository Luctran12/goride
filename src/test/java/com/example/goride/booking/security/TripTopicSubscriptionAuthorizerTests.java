package com.example.goride.booking.security;

import com.example.goride.booking.repository.TripRepository;
import com.example.goride.matching.service.OfferedTripAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripTopicSubscriptionAuthorizerTests {
    @Mock
    private TripRepository tripRepository;

    @Mock
    private OfferedTripAccessService offeredTripAccessService;

    private TripTopicSubscriptionAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        authorizer = new TripTopicSubscriptionAuthorizer(tripRepository, offeredTripAccessService);
    }

    @Test
    void allowsPassengerOrDriverAssignedToTripTopic() {
        when(tripRepository.existsAccessibleTripTopicByUserId(99L, 10L)).thenReturn(true);

        authorizer.authorize(authentication("10", "ROLE_PASSENGER"), "/topic/trip/99/messages");

        verify(tripRepository).existsAccessibleTripTopicByUserId(99L, 10L);
    }

    @Test
    void allowsParticipantToSubscribeToMessageReadTopic() {
        when(tripRepository.existsAccessibleTripTopicByUserId(99L, 10L)).thenReturn(true);

        authorizer.authorize(authentication("10", "ROLE_PASSENGER"), "/topic/trip/99/message-read");

        verify(tripRepository).existsAccessibleTripTopicByUserId(99L, 10L);
    }

    @Test
    void rejectsUserWhoIsNotPassengerOrDriverOfTripTopic() {
        when(tripRepository.existsAccessibleTripTopicByUserId(99L, 10L)).thenReturn(false);

        assertThatThrownBy(() ->
                authorizer.authorize(authentication("10", "ROLE_PASSENGER"), "/topic/trip/99/location")
        )
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("User is not allowed to subscribe to this trip topic");
    }

    @Test
    void allowsOfferedDriverToSubscribeToTripStatusBeforeAccepting() {
        when(tripRepository.existsAccessibleTripTopicByUserId(99L, 20L)).thenReturn(false);
        when(offeredTripAccessService.hasActiveOffer(99L, 20L)).thenReturn(true);

        authorizer.authorize(authentication("20", "ROLE_DRIVER"), "/topic/trip/99/status");
    }

    @Test
    void rejectsOfferedDriverFromTripMessagesBeforeAccepting() {
        when(tripRepository.existsAccessibleTripTopicByUserId(99L, 20L)).thenReturn(false);

        assertThatThrownBy(() ->
                authorizer.authorize(authentication("20", "ROLE_DRIVER"), "/topic/trip/99/messages")
        )
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("User is not allowed to subscribe to this trip topic");
    }

    @Test
    void allowsAdminWithoutTripOwnershipLookup() {
        authorizer.authorize(authentication("1", "ROLE_ADMIN"), "/topic/trip/99/status");

        verifyNoInteractions(tripRepository, offeredTripAccessService);
    }

    @Test
    void ignoresNonTripTopicDestinations() {
        authorizer.authorize(authentication("10", "ROLE_PASSENGER"), "/user/queue/notifications");

        verifyNoInteractions(tripRepository, offeredTripAccessService);
    }

    @Test
    void rejectsInvalidAuthenticatedSubjectForTripTopic() {
        assertThatThrownBy(() ->
                authorizer.authorize(authentication("not-a-number", "ROLE_PASSENGER"), "/topic/trip/99/status")
        )
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Authenticated STOMP principal subject is invalid");

        verifyNoInteractions(offeredTripAccessService);
    }

    private UsernamePasswordAuthenticationToken authentication(String userId, String role) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                "n/a",
                List.of(new SimpleGrantedAuthority(role))
        );
    }
}
