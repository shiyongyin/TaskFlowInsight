package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompareResult 查询 API 单元测试（P1-T3）
 *
 * @author TaskFlow Insight Team
 * @version 3.1.0-P1
 * @since 2025-10-08
 */
class CompareResultQueryAPITests {

    // ========== 分类1: 按类型查询（6个用例） ==========

    @Test
    void getChangesByType_single_type_should_filter_correctly() {
        // Given: 包含 CREATE/UPDATE/DELETE 的变更
        CompareResult result = createSampleResult(
            createChange("field1", ChangeType.CREATE),
            createChange("field2", ChangeType.UPDATE),
            createChange("field3", ChangeType.DELETE)
        );

        // When: 查询 CREATE
        List<FieldChange> creates = result.getChangesByType(ChangeType.CREATE);

        // Then: 仅返回 CREATE
        assertEquals(1, creates.size());
        assertEquals("field1", creates.get(0).getFieldName());
    }

    @Test
    void getChangesByType_multiple_types_should_combine_results() {
        // Given: 包含多种类型
        CompareResult result = createSampleResult(
            createChange("field1", ChangeType.CREATE),
            createChange("field2", ChangeType.UPDATE),
            createChange("field3", ChangeType.DELETE)
        );

        // When: 查询 CREATE + DELETE
        List<FieldChange> mutations = result.getChangesByType(
            ChangeType.CREATE, ChangeType.DELETE
        );

        // Then: 返回 2 个
        assertEquals(2, mutations.size());
    }

    @Test
    void getChangesByType_no_params_should_return_all() {
        // Given: 3个变更
        CompareResult result = createSampleResult(
            createChange("field1", ChangeType.CREATE),
            createChange("field2", ChangeType.UPDATE),
            createChange("field3", ChangeType.DELETE)
        );

        // When: 无参数查询
        List<FieldChange> all = result.getChangesByType();

        // Then: 返回全部
        assertEquals(3, all.size());
    }

    @Test
    void getChangesByType_should_return_immutable_list() {
        // Given: 包含变更
        CompareResult result = createSampleResult(
            createChange("field1", ChangeType.CREATE)
        );

        // When: 获取结果
        List<FieldChange> creates = result.getChangesByType(ChangeType.CREATE);

        // Then: 不可变列表
        assertThrows(UnsupportedOperationException.class, () ->
            creates.add(createChange("new", ChangeType.CREATE))
        );
    }

    @Test
    void getChangesByType_should_handle_null_changes() {
        // Given: changes = null
        CompareResult result = CompareResult.builder()
            .changes(null)
            .build();

        // When: 查询
        List<FieldChange> creates = result.getChangesByType(ChangeType.CREATE);

        // Then: 返回空列表
        assertTrue(creates.isEmpty());
    }

    @Test
    void getChangesByType_should_handle_empty_changes() {
        // Given: changes = []
        CompareResult result = CompareResult.builder()
            .changes(Collections.emptyList())
            .build();

        // Then: 返回空列表
        assertTrue(result.getChangesByType(ChangeType.CREATE).isEmpty());
    }

    // ========== 分类2: 语义化查询（4个用例） ==========

    @Test
    void getReferenceChanges_should_return_empty_when_not_implemented() {
        // Given: 包含普通变更（P1-T2 未实现）
        CompareResult result = createSampleResult(
            createChange("customer", ChangeType.UPDATE),
            createChange("status", ChangeType.CREATE)
        );

        // When: 查询引用变更
        List<FieldChange> refChanges = result.getReferenceChanges();

        // Then: 返回空列表（占位实现）
        assertTrue(refChanges.isEmpty());
    }

