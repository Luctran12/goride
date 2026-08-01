package com.example.goride.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public record AdminAnalyticsBenchmarkProfile(
        String name,
        String generatorVersion,
        long users,
        long drivers,
        long trips,
        long matchingRuns,
        long matchingOffers,
        long driverSupplySnapshots,
        long locationPoints,
        long payments
) {
    private static final Path PROFILES_PATH = Path.of(
            "benchmarks",
            "admin-analytics",
            "profiles.json"
    );

    public AdminAnalyticsBenchmarkProfile {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Benchmark profile name is required");
        }
        if (generatorVersion == null || generatorVersion.isBlank()) {
            throw new IllegalArgumentException("Generator version is required");
        }
        if (users <= 0 || drivers <= 0 || drivers >= users) {
            throw new IllegalArgumentException("Profile users/drivers are invalid");
        }
        if (trips <= 0
                || matchingRuns != trips
                || matchingOffers < matchingRuns
                || driverSupplySnapshots <= 0
                || locationPoints <= 0
                || payments <= 0
                || payments > trips) {
            throw new IllegalArgumentException("Profile analytical row counts are invalid");
        }
    }

    public static AdminAnalyticsBenchmarkProfile load(
            ObjectMapper objectMapper,
            String requestedName
    ) throws IOException {
        String normalizedName = requestedName == null
                ? "smoke"
                : requestedName.trim().toLowerCase(Locale.ROOT);
        JsonNode root = objectMapper.readTree(PROFILES_PATH.toFile());
        JsonNode selected = root.path("profiles").path(normalizedName);
        if (selected.isMissingNode()) {
            throw new IllegalArgumentException(
                    "Unknown benchmark profile: " + normalizedName
            );
        }
        return new AdminAnalyticsBenchmarkProfile(
                normalizedName,
                root.path("generatorVersion").asText(),
                requiredLong(selected, "users"),
                requiredLong(selected, "drivers"),
                requiredLong(selected, "trips"),
                requiredLong(selected, "matchingRuns"),
                requiredLong(selected, "matchingOffers"),
                requiredLong(selected, "driverSupplySnapshots"),
                requiredLong(selected, "locationPoints"),
                requiredLong(selected, "payments")
        );
    }

    private static long requiredLong(JsonNode selected, String field) {
        long value = selected.path(field).asLong(-1);
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "Benchmark profile field must be positive: " + field
            );
        }
        return value;
    }
}
