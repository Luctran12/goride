package com.example.goride.storage.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.storage.config.FileStorageProperties;
import com.example.goride.storage.domain.StoredFile;
import com.example.goride.storage.domain.UploadPurpose;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {
    private final FileStorageProperties properties;
    private final Path root;

    public LocalFileStorageService(FileStorageProperties properties) {
        this.properties = properties;
        this.root = properties.getLocalRoot().toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(MultipartFile file, UploadPurpose purpose, Long ownerId) {
        FileStorageSupport.validate(file, purpose, ownerId, properties);

        String contentType = FileStorageSupport.normalizeContentType(file.getContentType());
        String objectKey = FileStorageSupport.objectKey(purpose, ownerId, contentType);
        Path destination = root.resolve(objectKey).normalize();
        if (!destination.startsWith(root)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_INVALID, "File destination is invalid");
        }

        try {
            Files.createDirectories(destination.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Unable to store uploaded file");
        }

        return new StoredFile(publicUrl(objectKey), objectKey, contentType, file.getSize());
    }

    private String publicUrl(String objectKey) {
        return FileStorageSupport.publicUrl(properties.getPublicBaseUrl(), objectKey);
    }
}