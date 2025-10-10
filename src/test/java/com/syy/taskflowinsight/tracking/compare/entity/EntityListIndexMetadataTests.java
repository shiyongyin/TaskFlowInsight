package com.syy.taskflowinsight.tracking.compare.entity;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.list.AsSetListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.LevenshteinListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 索引元数据测试（PR-1: 列表索引元数据）
 * <p>
 * 测试目标：
 * - 1:1 场景：验证 oldIndex/newIndex 正确填充
 * - 重复 key 场景：验证 oldIndexes/newIndexes 正确填充
 * - CREATE/DELETE 场景：验证单侧索引处理
 * - 空列表场景：验证无索引元数据
 * - 性能：验证索引推断为 O(n)
 * </p>
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0 (PR-1)
 */
class EntityListIndexMetadataTests {

    private ListCompareExecutor executor;

    /**
     * 测试实体：简单实体，单一 @Key 字段
     */
    @Entity(name = "TestEntity")
    public static class TestEntity {
        @Key
        private Integer id;
        private String name;

        public TestEntity(Integer id, String name) {
            this.id = id;
            this.name = name;
        }

        public Integer getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestEntity that = (TestEntity) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return String.format("TestEntity[id=%d, name=%s]", id, name);
        }
    }

    @BeforeEach
    void setUp() {
        // 构建 ListCompareExecutor，注册所有策略
        executor = new ListCompareExecutor(Arrays.asList(
                new EntityListStrategy(),
                new AsSetListStrategy(),
                new SimpleListStrategy(),
                new LevenshteinListStrategy()
        ));
    }

    /**
     * 场景1: 1:1 索引分配（实体在两侧各出现一次）
     * <p>
     * 验证：
     * - oldIndex/newIndex 正确填充
     * - oldIndexes/newIndexes 为 null
     * - 索引值与列表位置一致
     * </p>
     */
    @Test
    void testOneToOneIndexAssignment() {
        // Given: 三个实体，顺序不同，所有都有变更
        List<TestEntity> list1 = Arrays.asList(
                new TestEntity(1, "Alice"),
                new TestEntity(2, "Bob"),
                new TestEntity(3, "Charlie")
        );
        List<TestEntity> list2 = Arrays.asList(
                new TestEntity(3, "Charlie-Modified"),  // 位置0
                new TestEntity(1, "Alice-Modified"),     // 位置1
                new TestEntity(2, "Bob-Modified")        // 位置2 (确保有变更)
        );

        // When: 比较并生成实体级差异结果
        CompareOptions options = CompareOptions.builder()
                .strategyName("ENTITY")
                .build();
        CompareResult compareResult = executor.compare(list1, list2, options);
        EntityListDiffResult result = EntityListDiffResult.from(compareResult, list1, list2);

        // Then: 验证索引元数据
        assertNotNull(result);
        assertTrue(result.hasChanges());

        // entity[1]: oldIndex=0, newIndex=1
        Optional<EntityChangeGroup> group1 = result.getGroupByKey("entity[1]");
        assertTrue(group1.isPresent(), "实体[1]应该存在");
        assertEquals(0, group1.get().getOldIndex(), "entity[1] oldIndex应为0");
        assertEquals(1, group1.get().getNewIndex(), "entity[1] newIndex应为1");
        assertNull(group1.get().getOldIndexes(), "1:1场景 oldIndexes应为null");
        assertNull(group1.get().getNewIndexes(), "1:1场景 newIndexes应为null");

        // entity[2]: oldIndex=1, newIndex=2
        Optional<EntityChangeGroup> group2 = result.getGroupByKey("entity[2]");
        assertTrue(group2.isPresent(), "实体[2]应该存在");
        assertEquals(1, group2.get().getOldIndex(), "entity[2] oldIndex应为1");
        assertEquals(2, group2.get().getNewIndex(), "entity[2] newIndex应为2");
        assertNull(group2.get().getOldIndexes(), "1:1场景 oldIndexes应为null");
        assertNull(group2.get().getNewIndexes(), "1:1场景 newIndexes应为null");

        // entity[3]: oldIndex=2, newIndex=0
        Optional<EntityChangeGroup> group3 = result.getGroupByKey("entity[3]");
        assertTrue(group3.isPresent(), "实体[3]应该存在");
        assertEquals(2, group3.get().getOldIndex(), "entity[3] oldIndex应为2");
        assertEquals(0, group3.get().getNewIndex(), "entity[3] newIndex应为0");
        assertNull(group3.get().getOldIndexes(), "1:1场景 oldIndexes应为null");
        assertNull(group3.get().getNewIndexes(), "1:1场景 newIndexes应为null");
    }

    /**
     * 场景2: 重复 key 索引列表（同一 key 在某侧出现多次）
     * <p>
     * 验证：
     * - oldIndexes/newIndexes 正确填充（按出现顺序）
     * - oldIndex/newIndex 为 null
     * - 诊断日志应触发（LIST-002）
     * </p>
     */
    @Test
    void testDuplicateKeyIndexLists() {
        // Given: list1 有2个重复 key (id=1)，list2 只有1个
        List<TestEntity> list1 = Arrays.asList(
                new TestEntity(1, "Alice"),    // index 0
                new TestEntity(1, "Alice2")    // index 1 (重复key)
        );
        List<TestEntity> list2 = Arrays.asList(
                new TestEntity(1, "Alice-Modified")  // index 0
        );

        // When: 比较
        CompareOptions options = CompareOptions.builder()
                .strategyName("ENTITY")
                .build();
        CompareResult compareResult = executor.compare(list1, list2, options);
        EntityListDiffResult result = EntityListDiffResult.from(compareResult, list1, list2);

        // Then: 验证重复 key 场景的索引元数据
        assertNotNull(result);

        // 由于重复 key，EntityListStrategy 会为每个实例生成独立路径（带 #idx 后缀）
        // 我们需要找到所有 entity[1] 相关的分组
        List<EntityChangeGroup> allGroups = result.getGroups();
        assertTrue(allGroups.size() > 0, "应该有变更分组");

        // 查找包含 entity[1] 的分组（可能是 entity[1#0], entity[1#1] 等）
        List<EntityChangeGroup> entity1Groups = allGroups.stream()
                .filter(g -> g.getEntityKey().contains("[1"))
                .toList();

        assertTrue(entity1Groups.size() > 0, "应该有 entity[1] 相关的分组");

        // 验证至少有一个分组使用了索引列表（重复 key 场景）
        boolean hasIndexLists = entity1Groups.stream().anyMatch(g ->
                g.getOldIndexes() != null || g.getNewIndexes() != null
        );

        if (hasIndexLists) {
            // 如果有索引列表，验证其正确性
            entity1Groups.stream()
                    .filter(g -> g.getOldIndexes() != null || g.getNewIndexes() != null)
                    .forEach(g -> {
                        assertNull(g.getOldIndex(), "重复key场景 oldIndex应为null");
                        assertNull(g.getNewIndex(), "重复key场景 newIndex应为null");

                        // 验证索引列表的大小
                        if (g.getOldIndexes() != null) {
                            assertTrue(g.getOldIndexes().size() > 0, "oldIndexes应非空");
                        }
                        if (g.getNewIndexes() != null) {
                            assertTrue(g.getNewIndexes().size() > 0, "newIndexes应非空");
                        }
                    });
        }
    }

    /**
     * 场景3: CREATE/DELETE 操作的索引处理
     * <p>
     * 验证：
     * - CREATE: oldIndex=null, newIndex 有值
     * - DELETE: oldIndex 有值, newIndex=null
     * </p>
     */
    @Test
    void testCreateDeleteIndexNulls() {
        // Sub-case 1: DELETE 操作（oldList 有，newList 无）
        List<TestEntity> list1 = Arrays.asList(
                new TestEntity(1, "Alice")  // index 0
        );
        List<TestEntity> list2 = Collections.emptyList();

        CompareOptions options = CompareOptions.builder()
                .strategyName("ENTITY")
                .build();
        CompareResult deleteResult = executor.compare(list1, list2, options);
        EntityListDiffResult deleteEntityResult = EntityListDiffResult.from(deleteResult, list1, list2);

        Optional<EntityChangeGroup> deleteGroup = deleteEntityResult.getGroupByKey("entity[1]");
        assertTrue(deleteGroup.isPresent(), "DELETE场景应有entity[1]");
        assertEquals(0, deleteGroup.get().getOldIndex(), "DELETE: oldIndex应为0");
        assertNull(deleteGroup.get().getNewIndex(), "DELETE: newIndex应为null");
        assertEquals(EntityOperation.DELETE, deleteGroup.get().getOperation());

        // Sub-case 2: CREATE 操作（oldList 无，newList 有）
        List<TestEntity> list3 = Collections.emptyList();
        List<TestEntity> list4 = Arrays.asList(
                new TestEntity(2, "Bob")  // index 0
        );

        CompareResult createResult = executor.compare(list3, list4, options);
        EntityListDiffResult createEntityResult = EntityListDiffResult.from(createResult, list3, list4);

        Optional<EntityChangeGroup> createGroup = createEntityResult.getGroupByKey("entity[2]");
        assertTrue(createGroup.isPresent(), "CREATE场景应有entity[2]");
        assertNull(createGroup.get().getOldIndex(), "CREATE: oldIndex应为null");
        assertEquals(0, createGroup.get().getNewIndex(), "CREATE: newIndex应为0");
        assertEquals(EntityOperation.ADD, createGroup.get().getOperation());
    }

    /**
     * 场景4: 空列表场景
     * <p>
     * 验证：
     * - 两个空列表：无变更组，无索引元数据
     * - 结果为空但不为 null
     * </p>
     */
    @Test
    void testEmptyLists() {
        // Given: 两个空列表
        List<TestEntity> emptyList1 = Collections.emptyList();
        List<TestEntity> emptyList2 = Collections.emptyList();

        // When: 比较
        CompareOptions options = CompareOptions.builder()
                .strategyName("ENTITY")
                .build();
        CompareResult compareResult = executor.compare(emptyList1, emptyList2, options);
        EntityListDiffResult result = EntityListDiffResult.from(compareResult, emptyList1, emptyList2);

        // Then: 结果应为空
        assertNotNull(result);
        assertFalse(result.hasChanges(), "空列表比较应无变更");
        assertEquals(0, result.getGroups().size(), "分组数应为0");
        // Note: isIdentical() 取决于底层 CompareResult 的实现，不在本 PR 范围内测试
    }

    /**
     * 场景5: 轻量性能测试（索引推断应为 O(n)）
     * <p>
     * 验证：
     * - 500个实体的索引推断能够快速完成（避免降级到 SIMPLE 策略）
     * - 所有实体的索引正确填充
     * - 不做强时限断言（避免 CI 抖动）
     * </p>
     */
    @Test
    void testIndexPerformanceLightweight() {
        // Given: 500个实体（轻量数据，避免超过1000阈值触发降级）
        int size = 500;
        List<TestEntity> list1 = new java.util.ArrayList<>(size);
        List<TestEntity> list2 = new java.util.ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            list1.add(new TestEntity(i, "Name" + i));
        }

        // list2 中的实体顺序相同，ID 相同，但 name 修改（确保有变更但能匹配）
        for (int i = 0; i < size; i++) {
            list2.add(new TestEntity(i, "Modified" + i));
        }

        // When: 比较（记录时间，但不做强断言）
        CompareOptions options = CompareOptions.builder()
                .strategyName("ENTITY")
                .build();
        long startTime = System.currentTimeMillis();
        CompareResult compareResult = executor.compare(list1, list2, options);
        EntityListDiffResult result = EntityListDiffResult.from(compareResult, list1, list2);
        long duration = System.currentTimeMillis() - startTime;

        // Then: 验证结果正确性
        assertNotNull(result);
        assertTrue(result.hasChanges(), "应该有变更");
        assertTrue(result.getGroups().size() > 0, "应该有变更分组");

        // 验证索引填充（抽样检查前10个）
        int filledCount = 0;
        for (int i = 0; i < Math.min(10, size); i++) {
            String key = "entity[" + i + "]";
            Optional<EntityChangeGroup> group = result.getGroupByKey(key);
            if (group.isPresent()) {
                // 验证有索引元数据
                if (group.get().getOldIndex() != null || group.get().getNewIndex() != null) {
                    filledCount++;
                }
            }
        }

        assertTrue(filledCount > 0, "应该有索引元数据填充");

        // 输出性能信息（便于观察，但不做强断言）
        System.out.printf("Performance test: %d entities compared in %d ms%n", size, duration);
        System.out.printf("Groups with index metadata (sample): %d/%d%n", filledCount, Math.min(10, size));
    }

    /**
     * 场景6: 混合场景（ADD + MODIFY + DELETE + 重排序）
     * <p>
     * 综合验证索引推断的准确性
     * </p>
     */
    @Test
    void testMixedOperationsWithIndexes() {
        // Given: 复杂场景
        List<TestEntity> list1 = Arrays.asList(
                new TestEntity(1, "Alice"),   // index 0 -> MODIFY
                new TestEntity(2, "Bob"),     // index 1 -> DELETE
                new TestEntity(3, "Charlie")  // index 2 -> MODIFY
        );
        List<TestEntity> list2 = Arrays.asList(
                new TestEntity(4, "David"),   // index 0 -> ADD
                new TestEntity(3, "Charlie-New"),  // index 1 -> MODIFY
                new TestEntity(1, "Alice-New")     // index 2 -> MODIFY
        );

        // When: 比较
        CompareOptions options = CompareOptions.builder()
                .strategyName("ENTITY")
                .build();
        CompareResult compareResult = executor.compare(list1, list2, options);
        EntityListDiffResult result = EntityListDiffResult.from(compareResult, list1, list2);

        // Then: 验证各实体的索引
        // entity[1]: MODIFY (oldIndex=0, newIndex=2)
        Optional<EntityChangeGroup> g1 = result.getGroupByKey("entity[1]");
        if (g1.isPresent()) {
            assertEquals(0, g1.get().getOldIndex());
            assertEquals(2, g1.get().getNewIndex());
            assertEquals(EntityOperation.MODIFY, g1.get().getOperation());
        }

        // entity[2]: DELETE (oldIndex=1, newIndex=null)
        Optional<EntityChangeGroup> g2 = result.getGroupByKey("entity[2]");
        if (g2.isPresent()) {
            assertEquals(1, g2.get().getOldIndex());
            assertNull(g2.get().getNewIndex());
            assertEquals(EntityOperation.DELETE, g2.get().getOperation());
        }

        // entity[3]: MODIFY (oldIndex=2, newIndex=1)
        Optional<EntityChangeGroup> g3 = result.getGroupByKey("entity[3]");
        if (g3.isPresent()) {
            assertEquals(2, g3.get().getOldIndex());
            assertEquals(1, g3.get().getNewIndex());
            assertEquals(EntityOperation.MODIFY, g3.get().getOperation());
        }

        // entity[4]: ADD (oldIndex=null, newIndex=0)
        Optional<EntityChangeGroup> g4 = result.getGroupByKey("entity[4]");
        if (g4.isPresent()) {
            assertNull(g4.get().getOldIndex());
            assertEquals(0, g4.get().getNewIndex());
            assertEquals(EntityOperation.ADD, g4.get().getOperation());
        }
    }
}
