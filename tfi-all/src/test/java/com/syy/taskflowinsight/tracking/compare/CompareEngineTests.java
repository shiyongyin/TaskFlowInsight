package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.syy.taskflowinsight.tracking.metrics.MicrometerDiagnosticSink;
import com.syy.taskflowinsight.tracking.ChangeType;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompareEngine 单元测试
 * M2: 验证引擎的快速检查、List路由、排序唯一性
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M2
 * @since 2025-10-04
 */
class CompareEngineTests {

    private CompareEngine engine;

    @BeforeEach
    void setUp() {
        // 最小初始化（无指标、无ListExecutor）
        engine = new CompareEngine(
            new StrategyResolver(),
            null, // TfiMetrics
            null, // MicrometerDiagnosticSink
            null, // ListCompareExecutor
            new java.util.concurrent.ConcurrentHashMap<>(),
            new java.util.concurrent.ConcurrentHashMap<>()
        );
    }

    @Test
    void execute_quickChecks_sameRef() {
        Object obj = new Object();
        CompareResult result = engine.execute(obj, obj, CompareOptions.DEFAULT);

        assertNotNull(result);
        assertTrue(result.isIdentical());
        assertEquals(0, result.getChangeCount());
    }

    @Test
    void execute_quickChecks_nullDiff() {
        CompareResult result = engine.execute(null, "test", CompareOptions.DEFAULT);

        assertNotNull(result);
        assertFalse(result.isIdentical());
        assertNull(result.getObject1());
        assertEquals("test", result.getObject2());
    }

    @Test
    void execute_quickChecks_typeDiff() {
        CompareResult result = engine.execute("string", 123, CompareOptions.DEFAULT);

        assertNotNull(result);
        assertFalse(result.isIdentical());
    }

    @Test
    void execute_fallback_noStrategy() {
        // 无策略匹配时返回 identical
        CompareResult result = engine.execute("a", "b", CompareOptions.DEFAULT);

        assertNotNull(result);
        // Fallback 返回 identical（未来可扩展为深度比较）
        assertTrue(result.isIdentical());
    }

    // P0修复：删除sortChanges测试（该方法已移除，排序仅在execute路径内部发生）
    // 排序功能由execute路径的集成测试覆盖

    @Test
    void execute_handlesPerfGuardSignals_partialOrDegraded() {
        // 使用 MicrometerDiagnosticSink 以验证降级原因打点
        MeterRegistry registry = new SimpleMeterRegistry();
        MicrometerDiagnosticSink sink = new MicrometerDiagnosticSink(registry);

        CompareEngine engineWithMetrics = new CompareEngine(
            new StrategyResolver(),
            null,
            sink,
            null,
            new java.util.concurrent.ConcurrentHashMap<>(),
            new java.util.concurrent.ConcurrentHashMap<>()
        );

        CompareResult r = CompareResult.builder()
            .object1("a").object2("b")
            .identical(false)
            .changes(java.util.List.of(
                FieldChange.builder().fieldName("x").fieldPath("x").changeType(ChangeType.UPDATE).build()
            ))
            .degradationReasons(java.util.List.of("timeout"))
            .build();

        long start = System.nanoTime();
        engineWithMetrics.sortResult(r, start, "test");

        // 验证降级原因计数（Micrometer 有 reason 标签）
        assertNotNull(registry.find("tfi.perf.degradation").tag("reason", "timeout").counter());
        assertEquals(1.0, registry.find("tfi.perf.degradation").tag("reason", "timeout").counter().count(), 0.001);
    }
}
