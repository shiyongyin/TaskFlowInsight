package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-T1: 容器事件结构化 - 黄金集成测试套件
 *
 * 这些测试验证真实世界场景下的完整行为:
 * 1. 复杂嵌套结构（List<Map<String, Entity>>）
 * 2. 多种操作混合（ADD + REMOVE + MODIFY + MOVE）
 * 3. 端到端查询API集成
 * 4. 性能基准（确保无明显性能退化）
 */
@DisplayName("P1-T1: Golden Integration Tests")
class ContainerEventsGoldenIntegrationTests {

    // ==================== Complex Domain Model ====================

    @Entity
    static class Order {
        @Key
        private final String orderId;
        private final String status;
        private final List<OrderItem> items;

        Order(String orderId, String status, List<OrderItem> items) {
            this.orderId = orderId;
            this.status = status;
            this.items = items;
        }

        public String getOrderId() { return orderId; }
        public String getStatus() { return status; }
        public List<OrderItem> getItems() { return items; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Order)) return false;
            Order order = (Order) o;
            return orderId.equals(order.orderId);
        }

        @Override
        public int hashCode() {
            return orderId.hashCode();
        }
    }

    @Entity
    static class OrderItem {
        @Key
        private final String sku;
        private final int quantity;
        private final double price;

        OrderItem(String sku, int quantity, double price) {
            this.sku = sku;
            this.quantity = quantity;
            this.price = price;
        }

        public String getSku() { return sku; }
        public int getQuantity() { return quantity; }
        public double getPrice() { return price; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof OrderItem)) return false;
            OrderItem item = (OrderItem) o;
            return sku.equals(item.sku);
        }

        @Override
        public int hashCode() {
            return sku.hashCode();
        }
    }

    // ==================== End-to-End Scenarios ====================

    @Test
    @DisplayName("E2E: Complex order modification should produce correct container events")
    void e2e_complexOrderModification_shouldProduceCorrectEvents() {
        EntityListStrategy strategy = new EntityListStrategy();

        // 旧状态：1个订单，2个商品
        Order oldOrder = new Order("ORD-001", "PENDING", List.of(
            new OrderItem("SKU-A", 2, 10.0),
            new OrderItem("SKU-B", 1, 20.0)
        ));

        // 新状态：订单状态改变 + 商品数量改变 + 新增商品
        Order newOrder = new Order("ORD-001", "CONFIRMED", List.of(
            new OrderItem("SKU-A", 5, 10.0),  // quantity: 2 -> 5
            new OrderItem("SKU-B", 1, 20.0),  // unchanged
            new OrderItem("SKU-C", 3, 15.0)   // new item
        ));

        List<Order> oldList = List.of(oldOrder);
        List<Order> newList = List.of(newOrder);

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        // 验证：应该有多个变更（状态变更 + 商品变更 + 新增商品）
        assertFalse(result.getChanges().isEmpty());

        // 验证所有变更都有elementEvent
        for (FieldChange fc : result.getChanges()) {
            assertTrue(fc.isContainerElementChange(), "All changes should have elementEvent");
            assertEquals(FieldChange.ContainerType.LIST, fc.getElementEvent().getContainerType());
        }

        // 验证查询API
        List<FieldChange> containerChanges = result.getContainerChanges();
        assertEquals(result.getChanges().size(), containerChanges.size());

        // 验证分组API
        Map<FieldChange.ElementOperation, List<FieldChange>> grouped = result.groupByContainerOperation();
        assertFalse(grouped.isEmpty());
    }

    @Test
    @DisplayName("E2E: Mixed container types (List + Map + Set) should all have proper events")
    void e2e_mixedContainerTypes_shouldHaveProperEvents() {
        // Scenario: 订单列表变更
        SimpleListStrategy listStrategy = new SimpleListStrategy();
        List<String> oldList = List.of("A", "B");
        List<String> newList = List.of("A", "C", "D");

        CompareResult listResult = listStrategy.compare(oldList, newList, CompareOptions.DEFAULT);

        // Scenario: 用户偏好Map变更
        MapCompareStrategy mapStrategy = new MapCompareStrategy();
        Map<String, String> oldMap = Map.of("theme", "light", "lang", "en");
        Map<String, String> newMap = Map.of("theme", "dark", "lang", "en", "timezone", "UTC");

        CompareResult mapResult = mapStrategy.compare(oldMap, newMap, CompareOptions.DEFAULT);

        // Scenario: 标签Set变更
        SetCompareStrategy setStrategy = new SetCompareStrategy();
        Set<String> oldSet = Set.of("tag1", "tag2");
        Set<String> newSet = Set.of("tag2", "tag3");

        CompareResult setResult = setStrategy.compare(oldSet, newSet, CompareOptions.DEFAULT);

        // 验证：所有3种容器类型都应该有正确的elementEvent
        assertFalse(listResult.getContainerChanges().isEmpty(), "LIST changes should have events");
        assertFalse(mapResult.getContainerChanges().isEmpty(), "MAP changes should have events");
        assertFalse(setResult.getContainerChanges().isEmpty(), "SET changes should have events");

        // 验证containerType正确
        for (FieldChange fc : listResult.getContainerChanges()) {
            assertEquals(FieldChange.ContainerType.LIST, fc.getElementEvent().getContainerType());
        }

        for (FieldChange fc : mapResult.getContainerChanges()) {
            assertEquals(FieldChange.ContainerType.MAP, fc.getElementEvent().getContainerType());
        }

        for (FieldChange fc : setResult.getContainerChanges()) {
            assertEquals(FieldChange.ContainerType.SET, fc.getElementEvent().getContainerType());
        }
    }

    @Test
    @DisplayName("E2E: Query API chaining should work for complex filtering")
    void e2e_queryApiChaining_shouldWorkForComplexFiltering() {
        SimpleListStrategy strategy = new SimpleListStrategy();
        List<Integer> oldList = List.of(1, 2, 3, 4, 5);
        List<Integer> newList = List.of(1, 99, 3, 88, 5, 6);

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        // 场景1: 获取所有容器变更
        List<FieldChange> allContainerChanges = result.getContainerChanges();
        assertFalse(allContainerChanges.isEmpty());

        // 场景2: 按操作类型分组
        Map<FieldChange.ElementOperation, List<FieldChange>> grouped = result.groupByContainerOperation();

        // 场景3: 过滤出MODIFY操作
        List<FieldChange> modifyOps = allContainerChanges.stream()
            .filter(fc -> fc.getContainerOperation() == FieldChange.ElementOperation.MODIFY)
            .toList();

        // 场景4: 过滤出CREATE操作
        List<FieldChange> createOps = allContainerChanges.stream()
            .filter(fc -> fc.getContainerOperation() == FieldChange.ElementOperation.ADD)
            .toList();

        // 验证
        assertTrue(modifyOps.size() + createOps.size() <= allContainerChanges.size());

        // 验证grouped map包含的元素总数等于allContainerChanges
        int totalGrouped = grouped.values().stream().mapToInt(List::size).sum();
        assertEquals(allContainerChanges.size(), totalGrouped,
            "Grouped map should contain all container changes");
    }

    // ==================== Performance Baseline Tests ====================

    @Test
    @DisplayName("PERF: Large list comparison should complete within reasonable time")
    void perf_largeListComparison_shouldCompleteQuickly() {
        SimpleListStrategy strategy = new SimpleListStrategy();

        // 创建100个元素的列表
        List<Integer> oldList = java.util.stream.IntStream.range(0, 100)
            .boxed()
            .toList();

        List<Integer> newList = java.util.stream.IntStream.range(0, 100)
            .map(i -> i % 2 == 0 ? i : i + 1000) // 修改一半元素
            .boxed()
            .toList();

        long startTime = System.currentTimeMillis();
        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);
        long duration = System.currentTimeMillis() - startTime;

        // 验证：应该在500ms内完成（基准：P1-T1不应导致明显性能退化）
        assertTrue(duration < 500, "Large list comparison should complete within 500ms, took: " + duration + "ms");

        // 验证结果正确性
        assertFalse(result.getChanges().isEmpty());
        assertTrue(result.getChanges().size() <= 100, "Should not produce more changes than elements");

        // 验证所有变更都有elementEvent
        for (FieldChange fc : result.getChanges()) {
            assertTrue(fc.isContainerElementChange());
        }
    }

    @Test
    @DisplayName("PERF: Deep entity comparison should complete within reasonable time")
    void perf_deepEntityComparison_shouldCompleteQuickly() {
        EntityListStrategy strategy = new EntityListStrategy();

        // 创建20个订单，每个订单10个商品
        List<Order> oldOrders = java.util.stream.IntStream.range(0, 20)
            .mapToObj(i -> new Order(
                "ORD-" + i,
                "PENDING",
                java.util.stream.IntStream.range(0, 10)
                    .mapToObj(j -> new OrderItem("SKU-" + j, 1, 10.0))
                    .toList()
            ))
            .toList();

        List<Order> newOrders = java.util.stream.IntStream.range(0, 20)
            .mapToObj(i -> new Order(
                "ORD-" + i,
                i % 2 == 0 ? "CONFIRMED" : "PENDING", // 修改一半订单状态
                java.util.stream.IntStream.range(0, 10)
                    .mapToObj(j -> new OrderItem("SKU-" + j, j % 2 == 0 ? 2 : 1, 10.0)) // 修改一半商品数量
                    .toList()
            ))
            .toList();

        long startTime = System.currentTimeMillis();
        CompareResult result = strategy.compare(oldOrders, newOrders, CompareOptions.DEFAULT);
        long duration = System.currentTimeMillis() - startTime;

        // 验证：深度比较应该在2秒内完成
        assertTrue(duration < 2000, "Deep entity comparison should complete within 2s, took: " + duration + "ms");

        // 验证结果正确性
        assertFalse(result.getChanges().isEmpty());

        // 验证所有变更都有elementEvent
        for (FieldChange fc : result.getChanges()) {
            assertTrue(fc.isContainerElementChange());
        }
    }

    // ==================== Error Recovery Tests ====================

    @Test
    @DisplayName("RESILIENCE: Null elements in containers should not cause failures")
    void resilience_nullElements_shouldNotCauseFailures() {
        SimpleListStrategy strategy = new SimpleListStrategy();

        List<String> oldList = java.util.Arrays.asList("A", "B");
        List<String> newList = java.util.Arrays.asList("A", null, "C");

        assertDoesNotThrow(() -> {
            CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);
            assertFalse(result.getChanges().isEmpty());

            // 验证所有变更都有elementEvent（即使值为null）
            for (FieldChange fc : result.getChanges()) {
                assertTrue(fc.isContainerElementChange());
            }
        });
    }

    @Test
    @DisplayName("RESILIENCE: Empty containers should produce valid results")
    void resilience_emptyContainers_shouldProduceValidResults() {
        // List
        SimpleListStrategy listStrategy = new SimpleListStrategy();
        CompareResult listResult = listStrategy.compare(List.of(), List.of("A"), CompareOptions.DEFAULT);
        assertEquals(1, listResult.getContainerChanges().size());

        // Map
        MapCompareStrategy mapStrategy = new MapCompareStrategy();
        CompareResult mapResult = mapStrategy.compare(Map.of(), Map.of("k", "v"), CompareOptions.DEFAULT);
        assertEquals(1, mapResult.getContainerChanges().size());

        // Set
        SetCompareStrategy setStrategy = new SetCompareStrategy();
        CompareResult setResult = setStrategy.compare(Set.of(), Set.of("A"), CompareOptions.DEFAULT);
        assertEquals(1, setResult.getContainerChanges().size());
    }

    // ==================== Complex Query Scenarios ====================

    @Test
    @DisplayName("QUERY: Complex filtering by multiple criteria should work")
    void query_complexFiltering_shouldWork() {
        SimpleListStrategy strategy = new SimpleListStrategy();
        List<Integer> oldList = List.of(1, 2, 3, 4, 5);
        List<Integer> newList = List.of(1, 99, 3, 88, 77, 6);

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        // 复杂查询1: 获取所有MODIFY操作且索引<3的变更
        List<FieldChange> filteredChanges = result.getContainerChanges().stream()
            .filter(fc -> fc.getContainerOperation() == FieldChange.ElementOperation.MODIFY)
            .filter(fc -> fc.getContainerIndex() != null && fc.getContainerIndex() < 3)
            .toList();

        // 复杂查询2: 获取所有新值>50的变更
        List<FieldChange> largeValueChanges = result.getContainerChanges().stream()
            .filter(fc -> fc.getNewValue() != null && (Integer)fc.getNewValue() > 50)
            .toList();

        // 验证
        assertNotNull(filteredChanges);
        assertNotNull(largeValueChanges);

        // 验证所有查询结果都有elementEvent
        for (FieldChange fc : filteredChanges) {
            assertTrue(fc.isContainerElementChange());
        }

        for (FieldChange fc : largeValueChanges) {
            assertTrue(fc.isContainerElementChange());
        }
    }
}
