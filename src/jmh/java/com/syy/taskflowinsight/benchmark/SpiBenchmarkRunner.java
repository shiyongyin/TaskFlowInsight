package com.syy.taskflowinsight.benchmark;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * 仅运行 SPI（ProviderRegistry）相关基准，并输出 JSON 报告。
 *
 * 使用方式：
 * ./mvnw -q -P bench exec:java -Dexec.mainClass=com.syy.taskflowinsight.benchmark.SpiBenchmarkRunner \
 *   -Dspi.perf.out=docs/task/v4.0.0/baseline/spi_perf.json
 */
public class SpiBenchmarkRunner {
    public static void main(String[] args) throws RunnerException {
        String out = System.getProperty("spi.perf.out", "docs/task/v4.0.0/baseline/spi_perf.json");

        Options opt = new OptionsBuilder()
            .include("com.syy.taskflowinsight.spi.ProviderRegistryBenchmark.*")
            .forks(0)
            .warmupIterations(3)
            .warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(1))
            .measurementIterations(8)
            .measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(2))
            .result(out)
            .resultFormat(ResultFormatType.JSON)
            .build();

        new Runner(opt).run();
        System.out.println("SPI benchmark results written to: " + out);
    }
}

