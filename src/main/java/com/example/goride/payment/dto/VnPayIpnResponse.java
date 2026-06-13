package com.example.goride.payment.dto;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;

public record VnPayIpnResponse(
        @JsonProperty("RspCode")
        String rspCode,
        @JsonProperty("Message")
        String message
) {
    public static VnPayIpnResponse confirmSuccess() {
        return new VnPayIpnResponse("00", "Confirm Success");
    }

    public static VnPayIpnResponse from(BusinessException exception) {
        if (exception.errorCode() == ErrorCode.PAYMENT_NOT_FOUND
                || "VNPay transaction reference is invalid".equals(exception.getMessage())) {
            return new VnPayIpnResponse("01", "Order not found");
        }
        if ("VNPay amount is invalid".equals(exception.getMessage())) {
            return new VnPayIpnResponse("04", "Invalid amount");
        }
        if ("Invalid VNPay secure hash".equals(exception.getMessage())) {
            return new VnPayIpnResponse("97", "Invalid signature");
        }
        return unknownError();
    }

    public static VnPayIpnResponse unknownError() {
        return new VnPayIpnResponse("99", "Unknown error");
    }
}
