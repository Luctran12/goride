package com.example.goride.benchmark;

import com.example.goride.benchmark.AdminAnalyticsBenchmarkArtifacts.Sample;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAnalyticsBenchmarkArtifactTests {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void writesRawSamplesAndChecksumsWithoutOverwritingAnExistingRun() throws Exception {
        Path runDirectory = temporaryDirectory.resolve("run");
        var artifacts = new AdminAnalyticsBenchmarkArtifacts(
                runDirectory,
                new ObjectMapper()
        );

        artifacts.initialize();
        artifacts.writeSamples(
                "Q01_7D",
                "DIRECT",
                List.of(new Sample(
                        "MEASURED",
                        1,
                        2,
                        1_500_000,
                        false,
                        "query failed, \"retained\""
                ))
        );
        artifacts.writeChecksums();

        assertThat(Files.readString(runDirectory.resolve("samples/Q01_7D_DIRECT.csv")))
                .contains("MEASURED,1,2,1500000,false")
                .contains("\"query failed, \"\"retained\"\"\"");
        assertThat(Files.readString(runDirectory.resolve("checksums.sha256")))
                .contains("samples/Q01_7D_DIRECT.csv");

        assertThatThrownBy(artifacts::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not empty");
    }

    @Test
    void rejectsArtifactPathsOutsideTheRunDirectory() throws Exception {
        var artifacts = new AdminAnalyticsBenchmarkArtifacts(
                temporaryDirectory.resolve("run"),
                new ObjectMapper()
        );
        artifacts.initialize();

        assertThatThrownBy(() -> artifacts.writeText("../escaped.txt", "unsafe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes run directory");
    }

    @Test
    void generatorSqlUsesOnlyVersionedTokensForDataVariation() throws Exception {
        String sql = Files.readString(Path.of(
                "benchmarks",
                "admin-analytics",
                "generate-dataset.sql"
        ));

        assertThat(sql)
                .contains("${SEED}", "${USERS}", "${TRIPS}", "${LOCATION_POINTS}")
                .doesNotContain(
                        "random(",
                        "now(",
                        "clock_timestamp(",
                        "gen_random",
                        "CURRENT_TIMESTAMP"
                );
    }
}
