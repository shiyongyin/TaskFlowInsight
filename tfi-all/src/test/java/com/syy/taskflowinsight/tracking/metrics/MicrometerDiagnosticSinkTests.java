package com.syy.taskflowinsight.tracking.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MicrometerDiagnosticSink 单元测试
 * M2: 验证指标门面的 No-Op 模式和正常记录
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M2
 * @since 2025-10-04
 */
class MicrometerDiagnosticSinkTests {

    @Test
    void construct_withNullRegistry_isNoOp() {
        MicrometerDiagnosticSink sink = new MicrometerDiagnosticSink(null);

        assertTrue(sink.isNoOp());
        assertNull(sink.getRegistry());
    }

    @Test
    void construct_withRegistry_isNotNoOp() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MicrometerDiagnosticSink sink = new MicrometerDiagnosticSink(registry);

        assertFalse(sink.isNoOp());
        assertSame(registry, sink.getRegistry());
    }

    @Test
    void recordCount_noOp_doesNotThrow() {
        MicrometerDiagnosticSink sink = new MicrometerDiagnosticSink(null);

        // No-Op 模式不应抛异常
        assertDoesNotThrow(() -> sink.recordCount("test.metric"));
        assertDoesNotThrow(() -> sink.recordCount("test.metric", "key", "value"));
    }

    @Test
    void recordCount_withRegistry_incrementsCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MicrometerDiagnosticSink sink = new MicrometerDiagnosticSink(registry);

        sink.recordCount("test.count", "type", "test");

        Counter counter = registry.find("test.count").tag("type", "test").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count(), 0.001);

        // 再次记录
        sink.recordCount("test.count", "type", "test");
        assertEquals(2.0, counter.count(), 0.001);
    }

    @Test
    void recordDuration_noOp_doesNotThrow() {
        MicrometerDiagnosticSink sink = new MicrometerDiagnosticSink(null);

        assertDoesNotThrow(() -> sink.recordDuration("test.duration", 1000000L));
        assertDoesNotThrow(() -> sink.recordDuration("test.duration", 1000000L, "key", "value"));
    }

    @Test
    void recordDuration_withRegistry_recordsTiming() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MicrometerDiagnosticSink sink = new MicrometerDiagnosticSink(registry);

        sink.recordDuration("test.duration", 1000000000L, "operation", "compare");

        // 验证 Timer 存在
        assertNotNull(registry.find("test.duration").tag("operation", "compare").timer());
    }

    @Test
    void recordDegradation_noOp_doesNotThrow() {
        MicrometerDiagnosticSink sink = new MicrometerDiagnosticSink(null);

        assertDoesNotThrow(() -> sink.recordDegradation("timeout"));
    }

    @Test
    void recordDegradation_withRegistry_recordsCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MicrometerDiagnosticSink sink = new MicrometerDiagnosticSink(registry);

        sink.recordDegradation("timeout");

        Counter counter = registry.find("tfi.perf.degradation").tag("reason", "timeout").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count(), 0.001);
    }

    @Test
    void recordError_noOp_doesNotThrow() {
        MicrometerDiagnosticSink sink = new MicrometerDiagnosticSink(null);

        assertDoesNotThrow(() -> sink.recordError("validation_error"));
    }

    @Test
    void recordError_withRegistry_recordsCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MicrometerDiagnosticSink sink = new MicrometerDiagnosticSink(registry);

        sink.recordError("validation_error");

        Counter counter = registry.find("tfi.compare.error").tag("type", "validation_error").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count(), 0.001);
    }
}
