package com.syy.taskflowinsight.tracking.compare.entity;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EntityChangeGroup 单元测试
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
class EntityChangeGroupTests {

    @Test
    void testBuilderConstruction() {
        // Given: 使用 Builder 构造
        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("entity[1001]")
                .operation(EntityOperation.MODIFY)
                .entityClass(TestEntity.class)
                .oldEntity(new TestEntity(1001, "Old"))
                .newEntity(new TestEntity(1001, "New"))
                .addChange(FieldChange.builder()
                        .fieldPath("entity[1001].name")
                        .changeType(ChangeType.UPDATE)
                        .oldValue("Old")
                        .newValue("New")
                        .build())
                .build();

        // Then: 所有字段应该正确设置
        assertEquals("entity[1001]", group.getEntityKey());
        assertEquals(EntityOperation.MODIFY, group.getOperation());
        assertEquals(TestEntity.class, group.getEntityClass());
        assertNotNull(group.getOldEntity());
        assertNotNull(group.getNewEntity());
        assertEquals(1, group.getChangeCount());
        assertTrue(group.hasChanges());
    }

    @Test
    void testBuilderWithChangesListInput() {
        // Given: 使用列表方式设置 changes
        List<FieldChange> changes = Arrays.asList(
                FieldChange.builder()
                        .fieldPath("entity[X].field1")
                        .changeType(ChangeType.UPDATE)
                        .build(),
                FieldChange.builder()
                        .fieldPath("entity[X].field2")
                        .changeType(ChangeType.UPDATE)
                        .build()
        );

        // When: 构造
        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("entity[X]")
                .operation(EntityOperation.MODIFY)
                .changes(changes)
                .build();

        // Then: changes 应该被正确设置
        assertEquals(2, group.getChangeCount());
        assertEquals(changes.size(), group.getChanges().size());
    }

    @Test
    void testImmutabilityOfChanges() {
        // Given: 构造 group
        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("entity[TEST]")
                .operation(EntityOperation.ADD)
                .addChange(FieldChange.builder()
                        .fieldPath("test.field")
                        .changeType(ChangeType.CREATE)
                        .build())
                .build();

        // When & Then: 获取的 changes 列表应该是不可变的
        List<FieldChange> changes = group.getChanges();
        assertThrows(UnsupportedOperationException.class, () -> {
            changes.add(FieldChange.builder()
                    .fieldPath("illegal")
                    .changeType(ChangeType.UPDATE)
                    .build());
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            changes.clear();
        });
    }

    @Test
    void testGetFieldChangesByFieldName() {
        // Given: 多个字段的变更
        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("entity[1]")
                .operation(EntityOperation.MODIFY)
                .addChange(FieldChange.builder()
                        .fieldPath("entity[1].name")
                        .fieldName("name")
                        .changeType(ChangeType.UPDATE)
                        .build())
                .addChange(FieldChange.builder()
                        .fieldPath("entity[1].age")
                        .fieldName("age")
                        .changeType(ChangeType.UPDATE)
                        .build())
                .addChange(FieldChange.builder()
                        .fieldPath("entity[1].address.city")
                        .fieldName("city")
                        .changeType(ChangeType.UPDATE)
                        .build())
                .build();

        // When: 查询特定字段
        List<FieldChange> nameChanges = group.getFieldChanges("name");
        List<FieldChange> ageChanges = group.getFieldChanges("age");
        List<FieldChange> cityChanges = group.getFieldChanges("city");
        List<FieldChange> notExist = group.getFieldChanges("notexist");

        // Then: 查询结果应该精确匹配
        assertEquals(1, nameChanges.size());
        assertEquals("entity[1].name", nameChanges.get(0).getFieldPath());

        assertEquals(1, ageChanges.size());
        assertEquals("entity[1].age", ageChanges.get(0).getFieldPath());

        assertEquals(1, cityChanges.size());
        assertEquals("entity[1].address.city", cityChanges.get(0).getFieldPath());

        assertTrue(notExist.isEmpty());
    }

    @Test
    void testGetFieldChangesFallbackToFieldName() {
        // Given: 仅有 fieldName，无 fieldPath
        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("entity[X]")
                .operation(EntityOperation.MODIFY)
                .addChange(FieldChange.builder()
                        .fieldName("simpleField")
                        .changeType(ChangeType.UPDATE)
                        .build())
                .build();

        // When: 查询
        List<FieldChange> result = group.getFieldChanges("simpleField");

        // Then: 应该能匹配
        assertEquals(1, result.size());
    }

    @Test
    void testHasChangesAndGetChangeCount() {
        // Given: 空变更组
        EntityChangeGroup emptyGroup = EntityChangeGroup.builder()
                .entityKey("entity[EMPTY]")
                .operation(EntityOperation.DELETE)
                .build();

        // Then: hasChanges 应该为 false
        assertFalse(emptyGroup.hasChanges());
        assertEquals(0, emptyGroup.getChangeCount());

        // Given: 非空变更组
        EntityChangeGroup nonEmptyGroup = EntityChangeGroup.builder()
                .entityKey("entity[NONEMPTY]")
                .operation(EntityOperation.MODIFY)
                .addChange(FieldChange.builder()
                        .fieldPath("field1")
                        .changeType(ChangeType.UPDATE)
                        .build())
                .addChange(FieldChange.builder()
                        .fieldPath("field2")
                        .changeType(ChangeType.UPDATE)
                        .build())
                .build();

        // Then: hasChanges 应该为 true，count 应该为 2
        assertTrue(nonEmptyGroup.hasChanges());
        assertEquals(2, nonEmptyGroup.getChangeCount());
    }

    @Test
    void testToString() {
        // Given: 变更组
        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("entity[TEST]")
                .operation(EntityOperation.ADD)
                .addChange(FieldChange.builder()
                        .fieldPath("test.field")
                        .changeType(ChangeType.CREATE)
                        .build())
                .build();

        // When: 调用 toString
        String str = group.toString();

        // Then: 应该包含关键信息
        assertTrue(str.contains("entity[TEST]"));
        assertTrue(str.contains("ADD"));
        assertTrue(str.contains("changes=1"));
    }

    @Test
    void testRequiredFields() {
        // Given: 缺少必填字段 entityKey
        // When & Then: 应该抛出 NullPointerException
        assertThrows(NullPointerException.class, () -> {
            EntityChangeGroup.builder()
                    .operation(EntityOperation.ADD)
                    .build();
        });

        // Given: 缺少必填字段 operation
        // When & Then: 应该抛出 NullPointerException
        assertThrows(NullPointerException.class, () -> {
            EntityChangeGroup.builder()
                    .entityKey("entity[X]")
                    .build();
        });
    }

    @Test
    void testOptionalFields() {
        // Given: 仅提供必填字段
        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("entity[MIN]")
                .operation(EntityOperation.DELETE)
                .build();

        // Then: 可选字段应该为 null 或空
        assertNull(group.getEntityClass());
        assertNull(group.getOldEntity());
        assertNull(group.getNewEntity());
        assertTrue(group.getChanges().isEmpty());
    }

    @Test
    void testBuilderChangesNullHandling() {
        // Given: 传递 null 作为 changes
        EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("entity[NULL]")
                .operation(EntityOperation.MODIFY)
                .changes(null)
                .build();

        // Then: 应该初始化为空列表而非 null
        assertNotNull(group.getChanges());
        assertTrue(group.getChanges().isEmpty());
    }

    // 测试用实体类
    static class TestEntity {
        private int id;
        private String name;

        public TestEntity(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}