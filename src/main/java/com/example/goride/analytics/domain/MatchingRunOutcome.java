package com.example.goride.analytics.domain;

public enum MatchingRunOutcome {
    IN_PROGRESS,
    MATCHED,
    NO_DRIVER,
    CANCELLED,
    FAILED;

    public boolean isTerminal() {
        return this != IN_PROGRESS;
    }
}
