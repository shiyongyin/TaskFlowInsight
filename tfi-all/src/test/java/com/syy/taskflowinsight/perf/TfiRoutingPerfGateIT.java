package com.syy.taskflowinsight.perf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 严格模式下比较同一次 CI 运行生成的 routing 与 legacy JMH 报告。 */
public class TfiRoutingPerfGateIT {

    private static final String ROUTING_BENCHMARK =
            "com.syy.taskflowinsight.api.TFIRoutingBenchmark.compare_routing_enabled";
    private static final String LEGACY_BENCHMARK =
            "com.syy.taskflowinsight.api.TFIRoutingBenchmark.compare_routing_disabled";
    private static final String ROUTING_REPORT_PROPERTY = "tfi.perf.report.routing";
    private static final String LEGACY_REPORT_PROPERTY = "tfi.perf.report.legacy";
    private static final double MAX_REGRESSION_RATIO = 1.05;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("Routing 与 Legacy 平均时延劣化不超过 5%")
    void routingPerfShouldNotRegressOverFivePercent() throws IOException {
        if (!Boolean.getBoolean("tfi.perf.strict")
                || !Boolean.getBoolean("tfi.perf.enabled")) {
            return;
        }

        verifyReports(
                reportPath(ROUTING_REPORT_PROPERTY, "tfi-routing-enabled.json"),
                reportPath(LEGACY_REPORT_PROPERTY, "tfi-routing-legacy.json"));
    }

    static void verifyReports(Path routingReport, Path legacyReport) throws IOException {
        requireGeneratedReport(routingReport);
        requireGeneratedReport(legacyReport);

        double routingAverage = extractAverageTimeScore(routingReport, ROUTING_BENCHMARK);
        double legacyAverage = extractAverageTimeScore(legacyReport, LEGACY_BENCHMARK);
        double ratio = routingAverage / legacyAverage;
        if (ratio > MAX_REGRESSION_RATIO) {
            double regressionPercent = (ratio - 1.0) * 100;
            throw new AssertionError(buildErrorMessage(
                    routingAverage, legacyAverage, regressionPercent));
        }
    }

    private static void requireGeneratedReport(Path report) {
        if (!Files.isRegularFile(report)) {
            throw new IllegalStateException("Missing generated JMH report: " + report);
        }
    }

    private static double extractAverageTimeScore(Path report, String expectedBenchmark)
            throws IOException {
        JsonNode root;
        try {
            root = JSON.readTree(report.toFile());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid JMH JSON report: " + report, exception);
        }
        if (!root.isArray()) {
            throw new IllegalArgumentException("JMH report must be a JSON array: " + report);
        }

        for (JsonNode result : root) {
            if (!expectedBenchmark.equals(result.path("benchmark").asText())) {
                continue;
            }
            String mode = result.path("mode").asText();
            String scoreUnit = result.path("primaryMetric").path("scoreUnit").asText();
            JsonNode scoreNode = result.path("primaryMetric").path("score");
            double score = scoreNode.asDouble(Double.NaN);
            if (!"avgt".equals(mode) || !"ns/op".equals(scoreUnit)
                    || !scoreNode.isNumber() || !Double.isFinite(score) || score <= 0.0) {
                throw new IllegalArgumentException(
                        "Invalid average-time JMH result for " + expectedBenchmark
                                + " in " + report);
            }
            return score;
        }
        throw new IllegalArgumentException(
                "Missing benchmark " + expectedBenchmark + " in " + report);
    }

    private static Path reportPath(String property, String fileName) {
        String override = System.getProperty(property);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        return repositoryRoot().resolve("tfi-examples/target/perf").resolve(fileName);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isDirectory(current.resolve("tfi-examples"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Cannot locate TaskFlowInsight repository root");
        }
        return current;
    }

    private static String buildErrorMessage(
            double routingAverage, double legacyAverage, double regressionPercent) {
        return String.format(
                "Routing perf regression too high: avg_ns %.2f vs %.2f (%.2f%%)",
                routingAverage, legacyAverage, regressionPercent);
    }
}
