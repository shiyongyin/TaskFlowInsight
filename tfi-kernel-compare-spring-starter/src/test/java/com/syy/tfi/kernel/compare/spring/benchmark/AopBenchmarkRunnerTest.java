package com.syy.tfi.kernel.compare.spring.benchmark;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/** 运行固定三场景并同时保留 JMH JSON 与可机器核验的 baseline 摘要。 */
class AopBenchmarkRunnerTest {

    /** JMH 包含每次 iteration 原始样本的 JSON 报告。 */
    static final Path JSON_REPORT = Path.of("target", "aop-jmh-results.json");
    /** 三组 time/op、allocation/op 与环境参数摘要。 */
    static final Path BASELINE_REPORT = Path.of("target", "aop-jmh-baseline.properties");

    @Test
    void fixedAopBenchmarksProduceTimingAllocationAndEnvironmentEvidence()
            throws Exception {
        Options options = new OptionsBuilder()
                .include(".*" + AopBenchmarks.class.getSimpleName() + ".*")
                .shouldFailOnError(true)
                .addProfiler(GCProfiler.class)
                .resultFormat(ResultFormatType.JSON)
                .result(JSON_REPORT.toString())
                .build();

        Collection<RunResult> results = new Runner(options).run();

        assertThat(results).hasSize(3);
        writeBaseline(results);
        assertThat(Files.size(JSON_REPORT)).isPositive();
        assertThat(Files.size(BASELINE_REPORT)).isPositive();
    }

    private static void writeBaseline(Collection<RunResult> results)
            throws IOException, RunnerException {
        Properties values = new Properties();
        for (RunResult run : results) {
            String scenario = scenario(run.getParams().getBenchmark());
            Result<?> timing = run.getPrimaryResult();
            Result<?> allocation = run.getSecondaryResults().get("gc.alloc.rate.norm");
            if (allocation == null) {
                throw new RunnerException(
                        "JMH GC profiler did not report allocation for " + scenario);
            }
            requireFiniteNonNegative(scenario + " time/op", timing.getScore());
            requireFiniteNonNegative(scenario + " allocation/op", allocation.getScore());
            values.setProperty(scenario + ".timePerOp", Double.toString(timing.getScore()));
            values.setProperty(scenario + ".timeUnit", timing.getScoreUnit());
            values.setProperty(
                    scenario + ".allocationPerOp", Double.toString(allocation.getScore()));
            values.setProperty(scenario + ".allocationUnit", allocation.getScoreUnit());
        }

        BenchmarkParams params = results.iterator().next().getParams();
        addEnvironment(values, params);
        Files.createDirectories(BASELINE_REPORT.getParent());
        try (OutputStream output = Files.newOutputStream(BASELINE_REPORT)) {
            values.store(output, "tfi-kernel-compare AOP initial baseline; no threshold");
        }
    }

    private static void addEnvironment(Properties values, BenchmarkParams params) {
        values.setProperty("baseline.kind", "INITIAL_NO_THRESHOLD");
        values.setProperty("java.version", System.getProperty("java.version"));
        values.setProperty("java.vm.name", System.getProperty("java.vm.name"));
        values.setProperty("java.vm.version", System.getProperty("java.vm.version"));
        values.setProperty("os.name", System.getProperty("os.name"));
        values.setProperty("os.version", System.getProperty("os.version"));
        values.setProperty("os.arch", System.getProperty("os.arch"));
        values.setProperty(
                "cpu.availableProcessors",
                Integer.toString(Runtime.getRuntime().availableProcessors()));
        values.setProperty("jmh.version", params.getJmhVersion());
        values.setProperty("jmh.mode", params.getMode().name());
        values.setProperty("jmh.threads", Integer.toString(params.getThreads()));
        values.setProperty("jmh.forks", Integer.toString(params.getForks()));
        values.setProperty(
                "jmh.warmup.iterations", Integer.toString(params.getWarmup().getCount()));
        values.setProperty("jmh.warmup.time", params.getWarmup().getTime().toString());
        values.setProperty(
                "jmh.measurement.iterations",
                Integer.toString(params.getMeasurement().getCount()));
        values.setProperty(
                "jmh.measurement.time", params.getMeasurement().getTime().toString());
    }

    private static String scenario(String benchmark) {
        return benchmark.substring(benchmark.lastIndexOf('.') + 1);
    }

    private static void requireFiniteNonNegative(String name, double value)
            throws RunnerException {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new RunnerException(name + " must be finite and non-negative");
        }
    }
}
