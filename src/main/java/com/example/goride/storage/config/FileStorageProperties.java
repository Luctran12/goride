package com.example.goride.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app.storage")
public class FileStorageProperties {
    private Provider provider = Provider.LOCAL;
    private Path localRoot = Path.of("uploads");
    private String publicBaseUrl = "/uploads";
    private DataSize maxFileSize = DataSize.ofMegabytes(5);
    private List<String> allowedContentTypes = List.of("image/jpeg", "image/png", "image/webp");
    private R2Properties r2 = new R2Properties();

    public enum Provider {
        LOCAL,
        R2
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public Path getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(Path localRoot) {
        this.localRoot = localRoot;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }

    public R2Properties getR2() {
        return r2;
    }

    public void setR2(R2Properties r2) {
        this.r2 = r2;
    }

    public static class R2Properties {
        private String endpoint = "";
        private String region = "auto";
        private String bucket = "";
        private String accessKey = "";
        private String secretKey = "";
        private String publicBaseUrl = "";
        private boolean pathStyleAccessEnabled = true;
        private Duration apiCallTimeout = Duration.ofSeconds(30);
        private Duration apiCallAttemptTimeout = Duration.ofSeconds(10);

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region == null || region.isBlank() ? "auto" : region.trim();
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }

        public boolean isPathStyleAccessEnabled() {
            return pathStyleAccessEnabled;
        }

        public void setPathStyleAccessEnabled(boolean pathStyleAccessEnabled) {
            this.pathStyleAccessEnabled = pathStyleAccessEnabled;
        }

        public Duration getApiCallTimeout() {
            return apiCallTimeout;
        }

        public void setApiCallTimeout(Duration apiCallTimeout) {
            this.apiCallTimeout = apiCallTimeout;
        }

        public Duration getApiCallAttemptTimeout() {
            return apiCallAttemptTimeout;
        }

        public void setApiCallAttemptTimeout(Duration apiCallAttemptTimeout) {
            this.apiCallAttemptTimeout = apiCallAttemptTimeout;
        }
    }
}