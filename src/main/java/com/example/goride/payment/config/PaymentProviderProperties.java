package com.example.goride.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

@ConfigurationProperties(prefix = "app.payments.providers")
public class PaymentProviderProperties {
    private ProviderSettings momo = new ProviderSettings();
    private ProviderSettings vnpay = new ProviderSettings();

    public ProviderSettings getMomo() {
        return momo;
    }

    public void setMomo(ProviderSettings momo) {
        this.momo = momo == null ? new ProviderSettings() : momo;
    }

    public ProviderSettings getVnpay() {
        return vnpay;
    }

    public void setVnpay(ProviderSettings vnpay) {
        this.vnpay = vnpay == null ? new ProviderSettings() : vnpay;
    }

    public ProviderSettings settingsFor(String providerName) {
        return switch (normalizeProviderName(providerName)) {
            case "momo" -> momo;
            case "vnpay" -> vnpay;
            default -> throw new IllegalArgumentException(
                    "Unsupported payment provider configuration: " + providerName
            );
        };
    }

    private String normalizeProviderName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("providerName must not be blank");
        }
        return providerName.strip().toLowerCase(Locale.ROOT);
    }

    public static class ProviderSettings {
        private boolean enabled;
        private boolean sandbox = true;
        private String merchantId;
        private String secretKey;
        private String checkoutBaseUrl;
        private String webhookSecret;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isSandbox() {
            return sandbox;
        }

        public void setSandbox(boolean sandbox) {
            this.sandbox = sandbox;
        }

        public String getMerchantId() {
            return merchantId;
        }

        public void setMerchantId(String merchantId) {
            this.merchantId = merchantId;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getCheckoutBaseUrl() {
            return checkoutBaseUrl;
        }

        public void setCheckoutBaseUrl(String checkoutBaseUrl) {
            this.checkoutBaseUrl = checkoutBaseUrl;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }

        public String normalizedMerchantId() {
            return normalize(merchantId);
        }

        public String normalizedSecretKey() {
            return normalize(secretKey);
        }

        public String normalizedCheckoutBaseUrl() {
            return normalize(checkoutBaseUrl);
        }

        public String normalizedWebhookSecret() {
            return normalize(webhookSecret);
        }

        public boolean hasCheckoutConfiguration() {
            return normalizedMerchantId() != null
                    && normalizedSecretKey() != null
                    && normalizedCheckoutBaseUrl() != null;
        }

        public boolean hasWebhookConfiguration() {
            return normalizedWebhookSecret() != null;
        }

        private String normalize(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.strip();
        }
    }
}
