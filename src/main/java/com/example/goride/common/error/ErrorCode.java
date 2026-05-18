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
    TRIP_ALREADY_RATED(HttpStatus.CONFLICT, "Trip has already been rated"),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Refresh token has expired, please log in again"),
    DRIVER_NOT_APPROVED(HttpStatus.UNPROCESSABLE_ENTITY, "Driver has not been approved"),
    DRIVER_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Driver is not available"),
    PASSENGER_HAS_ACTIVE_TRIP(HttpStatus.UNPROCESSABLE_ENTITY, "Passenger already has an active trip"),
    TRIP_CANNOT_BE_CANCELLED(HttpStatus.UNPROCESSABLE_ENTITY, "Trip cannot be cancelled in its current status"),
    TRIP_STATUS_INVALID_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "Trip status transition is invalid"),
    LOCATION_OUT_OF_SERVICE_AREA(HttpStatus.UNPROCESSABLE_ENTITY, "Location is outside the service area"),
    NO_DRIVER_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "No driver is available nearby"),
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
