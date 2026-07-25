package com.syy.tfi.kernel.compare.spring.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证初次基线证据完整且数值有效；本阶段明确不接受或执行性能预算。 */
public class AopPerfBaselineIT {

    /** 必须且只能出现的三个固定 benchmark 场景。 */
    private static final List<String> SCENARIOS = List.of(
            "directInvocation", "oneTargetSummaryOnly", "eightTargetsSummaryOnly");
    /** 基线摘要必须携带的运行环境与 JMH 参数。 */
    private static final List<String> ENVIRONMENT_KEYS = List.of(
            "java.version", "java.vm.name", "java.vm.version",
            "os.name", "os.version", "os.arch", "cpu.availableProcessors",
            "jmh.version", "jmh.mode", "jmh.threads", "jmh.forks",
            "jmh.warmup.iterations", "jmh.warmup.time",
            "jmh.measurement.iterations", "jmh.measurement.time");

    @Test
    void initialBaselineContainsThreeFiniteResultsWithoutThresholds()
            throws Exception {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(
                AopBenchmarkRunnerTest.BASELINE_REPORT)) {
            values.load(input);
        }

        assertThat(values.getProperty("baseline.kind"))
                .isEqualTo("INITIAL_NO_THRESHOLD");
        SCENARIOS.forEach(scenario -> {
            assertMetric(values, scenario + ".timePerOp");
            assertMetric(values, scenario + ".allocationPerOp");
            assertThat(values.getProperty(scenario + ".timeUnit")).isEqualTo("ns/op");
            assertThat(values.getProperty(scenario + ".allocationUnit")).isEqualTo("B/op");
        });
        ENVIRONMENT_KEYS.forEach(key ->
                assertThat(values.getProperty(key)).as(key).isNotBlank());
        assertThat(values.stringPropertyNames())
                .noneMatch(key -> key.toLowerCase(java.util.Locale.ROOT)
                        .contains("threshold"));

        assertRawJson();
    }

    private static void assertRawJson() throws Exception {
        JsonNode raw = new ObjectMapper().readTree(
                AopBenchmarkRunnerTest.JSON_REPORT.toFile());
        assertThat(raw.isArray()).isTrue();
        assertThat(raw).hasSize(3);
        List<String> benchmarks = new ArrayList<>();
        raw.forEach(result -> {
            benchmarks.add(scenario(result.path("benchmark").asText()));
            assertThat(result.path("primaryMetric").path("scoreUnit").asText())
                    .isEqualTo("ns/op");
            assertThat(result.path("secondaryMetrics")
                    .path("gc.alloc.rate.norm").path("scoreUnit").asText())
                    .isEqualTo("B/op");
            assertThat(result.path("primaryMetric").path("rawData").isArray())
                    .isTrue();
        });
        assertThat(benchmarks).containsExactlyInAnyOrderElementsOf(SCENARIOS);
    }

    private static void assertMetric(Properties values, String key) {
        String value = values.getProperty(key);
        assertThat(value).as("missing JMH metric " + key).isNotNull();
        double metric = Double.parseDouble(value);
        assertThat(Double.isFinite(metric)).as("finite JMH metric " + key).isTrue();
        assertThat(metric).as("non-negative JMH metric " + key)
                .isGreaterThanOrEqualTo(0.0);
    }

    private static String scenario(String benchmark) {
        return benchmark.substring(benchmark.lastIndexOf('.') + 1);
    }
}
