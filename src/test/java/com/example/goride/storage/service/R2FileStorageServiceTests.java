package com.example.goride.storage.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.storage.config.FileStorageProperties;
import com.example.goride.storage.domain.UploadPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class R2FileStorageServiceTests {
    private FileStorageProperties properties;
    private S3Client s3Client;
    private R2FileStorageService service;

    @BeforeEach
    void setUp() {
        properties = new FileStorageProperties();
        properties.setMaxFileSize(DataSize.ofBytes(10));
        properties.setAllowedContentTypes(List.of("image/png", "image/jpeg"));
        properties.getR2().setBucket("goride-uploads");
        properties.getR2().setPublicBaseUrl("https://cdn.example.com");

        s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").build());
        service = new R2FileStorageService(properties, s3Client);
    }

    @Test
    void storesAllowedImageInR2AndReturnsPublicUrl() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        var stored = service.store(file, UploadPurpose.USER_AVATAR, 10L);

        assertThat(stored.objectKey()).startsWith("avatars/10/").endsWith(".png");
        assertThat(stored.url()).isEqualTo("https://cdn.example.com/" + stored.objectKey());
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.sizeBytes()).isEqualTo(3);
        verify(s3Client).putObject(
                org.mockito.ArgumentMatchers.<PutObjectRequest>argThat(request ->
                        request.bucket().equals("goride-uploads")
                                && request.key().equals(stored.objectKey())
                                && request.contentType().equals("image/png")
                                && request.contentLength().equals(3L)
                ),
                any(RequestBody.class)
        );
    }

    @Test
    void rejectsUnsupportedContentTypeBeforeCallingR2() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.gif",
                "image/gif",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> service.store(file, UploadPurpose.USER_AVATAR, 10L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FILE_UPLOAD_INVALID)
                );
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void wrapsR2ClientFailuresAsStorageError() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.builder().message("timeout").build());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> service.store(file, UploadPurpose.USER_AVATAR, 10L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FILE_STORAGE_ERROR)
                );
    }
}