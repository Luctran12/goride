package com.example.goride.payment.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.payment.domain.PaymentSandboxUatResult;
import com.example.goride.payment.domain.PaymentSandboxUatStatus;
import com.example.goride.payment.dto.PaymentProviderReadinessResponse;
import com.example.goride.payment.dto.PaymentSandboxUatResultRequest;
import com.example.goride.payment.dto.PaymentSandboxUatResultResponse;
import com.example.goride.payment.repository.PaymentSandboxUatResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PaymentSandboxUatResultService {
    private final PaymentProviderReadinessService paymentProviderReadinessService;
    private final PaymentSandboxUatResultRepository resultRepository;
    private final Clock clock;

    public PaymentSandboxUatResultService(
            PaymentProviderReadinessService paymentProviderReadinessService,
            PaymentSandboxUatResultRepository resultRepository,
            Clock clock
    ) {
        this.paymentProviderReadinessService = paymentProviderReadinessService;
        this.resultRepository = resultRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PaymentSandboxUatResultResponse> listResults() {
        return paymentProviderReadinessService.listProviderReadiness().stream()
                .map(readiness -> PaymentSandboxUatResultResponse.from(
                        readiness,
                        resultRepository.findByProviderName(readiness.provider()).orElse(null)
                ))
                .toList();
    }

    @Transactional
    public PaymentSandboxUatResultResponse upsertResult(
            String providerName,
            PaymentSandboxUatResultRequest request,
            Long testedByUserId
    ) {
        String normalizedProviderName = normalizeProviderName(providerName);
        PaymentProviderReadinessResponse readiness = getReadiness(normalizedProviderName);
        validateRequest(readiness, request);

        PaymentSandboxUatResult result = resultRepository.findByProviderName(normalizedProviderName)
                .orElseGet(() -> PaymentSandboxUatResult.create(normalizedProviderName));
        result.update(
                request.status(),
                request.checkoutUrlTested(),
                request.successCallbackTested(),
                request.failureCallbackTested(),
                request.idempotentReplayTested(),
                request.freshnessRejectionTested(),
                request.notes(),
                testedAt(request),
                testedByUserId
        );
        PaymentSandboxUatResult savedResult = resultRepository.save(result);
        return PaymentSandboxUatResultResponse.from(readiness, savedResult);
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

    private void validateRequest(
            PaymentProviderReadinessResponse readiness,
            PaymentSandboxUatResultRequest request
    ) {
        if (request.status() == PaymentSandboxUatStatus.PASSED && !readiness.sandboxReady()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Provider must be sandboxReady before UAT can be marked PASSED",
                    Map.of("missingRequirements", readiness.missingRequirements())
            );
        }
        if (request.status() == PaymentSandboxUatStatus.PASSED && !hasAllRequiredChecks(request)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "All sandbox UAT checks are required before marking provider PASSED",
                    Map.of("missingChecks", missingChecks(request))
            );
        }
    }

    private Instant testedAt(PaymentSandboxUatResultRequest request) {
        if (request.status() == PaymentSandboxUatStatus.NOT_RUN) {
            return null;
        }
        return request.testedAt() == null ? Instant.now(clock) : request.testedAt();
    }

    private boolean hasAllRequiredChecks(PaymentSandboxUatResultRequest request) {
        return request.checkoutUrlTested()
                && request.successCallbackTested()
                && request.failureCallbackTested()
                && request.idempotentReplayTested()
                && request.freshnessRejectionTested();
    }

    private List<String> missingChecks(PaymentSandboxUatResultRequest request) {
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        if (!request.checkoutUrlTested()) {
            missing.add("checkout-url");
        }
        if (!request.successCallbackTested()) {
            missing.add("success-callback");
        }
        if (!request.failureCallbackTested()) {
            missing.add("failure-callback");
        }
        if (!request.idempotentReplayTested()) {
            missing.add("idempotent-replay");
        }
        if (!request.freshnessRejectionTested()) {
            missing.add("freshness-rejection");
        }
        return List.copyOf(missing);
    }

    private String normalizeProviderName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }
        return providerName.strip().toLowerCase(Locale.ROOT);
    }
}
