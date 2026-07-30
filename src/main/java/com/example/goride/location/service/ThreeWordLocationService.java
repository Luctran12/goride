package com.example.goride.location.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.location.config.ThreeWordLocationProperties;
import com.example.goride.location.dto.LocationCoordinateResponse;
import com.example.goride.location.dto.ThreeWordCellBoundsResponse;
import com.example.goride.location.dto.ThreeWordLocationResponse;
import com.example.goride.location.provider.ThreeWordLocationClient;
import com.example.goride.location.provider.ThreeWordLocationClient.ProviderCellBounds;
import com.example.goride.location.provider.ThreeWordLocationClient.ProviderLocation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ThreeWordLocationService {
    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);
    private static final int MAX_ADDRESS_LENGTH = 200;

    private final ThreeWordLocationProperties properties;
    private final ThreeWordLocationClient client;

    public ThreeWordLocationService(
            ThreeWordLocationProperties properties,
            ThreeWordLocationClient client
    ) {
        this.properties = properties;
        this.client = client;
    }

    public ThreeWordLocationResponse toWords(BigDecimal latitude, BigDecimal longitude) {
        validateInputCoordinates(latitude, longitude);
        ensureProviderEnabled();
        ProviderLocation response = client.toWords(
                providerBaseUrl(),
                latitude,
                longitude
        );
        return mapToWordsResponse(response);
    }

    public ThreeWordLocationResponse toCoordinate(String address) {
        String normalizedAddress = normalizeAddress(address);
        ensureProviderEnabled();
        ProviderLocation response = client.toCoordinate(
                providerBaseUrl(),
                normalizedAddress
        );
        return mapToCoordinateResponse(normalizedAddress, response);
    }

    String normalizeAddress(String address) {
        if (address == null || address.isBlank() || address.length() > MAX_ADDRESS_LENGTH) {
            throw invalidAddress("Three-word address is required and must be at most 200 characters");
        }
        String normalized = address.strip();
        if (normalized.startsWith("///")) {
            normalized = normalized.substring(3).strip();
        }
        String[] parts = normalized.split("\\.", -1);
        if (parts.length != 3) {
            throw invalidAddress("Use exactly three words separated by dots, for example: hoa.la.cay");
        }
        List<String> words = Arrays.stream(parts)
                .map(String::strip)
                .map(part -> part.replaceAll("\\s+", "_"))
                .map(part -> part.toLowerCase(Locale.ROOT))
                .toList();
        if (words.stream().anyMatch(String::isBlank)) {
            throw invalidAddress("Use exactly three non-empty words separated by dots");
        }
        return String.join(".", words);
    }

    private ThreeWordLocationResponse mapToWordsResponse(ProviderLocation response) {
        validateProviderLocation(response);
        List<String> words = normalizeProviderWords(response.words());
        String normalizedWordAddress = String.join(".", words);
        if (response.address() == null || !normalizedWordAddress.equals(normalizeProviderAddress(response.address()))) {
            throw providerError("Three-word location provider returned an inconsistent address");
        }
        return response(response, displayWords(words));
    }

    private ThreeWordLocationResponse mapToCoordinateResponse(
            String requestedAddress,
            ProviderLocation response
    ) {
        validateProviderLocation(response);
        if (response.address() == null) {
            throw providerError("Three-word location provider returned no address");
        }
        String wordAddress = normalizeProviderAddress(response.address());
        if (!requestedAddress.equals(wordAddress)) {
            throw providerError("Three-word location provider returned an inconsistent address");
        }
        return response(response, displayWords(List.of(wordAddress.split("\\.", -1))));
    }

    private ThreeWordLocationResponse response(
            ProviderLocation providerLocation,
            List<String> words
    ) {
        ThreeWordCellBoundsResponse bounds = mapBounds(providerLocation.bounds());
        if (!inRange(
                providerLocation.lat(),
                bounds.southwest().lat(),
                bounds.northeast().lat()
        ) || !inRange(
                providerLocation.lon(),
                bounds.southwest().lng(),
                bounds.northeast().lng()
        )) {
            throw providerError("Three-word location provider returned coordinates outside the cell bounds");
        }
        return new ThreeWordLocationResponse(
                providerLocation.lat(),
                providerLocation.lon(),
                List.copyOf(words),
                String.join(".", words),
                bounds
        );
    }

    private List<String> displayWords(List<String> words) {
        return words.stream()
                .map(word -> word.replace('_', ' '))
                .toList();
    }

    private void validateInputCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Latitude and longitude are required",
                    Map.of("coordinates", "lat and lng are required")
            );
        }
        if (!inRange(latitude, MIN_LATITUDE, MAX_LATITUDE)
                || !inRange(longitude, MIN_LONGITUDE, MAX_LONGITUDE)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Latitude or longitude is outside the WGS84 range",
                    Map.of("coordinates", "lat must be -90..90 and lng must be -180..180")
            );
        }
    }

    private void validateProviderLocation(ProviderLocation response) {
        if (response == null || response.lat() == null || response.lon() == null) {
            throw providerError("Three-word location provider returned invalid coordinates");
        }
        if (!inRange(response.lat(), MIN_LATITUDE, MAX_LATITUDE)
                || !inRange(response.lon(), MIN_LONGITUDE, MAX_LONGITUDE)) {
            throw providerError("Three-word location provider returned coordinates outside the WGS84 range");
        }
    }

    private List<String> normalizeProviderWords(List<String> words) {
        if (words == null || words.size() != 3) {
            throw providerError("Three-word location provider returned an invalid word list");
        }
        List<String> normalized = words.stream()
                .map(word -> word == null ? "" : word.strip().replaceAll("\\s+", "_").toLowerCase(Locale.ROOT))
                .toList();
        if (normalized.stream().anyMatch(word -> word.isBlank() || word.contains("."))) {
            throw providerError("Three-word location provider returned invalid words");
        }
        return normalized;
    }

    private String normalizeProviderAddress(String address) {
        try {
            return normalizeAddress(address);
        } catch (BusinessException exception) {
            throw providerError("Three-word location provider returned an invalid address");
        }
    }

    private ThreeWordCellBoundsResponse mapBounds(ProviderCellBounds bounds) {
        if (bounds == null || !validCoordinatePair(bounds.sw()) || !validCoordinatePair(bounds.ne())) {
            throw providerError("Three-word location provider returned invalid cell bounds");
        }
        LocationCoordinateResponse southwest = coordinate(bounds.sw());
        LocationCoordinateResponse northeast = coordinate(bounds.ne());
        if (southwest.lat().compareTo(northeast.lat()) > 0
                || southwest.lng().compareTo(northeast.lng()) > 0) {
            throw providerError("Three-word location provider returned inverted cell bounds");
        }
        return new ThreeWordCellBoundsResponse(southwest, northeast);
    }

    private LocationCoordinateResponse coordinate(List<BigDecimal> pair) {
        BigDecimal latitude = pair.get(0);
        BigDecimal longitude = pair.get(1);
        if (!inRange(latitude, MIN_LATITUDE, MAX_LATITUDE)
                || !inRange(longitude, MIN_LONGITUDE, MAX_LONGITUDE)) {
            throw providerError("Three-word location provider returned invalid cell coordinates");
        }
        return new LocationCoordinateResponse(latitude, longitude);
    }

    private boolean validCoordinatePair(List<BigDecimal> pair) {
        return pair != null && pair.size() == 2 && pair.get(0) != null && pair.get(1) != null;
    }

    private boolean inRange(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        return value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }

    private String providerBaseUrl() {
        try {
            return properties.normalizedBaseUrl();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.WORD_LOCATION_PROVIDER_UNAVAILABLE,
                    "Three-word location provider is not configured correctly"
            );
        }
    }

    private void ensureProviderEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(
                    ErrorCode.WORD_LOCATION_PROVIDER_UNAVAILABLE,
                    "Three-word location provider is disabled"
            );
        }
    }

    private BusinessException invalidAddress(String message) {
        return new BusinessException(
                ErrorCode.WORD_LOCATION_INVALID_ADDRESS,
                message,
                Map.of("address", "expected format: word.word.word")
        );
    }

    private BusinessException providerError(String message) {
        return new BusinessException(ErrorCode.WORD_LOCATION_PROVIDER_ERROR, message);
    }
}
