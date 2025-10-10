package com.syy.taskflowinsight.tracking.compare.entity;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EntityListDiffResult 单元测试
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
class EntityListDiffResultTests {

    @Test
    void testFromCompareResultBasicGrouping() {
        // Given: 多个实体的字段变更
        List<FieldChange> changes = Arrays.asList(
                FieldChange.builder()
                        .fieldPath("entity[1001].name")
                        .fieldName("name")
                        .oldValue("Alice")
                        .newValue("Bob")
                        .changeType(ChangeType.UPDATE)
                        .build(),
                FieldChange.builder()
                        .fieldPath("entity[1001].age")
                        .fieldName("age")
                        .oldValue(25)
                        .newValue(26)
                        .changeType(ChangeType.UPDATE)
                        .build(),
                FieldChange.builder()
                        .fieldPath("entity[2002].price")
                        .fieldName("price")
                        .oldValue(100.0)
                        .newValue(120.0)
                        .changeType(ChangeType.UPDATE)
                        .build(),
                FieldChange.builder()
                        .fieldPath("entity[3003]")
                        .fieldName("entity")
                        .changeType(ChangeType.CREATE)
                        .newValue("New Entity")
                        .build()
        );

        CompareResult compareResult = CompareResult.builder()
                .changes(changes)
                .identical(false)
                .build();

        // When: 转换为 EntityListDiffResult
        EntityListDiffResult result = EntityListDiffResult.from(compareResult);

        // Then: 应该分组为3个实体
        assertNotNull(result);
        assertEquals(3, result.getGroups().size());

        // entity[1001] 应该包含2个字段变更
        Optional<EntityChangeGroup> group1001 = result.getGroupByKey("entity[1001]");
        assertTrue(group1001.isPresent());
        assertEquals(2, group1001.get().getChangeCount());
        assertEquals(EntityOperation.MODIFY, group1001.get().getOperation());

        // entity[2002] 应该包含1个字段变更
        Optional<EntityChangeGroup> group2002 = result.getGroupByKey("entity[2002]");
        assertTrue(group2002.isPresent());
        assertEquals(1, group2002.get().getChangeCount());

        // entity[3003] 应该是新增操作
        Optional<EntityChangeGroup> group3003 = result.getGroupByKey("entity[3003]");
        assertTrue(group3003.isPresent());
        assertEquals(EntityOperation.ADD, group3003.get().getOperation());
    }

    @Test
    void testOperationMapping() {
        // Given: 不同的 ChangeType
        List<FieldChange> changes = Arrays.asList(
                FieldChange.builder()
                        .fieldPath("entity[101]")
                        .changeType(ChangeType.CREATE)
                        .newValue("Created")
                        .build(),
                FieldChange.builder()
                        .fieldPath("entity[202]")
                        .changeType(ChangeType.DELETE)
                        .oldValue("Deleted")
                        .build(),
                FieldChange.builder()
                        .fieldPath("entity[303].field")
                        .changeType(ChangeType.UPDATE)
                        .oldValue("Old")
                        .newValue("New")
                        .build(),
                FieldChange.builder()
                        .fieldPath("entity[404].field")
                        .changeType(ChangeType.MOVE)
                        .oldValue("PosA")
                        .newValue("PosB")
                        .build()
        );

        CompareResult compareResult = CompareResult.builder()
                .changes(changes)
                .build();

        // When: 转换并按操作分类
        EntityListDiffResult result = EntityListDiffResult.from(compareResult);

        // Then: 操作映射应该正确
        assertEquals(1, result.getAddedEntities().size()); // CREATE -> ADD
        assertEquals(1, result.getDeletedEntities().size()); // DELETE -> DELETE
        assertEquals(2, result.getModifiedEntities().size()); // UPDATE + MOVE -> MODIFY
    }

