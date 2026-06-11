package com.example.goride.payment.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProviderPropertiesTests {

    @Test
    void exposesSafeDefaultsForOnlineProviders() {
        PaymentProviderProperties properties = new PaymentProviderProperties();

        assertThat(properties.getMomo().isEnabled()).isFalse();
        assertThat(properties.getMomo().isSandbox()).isTrue();
        assertThat(properties.getMomo().hasCheckoutConfiguration()).isFalse();
        assertThat(properties.getMomo().hasWebhookConfiguration()).isFalse();
        assertThat(properties.getVnpay().isEnabled()).isFalse();
        assertThat(properties.getVnpay().isSandbox()).isTrue();
        assertThat(properties.getVnpay().hasCheckoutConfiguration()).isFalse();
        assertThat(properties.getVnpay().hasWebhookConfiguration()).isFalse();
    }

    @Test
    void resolvesProviderSettingsByName() {
        PaymentProviderProperties properties = new PaymentProviderProperties();

        assertThat(properties.settingsFor(" momo ")).isSameAs(properties.getMomo());
        assertThat(properties.settingsFor("VNPAY")).isSameAs(properties.getVnpay());
    }

    @Test
    void normalizesProviderConfigurationValues() {
        PaymentProviderProperties.ProviderSettings settings = new PaymentProviderProperties.ProviderSettings();

        settings.setEnabled(true);
        settings.setSandbox(false);
        settings.setMerchantId(" merchant-001 ");
        settings.setSecretKey(" secret-key ");
        settings.setCheckoutBaseUrl(" https://sandbox.pay.example/checkout ");
        settings.setReturnUrl(" https://api.goride.example/payments/return ");
        settings.setIpnUrl(" https://api.goride.example/payments/ipn ");
        settings.setDefaultIpAddress(" 10.0.0.1 ");
        settings.setWebhookSecret(" webhook-secret ");

        assertThat(settings.isEnabled()).isTrue();
        assertThat(settings.isSandbox()).isFalse();
        assertThat(settings.normalizedMerchantId()).isEqualTo("merchant-001");
        assertThat(settings.normalizedSecretKey()).isEqualTo("secret-key");
        assertThat(settings.normalizedCheckoutBaseUrl()).isEqualTo("https://sandbox.pay.example/checkout");
        assertThat(settings.normalizedReturnUrl()).isEqualTo("https://api.goride.example/payments/return");
        assertThat(settings.normalizedIpnUrl()).isEqualTo("https://api.goride.example/payments/ipn");
        assertThat(settings.normalizedDefaultIpAddress()).isEqualTo("10.0.0.1");
        assertThat(settings.normalizedWebhookSecret()).isEqualTo("webhook-secret");
        assertThat(settings.hasCheckoutConfiguration()).isTrue();
        assertThat(settings.hasWebhookConfiguration()).isTrue();
    }

    @Test
    void checkoutConfigurationRequiresReturnUrlAndHasDefaultIpAddressFallback() {
        PaymentProviderProperties.ProviderSettings settings = new PaymentProviderProperties.ProviderSettings();

        settings.setMerchantId("merchant");
        settings.setSecretKey("secret");
        settings.setCheckoutBaseUrl("https://sandbox.pay.example/checkout");

        assertThat(settings.hasCheckoutConfiguration()).isFalse();
        assertThat(settings.normalizedDefaultIpAddress()).isEqualTo("127.0.0.1");

        settings.setReturnUrl("https://api.goride.example/payments/return");
        settings.setDefaultIpAddress(" ");

        assertThat(settings.hasCheckoutConfiguration()).isTrue();
        assertThat(settings.normalizedDefaultIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void rejectsUnknownOrBlankProviderName() {
        PaymentProviderProperties properties = new PaymentProviderProperties();

        assertThatThrownBy(() -> properties.settingsFor("stripe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported payment provider configuration: stripe");
        assertThatThrownBy(() -> properties.settingsFor(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("providerName must not be blank");
    }
}
