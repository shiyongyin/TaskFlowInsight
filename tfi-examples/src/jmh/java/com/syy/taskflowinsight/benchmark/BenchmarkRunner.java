package com.syy.taskflowinsight.benchmark;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH基准测试运行器
 *
 * <p>使用方式：
 * <pre>
 * # 编译
 * ./mvnw clean compile -P bench
 *
 * # 运行所有基准
 * ./mvnw exec:java -P bench -Dexec.mainClass="com.syy.taskflowinsight.benchmark.BenchmarkRunner"
 *
 * # 运行特定基准
 * ./mvnw exec:java -P bench -Dexec.mainClass="com.syy.taskflowinsight.benchmark.BenchmarkRunner" \
 *   -Dexec.args="FilterBenchmarks.filterLargeObject"
 * </pre>
 *
 * @author TaskFlowInsight Team
 * @since 2025-10-09
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(args.length > 0 ? args[0] : ".*")  // 如果有参数，运行指定基准；否则运行全部
            .forks(0)  // 不fork，使用当前JVM（避免classpath问题）
            .warmupIterations(3)
            .warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(1))
            .measurementIterations(5)
            .measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(2))
            .build();

        new Runner(opt).run();
    }
}
