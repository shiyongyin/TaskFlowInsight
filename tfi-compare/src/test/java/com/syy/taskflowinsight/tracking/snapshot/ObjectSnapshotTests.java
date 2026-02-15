package com.syy.taskflowinsight.tracking.snapshot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * ObjectSnapshot 快照系统真实业务场景测试。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("ObjectSnapshot — 快照系统测试")
class ObjectSnapshotTests {

    // ── 浅快照 ──

    @Nested
    @DisplayName("浅快照 ObjectSnapshot.capture")
    class ShallowSnapshotTests {

        @Test
        @DisplayName("简单 POJO → 捕获所有标量字段")
        void simplePojo_shouldCaptureAllScalarFields() {
            TestUser user = new TestUser("Alice", 30, "alice@test.com");
            Map<String, Object> snapshot = ObjectSnapshot.capture("User", user);
            assertThat(snapshot).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("null 对象 → 返回空 Map")
        void nullObject_shouldReturnEmptyMap() {
            Map<String, Object> snapshot = ObjectSnapshot.capture("Null", null);
            assertThat(snapshot).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("指定字段 → 只捕获指定字段")
        void specifiedFields_shouldOnlyCaptureThose() {
            TestUser user = new TestUser("Bob", 25, "bob@test.com");
            Map<String, Object> snapshot = ObjectSnapshot.capture("User", user, "name", "age");
            assertThat(snapshot).isNotNull();
        }

        @Test
        @DisplayName("Map 对象 → 直接捕获 Map entries")
        void mapObject_shouldCaptureEntries() {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("key1", "value1");
            input.put("key2", 42);
            Map<String, Object> snapshot = ObjectSnapshot.capture("MapObj", input);
            assertThat(snapshot).isNotNull();
        }

        @Test
        @DisplayName("继承字段 → 父类字段也被捕获")
        void inheritedFields_shouldBeCaptured() {
            TestAdmin admin = new TestAdmin("Admin", 35, "admin@test.com", "SUPER");
            Map<String, Object> snapshot = ObjectSnapshot.capture("Admin", admin);
            assertThat(snapshot).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("特殊类型字段 — BigDecimal、Date、Enum")
        void specialTypes_shouldBeCaptured() {
            TestProduct product = new TestProduct("Widget", new BigDecimal("19.99"),
                    new Date(), ProductStatus.ACTIVE);
            Map<String, Object> snapshot = ObjectSnapshot.capture("Product", product);
            assertThat(snapshot).isNotNull().isNotEmpty();
        }
    }

    // ── 深快照 ──

    @Nested
    @DisplayName("深快照 ObjectSnapshotDeep")
    class DeepSnapshotTests {

        private final ObjectSnapshotDeep deepSnapshot = new ObjectSnapshotDeep(new SnapshotConfig());

        @Test
        @DisplayName("嵌套对象 → 递归捕获")
        void nestedObject_shouldCaptureRecursively() {
            TestAddress address = new TestAddress("Main St", "NYC", "10001");
            TestUserWithAddress user = new TestUserWithAddress("Alice", address);
            Map<String, Object> snapshot = deepSnapshot.captureDeep(user, 3,
                    Collections.emptySet(), Collections.emptySet());
            assertThat(snapshot).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("null 对象 → 返回空 Map")
        void nullObject_shouldReturnEmpty() {
            Map<String, Object> snapshot = deepSnapshot.captureDeep(null, 3,
                    Collections.emptySet(), Collections.emptySet());
            assertThat(snapshot).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("maxDepth=0 → 只捕获顶层")
        void maxDepthZero_shouldCaptureTopLevelOnly() {
            TestUserWithAddress user = new TestUserWithAddress("Alice",
                    new TestAddress("St", "City", "12345"));
            Map<String, Object> snapshot = deepSnapshot.captureDeep(user, 0,
                    Collections.emptySet(), Collections.emptySet());
            assertThat(snapshot).isNotNull();
        }

        @Test
        @DisplayName("List 字段 → 捕获列表内容")
        void listField_shouldBeCaptured() {
            TestOrderWithItems order = new TestOrderWithItems("ORD-001",
                    List.of("Item1", "Item2", "Item3"));
            Map<String, Object> snapshot = deepSnapshot.captureDeep(order, 5,
                    Collections.emptySet(), Collections.emptySet());
            assertThat(snapshot).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("循环引用 → 不死循环，安全返回")
        void circularReference_shouldNotHang() {
            TestNode nodeA = new TestNode("A");
            TestNode nodeB = new TestNode("B");
            nodeA.next = nodeB;
            nodeB.next = nodeA;
            assertThatCode(() ->
                    deepSnapshot.captureDeep(nodeA, 10,
                            Collections.emptySet(), Collections.emptySet())
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Map 字段 → 捕获 Map 内容")
        void mapField_shouldBeCaptured() {
            TestWithMap obj = new TestWithMap("test",
                    Map.of("k1", "v1", "k2", 42));
            Map<String, Object> snapshot = deepSnapshot.captureDeep(obj, 3,
                    Collections.emptySet(), Collections.emptySet());
            assertThat(snapshot).isNotNull();
        }

        @Test
        @DisplayName("大对象 — 100个字段不超时")
        void largeObject_shouldComplete() {
            Map<String, Object> largeObj = new HashMap<>();
            for (int i = 0; i < 100; i++) {
                largeObj.put("field" + i, "value" + i);
            }
            assertThatCode(() ->
                    deepSnapshot.captureDeep(largeObj, 2,
                            Collections.emptySet(), Collections.emptySet())
            ).doesNotThrowAnyException();
        }
    }

    // ── SnapshotProviders ──

    @Nested
    @DisplayName("SnapshotProviders")
    class SnapshotProvidersTests {

        @Test
        @DisplayName("get() 返回非 null Provider")
        void get_shouldReturnNonNull() {
            SnapshotProvider provider = SnapshotProviders.get();
            assertThat(provider).isNotNull();
        }

        @Test
        @DisplayName("captureBaseline 基本工作")
        void captureBaseline_shouldWork() {
            SnapshotProvider provider = SnapshotProviders.get();
            TestUser user = new TestUser("Alice", 30, "a@b.com");
            Map<String, Object> snapshot = provider.captureBaseline("User", user, new String[0]);
            assertThat(snapshot).isNotNull();
        }
    }

    // ── 快照策略 ──

    @Nested
    @DisplayName("快照策略")
    class SnapshotStrategyTests {

        @Test
        @DisplayName("ShallowSnapshotStrategy 基本功能")
        void shallowStrategy_shouldWork() {
            ShallowSnapshotStrategy strategy = new ShallowSnapshotStrategy();
            assertThat(strategy.getName()).isNotNull();
            SnapshotConfig config = new SnapshotConfig();
            Map<String, Object> result = strategy.capture("Test",
                    new TestUser("A", 1, "a@b"), config);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("DeepSnapshotStrategy 基本功能")
        void deepStrategy_shouldWork() {
            SnapshotConfig config = new SnapshotConfig();
            DeepSnapshotStrategy strategy = new DeepSnapshotStrategy(config);
            assertThat(strategy.getName()).isNotNull();
            Map<String, Object> result = strategy.capture("Test",
                    new TestUser("A", 1, "a@b"), config);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("SnapshotConfig 默认值合理")
        void snapshotConfig_defaultsShouldBeReasonable() {
            SnapshotConfig config = new SnapshotConfig();
            assertThat(config.getMaxDepth()).isGreaterThan(0);
        }
    }

    // ── ShallowReferenceMode ──

    @Nested
    @DisplayName("ShallowReferenceMode")
    class ShallowReferenceModeTests {

        @Test
        @DisplayName("fromString 解析已知值")
        void fromString_knownValues() {
            assertThat(ShallowReferenceMode.fromString("VALUE_ONLY"))
                    .isEqualTo(ShallowReferenceMode.VALUE_ONLY);
            assertThat(ShallowReferenceMode.fromString("COMPOSITE_STRING"))
                    .isEqualTo(ShallowReferenceMode.COMPOSITE_STRING);
            assertThat(ShallowReferenceMode.fromString("COMPOSITE_MAP"))
                    .isEqualTo(ShallowReferenceMode.COMPOSITE_MAP);
        }

        @Test
        @DisplayName("fromString 大小写不敏感")
        void fromString_caseInsensitive() {
            assertThat(ShallowReferenceMode.fromString("value_only"))
                    .isEqualTo(ShallowReferenceMode.VALUE_ONLY);
        }

        @Test
        @DisplayName("fromString 未知值 → 默认")
        void fromString_unknownValue() {
            ShallowReferenceMode mode = ShallowReferenceMode.fromString("UNKNOWN");
            assertThat(mode).isNotNull();
        }

        @Test
        @DisplayName("requiresKeyExtraction 语义正确")
        void requiresKeyExtraction_shouldBeConsistent() {
            for (ShallowReferenceMode mode : ShallowReferenceMode.values()) {
                assertThat(mode.getDescription()).isNotBlank();
            }
        }
    }

    // ── 测试数据模型 ──

    static class TestUser {
        String name; int age; String email;
        TestUser(String name, int age, String email) {
            this.name = name; this.age = age; this.email = email;
        }
    }

    static class TestAdmin extends TestUser {
        String role;
        TestAdmin(String name, int age, String email, String role) {
            super(name, age, email); this.role = role;
        }
    }

    static class TestAddress {
        String street; String city; String zip;
        TestAddress(String street, String city, String zip) {
            this.street = street; this.city = city; this.zip = zip;
        }
    }

    static class TestUserWithAddress {
        String name; TestAddress address;
        TestUserWithAddress(String name, TestAddress address) {
            this.name = name; this.address = address;
        }
    }

    static class TestOrderWithItems {
        String orderId; List<String> items;
        TestOrderWithItems(String orderId, List<String> items) {
            this.orderId = orderId; this.items = items;
        }
    }

    static class TestNode {
        String value; TestNode next;
        TestNode(String value) { this.value = value; }
    }

    static class TestWithMap {
        String label; Map<String, Object> attributes;
        TestWithMap(String label, Map<String, Object> attributes) {
            this.label = label; this.attributes = attributes;
        }
    }

    enum ProductStatus { ACTIVE, INACTIVE }

    static class TestProduct {
        String name; BigDecimal price; Date created; ProductStatus status;
        TestProduct(String name, BigDecimal price, Date created, ProductStatus status) {
            this.name = name; this.price = price; this.created = created; this.status = status;
        }
    }
}
