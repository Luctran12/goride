package com.example.goride.payment.service;

import com.example.goride.payment.config.PaymentProviderProperties;
import com.example.goride.payment.dto.PaymentProviderReadinessResponse;
import com.example.goride.payment.dto.PaymentSandboxUatPlanResponse;
import com.example.goride.payment.dto.PaymentSandboxUatResultResponse;
import com.example.goride.payment.repository.PaymentSandboxUatResultRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentSandboxUatPlanService {
    private static final String PAYMENT_CHECKOUT_ENDPOINT = "GET /api/v1/payments/trips/{tripId}/checkout";
    private static final String PAYMENT_DETAIL_ENDPOINT = "GET /api/v1/payments/trips/{tripId}";
    private static final String PAYMENT_METHODS_ENDPOINT = "GET /api/v1/payments/methods";

    private final PaymentProviderReadinessService paymentProviderReadinessService;
    private final PaymentProviderProperties paymentProviderProperties;
    private final PaymentSandboxUatResultRepository paymentSandboxUatResultRepository;

    public PaymentSandboxUatPlanService(
            PaymentProviderReadinessService paymentProviderReadinessService,
            PaymentProviderProperties paymentProviderProperties,
            PaymentSandboxUatResultRepository paymentSandboxUatResultRepository
    ) {
        this.paymentProviderReadinessService = paymentProviderReadinessService;
        this.paymentProviderProperties = paymentProviderProperties;
        this.paymentSandboxUatResultRepository = paymentSandboxUatResultRepository;
    }

    public PaymentSandboxUatPlanResponse getSandboxUatPlan() {
        return new PaymentSandboxUatPlanResponse(
                prerequisites(),
                paymentProviderReadinessService.listProviderReadiness().stream()
                        .map(this::providerPlan)
                        .toList(),
                validationScenarios()
        );
    }

    private PaymentSandboxUatPlanResponse.PaymentProviderSandboxUatResponse providerPlan(
            PaymentProviderReadinessResponse readiness
    ) {
        PaymentProviderProperties.ProviderSettings settings =
                paymentProviderProperties.settingsFor(readiness.provider());
        PaymentSandboxUatResultResponse latestUatResult = PaymentSandboxUatResultResponse.from(
                readiness,
                paymentSandboxUatResultRepository.findByProviderName(readiness.provider()).orElse(null)
        );
        return new PaymentSandboxUatPlanResponse.PaymentProviderSandboxUatResponse(
                readiness.method(),
                readiness.provider(),
                readiness.displayName(),
                status(readiness),
                readiness.enabled(),
                readiness.sandbox(),
                readiness.checkoutReady(),
                readiness.webhookReady(),
                readiness.sandboxReady(),
                latestUatResult.readyForFrontendExposure(),
                latestUatResult,
                PAYMENT_CHECKOUT_ENDPOINT,
                webhookEndpoint(readiness.provider()),
                settings.normalizedReturnUrl(),
                settings.normalizedIpnUrl(),
                readiness.missingRequirements(),
                frontendActions(readiness),
                backendChecks(readiness)
        );
    }

    private List<String> prerequisites() {
        return List.of(
                "Expose backend through a public HTTPS URL that the payment sandbox can call.",
                "Configure provider sandbox merchant credentials through environment variables or secret manager.",
                "Register the backend webhook/IPN URL in the provider sandbox console before enabling online payment in FE.",
                "Keep CASH available as the fallback payment method until real sandbox callbacks pass end to end."
        );
    }

    private List<String> validationScenarios() {
        return List.of(
                "Create a completed trip payment, open the checkout URL, then finish a sandbox success payment.",
                "Run a provider-declined or cancelled sandbox payment and verify the backend keeps the trip/payment consistent.",
                "Replay the same signed provider callback and verify the terminal payment update is idempotent.",
                "Send stale or future-dated signed callbacks in sandbox/staging and confirm backend rejects them.",
                "Refresh payment detail after redirect and confirm the FE state matches webhook-driven backend state."
        );
    }

    private List<String> frontendActions(PaymentProviderReadinessResponse readiness) {
        if (!readiness.sandboxReady()) {
            return List.of(
                    "Hide or disable this online payment method outside admin/UAT screens.",
                    "Use " + PAYMENT_METHODS_ENDPOINT + " and readiness status before exposing checkout.",
                    "Show CASH fallback while missing requirements are resolved."
            );
        }
        return List.of(
                "Show this payment method only when " + PAYMENT_METHODS_ENDPOINT + " marks it enabled.",
                "Call " + PAYMENT_CHECKOUT_ENDPOINT + " after trip completion and open the returned checkoutUrl.",
                "After provider redirect, call " + PAYMENT_DETAIL_ENDPOINT + " until payment status is terminal."
        );
    }

    private List<String> backendChecks(PaymentProviderReadinessResponse readiness) {
        if (!readiness.sandboxReady()) {
            return List.of(
                    "Resolve missingRequirements before running real provider sandbox UAT.",
                    "Verify provider callback URL reaches the backend over HTTPS.",
                    "Do not enable production exposure until both checkoutReady and webhookReady are true."
            );
        }
        return List.of(
                "Confirm checkout returns a provider URL without PAYMENT_PROVIDER_ERROR.",
                "Confirm provider webhook/IPN updates payment status through the shared completion workflow.",
                "Confirm duplicate terminal callbacks are accepted idempotently with the same transaction reference."
        );
    }

    private String webhookEndpoint(String provider) {
        return "POST /api/v1/payments/providers/" + provider + "/webhook";
    }

    private String status(PaymentProviderReadinessResponse readiness) {
        if (!readiness.providerRegistered()) {
            return "NOT_REGISTERED";
        }
        if (!readiness.enabled()) {
            return "DISABLED";
        }
        if (!readiness.sandbox()) {
            return "SANDBOX_DISABLED";
        }
        if (!readiness.checkoutReady() || !readiness.webhookReady()) {
            return "BLOCKED_BY_CONFIG";
        }
        return "READY_FOR_SANDBOX_UAT";
    }
}
