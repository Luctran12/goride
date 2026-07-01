package com.example.goride.storage.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.storage.config.FileStorageProperties;
import com.example.goride.storage.domain.StoredFile;
import com.example.goride.storage.domain.UploadPurpose;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;

@Service
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "r2")
public class R2FileStorageService implements FileStorageService {
    private final FileStorageProperties properties;
    private final FileStorageProperties.R2Properties r2;
    private final S3Client s3Client;

    public R2FileStorageService(FileStorageProperties properties, S3Client s3Client) {
        this.properties = properties;
        this.r2 = properties.getR2();
        this.s3Client = s3Client;
    }

    @Override
    public StoredFile store(MultipartFile file, UploadPurpose purpose, Long ownerId) {
        FileStorageSupport.validate(file, purpose, ownerId, properties);

        String contentType = FileStorageSupport.normalizeContentType(file.getContentType());
        String objectKey = FileStorageSupport.objectKey(purpose, ownerId, contentType);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(r2.getBucket().trim())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(file.getSize())
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, file.getSize()));
        } catch (IOException | S3Exception | SdkClientException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Unable to store uploaded file");
        }

        return new StoredFile(
                FileStorageSupport.publicUrl(r2.getPublicBaseUrl(), objectKey),
                objectKey,
                contentType,
                file.getSize()
        );
    }
}