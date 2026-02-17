package com.syy.taskflowinsight.benchmark;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.results.format.ResultFormatType;

/**
 * Entry point for running TFI Flow Core JMH benchmarks.
 *
 * <p>Provides a convenient main method that configures and launches
 * all benchmarks defined in {@link TfiFlowBenchmark}. Results are
 * written to {@code target/jmh-results.json} in JSON format.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * # Run all benchmarks via Maven perf profile
 * mvn test -Dtfi.perf.enabled=true \
 *     exec:java \
 *     -Dexec.mainClass=com.syy.taskflowinsight.benchmark.BenchmarkRunner
 *
 * # Run specific benchmark by regex filter
 * mvn test -Dtfi.perf.enabled=true \
 *     exec:java \
 *     -Dexec.mainClass=com.syy.taskflowinsight.benchmark.BenchmarkRunner \
 *     -Dexec.args="bm001"
 *
 * # Run with custom JMH options
 * java -cp "target/test-classes:target/classes:..." \
 *     com.syy.taskflowinsight.benchmark.BenchmarkRunner bm004
 * }</pre>
 *
 * @since 4.0.0
 */
public final class BenchmarkRunner {

    private BenchmarkRunner() {
        // utility class
    }

    /**
     * Launch JMH benchmarks.
     *
     * <p>Supports the following system properties:
     * <ul>
     *   <li>{@code -Djmh.forks=N} — override fork count (default: 0 for
     *       in-process execution; set to 1 for production benchmarks
     *       when running via {@code exec:exec} or uber JAR)</li>
     * </ul>
     *
     * @param args optional regex filter for benchmark method names
     *             (e.g. "bm001" to run only BM-001).
     *             If omitted, all benchmarks in {@link TfiFlowBenchmark}
     *             are executed.
     * @throws RunnerException if benchmark execution fails
     */
    public static void main(String[] args) throws RunnerException {
        String include = args.length > 0
            ? ".*" + args[0] + ".*"
            : TfiFlowBenchmark.class.getSimpleName();

        // Default to forks(0) for in-process execution (works with mvn exec:java).
        // Use -Djmh.forks=1 for proper forked benchmarks (exec:exec or uber JAR).
        int forks = Integer.parseInt(
            System.getProperty("jmh.forks", "0"));

        Options options = new OptionsBuilder()
            .include(include)
            .forks(forks)
            .resultFormat(ResultFormatType.JSON)
            .result("target/jmh-results.json")
            .build();

        new Runner(options).run();
    }
}
