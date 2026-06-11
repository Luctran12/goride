package com.example.goride.payment.provider;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record PaymentWebhookRequest(
        String providerName,
        Map<String, String> headers,
        Map<String, Object> payload,
        Instant receivedAt
) {
    public PaymentWebhookRequest {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("providerName must not be blank");
        }
        if (receivedAt == null) {
            throw new IllegalArgumentException("receivedAt must not be null");
        }
        providerName = providerName.strip();
        headers = immutableCopy(headers);
        payload = immutableCopy(payload);
    }

    private static <K, V> Map<K, V> immutableCopy(Map<K, V> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
