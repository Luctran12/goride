package com.example.goride.storage.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class R2StorageConfigTests {
    @Test
    void requiresR2ConfigWhenProviderIsR2() {
        FileStorageProperties properties = new FileStorageProperties();

        assertThatThrownBy(() -> new R2StorageConfig().r2S3Client(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.storage.r2.endpoint");
    }
}