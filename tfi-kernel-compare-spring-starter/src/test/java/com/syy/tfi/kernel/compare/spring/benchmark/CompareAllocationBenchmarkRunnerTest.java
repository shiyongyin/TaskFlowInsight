package com.syy.tfi.kernel.compare.spring.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/** 运行固定 allocation suite，并写出原始 JSON 与阈值裁决输入。 */
class CompareAllocationBenchmarkRunnerTest {

    /** 三个场景的 JMH 原始数据。 */
    static final Path JSON_REPORT = Path.of("target", "compare-allocation-jmh-results.json");

    /** mean、实测最大值、limit、单位和环境组成的机器可读证据。 */
    static final Path GATE_REPORT = Path.of("target", "compare-allocation-gate.properties");

    /** 阈值资源使用稳定 schema，防止属性缺失时退化为无限预算。 */
    static final String BUDGET_RESOURCE =
            "benchmark/compare-allocation-budget.properties";

    /** 门禁必须且只能覆盖的固定方法集合。 */
    static final List<String> SCENARIOS = List.of(
            "compareOnly", "oneTargetSummaryOnly", "eightTargetsSummaryOnly");

    @Test
    void typicalAllocationBenchmarksProduceHardGateEvidence() throws Exception {
        String repositoryRoot = System.getProperty(
                CompareAllocationBenchmarks.REPOSITORY_ROOT_PROPERTY);
        assertThat(repositoryRoot).isNotBlank();
        Options options = new OptionsBuilder()
                .include("^" + CompareAllocationBenchmarks.class.getName()
                        + "\\.(compareOnly|oneTargetSummaryOnly|eightTargetsSummaryOnly)$")
                .shouldFailOnError(true)
                .addProfiler(GCProfiler.class)
                .jvmArgsAppend("-D" + CompareAllocationBenchmarks.REPOSITORY_ROOT_PROPERTY
                        + "=" + repositoryRoot)
                .resultFormat(ResultFormatType.JSON)
                .result(JSON_REPORT.toString())
                .build();

        Collection<RunResult> results = new Runner(options).run();

        assertThat(results).hasSize(SCENARIOS.size());
        writeGateEvidence(results);
        assertThat(Files.size(JSON_REPORT)).isPositive();
        assertThat(Files.size(GATE_REPORT)).isPositive();
    }

    static Properties loadBudget() throws IOException {
        Properties budget = new Properties();
        try (InputStream input = CompareAllocationBenchmarkRunnerTest.class
                .getClassLoader().getResourceAsStream(BUDGET_RESOURCE)) {
            if (input == null) {
                throw new IOException("missing allocation budget: " + BUDGET_RESOURCE);
            }
            budget.load(input);
        }
        assertThat(budget.getProperty("budget.schema"))
                .isEqualTo("TFI_COMPARE_ALLOCATION_BUDGET_V1");
        assertThat(budget.stringPropertyNames()).containsExactlyInAnyOrder(
                "budget.schema",
                "compareOnly.maxBytesPerOp",
                "oneTargetSummaryOnly.maxBytesPerOp",
                "eightTargetsSummaryOnly.maxBytesPerOp");
        return budget;
    }

    private static void writeGateEvidence(Collection<RunResult> results)
            throws IOException, RunnerException {
        Properties budget = loadBudget();
        Properties evidence = new Properties();
        evidence.setProperty("gate.schema", budget.getProperty("budget.schema"));
        for (RunResult run : results) {
            String scenario = scenario(run.getParams().getBenchmark());
            if (!SCENARIOS.contains(scenario)) {
                throw new RunnerException("unexpected allocation scenario: " + scenario);
            }
            Result<?> allocation = run.getSecondaryResults().get("gc.alloc.rate.norm");
            if (allocation == null || !"B/op".equals(allocation.getScoreUnit())) {
                throw new RunnerException(
                        "JMH GC profiler did not report B/op for " + scenario);
            }
            double mean = allocation.getScore();
            double observedMaximum = allocation.getStatistics().getMax();
            double maximum = Double.parseDouble(
                    budget.getProperty(scenario + ".maxBytesPerOp"));
            requireFinitePositive(scenario + " mean", mean);
            requireFinitePositive(scenario + " observed maximum", observedMaximum);
            requireFinitePositive(scenario + " maximum", maximum);
            evidence.setProperty(scenario + ".meanBytesPerOp", Double.toString(mean));
            evidence.setProperty(
                    scenario + ".maxObservedBytesPerOp",
                    Double.toString(observedMaximum));
            evidence.setProperty(scenario + ".maxBytesPerOp", Double.toString(maximum));
            evidence.setProperty(scenario + ".allocationUnit", allocation.getScoreUnit());
        }
        evidence.setProperty("java.version", System.getProperty("java.version"));
        evidence.setProperty("java.vm.name", System.getProperty("java.vm.name"));
        evidence.setProperty("os.name", System.getProperty("os.name"));
        evidence.setProperty("os.arch", System.getProperty("os.arch"));
        Files.createDirectories(GATE_REPORT.getParent());
        try (OutputStream output = Files.newOutputStream(GATE_REPORT)) {
            evidence.store(output, "TFI Compare allocation hard-gate evidence");
        }
    }

    private static String scenario(String benchmark) {
        return benchmark.substring(benchmark.lastIndexOf('.') + 1);
    }

    private static void requireFinitePositive(String name, double value)
            throws RunnerException {
        if (!Double.isFinite(value) || value <= 0.0d) {
            throw new RunnerException(name + " must be finite and positive");
        }
    }
}
