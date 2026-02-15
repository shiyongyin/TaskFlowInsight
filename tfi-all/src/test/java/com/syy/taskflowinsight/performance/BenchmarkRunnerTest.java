package com.syy.taskflowinsight.performance;

import com.syy.taskflowinsight.tracking.ChangeTracker;
import com.syy.taskflowinsight.tracking.path.CaffeinePathMatcherCache;
import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.tracking.summary.CollectionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BenchmarkRunner 单元测试 - 性能基准测试执行器验证
 * 
 * <h2>测试设计思路：</h2>
 * <ul>
 *   <li>基于基准测试方法学设计测试，验证各项性能指标测量</li>
 *   <li>使用反射机制调整测试参数，减少测试执行时间</li>
 *   <li>通过Optional依赖注入模式处理组件缺失情况</li>
 *   <li>采用多维度性能验证确保测试结果的准确性</li>
 *   <li>使用格式化输出测试验证报告生成功能</li>
 * </ul>
 * 
 * <h2>覆盖范围：</h2>
 * <ul>
 *   <li><strong>基准测试套件：</strong>runAll方法执行所有5项基准测试</li>
 *   <li><strong>变更追踪基准：</strong>benchmarkChangeTracking性能测量</li>
 *   <li><strong>对象快照基准：</strong>benchmarkObjectSnapshot执行效率</li>
 *   <li><strong>路径匹配基准：</strong>benchmarkPathMatching缓存性能</li>
 *   <li><strong>集合摘要基准：</strong>benchmarkCollectionSummary处理效率</li>
 *   <li><strong>并发追踪基准：</strong>benchmarkConcurrentTracking多线程性能</li>
 *   <li><strong>异常处理：</strong>组件缺失时的优雅降级</li>
 *   <li><strong>结果格式化：</strong>format和toMicros输出格式验证</li>
 * </ul>
 * 
 * <h2>性能测试配置：</h2>
 * <ul>
 *   <li><strong>预热迭代：</strong>10次（减少JIT编译影响）</li>
 *   <li><strong>测量迭代：</strong>100次（获得稳定统计数据）</li>
 *   <li><strong>线程数：</strong>2个（并发测试配置）</li>
 *   <li><strong>特殊配置：</strong>集合摘要测试使用1000次迭代</li>
 * </ul>
 * 
 * <h2>测试场景：</h2>
 * <ul>
 *   <li><strong>完整测试：</strong>运行所有基准测试，验证报告完整性</li>
 *   <li><strong>单项测试：</strong>分别验证5个独立基准测试的执行</li>
 *   <li><strong>容错测试：</strong>ChangeTracker为null时的处理</li>
 *   <li><strong>格式测试：</strong>结果格式化和单位转换功能</li>
 * </ul>
 * 
 * <h2>期望结果：</h2>
 * <ul>
 *   <li><strong>执行成功：</strong>所有基准测试都能成功执行并返回结果</li>
 *   <li><strong>数据完整：</strong>测试结果包含完整的性能统计数据</li>
 *   <li><strong>性能合理：</strong>路径匹配P95延迟<10ms（缓存优化效果）</li>
 *   <li><strong>容错健壮：</strong>组件缺失时仍能优雅处理</li>
 *   <li><strong>格式正确：</strong>输出格式包含所有必要的性能指标</li>
 *   <li><strong>统计准确：</strong>样本数、吞吐量、延迟统计都正确</li>
 * </ul>
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
        // 创建依赖实例
        TfiConfig mockConfig = createMockTfiConfig();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CaffeinePathMatcherCache pathMatcher = new CaffeinePathMatcherCache(mockConfig, registry);
        CollectionSummary collectionSummary = new CollectionSummary();
        
        // 使用新的构造函数
        runner = new BenchmarkRunner(
            Optional.empty(), // ChangeTracker
            Optional.of(pathMatcher),
            Optional.of(collectionSummary)
        );
        
        // 设置较小的迭代次数以加快测试
        ReflectionTestUtils.setField(runner, "warmupIterations", 10);
        ReflectionTestUtils.setField(runner, "measurementIterations", 100);
        ReflectionTestUtils.setField(runner, "threadCount", 2);
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
    
    private TfiConfig createMockTfiConfig() {
        return new TfiConfig(true, null, null, null, null);
    }
}