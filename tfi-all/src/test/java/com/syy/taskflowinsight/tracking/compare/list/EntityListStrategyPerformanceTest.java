package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EntityListStrategy 性能基准测试 - 大样本重复key场景
 *
 * 运行方式: ./mvnw test -Dtest=EntityListStrategyPerformanceTest -Dperf=true
 *
 * @author TaskFlow Insight Team
 */
@EnabledIfSystemProperty(named = "perf", matches = "true")
class EntityListStrategyPerformanceTest {

    @Entity(name = "Product")
    static class Product {
        @Key
        private Long id;
        private String name;
        private Double price;

        public Product(Long id, String name, Double price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public Double getPrice() { return price; }

        // ⚠️ equals比较所有字段，允许Set包含多个相同id的Product
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Product product = (Product) o;
            return Objects.equals(id, product.id) &&
                   Objects.equals(name, product.name) &&
                   Objects.equals(price, product.price);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name, price);
        }
    }

    /**
     * 场景1: 无重复key - 基准测试 (1000个实体)
     */
    @Test
    void benchmarkNoDuplicates_1000Entities() {
        System.out.println("\n【性能基准】无重复key - 1000个实体");

        List<Product> list1 = generateProducts(1000, 1);  // 每个key 1个实例
        List<Product> list2 = generateProducts(1000, 1);

        EntityListStrategy strategy = new EntityListStrategy();

        long start = System.currentTimeMillis();
        CompareResult result = strategy.compare(list1, list2, CompareOptions.builder().build());
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("耗时: %d ms | 变更数: %d | 重复Keys: %s%n",
            elapsed, result.getChangeCount(), result.hasDuplicateKeys());

        assertFalse(result.hasDuplicateKeys());
        assertTrue(elapsed < 1000, "应该在1秒内完成");
    }

    /**
     * 场景2: 少量重复key - 100个key，每个key 2个实例 (共200个实体)
     */
    @Test
    void benchmarkLowDuplication_100Keys_2InstancesEach() {
        System.out.println("\n【性能基准】少量重复 - 100个key × 2实例");

        List<Product> list1 = generateProducts(100, 2);
        List<Product> list2 = generateProducts(100, 2);

        EntityListStrategy strategy = new EntityListStrategy();

        long start = System.currentTimeMillis();
        CompareResult result = strategy.compare(list1, list2, CompareOptions.builder().build());
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("耗时: %d ms | 变更数: %d | 重复Keys数: %d%n",
            elapsed, result.getChangeCount(), result.getDuplicateKeys().size());

        assertTrue(result.hasDuplicateKeys());
        assertEquals(100, result.getDuplicateKeys().size(), "应该有100个重复key");
        assertTrue(elapsed < 2000, "应该在2秒内完成");
    }

    /**
     * 场景3: 中等重复 - 50个key，每个key 5个实例 (共250个实体)
     */
    @Test
    void benchmarkMediumDuplication_50Keys_5InstancesEach() {
        System.out.println("\n【性能基准】中等重复 - 50个key × 5实例");

        List<Product> list1 = generateProducts(50, 5);
        List<Product> list2 = generateProducts(50, 5);

        EntityListStrategy strategy = new EntityListStrategy();

        long start = System.currentTimeMillis();
        CompareResult result = strategy.compare(list1, list2, CompareOptions.builder().build());
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("耗时: %d ms | 变更数: %d | 重复Keys数: %d%n",
            elapsed, result.getChangeCount(), result.getDuplicateKeys().size());

        assertEquals(50, result.getDuplicateKeys().size());
        assertTrue(elapsed < 3000, "应该在3秒内完成");
    }

    /**
     * 场景4: 高度重复 - 10个key，每个key 10个实例 (共100个实体)
     */
    @Test
    void benchmarkHighDuplication_10Keys_10InstancesEach() {
        System.out.println("\n【性能基准】高度重复 - 10个key × 10实例");

        List<Product> list1 = generateProducts(10, 10);
        List<Product> list2 = generateProducts(10, 10);

        EntityListStrategy strategy = new EntityListStrategy();

        long start = System.currentTimeMillis();
        CompareResult result = strategy.compare(list1, list2, CompareOptions.builder().build());
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("耗时: %d ms | 变更数: %d | 重复Keys数: %d%n",
            elapsed, result.getChangeCount(), result.getDuplicateKeys().size());

        assertEquals(10, result.getDuplicateKeys().size());

        // 高度重复场景：每个key的10个旧实例 + 10个新实例 = 20个变更 × 10个key = 200个变更
        assertEquals(200, result.getChangeCount(), "应该有200个变更（10 DELETE + 10 CREATE per key）");
        assertTrue(elapsed < 5000, "应该在5秒内完成");
    }

    /**
     * 场景5: 极端重复 - 1个key，100个实例
     */
    @Test
    void benchmarkExtremeDuplication_1Key_100Instances() {
        System.out.println("\n【性能基准】极端重复 - 1个key × 100实例");

        List<Product> list1 = generateProducts(1, 100);
        List<Product> list2 = generateProducts(1, 100);

        EntityListStrategy strategy = new EntityListStrategy();

        long start = System.currentTimeMillis();
        CompareResult result = strategy.compare(list1, list2, CompareOptions.builder().build());
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("耗时: %d ms | 变更数: %d | 重复Keys数: %d%n",
            elapsed, result.getChangeCount(), result.getDuplicateKeys().size());

        assertEquals(1, result.getDuplicateKeys().size());
        assertEquals(200, result.getChangeCount(), "应该有200个变更（100 DELETE + 100 CREATE）");
        assertTrue(elapsed < 10000, "应该在10秒内完成");
    }

    /**
     * 场景6: 复合键重复 - 性能对比
     */
    @Test
    void benchmarkCompositeKeyDuplication() {
        System.out.println("\n【性能基准】复合键重复场景");

        @Entity(name = "Config")
        class Config {
            @Key
            private String namespace;
            @Key
            private String key;
            private String value;

            public Config(String namespace, String key, String value) {
                this.namespace = namespace;
                this.key = key;
                this.value = value;
            }

            public String getNamespace() { return namespace; }
            public String getKey() { return key; }
            public String getValue() { return value; }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                Config config = (Config) o;
                return Objects.equals(namespace, config.namespace) &&
                       Objects.equals(key, config.key) &&
                       Objects.equals(value, config.value);
            }

            @Override
            public int hashCode() {
                return Objects.hash(namespace, key, value);
            }
        }

        // 50个复合key，每个key 3个实例
        List<Config> list1 = new ArrayList<>();
        List<Config> list2 = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            String ns = "ns" + (i / 10);  // 5个namespace
            String key = "key" + i;

            for (int j = 0; j < 3; j++) {
                list1.add(new Config(ns, key, "value" + i + "-" + j));
                list2.add(new Config(ns, key, "value" + i + "-" + j));
            }
        }

        EntityListStrategy strategy = new EntityListStrategy();

        long start = System.currentTimeMillis();
        CompareResult result = strategy.compare(list1, list2, CompareOptions.builder().build());
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("耗时: %d ms | 变更数: %d | 重复Keys数: %d%n",
            elapsed, result.getChangeCount(), result.getDuplicateKeys().size());

        assertEquals(50, result.getDuplicateKeys().size());
        assertTrue(elapsed < 5000, "复合键场景应该在5秒内完成");
    }

    /**
     * 生成测试数据
     *
     * @param uniqueKeys 唯一key数量
     * @param instancesPerKey 每个key的实例数量
     * @return 产品列表
     */
    private List<Product> generateProducts(int uniqueKeys, int instancesPerKey) {
        List<Product> products = new ArrayList<>();

        for (long id = 1; id <= uniqueKeys; id++) {
            for (int i = 0; i < instancesPerKey; i++) {
                products.add(new Product(
                    id,
                    "Product-" + id + "-" + i,
                    100.0 + (id * 10) + i
                ));
            }
        }

        return products;
    }

    /**
     * 打印性能总结
     */
    @Test
    void printPerformanceSummary() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("性能测试总结");
        System.out.println("=".repeat(80));
        System.out.println("测试配置: 运行所有性能测试用 -Dperf=true");
        System.out.println();
        System.out.println("测试覆盖场景:");
        System.out.println("  1. 无重复 (1000个实体) - 基准对比");
        System.out.println("  2. 少量重复 (100 keys × 2) - 低密度重复");
        System.out.println("  3. 中等重复 (50 keys × 5) - 中等密度");
        System.out.println("  4. 高度重复 (10 keys × 10) - 高密度重复");
        System.out.println("  5. 极端重复 (1 key × 100) - 最坏情况");
        System.out.println("  6. 复合键重复 - 转义处理性能");
        System.out.println();
        System.out.println("时间复杂度分析:");
        System.out.println("  - createEntityMap: O(n) - 遍历构建Map<String, List<Object>>");
        System.out.println("  - compare: O(n × m) - n=uniqueKeys, m=avg_instances_per_key");
        System.out.println("  - handleMultipleInstances: O(k) - k=instances for this key");
        System.out.println();
        System.out.println("内存使用:");
        System.out.println("  - Map存储: ~O(n) for unique keys");
        System.out.println("  - 变更列表: O(total_changes)");
        System.out.println("  - 重复key场景下变更数: 2 × (旧实例数 + 新实例数)");
        System.out.println("=".repeat(80));
    }
}
