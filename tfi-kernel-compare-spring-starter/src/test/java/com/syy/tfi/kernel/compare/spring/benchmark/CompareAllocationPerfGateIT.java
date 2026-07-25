package com.syy.tfi.kernel.compare.spring.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/** 对固定 TYPICAL 三场景执行 allocation hard limit 裁决。 */
@EnabledIfSystemProperty(named = "tfi.perf.strict", matches = "true")
public class CompareAllocationPerfGateIT {

    @Test
    void typicalAllocationStaysWithinAcceptedBudgets() throws Exception {
        Properties budget = CompareAllocationBenchmarkRunnerTest.loadBudget();
        Properties evidence = new Properties();
        try (InputStream input = Files.newInputStream(
                CompareAllocationBenchmarkRunnerTest.GATE_REPORT)) {
            evidence.load(input);
        }

        assertThat(evidence.getProperty("gate.schema"))
                .isEqualTo(budget.getProperty("budget.schema"));
        CompareAllocationGatePolicy.verify(
                budget, evidence, CompareAllocationBenchmarkRunnerTest.SCENARIOS);
        assertRawJson();
    }

    private static void assertRawJson() throws Exception {
        JsonNode raw = new ObjectMapper().readTree(
                CompareAllocationBenchmarkRunnerTest.JSON_REPORT.toFile());
        assertThat(raw.isArray()).isTrue();
        assertThat(raw).hasSize(CompareAllocationBenchmarkRunnerTest.SCENARIOS.size());
        List<String> scenarios = new ArrayList<>();
        raw.forEach(result -> {
            scenarios.add(scenario(result.path("benchmark").asText()));
            assertThat(result.path("secondaryMetrics")
                    .path("gc.alloc.rate.norm").path("scoreUnit").asText())
                    .isEqualTo("B/op");
            assertThat(result.path("primaryMetric").path("rawData").isArray())
                    .isTrue();
        });
        assertThat(scenarios).containsExactlyInAnyOrderElementsOf(
                CompareAllocationBenchmarkRunnerTest.SCENARIOS);
    }

    private static String scenario(String benchmark) {
        return benchmark.substring(benchmark.lastIndexOf('.') + 1);
    }
}