    @Test
    void getContainerChanges_should_return_empty_when_not_implemented() {
        // Given: 包含普通变更（P1-T1 未实现）
        CompareResult result = createSampleResult(
            createChange("items", ChangeType.UPDATE),
            createChange("status", ChangeType.CREATE)
        );

        // When: 查询容器变更
        List<FieldChange> containerChanges = result.getContainerChanges();

        // Then: 返回空列表（占位实现）
        assertTrue(containerChanges.isEmpty());
    }

    @Test
    void getReferenceChanges_should_handle_empty() {
        // Given: 无变更
        CompareResult result = createSampleResult();

        // Then: 返回空列表
        assertTrue(result.getReferenceChanges().isEmpty());
    }

    @Test
    void getContainerChanges_should_handle_empty() {
        // Given: 无变更
        CompareResult result = createSampleResult();

        // Then: 返回空列表
        assertTrue(result.getContainerChanges().isEmpty());
    }

    // ========== 分类3: 分组查询（6个用例） ==========

    @Test
    void groupByObject_should_group_by_path_prefix() {
        // Given: 嵌套对象变更
        FieldChange c1 = FieldChange.builder()
            .fieldName("status")
            .fieldPath("order.status")
            .changeType(ChangeType.UPDATE)
            .build();
        FieldChange c2 = FieldChange.builder()
            .fieldName("amount")
            .fieldPath("order.amount")
            .changeType(ChangeType.UPDATE)
            .build();
        FieldChange c3 = FieldChange.builder()
            .fieldName("name")
            .fieldPath("customer.name")
            .changeType(ChangeType.UPDATE)
            .build();

        CompareResult result = createSampleResult(c1, c2, c3);

        // When: 按对象分组
        Map<String, List<FieldChange>> byObject = result.groupByObject();

        // Then: 分为 "order" 和 "customer" 两组
        assertEquals(2, byObject.size());
        assertEquals(2, byObject.get("order").size());
        assertEquals(1, byObject.get("customer").size());
    }

    @Test
    void groupByObject_should_handle_top_level_fields() {
        // Given: 顶级字段（无路径）
        FieldChange c1 = FieldChange.builder()
            .fieldName("status")
            .changeType(ChangeType.UPDATE)
            .build();

        CompareResult result = createSampleResult(c1);

        // When: 分组
        Map<String, List<FieldChange>> byObject = result.groupByObject();

        // Then: 使用字段名作为分组键
        assertTrue(byObject.containsKey("status"));
        assertEquals(1, byObject.get("status").size());
    }

    @Test
    void groupByProperty_should_group_by_field_name() {
        // Given: 多个对象的同名字段
        FieldChange c1 = FieldChange.builder()
            .fieldName("price")
            .fieldPath("items[0].price")
            .changeType(ChangeType.UPDATE)
            .build();
        FieldChange c2 = FieldChange.builder()
            .fieldName("price")
            .fieldPath("items[1].price")
            .changeType(ChangeType.UPDATE)
            .build();
        FieldChange c3 = FieldChange.builder()
            .fieldName("status")
            .changeType(ChangeType.UPDATE)
            .build();

        CompareResult result = createSampleResult(c1, c2, c3);

        // When: 按属性分组
        Map<String, List<FieldChange>> byProperty = result.groupByProperty();

        // Then: "price" 组包含 2 个变更
        assertEquals(2, byProperty.get("price").size());
        assertEquals(1, byProperty.get("status").size());
    }

    @Test
    void groupByObject_should_handle_array_nested_paths() {
        // Given: 复杂路径 items[0].child.field
        FieldChange c1 = FieldChange.builder()
            .fieldName("field")
            .fieldPath("items[0].child.field")
            .changeType(ChangeType.UPDATE)
            .build();
        CompareResult result = createSampleResult(c1);

        // When
        Map<String, List<FieldChange>> byObject = result.groupByObject();

        // Then: 首段应为 items[0]
        assertTrue(byObject.containsKey("items[0]"));
        assertEquals(1, byObject.get("items[0]").size());
    }

