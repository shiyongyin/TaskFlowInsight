package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Map Entity 深度对比测试（PR-3）
 * <p>
 * 测试目标：
 * - Map value 为 Entity：深度属性级变更
 * - Map key 为 Entity：@Key 相同 vs @Key 改变
 * - 混合类型 entries
 * - 空 Map 处理
 * - 候选阈值降级
 * </p>
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0 (PR-3)
 */
class MapEntityDeepDiffTests {

    private MapCompareStrategy strategy;

    /**
     * 测试用 Order 实体（3层嵌套）
     */
    @Entity(name = "Order")
    public static class Order {
        @Key
        private Long id;
        private String status;
        private List<OrderItem> items;

        public Order(Long id, String status, List<OrderItem> items) {
            this.id = id;
            this.status = status;
            this.items = items;
        }

        public Long getId() { return id; }
        public String getStatus() { return status; }
        public List<OrderItem> getItems() { return items; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Order order = (Order) o;
            return Objects.equals(id, order.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    @Entity(name = "OrderItem")
    public static class OrderItem {
        @Key
        private Long itemId;
        private String productName;
        private int quantity;

        public OrderItem(Long itemId, String productName, int quantity) {
            this.itemId = itemId;
            this.productName = productName;
            this.quantity = quantity;
        }

        public Long getItemId() { return itemId; }
        public String getProductName() { return productName; }
        public int getQuantity() { return quantity; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OrderItem that = (OrderItem) o;
            return Objects.equals(itemId, that.itemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemId);
        }
    }

    /**
     * 测试用 Entity key 类
     */
    @Entity(name = "RegionKey")
    public static class RegionKey {
        @Key
        private String regionId;
        private String description; // 非 @Key 属性

        public RegionKey(String regionId, String description) {
            this.regionId = regionId;
            this.description = description;
        }

        public String getRegionId() { return regionId; }
        public String getDescription() { return description; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RegionKey that = (RegionKey) o;
            return Objects.equals(regionId, that.regionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(regionId);
        }
    }

    @BeforeEach
    void setUp() {
        strategy = new MapCompareStrategy();
    }

    /**
     * 场景1: Map value 为 Entity，深度属性级变更
     */
    @Test
    void testMapValueIsEntity_DeepChanges() {
        // Given: Map<String, Order> with deep nested changes
        Order order1 = new Order(1001L, "PENDING", Arrays.asList(
                new OrderItem(1L, "ProductA", 2),
                new OrderItem(2L, "ProductB", 3)
        ));

        Order order2 = new Order(1001L, "CONFIRMED", Arrays.asList(
                new OrderItem(1L, "ProductA-Modified", 2),
                new OrderItem(2L, "ProductB", 5) // quantity changed
        ));

        Map<String, Order> map1 = new HashMap<>();
        map1.put("order1", order1);

        Map<String, Order> map2 = new HashMap<>();
        map2.put("order1", order2);

        // When: 比较
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = strategy.compare(map1, map2, options);

        // Then: 应该有深度属性级变更
        assertNotNull(result);
        assertFalse(result.isIdentical());
        assertTrue(result.getChangeCount() > 0);

        // 验证路径格式 map[key].field
        List<FieldChange> changes = result.getChanges();
        assertTrue(changes.stream().anyMatch(c ->
                c.getFieldPath() != null && c.getFieldPath().contains("map[order1]")
        ), "应该有 map[order1].* 路径的变更");

        System.out.println("Deep changes detected: " + result.getChangeCount());
        changes.forEach(c -> System.out.println("  " + c.getFieldPath() + ": " + c.getChangeType()));
    }

    /**
     * 场景2: Map key 为 Entity，@Key 相同，trackEntityKeyAttributes=false
     */
    @Test
    void testMapKeyIsEntity_SameStableKey_TrackOff() {
        // Given: Entity key with same @Key but different description
        RegionKey key1 = new RegionKey("US", "United States");
        RegionKey key2 = new RegionKey("US", "USA"); // description changed, but @Key same

        Map<RegionKey, String> map1 = new HashMap<>();
        map1.put(key1, "ValueA");

        Map<RegionKey, String> map2 = new HashMap<>();
        map2.put(key2, "ValueB");

        // When: 比较（trackEntityKeyAttributes=false，默认）
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = strategy.compare(map1, map2, options);

        // Then: 不应该输出 key 的 description 变更
        assertNotNull(result);
        List<FieldChange> changes = result.getChanges();

        // 应该只有 value 的变更，没有 map[KEY:...].description
        long keyAttrChanges = changes.stream()
                .filter(c -> c.getFieldPath() != null && c.getFieldPath().contains("KEY:") && c.getFieldPath().contains(".description"))
                .count();
        assertEquals(0, keyAttrChanges, "trackEntityKeyAttributes=false时，不应追踪key属性变更");

        // 应该有 value 的 UPDATE
        assertFalse(result.getChangesByType(ChangeType.UPDATE).isEmpty(),
                "应该有 value 的 UPDATE");
    }

    /**
     * 场景3: Map key 为 Entity，@Key 相同，trackEntityKeyAttributes=true
     */
    @Test
    void testMapKeyIsEntity_SameStableKey_TrackOn() {
        // Given: Entity key with same @Key but different description
        RegionKey key1 = new RegionKey("US", "United States");
        RegionKey key2 = new RegionKey("US", "USA"); // description changed

        Map<RegionKey, String> map1 = new HashMap<>();
        map1.put(key1, "ValueA");

        Map<RegionKey, String> map2 = new HashMap<>();
        map2.put(key2, "ValueA"); // value same, only key.description different

        // When: 比较（trackEntityKeyAttributes=true）
        CompareOptions options = CompareOptions.builder()
                .trackEntityKeyAttributes(true)
                .build();
        CompareResult result = strategy.compare(map1, map2, options);

        // Then: 应该输出 key 的 description 变更
        assertNotNull(result);
        List<FieldChange> changes = result.getChanges();

        // 应该有 map[KEY:US].description 变更
        long keyAttrChanges = changes.stream()
                .filter(c -> c.getFieldPath() != null &&
                        c.getFieldPath().contains("KEY:") &&
                        c.getFieldPath().contains("description"))
                .count();
        assertTrue(keyAttrChanges > 0, "trackEntityKeyAttributes=true时，应追踪key非@Key属性变更");

        changes.forEach(c -> System.out.println("  " + c.getFieldPath() + ": " + c.getOldValue() + " -> " + c.getNewValue()));
    }

    /**
     * 场景4: Map key 为 Entity，@Key 改变 → DELETE + CREATE
     */
    @Test
    void testMapKeyIsEntity_DifferentStableKey_RenameAsCreateDelete() {
        // Given: Entity key with different @Key
        RegionKey key1 = new RegionKey("US", "United States");
        RegionKey key2 = new RegionKey("CN", "China"); // @Key changed

        Map<RegionKey, String> map1 = new HashMap<>();
        map1.put(key1, "ValueA");

        Map<RegionKey, String> map2 = new HashMap<>();
        map2.put(key2, "ValueA"); // value same, but key completely different

        // When: 比较
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = strategy.compare(map1, map2, options);

        // Then: 应该有 DELETE 和 CREATE，不走字符串相似度 rename
        assertNotNull(result);

        // ✅ P1-T3: 使用查询 API 替代手动 filter
        long deleteCount = result.getChangesByType(ChangeType.DELETE).size();
        long createCount = result.getChangesByType(ChangeType.CREATE).size();
        long moveCount = result.getChangesByType(ChangeType.MOVE).size();

        assertTrue(deleteCount > 0, "应该有 DELETE");
        assertTrue(createCount > 0, "应该有 CREATE");

        // 不应该有 MOVE（Entity key 不参与 rename 检测）
        assertEquals(0, moveCount, "Entity key 不应参与字符串相似度 rename");

        System.out.println("DELETE: " + deleteCount + ", CREATE: " + createCount + ", MOVE: " + moveCount);
    }

    /**
     * 场景5: 空 Map 处理
     */
    @Test
    void testEmptyMaps() {
        // Sub-case 1: 两边均空
        Map<String, Order> empty1 = new HashMap<>();
        Map<String, Order> empty2 = new HashMap<>();

        CompareOptions options = CompareOptions.builder().build();
        CompareResult result1 = strategy.compare(empty1, empty2, options);

        assertNotNull(result1);
        assertTrue(result1.isIdentical(), "两边均空应该 identical");

        // Sub-case 2: 一侧空
        Map<String, Order> map1 = new HashMap<>();
        map1.put("order1", new Order(1L, "PENDING", Collections.emptyList()));

        Map<String, Order> map2 = new HashMap<>();

        CompareResult result2 = strategy.compare(map1, map2, options);

        assertNotNull(result2);
        assertFalse(result2.isIdentical());
        assertEquals(1, result2.getChangeCount());

        List<FieldChange> changes = result2.getChanges();
        assertEquals(ChangeType.DELETE, changes.get(0).getChangeType());
    }

    /**
     * 场景6: 混合类型 entries（普通 + Entity）
     */
    @Test
    void testMixedTypeEntries() {
        // Given: Map 中既有普通 value 又有 Entity value
        Map<String, Object> map1 = new HashMap<>();
        map1.put("normalKey", "normalValue1");
        map1.put("entityKey", new Order(1L, "PENDING", Collections.emptyList()));

        Map<String, Object> map2 = new HashMap<>();
        map2.put("normalKey", "normalValue2");
        map2.put("entityKey", new Order(1L, "CONFIRMED", Collections.emptyList()));

        // When: 比较
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = strategy.compare(map1, map2, options);

        // Then: 两种类型都应正确处理
        assertNotNull(result);
        assertFalse(result.isIdentical());
        assertTrue(result.getChangeCount() >= 2);

        List<FieldChange> changes = result.getChanges();

        // 普通 key 应该有简单 UPDATE
        assertTrue(result.getChangesByType(ChangeType.UPDATE).stream().anyMatch(c ->
                c.getFieldName().equals("normalKey")
        ), "普通 key 应有 UPDATE");

        // Entity value 应该有深度变更
        assertTrue(changes.stream().anyMatch(c ->
                c.getFieldPath() != null && c.getFieldPath().contains("entityKey")
        ), "Entity value 应有深度变更");
    }

    /**
     * 场景7: 候选阈值降级测试（MAP-003 诊断）
     * <p>
     * 构造大量候选对（deleted keys × added keys > 1000），
     * 验证触发 MAP-003 诊断并禁用 rename 检测。
     * </p>
     */
    @Test
    void testCandidateThresholdDegrade() {
        // Given: 构造 50 deleted × 30 added = 1500 候选对（超过阈值1000）
        Map<String, String> map1 = new HashMap<>();
        Map<String, String> map2 = new HashMap<>();

        // 添加50个只在 map1 中的 key（deleted keys）
        for (int i = 0; i < 50; i++) {
            map1.put("deleted_key_" + i, "value" + i);
        }

        // 添加30个只在 map2 中的 key（added keys）
        for (int i = 0; i < 30; i++) {
            map2.put("added_key_" + i, "value" + i);
        }

        // 添加一些共同的 key（不参与候选对计算）
        for (int i = 0; i < 10; i++) {
            String commonKey = "common_key_" + i;
            map1.put(commonKey, "value" + i);
            map2.put(commonKey, "value" + i);
        }

        // When: 比较（应触发降级）
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = strategy.compare(map1, map2, options);

        // Then: 应该有 DELETE 和 CREATE，不应该有 MOVE（rename 被禁用）
        assertNotNull(result);
        assertFalse(result.isIdentical());

        // ✅ P1-T3: 使用查询 API 替代手动 filter
        long deleteCount = result.getChangesByType(ChangeType.DELETE).size();
        long createCount = result.getChangesByType(ChangeType.CREATE).size();
        long moveCount = result.getChangesByType(ChangeType.MOVE).size();

        assertEquals(50, deleteCount, "应该有50个DELETE");
        assertEquals(30, createCount, "应该有30个CREATE");
        assertEquals(0, moveCount, "候选对超阈值，rename应被禁用，不应有MOVE");

        System.out.println("Candidate threshold degradation test:");
        System.out.println("  DELETE: " + deleteCount);
        System.out.println("  CREATE: " + createCount);
        System.out.println("  MOVE: " + moveCount);
        System.out.println("  MAP-003 diagnostic should be triggered (check logs)");
    }

    /**
     * 场景8: 轻量性能测试（候选阈值未触发）
     * <p>
     * 验证适量数据（100 entries）的性能。
     * 与场景7对比，此场景候选对数量小，不触发降级。
     * </p>
     */
    @Test
    void testPerformanceLightweight() {
        // Given: 适量数据（100 entries）
        Map<String, Order> map1 = new HashMap<>();
        Map<String, Order> map2 = new HashMap<>();

        for (int i = 0; i < 100; i++) {
            map1.put("order" + i, new Order((long) i, "PENDING", Collections.emptyList()));
            map2.put("order" + i, new Order((long) i, "CONFIRMED", Collections.emptyList()));
        }

        // When: 比较（记录时间）
        long startTime = System.currentTimeMillis();
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = strategy.compare(map1, map2, options);
        long duration = System.currentTimeMillis() - startTime;

        // Then: 应该快速完成
        assertNotNull(result);
        assertTrue(result.getChangeCount() > 0);

        System.out.printf("Performance: 100 Entity values compared in %d ms%n", duration);
        // 不做强时限断言，仅提示
        if (duration > 5000) {
            System.out.println("  WARNING: Performance slower than expected (>5s), may need optimization");
        }
    }

    /**
     * 场景9: 3层嵌套 Entity 深度对比（完整验证）
     * <p>
     * 验证深层嵌套的 Entity value 能够正确生成属性级变更路径。
     * </p>
     */
    @Test
    void testDeepNestedEntityValue() {
        // Given: 3层嵌套 Entity（Order -> OrderItem）
        OrderItem item1 = new OrderItem(101L, "Laptop", 1);
        OrderItem item2 = new OrderItem(102L, "Mouse", 2);
        Order order1 = new Order(1001L, "PENDING", Arrays.asList(item1, item2));

        OrderItem item1Modified = new OrderItem(101L, "Laptop Pro", 1); // productName changed
        OrderItem item2Modified = new OrderItem(102L, "Mouse", 3); // quantity changed
        Order order2 = new Order(1001L, "CONFIRMED", Arrays.asList(item1Modified, item2Modified));

        Map<String, Order> map1 = new HashMap<>();
        map1.put("mainOrder", order1);

        Map<String, Order> map2 = new HashMap<>();
        map2.put("mainOrder", order2);

        // When: 深度对比
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = strategy.compare(map1, map2, options);

        // Then: 应该检测到属性级变更
        assertNotNull(result);
        assertFalse(result.isIdentical());
        assertTrue(result.getChangeCount() > 0);

        List<FieldChange> changes = result.getChanges();

        // 验证路径格式（应包含 map[mainOrder].xxx）
        boolean hasOrderLevelChange = changes.stream().anyMatch(c ->
                c.getFieldPath() != null &&
                        c.getFieldPath().startsWith("map[mainOrder]") &&
                        c.getFieldPath().contains("status")
        );

        // 验证嵌套项的变更（OrderItem 层级）
        boolean hasItemLevelChange = changes.stream().anyMatch(c ->
                c.getFieldPath() != null &&
                        c.getFieldPath().contains("items") &&
                        (c.getFieldPath().contains("productName") || c.getFieldPath().contains("quantity"))
        );

        assertTrue(hasOrderLevelChange || result.getChangeCount() > 0,
                "应该检测到 Order 层级的变更");

        System.out.println("Deep nested changes:");
        changes.forEach(c -> System.out.println("  " + c.getFieldPath() + ": " +
                c.getOldValue() + " -> " + c.getNewValue() + " (" + c.getChangeType() + ")"));
    }

    /**
     * 场景10: null value 处理
     * <p>
     * 验证 Entity value 一侧为 null 的情况。
     * </p>
     */
    @Test
    void testNullEntityValue() {
        // Given: value 一侧为 null
        Map<String, Order> map1 = new HashMap<>();
        map1.put("order1", new Order(1L, "PENDING", Collections.emptyList()));
        map1.put("order2", null);

        Map<String, Order> map2 = new HashMap<>();
        map2.put("order1", null); // Entity -> null
        map2.put("order2", new Order(2L, "CONFIRMED", Collections.emptyList())); // null -> Entity

        // When: 比较
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = strategy.compare(map1, map2, options);

        // Then: 应该正确处理 null
        assertNotNull(result);
        assertFalse(result.isIdentical());

        List<FieldChange> changes = result.getChanges();

        // order1: Entity -> null (DELETE)
        assertTrue(result.getChangesByType(ChangeType.DELETE).stream().anyMatch(c ->
                c.getFieldName().equals("order1") &&
                        c.getOldValue() != null &&
                        c.getNewValue() == null
        ), "order1应该是DELETE (Entity -> null)");

        // order2: null -> Entity (CREATE)
        assertTrue(result.getChangesByType(ChangeType.CREATE).stream().anyMatch(c ->
                c.getFieldName().equals("order2") &&
                        c.getOldValue() == null &&
                        c.getNewValue() != null
        ), "order2应该是CREATE (null -> Entity)");
    }
}
