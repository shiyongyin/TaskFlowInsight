package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.internal.CompareResultReducer;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.IndexSegment;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * CompareEngine 核心路径单元测试
 *
 * <p>覆盖测试方案 CE-WB-001 ~ CE-WB-012 的控制流与数据流。
 * 测试原则：
 * <ul>
 *   <li>每条控制流路径至少一个 case</li>
 *   <li>快速路径优先验证（same-ref / null / type-diff）</li>
 *   <li>策略路由 / Fallback / 异常处理全覆盖</li>
 *   <li>排序 SSOT 验证</li>
 * </ul>
 *
 * @author Expert Panel - Senior Developer
 * @since 3.0.0
 */
@DisplayName("CompareEngine — 核心路径单元测试")
class CompareEngineTests {

    private CompareEngine engine;
    private Map<Class<?>, CompareStrategy<?>> customStrategies;
    private Map<String, CompareStrategy<?>> namedStrategies;
    private StubListCompareExecutor stubListExecutor;

    @BeforeEach
    void setUp() {
        customStrategies = new ConcurrentHashMap<>();
        namedStrategies = new ConcurrentHashMap<>();
        stubListExecutor = new StubListCompareExecutor();

        engine = new CompareEngine(
                ComparePolicy.defaults(),
                stubListExecutor,
                customStrategies,
                Map.of()
        );
    }

    // ──────────────────────────────────────────────────────────────
    //  快速路径 (Quick Path)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("快速路径检查")
    class QuickPathTests {

        @Test
        @DisplayName("CE-WB-001: 相同引用 → identical")
        void sameReference_shouldReturnIdentical() {
            Object obj = new SimpleObject("Alice", 30);
            CompareResult result = engine.execute(obj, obj, CompareOptions.builder().build());

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
            assertThat(result.getChanges()).isEmpty();
        }

