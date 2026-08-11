package com.example.goride.matching.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchingRouteEtaPropertiesTests {
    @Test
    void defaultsKeepRankingOptInWithThreeCandidateLimit() {
        MatchingRouteEtaProperties properties = new MatchingRouteEtaProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getCandidateLimit()).isEqualTo(3);
        assertThat(properties.getExecutorThreads()).isEqualTo(6);
    }

    @Test
    void acceptsCandidateLimitBoundaryValues() {
        MatchingRouteEtaProperties properties = new MatchingRouteEtaProperties();

        properties.setCandidateLimit(1);
        assertThat(properties.getCandidateLimit()).isEqualTo(1);
        properties.setCandidateLimit(10);
        assertThat(properties.getCandidateLimit()).isEqualTo(10);
    }

    @Test
    void rejectsCandidateLimitOutsideAllowedRange() {
        MatchingRouteEtaProperties properties = new MatchingRouteEtaProperties();

        assertThatThrownBy(() -> properties.setCandidateLimit(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setCandidateLimit(11))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setExecutorThreads(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setExecutorThreads(33))
                .isInstanceOf(IllegalArgumentException.class);
    }
}