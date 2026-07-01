package com.example.goride.driver.dto;

import com.example.goride.storage.domain.StoredFile;

public record DriverDocumentUploadResponse(
        DriverDocumentType documentType,
        String url,
        String objectKey,
        String contentType,
        long sizeBytes
) {
    public static DriverDocumentUploadResponse from(DriverDocumentType documentType, StoredFile storedFile) {
        return new DriverDocumentUploadResponse(
                documentType,
                storedFile.url(),
                storedFile.objectKey(),
                storedFile.contentType(),
                storedFile.sizeBytes()
        );
    }
}