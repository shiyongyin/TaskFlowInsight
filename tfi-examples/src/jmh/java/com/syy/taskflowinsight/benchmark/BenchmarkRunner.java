package com.syy.taskflowinsight.benchmark;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH 基准测试运行器。
 *
 * <p>提供命令行入口，用于执行 tfi-examples 模块中的 JMH 微基准测试。
 * 支持按名称过滤基准或一次性运行全部。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * # 编译
 * ./mvnw clean compile -P bench
 *
 * # 运行所有基准
 * ./mvnw exec:java -P bench -Dexec.mainClass="com.syy.taskflowinsight.benchmark.BenchmarkRunner"
 *
 * # 运行特定基准
 * ./mvnw exec:java -P bench -Dexec.mainClass="com.syy.taskflowinsight.benchmark.BenchmarkRunner" \
 *   -Dexec.args="FilterBenchmarks.filterLargeObject"
 * }</pre>
 *
 * <h3>forks(0) 说明</h3>
 * <p>当前使用 {@code forks(0)}（在当前 JVM 内运行），原因是 tfi-examples
 * 模块通过 Maven exec 插件启动，fork 子进程会遇到 classpath 隔离问题。
 * 如需生产级别的可复现结果，建议使用独立的 JMH JAR 方式运行并设置 {@code forks(1+)}。</p>
 *
 * @author TaskFlowInsight Team
 * @since 3.0.0
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(args.length > 0 ? args[0] : ".*")
            .forks(0) // 避免 classpath 隔离问题；生产级测试请设为 1+
            .warmupIterations(3)
            .warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(1))
            .measurementIterations(5)
            .measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(2))
            .build();

        new Runner(opt).run();
    }
}
