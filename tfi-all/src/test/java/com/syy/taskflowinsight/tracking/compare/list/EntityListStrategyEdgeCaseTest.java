package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EntityListStrategy 极端场景测试
 *
 * 测试覆盖：
 * 1. 复合键 + 转义冒号（无重复）
 * 2. 复合键 + 转义冒号 + 重复key
 * 3. 极端特殊字符键值
 * 4. extractPureEntityKey 工具方法
 * 5. extractDuplicateIndex 工具方法
 * 6. 单侧重复 - 路径格式一致性
 * 7. 多个连续冒号（::、:::等）
 * 8. 混合转义的复合键（预转义字符）
 * 9. 长复合键（5+组件）+ 重复场景
 * 10. 空组件在复合键中
 *
 * @author TaskFlow Insight Team
 */
class EntityListStrategyEdgeCaseTest {

    /**
     * 测试实体：包含转义冒号的复合键
     */
    @Entity(name = "Config")
    public static class ConfigEntry {
        @Key
        private String namespace;  // 可能包含冒号，如 "app:prod"

        @Key
        private String key;  // 可能包含冒号，如 "db:host"

        private String value;

        public ConfigEntry(String namespace, String key, String value) {
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
            ConfigEntry that = (ConfigEntry) o;
            return Objects.equals(namespace, that.namespace) &&
                   Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(namespace, key);
        }

        @Override
        public String toString() {
            return String.format("Config[ns=%s, key=%s, value=%s]", namespace, key, value);
        }
    }

    /**
     * 场景1: 复合键包含冒号（无重复）
     * 验证 parseKeyParts 正确处理转义冒号
     */
    @Test
    void testCompositeKeyWithColons_NoCollision() {
        EntityListStrategy strategy = new EntityListStrategy();

        List<ConfigEntry> list1 = Arrays.asList(
            new ConfigEntry("app:prod", "db:host", "localhost"),
            new ConfigEntry("app:dev", "db:port", "5432")
        );

        List<ConfigEntry> list2 = Arrays.asList(
            new ConfigEntry("app:prod", "db:host", "192.168.1.1"),  // value变化
            new ConfigEntry("app:dev", "db:port", "5432")
        );

        CompareResult result = strategy.compare(list1, list2, CompareOptions.builder().build());

        System.out.println("\n【场景1】复合键包含冒号（无重复）");
        System.out.println("变更数量: " + result.getChangeCount());
        result.getChanges().forEach(change ->
            System.out.printf("  %s | %s%n", change.getFieldName(), change.getChangeType())
        );

        // 断言
        assertEquals(1, result.getChangeCount(), "应该检测到1个UPDATE");
        assertFalse(result.hasDuplicateKeys(), "不应该有重复key");
    }

    /**
     * 场景2: 复合键包含冒号 + 重复key（equals比较全部字段）
     * 这是最极端的场景，测试稳健性
     */
    @Test
    void testCompositeKeyWithColons_WithDuplicates() {
        EntityListStrategy strategy = new EntityListStrategy();

        // 使用特殊ConfigEntry，equals比较所有字段（包括value）
        @Entity(name = "Config")
        class ConfigWithFullEquals {
            @Key
            private String namespace;
            @Key
            private String key;
            private String value;

            public ConfigWithFullEquals(String namespace, String key, String value) {
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
                ConfigWithFullEquals that = (ConfigWithFullEquals) o;
                // ⚠️ equals比较所有字段，包括非@Key的value
                return Objects.equals(namespace, that.namespace) &&
                       Objects.equals(key, that.key) &&
                       Objects.equals(value, that.value);
            }

            @Override
            public int hashCode() {
                return Objects.hash(namespace, key, value);
            }

            @Override
            public String toString() {
                return String.format("Config[ns=%s, key=%s, value=%s]", namespace, key, value);
            }
        }

        List<ConfigWithFullEquals> list1 = Arrays.asList(
            new ConfigWithFullEquals("app:prod", "db:host", "localhost")
        );

        // list2包含两个相同@Key但不同value的实例
        List<ConfigWithFullEquals> list2 = Arrays.asList(
            new ConfigWithFullEquals("app:prod", "db:host", "localhost"),
            new ConfigWithFullEquals("app:prod", "db:host", "192.168.1.1")
        );

        CompareResult result = strategy.compare(list1, list2, CompareOptions.builder().build());

        System.out.println("\n【场景2】复合键包含冒号 + 重复key");
        System.out.println("变更数量: " + result.getChangeCount());
        System.out.println("包含重复Keys: " + result.hasDuplicateKeys());
        if (result.hasDuplicateKeys()) {
            System.out.println("重复Keys: " + result.getDuplicateKeys());
        }
        result.getChanges().forEach(change ->
            System.out.printf("  %s | %s%n", change.getFieldName(), change.getChangeType())
        );

        // 断言
        assertTrue(result.hasDuplicateKeys(), "应该检测到重复key");
        assertTrue(result.getChangeCount() >= 2, "应该至少有2个变更（CREATE+DELETE）");

        // 验证路径格式包含 #idx
        boolean hasIndexedFormat = result.getChanges().stream()
            .anyMatch(c -> c.getFieldName().contains("#"));
        assertTrue(hasIndexedFormat, "应该使用entity[key#idx]格式");
    }

