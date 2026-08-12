package com.example.goride.chat.service;

import com.example.goride.chat.config.TripMessageRateLimitProperties;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.common.ratelimit.RateLimitDecision;
import com.example.goride.common.ratelimit.RateLimitProperties;
import com.example.goride.common.ratelimit.RateLimitStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TripMessageRateLimiterTests {
    @Test
    void rejectsUserWhenChatBucketIsExhausted() {
        RateLimitStore store = mock(RateLimitStore.class);
        TripMessageRateLimiter limiter = new TripMessageRateLimiter(store, globalProperties(), chatProperties());
        when(store.consume(eq("trip-message:user:10"), any(RateLimitProperties.class)))
                .thenReturn(new RateLimitDecision(false, 30, 0, 12, 100));

        assertThatThrownBy(() -> limiter.checkAllowed(10L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
                    assertThat(exception.details()).containsEntry("retryAfterSeconds", 12L);
                });
    }

    private RateLimitProperties globalProperties() {
        return new RateLimitProperties(
                true,
                RateLimitProperties.Store.MEMORY,
                120,
                120,
                60,
                10_000,
                "goride:rate-limit:",
                List.of(),
                false
        );
    }

    private TripMessageRateLimitProperties chatProperties() {
        return new TripMessageRateLimitProperties(true, 30, 30, 60, "goride:chat-rate-limit:");
    }
}
