package com.syy.taskflowinsight.api;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * TFI 路由端到端基准（compare 热路径）：
 * - routing.enabled=true vs false
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class TFIRoutingBenchmark {

    private Object a;
    private Object b;

    @Setup(Level.Trial)
    public void setup() {
        // 使用相等对象以测试热路径（最小工作量，关注路由开销）
        this.a = "abcdef";
        this.b = "abcdef";
        // 预热：确保类加载完成
        System.setProperty("tfi.api.routing.enabled", "true");
        TFI.compare(a, b);
        System.setProperty("tfi.api.routing.enabled", "false");
        TFI.compare(a, b);
    }

    @Benchmark
    public void compare_routing_enabled(Blackhole bh) {
        System.setProperty("tfi.api.routing.enabled", "true");
        bh.consume(TFI.compare(a, b));
    }

    @Benchmark
    public void compare_routing_disabled(Blackhole bh) {
        System.setProperty("tfi.api.routing.enabled", "false");
        bh.consume(TFI.compare(a, b));
    }
}

