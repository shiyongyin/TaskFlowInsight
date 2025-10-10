package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompareResult 查询 API 集成测试（P1-T3 黄金测试）
 *
 * @author TaskFlow Insight Team
 * @version 3.1.0-P1
 * @since 2025-10-08
 */
class CompareResultQueryAPIIntegrationTests {

    @Test
    void golden_test_query_api_replaces_manual_filter() {
        // Given: 包含多种类型的变更
        CompareResult result = CompareResult.builder()
            .changes(List.of(
                createChange("field1", ChangeType.CREATE),
                createChange("field2", ChangeType.CREATE),
                createChange("field3", ChangeType.UPDATE),
                createChange("field4", ChangeType.DELETE)
            ))
            .build();

        // ❌ 旧代码（样板）
        long oldCreateCount = result.getChanges().stream()
            .filter(c -> c.getChangeType() == ChangeType.CREATE)
            .count();

        // ✅ 新代码（便捷API）
        long newCreateCount = result.getChangesByType(ChangeType.CREATE).size();

        // Then: 功能等价
        assertEquals(oldCreateCount, newCreateCount);
        assertEquals(2, newCreateCount);
    }

    @Test
    void golden_test_grouping_api_for_nested_objects() {
        // Given: 嵌套对象变更场景
        CompareResult result = CompareResult.builder()
            .changes(List.of(
                FieldChange.builder()
                    .fieldName("status")
                    .fieldPath("order.status")
                    .changeType(ChangeType.UPDATE)
                    .oldValue("PENDING")
                    .newValue("CONFIRMED")
                    .build(),
                FieldChange.builder()
                    .fieldName("amount")
                    .fieldPath("order.amount")
                    .changeType(ChangeType.UPDATE)
                    .oldValue(100.0)
                    .newValue(200.0)
                    .build(),
                FieldChange.builder()
                    .fieldName("name")
                    .fieldPath("customer.name")
                    .changeType(ChangeType.UPDATE)
                    .oldValue("Alice")
                    .newValue("Bob")
                    .build()
            ))
            .build();

        // When: 按对象分组
        Map<String, List<FieldChange>> byObject = result.groupByObject();

        // Then: 分组正确
        assertEquals(2, byObject.size());
        assertEquals(2, byObject.get("order").size());
        assertEquals(1, byObject.get("customer").size());

        // When: 按属性分组
        Map<String, List<FieldChange>> byProperty = result.groupByProperty();

        // Then: 每个属性一组
        assertEquals(3, byProperty.size());
        assertEquals(1, byProperty.get("status").size());
        assertEquals(1, byProperty.get("amount").size());
        assertEquals(1, byProperty.get("name").size());
    }

    @Test
    void golden_test_pretty_print_output_format() {
        // Given: 复杂变更场景
        CompareResult result = CompareResult.builder()
            .changes(List.of(
                createChange("field1", ChangeType.CREATE),
                createChange("field2", ChangeType.CREATE),
                createChange("field3", ChangeType.CREATE),
                createChange("field4", ChangeType.CREATE),
                createChange("field5", ChangeType.CREATE),
                createChange("field6", ChangeType.UPDATE),
                createChange("field7", ChangeType.UPDATE),
                createChange("field8", ChangeType.UPDATE),
                createChange("field9", ChangeType.UPDATE),
                createChange("field10", ChangeType.UPDATE),
                createChange("field11", ChangeType.UPDATE),
                createChange("field12", ChangeType.UPDATE),
                createChange("field13", ChangeType.UPDATE),
                createChange("field14", ChangeType.DELETE),
                createChange("field15", ChangeType.DELETE)
            ))
            .build();

        // When: 美化输出
        String output = result.prettyPrint();

        // Then: 验证输出格式
        assertTrue(output.contains("=== Change Summary ==="));
        assertTrue(output.contains("Total: 15 changes"));
        assertTrue(output.contains("CREATE: 5"));
        assertTrue(output.contains("UPDATE: 8"));
        assertTrue(output.contains("DELETE: 2"));

        // Then: 不包含空的引用变更和容器操作（P1-T1/T2 未实现）
        assertFalse(output.contains("Reference Changes:"));
        assertFalse(output.contains("Container Operations:"));
    }

    @Test
    void golden_test_statistics_api_for_dashboard() {
        // Given: 模拟仪表盘统计场景
        CompareResult result = CompareResult.builder()
            .changes(List.of(
                createChange("new1", ChangeType.CREATE),
                createChange("new2", ChangeType.CREATE),
                createChange("new3", ChangeType.CREATE),
                createChange("upd1", ChangeType.UPDATE),
                createChange("upd2", ChangeType.UPDATE),
                createChange("upd3", ChangeType.UPDATE),
                createChange("upd4", ChangeType.UPDATE),
                createChange("upd5", ChangeType.UPDATE),
                createChange("del1", ChangeType.DELETE)
            ))
            .build();

        // When: 获取统计数据
        Map<ChangeType, Long> stats = result.getChangeCountByType();

        // Then: 统计正确
        assertEquals(3L, stats.get(ChangeType.CREATE));
        assertEquals(5L, stats.get(ChangeType.UPDATE));
        assertEquals(1L, stats.get(ChangeType.DELETE));

        // When: 组合查询（新增 + 删除）
        List<FieldChange> mutations = result.getChangesByType(
            ChangeType.CREATE, ChangeType.DELETE
        );

        // Then: 组合查询正确
        assertEquals(4, mutations.size());
    }

    // ========== 辅助方法 ==========

    private FieldChange createChange(String fieldName, ChangeType type) {
        return FieldChange.builder()
            .fieldName(fieldName)
            .changeType(type)
            .oldValue(type == ChangeType.CREATE ? null : "oldValue")
            .newValue(type == ChangeType.DELETE ? null : "newValue")
            .build();
    }
}

