package com.syy.tfi.kernel.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * 执行固定 JMH 口径并拒绝空结果，避免 benchmark fork 失败时 Maven 假绿。
 */
class KernelBenchmarkRunnerTest {
    private static final Path JSON_REPORT = Path.of("target", "kernel-jmh-results.json");
    private static final Path GATE_REPORT = Path.of("target", "kernel-jmh-gate.properties");

    @Test
    void fixedKernelBenchmarksProduceTimingAndAllocationEvidence() throws Exception {
        Options options = new OptionsBuilder()
                .include(".*" + KernelBenchmarks.class.getSimpleName() + ".*")
                .shouldFailOnError(true)
                .addProfiler(GCProfiler.class)
                .resultFormat(ResultFormatType.JSON)
                .result(JSON_REPORT.toString())
                .build();

        Collection<RunResult> results = new Runner(options).run();
        assertThat(results).hasSize(20);
        writeGateReport(results);
        assertThat(Files.size(JSON_REPORT)).isPositive();
        assertThat(Files.size(GATE_REPORT)).isPositive();
    }

    private static void writeGateReport(Collection<RunResult> results) throws IOException, RunnerException {
        Properties values = new Properties();
        for (RunResult run : results) {
            String method = run.getParams().getBenchmark();
            method = method.substring(method.lastIndexOf('.') + 1);
            method += parameterSuffix(run);
            double p50 = run.getPrimaryResult().getStatistics().getPercentile(50.0);
            Result<?> allocation = run.getSecondaryResults().get("gc.alloc.rate.norm");
            if (allocation == null) {
                throw new RunnerException("JMH GC profiler did not report allocation for " + method);
            }
            values.setProperty(method + ".p50Ns", Double.toString(p50));
            values.setProperty(method + ".allocBytes", Double.toString(allocation.getScore()));
        }
        try (OutputStream output = Files.newOutputStream(GATE_REPORT)) {
            values.store(output, "tfi-kernel JMH gate evidence");
        }
    }

    private static String parameterSuffix(RunResult run) {
        return run.getParams().getParamsKeys().stream()
                .sorted(Comparator.naturalOrder())
                .map(key -> "." + key + "-" + run.getParams().getParam(key))
                .collect(Collectors.joining());
    }
}
