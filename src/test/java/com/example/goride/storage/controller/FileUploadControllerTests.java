package com.example.goride.storage.controller;

import com.example.goride.common.security.CurrentUser;
import com.example.goride.driver.dto.DriverDocumentType;
import com.example.goride.storage.domain.StoredFile;
import com.example.goride.storage.domain.UploadPurpose;
import com.example.goride.storage.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileUploadControllerTests {
    @Test
    void uploadsDriverDocumentForAuthenticatedDriver() {
        FileStorageService fileStorageService = mock(FileStorageService.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        Authentication authentication = mock(Authentication.class);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "license.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        StoredFile storedFile = new StoredFile(
                "/uploads/driver-documents/licenses/10/license.png",
                "driver-documents/licenses/10/license.png",
                "image/png",
                3
        );
        when(currentUser.requireUserId(authentication)).thenReturn(10L);
        when(fileStorageService.store(file, UploadPurpose.DRIVER_LICENSE, 10L)).thenReturn(storedFile);
        FileUploadController controller = new FileUploadController(fileStorageService, currentUser);

        var response = controller.uploadDriverDocument(authentication, DriverDocumentType.LICENSE, file);

        assertThat(response.data().documentType()).isEqualTo(DriverDocumentType.LICENSE);
        assertThat(response.data().url()).isEqualTo(storedFile.url());
        assertThat(response.data().objectKey()).isEqualTo(storedFile.objectKey());
        verify(fileStorageService).store(file, UploadPurpose.DRIVER_LICENSE, 10L);
    }
}