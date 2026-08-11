package com.example.goride.matching.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.matching.route-eta")
public class MatchingRouteEtaProperties {
    private boolean enabled;
    private int candidateLimit = 3;
    private int executorThreads = 6;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCandidateLimit() {
        return candidateLimit;
    }

    public void setCandidateLimit(int candidateLimit) {
        if (candidateLimit < 1 || candidateLimit > 10) {
            throw new IllegalArgumentException("candidateLimit must be between 1 and 10");
        }
        this.candidateLimit = candidateLimit;
    }

    public int getExecutorThreads() {
        return executorThreads;
    }

    public void setExecutorThreads(int executorThreads) {
        if (executorThreads < 1 || executorThreads > 32) {
            throw new IllegalArgumentException("executorThreads must be between 1 and 32");
        }
        this.executorThreads = executorThreads;
    }
}