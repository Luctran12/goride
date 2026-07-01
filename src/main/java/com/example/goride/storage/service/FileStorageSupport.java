package com.example.goride.storage.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.storage.config.FileStorageProperties;
import com.example.goride.storage.domain.UploadPurpose;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class FileStorageSupport {
    private FileStorageSupport() {
    }

    static void validate(MultipartFile file, UploadPurpose purpose, Long ownerId, FileStorageProperties properties) {
        if (purpose == null) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_INVALID, "Upload purpose is required");
        }
        if (ownerId == null) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_INVALID, "Upload owner is required");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_INVALID, "Uploaded file must not be empty");
        }
        if (file.getSize() > properties.getMaxFileSize().toBytes()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_INVALID, "Uploaded file is too large");
        }
        String contentType = normalizeContentType(file.getContentType());
        Set<String> allowed = properties.getAllowedContentTypes().stream()
                .map(FileStorageSupport::normalizeContentType)
                .collect(Collectors.toSet());
        if (!allowed.contains(contentType)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_INVALID, "Uploaded file type is not supported");
        }
    }

    static String objectKey(UploadPurpose purpose, Long ownerId, String contentType) {
        String extension = extensionFor(contentType);
        return purpose.directory() + "/" + ownerId + "/" + UUID.randomUUID() + extension;
    }

    static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    static String publicUrl(String baseUrl, String objectKey) {
        String normalizedBaseUrl = baseUrl == null || baseUrl.isBlank()
                ? "/uploads"
                : baseUrl.trim();
        return normalizedBaseUrl.replaceAll("/+$", "") + "/" + objectKey.replace("\\", "/");
    }

    private static String extensionFor(String contentType) {
        return switch (normalizeContentType(contentType)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }
}