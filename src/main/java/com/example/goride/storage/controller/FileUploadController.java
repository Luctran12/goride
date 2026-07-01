package com.example.goride.storage.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.security.CurrentUser;
import com.example.goride.driver.dto.DriverDocumentType;
import com.example.goride.driver.dto.DriverDocumentUploadResponse;
import com.example.goride.storage.domain.StoredFile;
import com.example.goride.storage.service.FileStorageService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/uploads")
public class FileUploadController {
    private final FileStorageService fileStorageService;
    private final CurrentUser currentUser;

    public FileUploadController(FileStorageService fileStorageService, CurrentUser currentUser) {
        this.fileStorageService = fileStorageService;
        this.currentUser = currentUser;
    }

    @PostMapping(value = "/driver-documents/{documentType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<DriverDocumentUploadResponse> uploadDriverDocument(
            Authentication authentication,
            @PathVariable DriverDocumentType documentType,
            @RequestPart("file") MultipartFile file
    ) {
        Long userId = currentUser.requireUserId(authentication);
        StoredFile storedFile = fileStorageService.store(file, documentType.uploadPurpose(), userId);
        return ApiResponse.ok(DriverDocumentUploadResponse.from(documentType, storedFile));
    }
}