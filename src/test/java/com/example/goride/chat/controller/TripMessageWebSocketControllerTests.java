package com.example.goride.chat.controller;

import com.example.goride.chat.dto.TripMessageSendRequest;
import com.example.goride.chat.dto.TripMessageResponse;
import com.example.goride.chat.domain.TripMessageSenderRole;
import com.example.goride.chat.service.TripMessageService;
import com.example.goride.common.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripMessageWebSocketControllerTests {
    @Test
    void sendsMessageFromAuthenticatedPrincipal() {
        TripMessageService service = mock(TripMessageService.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        Principal principal = mock(Principal.class);
        TripMessageSendRequest request = new TripMessageSendRequest(
                99L,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Hello"
        );
        when(currentUser.requireUserId(principal)).thenReturn(10L);
        when(service.sendMessage(10L, request)).thenReturn(new TripMessageResponse(
                1L,
                99L,
                10L,
                TripMessageSenderRole.PASSENGER,
                request.clientMessageId(),
                request.body(),
                Instant.parse("2026-08-11T10:00:00Z")
        ));
        TripMessageWebSocketController controller = new TripMessageWebSocketController(service, currentUser);

        var response = controller.sendTripMessage(principal, request);

        verify(service).sendMessage(10L, request);
        org.assertj.core.api.Assertions.assertThat(response.clientMessageId()).isEqualTo(request.clientMessageId());
    }
}