    @Test
    void groupByObject_should_handle_map_key_paths() {
        // Given: 复杂路径 ordersMap[order-1].price
        FieldChange c1 = FieldChange.builder()
            .fieldName("price")
            .fieldPath("ordersMap[order-1].price")
            .changeType(ChangeType.UPDATE)
            .build();
        CompareResult result = createSampleResult(c1);

        // When
        Map<String, List<FieldChange>> byObject = result.groupByObject();

        // Then: 首段应为 ordersMap[order-1]
        assertTrue(byObject.containsKey("ordersMap[order-1]"));
        assertEquals(1, byObject.get("ordersMap[order-1]").size());
    }

    @Test
    void groupByObject_should_handle_entity_like_paths() {
        // Given: 复杂路径 orders[ID-123].lines[2].qty
        FieldChange c1 = FieldChange.builder()
            .fieldName("qty")
            .fieldPath("orders[ID-123].lines[2].qty")
            .changeType(ChangeType.UPDATE)
            .build();
        CompareResult result = createSampleResult(c1);

        // When
        Map<String, List<FieldChange>> byObject = result.groupByObject();

        // Then: 首段应为 orders[ID-123]
        assertTrue(byObject.containsKey("orders[ID-123]"));
        assertEquals(1, byObject.get("orders[ID-123]").size());
    }

    @Test
    void groupByObject_should_handle_bracket_only_paths() {
        // Given: 无点号，仅有索引 items[10]
        FieldChange c1 = FieldChange.builder()
            .fieldName("items")
            .fieldPath("items[10]")
            .changeType(ChangeType.UPDATE)
            .build();
        CompareResult result = createSampleResult(c1);

        // When
        Map<String, List<FieldChange>> byObject = result.groupByObject();

        // Then: 分组键即为完整首段
        assertTrue(byObject.containsKey("items[10]"));
        assertEquals(1, byObject.get("items[10]").size());
    }

    @Test
    void groupByContainerOperation_should_return_empty_when_not_implemented() {
        // Given: 包含变更（P1-T1 未实现）
        CompareResult result = createSampleResult(
            createChange("items", ChangeType.UPDATE)
        );

        // When: 按容器操作分组
        Map<FieldChange.ElementOperation, List<FieldChange>> byOp = result.groupByContainerOperation();

        // Then: 返回空 Map（占位实现）
        assertTrue(byOp.isEmpty());
    }

    @Test
    void groupByContainerOperation_should_handle_empty() {
        // Given: 无变更
        CompareResult result = createSampleResult();

        // Then: 返回空 Map
        assertTrue(result.groupByContainerOperation().isEmpty());
    }

    @Test
    void groupBy_should_return_immutable_map() {
        // Given: 包含变更
        CompareResult result = createSampleResult(
            createChange("field1", ChangeType.CREATE)
        );

        // When: 获取分组结果
        Map<String, List<FieldChange>> byObject = result.groupByObject();

        // Then: 不可修改
        assertThrows(UnsupportedOperationException.class, () ->
            byObject.put("new", Collections.emptyList())
        );
    }

    // ========== 分类4: 统计查询（3个用例） ==========

    @Test
    void getChangeCountByType_should_return_correct_counts() {
        // Given: 包含 5 CREATE, 3 UPDATE, 2 DELETE
        List<FieldChange> changes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            changes.add(createChange("create" + i, ChangeType.CREATE));
        }
        for (int i = 0; i < 3; i++) {
            changes.add(createChange("update" + i, ChangeType.UPDATE));
        }
        for (int i = 0; i < 2; i++) {
            changes.add(createChange("delete" + i, ChangeType.DELETE));
        }

        CompareResult result = CompareResult.builder().changes(changes).build();

        // When: 统计
        Map<ChangeType, Long> counts = result.getChangeCountByType();