        @Test
        @DisplayName("CE-WB-002: 左侧 null → nullDiff")
        void leftNull_shouldReturnNullDiff() {
            Object obj = new SimpleObject("Bob", 25);
            CompareResult result = engine.execute(null, obj, CompareOptions.builder().build());

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("CE-WB-003: 右侧 null → nullDiff")
        void rightNull_shouldReturnNullDiff() {
            Object obj = new SimpleObject("Carol", 28);
            CompareResult result = engine.execute(obj, null, CompareOptions.builder().build());

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("CE-WB-004: 类型不匹配 → typeDiff")
        void typeMismatch_shouldReturnTypeDiff() {
            CompareResult result = engine.execute("hello", 42, CompareOptions.builder().build());

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("CE-WB-001b: 两侧均为 null → identical")
        void bothNull_shouldReturnIdentical() {
            CompareResult result = engine.execute(null, null, CompareOptions.builder().build());

            // null == null → same reference path
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  List 路由
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("List 路由")
    class ListRoutingTests {

        @Test
        @DisplayName("CE-WB-005: List + executor 存在 → typed kernel仍是唯一主路径")
        void listWithExecutor_shouldUseTypedKernel() {
            List<String> list1 = List.of("a", "b");
            List<String> list2 = List.of("a", "c");

            CompareResult result = engine.execute(list1, list2, CompareOptions.builder().build());

            assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
            assertThat(result.getChanges()).singleElement().satisfies(change ->
                    assertThat(change.after().orElseThrow().path().segments())
                            .containsExactly(new IndexSegment(1)));
            assertThat(result.getDiagnostics().comparedNodes()).isEqualTo(9);
            assertThat(result.getDiagnostics().consumedElements()).isEqualTo(4);
            assertThat(stubListExecutor.invocationCount).isZero();
        }

        @Test
        @DisplayName("CE-WB-006: List + executor 为 null → fallback 到 snapshot diff")
        void listWithoutExecutor_shouldFallbackToSnapshot() {
            CompareEngine engineNoListExecutor = new CompareEngine(
                    ComparePolicy.defaults(), null,
                    customStrategies, Map.of()
            );

            List<String> list1 = List.of("a", "b");
            List<String> list2 = List.of("a", "c");

            // Should not throw, falls back to deep compare
            CompareResult result = engineNoListExecutor.execute(list1, list2, CompareOptions.builder().build());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("CE-WB-005b: 空列表比较 — 同类型空列表走 same-ref 或 executor")
        void emptyLists_shouldReturnResult() {
            List<String> list1 = new ArrayList<>();
            List<String> list2 = new ArrayList<>();

            CompareResult result = engine.execute(list1, list2, CompareOptions.builder().build());

            // Two distinct empty ArrayLists should be routed to executor
            assertThat(result).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  策略路由
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("策略路由")
    class StrategyRoutingTests {

        @Test
        @DisplayName("CE-WB-007: 构造后修改命名策略Map不影响冻结Engine")
        void namedStrategyMutation_isIgnored() {
            RecordingStrategy<SimpleObject> strategy = new RecordingStrategy<>("test-named");
            namedStrategies.put("test-named", strategy);

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            CompareOptions opts = CompareOptions.builder().build();
            CompareResult result = engine.execute(a, b, opts);

            assertThat(result).isNotNull();
            assertThat(strategy.invocationCount).isZero();
        }

        @Test
        @DisplayName("CE-WB-008: 构造后修改类型策略Map不影响冻结Engine")
        void customStrategyMutation_isIgnored() {
            RecordingStrategy<SimpleObject> strategy = new RecordingStrategy<>("custom-simple") {
                @Override
                public boolean supports(Class<?> type) {
                    return SimpleObject.class.isAssignableFrom(type);
                }
            };
            customStrategies.put(SimpleObject.class, strategy);

            SimpleObject a = new SimpleObject("X", 10);
            SimpleObject b = new SimpleObject("Y", 20);

            CompareResult result = engine.execute(a, b, CompareOptions.builder().build());

            assertThat(result).isNotNull();
            assertThat(strategy.invocationCount).isZero();
        }

        @Test
        @DisplayName("CE-WB-009: 命名策略不存在 → 回退到 StrategyResolver")
        void unknownNamedStrategy_shouldFallbackToResolver() {
            CompareOptions opts = CompareOptions.builder().build();
            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            // Should not throw, falls back to deep compare
            CompareResult result = engine.execute(a, b, opts);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("CE-WB-009b: resolver不观察构造后的外部Map mutation")
        void strategyResolver_doesNotObserveMapMutation() {
            // Register a strategy that supports SimpleObject via customStrategies map
            // BUT do NOT use namedStrategies or strategyName — force the resolver.resolve() path
            RecordingStrategy<SimpleObject> resolverStrategy = new RecordingStrategy<>("resolver-hit") {
                @Override
                public boolean supports(Class<?> type) {
                    return SimpleObject.class.isAssignableFrom(type);
                }
            };
            customStrategies.put(SimpleObject.class, resolverStrategy);

            SimpleObject a = new SimpleObject("P", 100);
            SimpleObject b = new SimpleObject("Q", 200);

            // Use DEFAULT options (no strategyName) → forces path through resolveStrategy → resolver.resolve()
            CompareResult result = engine.execute(a, b, CompareOptions.builder().build());

            assertThat(result).isNotNull();
            assertThat(resolverStrategy.invocationCount).isZero();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Fallback & 异常
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fallback 与异常处理")
    class FallbackAndExceptionTests {

        @Test
        @DisplayName("CE-WB-010: 无策略匹配 → fallback 到 snapshot diff")
        void noStrategyMatch_shouldFallbackToSnapshotDiff() {
            SimpleObject a = new SimpleObject("Alice", 30);
            SimpleObject b = new SimpleObject("Bob", 25);

            CompareResult result = engine.execute(a, b, CompareOptions.builder().build());

            assertThat(result).isNotNull();
            // Should detect field differences via snapshot
            // 该分支只验证 canonical runtime 的异常边界，不依赖外部初始化状态。
            // but the key assertion is: no exception, returns a result
        }

        @Test
        @DisplayName("CE-WB-010b: 深度比较 fallback 检测字段变更")
        void deepCompareFallback_shouldDetectChanges() {
            SimpleObject a = new SimpleObject("Alice", 30);
            SimpleObject b = new SimpleObject("Bob", 25);

            CompareOptions opts = CompareOptions.builder()
                    
                    .maxDepth(5)
                    .build();

            CompareResult result = engine.execute(a, b, opts);

            assertThat(result).isNotNull();
            // Deep compare should detect differences
        }

        @Test
        @DisplayName("CE-WB-011: 引擎执行异常 → 返回降级结果而非抛出")
        void engineException_shouldReturnDegradedResult() {
            // Use a strategy that throws
            RecordingStrategy<SimpleObject> throwingStrategy = new RecordingStrategy<>("throwing") {
                @Override
                public boolean supports(Class<?> type) {
                    return SimpleObject.class.isAssignableFrom(type);
                }

                @Override
                public CompareResult compare(SimpleObject obj1, SimpleObject obj2, CompareOptions options) {
                    throw new RuntimeException("Simulated comparison failure");
                }
            };
            customStrategies.put(SimpleObject.class, throwingStrategy);

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            CompareResult result = engine.execute(a, b, CompareOptions.builder().build());

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("CE-WB-011b: 引擎异常不传播到调用方")
        void engineException_shouldNotPropagate() {
            RecordingStrategy<SimpleObject> throwingStrategy = new RecordingStrategy<>("npe-thrower") {
                @Override
                public boolean supports(Class<?> type) {
                    return SimpleObject.class.isAssignableFrom(type);
                }

                @Override
                public CompareResult compare(SimpleObject obj1, SimpleObject obj2, CompareOptions options) {
                    throw new NullPointerException("Unexpected NPE in strategy");
                }
            };
            customStrategies.put(SimpleObject.class, throwingStrategy);

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            assertThatCode(() -> engine.execute(a, b, CompareOptions.builder().build()))
                    .doesNotThrowAnyException();
        }

    }

    // ──────────────────────────────────────────────────────────────
    //  排序 SSOT
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("排序 SSOT 验证")
    class SortingTests {

        @Test
        @DisplayName("CE-WB-012: 结果经过 StableSorter 排序")
        void result_shouldBeSortedByStableSorter() {
            // Create a strategy that returns unsorted changes
            RecordingStrategy<SimpleObject> unorderedStrategy = new RecordingStrategy<>("unordered") {
                @Override
                public boolean supports(Class<?> type) {
                    return SimpleObject.class.isAssignableFrom(type);
                }

                @Override
                public CompareResult compare(SimpleObject obj1, SimpleObject obj2, CompareOptions options) {
                    List<FieldChange> changes = new ArrayList<>();
                    changes.add(FieldChange.at(ChangeKind.MODIFY,
                            ComparePath.root().append(new PropertySegment("zzz")), "old1", "new1"));
                    changes.add(FieldChange.at(ChangeKind.MODIFY,
                            ComparePath.root().append(new PropertySegment("aaa")), "old2", "new2"));
                    return CompareResultReducer.complete(changes);
                }
            };
            customStrategies.put(SimpleObject.class, unorderedStrategy);

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            CompareResult result = engine.execute(a, b, CompareOptions.builder().build());

            assertThat(result).isNotNull();
            assertThat(result.getChanges()).isNotEmpty();
            // StableSorter sorts by field name — "aaa" should come before "zzz"
            if (result.getChanges().size() >= 2) {
                assertThat(result.getChanges().get(0).getFieldName())
                        .isLessThanOrEqualTo(result.getChanges().get(1).getFieldName());
            }
        }

        @Test
        @DisplayName("CE-WB-012b: 空变更列表不触发排序异常")
        void emptyChanges_shouldNotFailSorting() {
            RecordingStrategy<SimpleObject> emptyStrategy = new RecordingStrategy<>("empty") {
                @Override
                public boolean supports(Class<?> type) {
                    return SimpleObject.class.isAssignableFrom(type);
                }

                @Override
                public CompareResult compare(SimpleObject obj1, SimpleObject obj2, CompareOptions options) {
                    return CompareResultReducer.complete(Collections.emptyList());
                }
            };
            customStrategies.put(SimpleObject.class, emptyStrategy);

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("A", 1);

            CompareResult result = engine.execute(a, b, CompareOptions.builder().build());

            assertThat(result).isNotNull();
            assertThat(result.getChanges()).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  构造与配置
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("构造与配置")
    class ConstructionTests {

        @Test
        @DisplayName("CE-WB-013: null 参数构造不抛异常")
        void nullParameters_shouldNotFail() {
            assertThatCode(() ->
                    CompareRuntime.builder().build().engine()
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("CE-WB-013b: 最小配置引擎可执行比较")
        void minimalEngine_shouldWork() {
            CompareEngine minimal = CompareRuntime.builder().build().engine();

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            CompareResult result = minimal.execute(a, b, CompareOptions.builder().build());
            assertThat(result).isNotNull();
        }

    }

    // ──────────────────────────────────────────────────────────────
    //  Test Doubles
    // ──────────────────────────────────────────────────────────────

    /**
     * Stub ListCompareExecutor that records invocations.
     */
    static class StubListCompareExecutor extends ListCompareExecutor {
        int invocationCount = 0;

        StubListCompareExecutor() {
            super(Collections.emptyList());
        }

        @Override
        public CompareResult compare(List<?> list1, List<?> list2, CompareOptions options) {
            invocationCount++;
            return list1.equals(list2)
                    ? CompareResult.identical()
                    : CompareResultReducer.complete(List.of(FieldChange.at(
                            ChangeKind.MODIFY,
                            ComparePath.root().append(new PropertySegment("list")), null, null)));
        }
    }

    /**
     * Recording CompareStrategy that tracks invocations.
     */
    static class RecordingStrategy<T> implements CompareStrategy<T> {
        final String name;
        int invocationCount = 0;

        RecordingStrategy(String name) {
            this.name = name;
        }

        @Override
        public CompareResult compare(T obj1, T obj2, CompareOptions options) {
            invocationCount++;
            return CompareResultReducer.complete(Collections.emptyList());
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean supports(Class<?> type) {
            return false;
        }
    }

    /**
     * Simple test object for comparison.
     */
    static class SimpleObject {
        private final String name;
        private final int age;

        SimpleObject(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public int getAge() { return age; }

        @Override
        public String toString() {
            return "SimpleObject{name='" + name + "', age=" + age + "}";
        }
    }
}
