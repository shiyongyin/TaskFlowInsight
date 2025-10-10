package com.syy.taskflowinsight.annotation;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for AnnotationPerformanceMonitor to improve coverage
 */
class AnnotationPerformanceMonitorComprehensiveTest {

    private AnnotationPerformanceMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new AnnotationPerformanceMonitor(new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("零采样路径记录异常处理")
    void recordZeroSamplingPath_handlesExceptions() {
        assertThatThrownBy(() -> monitor.recordZeroSamplingPath(() -> {
            throw new RuntimeException("Test exception");
        })).isInstanceOf(RuntimeException.class)
          .hasMessage("Zero sampling recording failed");
    }

    @Test
    @DisplayName("切面开销记录返回正确值")
    void recordAspectOverhead_returnsCorrectValue() {
        String result = monitor.recordAspectOverhead(() -> "test-result");
        assertThat(result).isEqualTo("test-result");
    }

    @Test
    @DisplayName("切面开销记录异常处理")
    void recordAspectOverhead_handlesExceptions() {
        assertThatThrownBy(() -> monitor.recordAspectOverhead(() -> {
            throw new Exception("Test exception");
        })).isInstanceOf(RuntimeException.class)
          .hasMessage("Aspect overhead recording failed");
    }

    @Test
    @DisplayName("SpEL评估记录返回正确值")
    void recordSpELEvaluation_returnsCorrectValue() {
        Integer result = monitor.recordSpELEvaluation(() -> 42);
        assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("SpEL评估记录异常处理")
    void recordSpELEvaluation_handlesExceptions() {
        assertThatThrownBy(() -> monitor.recordSpELEvaluation(() -> {
            throw new Exception("Test exception");
        })).isInstanceOf(RuntimeException.class)
          .hasMessage("SpEL evaluation recording failed");
    }

    @Test
    @DisplayName("采样决策记录返回正确值")
    void recordSamplingDecision_returnsCorrectValue() {
        boolean result = monitor.recordSamplingDecision(() -> true);
        assertThat(result).isTrue();

        result = monitor.recordSamplingDecision(() -> false);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("采样决策记录异常处理")
    void recordSamplingDecision_handlesExceptions() {
        assertThatThrownBy(() -> monitor.recordSamplingDecision(() -> {
            throw new RuntimeException("Test exception");
        })).isInstanceOf(RuntimeException.class)
          .hasMessage("Sampling decision recording failed");
    }

    @Test
    @DisplayName("方法性能记录正确统计")
    void recordMethodPerformance_correctStats() {
        String methodSig = "com.test.Method#doSomething()";
        
        monitor.recordMethodPerformance(methodSig, 1000, true);
        monitor.recordMethodPerformance(methodSig, 2000, false);
        monitor.recordMethodPerformance(methodSig, 1500, true);

        Map<String, AnnotationPerformanceMonitor.MethodPerformanceStats> stats = monitor.getMethodStats();
        assertThat(stats).containsKey(methodSig);
        
        AnnotationPerformanceMonitor.MethodPerformanceStats methodStats = stats.get(methodSig);
        assertThat(methodStats.getTotalCalls()).isEqualTo(3);
        assertThat(methodStats.getSampledCalls()).isEqualTo(2);
        assertThat(methodStats.getSamplingRate()).isEqualTo(2.0 / 3.0);
        assertThat(methodStats.getMinDuration()).isEqualTo(1000);
        assertThat(methodStats.getMaxDuration()).isEqualTo(2000);
        assertThat(methodStats.getAvgDuration()).isGreaterThan(0);
    }

    @Test
    @DisplayName("性能统计摘要正确计算")
    void getPerformanceSummary_correctCalculations() {
        // 执行一些操作
        monitor.recordZeroSamplingPath(() -> {});
        monitor.recordZeroSamplingPath(() -> {});
        monitor.recordAspectOverhead(() -> "result");
        monitor.recordSamplingDecision(() -> true);
        monitor.recordSamplingDecision(() -> false);

        AnnotationPerformanceMonitor.PerformanceSummary summary = monitor.getPerformanceSummary();
        
        assertThat(summary.totalInvocations).isGreaterThan(0);
        assertThat(summary.zeroPathInvocations).isEqualTo(2);
        assertThat(summary.sampledInvocations).isEqualTo(1);
        assertThat(summary.samplingRate).isGreaterThan(0);
        assertThat(summary.zeroPathRate).isGreaterThan(0);
    }

    @Test
    @DisplayName("性能评估检查逻辑")
    void assessPerformance_checksLogic() {
        // 进行一些操作
        for (int i = 0; i < 10; i++) {
            monitor.recordZeroSamplingPath(() -> {});
            monitor.recordAspectOverhead(() -> "fast");
            monitor.recordSpELEvaluation(() -> true);
            monitor.recordSamplingDecision(() -> true);
        }

        AnnotationPerformanceMonitor.PerformanceAssessment assessment = monitor.assessPerformance();
        
        // 验证评估对象被正确创建，实际值为数字（可能是NaN）
        assertThat(assessment.zeroPathActual).isInstanceOf(Double.class);
        assertThat(assessment.aspectActual).isInstanceOf(Double.class);
        assertThat(assessment.spelActual).isInstanceOf(Double.class);
        assertThat(assessment.samplingActual).isInstanceOf(Double.class);
        
        // allTargetsMet() 方法应该正确反映各个目标的状态
        boolean expected = assessment.zeroPathTarget && assessment.aspectTarget && 
                          assessment.spelTarget && assessment.samplingTarget;
        assertThat(assessment.allTargetsMet()).isEqualTo(expected);
    }

    @Test
    @DisplayName("性能评估目标未达成情况")
    void assessPerformance_targetsNotMet() {
        // 创建一个新的monitor，模拟性能不达标的情况
        AnnotationPerformanceMonitor slowMonitor = new AnnotationPerformanceMonitor(new SimpleMeterRegistry()) {
            @Override
            public PerformanceSummary getPerformanceSummary() {
                // 返回一个性能不达标的摘要
                return new PerformanceSummary(
                    100, 50, 20, 0.5, 0.2,
                    new TimerSummary("zero_sampling", 20, 500, 2000, 2000), // > 1μs
                    new TimerSummary("aspect_overhead", 100, 5000, 15000, 15000), // > 10μs
                    new TimerSummary("spel_evaluation", 50, 30000, 60000, 60000), // > 50μs
                    new TimerSummary("sampling_decision", 100, 300, 600, 600) // > 500ns
                );
            }
        };

        AnnotationPerformanceMonitor.PerformanceAssessment assessment = slowMonitor.assessPerformance();
        
        assertThat(assessment.zeroPathTarget).isFalse();
        assertThat(assessment.aspectTarget).isFalse();
        assertThat(assessment.spelTarget).isFalse();
        assertThat(assessment.samplingTarget).isFalse();
        assertThat(assessment.allTargetsMet()).isFalse();
        
        assertThat(assessment.zeroPathActual).isEqualTo(2000);
        assertThat(assessment.aspectActual).isEqualTo(15000);
        assertThat(assessment.spelActual).isEqualTo(60000);
        assertThat(assessment.samplingActual).isEqualTo(600);
    }

    @Test
    @DisplayName("清理统计数据")
    void clearStats_resetsAllCounters() {
        // 先生成一些数据
        monitor.recordZeroSamplingPath(() -> {});
        monitor.recordAspectOverhead(() -> "result");
        monitor.recordSamplingDecision(() -> true);
        monitor.recordMethodPerformance("test.method", 1000, true);

        // 清理统计
        monitor.clearStats();

        AnnotationPerformanceMonitor.PerformanceSummary summary = monitor.getPerformanceSummary();
        assertThat(summary.totalInvocations).isEqualTo(0);
        assertThat(summary.sampledInvocations).isEqualTo(0);
        assertThat(summary.zeroPathInvocations).isEqualTo(0);
        assertThat(monitor.getMethodStats()).isEmpty();
    }

    @Test
    @DisplayName("MethodPerformanceStats边界情况")
    void methodPerformanceStats_edgeCases() {
        AnnotationPerformanceMonitor.MethodPerformanceStats stats = 
            new AnnotationPerformanceMonitor.MethodPerformanceStats();

        // 初始状态
        assertThat(stats.getTotalCalls()).isEqualTo(0);
        assertThat(stats.getSampledCalls()).isEqualTo(0);
        assertThat(stats.getSamplingRate()).isEqualTo(0.0);
        assertThat(stats.getMinDuration()).isEqualTo(0); // Long.MAX_VALUE reset to 0
        assertThat(stats.getMaxDuration()).isEqualTo(0);
        assertThat(stats.getAvgDuration()).isEqualTo(0.0);

        // 只记录未采样的调用
        stats.record(1000, false);
        stats.record(2000, false);
        
        assertThat(stats.getTotalCalls()).isEqualTo(2);
        assertThat(stats.getSampledCalls()).isEqualTo(0);
        assertThat(stats.getSamplingRate()).isEqualTo(0.0);
        assertThat(stats.getMinDuration()).isEqualTo(1000);
        assertThat(stats.getMaxDuration()).isEqualTo(2000);

        // 记录采样的调用
        stats.record(1500, true);
        
        assertThat(stats.getTotalCalls()).isEqualTo(3);
        assertThat(stats.getSampledCalls()).isEqualTo(1);
        assertThat(stats.getSamplingRate()).isEqualTo(1.0 / 3.0);
    }

    @Test
    @DisplayName("TimerSummary空计时器情况")
    void timerSummary_emptyTimer() {
        // 获取一个没有记录的计时器的摘要
        AnnotationPerformanceMonitor emptyMonitor = new AnnotationPerformanceMonitor(new SimpleMeterRegistry());
        AnnotationPerformanceMonitor.PerformanceSummary summary = emptyMonitor.getPerformanceSummary();
        
        // 所有计时器摘要应该显示0计数
        assertThat(summary.zeroSamplingP99).isEqualTo(0.0);
        assertThat(summary.aspectOverheadP99).isEqualTo(0.0);
        assertThat(summary.spelEvaluationP99).isEqualTo(0.0);
        assertThat(summary.samplingDecisionP99).isEqualTo(0.0);
    }

    @Test
    @DisplayName("并发访问安全性")
    void concurrentAccess_threadSafe() throws InterruptedException {
        AtomicInteger exceptions = new AtomicInteger(0);
        Thread[] threads = new Thread[10];
        
        for (int i = 0; i < threads.length; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        final int iteration = j;
                        monitor.recordZeroSamplingPath(() -> {});
                        monitor.recordAspectOverhead(() -> "thread-" + threadId);
                        monitor.recordSamplingDecision(() -> iteration % 2 == 0);
                        monitor.recordMethodPerformance("method-" + threadId, iteration * 100, iteration % 3 == 0);
                    }
                } catch (Exception e) {
                    exceptions.incrementAndGet();
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(exceptions.get()).isEqualTo(0);
        
        AnnotationPerformanceMonitor.PerformanceSummary summary = monitor.getPerformanceSummary();
        assertThat(summary.totalInvocations).isGreaterThan(0);
        assertThat(monitor.getMethodStats()).isNotEmpty();
    }

    @Test
    @DisplayName("采样率计算边界情况")
    void samplingRateCalculation_edgeCases() {
        // 测试0总调用的情况
        AnnotationPerformanceMonitor emptyMonitor = new AnnotationPerformanceMonitor(new SimpleMeterRegistry());
        AnnotationPerformanceMonitor.PerformanceSummary summary = emptyMonitor.getPerformanceSummary();
        
        assertThat(summary.samplingRate).isEqualTo(0.0);
        assertThat(summary.zeroPathRate).isEqualTo(0.0);
    }
}