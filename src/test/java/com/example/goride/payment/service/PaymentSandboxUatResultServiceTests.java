package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.payment.domain.PaymentSandboxUatResult;
import com.example.goride.payment.domain.PaymentSandboxUatStatus;
import com.example.goride.payment.dto.PaymentProviderReadinessResponse;
import com.example.goride.payment.dto.PaymentSandboxUatResultRequest;
import com.example.goride.payment.repository.PaymentSandboxUatResultRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentSandboxUatResultServiceTests {
    private static final Instant NOW = Instant.parse("2026-07-05T10:00:00Z");

    @Test
    void listResultsReturnsDefaultNotRunEvidenceForReadyProvidersWithoutStoredResult() {
        PaymentSandboxUatResultRepository repository = mock(PaymentSandboxUatResultRepository.class);
        when(repository.findByProviderName("momo")).thenReturn(Optional.empty());
        PaymentSandboxUatResultService service = service(repository, readiness(true));

        var response = service.listResults();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).provider()).isEqualTo("momo");
        assertThat(response.get(0).status()).isEqualTo(PaymentSandboxUatStatus.NOT_RUN);
        assertThat(response.get(0).sandboxReady()).isTrue();
        assertThat(response.get(0).readyForFrontendExposure()).isFalse();
        assertThat(response.get(0).missingChecks())
                .containsExactly(
                        "checkout-url",
                        "success-callback",
                        "failure-callback",
                        "idempotent-replay",
                        "freshness-rejection"
                );
    }

    @Test
    void upsertPassedResultRequiresReadyProviderAndAllChecks() {
        PaymentSandboxUatResultRepository repository = mock(PaymentSandboxUatResultRepository.class);
        when(repository.findByProviderName("momo")).thenReturn(Optional.empty());
        when(repository.save(any(PaymentSandboxUatResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentSandboxUatResultService service = service(repository, readiness(true));

        var response = service.upsertResult("MoMo", passedRequest(null), 99L);

        assertThat(response.status()).isEqualTo(PaymentSandboxUatStatus.PASSED);
        assertThat(response.readyForFrontendExposure()).isTrue();
        assertThat(response.testedAt()).isEqualTo(NOW);
        assertThat(response.testedByUserId()).isEqualTo(99L);
        assertThat(response.notes()).isEqualTo("Sandbox success/failure callbacks verified");
        assertThat(response.missingChecks()).isEmpty();
        verify(repository).save(any(PaymentSandboxUatResult.class));
    }

    @Test
    void upsertPassedResultRejectsMissingChecks() {
        PaymentSandboxUatResultService service = service(mock(PaymentSandboxUatResultRepository.class), readiness(true));
        PaymentSandboxUatResultRequest request = new PaymentSandboxUatResultRequest(
                PaymentSandboxUatStatus.PASSED,
                true,
                true,
                false,
                true,
                true,
                "Failure path not tested",
                NOW
        );

        assertThatThrownBy(() -> service.upsertResult("momo", request, 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.details()).containsEntry("missingChecks", List.of("failure-callback"));
                });
    }

    @Test
    void upsertPassedResultRejectsProviderThatIsNotSandboxReady() {
        PaymentSandboxUatResultService service = service(mock(PaymentSandboxUatResultRepository.class), readiness(false));

        assertThatThrownBy(() -> service.upsertResult("momo", passedRequest(NOW), 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.details()).containsEntry("missingRequirements", List.of("webhook-secret"));
                });
    }

    @Test
    void upsertRejectsUnsupportedProvider() {
        PaymentSandboxUatResultService service = service(mock(PaymentSandboxUatResultRepository.class));

        assertThatThrownBy(() -> service.upsertResult("zalopay", passedRequest(NOW), 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED)
                );
    }

    private PaymentSandboxUatResultRequest passedRequest(Instant testedAt) {
        return new PaymentSandboxUatResultRequest(
                PaymentSandboxUatStatus.PASSED,
                true,
                true,
                true,
                true,
                true,
                " Sandbox success/failure callbacks verified ",
                testedAt
        );
    }

    private PaymentSandboxUatResultService service(
            PaymentSandboxUatResultRepository repository,
            PaymentProviderReadinessResponse... readinessResponses
    ) {
        PaymentProviderReadinessService readinessService = mock(PaymentProviderReadinessService.class);
        when(readinessService.listProviderReadiness()).thenReturn(List.of(readinessResponses));
        return new PaymentSandboxUatResultService(
                readinessService,
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private PaymentProviderReadinessResponse readiness(boolean sandboxReady) {
        return new PaymentProviderReadinessResponse(
                PaymentMethod.MOMO,
                "momo",
                "MoMo",
                true,
                true,
                true,
                true,
                sandboxReady,
                true,
                sandboxReady,
                sandboxReady,
                sandboxReady ? List.of() : List.of("webhook-secret"),
                86_400,
                300
        );
    }
}
