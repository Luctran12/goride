package com.example.goride.chat.controller;

import com.example.goride.chat.dto.TripMessageSendRequest;
import com.example.goride.chat.dto.TripMessageStompAckResponse;
import com.example.goride.chat.dto.TripMessageStompErrorResponse;
import com.example.goride.chat.service.TripMessageService;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class TripMessageWebSocketController {
    private final TripMessageService tripMessageService;
    private final CurrentUser currentUser;

    public TripMessageWebSocketController(TripMessageService tripMessageService, CurrentUser currentUser) {
        this.tripMessageService = tripMessageService;
        this.currentUser = currentUser;
    }

    @MessageMapping("/trip.message")
    @PreAuthorize("hasAnyRole('PASSENGER', 'DRIVER')")
    @SendToUser("/queue/trip-message-acks")
    public TripMessageStompAckResponse sendTripMessage(
            Principal principal,
            @Valid @Payload TripMessageSendRequest request
    ) {
        return TripMessageStompAckResponse.from(
                tripMessageService.sendMessage(currentUser.requireUserId(principal), request)
        );
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/trip-message-errors")
    public TripMessageStompErrorResponse handleBusinessException(BusinessException exception) {
        return new TripMessageStompErrorResponse(
                exception.errorCode().name(),
                exception.getMessage(),
                exception.details()
        );
    }
}