    @Test
    void testGetAddedModifiedDeletedEntities() {
        // Given: 混合操作
        CompareResult compareResult = CompareResult.builder()
                .changes(Arrays.asList(
                        FieldChange.builder()
                                .fieldPath("entity[A]")
                                .changeType(ChangeType.CREATE)
                                .build(),
                        FieldChange.builder()
                                .fieldPath("entity[B].name")
                                .changeType(ChangeType.UPDATE)
                                .build(),
                        FieldChange.builder()
                                .fieldPath("entity[C]")
                                .changeType(ChangeType.DELETE)
                                .build()
                ))
                .build();

        // When: 转换
        EntityListDiffResult result = EntityListDiffResult.from(compareResult);

        // Then: 各类计数正确
        assertEquals(1, result.getAddedEntities().size());
        assertEquals(1, result.getModifiedEntities().size());
        assertEquals(1, result.getDeletedEntities().size());

        assertEquals("entity[A]", result.getAddedEntities().get(0).getEntityKey());
        assertEquals("entity[B]", result.getModifiedEntities().get(0).getEntityKey());
        assertEquals("entity[C]", result.getDeletedEntities().get(0).getEntityKey());
    }

    @Test
    void testStatistics() {
        // Given: 已知分布的变更
        CompareResult compareResult = CompareResult.builder()
                .changes(Arrays.asList(
                        FieldChange.builder().fieldPath("entity[1]").changeType(ChangeType.CREATE).build(),
                        FieldChange.builder().fieldPath("entity[2].a").changeType(ChangeType.UPDATE).build(),
                        FieldChange.builder().fieldPath("entity[2].b").changeType(ChangeType.UPDATE).build(),
                        FieldChange.builder().fieldPath("entity[3]").changeType(ChangeType.DELETE).build()
                ))
                .build();

        // When: 转换
        EntityListDiffResult result = EntityListDiffResult.from(compareResult);
        EntityListDiffResult.Statistics stats = result.getStatistics();

        // Then: 统计信息正确
        assertEquals(3, stats.getTotalEntities());
        assertEquals(1, stats.getAddedCount());
        assertEquals(1, stats.getModifiedCount());
        assertEquals(1, stats.getDeletedCount());
        assertEquals(4, stats.getTotalChanges()); // 总共4个字段变更
    }

    @Test
    void testEmptyResult() {
        // Given: 空变更
        CompareResult emptyCompareResult = CompareResult.builder()
                .changes(Collections.emptyList())
                .identical(true)
                .build();

        // When: 转换
        EntityListDiffResult result = EntityListDiffResult.from(emptyCompareResult);

        // Then: 应该返回空结果
        assertNotNull(result);
        assertTrue(result.getGroups().isEmpty());
        assertFalse(result.hasChanges());
        assertEquals("No changes detected", result.getSummary());
        assertEquals(0, result.getStatistics().getTotalEntities());
        assertTrue(result.getAddedEntities().isEmpty());
        assertTrue(result.getModifiedEntities().isEmpty());
        assertTrue(result.getDeletedEntities().isEmpty());
    }

    @Test
    void testGetGroupByKey() {
        // Given: 特定键的变更
        CompareResult compareResult = CompareResult.builder()
                .changes(Arrays.asList(
                        FieldChange.builder()
                                .fieldPath("entity[TARGET].field")
                                .changeType(ChangeType.UPDATE)
                                .build()
                ))
                .build();

        // When: 按键查询
        EntityListDiffResult result = EntityListDiffResult.from(compareResult);
        Optional<EntityChangeGroup> targetGroup = result.getGroupByKey("entity[TARGET]");
        Optional<EntityChangeGroup> notExist = result.getGroupByKey("entity[NOTEXIST]");

        // Then: 查询结果正确
        assertTrue(targetGroup.isPresent());
        assertEquals("entity[TARGET]", targetGroup.get().getEntityKey());
        assertEquals(1, targetGroup.get().getChangeCount());

        assertFalse(notExist.isPresent());
    }

