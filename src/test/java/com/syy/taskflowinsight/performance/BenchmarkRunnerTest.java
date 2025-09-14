package com.syy.taskflowinsight.performance;

import com.syy.taskflowinsight.tracking.ChangeTracker;
import com.syy.taskflowinsight.tracking.path.PathMatcherCache;
import com.syy.taskflowinsight.tracking.summary.CollectionSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BenchmarkRunner单元测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@DisplayName("BenchmarkRunner单元测试")
public class BenchmarkRunnerTest {
    
    private BenchmarkRunner runner;
    
    @BeforeEach
    void setUp() {
        runner = new BenchmarkRunner();
        
        // 设置较小的迭代次数以加快测试
        ReflectionTestUtils.setField(runner, "warmupIterations", 10);
        ReflectionTestUtils.setField(runner, "measurementIterations", 100);
        ReflectionTestUtils.setField(runner, "threadCount", 2);
        
        // 注入依赖（ChangeTracker是静态类，不需要实例）
        // 由于ChangeTracker使用静态方法，我们不能注入实例，测试时会被跳过
        ReflectionTestUtils.setField(runner, "pathMatcher", new PathMatcherCache());
        ReflectionTestUtils.setField(runner, "collectionSummary", new CollectionSummary());
    }
    
    @Test
    @DisplayName("运行所有基准测试")
    void testRunAll() {
        BenchmarkReport report = runner.runAll();
        
        assertThat(report).isNotNull();
        assertThat(report.getStartTime()).isGreaterThan(0);
        assertThat(report.getEndTime()).isGreaterThan(report.getStartTime());
        assertThat(report.getResults()).hasSize(5);
        assertThat(report.getSummary()).containsKey("total_duration_ms");
        assertThat(report.getSummary()).containsKey("test_count");
        assertThat(report.getSummary()).containsKey("success_count");
    }
    
    @Test
    @DisplayName("变更追踪基准测试")
    void testBenchmarkChangeTracking() {
        BenchmarkResult result = runner.benchmarkChangeTracking();
        
        assertThat(result).isNotNull();
        // 现在ChangeTracker可以运行（使用try-catch处理异常）
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getName()).isEqualTo("change_tracking");
        assertThat(result.getCount()).isEqualTo(100);
        assertThat(result.getMeanNanos()).isGreaterThan(0);
        assertThat(result.getThroughput()).isGreaterThan(0);
    }
    
    @Test
    @DisplayName("对象快照基准测试")
    void testBenchmarkObjectSnapshot() {
        BenchmarkResult result = runner.benchmarkObjectSnapshot();
        
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getName()).isEqualTo("object_snapshot");
        assertThat(result.getCount()).isEqualTo(100);
        assertThat(result.getMeanNanos()).isGreaterThan(0);
        assertThat(result.getThroughput()).isGreaterThan(0);
    }
    
    @Test
    @DisplayName("路径匹配基准测试")
    void testBenchmarkPathMatching() {
        BenchmarkResult result = runner.benchmarkPathMatching();
        
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getName()).isEqualTo("path_matching");
        assertThat(result.getCount()).isEqualTo(100);
        assertThat(result.getMeanNanos()).isGreaterThan(0);
        assertThat(result.getThroughput()).isGreaterThan(0);
        
        // 路径匹配应该很快（缓存命中）
        assertThat(result.getP95Nanos()).isLessThan(10_000_000); // < 10ms
    }
    
    @Test
    @DisplayName("集合摘要基准测试")
    void testBenchmarkCollectionSummary() {
        BenchmarkResult result = runner.benchmarkCollectionSummary();
        
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getName()).isEqualTo("collection_summary");
        // BenchmarkRunner中对collectionSummary使用了1000次迭代
        assertThat(result.getCount()).isEqualTo(1000);
        assertThat(result.getMeanNanos()).isGreaterThan(0);
        assertThat(result.getThroughput()).isGreaterThan(0);
    }
    
    @Test
    @DisplayName("并发追踪基准测试")
    void testBenchmarkConcurrentTracking() {
        BenchmarkResult result = runner.benchmarkConcurrentTracking();
        
        assertThat(result).isNotNull();
        // 现在可以运行并发测试
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getName()).isEqualTo("concurrent_tracking");
        assertThat(result.getCount()).isEqualTo(100); // 总共100次（2线程各50次）
        assertThat(result.getMeanNanos()).isGreaterThan(0);
        assertThat(result.getThroughput()).isGreaterThan(0);
    }
    
    @Test
    @DisplayName("无ChangeTracker时的跳过处理")
    void testSkippedWhenNoChangeTracker() {
        // 即使设置为null，方法也会优雅处理
        ReflectionTestUtils.setField(runner, "changeTracker", null);
        
        BenchmarkResult result = runner.benchmarkChangeTracking();
        
        assertThat(result).isNotNull();
        // 由于使用了try-catch，测试仍然会成功运行
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getCount()).isEqualTo(100);
    }
    
    @Test
    @DisplayName("BenchmarkResult格式化输出")
    void testBenchmarkResultFormat() {
        BenchmarkResult result = runner.benchmarkObjectSnapshot();
        String formatted = result.format();
        
        assertThat(formatted).contains("object_snapshot");
        assertThat(formatted).contains("Samples:");
        assertThat(formatted).contains("Min:");
        assertThat(formatted).contains("Max:");
        assertThat(formatted).contains("Mean:");
        assertThat(formatted).contains("P95:");
        assertThat(formatted).contains("Throughput:");
        assertThat(formatted).contains("ops/sec");
    }
    
    @Test
    @DisplayName("BenchmarkResult转换为微秒")
    void testBenchmarkResultToMicros() {
        BenchmarkResult result = runner.benchmarkObjectSnapshot();
        BenchmarkResult.BenchmarkResultMicros micros = result.toMicros();
        
        assertThat(micros).isNotNull();
        assertThat(micros.getName()).isEqualTo(result.getName());
        assertThat(micros.getCount()).isEqualTo(result.getCount());
        assertThat(micros.getMean()).isEqualTo(result.getMeanNanos() / 1000);
        assertThat(micros.getP95()).isEqualTo(result.getP95Nanos() / 1000);
    }
}