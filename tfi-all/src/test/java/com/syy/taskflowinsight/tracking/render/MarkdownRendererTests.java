package com.syy.taskflowinsight.tracking.render;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.entity.EntityChangeGroup;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.compare.entity.EntityOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MarkdownRenderer 单元测试
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
class MarkdownRendererTests {

    private MarkdownRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new MarkdownRenderer();
    }

    @Test
    void testRenderNoChanges() {
        // Given: 空的结果
        EntityListDiffResult emptyResult = EntityListDiffResult.builder()
                .groups(Collections.emptyList())
                .build();

        // When: 渲染
        String output = renderer.render(emptyResult, RenderStyle.standard());

        // Then: 应该显示无变更消息
        assertTrue(output.contains("✅ No Changes Detected"));
        assertTrue(output.contains("The compared lists are identical"));
    }

    @Test
    void testRenderWithStatistics() {
        // Given: 有统计信息的结果
        EntityChangeGroup group = createTestGroup("1001", EntityOperation.ADD, 3);
        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 使用 showStatistics=true 渲染
        RenderStyle style = RenderStyle.builder().showStatistics(true).build();
        String output = renderer.render(result, style);

        // Then: 应该包含统计信息段
        assertTrue(output.contains("## Statistics"));
        assertTrue(output.contains("| Operation | Count | Percentage |"));
        assertTrue(output.contains("➕ Added"));
        assertTrue(output.contains("| **Total** |"));
    }

    @Test
    void testRenderWithoutStatistics() {
        // Given: 有变更的结果
        EntityChangeGroup group = createTestGroup("1001", EntityOperation.ADD, 2);
        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 使用 showStatistics=false 渲染
        RenderStyle style = RenderStyle.builder().showStatistics(false).build();
        String output = renderer.render(result, style);

        // Then: 不应该包含统计信息段
        assertFalse(output.contains("## Statistics"));
        assertFalse(output.contains("| Operation | Count | Percentage |"));
    }

    @Test
    void testRenderGroupedByOperation() {
        // Given: 包含 ADD, MODIFY, DELETE 的结果
        EntityChangeGroup addedGroup = createTestGroup("1001", EntityOperation.ADD, 2);
        EntityChangeGroup modifiedGroup = createTestGroup("1002", EntityOperation.MODIFY, 1);
        EntityChangeGroup deletedGroup = createTestGroup("1003", EntityOperation.DELETE, 1);

        List<EntityChangeGroup> groups = Arrays.asList(addedGroup, modifiedGroup, deletedGroup);
        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(groups)
                .build();

        // When: 使用 groupByOperation=true 渲染
        RenderStyle style = RenderStyle.builder()
                .groupByOperation(true)
                .showStatistics(false)
                .build();
        String output = renderer.render(result, style);

        // Then: 应该有三个操作分节
        assertTrue(output.contains("## ➕ Added Entities"));
        assertTrue(output.contains("## ✏️ Modified Entities"));
        assertTrue(output.contains("## ❌ Deleted Entities"));

        // 每个节下应该有正确的实体
        assertTrue(output.contains("### Entity: `1001`"));
        assertTrue(output.contains("### Entity: `1002`"));
        assertTrue(output.contains("### Entity: `1003`"));

        // 验证分节顺序（ADD -> MODIFY -> DELETE）
        int addIndex = output.indexOf("## ➕ Added Entities");
        int modifyIndex = output.indexOf("## ✏️ Modified Entities");
        int deleteIndex = output.indexOf("## ❌ Deleted Entities");
        assertTrue(addIndex < modifyIndex);
        assertTrue(modifyIndex < deleteIndex);
    }

    @Test
    void testRenderNotGroupedByOperation() {
        // Given: 多个变更组
        EntityChangeGroup group1 = createTestGroup("1001", EntityOperation.ADD, 1);
        EntityChangeGroup group2 = createTestGroup("1002", EntityOperation.MODIFY, 1);

        List<EntityChangeGroup> groups = Arrays.asList(group1, group2);
        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(groups)
                .build();

        // When: 使用 groupByOperation=false 渲染
        RenderStyle style = RenderStyle.builder()
                .groupByOperation(false)
                .showStatistics(false)
                .build();
        String output = renderer.render(result, style);

        // Then: 应该只有一个 Changes 节
        assertTrue(output.contains("## Changes"));
        assertFalse(output.contains("## ➕ Added Entities"));
        assertFalse(output.contains("## ✏️ Modified Entities"));

        // 所有实体应该按顺序列出
        assertTrue(output.contains("### Entity: `1001`"));
        assertTrue(output.contains("### Entity: `1002`"));
    }

    @Test
    void testRenderTableFormat() {
        // Given: 包含字段变更的结果
        FieldChange change1 = FieldChange.builder()
                .fieldName("name")
                .fieldPath("entity[1001].name")
                .oldValue("Alice")
                .newValue("Bob")
                .changeType(ChangeType.UPDATE)
                .build();

        FieldChange change2 = FieldChange.builder()
                .fieldName("age")
                .fieldPath("entity[1001].age")
                .oldValue(25)
                .newValue(26)
                .changeType(ChangeType.UPDATE)
                .build();

        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("1001")
                .operation(EntityOperation.MODIFY)
                .changes(Arrays.asList(change1, change2))
                .build();

        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 使用 GITHUB 表格格式渲染
        RenderStyle style = RenderStyle.builder()
                .tableFormat(RenderStyle.TableFormat.GITHUB)
                .showStatistics(false)
                .detailLevel(RenderStyle.DetailLevel.NORMAL)
                .build();
        String output = renderer.render(result, style);

        // Then: 应该包含 GitHub 表格格式
        assertTrue(output.contains("| Field | Old Value | New Value | Type |"));
        assertTrue(output.contains("|-------|-----------|-----------|------|"));
        assertTrue(output.contains("| name | `Alice` | `Bob` | UPDATE |"));
        assertTrue(output.contains("| age | `25` | `26` | UPDATE |"));
    }

    @Test
    void testRenderSimpleTableFormat() {
        // Given: 包含字段变更的结果
        FieldChange change = FieldChange.builder()
                .fieldName("status")
                .fieldPath("entity[1001].status")
                .oldValue("active")
                .newValue("inactive")
                .changeType(ChangeType.UPDATE)
                .build();

        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("1001")
                .operation(EntityOperation.MODIFY)
                .changes(Collections.singletonList(change))
                .build();

        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 使用 SIMPLE 列表格式渲染
        RenderStyle style = RenderStyle.builder()
                .tableFormat(RenderStyle.TableFormat.SIMPLE)
                .showStatistics(false)
                .detailLevel(RenderStyle.DetailLevel.NORMAL)
                .build();
        String output = renderer.render(result, style);

        // Then: 应该使用简单列表格式，不是表格
        assertFalse(output.contains("| Field | Old Value | New Value | Type |"));
        assertTrue(output.contains("- **status**: `active` → `inactive` (UPDATE)"));
    }

    @Test
    void testValueTruncation() {
        // Given: 包含超长值的字段变更
        String longValue = "A".repeat(200); // 200 个字符
        FieldChange change = FieldChange.builder()
                .fieldName("description")
                .fieldPath("entity[1001].description")
                .oldValue("short")
                .newValue(longValue)
                .changeType(ChangeType.UPDATE)
                .build();

        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("1001")
                .operation(EntityOperation.MODIFY)
                .changes(Collections.singletonList(change))
                .build();

        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 使用默认 maxValueLength=100 渲染
        RenderStyle style = RenderStyle.builder()
                .maxValueLength(100)
                .tableFormat(RenderStyle.TableFormat.GITHUB)
                .showStatistics(false)
                .detailLevel(RenderStyle.DetailLevel.NORMAL)
                .build();
        String output = renderer.render(result, style);

        // Then: 长值应该被截断并添加省略号
        assertTrue(output.contains("..."));
        // 确保截断后的长度是 100 + "..." = 103 字符（加上反引号）
        // 查找包含 AAAA... 的行
        String[] lines = output.split("\n");
        boolean foundTruncated = false;
        for (String line : lines) {
            if (line.contains("description")) {
                // 新值应该被截断
                assertTrue(line.contains("`" + "A".repeat(100) + "...`"));
                foundTruncated = true;
            }
        }
        assertTrue(foundTruncated, "应该找到被截断的值");
    }

    @Test
    void testValueTruncationCustomLength() {
        // Given: 包含中等长度值的字段变更
        String mediumValue = "B".repeat(80);
        FieldChange change = FieldChange.builder()
                .fieldName("content")
                .fieldPath("entity[1001].content")
                .oldValue("short")
                .newValue(mediumValue)
                .changeType(ChangeType.UPDATE)
                .build();

        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("1001")
                .operation(EntityOperation.MODIFY)
                .changes(Collections.singletonList(change))
                .build();

        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 使用自定义 maxValueLength=50 渲染
        RenderStyle style = RenderStyle.builder()
                .maxValueLength(50)
                .tableFormat(RenderStyle.TableFormat.SIMPLE)
                .showStatistics(false)
                .detailLevel(RenderStyle.DetailLevel.NORMAL)
                .build();
        String output = renderer.render(result, style);

        // Then: 值应该在 50 字符处截断
        assertTrue(output.contains("`" + "B".repeat(50) + "...`"));
        assertFalse(output.contains("B".repeat(80))); // 不应该包含完整的 80 字符
    }

    @Test
    void testMarkdownEscaping() {
        // Given: 包含 Markdown 特殊字符的值
        FieldChange pipeChange = FieldChange.builder()
                .fieldName("expression")
                .fieldPath("entity[1001].expression")
                .oldValue("a | b")
                .newValue("c | d | e")
                .changeType(ChangeType.UPDATE)
                .build();

        FieldChange newlineChange = FieldChange.builder()
                .fieldName("multiline")
                .fieldPath("entity[1001].multiline")
                .oldValue("line1\nline2")
                .newValue("line1\nline2\nline3")
                .changeType(ChangeType.UPDATE)
                .build();

        FieldChange carriageReturnChange = FieldChange.builder()
                .fieldName("text")
                .fieldPath("entity[1001].text")
                .oldValue("text\rwith\rCR")
                .newValue("newtext\rwith\rCR")
                .changeType(ChangeType.UPDATE)
                .build();

        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("1001")
                .operation(EntityOperation.MODIFY)
                .changes(Arrays.asList(pipeChange, newlineChange, carriageReturnChange))
                .build();

        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 渲染
        RenderStyle style = RenderStyle.builder()
                .tableFormat(RenderStyle.TableFormat.GITHUB)
                .showStatistics(false)
                .detailLevel(RenderStyle.DetailLevel.NORMAL)
                .build();
        String output = renderer.render(result, style);

        // Then: 管道符应该被转义为 \|
        assertTrue(output.contains("a \\| b"));
        assertTrue(output.contains("c \\| d \\| e"));

        // 换行符应该被替换为空格
        assertTrue(output.contains("`line1 line2`"));
        assertTrue(output.contains("`line1 line2 line3`"));

        // 回车符应该被移除
        assertFalse(output.contains("\r"));
        assertTrue(output.contains("`textwithCR`"));
        assertTrue(output.contains("`newtextwithCR`"));
    }

    @Test
    void testDetailLevelSummary() {
        // Given: 有变更的结果
        EntityChangeGroup group = createTestGroup("1001", EntityOperation.ADD, 2);
        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 使用 SUMMARY 详细级别渲染
        RenderStyle style = RenderStyle.builder()
                .detailLevel(RenderStyle.DetailLevel.SUMMARY)
                .showStatistics(false)
                .build();
        String output = renderer.render(result, style);

        // Then: 不应该显示字段变更详情
        assertTrue(output.contains("### Entity: `1001`"));
        assertFalse(output.contains("| Field | Old Value | New Value | Type |"));
    }

    @Test
    void testDetailLevelDetailed() {
        // Given: 有变更的结果
        FieldChange change = FieldChange.builder()
                .fieldName("name")
                .fieldPath("entity[1001].name")
                .oldValue("Alice")
                .newValue("Bob")
                .changeType(ChangeType.UPDATE)
                .build();

        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("1001")
                .operation(EntityOperation.MODIFY)
                .entityClass(TestEntity.class)
                .changes(Collections.singletonList(change))
                .build();

        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 使用 DETAILED 详细级别渲染
        RenderStyle style = RenderStyle.builder()
                .detailLevel(RenderStyle.DetailLevel.DETAILED)
                .showStatistics(false)
                .build();
        String output = renderer.render(result, style);

        // Then: 应该显示实体类型和操作信息
        assertTrue(output.contains("**Operation**: 修改"));
        assertTrue(output.contains("**Type**: TestEntity"));
        assertTrue(output.contains("**Changes**: 1"));
    }

    @Test
    void testTimestampRendering() {
        // Given: 有变更的结果
        EntityChangeGroup group = createTestGroup("1001", EntityOperation.ADD, 1);
        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 使用 showTimestamp=true 渲染
        RenderStyle style = RenderStyle.builder()
                .showTimestamp(true)
                .showStatistics(false)
                .build();
        String output = renderer.render(result, style);

        // Then: 应该包含时间戳
        assertTrue(output.contains("_Generated at:"));
        assertTrue(output.contains("---"));
    }

    @Test
    void testNullValueFormatting() {
        // Given: 包含 null 值的字段变更
        FieldChange change = FieldChange.builder()
                .fieldName("optionalField")
                .fieldPath("entity[1001].optionalField")
                .oldValue(null)
                .newValue("value")
                .changeType(ChangeType.UPDATE)
                .build();

        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("1001")
                .operation(EntityOperation.MODIFY)
                .changes(Collections.singletonList(change))
                .build();

        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 渲染
        RenderStyle style = RenderStyle.builder()
                .tableFormat(RenderStyle.TableFormat.GITHUB)
                .showStatistics(false)
                .detailLevel(RenderStyle.DetailLevel.NORMAL)
                .build();
        String output = renderer.render(result, style);

        // Then: null 应该显示为 _null_
        assertTrue(output.contains("_null_"));
        assertTrue(output.contains("| optionalField | _null_ | `value` | UPDATE |"));
    }

    @Test
    void testSupportsMethod() {
        // When & Then: 应该支持 EntityListDiffResult 类型
        assertTrue(renderer.supports(EntityListDiffResult.class));
    }

    @Test
    void testGetDefaultStyle() {
        // When: 获取默认样式
        RenderStyle defaultStyle = renderer.getDefaultStyle();

        // Then: 应该是标准样式
        assertNotNull(defaultStyle);
        assertEquals(RenderStyle.DetailLevel.NORMAL, defaultStyle.getDetailLevel());
        assertTrue(defaultStyle.isShowStatistics());
    }

    @Test
    void testGetName() {
        // When & Then: 应该返回正确的名称
        assertEquals("markdown", renderer.getName());
    }

    // 辅助方法

    /**
     * 创建测试用的 EntityChangeGroup
     */
    private EntityChangeGroup createTestGroup(String key, EntityOperation operation, int changeCount) {
        List<FieldChange> changes = new java.util.ArrayList<>();
        for (int i = 0; i < changeCount; i++) {
            changes.add(FieldChange.builder()
                    .fieldName("field" + i)
                    .fieldPath("entity[" + key + "].field" + i)
                    .oldValue("oldValue" + i)
                    .newValue("newValue" + i)
                    .changeType(ChangeType.UPDATE)
                    .build());
        }

        return EntityChangeGroup.builder()
                .entityKey(key)
                .operation(operation)
                .changes(changes)
                .build();
    }

    /**
     * 测试用实体类
     */
    static class TestEntity {
        private String name;
        private int age;
    }

    // ========== PR-4: EntityKeyMode 渲染模式测试 ==========

    /**
     * 场景 1: 标准格式（默认）
     * <p>验证默认样式使用标准格式，表头为 Field，无 Key 列</p>
     */
    @Test
    void testStandardFormat_Default() {
        // Given: 包含 entity 路径的字段变更
        FieldChange change = FieldChange.builder()
                .fieldName("name")
                .fieldPath("entity[1001].name")
                .oldValue("Alice")
                .newValue("Bob")
                .changeType(ChangeType.UPDATE)
                .build();

        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("1001")
                .operation(EntityOperation.MODIFY)
                .changes(Collections.singletonList(change))
                .build();

        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 使用默认 standard() 样式渲染
        RenderStyle style = RenderStyle.standard();
        String output = renderer.render(result, style);

        // Then: 表头应为 Field，不包含 Key 列
        assertTrue(output.contains("| Field | Old Value | New Value | Type |"));
        assertFalse(output.contains("| Key |"));
        assertTrue(output.contains("| name |"));
    }

    /**
     * 场景 2: Key 分列格式
     * <p>验证 Key 与 Field 分为两列显示，entity[...] 和 map[...] 提取正确</p>
     */
    @Test
    void testKeySeparatedFormat() {
        // Given: 包含 entity 和普通字段的变更
        FieldChange entityChange = FieldChange.builder()
                .fieldName("price")
                .fieldPath("entity[1001].price")
                .oldValue(100.0)
                .newValue(120.0)
                .changeType(ChangeType.UPDATE)
                .build();

        FieldChange mapChange = FieldChange.builder()
                .fieldName("value")
                .fieldPath("map[key1].value")
                .oldValue("oldVal")
                .newValue("newVal")
                .changeType(ChangeType.UPDATE)
                .build();

        FieldChange normalChange = FieldChange.builder()
                .fieldName("amount")
                .fieldPath("order.amount")
                .oldValue(50)
                .newValue(60)
                .changeType(ChangeType.UPDATE)
                .build();

        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("1001")
                .operation(EntityOperation.MODIFY)
                .changes(Arrays.asList(entityChange, mapChange, normalChange))
                .build();

        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 使用 keySeparated() 样式渲染
        RenderStyle style = RenderStyle.keySeparated();
        String output = renderer.render(result, style);

        // Then: 表头应包含 Key 列
        assertTrue(output.contains("| Key | Field | Old Value | New Value | Type |"));

        // entity[1001] 应提取为 Key
        assertTrue(output.contains("| entity[1001] | price |"));

        // map[key1] 应提取为 Key
        assertTrue(output.contains("| map[key1] | value |"));

        // 普通路径 Key 为 "-"
        assertTrue(output.contains("| - | order.amount |"));
    }

    /**
     * 场景 3: Key 前缀格式
     * <p>验证 Field 列以 "[Key] Field" 形式显示</p>
     */
    @Test
    void testKeyPrefixedFormat() {
        // Given: 包含 entity 路径的字段变更
        FieldChange entityChange = FieldChange.builder()
                .fieldName("price")
                .fieldPath("entity[1001].price")
                .oldValue(100.0)
                .newValue(120.0)
                .changeType(ChangeType.UPDATE)
                .build();

        FieldChange normalChange = FieldChange.builder()
                .fieldName("total")
                .fieldPath("summary.total")
                .oldValue(500)
                .newValue(600)
                .changeType(ChangeType.UPDATE)
                .build();

        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("1001")
                .operation(EntityOperation.MODIFY)
                .changes(Arrays.asList(entityChange, normalChange))
                .build();

        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 使用 keyPrefixed() 样式渲染
        RenderStyle style = RenderStyle.keyPrefixed();
        String output = renderer.render(result, style);

        // Then: 表头应为 Field（无 Key 列）
        assertTrue(output.contains("| Field | Old Value | New Value | Type |"));
        assertFalse(output.contains("| Key |"));

        // entity 路径应显示为 "[entity[1001]] price"
        assertTrue(output.contains("| [entity[1001]] price |"));

        // 普通路径无前缀，直接显示 "summary.total"
        assertTrue(output.contains("| summary.total |"));
    }

    /**
     * 场景 4: 嵌套路径提取
     * <p>验证复杂嵌套路径只提取最外层 entity/map 作为 Key</p>
     */
    @Test
    void testNestedPathExtraction() {
        // Given: 复杂嵌套路径
        FieldChange nestedChange = FieldChange.builder()
                .fieldName("name")
                .fieldPath("entity[1].orders[2].items[3].name")
                .oldValue("ItemA")
                .newValue("ItemB")
                .changeType(ChangeType.UPDATE)
                .build();

        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("1")
                .operation(EntityOperation.MODIFY)
                .changes(Collections.singletonList(nestedChange))
                .build();

        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 使用 keySeparated() 样式渲染
        RenderStyle style = RenderStyle.keySeparated();
        String output = renderer.render(result, style);

        // Then: Key 应为 "entity[1]"，Field 应为 "orders[2].items[3].name"
        assertTrue(output.contains("| entity[1] | orders[2].items[3].name |"));
    }

    /**
     * 场景 5: 整体变化（无后续字段）
     * <p>验证当路径仅为 entity[...] 或 map[...] 时，Field 显示 "(整体变化)"</p>
     */
    @Test
    void testWholeEntityChange() {
        // Given: 路径仅为 entity[1001]，无后续字段
        FieldChange wholeChange = FieldChange.builder()
                .fieldName("entity[1001]")
                .fieldPath("entity[1001]")
                .oldValue("OldEntity")
                .newValue("NewEntity")
                .changeType(ChangeType.UPDATE)
                .build();

        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("1001")
                .operation(EntityOperation.MODIFY)
                .changes(Collections.singletonList(wholeChange))
                .build();

        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 使用 keySeparated() 样式渲染
        RenderStyle style = RenderStyle.keySeparated();
        String output = renderer.render(result, style);

        // Then: Field 应显示 "(整体变化)"
        assertTrue(output.contains("| entity[1001] | (整体变化) |"));
    }

    /**
     * 场景 6: 非 Entity 路径
     * <p>验证普通字段路径的 Key 为 "-"，Field 为完整路径</p>
     */
    @Test
    void testNonEntityPath() {
        // Given: 普通字段路径
        FieldChange normalChange = FieldChange.builder()
                .fieldName("amount")
                .fieldPath("order.amount")
                .oldValue(100)
                .newValue(200)
                .changeType(ChangeType.UPDATE)
                .build();

        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("order1")
                .operation(EntityOperation.MODIFY)
                .changes(Collections.singletonList(normalChange))
                .build();

        EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(Collections.singletonList(group))
                .build();

        // When: 使用 keySeparated() 样式渲染
        RenderStyle style = RenderStyle.keySeparated();
        String output = renderer.render(result, style);

        // Then: Key 应为 "-"，Field 应为 "order.amount"
        assertTrue(output.contains("| - | order.amount |"));
    }
}