    @Test
    void testPathParsingRobustness() {
        // Given: 各种路径格式
        List<FieldChange> changes = Arrays.asList(
                // 标准格式
                FieldChange.builder()
                        .fieldPath("entity[1001:US].name")
                        .changeType(ChangeType.UPDATE)
                        .build(),
                // 无字段后缀
                FieldChange.builder()
                        .fieldPath("entity[2002]")
                        .changeType(ChangeType.DELETE)
                        .build(),
                // 无中括号（降级处理）
                FieldChange.builder()
                        .fieldPath("product.price")
                        .changeType(ChangeType.UPDATE)
                        .build(),
                // 仅 fieldName（无 fieldPath）
                FieldChange.builder()
                        .fieldName("simpleField")
                        .changeType(ChangeType.CREATE)
                        .build(),
                // 空路径（极端情况）
                FieldChange.builder()
                        .fieldPath("")
                        .fieldName("fallbackField")
                        .changeType(ChangeType.UPDATE)
                        .build()
        );

        CompareResult compareResult = CompareResult.builder()
                .changes(changes)
                .build();

        // When: 解析路径
        EntityListDiffResult result = EntityListDiffResult.from(compareResult);

        // Then: 各路径应该被正确解析
        assertEquals(5, result.getGroups().size());

        // 标准格式：entity[1001:US]
        assertTrue(result.getGroupByKey("entity[1001:US]").isPresent());

        // 无字段后缀：entity[2002]
        assertTrue(result.getGroupByKey("entity[2002]").isPresent());

        // 降级处理：product
        assertTrue(result.getGroupByKey("product").isPresent());

        // 仅 fieldName：simpleField
        assertTrue(result.getGroupByKey("simpleField").isPresent());

        // 空路径 fallback：fallbackField
        assertTrue(result.getGroupByKey("fallbackField").isPresent());
    }

    @Test
    void testSummaryFormat() {
        // Given: 已知变更
        CompareResult compareResult = CompareResult.builder()
                .changes(Arrays.asList(
                        FieldChange.builder().fieldPath("entity[A]").changeType(ChangeType.CREATE).build(),
                        FieldChange.builder().fieldPath("entity[B]").changeType(ChangeType.UPDATE).build(),
                        FieldChange.builder().fieldPath("entity[C]").changeType(ChangeType.DELETE).build()
                ))
                .build();

        // When: 获取摘要
        EntityListDiffResult result = EntityListDiffResult.from(compareResult);
        String summary = result.getSummary();

        // Then: 摘要格式正确
        assertEquals("Total: 3 entities changed (Added: 1, Modified: 1, Deleted: 1)", summary);
    }

    @Test
    void testFromWithListsParametersPreservation() {
        // Given: 提供旧/新列表（当前未使用，但接口保留）
        List<String> oldList = Arrays.asList("old1", "old2");
        List<String> newList = Arrays.asList("new1", "new2", "new3");

        CompareResult compareResult = CompareResult.builder()
                .changes(Arrays.asList(
                        FieldChange.builder().fieldPath("entity[X]").changeType(ChangeType.UPDATE).build()
                ))
                .build();

        // When: 使用 from(result, oldList, newList)
        EntityListDiffResult result = EntityListDiffResult.from(compareResult, oldList, newList);

        // Then: 应该成功转换（即使 oldList/newList 暂未使用）
        assertNotNull(result);
        assertEquals(1, result.getGroups().size());
    }

    @Test
    void testOldNewIndexAndKeyPartsFilling() {
        // Given: 列表与包含三类操作的变更 (SSOT format: id=value)
        class E { @com.syy.taskflowinsight.annotation.Key String id; E(String id){this.id=id;} }
        List<E> oldList = Arrays.asList(new E("A"), new E("B"));
        List<E> newList = Arrays.asList(new E("A"), new E("C"));

        List<FieldChange> changes = Arrays.asList(
                FieldChange.builder().fieldPath("entity[id=A].name").changeType(ChangeType.UPDATE).build(),
                FieldChange.builder().fieldPath("entity[id=B]").changeType(ChangeType.DELETE).build(),
                FieldChange.builder().fieldPath("entity[id=C]").changeType(ChangeType.CREATE).build()
        );

        CompareResult compareResult = CompareResult.builder().changes(changes).build();

        // When
        EntityListDiffResult result = EntityListDiffResult.from(compareResult, oldList, newList);

        // Then: 索引与分片应正确
        EntityChangeGroup gA = result.getGroupByKey("entity[id=A]").orElseThrow();
        EntityChangeGroup gB = result.getGroupByKey("entity[id=B]").orElseThrow();
        EntityChangeGroup gC = result.getGroupByKey("entity[id=C]").orElseThrow();

        // keyParts (SSOT format splits by |, single key field returns list with one element)
        assertEquals(java.util.List.of("id=A"), gA.getKeyParts());
        assertEquals(java.util.List.of("id=B"), gB.getKeyParts());
        assertEquals(java.util.List.of("id=C"), gC.getKeyParts());

        // indices: A at 0 in both; B old=1, new=null; C old=null, new=1
        assertEquals(Integer.valueOf(0), gA.getOldIndex());
        assertEquals(Integer.valueOf(0), gA.getNewIndex());
        assertEquals(Integer.valueOf(1), gB.getOldIndex());
        assertNull(gB.getNewIndex());
        assertNull(gC.getOldIndex());
        assertEquals(Integer.valueOf(1), gC.getNewIndex());
    }

