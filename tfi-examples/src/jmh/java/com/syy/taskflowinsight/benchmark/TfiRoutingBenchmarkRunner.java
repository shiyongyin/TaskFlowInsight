package com.syy.taskflowinsight.benchmark;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * 运行 TFI 路由端到端基准，并输出 JSON 报告（分别针对 routing.enabled=true/false）。
 *
 * 使用方式：
 * ./mvnw -q -P bench exec:java -Dexec.mainClass=com.syy.taskflowinsight.benchmark.TfiRoutingBenchmarkRunner
 */
public class TfiRoutingBenchmarkRunner {
    public static void main(String[] args) throws RunnerException {
        String outRouting = System.getProperty("tfi.perf.out.routing", "docs/task/v4.0.0/baseline/tfi_routing_enabled.json");
        String outLegacy = System.getProperty("tfi.perf.out.legacy", "docs/task/v4.0.0/baseline/tfi_routing_legacy.json");

        // routing.enabled=true
        System.setProperty("tfi.api.routing.enabled", "true");
        Options opt1 = new OptionsBuilder()
            .include("com.syy.taskflowinsight.api.TFIRoutingBenchmark.compare_routing_enabled")
            .forks(0)
            .warmupIterations(3)
            .measurementIterations(8)
            .result(outRouting)
            .resultFormat(ResultFormatType.JSON)
            .build();
        new Runner(opt1).run();

        // routing.enabled=false
        System.setProperty("tfi.api.routing.enabled", "false");
        Options opt2 = new OptionsBuilder()
            .include("com.syy.taskflowinsight.api.TFIRoutingBenchmark.compare_routing_disabled")
            .forks(0)
            .warmupIterations(3)
            .measurementIterations(8)
            .result(outLegacy)
            .resultFormat(ResultFormatType.JSON)
            .build();
        new Runner(opt2).run();

        System.out.println("TFI routing benchmark results written to: \n - " + outRouting + "\n - " + outLegacy);
    }
}

