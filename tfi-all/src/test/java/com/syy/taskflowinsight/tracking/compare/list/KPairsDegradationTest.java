package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.monitoring.DegradationDecisionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * K对数阈值降级功能测试
 * 验证K对数（n1 * n2）超过阈值时的自动降级
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0
 */
class KPairsDegradationTest {

    private ListCompareExecutor listCompareExecutor;
    private DegradationDecisionEngine degradationDecisionEngine;

    @BeforeEach
    void setUp() {
        // 创建策略列表
        List<ListCompareStrategy> strategies = List.of(
            new SimpleListStrategy(),
            new LevenshteinListStrategy(),
            new AsSetListStrategy()
        );

        this.listCompareExecutor = new ListCompareExecutor(strategies);

        // 使用反射注入 DegradationDecisionEngine（模拟Spring注入）
        try {
            var field = ListCompareExecutor.class.getDeclaredField("degradationDecisionEngine");
            field.setAccessible(true);

            // 创建真实的决策引擎（会读取配置）
            var config = new com.syy.taskflowinsight.tracking.monitoring.DegradationConfig(
                true, // enabled
                java.time.Duration.ofSeconds(5), // evaluationInterval
                java.time.Duration.ofSeconds(30), // minLevelChangeDuration
                java.time.Duration.ofSeconds(10), // metricsCacheTime
                200L, // slowOperationThresholdMs
                90.0, // criticalMemoryThreshold
                1000L, // criticalOperationTimeMs
                null, // memoryThresholds (使用默认)
                null, // performanceThresholds (使用默认)
                500, // listSizeThreshold
                5, // maxCandidates
                10000 // kPairsThreshold - 关键配置
            );

            this.degradationDecisionEngine = new DegradationDecisionEngine(config);
            field.set(listCompareExecutor, degradationDecisionEngine);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject DegradationDecisionEngine", e);
        }
    }

    @Test
    @DisplayName("K对数≤10000时不触发降级")
    void shouldNotDegradeWhenKPairsUnderThreshold() {
        // Given: 100 × 100 = 10000（刚好等于阈值，不超过）
        List<String> list1 = IntStream.range(0, 100)
            .mapToObj(i -> "item1_" + i)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        List<String> list2 = IntStream.range(0, 100)
            .mapToObj(i -> "item2_" + i)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        CompareOptions options = CompareOptions.builder()
            .strategyName("LEVENSHTEIN")
            .build();

        // When
        CompareResult result = listCompareExecutor.compare(list1, list2, options);

        // Then: 应该没有降级，LEVENSHTEIN策略正常执行
        assertThat(result).isNotNull();
        assertThat(result.isIdentical()).isFalse(); // 两个不同的列表
        assertThat(listCompareExecutor.getDegradationCount()).isEqualTo(0); // 没有发生降级
    }

    @Test
    @DisplayName("K对数>10000时触发降级到SIMPLE")
    void shouldDegradeWhenKPairsExceedThreshold() {
        // Given: 101 × 101 = 10201 > 10000（超过阈值）
        List<String> list1 = IntStream.range(0, 101)
            .mapToObj(i -> "item1_" + i)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        List<String> list2 = IntStream.range(0, 101)
            .mapToObj(i -> "item2_" + i)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        CompareOptions options = CompareOptions.builder()
            .strategyName("LEVENSHTEIN") // 请求LEVENSHTEIN，但应该被降级到SIMPLE
            .build();

        // When
        CompareResult result = listCompareExecutor.compare(list1, list2, options);

        // Then: 应该发生降级
        assertThat(result).isNotNull();
        assertThat(result.isIdentical()).isFalse();
        assertThat(listCompareExecutor.getDegradationCount()).isGreaterThan(0); // 发生了降级
    }

    @Test
    @DisplayName("极大K对数情况：1000×1000=1000000")
    void shouldDegradeOnVeryLargeKPairs() {
        // Given: 非常大的K对数
        List<String> list1 = IntStream.range(0, 200) // 200个元素
            .mapToObj(i -> "large1_" + i)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        List<String> list2 = IntStream.range(0, 200) // 200个元素，K对数 = 200×200 = 40000 >> 10000
            .mapToObj(i -> "large2_" + i)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        CompareOptions options = CompareOptions.builder()
            .strategyName("LEVENSHTEIN")
            .build();

        // When
        CompareResult result = listCompareExecutor.compare(list1, list2, options);

        // Then: 必须降级
        assertThat(result).isNotNull();
        assertThat(listCompareExecutor.getDegradationCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("边界情况：空列表不触发K对数降级")
    void shouldHandleEmptyLists() {
        // Given
        List<String> emptyList1 = new ArrayList<>();
        List<String> emptyList2 = new ArrayList<>();

        CompareOptions options = CompareOptions.builder()
            .strategyName("LEVENSHTEIN")
            .build();

        // When
        CompareResult result = listCompareExecutor.compare(emptyList1, emptyList2, options);

        // Then: K对数 = 0×0 = 0，不会降级
        assertThat(result).isNotNull();
        assertThat(result.isIdentical()).isTrue(); // 两个空列表相同
        assertThat(listCompareExecutor.getDegradationCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("验证配置的K对数阈值生效")
    void shouldRespectConfiguredKPairsThreshold() {
        // Given: 直接测试决策引擎的阈值检查
        // 测试刚好在阈值边界的情况

        // When & Then
        assertThat(degradationDecisionEngine.shouldDegradeForKPairs(10000)).isFalse(); // 等于阈值，不降级
        assertThat(degradationDecisionEngine.shouldDegradeForKPairs(10001)).isTrue();  // 超过阈值，降级
        assertThat(degradationDecisionEngine.shouldDegradeForKPairs(9999)).isFalse();  // 小于阈值，不降级
        assertThat(degradationDecisionEngine.shouldDegradeForKPairs(50000)).isTrue();  // 远超阈值，降级
    }

    @Test
    @DisplayName("K对数降级优先于大小降级")
    void kPairsDegradationShouldTakePrecedenceOverSizeDegradation() {
        // Given: 设计一个场景，大小未超过500但K对数超过10000
        // 例如：150 × 150 = 22500 > 10000，但max(150, 150) = 150 < 500
        List<String> list1 = IntStream.range(0, 150)
            .mapToObj(i -> "priority1_" + i)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        List<String> list2 = IntStream.range(0, 150)
            .mapToObj(i -> "priority2_" + i)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        CompareOptions options = CompareOptions.builder()
            .strategyName("LEVENSHTEIN")
            .build();

        long initialCount = listCompareExecutor.getDegradationCount();

        // When
        CompareResult result = listCompareExecutor.compare(list1, list2, options);

        // Then: 应该因为K对数降级（而不是大小降级）
        assertThat(result).isNotNull();
        assertThat(listCompareExecutor.getDegradationCount()).isEqualTo(initialCount + 1);

        // 验证计算：150 × 150 = 22500 > 10000
        assertThat(150 * 150).isEqualTo(22500).isGreaterThan(10000);
        // 同时验证单个大小未超过500
        assertThat(Math.max(150, 150)).isEqualTo(150).isLessThan(500);
    }
}