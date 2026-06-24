package com.example.goride.payment.service;

import com.example.goride.payment.dto.PaymentWebhookResponse;
import com.example.goride.payment.provider.PaymentProvider;
import com.example.goride.payment.provider.PaymentProviderRegistry;
import com.example.goride.payment.provider.PaymentWebhookRequest;
import com.example.goride.payment.provider.PaymentWebhookResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Service
public class PaymentWebhookService {
    private final PaymentProviderRegistry paymentProviderRegistry;
    private final Clock clock;

    public PaymentWebhookService(
            PaymentProviderRegistry paymentProviderRegistry,
            Clock clock
    ) {
        this.paymentProviderRegistry = paymentProviderRegistry;
        this.clock = clock;
    }

    @Transactional
    public PaymentWebhookResponse handleProviderWebhook(
            String providerName,
            Map<String, String> headers,
            Map<String, Object> payload
    ) {
        PaymentProvider provider = paymentProviderRegistry.requireProvider(providerName);
        PaymentWebhookRequest request = new PaymentWebhookRequest(
                provider.providerName(),
                headers,
                payload,
                Instant.now(clock)
        );
        PaymentWebhookResult result = provider.handleWebhook(request);
        return PaymentWebhookResponse.from(provider.providerName(), result);
    }
}
