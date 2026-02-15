package com.syy.taskflowinsight.annotation;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AnnotationPerformanceMonitor core behaviors.
 */
class AnnotationPerformanceMonitorTests {

    @Test
    void recordsAllTimings_andProducesSummary() {
        AnnotationPerformanceMonitor monitor = new AnnotationPerformanceMonitor(new SimpleMeterRegistry());

        // zero sampling path
        monitor.recordZeroSamplingPath(() -> {});

        // aspect overhead
        String value = monitor.recordAspectOverhead(() -> "ok");
        assertThat(value).isEqualTo("ok");

        // SpEL evaluation
        Integer spel = monitor.recordSpELEvaluation(() -> 42);
        assertThat(spel).isEqualTo(42);

        // sampling decision
        boolean sampled = monitor.recordSamplingDecision(() -> true);
        assertThat(sampled).isTrue();

        // method perf
        monitor.recordMethodPerformance("com.foo.Bar#baz()", 1_000, true);

        // fetch summaries and assessments
        var summary = monitor.getPerformanceSummary();
        assertThat(summary.totalInvocations).isGreaterThanOrEqualTo(1);

        var assessment = monitor.assessPerformance();
        assertThat(assessment).isNotNull();
    }
}

