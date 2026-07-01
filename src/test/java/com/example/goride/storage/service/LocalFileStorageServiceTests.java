package com.example.goride.storage.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.storage.config.FileStorageProperties;
import com.example.goride.storage.domain.UploadPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTests {
    @TempDir
    Path tempDir;

    private FileStorageProperties properties;
    private LocalFileStorageService service;

    @BeforeEach
    void setUp() {
        properties = new FileStorageProperties();
        properties.setLocalRoot(tempDir);
        properties.setPublicBaseUrl("https://cdn.example.com/uploads/");
        properties.setMaxFileSize(DataSize.ofBytes(10));
        properties.setAllowedContentTypes(List.of("image/png", "image/jpeg"));
        service = new LocalFileStorageService(properties);
    }

    @Test
    void storesAllowedImageUnderPurposeAndOwnerDirectory() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        var stored = service.store(file, UploadPurpose.USER_AVATAR, 10L);

        assertThat(stored.objectKey()).startsWith("avatars/10/").endsWith(".png");
        assertThat(stored.url()).isEqualTo("https://cdn.example.com/uploads/" + stored.objectKey());
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.sizeBytes()).isEqualTo(3);
        assertThat(Files.exists(tempDir.resolve(stored.objectKey()))).isTrue();
    }

    @Test
    void rejectsEmptyUpload() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.store(file, UploadPurpose.USER_AVATAR, 10L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FILE_UPLOAD_INVALID)
                );
    }

    @Test
    void rejectsOversizedUpload() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.png",
                "image/png",
                new byte[11]
        );

        assertThatThrownBy(() -> service.store(file, UploadPurpose.USER_AVATAR, 10L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FILE_UPLOAD_INVALID)
                );
    }

    @Test
    void rejectsUnsupportedContentType() {
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
    }
}