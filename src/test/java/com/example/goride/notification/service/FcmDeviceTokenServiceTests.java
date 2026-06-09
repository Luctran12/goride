package com.example.goride.notification.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.notification.dto.FcmTokenUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FcmDeviceTokenServiceTests {
    @Mock
    private FcmDeviceTokenStore fcmDeviceTokenStore;

    private FcmDeviceTokenService service;

    @BeforeEach
    void setUp() {
        service = new FcmDeviceTokenService(fcmDeviceTokenStore);
    }

    @Test
    void updateTokenStoresTrimmedFcmTokenForUser() {
        var response = service.updateToken(10L, new FcmTokenUpdateRequest("  fcm-token-123  "));

        verify(fcmDeviceTokenStore).saveToken(10L, "fcm-token-123");
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.registered()).isTrue();
    }

    @Test
    void updateTokenRejectsBlankToken() {
        assertThatThrownBy(() -> service.updateToken(10L, new FcmTokenUpdateRequest("  ")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(fcmDeviceTokenStore);
    }

    @Test
    void updateTokenRejectsOverlongToken() {
        String token = "a".repeat(4097);

        assertThatThrownBy(() -> service.updateToken(10L, new FcmTokenUpdateRequest(token)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(fcmDeviceTokenStore);
    }

    @Test
    void updateTokenRejectsMissingUserId() {
        assertThatThrownBy(() -> service.updateToken(null, new FcmTokenUpdateRequest("fcm-token-123")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(fcmDeviceTokenStore);
    }

    @Test
    void deleteTokenRemovesStoredTokenForUser() {
        var response = service.deleteToken(10L);

        verify(fcmDeviceTokenStore).deleteToken(10L);
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.registered()).isFalse();
    }

    @Test
    void deleteTokenRejectsMissingUserId() {
        assertThatThrownBy(() -> service.deleteToken(null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(fcmDeviceTokenStore);
    }
}
