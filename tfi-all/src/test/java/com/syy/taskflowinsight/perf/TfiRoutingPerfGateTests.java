package com.syy.taskflowinsight.perf;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TfiRoutingPerfGateTests {

    private static final String ROUTING_BENCHMARK =
            "com.syy.taskflowinsight.api.TFIRoutingBenchmark.compare_routing_enabled";
    private static final String LEGACY_BENCHMARK =
            "com.syy.taskflowinsight.api.TFIRoutingBenchmark.compare_routing_disabled";

    @TempDir
    Path tempDirectory;

    @Test
    void strictGateRejectsMissingGeneratedReport() {
        Path routing = tempDirectory.resolve("routing.json");
        Path legacy = tempDirectory.resolve("legacy.json");

        assertThatThrownBy(() -> TfiRoutingPerfGateIT.verifyReports(routing, legacy))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing generated JMH report")
                .hasMessageContaining(routing.toString());
    }

    @Test
    void strictGateAcceptsARegressionBelowFivePercent() throws Exception {
        Path routing = writeReport("routing.json", ROUTING_BENCHMARK, 104.9);
        Path legacy = writeReport("legacy.json", LEGACY_BENCHMARK, 100.0);

        assertThatCode(() -> TfiRoutingPerfGateIT.verifyReports(routing, legacy))
                .doesNotThrowAnyException();
    }

    @Test
    void strictGateRejectsARegressionAboveFivePercent() throws Exception {
        Path routing = writeReport("routing.json", ROUTING_BENCHMARK, 105.1);
        Path legacy = writeReport("legacy.json", LEGACY_BENCHMARK, 100.0);

        assertThatThrownBy(() -> TfiRoutingPerfGateIT.verifyReports(routing, legacy))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("5.10%");
    }

    @Test
    void strictGateRejectsReportWithoutTheExpectedBenchmark() throws Exception {
        Path routing = writeReport("routing.json", "other.Benchmark", 100.0);
        Path legacy = writeReport("legacy.json", LEGACY_BENCHMARK, 100.0);

        assertThatThrownBy(() -> TfiRoutingPerfGateIT.verifyReports(routing, legacy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ROUTING_BENCHMARK);
    }

    private Path writeReport(String name, String benchmark, double score) throws Exception {
        Path report = tempDirectory.resolve(name);
        Files.writeString(report, """
                [{
                  "benchmark": "%s",
                  "mode": "avgt",
                  "primaryMetric": {
                    "score": %s,
                    "scoreUnit": "ns/op"
                  }
                }]
                """.formatted(benchmark, score));
        return report;
    }
}
