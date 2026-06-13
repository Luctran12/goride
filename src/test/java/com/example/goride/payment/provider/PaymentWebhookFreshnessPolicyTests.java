package com.example.goride.payment.provider;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.payment.config.PaymentProviderProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentWebhookFreshnessPolicyTests {
    private static final Instant RECEIVED_AT = Instant.parse("2026-06-13T06:00:00Z");

    private final PaymentWebhookFreshnessPolicy policy = new PaymentWebhookFreshnessPolicy();
    private final PaymentProviderProperties.ProviderSettings settings =
            new PaymentProviderProperties.ProviderSettings();

    @Test
    void acceptsFreshCallbackWithinConfiguredWindow() {
        assertThatCode(() -> policy.validate(
                RECEIVED_AT.minusSeconds(86_400),
                request(),
                settings,
                false
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsStaleCallbackThatIsNotAnIdempotentReplay() {
        assertValidationError(
                () -> policy.validate(
                        RECEIVED_AT.minusSeconds(86_401),
                        request(),
                        settings,
                        false
                ),
                "Payment provider callback is too old"
        );
    }

    @Test
    void acceptsStaleIdempotentReplay() {
        assertThatCode(() -> policy.validate(
                RECEIVED_AT.minusSeconds(604_800),
                request(),
                settings,
                true
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsFutureCallbackEvenWhenItMatchesAnIdempotentReplay() {
        assertValidationError(
                () -> policy.validate(
                        RECEIVED_AT.plusSeconds(301),
                        request(),
                        settings,
                        true
                ),
                "Payment provider callback timestamp is in the future"
        );
    }

    @Test
    void honorsProviderSpecificFreshnessConfiguration() {
        settings.setWebhookMaxAgeSeconds(60);
        settings.setWebhookFutureSkewSeconds(10);

        assertValidationError(
                () -> policy.validate(RECEIVED_AT.minusSeconds(61), request(), settings, false),
                "Payment provider callback is too old"
        );
        assertValidationError(
                () -> policy.validate(RECEIVED_AT.plusSeconds(11), request(), settings, false),
                "Payment provider callback timestamp is in the future"
        );
    }

    private PaymentWebhookRequest request() {
        return new PaymentWebhookRequest("momo", Map.of(), Map.of(), RECEIVED_AT);
    }

    private void assertValidationError(Runnable action, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                )
                .hasMessage(message);
    }
}
