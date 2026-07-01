package com.example.goride.driver.dto;

import com.example.goride.storage.domain.UploadPurpose;

public enum DriverDocumentType {
    PORTRAIT(UploadPurpose.DRIVER_PORTRAIT),
    LICENSE(UploadPurpose.DRIVER_LICENSE),
    ID_CARD(UploadPurpose.DRIVER_ID_CARD),
    VEHICLE_REGISTRATION(UploadPurpose.DRIVER_VEHICLE_REGISTRATION);

    private final UploadPurpose uploadPurpose;

    DriverDocumentType(UploadPurpose uploadPurpose) {
        this.uploadPurpose = uploadPurpose;
    }

    public UploadPurpose uploadPurpose() {
        return uploadPurpose;
    }
}