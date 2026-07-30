package com.example.goride.integration;

import com.example.goride.analytics.model.AnalyticsBucket;
import com.example.goride.analytics.model.AnalyticsFilter;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort;
import com.example.goride.analytics.repository.MaterializedAnalyticsQueryPort;
import com.example.goride.analytics.service.MaterializedAnalyticsRefreshService;
import com.example.goride.benchmark.AdminAnalyticsBenchmarkArtifacts;
import com.example.goride.benchmark.AdminAnalyticsBenchmarkArtifacts.Sample;
import com.example.goride.benchmark.AdminAnalyticsBenchmarkDatasetGenerator;
import com.example.goride.benchmark.AdminAnalyticsBenchmarkPlanSql;
import com.example.goride.benchmark.AdminAnalyticsBenchmarkProfile;
import com.example.goride.benchmark.AdminAnalyticsBenchmarkStatistics;
import com.example.goride.driver.domain.VehicleType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminAnalyticsBenchmarkIT extends PostgresRedisIntegrationTest {
    private static final Instant QUERY_CUTOFF = Instant.parse("2026-05-02T17:00:00Z");
    private static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int PROJECTED_SRID = 32648;
    private static final int RESULT_LIMIT = 5001;
    private static final int NUMERIC_CORRECTNESS_SCALE = 9;
    private static volatile int resultSink;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private List<DirectAnalyticsQueryPort> queryPorts;

    @Autowired
    private MaterializedAnalyticsQueryPort materializedQueryPort;

    @Autowired
    private MaterializedAnalyticsRefreshService refreshService;

    @DynamicPropertySource
    static void benchmarkProperties(DynamicPropertyRegistry registry) {
        registry.add("app.analytics.materialized.enabled", () -> "true");
        registry.add("app.analytics.materialized.query-variant", () -> "MATERIALIZED");
        registry.add("app.analytics.materialized.refresh-enabled", () -> "false");
    }

    @Test
    void generatesCorrectDatasetAndWritesTraceableBenchmarkArtifacts() throws Exception {
        BenchmarkOptions options = BenchmarkOptions.fromSystemProperties();
        AdminAnalyticsBenchmarkProfile profile = AdminAnalyticsBenchmarkProfile.load(
                objectMapper,
                options.profile()
        );
        Path runDirectory = options.outputDirectory() == null
                ? defaultRunDirectory(profile, options.seed())
                : options.outputDirectory();
        AdminAnalyticsBenchmarkArtifacts artifacts =
                new AdminAnalyticsBenchmarkArtifacts(runDirectory, objectMapper);
        artifacts.initialize();

        applyRelease("20260729-admin-analytics-spatial-indexes");
        applyRelease("20260729-admin-analytics-materialized");

        var generator = new AdminAnalyticsBenchmarkDatasetGenerator(
                jdbcTemplate,
                objectMapper
        );
        var datasetManifest = generator.generate(profile, options.seed());
        artifacts.writeJson("dataset-manifest.json", datasetManifest);

        List<RefreshEvidence> refreshEvidence = new ArrayList<>();
        refreshEvidence.add(refreshEvidence(0, Map.of()));
        refreshEvidence.add(refreshEvidence(1, materializedViewRows()));
        artifacts.writeText(
                "refresh/refresh-samples.csv",
                refreshCsv(refreshEvidence)
        );

        DirectAnalyticsQueryPort direct = directQueryPort();
        List<QueryCase> queryCases = queryCases();
        List<CorrectnessCase> correctnessCases = verifyCorrectness(
                queryCases,
                direct
        );
        artifacts.writeJson(
                "correctness.json",
                Map.of(
                        "passed", true,
                        "numericComparisonScale", NUMERIC_CORRECTNESS_SCALE,
                        "queryCutoff", QUERY_CUTOFF,
                        "materializedCutoff", refreshEvidence.get(1).cutoff(),
                        "cases", correctnessCases
                )
        );

        Map<String, Object> environment = environment(
                profile,
                options,
                refreshEvidence.get(1).cutoff()
        );
        artifacts.writeJson("environment.json", environment);
        artifacts.writeJson("storage.json", storageEvidence());

        List<QuerySummary> summaries = new ArrayList<>();
        List<QueryCaseManifest> caseManifests = new ArrayList<>();
        long measuredErrors = 0;
        for (QueryCase queryCase : queryCases) {
            CaseMeasurement measurement = measure(
                    queryCase,
                    direct,
                    options
            );
            for (String variant : List.of("DIRECT", "MATERIALIZED")) {
                List<Sample> samples = measurement.samples().get(variant);
                artifacts.writeSamples(queryCase.id(), variant, samples);
                List<Long> successfulMeasured = samples.stream()
                        .filter(sample -> "MEASURED".equals(sample.phase()))
                        .filter(Sample::success)
                        .map(Sample::durationNanos)
                        .toList();
                long errors = samples.stream()
                        .filter(sample -> "MEASURED".equals(sample.phase()))
                        .filter(sample -> !sample.success())
                        .count();
                measuredErrors += errors;
                summaries.add(new QuerySummary(
                        queryCase.id(),
                        variant,
                        AdminAnalyticsBenchmarkStatistics.summarize(
                                successfulMeasured,
                                errors
                        )
                ));

                String planSql = AdminAnalyticsBenchmarkPlanSql.forCase(
                        queryCase.id(),
                        variant,
                        queryCase.filter().from(),
                        queryCase.filter().to(),
                        queryCase.bucket() == null
                                ? null
                                : queryCase.bucket().sqlUnit(),
                        queryCase.cellSizeMeters()
                );
                artifacts.writeText(
                        "sql/" + queryCase.id() + "_" + variant + ".sql",
                        planSql + System.lineSeparator()
                );
                artifacts.writeJson(
                        "explain/" + queryCase.id() + "_" + variant + ".json",
                        explain(planSql)
                );
            }
            caseManifests.add(QueryCaseManifest.from(queryCase));
        }

        artifacts.writeJson("query-cases.json", caseManifests);
        artifacts.writeJson(
                "summary.json",
                Map.of(
                        "profile", profile.name(),
                        "seed", options.seed(),
                        "warmupIterations", options.warmupIterations(),
                        "measuredIterations", options.measuredIterations(),
                        "queryCutoff", QUERY_CUTOFF,
                        "correctnessPassed", true,
                        "results", summaries
                )
        );
        artifacts.writeText(
                "README.md",
                runReadme(profile, options, refreshEvidence.get(1).cutoff())
        );
        artifacts.writeChecksums();

        assertThat(measuredErrors)
                .as("Measured query errors are retained in raw CSV and fail the run")
                .isZero();
        assertThat(resultSink).isNotEqualTo(Integer.MIN_VALUE);
        System.out.println("Admin Analytics benchmark artifacts: " + artifacts.runDirectory());
    }

    private List<CorrectnessCase> verifyCorrectness(
            List<QueryCase> queryCases,
            DirectAnalyticsQueryPort direct
    ) {
        List<CorrectnessCase> results = new ArrayList<>();
        for (QueryCase queryCase : queryCases) {
            Object directResult = queryCase.execute(direct);
            Object materializedResult = queryCase.execute(materializedQueryPort);
            assertThat(materializedResult)
                    .as("Correctness gate for " + queryCase.id())
                    .usingRecursiveComparison()
                    .withComparatorForType(
                            (left, right) -> normalizeDecimal(left)
                                    .compareTo(normalizeDecimal(right)),
                            BigDecimal.class
                    )
                    .isEqualTo(directResult);
            results.add(new CorrectnessCase(
                    queryCase.id(),
                    canonicalHash(directResult),
                    canonicalHash(materializedResult),
                    true
            ));
        }
        return List.copyOf(results);
    }

    private CaseMeasurement measure(
            QueryCase queryCase,
            DirectAnalyticsQueryPort direct,
            BenchmarkOptions options
    ) {
        Map<String, List<Sample>> samples = new LinkedHashMap<>();
        samples.put("DIRECT", new ArrayList<>());
        samples.put("MATERIALIZED", new ArrayList<>());
        measurePhase(
                "WARMUP",
                options.warmupIterations(),
                queryCase,
                direct,
                options.seed(),
                samples
        );
        measurePhase(
                "MEASURED",
                options.measuredIterations(),
                queryCase,
                direct,
                options.seed() + 10_000,
                samples
        );
        return new CaseMeasurement(Map.copyOf(samples));
    }

    private void measurePhase(
            String phase,
            int iterations,
            QueryCase queryCase,
            DirectAnalyticsQueryPort direct,
            long orderSeed,
            Map<String, List<Sample>> samples
    ) {
        for (int iteration = 1; iteration <= iterations; iteration++) {
            boolean directFirst = Math.floorMod(
                    orderSeed + queryCase.id().hashCode() + iteration,
                    2
            ) == 0;
            List<String> order = directFirst
                    ? List.of("DIRECT", "MATERIALIZED")
                    : List.of("MATERIALIZED", "DIRECT");
            for (int executionOrder = 0; executionOrder < order.size(); executionOrder++) {
                String variant = order.get(executionOrder);
                DirectAnalyticsQueryPort port = "DIRECT".equals(variant)
                        ? direct
                        : materializedQueryPort;
                samples.get(variant).add(timedSample(
                        phase,
                        iteration,
                        executionOrder + 1,
                        () -> queryCase.execute(port)
                ));
            }
        }
    }

    private Sample timedSample(
            String phase,
            int iteration,
            int executionOrder,
            Supplier<Object> invocation
    ) {
        long startedNanos = System.nanoTime();
        try {
            Object result = invocation.get();
            resultSink ^= result == null ? 0 : result.hashCode();
            return new Sample(
                    phase,
                    iteration,
                    executionOrder,
                    System.nanoTime() - startedNanos,
                    true,
                    null
            );
        } catch (RuntimeException exception) {
            return new Sample(
                    phase,
                    iteration,
                    executionOrder,
                    System.nanoTime() - startedNanos,
                    false,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private RefreshEvidence refreshEvidence(
            int sequence,
            Map<String, Long> rowsBefore
    ) {
        Instant startedAt = Instant.now();
        var result = refreshService.refresh();
        Instant completedAt = Instant.now();
        assertThat(result.refreshed()).isTrue();
        return new RefreshEvidence(
                sequence,
                startedAt,
                completedAt,
                result.durationMs(),
                result.concurrent(),
                result.cutoff(),
                rowsBefore,
                materializedViewRows()
        );
    }

    private String refreshCsv(List<RefreshEvidence> refreshes) {
        StringBuilder csv = new StringBuilder(
                "sequence,started_at,completed_at,duration_ms,concurrent,cutoff,"
                        + "rows_before,rows_after\n"
        );
        for (RefreshEvidence refresh : refreshes) {
            csv.append(refresh.sequence()).append(',')
                    .append(refresh.startedAt()).append(',')
                    .append(refresh.completedAt()).append(',')
                    .append(refresh.durationMs()).append(',')
                    .append(refresh.concurrent()).append(',')
                    .append(refresh.cutoff()).append(',')
                    .append('"').append(refresh.rowsBefore()).append('"').append(',')
                    .append('"').append(refresh.rowsAfter()).append('"')
                    .append('\n');
        }
        return csv.toString();
    }

    private Map<String, Long> materializedViewRows() {
        Map<String, Long> rows = new LinkedHashMap<>();
        for (String view : List.of(
                "analytics.mv_trip_daily",
                "analytics.mv_demand_hourly_cell",
                "analytics.mv_supply_hourly",
                "analytics.mv_matching_daily"
        )) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + view,
                    Long.class
            );
            rows.put(view, count == null ? 0 : count);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(rows));
    }

    private JsonNode explain(String querySql) throws IOException {
        String json = jdbcTemplate.queryForObject(
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + querySql,
                (resultSet, rowNumber) -> resultSet.getString(1)
        );
        if (json == null) {
            throw new IllegalStateException("PostgreSQL returned no query plan");
        }
        return objectMapper.readTree(json);
    }

    private List<Map<String, Object>> storageEvidence() {
        List<String> sourceTables = List.of(
                "users",
                "trips",
                "trip_status_history",
                "payments",
                "matching_runs",
                "matching_offer_events",
                "driver_supply_snapshots",
                "trip_location_history"
        );
        List<String> materializedViews = List.of(
                "analytics.mv_trip_daily",
                "analytics.mv_demand_hourly_cell",
                "analytics.mv_supply_hourly",
                "analytics.mv_matching_daily"
        );
        List<Map<String, Object>> evidence = new ArrayList<>();
        sourceTables.forEach(name -> evidence.add(relationSize(name, "SOURCE_TABLE")));
        materializedViews.forEach(name -> evidence.add(relationSize(name, "MATERIALIZED_VIEW")));
        return List.copyOf(evidence);
    }

    private Map<String, Object> relationSize(String relation, String kind) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT
                            pg_table_size(to_regclass(?)) AS table_bytes,
                            pg_indexes_size(to_regclass(?)) AS index_bytes,
                            pg_total_relation_size(to_regclass(?)) AS total_bytes
                        """,
                (resultSet, rowNumber) -> {
                    Map<String, Object> size = new LinkedHashMap<>();
                    size.put("relation", relation);
                    size.put("kind", kind);
                    size.put("tableBytes", resultSet.getLong("table_bytes"));
                    size.put("indexBytes", resultSet.getLong("index_bytes"));
                    size.put("totalBytes", resultSet.getLong("total_bytes"));
                    return Map.copyOf(size);
                },
                relation,
                relation,
                relation
        );
    }

    private Map<String, Object> environment(
            AdminAnalyticsBenchmarkProfile profile,
            BenchmarkOptions options,
            Instant materializedCutoff
    ) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("recordedAt", Instant.now());
        manifest.put("gitCommit", git("rev-parse", "HEAD"));
        manifest.put("gitBranch", git("branch", "--show-current"));
        String workingTreeStatus = git("status", "--short");
        manifest.put("gitWorkingTreeClean", workingTreeStatus.isBlank());
        manifest.put("gitWorkingTreeStatus", workingTreeStatus);
        manifest.put("operatingSystem", System.getProperty("os.name"));
        manifest.put("osVersion", System.getProperty("os.version"));
        manifest.put("osArchitecture", System.getProperty("os.arch"));
        manifest.put("logicalProcessors", Runtime.getRuntime().availableProcessors());
        manifest.put(
                "processorIdentifier",
                System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "unavailable")
        );
        manifest.put(
                "totalPhysicalMemoryBytes",
                totalPhysicalMemoryBytes()
        );
        manifest.put("jvmMaxMemoryBytes", Runtime.getRuntime().maxMemory());
        manifest.put("javaVersion", System.getProperty("java.version"));
        manifest.put("javaVendor", System.getProperty("java.vendor"));
        manifest.put("jvmUptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        manifest.put("postgresqlVersion", queryString("SELECT version()"));
        manifest.put("postgisVersion", queryString("SELECT postgis_full_version()"));
        manifest.put("database", queryString("SELECT current_database()"));
        manifest.put("databaseSettings", Map.of(
                "sharedBuffers", queryString("SELECT current_setting('shared_buffers')"),
                "workMem", queryString("SELECT current_setting('work_mem')"),
                "effectiveCacheSize",
                queryString("SELECT current_setting('effective_cache_size')")
        ));
        manifest.put("containerRuntime", "Docker via Testcontainers");
        manifest.put("dockerServerVersion", dockerServerVersion());
        manifest.put("testcontainersVersion", libraryVersion(DockerClientFactory.class));
        manifest.put("postgresImage", "postgis/postgis:15-3.3");
        manifest.put("storageDescription", options.storageDescription());
        manifest.put("profile", profile.name());
        manifest.put("seed", options.seed());
        manifest.put("warmupIterations", options.warmupIterations());
        manifest.put("measuredIterations", options.measuredIterations());
        manifest.put("queryCutoff", QUERY_CUTOFF);
        manifest.put("materializedCutoff", materializedCutoff);
        manifest.put(
                "freshnessLagMsAtManifest",
                Duration.between(materializedCutoff, Instant.now()).toMillis()
        );
        return Map.copyOf(manifest);
    }

    private long totalPhysicalMemoryBytes() {
        if (ManagementFactory.getOperatingSystemMXBean()
                instanceof com.sun.management.OperatingSystemMXBean operatingSystem) {
            return operatingSystem.getTotalMemorySize();
        }
        return -1;
    }

    private String dockerServerVersion() {
        String version = DockerClientFactory.instance()
                .client()
                .versionCmd()
                .exec()
                .getVersion();
        return version == null || version.isBlank() ? "unavailable" : version;
    }

    private String libraryVersion(Class<?> libraryClass) {
        String version = libraryClass.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "unavailable" : version;
    }

    private String queryString(String sql) {
        return jdbcTemplate.queryForObject(sql, String.class);
    }

    private String git(String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command)
                    .directory(Path.of("").toAbsolutePath().toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();
            if (process.waitFor() != 0) {
                return "unavailable: " + output;
            }
            return output;
        } catch (IOException exception) {
            return "unavailable: " + exception.getMessage();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "unavailable: interrupted";
        }
    }

    private String canonicalHash(Object value) {
        JsonNode node = objectMapper.valueToTree(value);
        StringBuilder canonical = new StringBuilder();
        appendCanonical(node, canonical);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    canonical.toString().getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void appendCanonical(JsonNode node, StringBuilder target) {
        if (node.isObject()) {
            target.append('{');
            Map<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((name, value) -> {
                target.append(name).append(':');
                appendCanonical(value, target);
                target.append(';');
            });
            target.append('}');
            return;
        }
        if (node.isArray()) {
            target.append('[');
            node.forEach(value -> appendCanonical(value, target));
            target.append(']');
            return;
        }
        if (node.isNumber()) {
            target.append(
                    normalizeDecimal(node.decimalValue())
                            .stripTrailingZeros()
                            .toPlainString()
            );
            return;
        }
        target.append(node.toString());
    }

    private BigDecimal normalizeDecimal(BigDecimal value) {
        return value.setScale(NUMERIC_CORRECTNESS_SCALE, RoundingMode.HALF_UP);
    }

    private void applyRelease(String releaseName) throws IOException {
        Path release = Path.of("db", "releases", releaseName);
        jdbcTemplate.execute(Files.readString(release.resolve("precheck.sql")));
        jdbcTemplate.execute(Files.readString(release.resolve("apply.sql")));
        jdbcTemplate.execute(Files.readString(release.resolve("verify.sql")));
    }

    private DirectAnalyticsQueryPort directQueryPort() {
        return queryPorts.stream()
                .filter(port -> !(port instanceof MaterializedAnalyticsQueryPort))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Direct analytics query port is unavailable"
                ));
    }

    private List<QueryCase> queryCases() {
        return List.of(
                QueryCase.overview("Q01_OVERVIEW_7D", days(7)),
                QueryCase.overview("Q02_OVERVIEW_30D", days(30)),
                QueryCase.demand(
                        "Q03_DEMAND_HOURLY_7D",
                        days(7),
                        AnalyticsBucket.HOUR
                ),
                QueryCase.demand(
                        "Q04_DEMAND_DAILY_90D",
                        days(90),
                        AnalyticsBucket.DAY
                ),
                QueryCase.supply(
                        "Q05_SUPPLY_HOURLY_7D",
                        days(7),
                        AnalyticsBucket.HOUR
                ),
                QueryCase.matchingPerformance(
                        "Q06_MATCHING_PERFORMANCE_30D",
                        days(30)
                ),
                QueryCase.matchingFunnel(
                        "Q07_MATCHING_FUNNEL_30D",
                        days(30)
                ),
                QueryCase.heatmap("Q08_HEATMAP_7D_250M", days(7), 250),
                QueryCase.heatmap("Q09_HEATMAP_7D_1000M", days(7), 1000),
                QueryCase.heatmap("Q10_HEATMAP_30D_2000M", days(30), 2000)
        );
    }

    private AnalyticsFilter days(long days) {
        return new AnalyticsFilter(
                QUERY_CUTOFF.minus(Duration.ofDays(days)),
                QUERY_CUTOFF,
                REPORTING_ZONE,
                VehicleType.MOTORBIKE,
                null
        );
    }

    private Path defaultRunDirectory(
            AdminAnalyticsBenchmarkProfile profile,
            long seed
    ) {
        String timestamp = DateTimeFormatter
                .ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
                .withZone(ZoneId.of("UTC"))
                .format(Instant.now());
        String commit = git("rev-parse", "--short", "HEAD");
        return Path.of(
                "benchmark-results",
                timestamp + "-" + commit + "-" + profile.name() + "-" + seed
        );
    }

    private String runReadme(
            AdminAnalyticsBenchmarkProfile profile,
            BenchmarkOptions options,
            Instant materializedCutoff
    ) {
        return """
                # Admin Analytics Benchmark Run

                This directory is raw evidence produced by the versioned Phase 7 runner.

                - Profile: `%s`
                - Seed: `%d`
                - Warm-up iterations: `%d`
                - Measured iterations: `%d`
                - Query cutoff: `%s`
                - Materialized refresh cutoff: `%s`
                - Correctness gate: passed before timing

                Latency samples measure the backend JDBC query-port operations. SQL and
                EXPLAIN files are representative, versioned plan evidence for the same
                workload, tables, filters and ranges; some overview plans intentionally
                use a metric subset. They are not substituted for raw latency samples.

                `summary.json` is derived only from `MEASURED` rows. Warm-up rows remain
                in the CSV files but are excluded. P50/P95 use continuous interpolation;
                standard deviation is the population value.

                This smoke/development artifact is evidence for this environment only.
                It is not, by itself, a thesis performance claim.
                """.formatted(
                profile.name(),
                options.seed(),
                options.warmupIterations(),
                options.measuredIterations(),
                QUERY_CUTOFF,
                materializedCutoff
        );
    }

    private enum Operation {
        OVERVIEW,
        DEMAND,
        SUPPLY,
        MATCHING_PERFORMANCE,
        MATCHING_FUNNEL,
        HEATMAP
    }

    private record QueryCase(
            String id,
            Operation operation,
            AnalyticsFilter filter,
            AnalyticsBucket bucket,
            Integer cellSizeMeters
    ) {
        static QueryCase overview(String id, AnalyticsFilter filter) {
            return new QueryCase(id, Operation.OVERVIEW, filter, null, null);
        }

        static QueryCase demand(
                String id,
                AnalyticsFilter filter,
                AnalyticsBucket bucket
        ) {
            return new QueryCase(id, Operation.DEMAND, filter, bucket, null);
        }

        static QueryCase supply(
                String id,
                AnalyticsFilter filter,
                AnalyticsBucket bucket
        ) {
            return new QueryCase(id, Operation.SUPPLY, filter, bucket, null);
        }

        static QueryCase matchingPerformance(String id, AnalyticsFilter filter) {
            return new QueryCase(
                    id,
                    Operation.MATCHING_PERFORMANCE,
                    filter,
                    null,
                    null
            );
        }

        static QueryCase matchingFunnel(String id, AnalyticsFilter filter) {
            return new QueryCase(
                    id,
                    Operation.MATCHING_FUNNEL,
                    filter,
                    null,
                    null
            );
        }

        static QueryCase heatmap(
                String id,
                AnalyticsFilter filter,
                int cellSizeMeters
        ) {
            return new QueryCase(
                    id,
                    Operation.HEATMAP,
                    filter,
                    null,
                    cellSizeMeters
            );
        }

        Object execute(DirectAnalyticsQueryPort port) {
            return switch (operation) {
                case OVERVIEW -> port.overview(filter);
                case DEMAND -> port.demandTimeseries(filter, bucket);
                case SUPPLY -> port.supplyTimeseries(filter, bucket);
                case MATCHING_PERFORMANCE -> port.matchingPerformance(filter);
                case MATCHING_FUNNEL -> port.matchingFunnel(filter);
                case HEATMAP -> port.demandHeatmap(
                        filter,
                        cellSizeMeters,
                        PROJECTED_SRID,
                        null,
                        RESULT_LIMIT
                );
            };
        }
    }

    private record BenchmarkOptions(
            String profile,
            long seed,
            int warmupIterations,
            int measuredIterations,
            String storageDescription,
            Path outputDirectory
    ) {
        static BenchmarkOptions fromSystemProperties() {
            String profile = System.getProperty("admin.analytics.benchmark.profile", "smoke");
            long seed = Long.parseLong(
                    System.getProperty("admin.analytics.benchmark.seed", "5537")
            );
            int warmups = positiveInt(
                    "admin.analytics.benchmark.warmups",
                    10
            );
            int measurements = positiveInt(
                    "admin.analytics.benchmark.measurements",
                    50
            );
            String output = System.getProperty("admin.analytics.benchmark.output");
            String storageDescription = System.getProperty(
                    "admin.analytics.benchmark.storage-description",
                    "not asserted"
            );
            return new BenchmarkOptions(
                    profile,
                    seed,
                    warmups,
                    measurements,
                    storageDescription,
                    output == null || output.isBlank() ? null : Path.of(output)
            );
        }

        private static int positiveInt(String property, int defaultValue) {
            int value = Integer.parseInt(
                    System.getProperty(property, Integer.toString(defaultValue))
            );
            if (value <= 0) {
                throw new IllegalArgumentException(property + " must be positive");
            }
            return value;
        }
    }

    private record CorrectnessCase(
            String queryId,
            String directResultSha256,
            String materializedResultSha256,
            boolean passed
    ) {
    }

    private record CaseMeasurement(Map<String, List<Sample>> samples) {
    }

    private record QuerySummary(
            String queryId,
            String variant,
            AdminAnalyticsBenchmarkStatistics.Summary statistics
    ) {
    }

    private record QueryCaseManifest(
            String queryId,
            Instant from,
            Instant to,
            String reportingTimezone,
            String vehicleType,
            Long serviceAreaId,
            String bucket,
            Integer cellSizeMeters
    ) {
        static QueryCaseManifest from(QueryCase queryCase) {
            return new QueryCaseManifest(
                    queryCase.id(),
                    queryCase.filter().from(),
                    queryCase.filter().to(),
                    queryCase.filter().reportingTimezone().getId(),
                    queryCase.filter().vehicleType().name(),
                    queryCase.filter().serviceAreaId(),
                    queryCase.bucket() == null ? null : queryCase.bucket().name(),
                    queryCase.cellSizeMeters()
            );
        }
    }

    private record RefreshEvidence(
            int sequence,
            Instant startedAt,
            Instant completedAt,
            long durationMs,
            boolean concurrent,
            Instant cutoff,
            Map<String, Long> rowsBefore,
            Map<String, Long> rowsAfter
    ) {
    }
}
