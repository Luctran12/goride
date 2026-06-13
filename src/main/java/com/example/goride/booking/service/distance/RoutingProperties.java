package com.example.goride.booking.service.distance;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.routing")
public class RoutingProperties {
    private boolean enabled;
    private String baseUrl = "https://router.project-osrm.org";
    private String profile = "driving";
    private long timeoutSeconds = 5;
    private boolean fallbackEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Routing base URL must not be blank");
        }
        String normalized = baseUrl.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri = URI.create(normalized);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("Routing base URL must be an HTTP URL");
        }
        return normalized;
    }

    public String normalizedProfile() {
        if (profile == null || !profile.strip().matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Routing profile is invalid");
        }
        return profile.strip();
    }

    public Duration timeout() {
        return Duration.ofSeconds(timeoutSeconds);
    }
}
