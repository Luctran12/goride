package com.example.goride.storage.service;

import com.example.goride.storage.domain.StoredFile;
import com.example.goride.storage.domain.UploadPurpose;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    StoredFile store(MultipartFile file, UploadPurpose purpose, Long ownerId);
}