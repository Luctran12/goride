package com.example.goride.common.ratelimit;

import com.example.goride.common.api.ErrorResponse;
import com.example.goride.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

public class RateLimitFilter extends OncePerRequestFilter {
    public static final String RATE_LIMIT_LIMIT_HEADER = "X-RateLimit-Limit";
    public static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    public static final String RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset";

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final RateLimitProperties properties;
    private final InMemoryRateLimitStore rateLimitStore;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(
            RateLimitProperties properties,
            InMemoryRateLimitStore rateLimitStore,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.rateLimitStore = rateLimitStore;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.isEnabled() || shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitDecision decision = rateLimitStore.consume("ip:" + resolveClientIp(request), properties);
        applyRateLimitHeaders(response, decision);
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(ErrorCode.RATE_LIMIT_EXCEEDED.httpStatus().value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ErrorResponse.of(
                        ErrorCode.RATE_LIMIT_EXCEEDED,
                        ErrorCode.RATE_LIMIT_EXCEEDED.defaultMessage(),
                        Map.of("retryAfterSeconds", decision.retryAfterSeconds())
                )
        );
    }

    private boolean shouldSkip(HttpServletRequest request) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        String path = request.getRequestURI();
        return properties.excludedPaths().stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (properties.shouldUseForwardedFor()) {
            String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",", 2)[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void applyRateLimitHeaders(HttpServletResponse response, RateLimitDecision decision) {
        response.setHeader(RATE_LIMIT_LIMIT_HEADER, Integer.toString(decision.limit()));
        response.setHeader(RATE_LIMIT_REMAINING_HEADER, Integer.toString(decision.remaining()));
        response.setHeader(RATE_LIMIT_RESET_HEADER, Long.toString(decision.resetEpochSeconds()));
    }
}
