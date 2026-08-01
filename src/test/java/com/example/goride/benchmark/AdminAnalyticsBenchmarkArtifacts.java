package com.example.goride.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

public class AdminAnalyticsBenchmarkArtifacts {
    private final Path runDirectory;
    private final ObjectMapper objectMapper;

    public AdminAnalyticsBenchmarkArtifacts(Path runDirectory, ObjectMapper objectMapper) {
        this.runDirectory = runDirectory.toAbsolutePath().normalize();
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public Path runDirectory() {
        return runDirectory;
    }

    public void initialize() throws IOException {
        if (Files.exists(runDirectory)) {
            try (Stream<Path> content = Files.list(runDirectory)) {
                if (content.findAny().isPresent()) {
                    throw new IllegalStateException(
                            "Benchmark output directory is not empty: " + runDirectory
                    );
                }
            }
        }
        Files.createDirectories(runDirectory);
    }

    public void writeJson(String relativePath, Object value) throws IOException {
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), value);
    }

    public void writeText(String relativePath, String value) throws IOException {
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(
                target,
                value,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW
        );
    }

    public void writeSamples(
            String queryId,
            String variant,
            List<Sample> samples
    ) throws IOException {
        StringBuilder csv = new StringBuilder(
                "phase,iteration,execution_order,duration_ns,success,error\n"
        );
        for (Sample sample : samples) {
            csv.append(sample.phase()).append(',')
                    .append(sample.iteration()).append(',')
                    .append(sample.executionOrder()).append(',')
                    .append(sample.durationNanos()).append(',')
                    .append(sample.success()).append(',')
                    .append(csvValue(sample.error()))
                    .append('\n');
        }
        writeText(
                "samples/" + queryId + "_" + variant + ".csv",
                csv.toString()
        );
    }

    public void writeChecksums() throws IOException {
        StringBuilder checksums = new StringBuilder();
        try (Stream<Path> paths = Files.walk(runDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("checksums.sha256"))
                    .sorted(Comparator.comparing(path -> runDirectory.relativize(path).toString()))
                    .forEach(path -> checksums
                            .append(sha256(path))
                            .append("  ")
                            .append(runDirectory.relativize(path).toString().replace('\\', '/'))
                            .append('\n'));
        }
        writeText("checksums.sha256", checksums.toString());
    }

    private Path resolve(String relativePath) {
        Path resolved = runDirectory.resolve(relativePath).normalize();
        if (!resolved.startsWith(runDirectory)) {
            throw new IllegalArgumentException("Artifact path escapes run directory");
        }
        return resolved;
    }

    private String csvValue(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String sha256(Path path) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            return HexFormat.of().formatHex(digest);
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not checksum " + path, exception);
        }
    }

    public record Sample(
            String phase,
            int iteration,
            int executionOrder,
            long durationNanos,
            boolean success,
            String error
    ) {
    }
}
