package com.example.goride.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notifications.fcm")
public class FcmPushProperties {
    private boolean enabled;
    private int maxAttempts = 2;
    private String serviceAccountPath;
    private String projectId;
    private String appName = "goride";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("app.notifications.fcm.max-attempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
    }

    public String getServiceAccountPath() {
        return serviceAccountPath;
    }

    public void setServiceAccountPath(String serviceAccountPath) {
        this.serviceAccountPath = serviceAccountPath;
    }

    public boolean hasServiceAccountPath() {
        return serviceAccountPath != null && !serviceAccountPath.isBlank();
    }

    public String normalizedServiceAccountPath() {
        return serviceAccountPath == null ? null : serviceAccountPath.strip();
    }

    public String credentialSource() {
        return hasServiceAccountPath()
                ? "service account file " + normalizedServiceAccountPath()
                : "Google Application Default Credentials";
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String normalizedProjectId() {
        return projectId == null || projectId.isBlank() ? null : projectId.strip();
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        if (appName == null || appName.isBlank()) {
            throw new IllegalArgumentException("app.notifications.fcm.app-name must not be blank");
        }
        this.appName = appName.strip();
    }
}