    @Test
    void testNullSafetyInFrom() {
        // Given: null result
        EntityListDiffResult result1 = EntityListDiffResult.from(null);

        // Then: 应该返回空结果而非抛异常
        assertNotNull(result1);
        assertTrue(result1.getGroups().isEmpty());

        // Given: null changes
        CompareResult nullChangesResult = CompareResult.builder()
                .changes(null)
                .build();
        EntityListDiffResult result2 = EntityListDiffResult.from(nullChangesResult);

        // Then: 应该返回空结果
        assertNotNull(result2);
        assertTrue(result2.getGroups().isEmpty());
    }

    @Test
    void testImmutability() {
        // Given: 包含各类操作的变更
        CompareResult compareResult = CompareResult.builder()
                .changes(Arrays.asList(
                        FieldChange.builder().fieldPath("entity[1]").changeType(ChangeType.CREATE).build(),
                        FieldChange.builder().fieldPath("entity[2]").changeType(ChangeType.UPDATE).build()
                ))
                .build();

        EntityListDiffResult result = EntityListDiffResult.from(compareResult);

        // When & Then: 获取的列表应该是不可变的
        List<EntityChangeGroup> groups = result.getGroups();
        assertThrows(UnsupportedOperationException.class, () -> {
            groups.add(EntityChangeGroup.builder()
                    .entityKey("illegal")
                    .operation(EntityOperation.ADD)
                    .build());
        });

        // 测试非空列表的不可变性
        List<EntityChangeGroup> addedEntities = result.getAddedEntities();
        assertTrue(addedEntities.size() > 0, "Should have added entities for testing immutability");
        assertThrows(UnsupportedOperationException.class, () -> {
            addedEntities.clear();
        });

        // 测试空列表的不可变性
        List<EntityChangeGroup> deletedEntities = result.getDeletedEntities();
        assertThrows(UnsupportedOperationException.class, () -> {
            deletedEntities.add(EntityChangeGroup.builder()
                    .entityKey("illegal")
                    .operation(EntityOperation.DELETE)
                    .build());
        });
    }

    @Test
    void testOriginalResultPreservation() {
        // Given: 原始 CompareResult
        CompareResult original = CompareResult.builder()
                .changes(Arrays.asList(
                        FieldChange.builder().fieldPath("entity[X]").changeType(ChangeType.UPDATE).build()
                ))
                .identical(false)
                .similarity(0.8)
                .build();

        // When: 转换
        EntityListDiffResult result = EntityListDiffResult.from(original);

        // Then: 原始结果应该被保留
        assertNotNull(result.getOriginalResult());
        assertSame(original, result.getOriginalResult());
        assertEquals(0.8, result.getOriginalResult().getSimilarity());
    }

    @Test
    void testEmptyFactory() {
        // When: 使用 empty() 工厂
        EntityListDiffResult empty = EntityListDiffResult.empty();

        // Then: 应该创建空结果
        assertNotNull(empty);
        assertTrue(empty.getGroups().isEmpty());
        assertFalse(empty.hasChanges());
        assertEquals("No changes detected", empty.getSummary());
    }

