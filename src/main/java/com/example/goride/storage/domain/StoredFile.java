package com.example.goride.storage.domain;

public record StoredFile(
        String url,
        String objectKey,
        String contentType,
        long sizeBytes
) {
}