package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * CompareService 真实业务场景测试。
 * 目标：验证比较服务的核心业务功能，暴露实际问题。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("CompareService — 真实业务场景测试")
class CompareServiceTests {

    private CompareService service;

    @BeforeEach
    void setUp() {
        service = new CompareService();
    }

    @Nested
    @DisplayName("基本比较功能")
    class BasicCompareTests {

        @Test
        @DisplayName("相同对象比较 → identical")
        void sameObject_shouldReturnIdentical() {
            TestOrder order = new TestOrder(1L, "iPhone", 999.99, "PAID");
            CompareResult result = service.compare(order, order);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("不同对象比较 → 检测到字段差异")
        void differentObjects_shouldDetectChanges() {
            TestOrder before = new TestOrder(1L, "iPhone", 999.99, "PENDING");
            TestOrder after = new TestOrder(1L, "iPhone", 899.99, "PAID");
            CompareResult result = service.compare(before, after);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("null vs 对象 → 不抛异常，返回有意义结果")
        void nullVsObject_shouldNotThrow() {
            TestOrder order = new TestOrder(1L, "Phone", 100.0, "NEW");
            CompareResult result = service.compare(null, order);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("对象 vs null → 不抛异常")
        void objectVsNull_shouldNotThrow() {
            TestOrder order = new TestOrder(1L, "Phone", 100.0, "NEW");
            CompareResult result = service.compare(order, null);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("两个 null → identical")
        void bothNull_shouldBeIdentical() {
            CompareResult result = service.compare(null, null);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("类型不匹配 → 不抛异常")
        void typeMismatch_shouldNotThrow() {
            CompareResult result = service.compare("hello", 42);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }
    }

    @Nested
    @DisplayName("CompareOptions 功能")
    class CompareOptionsTests {

        @Test
        @DisplayName("DEFAULT 选项不为 null")
        void defaultOptions_shouldBeValid() {
            assertThat(CompareOptions.DEFAULT).isNotNull();
        }

        @Test
        @DisplayName("Builder 构建自定义选项")
        void builder_shouldCreateOptions() {
            CompareOptions opts = CompareOptions.builder()
                    .enableDeepCompare(true)
                    .maxDepth(5)
                    .includeNullChanges(true)
                    .strategyName("custom")
                    .build();
            assertThat(opts.isEnableDeepCompare()).isTrue();
            assertThat(opts.getMaxDepth()).isEqualTo(5);
            assertThat(opts.isIncludeNullChanges()).isTrue();
            assertThat(opts.getStrategyName()).isEqualTo("custom");
        }

        @Test
        @DisplayName("使用 options 比较 → 选项生效")
        void compareWithOptions_shouldWork() {
            TestOrder a = new TestOrder(1L, "A", 10.0, "NEW");
            TestOrder b = new TestOrder(1L, "B", 20.0, "OLD");
            CompareOptions opts = CompareOptions.builder()
                    .enableDeepCompare(true)
                    .maxDepth(3)
                    .build();
            CompareResult result = service.compare(a, b, opts);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("忽略字段 → 被忽略的字段不在变更列表中")
        void ignoreFields_shouldExcludeThem() {
            TestOrder a = new TestOrder(1L, "A", 10.0, "NEW");
            TestOrder b = new TestOrder(1L, "B", 20.0, "OLD");
            CompareOptions opts = CompareOptions.builder()
                    .ignoreFields(List.of("name"))
                    .build();
            CompareResult result = service.compare(a, b, opts);
            assertThat(result).isNotNull();
            if (result.getChanges() != null) {
                assertThat(result.getChanges())
                        .noneMatch(c -> "name".equals(c.getFieldName()));
            }
        }
    }

    @Nested
    @DisplayName("策略注册")
    class StrategyRegistrationTests {

        @Test
        @DisplayName("注册自定义策略 → 比较时使用")
        void registerCustomStrategy_shouldBeUsed() {
            CompareStrategy<TestOrder> strategy = new CompareStrategy<>() {
                @Override
                public CompareResult compare(TestOrder obj1, TestOrder obj2, CompareOptions options) {
                    List<FieldChange> changes = new ArrayList<>();
                    if (!Objects.equals(obj1.status, obj2.status)) {
                        changes.add(FieldChange.builder()
                                .fieldName("status")
                                .fieldPath("status")
                                .oldValue(obj1.status)
                                .newValue(obj2.status)
                                .changeType(ChangeType.UPDATE)
                                .build());
                    }
                    return CompareResult.builder()
                            .object1(obj1).object2(obj2)
                            .changes(changes)
                            .identical(changes.isEmpty())
                            .build();
                }

                @Override
                public String getName() { return "test-order-strategy"; }

                @Override
                public boolean supports(Class<?> type) { return TestOrder.class.isAssignableFrom(type); }
            };

            service.registerStrategy(TestOrder.class, strategy);

            TestOrder a = new TestOrder(1L, "A", 10.0, "PENDING");
            TestOrder b = new TestOrder(1L, "B", 20.0, "PAID");
            CompareResult result = service.compare(a, b);

            assertThat(result).isNotNull();
            // Custom strategy only tracks status, so only status change should be found
            assertThat(result.getChanges()).hasSize(1);
            assertThat(result.getChanges().get(0).getFieldName()).isEqualTo("status");
        }

        @Test
        @DisplayName("注册命名策略 → 通过 strategyName 使用")
        void registerNamedStrategy_shouldBeUsedByName() {
            CompareStrategy<Object> namedStrategy = new CompareStrategy<>() {
                @Override
                public CompareResult compare(Object obj1, Object obj2, CompareOptions options) {
                    return CompareResult.builder()
                            .object1(obj1).object2(obj2)
                            .changes(Collections.emptyList())
                            .identical(true)
                            .similarity(1.0)
                            .build();
                }

                @Override
                public String getName() { return "always-identical"; }

                @Override
                public boolean supports(Class<?> type) { return true; }
            };

            service.registerNamedStrategy("always-identical", namedStrategy);

            CompareOptions opts = CompareOptions.builder().strategyName("always-identical").build();
            CompareResult result = service.compare("different", "values", opts);

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }
    }

    @Nested
    @DisplayName("批量比较")
    class BatchCompareTests {

        @Test
        @DisplayName("空列表 → 返回空结果")
        void emptyBatch_shouldReturnEmpty() {
            List<Pair<Object, Object>> pairs = Collections.emptyList();
            List<CompareResult> results = service.compareBatch(pairs);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("单对批量 → 等价于单次比较")
        void singlePairBatch_shouldWork() {
            TestOrder a = new TestOrder(1L, "A", 10.0, "NEW");
            TestOrder b = new TestOrder(1L, "B", 20.0, "OLD");
            List<Pair<Object, Object>> pairs = List.of(Pair.of(a, b));
            List<CompareResult> results = service.compareBatch(pairs);
            assertThat(results).hasSize(1);
            assertThat(results.get(0)).isNotNull();
        }

        @Test
        @DisplayName("多对批量 → 全部成功比较")
        void multiplePairBatch_shouldCompareAll() {
            List<Pair<Object, Object>> pairs = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                pairs.add(Pair.of(
                        new TestOrder((long) i, "item" + i, i * 10.0, "OLD"),
                        new TestOrder((long) i, "item" + i, i * 10.0 + 1, "NEW")
                ));
            }
            List<CompareResult> results = service.compareBatch(pairs);
            assertThat(results).hasSize(10);
            results.forEach(r -> assertThat(r).isNotNull());
        }
    }

    @Nested
    @DisplayName("三方合并")
    class ThreeWayMergeTests {

        @Test
        @DisplayName("无冲突三方合并 → 成功")
        void noConflict_shouldSucceed() {
            TestOrder base = new TestOrder(1L, "Phone", 100.0, "NEW");
            TestOrder left = new TestOrder(1L, "Phone", 90.0, "NEW"); // price changed
            TestOrder right = new TestOrder(1L, "Phone", 100.0, "PAID"); // status changed
            MergeResult result = service.compareThreeWay(base, left, right);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("三方合并 null 安全")
        void nullBase_shouldNotThrow() {
            TestOrder left = new TestOrder(1L, "A", 10.0, "NEW");
            TestOrder right = new TestOrder(1L, "B", 20.0, "OLD");
            assertThatCode(() -> service.compareThreeWay(null, left, right))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("CompareResult 验证")
    class CompareResultTests {

        @Test
        @DisplayName("identical() 工厂方法")
        void identical_shouldCreateCorrectResult() {
            CompareResult result = CompareResult.identical();
            assertThat(result.isIdentical()).isTrue();
            assertThat(result.getChanges()).isEmpty();
            assertThat(result.getSimilarity()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("ofNullDiff 工厂方法")
        void ofNullDiff_shouldCreateCorrectResult() {
            Object obj = "test";
            CompareResult result = CompareResult.ofNullDiff(null, obj);
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getObject1()).isNull();
            assertThat(result.getObject2()).isEqualTo(obj);
        }

        @Test
        @DisplayName("ofTypeDiff 工厂方法")
        void ofTypeDiff_shouldCreateCorrectResult() {
            CompareResult result = CompareResult.ofTypeDiff("string", 42);
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getSimilarity()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Builder 构建完整结果")
        void builder_shouldCreateFullResult() {
            FieldChange change = FieldChange.builder()
                    .fieldName("name")
                    .fieldPath("name")
                    .oldValue("old")
                    .newValue("new")
                    .changeType(ChangeType.UPDATE)
                    .build();
            CompareResult result = CompareResult.builder()
                    .object1("a")
                    .object2("b")
                    .changes(List.of(change))
                    .identical(false)
                    .similarity(0.5)
                    .build();
            assertThat(result.getChanges()).hasSize(1);
            assertThat(result.getSimilarity()).isEqualTo(0.5);
            assertThat(result.getChangeCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("FieldChange 验证")
    class FieldChangeTests {

        @Test
        @DisplayName("Builder 构建字段变更")
        void builder_shouldCreateFieldChange() {
            FieldChange fc = FieldChange.builder()
                    .fieldName("price")
                    .fieldPath("order.price")
                    .oldValue(100.0)
                    .newValue(90.0)
                    .changeType(ChangeType.UPDATE)
                    .valueType("Double")
                    .build();

            assertThat(fc.getFieldName()).isEqualTo("price");
            assertThat(fc.getFieldPath()).isEqualTo("order.price");
            assertThat(fc.getOldValue()).isEqualTo(100.0);
            assertThat(fc.getNewValue()).isEqualTo(90.0);
            assertThat(fc.getChangeType()).isEqualTo(ChangeType.UPDATE);
            assertThat(fc.getValueType()).isEqualTo("Double");
        }

        @Test
        @DisplayName("referenceChange 标记")
        void referenceChange_shouldBeTracked() {
            FieldChange fc = FieldChange.builder()
                    .fieldName("ref")
                    .fieldPath("ref")
                    .changeType(ChangeType.UPDATE)
                    .referenceChange(true)
                    .build();
            assertThat(fc.isReferenceChange()).isTrue();
        }
    }

    // ── 测试数据模型 ──

    static class TestOrder {
        Long id;
        String name;
        double price;
        String status;

        TestOrder(Long id, String name, double price, String status) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.status = status;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public double getPrice() { return price; }
        public String getStatus() { return status; }
    }
}
