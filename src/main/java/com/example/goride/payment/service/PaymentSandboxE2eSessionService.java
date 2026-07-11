package com.example.goride.payment.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentSandboxE2eSession;
import com.example.goride.payment.domain.PaymentSandboxUatStatus;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.dto.PaymentProviderReadinessResponse;
import com.example.goride.payment.dto.PaymentSandboxE2eSessionRequest;
import com.example.goride.payment.dto.PaymentSandboxE2eSessionResponse;
import com.example.goride.payment.dto.PaymentSandboxUatResultRequest;
import com.example.goride.payment.repository.PaymentRepository;
import com.example.goride.payment.repository.PaymentSandboxE2eSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class PaymentSandboxE2eSessionService {
    private final PaymentProviderReadinessService paymentProviderReadinessService;
    private final PaymentSandboxE2eSessionRepository sessionRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentSandboxUatResultService paymentSandboxUatResultService;
    private final Clock clock;

    public PaymentSandboxE2eSessionService(
            PaymentProviderReadinessService paymentProviderReadinessService,
            PaymentSandboxE2eSessionRepository sessionRepository,
            PaymentRepository paymentRepository,
            PaymentSandboxUatResultService paymentSandboxUatResultService,
            Clock clock
    ) {
        this.paymentProviderReadinessService = paymentProviderReadinessService;
        this.sessionRepository = sessionRepository;
        this.paymentRepository = paymentRepository;
        this.paymentSandboxUatResultService = paymentSandboxUatResultService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PaymentSandboxE2eSessionResponse> listSessions() {
        return sessionRepository.findAllByOrderByTestedAtDescCreatedAtDesc()
                .stream()
                .map(session -> PaymentSandboxE2eSessionResponse.from(
                        getReadiness(session.getProviderName()),
                        session
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentSandboxE2eSessionResponse> listProviderSessions(String providerName) {
        String normalizedProviderName = normalizeProviderName(providerName);
        PaymentProviderReadinessResponse readiness = getReadiness(normalizedProviderName);
        return sessionRepository.findByProviderNameOrderByTestedAtDescCreatedAtDesc(normalizedProviderName)
                .stream()
                .map(session -> PaymentSandboxE2eSessionResponse.from(readiness, session))
                .toList();
    }

    @Transactional
    public PaymentSandboxE2eSessionResponse recordSession(
            String providerName,
            PaymentSandboxE2eSessionRequest request,
            Long testedByUserId
    ) {
        String normalizedProviderName = normalizeProviderName(providerName);
        PaymentProviderReadinessResponse readiness = getReadiness(normalizedProviderName);
        validateRequest(readiness, request);

        PaymentSandboxE2eSession session = PaymentSandboxE2eSession.create(normalizedProviderName);
        session.update(
                request.status(),
                request.checkoutPaymentId(),
                request.checkoutUrl(),
                request.successPaymentId(),
                request.successTransactionRef(),
                request.failurePaymentId(),
                request.failureTransactionRef(),
                request.replayTransactionRef(),
                request.checkoutUrlTested(),
                request.successCallbackTested(),
                request.failureCallbackTested(),
                request.idempotentReplayTested(),
                request.freshnessRejectionTested(),
                request.notes(),
                testedAt(request),
                testedByUserId
        );
        PaymentSandboxE2eSession savedSession = sessionRepository.save(session);
        paymentSandboxUatResultService.upsertResult(
                normalizedProviderName,
                toAggregateResultRequest(request, savedSession.getTestedAt()),
                testedByUserId
        );
        return PaymentSandboxE2eSessionResponse.from(readiness, savedSession);
    }

    private void validateRequest(
            PaymentProviderReadinessResponse readiness,
            PaymentSandboxE2eSessionRequest request
    ) {
        if (request.status() == PaymentSandboxUatStatus.PASSED && !readiness.sandboxReady()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Provider must be sandboxReady before E2E session can be marked PASSED",
                    Map.of("missingRequirements", readiness.missingRequirements())
            );
        }
        validateCheckoutEvidence(request);
        validateSuccessCallbackEvidence(readiness.provider(), request);
        validateFailureCallbackEvidence(readiness.provider(), request);
        validateReplayEvidence(request);
    }

    private void validateCheckoutEvidence(PaymentSandboxE2eSessionRequest request) {
        if (!request.checkoutUrlTested()) {
            return;
        }
        requireId(request.checkoutPaymentId(), "checkoutPaymentId");
        requireHttpsUrl(request.checkoutUrl(), "checkoutUrl");
    }

    private void validateSuccessCallbackEvidence(
            String providerName,
            PaymentSandboxE2eSessionRequest request
    ) {
        if (!request.successCallbackTested()) {
            return;
        }
        requireId(request.successPaymentId(), "successPaymentId");
        String transactionRef = requireText(request.successTransactionRef(), "successTransactionRef");
        Payment payment = getPayment(request.successPaymentId());
        assertPaymentMatchesProvider(payment, providerName, "successPaymentId");
        assertPaymentStatus(payment, PaymentStatus.COMPLETED, "successPaymentId");
        assertTransactionRef(payment, transactionRef, "successTransactionRef");
    }

    private void validateFailureCallbackEvidence(
            String providerName,
            PaymentSandboxE2eSessionRequest request
    ) {
        if (!request.failureCallbackTested()) {
            return;
        }
        requireId(request.failurePaymentId(), "failurePaymentId");
        String transactionRef = requireText(request.failureTransactionRef(), "failureTransactionRef");
        Payment payment = getPayment(request.failurePaymentId());
        assertPaymentMatchesProvider(payment, providerName, "failurePaymentId");
        assertPaymentStatus(payment, PaymentStatus.FAILED, "failurePaymentId");
        assertTransactionRef(payment, transactionRef, "failureTransactionRef");
    }

    private void validateReplayEvidence(PaymentSandboxE2eSessionRequest request) {
        if (!request.idempotentReplayTested()) {
            return;
        }
        String replayTransactionRef = requireText(request.replayTransactionRef(), "replayTransactionRef");
        if (!Objects.equals(replayTransactionRef, normalize(request.successTransactionRef()))
                && !Objects.equals(replayTransactionRef, normalize(request.failureTransactionRef()))) {
            throw validationError(
                    "Replay transaction reference must match a terminal sandbox callback reference",
                    Map.of("field", "replayTransactionRef")
            );
        }
    }

    private Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private void assertPaymentMatchesProvider(Payment payment, String providerName, String field) {
        if (!payment.getMethod().providerName().equals(providerName)) {
            throw validationError(
                    "Payment method does not match sandbox provider",
                    Map.of(
                            "field", field,
                            "paymentMethod", payment.getMethod(),
                            "provider", providerName
                    )
            );
        }
    }

    private void assertPaymentStatus(Payment payment, PaymentStatus expectedStatus, String field) {
        if (payment.getStatus() != expectedStatus) {
            throw validationError(
                    "Payment status does not match sandbox evidence",
                    Map.of(
                            "field", field,
                            "expectedStatus", expectedStatus,
                            "actualStatus", payment.getStatus()
                    )
            );
        }
    }

    private void assertTransactionRef(Payment payment, String transactionRef, String field) {
        if (!Objects.equals(payment.getTransactionRef(), transactionRef)) {
            throw validationError(
                    "Payment transaction reference does not match sandbox evidence",
                    Map.of("field", field)
            );
        }
    }

    private Long requireId(Long value, String field) {
        if (value == null) {
            throw validationError("Sandbox E2E evidence is missing required payment id", Map.of("field", field));
        }
        return value;
    }

    private String requireText(String value, String field) {
        String normalizedValue = normalize(value);
        if (normalizedValue == null) {
            throw validationError("Sandbox E2E evidence is missing required reference", Map.of("field", field));
        }
        return normalizedValue;
    }

    private void requireHttpsUrl(String value, String field) {
        String normalizedValue = requireText(value, field);
        try {
            URI uri = new URI(normalizedValue);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw validationError("Sandbox checkout URL must use HTTPS", Map.of("field", field));
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw validationError("Sandbox checkout URL must include a host", Map.of("field", field));
            }
        } catch (URISyntaxException exception) {
            throw validationError("Sandbox checkout URL is invalid", Map.of("field", field));
        }
    }

    private PaymentSandboxUatResultRequest toAggregateResultRequest(
            PaymentSandboxE2eSessionRequest request,
            Instant testedAt
    ) {
        return new PaymentSandboxUatResultRequest(
                request.status(),
                request.checkoutUrlTested(),
                request.successCallbackTested(),
                request.failureCallbackTested(),
                request.idempotentReplayTested(),
                request.freshnessRejectionTested(),
                request.notes(),
                testedAt
        );
    }

    private Instant testedAt(PaymentSandboxE2eSessionRequest request) {
        if (request.status() == PaymentSandboxUatStatus.NOT_RUN) {
            return null;
        }
        return request.testedAt() == null ? Instant.now(clock) : request.testedAt();
    }

    private PaymentProviderReadinessResponse getReadiness(String providerName) {
        return paymentProviderReadinessService.listProviderReadiness().stream()
                .filter(readiness -> readiness.provider().equals(providerName))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED,
                        "Payment provider is not supported: " + providerName
                ));
    }

    private String normalizeProviderName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }
        return providerName.strip().toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private BusinessException validationError(String message, Map<String, Object> details) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, details);
    }
}