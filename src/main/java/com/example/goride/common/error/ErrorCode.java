package com.example.goride.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request is invalid"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid phone or password"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Access token has expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token is invalid"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "Trip not found"),
    DRIVER_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Driver profile not found"),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Phone number is already registered"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email is already registered"),
    DRIVER_PROFILE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Driver profile already exists"),
    LICENSE_NUMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "License number is already registered"),
    ID_CARD_ALREADY_EXISTS(HttpStatus.CONFLICT, "ID card number is already registered"),
    VEHICLE_PLATE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Vehicle plate is already registered"),
    PRICING_CONFIG_NOT_FOUND(HttpStatus.UNPROCESSABLE_ENTITY, "Pricing is not configured for this vehicle type"),
    TRIP_ALREADY_RATED(HttpStatus.CONFLICT, "Trip has already been rated"),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Refresh token has expired, please log in again"),
    DRIVER_NOT_APPROVED(HttpStatus.UNPROCESSABLE_ENTITY, "Driver has not been approved"),
    DRIVER_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Driver is not available"),
    PASSENGER_HAS_ACTIVE_TRIP(HttpStatus.UNPROCESSABLE_ENTITY, "Passenger already has an active trip"),
    TRIP_CANNOT_BE_CANCELLED(HttpStatus.UNPROCESSABLE_ENTITY, "Trip cannot be cancelled in its current status"),
    TRIP_STATUS_INVALID_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "Trip status transition is invalid"),
    LOCATION_OUT_OF_SERVICE_AREA(HttpStatus.UNPROCESSABLE_ENTITY, "Location is outside the service area"),
    DRIVER_LOCATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Driver location not found"),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Notification not found"),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Payment not found"),
    PAYMENT_PROVIDER_UNSUPPORTED(HttpStatus.UNPROCESSABLE_ENTITY, "Payment provider is not supported"),
    PAYMENT_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "Payment provider request failed"),
    ROUTING_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "Routing provider request failed"),
    FILE_UPLOAD_INVALID(HttpStatus.BAD_REQUEST, "Uploaded file is invalid"),
    FILE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "File storage failed"),
    TRIP_ROUTE_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Routing is not available for this trip status"),
    TRIP_MESSAGE_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Messaging is not available for this trip status"),
    PAYMENT_INVALID_STATUS(HttpStatus.UNPROCESSABLE_ENTITY, "Payment status is invalid"),
    NO_DRIVER_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "No driver is available nearby"),
    MATCHING_OFFER_NOT_FOUND(HttpStatus.NOT_FOUND, "Matching offer not found"),
    MATCHING_OFFER_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Matching offer has expired"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