    /**
     * 场景3: 极端特殊字符
     * 测试各种边界字符
     */
    @Test
    void testExtremeCases() {
        EntityListStrategy strategy = new EntityListStrategy();

        List<ConfigEntry> list1 = Arrays.asList(
            new ConfigEntry("a:b:c", "x:y:z", "v1"),  // 多个冒号
            new ConfigEntry("", "empty-ns", "v2"),     // 空namespace
            new ConfigEntry("ns", "", "v3")            // 空key
        );

        List<ConfigEntry> list2 = Arrays.asList(
            new ConfigEntry("a:b:c", "x:y:z", "v1-changed"),
            new ConfigEntry("", "empty-ns", "v2"),
            new ConfigEntry("ns", "", "v3-changed")
        );

        CompareResult result = strategy.compare(list1, list2, CompareOptions.builder().build());

        System.out.println("\n【场景3】极端特殊字符");
        System.out.println("变更数量: " + result.getChangeCount());
        result.getChanges().forEach(change ->
            System.out.printf("  %s | %s%n", change.getFieldName(), change.getChangeType())
        );

        // 断言：应该正常处理，不抛异常
        assertEquals(2, result.getChangeCount(), "应该检测到2个UPDATE");
    }

    /**
     * 场景4: 工具方法 extractPureEntityKey 测试
     */
    @Test
    void testExtractPureEntityKey() {
        // 正常情况
        assertEquals("123", EntityListStrategy.extractPureEntityKey("entity[123]"));

        // 带索引
        assertEquals("123", EntityListStrategy.extractPureEntityKey("entity[123#0]"));
        assertEquals("456", EntityListStrategy.extractPureEntityKey("entity[456#1]"));

        // 复合键
        assertEquals("ns1:key1", EntityListStrategy.extractPureEntityKey("entity[ns1:key1]"));
        assertEquals("ns1:key1", EntityListStrategy.extractPureEntityKey("entity[ns1:key1#0]"));

        // 边界情况
        assertEquals("", EntityListStrategy.extractPureEntityKey("entity[]"));
        assertEquals("", EntityListStrategy.extractPureEntityKey("entity[#0]"));
        assertEquals("invalid", EntityListStrategy.extractPureEntityKey("invalid"));  // 返回原始值
        assertNull(EntityListStrategy.extractPureEntityKey(null));
    }

    /**
     * 场景5: 工具方法 extractDuplicateIndex 测试
     */
    @Test
    void testExtractDuplicateIndex() {
        // 带索引
        assertEquals(0, EntityListStrategy.extractDuplicateIndex("entity[123#0]"));
        assertEquals(1, EntityListStrategy.extractDuplicateIndex("entity[123#1]"));
        assertEquals(99, EntityListStrategy.extractDuplicateIndex("entity[key#99]"));

        // 无索引
        assertEquals(-1, EntityListStrategy.extractDuplicateIndex("entity[123]"));
        assertEquals(-1, EntityListStrategy.extractDuplicateIndex("entity[ns:key]"));

        // 边界情况
        assertEquals(-1, EntityListStrategy.extractDuplicateIndex("invalid"));
        assertEquals(-1, EntityListStrategy.extractDuplicateIndex(null));
    }

