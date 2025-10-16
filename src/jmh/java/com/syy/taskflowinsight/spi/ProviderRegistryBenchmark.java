package com.syy.taskflowinsight.spi;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * ProviderRegistry 基准：
 * - 冷启动：首次 ServiceLoader 加载 + 排序 + 缓存
 * - 热路径：缓存命中下的 lookup()
 */
@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class ProviderRegistryBenchmark {

    @Setup(Level.Trial)
    public void setup() {
        ProviderRegistry.clearAll();
        // 预热一次 ServiceLoader，确保热路径基准测量的是缓存命中
        ProviderRegistry.lookup(ComparisonProvider.class);
    }

    @Benchmark
    public void hotPath_cachedLookup(Blackhole bh) {
        // 预期 P95 < 1µs（1000 ns），以平均时延作为参考
        ComparisonProvider p = ProviderRegistry.lookup(ComparisonProvider.class);
        bh.consume(p);
    }

    @Benchmark
    @OperationsPerInvocation(1)
    public void coldStart_serviceLoaderFirstLoad(Blackhole bh) {
        // 清空缓存与注册，模拟冷启动
        ProviderRegistry.clearAll();
        ComparisonProvider p = ProviderRegistry.lookup(ComparisonProvider.class);
        bh.consume(p);
    }
}

