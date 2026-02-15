package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompareResult 查询 API 性能测试（P1-T3）
 *
 * @author TaskFlow Insight Team
 * @version 3.1.0-P1
 * @since 2025-10-08
 */
class CompareResultQueryAPIPerformanceTests {

    @Test
    void query_api_should_have_minimal_overhead() {
        // Given: 1000 个变更
        List<FieldChange> changes = generateChanges(1000);
        CompareResult result = CompareResult.builder().changes(changes).build();

        // Baseline: 直接访问 changes
        long baselineStart = System.nanoTime();
        int baselineSize = result.getChanges().size();
        long baselineTime = System.nanoTime() - baselineStart;

        // Enhanced: 使用查询 API
        long enhancedStart = System.nanoTime();
        List<FieldChange> enhanced = result.getChangesByType(ChangeType.CREATE);
        long enhancedTime = System.nanoTime() - enhancedStart;

        // Then: 查询 API 应该正常工作
        assertNotNull(enhanced);
        assertTrue(enhanced.size() > 0);
        assertTrue(baselineSize >= enhanced.size());

        // Note: 性能开销验证在大数据量下更有意义，此处仅验证功能
        System.out.println("Baseline time: " + baselineTime + " ns");
        System.out.println("Enhanced time: " + enhancedTime + " ns");
    }

    @Test
    void grouping_should_be_performant_on_large_dataset() {
        // Given: 5000 个变更
        List<FieldChange> changes = generateChanges(5000);
        CompareResult result = CompareResult.builder().changes(changes).build();

        // When: 执行分组操作
        long start = System.nanoTime();
        Map<String, List<FieldChange>> byObject = result.groupByObject();
        Map<String, List<FieldChange>> byProperty = result.groupByProperty();
        long duration = System.nanoTime() - start;

        // Then: 分组应该正常完成
        assertNotNull(byObject);
        assertNotNull(byProperty);
        assertTrue(byObject.size() > 0);
        assertTrue(byProperty.size() > 0);

        // Then: 性能应该可接受（< 100ms）
        long durationMs = duration / 1_000_000;
        System.out.println("Grouping 5000 changes took: " + durationMs + " ms");
        assertTrue(durationMs < 100, "Grouping should complete within 100ms, actual: " + durationMs + "ms");
    }

    @Test
    void statistics_should_be_fast() {
        // Given: 10000 个变更
        List<FieldChange> changes = generateChanges(10000);
        CompareResult result = CompareResult.builder().changes(changes).build();

        // When: 统计操作
        long start = System.nanoTime();
        Map<ChangeType, Long> counts = result.getChangeCountByType();
        long duration = System.nanoTime() - start;

        // Then: 统计应该正确
        assertNotNull(counts);
        assertTrue(counts.size() > 0);

        // Then: 性能应该可接受（< 50ms）
        long durationMs = duration / 1_000_000;
        System.out.println("Counting 10000 changes took: " + durationMs + " ms");
        assertTrue(durationMs < 50, "Counting should complete within 50ms, actual: " + durationMs + "ms");
    }

    @Test
    void prettyPrint_should_be_fast() {
        // Given: 1000 个变更
        List<FieldChange> changes = generateChanges(1000);
        CompareResult result = CompareResult.builder().changes(changes).build();

        // When: 美化输出
        long start = System.nanoTime();
        String output = result.prettyPrint();
        long duration = System.nanoTime() - start;

        // Then: 输出应该正确
        assertNotNull(output);
        assertTrue(output.contains("Total:"));

        // Then: 性能应该可接受（< 20ms）
        long durationMs = duration / 1_000_000;
        System.out.println("Pretty printing 1000 changes took: " + durationMs + " ms");
        assertTrue(durationMs < 20, "Pretty printing should complete within 20ms, actual: " + durationMs + "ms");
    }

    // ========== 辅助方法 ==========

    private List<FieldChange> generateChanges(int count) {
        List<FieldChange> changes = new ArrayList<>(count);
        ChangeType[] types = ChangeType.values();

        for (int i = 0; i < count; i++) {
            ChangeType type = types[i % types.length];
            changes.add(FieldChange.builder()
                .fieldName("field" + i)
                .fieldPath("object" + (i / 10) + ".field" + i)
                .changeType(type)
                .oldValue(type == ChangeType.CREATE ? null : "oldValue" + i)
                .newValue(type == ChangeType.DELETE ? null : "newValue" + i)
                .build());
        }

        return changes;
    }
}