    /**
     * 场景6: 单侧重复 - 验证路径格式一致性
     */
    @Test
    void testOneSideDuplicate_PathConsistency() {
        EntityListStrategy strategy = new EntityListStrategy();

        @Entity(name = "Product")
        class ProductWithFullEquals {
            @Key
            private Long id;
            private String name;

            public ProductWithFullEquals(Long id, String name) {
                this.id = id;
                this.name = name;
            }

            public Long getId() { return id; }
            public String getName() { return name; }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                ProductWithFullEquals that = (ProductWithFullEquals) o;
                return Objects.equals(id, that.id) && Objects.equals(name, that.name);
            }

            @Override
            public int hashCode() {
                return Objects.hash(id, name);
            }
        }

        // 旧侧单个，新侧重复
        List<ProductWithFullEquals> list1 = Arrays.asList(
            new ProductWithFullEquals(1L, "A")
        );

        List<ProductWithFullEquals> list2 = Arrays.asList(
            new ProductWithFullEquals(1L, "A"),
            new ProductWithFullEquals(1L, "B")
        );

        CompareResult result = strategy.compare(list1, list2, CompareOptions.builder().build());

        System.out.println("\n【场景6】单侧重复 - 路径格式一致性");
        result.getChanges().forEach(change ->
            System.out.printf("  %s | %s%n", change.getFieldName(), change.getChangeType())
        );

        // 验证：旧侧单个实例也应该使用 #0 格式
        boolean allIndexed = result.getChanges().stream()
            .allMatch(c -> c.getFieldName().matches("entity\\[.*#\\d+\\]"));

