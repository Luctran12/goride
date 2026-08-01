package com.example.goride.analytics.domain;

public enum MatchingOfferOutcome {
    OFFERED,
    ACCEPTED,
    REJECTED,
    TIMEOUT,
    CANCELLED,
    EXPIRED;

    public boolean isTerminal() {
        return this != OFFERED;
    }
}
