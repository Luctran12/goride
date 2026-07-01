package com.example.goride.storage.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "r2")
public class R2StorageConfig {
    @Bean(destroyMethod = "close")
    public S3Client r2S3Client(FileStorageProperties properties) {
        FileStorageProperties.R2Properties r2 = properties.getR2();
        requireText(r2.getEndpoint(), "app.storage.r2.endpoint");
        requireText(r2.getBucket(), "app.storage.r2.bucket");
        requireText(r2.getAccessKey(), "app.storage.r2.access-key");
        requireText(r2.getSecretKey(), "app.storage.r2.secret-key");
        requireText(r2.getPublicBaseUrl(), "app.storage.r2.public-base-url");

        return S3Client.builder()
                .endpointOverride(URI.create(r2.getEndpoint().trim()))
                .region(Region.of(r2.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(r2.getAccessKey().trim(), r2.getSecretKey().trim())
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(r2.isPathStyleAccessEnabled())
                        .build())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(r2.getApiCallTimeout())
                        .apiCallAttemptTimeout(r2.getApiCallAttemptTimeout())
                        .build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    private void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " is required when app.storage.provider=r2");
        }
    }
}