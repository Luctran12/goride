package com.example.goride.benchmark;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAnalyticsThesisArtifactTests {
    private static final Path DOCS = Path.of("docs", "admin-analytics");
    private static final List<String> FINAL_ARTIFACTS = List.of(
            "README.md",
            "architecture-and-data-flow.md",
            "operations-hardening-review.md",
            "thesis-traceability-matrix.md",
            "evaluation-limitations-and-future-work.md"
    );
    private static final Pattern MARKDOWN_LINK =
            Pattern.compile("\\[[^\\]]+]\\(([^)]+)\\)");

    @Test
    void finalArtifactsExistAndAllRelativeLinksResolve() throws IOException {
        for (String artifact : FINAL_ARTIFACTS) {
            Path source = DOCS.resolve(artifact);
            assertThat(source).exists().isRegularFile();
            Matcher matcher = MARKDOWN_LINK.matcher(Files.readString(source));
            while (matcher.find()) {
                String target = matcher.group(1);
                if (target.startsWith("http://")
                        || target.startsWith("https://")
                        || target.startsWith("mailto:")
                        || target.startsWith("#")) {
                    continue;
                }
                String pathPart = target.split("#", 2)[0];
                if (!pathPart.isBlank()) {
                    assertThat(source.getParent().resolve(pathPart).normalize())
                            .as("%s link %s", source, target)
                            .exists();
                }
            }
        }
    }

    @Test
    void traceabilityCoversEveryFrozenQueryAndRawEvidenceLayer() throws IOException {
        String traceability = Files.readString(
                DOCS.resolve("thesis-traceability-matrix.md")
        );

        for (int query = 1; query <= 10; query++) {
            assertThat(traceability).contains("Q%02d".formatted(query));
        }
        assertThat(traceability)
                .contains("environment.json")
                .contains("dataset-manifest.json")
                .contains("correctness.json")
                .contains("samples/")
                .contains("summary.json")
                .contains("sql/")
                .contains("explain/")
                .contains("storage.json")
                .contains("checksums.sha256")
                .contains("aaea493488926f36a99186d62185a8549722d189")
                .contains("5537")
                .contains("68d4bf0c35479d67fabdc1474663b7a3f6ac33bdf57dfe24b59b9dd1a8821b2f");
    }

    @Test
    void finalEvaluationKeepsUnsupportedClaimsOutsideCoreScope() throws IOException {
        String evaluation = Files.readString(
                DOCS.resolve("evaluation-limitations-and-future-work.md")
        );
        String readme = Files.readString(DOCS.resolve("README.md"));

        assertThat(evaluation)
                .contains("`DIRECT` remains the production default")
                .contains("thesis-scale experiment pending")
                .contains("Forecasting and anomaly detection are explicitly outside")
                .contains("synthetic")
                .contains("warm-cache and single-client");
        assertThat(readme)
                .contains("not a thesis-scale performance result");
    }

    @Test
    void architectureContainsSystemAndQueryFlowDiagrams() throws IOException {
        String architecture = Files.readString(
                DOCS.resolve("architecture-and-data-flow.md")
        );

        assertThat(architecture.split("```mermaid", -1).length - 1)
                .isGreaterThanOrEqualTo(3);
        assertThat(architecture)
                .contains("Matching telemetry port")
                .contains("Exact read-model eligibility")
                .contains("sourceVariant and dataFreshnessAt");
    }
}
