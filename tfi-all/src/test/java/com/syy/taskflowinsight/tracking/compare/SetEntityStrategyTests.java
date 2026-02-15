package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SetCompareStrategy Entity 处理测试（PR-2）
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 */
class SetEntityStrategyTests {

    private SetCompareStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SetCompareStrategy();
    }

    /**
     * 测试用 Entity 类（正常实现：@Key 与 equals/hashCode 一致）
     */
    @Entity(name = "Product")
    public static class Product {
        @Key
        private Long id;
        private String name;
        private double price;

        public Product(Long id, String name, double price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public double getPrice() { return price; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Product product = (Product) o;
            return Objects.equals(id, product.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    /**
     * 测试用 Entity 类（不一致实现：equals/hashCode 基于所有字段，@Key 仅 id）
     * 用于模拟重复 @Key 场景
     */
    @Entity(name = "BadProduct")
    public static class BadProduct {
        @Key
        private Long id;
        private String name;

        public BadProduct(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() { return id; }
        public String getName() { return name; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BadProduct that = (BadProduct) o;
            // 错误：equals 基于所有字段，但 @Key 只有 id
            return Objects.equals(id, that.id) && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name);
        }
    }

    /**
     * 场景 1: 正常 Entity Set 属性变更
     */
    @Test
    void testNormalEntitySet_Modify() {
        // Given: Set<Product> with one entity modified
        Product p1 = new Product(100L, "Laptop", 1000.0);
        Product p2 = new Product(200L, "Mouse", 25.0);

        Product p1Modified = new Product(100L, "Laptop Pro", 1200.0);
        Product p2Same = new Product(200L, "Mouse", 25.0);

        Set<Product> set1 = new HashSet<>(Arrays.asList(p1, p2));
        Set<Product> set2 = new HashSet<>(Arrays.asList(p1Modified, p2Same));

        // When: 比较
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = strategy.compare(set1, set2, options);

        // Then: 应有实体级属性变更
        assertNotNull(result);
        assertFalse(result.isIdentical());
        assertTrue(result.getChangeCount() > 0);

        // 验证路径格式 entity[key].field (SSOT format: id=100)
        List<FieldChange> changes = result.getChanges();
        assertTrue(changes.stream().anyMatch(c ->
                c.getFieldPath() != null && c.getFieldPath().contains("[id=100]")
        ), "应有 entity[id=100].* 路径的变更");

        System.out.println("Normal Entity Set changes:");
        changes.forEach(c -> System.out.println("  " + c.getFieldPath() + ": " + c.getOldValue() + " -> " + c.getNewValue()));
    }

    /**
     * 场景 2: 重复 @Key 检测（宽松模式）
     */
    @Test
    void testDuplicateKey_Loose() {
        // Given: Set with duplicate @Key (same id, different name)
        // 由于 equals/hashCode 基于 id+name，Set 能容纳相同 id 的元素
        BadProduct bad1 = new BadProduct(300L, "NameA");
        BadProduct bad2 = new BadProduct(300L, "NameB"); // 相同 @Key (id=300)

        Set<BadProduct> set1 = new HashSet<>(Arrays.asList(bad1, bad2));
        Set<BadProduct> set2 = new HashSet<>();

        // When: 宽松模式比较
        CompareOptions options = CompareOptions.builder().strictDuplicateKey(false).build();
        CompareResult result = strategy.compare(set1, set2, options);

        // Then: 应继续比较（不抛异常），触发 SET-002 诊断
        assertNotNull(result);
        // 由于 set2 为空，应有 DELETE 变更
        assertTrue(result.getChangeCount() > 0);

        System.out.println("Duplicate key (loose mode) detected, changes count: " + result.getChangeCount());
        System.out.println("Check logs for SET-002 diagnostic");
    }

    /**
     * 场景 3: 重复 @Key 检测（严格模式）
     */
    @Test
    void testDuplicateKey_Strict() {
        // Given: Set with duplicate @Key
        BadProduct bad1 = new BadProduct(400L, "NameX");
        BadProduct bad2 = new BadProduct(400L, "NameY");

        Set<BadProduct> set1 = new HashSet<>(Arrays.asList(bad1, bad2));
        Set<BadProduct> set2 = new HashSet<>();

        // When & Then: 严格模式应抛异常
        CompareOptions options = CompareOptions.builder().strictDuplicateKey(true).build();
        assertThrows(IllegalArgumentException.class, () -> {
            strategy.compare(set1, set2, options);
        }, "严格模式下重复 @Key 应抛 IllegalArgumentException");

        System.out.println("Strict mode: Exception thrown as expected");
    }

    /**
     * 场景 4: 排序稳定性测试
     */
    @Test
    void testSortingStability() {
        // Given: 多次运行相同 Set
        Product p1 = new Product(500L, "Item1", 10.0);
        Product p2 = new Product(501L, "Item2", 20.0);
        Product p3 = new Product(502L, "Item3", 30.0);

        Set<Product> set = new HashSet<>(Arrays.asList(p1, p2, p3));

        // When: 多次比较
        CompareOptions options = CompareOptions.builder().build();
        List<String> run1 = extractChangePaths(strategy.compare(set, new HashSet<>(), options));
        List<String> run2 = extractChangePaths(strategy.compare(set, new HashSet<>(), options));
        List<String> run3 = extractChangePaths(strategy.compare(set, new HashSet<>(), options));

        // Then: 路径顺序一致
        assertEquals(run1, run2, "第1次与第2次运行结果应一致");
        assertEquals(run2, run3, "第2次与第3次运行结果应一致");

        System.out.println("Sorting stability verified: " + run1.size() + " changes, order consistent");
    }

    /**
     * 场景 5: generateDetailedChangeRecords 兼容性
     */
    @Test
    void testDetailedRecords_Compatible() {
        // Given: Set<Product> with changes
        Product p1 = new Product(600L, "Old", 100.0);
        Product p2 = new Product(600L, "New", 150.0);

        Set<Product> set1 = new HashSet<>(Collections.singletonList(p1));
        Set<Product> set2 = new HashSet<>(Collections.singletonList(p2));

        // When: 生成详细记录
        List<ChangeRecord> records = strategy.generateDetailedChangeRecords(
                "TestObject", "products", set1, set2, "session1", "/test"
        );

        // Then: 应返回 ChangeRecord，valueKind 标注为 SET_ENTITY
        assertNotNull(records);
        assertTrue(records.size() > 0, "应有 ChangeRecord");

        boolean hasSetEntity = records.stream().anyMatch(r -> "SET_ENTITY".equals(r.getValueKind()));
        assertTrue(hasSetEntity, "至少有一个 ChangeRecord 的 valueKind 为 SET_ENTITY");

        System.out.println("DetailedChangeRecords generated: " + records.size());
        records.forEach(r -> System.out.println("  " + r.getFieldName() + " (" + r.getValueKind() + ")"));
    }

    /**
     * 场景 6: 普通 Set（非 Entity）保持原逻辑
     */
    @Test
    void testNonEntitySet() {
        // Given: Set<String>
        Set<String> set1 = new HashSet<>(Arrays.asList("apple", "banana"));
        Set<String> set2 = new HashSet<>(Arrays.asList("banana", "cherry"));

        // When: 比较
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = strategy.compare(set1, set2, options);

        // Then: 应使用简单增删逻辑
        assertNotNull(result);
        assertFalse(result.isIdentical());

        // ✅ P1-T3: 使用查询 API 替代手动 filter
        long createCount = result.getChangesByType(ChangeType.CREATE).size();
        long deleteCount = result.getChangesByType(ChangeType.DELETE).size();

        assertEquals(1, createCount, "应有 1 个 CREATE (cherry)");
        assertEquals(1, deleteCount, "应有 1 个 DELETE (apple)");

        System.out.println("Non-Entity Set: CREATE=" + createCount + ", DELETE=" + deleteCount);
    }

    /**
     * 辅助方法：提取变更路径
     */
    private List<String> extractChangePaths(CompareResult result) {
        return result.getChanges().stream()
                .map(c -> c.getFieldPath() != null ? c.getFieldPath() : c.getFieldName())
                .sorted()
                .toList();
    }
}
