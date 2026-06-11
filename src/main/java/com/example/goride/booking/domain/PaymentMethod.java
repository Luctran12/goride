package com.example.goride.booking.domain;

public enum PaymentMethod {
    CASH("cash", "Cash", false),
    MOMO("momo", "MoMo", true),
    VNPAY("vnpay", "VNPay", true);

    private final String providerName;
    private final String displayName;
    private final boolean checkoutRequired;

    PaymentMethod(String providerName, String displayName, boolean checkoutRequired) {
        this.providerName = providerName;
        this.displayName = displayName;
        this.checkoutRequired = checkoutRequired;
    }

    public String providerName() {
        return providerName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean checkoutRequired() {
        return checkoutRequired;
    }
}