    @Test
    void testFallbackToPathParsingForUnmigratedStrategies() {
        // Given: CollectionCompareStrategy 生成的变更（无 elementEvent）
        FieldChange collectionChange = FieldChange.builder()
                .fieldName("collection")
                .changeType(ChangeType.UPDATE)
                .collectionChange(true)
                .collectionDetail(FieldChange.CollectionChangeDetail.builder()
                        .addedCount(1)
                        .removedCount(0)
                        .originalSize(2)
                        .newSize(3)
                        .build())
                .oldValue(Arrays.asList("a", "b"))
                .newValue(Arrays.asList("a", "b", "c"))
                .build();

        CompareResult result = CompareResult.builder()
                .changes(Collections.singletonList(collectionChange))
                .identical(false)
                .build();

        // When: 转换为 EntityListDiffResult
        EntityListDiffResult diffResult = EntityListDiffResult.from(result);

        // Then: 应降级到 fieldName 路径解析（修复后应能正确分组）
        assertNotNull(diffResult);
        assertEquals(1, diffResult.getGroups().size(), "Should group by fieldName for unmigrated strategies");

        // 验证能通过 "collection" 键查询到分组
        Optional<EntityChangeGroup> group = diffResult.getGroupByKey("collection");
        assertTrue(group.isPresent(), "Should fallback to fieldName='collection' as entity key");
        assertEquals(EntityOperation.MODIFY, group.get().getOperation());
        assertEquals(1, group.get().getChangeCount());
    }

    @Test
    void testFallbackToIndexWhenOnlyContainerEventIndex() {
        // Given: 容器事件只有 index，无 entityKey（P1 降级场景）
        FieldChange changeWithIndexOnly = FieldChange.builder()
                .fieldName("[0]")
                .changeType(ChangeType.CREATE)
                .elementEvent(FieldChange.ContainerElementEvent.builder()
                        .containerType(FieldChange.ContainerType.LIST)
                        .operation(FieldChange.ElementOperation.ADD)
                        .index(0)
                        // 注意：无 entityKey
                        .build())
                .newValue("New Item")
                .build();

        CompareResult result = CompareResult.builder()
                .changes(Collections.singletonList(changeWithIndexOnly))
                .build();

        // When: 转换
        EntityListDiffResult diffResult = EntityListDiffResult.from(result);

        // Then: 应生成通用键 "entity[0]"
        assertEquals(1, diffResult.getGroups().size());
        Optional<EntityChangeGroup> group = diffResult.getGroupByKey("entity[0]");
        assertTrue(group.isPresent(), "Should fallback to 'entity[<index>]' when only index provided");
        assertEquals(EntityOperation.ADD, group.get().getOperation());
    }

    @Test
    void testPriorityOfEntityKeyResolution() {
        // Given: 混合场景，验证优先级顺序
        List<FieldChange> changes = Arrays.asList(
                // 案例1: P0 优先 - 容器事件提供 entityKey
                FieldChange.builder()
                        .fieldPath("entity[key1].field")
                        .fieldName("field")
                        .changeType(ChangeType.UPDATE)
                        .elementEvent(FieldChange.ContainerElementEvent.builder()
                                .entityKey("order[O123]")  // P0 最高优先级
                                .operation(FieldChange.ElementOperation.MODIFY)
                                .build())
                        .build(),
                // 案例2: P2 降级 - 路径解析（fieldPath 有标准格式）
                FieldChange.builder()
                        .fieldPath("entity[key2].price")
                        .fieldName("price")
                        .changeType(ChangeType.UPDATE)
                        .build(),
                // 案例3: P2 降级 - 非标准 fieldName（如 "collection"）
                FieldChange.builder()
                        .fieldName("collection")
                        .changeType(ChangeType.UPDATE)
                        .build()
        );

        CompareResult result = CompareResult.builder().changes(changes).build();

        // When: 转换
        EntityListDiffResult diffResult = EntityListDiffResult.from(result);

        // Then: 验证各优先级
        assertEquals(3, diffResult.getGroups().size());

        // 验证 P0：容器事件 entityKey
        assertTrue(diffResult.getGroupByKey("order[O123]").isPresent());

        // 验证 P2：路径解析提取 key
        assertTrue(diffResult.getGroupByKey("entity[key2]").isPresent());

        // 验证 P2：非标准格式返回原值（修复后）
        assertTrue(diffResult.getGroupByKey("collection").isPresent());
    }
}
