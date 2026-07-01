package com.example.goride.storage.domain;

public enum UploadPurpose {
    USER_AVATAR("avatars"),
    DRIVER_PORTRAIT("driver-documents/portraits"),
    DRIVER_LICENSE("driver-documents/licenses"),
    DRIVER_ID_CARD("driver-documents/id-cards"),
    DRIVER_VEHICLE_REGISTRATION("driver-documents/vehicle-registrations");

    private final String directory;

    UploadPurpose(String directory) {
        this.directory = directory;
    }

    public String directory() {
        return directory;
    }
}