        assertTrue(allIndexed, "所有路径都应该使用entity[key#idx]格式（包括单侧单个实例）");
        assertTrue(result.hasDuplicateKeys(), "应该检测到重复key");
    }

    /**
     * 场景7: 多个连续冒号
     * 验证 a::b、c:::d 等极端格式的处理
     */
    @Test
    void testMultipleConsecutiveColons() {
        EntityListStrategy strategy = new EntityListStrategy();

        List<ConfigEntry> list1 = Arrays.asList(
            new ConfigEntry("a::b", "x::y", "v1"),        // 双冒号
            new ConfigEntry("c:::d", "p:::q:::r", "v2"),  // 三连冒号
            new ConfigEntry("::start", "end::", "v3")     // 首尾冒号
        );

        List<ConfigEntry> list2 = Arrays.asList(
            new ConfigEntry("a::b", "x::y", "v1-changed"),
            new ConfigEntry("c:::d", "p:::q:::r", "v2"),
            new ConfigEntry("::start", "end::", "v3-changed")
        );

        CompareResult result = strategy.compare(list1, list2, CompareOptions.builder().build());

        System.out.println("\n【场景7】多个连续冒号");
        System.out.println("变更数量: " + result.getChangeCount());
        result.getChanges().forEach(change ->
            System.out.printf("  %s | %s%n", change.getFieldName(), change.getChangeType())
        );

        // 断言：应该正常处理，不抛异常
        assertEquals(2, result.getChangeCount(), "应该检测到2个UPDATE");
        assertFalse(result.hasDuplicateKeys(), "不应该有重复key");
    }

    /**
     * 场景8: 混合转义的复合键
     * 验证部分字段已转义的情况
     */
    @Test
    void testMixedEscapingInCompositeKeys() {
        EntityListStrategy strategy = new EntityListStrategy();

        List<ConfigEntry> list1 = Arrays.asList(
            new ConfigEntry("app\\:prod", "env:test", "v1"),      // namespace已转义
            new ConfigEntry("sys", "key\\:name\\:id", "v2")       // key已转义
        );

        List<ConfigEntry> list2 = Arrays.asList(
            new ConfigEntry("app\\:prod", "env:test", "v1-changed"),
            new ConfigEntry("sys", "key\\:name\\:id", "v2-changed")
        );

        CompareResult result = strategy.compare(list1, list2, CompareOptions.builder().build());

        System.out.println("\n【场景8】混合转义的复合键");
        System.out.println("变更数量: " + result.getChangeCount());
        result.getChanges().forEach(change ->
            System.out.printf("  %s | %s%n", change.getFieldName(), change.getChangeType())
        );

        // 断言：应该正常处理预转义的字符
        assertEquals(2, result.getChangeCount(), "应该检测到2个UPDATE");
        assertFalse(result.hasDuplicateKeys(), "不应该有重复key");
    }

    /**
     * 场景9: 长复合键 + 重复场景
     * 验证5+组件复合键的性能与正确性
     */
    @Test
    void testLongCompositeKeysWithDuplicates() {
        EntityListStrategy strategy = new EntityListStrategy();

        @Entity(name = "LongKey")
        class LongKeyEntity {
            @Key private String part1;
            @Key private String part2;
            @Key private String part3;
            @Key private String part4;
            @Key private String part5;
            private String value;

            public LongKeyEntity(String p1, String p2, String p3, String p4, String p5, String val) {
                this.part1 = p1;
                this.part2 = p2;
                this.part3 = p3;
                this.part4 = p4;
                this.part5 = p5;
                this.value = val;
            }

            public String getPart1() { return part1; }
            public String getPart2() { return part2; }
            public String getPart3() { return part3; }
            public String getPart4() { return part4; }
            public String getPart5() { return part5; }
            public String getValue() { return value; }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                LongKeyEntity that = (LongKeyEntity) o;
                // equals比较所有字段，允许重复@Key
                return Objects.equals(part1, that.part1) &&
                       Objects.equals(part2, that.part2) &&
                       Objects.equals(part3, that.part3) &&
                       Objects.equals(part4, that.part4) &&
                       Objects.equals(part5, that.part5) &&
                       Objects.equals(value, that.value);
            }

            @Override
            public int hashCode() {
                return Objects.hash(part1, part2, part3, part4, part5, value);
            }
        }

        List<LongKeyEntity> list1 = Arrays.asList(
            new LongKeyEntity("a:1", "b:2", "c:3", "d:4", "e:5", "v1")
        );

        // 重复@Key，不同value
        List<LongKeyEntity> list2 = Arrays.asList(
            new LongKeyEntity("a:1", "b:2", "c:3", "d:4", "e:5", "v1"),
            new LongKeyEntity("a:1", "b:2", "c:3", "d:4", "e:5", "v2"),
            new LongKeyEntity("a:1", "b:2", "c:3", "d:4", "e:5", "v3")
        );

        long startTime = System.currentTimeMillis();
        CompareResult result = strategy.compare(list1, list2, CompareOptions.builder().build());
        long elapsed = System.currentTimeMillis() - startTime;

        System.out.println("\n【场景9】长复合键 + 重复场景");
        System.out.println("变更数量: " + result.getChangeCount());
        System.out.println("耗时: " + elapsed + "ms");
        System.out.println("重复Keys数: " + result.getDuplicateKeys().size());

        // 断言
        assertTrue(result.hasDuplicateKeys(), "应该检测到重复key");
        assertEquals(4, result.getChangeCount(), "应该有4个变更（1 DELETE + 3 CREATE）");
        assertTrue(elapsed < 100, "长复合键性能应该在100ms内");

        // 验证所有路径使用索引格式
        boolean allIndexed = result.getChanges().stream()
            .allMatch(c -> c.getFieldName().contains("#"));
        assertTrue(allIndexed, "所有路径都应该使用索引格式");
    }

    /**
     * 场景10: 空组件在复合键中
     * 验证 a::b（中间空）、::c（前空）、d::（后空）等模式
     */
    @Test
    void testEmptyComponentsInCompositeKeys() {
        EntityListStrategy strategy = new EntityListStrategy();

        List<ConfigEntry> list1 = Arrays.asList(
            new ConfigEntry("a", "", "v1"),           // 空key
            new ConfigEntry("", "b", "v2"),           // 空namespace
            new ConfigEntry("", "", "v3")             // 全空
        );

        List<ConfigEntry> list2 = Arrays.asList(
            new ConfigEntry("a", "", "v1-changed"),
            new ConfigEntry("", "b", "v2"),
            new ConfigEntry("", "", "v3-changed")
        );

        CompareResult result = strategy.compare(list1, list2, CompareOptions.builder().build());

        System.out.println("\n【场景10】空组件在复合键中");
        System.out.println("变更数量: " + result.getChangeCount());
        result.getChanges().forEach(change ->
            System.out.printf("  %s | %s%n", change.getFieldName(), change.getChangeType())
        );

        // 断言：应该正常处理空字符串组件
        assertEquals(2, result.getChangeCount(), "应该检测到2个UPDATE");
        assertFalse(result.hasDuplicateKeys(), "不应该有重复key");

        // 验证路径不含非法字符
        boolean allValid = result.getChanges().stream()
            .allMatch(c -> c.getFieldName() != null && !c.getFieldName().isEmpty());
        assertTrue(allValid, "所有路径都应该是有效的");
    }
}
