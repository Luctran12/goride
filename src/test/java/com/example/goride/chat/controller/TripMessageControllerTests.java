package com.example.goride.chat.controller;

import com.example.goride.chat.domain.TripMessageSenderRole;
import com.example.goride.chat.dto.TripMessageCreateRequest;
import com.example.goride.chat.dto.TripMessageResponse;
import com.example.goride.chat.dto.TripMessageSendRequest;
import com.example.goride.chat.service.TripMessageService;
import com.example.goride.common.api.PageResponse;
import com.example.goride.common.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripMessageControllerTests {
    @Test
    void sendsMessageThroughRestEndpoint() {
        TripMessageService service = mock(TripMessageService.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        var authentication = authentication("10", "ROLE_PASSENGER");
        var request = new TripMessageCreateRequest("Hello");
        var expected = response();
        when(currentUser.requireUserId(authentication)).thenReturn(10L);
        when(service.sendMessage(10L, 99L, request)).thenReturn(expected);
        TripMessageController controller = new TripMessageController(service, currentUser);

        var response = controller.sendMessage(authentication, 99L, request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(expected);
        verify(service).sendMessage(10L, 99L, request);
    }

    @Test
    void listsMessagesWithAdminFlag() {
        TripMessageService service = mock(TripMessageService.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        var authentication = authentication("1", "ROLE_ADMIN");
        var page = PageResponse.of(List.of(response()), 1, 50, 1);
        when(currentUser.requireUserId(authentication)).thenReturn(1L);
        when(service.listMessages(1L, true, 99L, 1, 50)).thenReturn(page);
        TripMessageController controller = new TripMessageController(service, currentUser);

        var response = controller.listMessages(authentication, 99L, 1, 50);

        assertThat(response.data()).isEqualTo(page);
        verify(service).listMessages(1L, true, 99L, 1, 50);
    }

    private UsernamePasswordAuthenticationToken authentication(String userId, String role) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                "n/a",
                List.of(new SimpleGrantedAuthority(role))
        );
    }

    private TripMessageResponse response() {
        return new TripMessageResponse(
                1L,
                99L,
                10L,
                TripMessageSenderRole.PASSENGER,
                "Hello",
                Instant.parse("2026-07-01T10:00:00Z")
        );
    }
}
