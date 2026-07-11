package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentSandboxE2eSession;
import com.example.goride.payment.domain.PaymentSandboxUatStatus;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.dto.PaymentProviderReadinessResponse;
import com.example.goride.payment.dto.PaymentSandboxE2eSessionRequest;
import com.example.goride.payment.dto.PaymentSandboxUatResultRequest;
import com.example.goride.payment.repository.PaymentRepository;
import com.example.goride.payment.repository.PaymentSandboxE2eSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentSandboxE2eSessionServiceTests {
    private static final Instant NOW = Instant.parse("2026-07-10T08:00:00Z");

    @Test
    void recordPassedSessionPersistsEvidenceAndUpdatesAggregateUatResult() {
        PaymentSandboxE2eSessionRepository sessionRepository = mock(PaymentSandboxE2eSessionRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentSandboxUatResultService resultService = mock(PaymentSandboxUatResultService.class);
        when(sessionRepository.save(any(PaymentSandboxE2eSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Payment successPayment = payment(PaymentStatus.COMPLETED, "SUCCESS-TXN");
        when(paymentRepository.findById(101L)).thenReturn(Optional.of(successPayment));
        Payment failedPayment = payment(PaymentStatus.FAILED, "FAILED-TXN");
        when(paymentRepository.findById(102L)).thenReturn(Optional.of(failedPayment));
        PaymentSandboxE2eSessionService service = service(
                sessionRepository,
                paymentRepository,
                resultService,
                readiness(true)
        );

        var response = service.recordSession("MoMo", passedRequest(), 77L);

        assertThat(response.provider()).isEqualTo("momo");
        assertThat(response.status()).isEqualTo(PaymentSandboxUatStatus.PASSED);
        assertThat(response.sessionEvidencePassed()).isTrue();
        assertThat(response.testedAt()).isEqualTo(NOW);
        assertThat(response.testedByUserId()).isEqualTo(77L);
        assertThat(response.missingChecks()).isEmpty();

        ArgumentCaptor<PaymentSandboxUatResultRequest> aggregateRequest =
                ArgumentCaptor.forClass(PaymentSandboxUatResultRequest.class);
        verify(resultService).upsertResult(eq("momo"), aggregateRequest.capture(), eq(77L));
        assertThat(aggregateRequest.getValue().status()).isEqualTo(PaymentSandboxUatStatus.PASSED);
        assertThat(aggregateRequest.getValue().checkoutUrlTested()).isTrue();
        assertThat(aggregateRequest.getValue().successCallbackTested()).isTrue();
        assertThat(aggregateRequest.getValue().failureCallbackTested()).isTrue();
        assertThat(aggregateRequest.getValue().idempotentReplayTested()).isTrue();
        assertThat(aggregateRequest.getValue().freshnessRejectionTested()).isTrue();
    }

    @Test
    void recordPassedSessionRejectsProviderThatIsNotSandboxReady() {
        PaymentSandboxE2eSessionRepository sessionRepository = mock(PaymentSandboxE2eSessionRepository.class);
        PaymentSandboxE2eSessionService service = service(
                sessionRepository,
                mock(PaymentRepository.class),
                mock(PaymentSandboxUatResultService.class),
                readiness(false)
        );

        assertThatThrownBy(() -> service.recordSession("momo", passedRequest(), 77L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.details()).containsEntry("missingRequirements", List.of("webhook-secret"));
                });
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void recordSessionRejectsSuccessEvidenceWhenPaymentIsNotCompleted() {
        PaymentSandboxE2eSessionRepository sessionRepository = mock(PaymentSandboxE2eSessionRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        Payment pendingPayment = payment(PaymentStatus.PENDING, "SUCCESS-TXN");
        when(paymentRepository.findById(101L)).thenReturn(Optional.of(pendingPayment));
        PaymentSandboxE2eSessionService service = service(
                sessionRepository,
                paymentRepository,
                mock(PaymentSandboxUatResultService.class),
                readiness(true)
        );

        assertThatThrownBy(() -> service.recordSession("momo", passedRequest(), 77L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.details()).containsEntry("expectedStatus", PaymentStatus.COMPLETED);
                    assertThat(exception.details()).containsEntry("actualStatus", PaymentStatus.PENDING);
                });
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void recordSessionRejectsNonHttpsCheckoutEvidence() {
        PaymentSandboxE2eSessionRepository sessionRepository = mock(PaymentSandboxE2eSessionRepository.class);
        PaymentSandboxE2eSessionService service = service(
                sessionRepository,
                mock(PaymentRepository.class),
                mock(PaymentSandboxUatResultService.class),
                readiness(true)
        );
        PaymentSandboxE2eSessionRequest request = new PaymentSandboxE2eSessionRequest(
                PaymentSandboxUatStatus.FAILED,
                100L,
                "http://sandbox.example/checkout",
                null,
                null,
                null,
                null,
                null,
                true,
                false,
                false,
                false,
                false,
                "Checkout URL was not secure",
                NOW
        );

        assertThatThrownBy(() -> service.recordSession("momo", request, 77L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.details()).containsEntry("field", "checkoutUrl");
                });
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void listProviderSessionsUsesReadinessMetadata() {
        PaymentSandboxE2eSessionRepository sessionRepository = mock(PaymentSandboxE2eSessionRepository.class);
        PaymentSandboxE2eSession session = PaymentSandboxE2eSession.create("momo");
        session.update(
                PaymentSandboxUatStatus.FAILED,
                100L,
                "https://sandbox.example/checkout",
                null,
                null,
                null,
                null,
                null,
                true,
                false,
                false,
                false,
                false,
                "Checkout opened but callback failed",
                NOW,
                77L
        );
        when(sessionRepository.findByProviderNameOrderByTestedAtDescCreatedAtDesc("momo"))
                .thenReturn(List.of(session));
        PaymentSandboxE2eSessionService service = service(
                sessionRepository,
                mock(PaymentRepository.class),
                mock(PaymentSandboxUatResultService.class),
                readiness(true)
        );

        var response = service.listProviderSessions("MOMO");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).displayName()).isEqualTo("MoMo");
        assertThat(response.get(0).missingChecks())
                .containsExactly("success-callback", "failure-callback", "idempotent-replay", "freshness-rejection");
    }

    @Test
    void listProviderSessionsKeepsSessionEvidenceIndependentFromCurrentReadiness() {
        PaymentSandboxE2eSessionRepository sessionRepository = mock(PaymentSandboxE2eSessionRepository.class);
        PaymentSandboxE2eSession session = PaymentSandboxE2eSession.create("momo");
        session.update(
                PaymentSandboxUatStatus.PASSED,
                100L,
                "https://sandbox.example/checkout",
                101L,
                "SUCCESS-TXN",
                102L,
                "FAILED-TXN",
                "SUCCESS-TXN",
                true,
                true,
                true,
                true,
                true,
                "Historical sandbox evidence passed",
                NOW,
                77L
        );
        when(sessionRepository.findByProviderNameOrderByTestedAtDescCreatedAtDesc("momo"))
                .thenReturn(List.of(session));
        PaymentSandboxE2eSessionService service = service(
                sessionRepository,
                mock(PaymentRepository.class),
                mock(PaymentSandboxUatResultService.class),
                readiness(false)
        );

        var response = service.listProviderSessions("momo").get(0);

        assertThat(response.sandboxReady()).isFalse();
        assertThat(response.sessionEvidencePassed()).isTrue();
    }

    private PaymentSandboxE2eSessionRequest passedRequest() {
        return new PaymentSandboxE2eSessionRequest(
                PaymentSandboxUatStatus.PASSED,
                100L,
                "https://sandbox.example/checkout/abc",
                101L,
                "SUCCESS-TXN",
                102L,
                "FAILED-TXN",
                "SUCCESS-TXN",
                true,
                true,
                true,
                true,
                true,
                "MoMo sandbox success, failure, replay and freshness checks passed",
                null
        );
    }

    private Payment payment(PaymentStatus status, String transactionRef) {
        Payment payment = mock(Payment.class);
        when(payment.getMethod()).thenReturn(PaymentMethod.MOMO);
        when(payment.getStatus()).thenReturn(status);
        when(payment.getTransactionRef()).thenReturn(transactionRef);
        return payment;
    }

    private PaymentSandboxE2eSessionService service(
            PaymentSandboxE2eSessionRepository sessionRepository,
            PaymentRepository paymentRepository,
            PaymentSandboxUatResultService resultService,
            PaymentProviderReadinessResponse... readinessResponses
    ) {
        PaymentProviderReadinessService readinessService = mock(PaymentProviderReadinessService.class);
        when(readinessService.listProviderReadiness()).thenReturn(List.of(readinessResponses));
        return new PaymentSandboxE2eSessionService(
                readinessService,
                sessionRepository,
                paymentRepository,
                resultService,
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