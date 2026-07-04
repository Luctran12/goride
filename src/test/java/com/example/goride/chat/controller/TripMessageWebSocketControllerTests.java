package com.example.goride.chat.controller;

import com.example.goride.chat.dto.TripMessageSendRequest;
import com.example.goride.chat.service.TripMessageService;
import com.example.goride.common.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripMessageWebSocketControllerTests {
    @Test
    void sendsMessageFromAuthenticatedPrincipal() {
        TripMessageService service = mock(TripMessageService.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        Principal principal = mock(Principal.class);
        TripMessageSendRequest request = new TripMessageSendRequest(99L, "Hello");
        when(currentUser.requireUserId(principal)).thenReturn(10L);
        TripMessageWebSocketController controller = new TripMessageWebSocketController(service, currentUser);

        controller.sendTripMessage(principal, request);

        verify(service).sendMessage(10L, request);
    }
}
