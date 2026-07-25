package com.syy.taskflowinsight.benchmark;

import java.util.Collection;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Entry point for running TFI Flow Core JMH benchmarks.
 *
 * <p>Provides a convenient main method that configures and launches
 * facade benchmarks and the model-package gate/capture benchmarks. Results are
 * written to {@code target/jmh-results.json} in JSON format.
 *
 * <h3>Usage</h3>
 * <pre>{@code
     * # Compile and run all benchmarks with a real test classpath
     * ./mvnw -pl tfi-flow-core -Dtfi.perf.enabled=true -DskipTests \
     *     test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:exec \
     *     -Dexec.executable=java -Dexec.classpathScope=test \
     *     '-Dexec.args=-Djmh.forks=1 -cp %classpath \
     *     com.syy.taskflowinsight.benchmark.BenchmarkRunner'
 *
 * # Run specific benchmark by regex filter
     * ./mvnw -pl tfi-flow-core -Dtfi.perf.enabled=true -DskipTests \
     *     test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:exec \
     *     -Dexec.executable=java -Dexec.classpathScope=test \
     *     '-Dexec.args=-Djmh.forks=1 -cp %classpath \
     *     com.syy.taskflowinsight.benchmark.BenchmarkRunner bm001'
 *
 * # Run with custom JMH options
 * java -cp "target/test-classes:target/classes:..." \
 *     com.syy.taskflowinsight.benchmark.BenchmarkRunner bm004
 * }</pre>
 *
 * @since 4.0.0
 */
public final class BenchmarkRunner {

    private static final String DEFAULT_INCLUDE =
        ".*(?:TfiFlowBenchmark|TaskTreeMutationBenchmark).*";

    private BenchmarkRunner() {
        // utility class
    }

    /**
     * Launch JMH benchmarks.
     *
     * <p>Supports the following system properties:
     * <ul>
     *   <li>{@code -Djmh.forks=N} - override fork count (default: 1). A forked
     *       run requires a real Java classpath, such as {@code exec:exec} with
     *       {@code -cp %classpath}; {@code exec:java} is not supported.</li>
     * </ul>
     *
     * @param args optional regex filter for benchmark method names
     *             (e.g. "bm001" to run only BM-001).
     *             If omitted, both facade and task-tree gate benchmark classes
     *             are executed.
     * @throws RunnerException if benchmark execution fails
     */
    public static void main(String[] args) throws RunnerException {
        String include = args.length > 0
            ? ".*" + args[0] + ".*"
            : DEFAULT_INCLUDE;

        int forks = Integer.parseInt(
            System.getProperty("jmh.forks", "1"));

        Options options = new OptionsBuilder()
            .include(include)
            .forks(forks)
            .resultFormat(ResultFormatType.JSON)
            .result("target/jmh-results.json")
            .build();

        Collection<RunResult> results = new Runner(options).run();
        requireResults(results);
    }

    /**
     * Rejects runs where every selected benchmark failed before producing a result.
     *
     * <p>JMH logs fork failures but returns an empty collection, so ignoring this
     * value makes Maven report a false success.
     */
    static void requireResults(Collection<RunResult> results) throws RunnerException {
        if (results.isEmpty()) {
            throw new RunnerException(
                "JMH completed without any successful benchmark results");
        }
    }
}
