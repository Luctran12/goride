package com.example.goride.location.provider;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.location.config.ThreeWordLocationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RestClientThreeWordLocationClient implements ThreeWordLocationClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public RestClientThreeWordLocationClient(
            ThreeWordLocationProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this(restClientBuilder, objectMapper, properties.timeout());
    }

    RestClientThreeWordLocationClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            Duration timeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public ProviderLocation toWords(
            String baseUrl,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/api/to-words")
                .queryParam("lat", coordinate(latitude))
                .queryParam("lon", coordinate(longitude))
                .build()
                .encode()
                .toUri();
        return get(uri, Operation.TO_WORDS);
    }

    @Override
    public ProviderLocation toCoordinate(String baseUrl, String address) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/api/to-coordinate")
                .queryParam("address", address)
                .build()
                .encode()
                .toUri();
        return get(uri, Operation.TO_COORDINATE);
    }

    private ProviderLocation get(URI uri, Operation operation) {
        try {
            ProviderLocation response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(ProviderLocation.class);
            if (response == null) {
                throw providerError(operation.failureMessage(), Map.of("providerReason", "empty_response"));
            }
            return response;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw mapProviderError(exception, operation);
        } catch (RestClientException | IllegalArgumentException exception) {
            throw providerError(operation.failureMessage(), Map.of());
        }
    }

    private BusinessException mapProviderError(
            RestClientResponseException exception,
            Operation operation
    ) {
        ProviderErrorResponse response = parseError(exception.getResponseBodyAsString());
        Map<String, Object> details = providerDetails(exception, response);
        int status = exception.getStatusCode().value();
        if (operation == Operation.TO_WORDS && status == 400) {
            return new BusinessException(
                    ErrorCode.WORD_LOCATION_OUT_OF_BOUNDS,
                    "Coordinates are outside the supported three-word map",
                    details
            );
        }
        if (operation == Operation.TO_COORDINATE && status == 400) {
            return new BusinessException(
                    ErrorCode.WORD_LOCATION_INVALID_ADDRESS,
                    "Three-word address format is invalid",
                    details
            );
        }
        if (operation == Operation.TO_COORDINATE && status == 404) {
            return new BusinessException(
                    ErrorCode.WORD_LOCATION_NOT_FOUND,
                    "Three-word address was not found",
                    details
            );
        }
        return providerError(operation.failureMessage(), details);
    }

    private Map<String, Object> providerDetails(
            RestClientResponseException exception,
            ProviderErrorResponse response
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("providerStatus", exception.getStatusCode().value());
        if (response != null && response.error() != null && !response.error().isBlank()) {
            details.put("providerMessage", response.error());
        }
        if (response != null && response.bounds() != null && !response.bounds().isEmpty()) {
            details.put("bounds", response.bounds());
        }
        return details;
    }

    private ProviderErrorResponse parseError(String body) {
        try {
            return objectMapper.readValue(body, ProviderErrorResponse.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private String coordinate(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private BusinessException providerError(String message, Map<String, Object> details) {
        return new BusinessException(ErrorCode.WORD_LOCATION_PROVIDER_ERROR, message, details);
    }

    private enum Operation {
        TO_WORDS("Unable to convert coordinates to a three-word address"),
        TO_COORDINATE("Unable to resolve the three-word address");

        private final String failureMessage;

        Operation(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        String failureMessage() {
            return failureMessage;
        }
    }

    private record ProviderErrorResponse(String error, Map<String, Object> bounds) {
    }
}