        // Then: 数量正确
        assertEquals(5L, counts.get(ChangeType.CREATE));
        assertEquals(3L, counts.get(ChangeType.UPDATE));
        assertEquals(2L, counts.get(ChangeType.DELETE));
    }

    @Test
    void getChangeCountByType_should_handle_empty() {
        // Given: 无变更
        CompareResult result = CompareResult.builder()
            .changes(Collections.emptyList())
            .build();

        // Then: 返回空 Map
        assertTrue(result.getChangeCountByType().isEmpty());
    }

    @Test
    void getChangeCountByType_should_handle_null() {
        // Given: changes = null
        CompareResult result = CompareResult.builder().changes(null).build();

        // Then: 返回空 Map
        assertTrue(result.getChangeCountByType().isEmpty());
    }

    // ========== 分类5: 美化输出（6个用例） ==========

    @Test
    void prettyPrint_should_include_type_summary() {
        // Given: 包含多种类型变更
        List<FieldChange> changes = Arrays.asList(
            createChange("f1", ChangeType.CREATE),
            createChange("f2", ChangeType.UPDATE),
            createChange("f3", ChangeType.DELETE)
        );
        CompareResult result = CompareResult.builder().changes(changes).build();

        // When: 美化输出
        String output = result.prettyPrint();

        // Then: 包含类型统计
        assertTrue(output.contains("Total: 3 changes"));
        assertTrue(output.contains("CREATE: 1"));
        assertTrue(output.contains("UPDATE: 1"));
        assertTrue(output.contains("DELETE: 1"));
    }

    @Test
    void prettyPrint_should_skip_reference_changes_when_empty() {
        // Given: 无引用变更（P1-T2 未实现）
        CompareResult result = createSampleResult(
            createChange("status", ChangeType.UPDATE)
        );

        // When: 输出
        String output = result.prettyPrint();

        // Then: 不包含引用变更统计
        assertFalse(output.contains("Reference Changes:"));
    }

    @Test
    void prettyPrint_should_skip_container_operations_when_empty() {
        // Given: 无容器操作（P1-T1 未实现）
        CompareResult result = createSampleResult(
            createChange("status", ChangeType.UPDATE)
        );

        // When: 输出
        String output = result.prettyPrint();

        // Then: 不包含容器操作统计
        assertFalse(output.contains("Container Operations:"));
    }

    @Test
    void prettyPrint_should_handle_empty_changes() {
        // Given: 无变更
        CompareResult result = CompareResult.builder()
            .changes(Collections.emptyList())
            .build();

        // When: 输出
        String output = result.prettyPrint();

        // Then: 显示 "No changes"
        assertTrue(output.contains("No changes detected"));
    }

    @Test
    void prettyPrint_should_handle_null_changes() {
        // Given: changes = null
        CompareResult result = CompareResult.builder().changes(null).build();

        // Then: 显示 "No changes"
        assertTrue(result.prettyPrint().contains("No changes detected"));
    }

    @Test
    void prettyPrint_format_should_be_readable() {
        // Given: 包含类型统计
        CompareResult result = createSampleResult(
            createChange("f1", ChangeType.CREATE),
            createChange("f2", ChangeType.UPDATE)
        );

        // When: 输出
        String output = result.prettyPrint();

        // Then: 格式验证
        assertTrue(output.startsWith("=== Change Summary ==="));
        assertTrue(output.contains("\n\n"));  // 段落分隔
        assertTrue(output.contains("  "));    // 缩进
    }

    // ========== 辅助方法 ==========

    private CompareResult createSampleResult(FieldChange... changes) {
        return CompareResult.builder()
            .changes(Arrays.asList(changes))
            .identical(false)
            .build();
    }

    private FieldChange createChange(String fieldName, ChangeType type) {
        return FieldChange.builder()
            .fieldName(fieldName)
            .changeType(type)
            .oldValue(type == ChangeType.CREATE ? null : "oldValue")
            .newValue(type == ChangeType.DELETE ? null : "newValue")
            .build();
    }
}
