package com.syy.taskflowinsight.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/** 运行 routing/legacy 回归与 21-workload 生产基准，并把本次运行结果写入模块 target 目录。 */
public final class TfiRoutingBenchmarkRunner {

    private static final String ROUTING_BENCHMARK =
            "com.syy.taskflowinsight.api.TFIRoutingBenchmark.compare_routing_enabled";
    private static final String LEGACY_BENCHMARK =
            "com.syy.taskflowinsight.api.TFIRoutingBenchmark.compare_routing_disabled";

    private TfiRoutingBenchmarkRunner() {
        // utility class
    }

    /**
     * 使用真实 Java classpath 启动 forked JMH。
     *
     * <pre>{@code
     * ./mvnw -pl tfi-examples -Pbench -DskipTests compile \
     *   org.codehaus.mojo:exec-maven-plugin:3.5.0:exec \
     *   -Dexec.executable=java -Dexec.classpathScope=runtime \
     *   '-Dexec.args=-cp %classpath \
     *   com.syy.taskflowinsight.benchmark.TfiRoutingBenchmarkRunner'
     * }</pre>
     *
     * @param args unused
     * @throws IOException if the target report directory cannot be created
     * @throws RunnerException if a selected benchmark produces no result
     */
    public static void main(String[] args) throws IOException, RunnerException {
        String routingOutput = System.getProperty(
                "tfi.perf.out.routing", "target/perf/tfi-routing-enabled.json");
        String legacyOutput = System.getProperty(
                "tfi.perf.out.legacy", "target/perf/tfi-routing-legacy.json");
        int forks = Integer.parseInt(System.getProperty("jmh.forks", "1"));

        createParentDirectory(routingOutput);
        createParentDirectory(legacyOutput);
        runBenchmark(ROUTING_BENCHMARK, routingOutput, forks, "routing");
        runBenchmark(LEGACY_BENCHMARK, legacyOutput, forks, "legacy");
        // 发布流水线只有一个 P-JMH commandId；生产 workload 必须属于同一次可审计执行。
        CompareProductionBenchmarkRunner.main(args);

        System.out.println("TFI routing benchmark results written to:\n - "
                + routingOutput + "\n - " + legacyOutput);
    }

    private static void runBenchmark(
            String benchmark, String output, int forks, String label) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(benchmark)
                .forks(forks)
                .warmupIterations(3)
                .measurementIterations(8)
                .result(output)
                .resultFormat(ResultFormatType.JSON)
                .build();
        Collection<RunResult> results = new Runner(options).run();
        requireResults(results, label);
    }

    static void requireResults(Collection<RunResult> results, String label)
            throws RunnerException {
        if (results.isEmpty()) {
            throw new RunnerException(
                    "JMH " + label + " run completed without a successful result");
        }
    }

    private static void createParentDirectory(String output) throws IOException {
        Path parent = Path.of(output).toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
