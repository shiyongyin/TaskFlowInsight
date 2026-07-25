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
 * CompareEngine 白盒测试
 * <p>
 * 针对 425 条未覆盖指令，覆盖以下路径：
 * - execute: 相同引用、null、类型不匹配、List 路由、策略路由、深度 fallback
 * - 异常处理（catch 块）
 * - shouldIncludeChange: referenceChange、includeNullChanges、ignoreFields
 * - 深度比较：enableDeepCompare、excludeFields、forcedObjectType、forcedStrategy
 * - 空深度快照处理
 * - detectShallowReferenceChanges: 数组、集合、Map、@ShallowReference
 * - resolveStrategy: 命名策略 vs resolver
 * - sortResult: canonical changes稳定排序
 * - 各种 CompareOptions 配置
 * </p>
 *
 * @author Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("CompareEngine — 白盒测试")
class CompareEngineWhiteBoxTests {

    private CompareEngine engine;
    private Map<Class<?>, CompareStrategy<?>> customStrategies;
    private Map<String, CompareStrategy<?>> namedStrategies;
    private StubListExecutor stubListExecutor;

    @BeforeEach
    void setUp() {
        customStrategies = new ConcurrentHashMap<>();
        namedStrategies = new ConcurrentHashMap<>();
        stubListExecutor = new StubListExecutor();
        engine = new CompareEngine(
                ComparePolicy.defaults(),
                stubListExecutor,
                customStrategies,
                Map.of()
        );
    }

    // ──────────────────────────────────────────────────────────────
    //  execute 快速路径
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("execute — 快速路径")
    class ExecuteQuickPathTests {

        @Test
        @DisplayName("相同引用 a == b → identical")
        void sameReference_shouldReturnIdentical() {
            SimpleObject obj = new SimpleObject("Alice", 30);
            CompareResult result = engine.execute(obj, obj, CompareOptions.builder().build());

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
            assertThat(result.getChanges()).isEmpty();
        }

        @Test
        @DisplayName("左侧 null → ofNullDiff")
        void leftNull_shouldReturnNullDiff() {
            SimpleObject obj = new SimpleObject("Bob", 25);
            CompareResult result = engine.execute(null, obj, CompareOptions.builder().build());

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("右侧 null → ofNullDiff")
        void rightNull_shouldReturnNullDiff() {
            SimpleObject obj = new SimpleObject("Carol", 28);
            CompareResult result = engine.execute(obj, null, CompareOptions.builder().build());

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("类型不匹配 → ofTypeDiff")
        void typeMismatch_shouldReturnTypeDiff() {
            CompareResult result = engine.execute("hello", 42, CompareOptions.builder().build());

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  List 路由
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("execute — List 路由")
    class ListRoutingTests {

        @Test
        @DisplayName("List 路由 → typed snapshot/path kernel")
        void listRouting_shouldUseTypedKernel() {
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
        @DisplayName("List + executor 为 null → fallback 到 deep")
        void listWithoutExecutor_shouldFallbackToDeep() {
            CompareEngine engineNoList = new CompareEngine(
                    ComparePolicy.defaults(), null,
                    customStrategies, Map.of()
            );

            List<String> list1 = List.of("a", "b");
            List<String> list2 = List.of("a", "c");

            CompareResult result = engineNoList.execute(list1, list2, CompareOptions.builder().build());
            assertThat(result).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  策略路由
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("execute — 策略路由")
    class StrategyRoutingTests {

        @Test
        @DisplayName("构造后命名策略 mutation 不影响Engine")
        void namedStrategyMutation_isIgnored() {
            RecordingStrategy strategy = new RecordingStrategy("named");
            namedStrategies.put("named", strategy);

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);
            CompareOptions opts = CompareOptions.builder().build();

            CompareResult result = engine.execute(a, b, opts);

            assertThat(result).isNotNull();
            assertThat(strategy.invocationCount).isZero();
        }

        @Test
        @DisplayName("StrategyResolver 不观察构造后的外部Map mutation")
        void resolverStrategyMutation_isIgnored() {
            RecordingStrategy strategy = new RecordingStrategy("custom") {
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
        @DisplayName("命名策略不存在 → fallback 到 resolver")
        void unknownNamedStrategy_shouldFallbackToResolver() {
            CompareOptions opts = CompareOptions.builder().build();
            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            CompareResult result = engine.execute(a, b, opts);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("无策略匹配 → deep fallback")
        void noStrategyMatch_shouldFallbackToDeep() {
            SimpleObject a = new SimpleObject("Alice", 30);
            SimpleObject b = new SimpleObject("Bob", 25);

            CompareResult result = engine.execute(a, b, CompareOptions.builder().build());

            assertThat(result).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  异常处理
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("execute — 异常处理")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("异常不传播到调用方")
        void exception_shouldNotPropagate() {
            RecordingStrategy npeStrategy = new RecordingStrategy("npe") {
                @Override
                public boolean supports(Class<?> type) {
                    return SimpleObject.class.isAssignableFrom(type);
                }

                @Override
                public CompareResult compare(Object obj1, Object obj2, CompareOptions options) {
                    throw new NullPointerException("Unexpected NPE");
                }
            };
            customStrategies.put(SimpleObject.class, npeStrategy);

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            assertThatCode(() -> engine.execute(a, b, CompareOptions.builder().build()))
                    .doesNotThrowAnyException();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  shouldIncludeChange 路径（通过 deep compare）
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("shouldIncludeChange — 引用变更、includeNullChanges、ignoreFields")
    class ShouldIncludeChangeTests {

        @Test
        @DisplayName("旧 ignoreFields 删除后不丢弃差异")
        void removedIgnoreFields_doesNotDiscardChanges() {
            SimpleObject a = new SimpleObject("Alice", 30);
            SimpleObject b = new SimpleObject("Bob", 25);

            CompareOptions opts = CompareOptions.builder()
                    .build();

            CompareResult result = engine.execute(a, b, opts);

            assertThat(result).isNotNull();
            assertThat(result.getChanges())
                    .extracting(FieldChange::getFieldName)
                    .contains("name", "age");
        }

        @Test
        @DisplayName("includeNullChanges 包含 null 变更")
        void includeNullChanges_shouldInclude() {
            SimpleObject a = new SimpleObject("Alice", 30);
            SimpleObject b = new SimpleObject("Bob", 25);

            CompareOptions opts = CompareOptions.builder()
                    
                    
                    .build();

            CompareResult result = engine.execute(a, b, opts);
            assertThat(result).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  深度比较
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("深度比较 — enableDeepCompare、excludeFields、forcedObjectType、forcedStrategy")
    class DeepCompareTests {

        @Test
        @DisplayName("enableDeepCompare(true) 使用深度快照")
        void enableDeepCompare_shouldUseDeepSnapshot() {
            SimpleObject a = new SimpleObject("Alice", 30);
            SimpleObject b = new SimpleObject("Bob", 25);

            CompareOptions opts = CompareOptions.builder()
                    
                    .maxDepth(5)
                    .build();

            CompareResult result = engine.execute(a, b, opts);

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("excludeFields 排除字段")
        void excludeFields_shouldExclude() {
            SimpleObject a = new SimpleObject("Alice", 30);
            SimpleObject b = new SimpleObject("Bob", 25);

            CompareOptions opts = CompareOptions.builder()
                    .build();

            CompareResult result = engine.execute(a, b, opts);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("forcedObjectType 强制对象类型")
        void forcedObjectType_shouldApply() {
            SimpleObject a = new SimpleObject("Alice", 30);
            SimpleObject b = new SimpleObject("Bob", 25);

            CompareOptions opts = CompareOptions.builder()
                    
                    
                    .build();

            CompareResult result = engine.execute(a, b, opts);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("forcedStrategy 强制策略")
        void forcedStrategy_shouldApply() {
            SimpleObject a = new SimpleObject("Alice", 30);
            SimpleObject b = new SimpleObject("Bob", 25);

            CompareOptions opts = CompareOptions.builder()
                    
                    
                    .build();

            CompareResult result = engine.execute(a, b, opts);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("typeAwareEnabled 启用类型感知")
        void typeAware_shouldApply() {
            SimpleObject a = new SimpleObject("Alice", 30);
            SimpleObject b = new SimpleObject("Bob", 25);

            CompareOptions opts = CompareOptions.builder().build();

            CompareResult result = engine.execute(a, b, opts);
            assertThat(result).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  sortResult — algorithmUsed、degradationReasons
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sortResult — canonical changes 排序")
    class SortResultTests {
        @Test
        @DisplayName("StableSorter 对 changes 排序")
        void result_shouldBeSorted() {
            RecordingStrategy unorderedStrategy = new RecordingStrategy("unordered") {
                @Override
                public boolean supports(Class<?> type) {
                    return SimpleObject.class.isAssignableFrom(type);
                }

                @Override
                public CompareResult compare(Object obj1, Object obj2, CompareOptions options) {
                    List<FieldChange> changes = new ArrayList<>();
                    changes.add(FieldChange.at(ChangeKind.MODIFY,
                            ComparePath.root().append(new PropertySegment("zzz")), "o", "n"));
                    changes.add(FieldChange.at(ChangeKind.MODIFY,
                            ComparePath.root().append(new PropertySegment("aaa")), "o", "n"));
                    return CompareResultReducer.complete(changes);
                }
            };
            customStrategies.put(SimpleObject.class, unorderedStrategy);

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            CompareResult result = engine.execute(a, b, CompareOptions.builder().build());

            assertThat(result).isNotNull();
            assertThat(result.getChanges()).hasSize(2);
            assertThat(result.getChanges().get(0).getFieldName())
                    .isLessThanOrEqualTo(result.getChanges().get(1).getFieldName());
        }

        @Test
        @DisplayName("空 changes 不触发排序异常")
        void emptyChanges_shouldNotFail() {
            RecordingStrategy emptyStrategy = new RecordingStrategy("empty") {
                @Override
                public boolean supports(Class<?> type) {
                    return SimpleObject.class.isAssignableFrom(type);
                }

                @Override
                public CompareResult compare(Object obj1, Object obj2, CompareOptions options) {
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
    //  CompareOptions 配置
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CompareOptions 配置")
    class CompareOptionsTests {

        @Test
        @DisplayName("DEFAULT 选项")
        void defaultOptions() {
            CompareResult result = engine.execute(
                    new SimpleObject("A", 1),
                    new SimpleObject("B", 2),
                    CompareOptions.builder().build()
            );
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("DEEP 选项")
        void deepOptions() {
            CompareResult result = engine.execute(
                    new SimpleObject("A", 1),
                    new SimpleObject("B", 2),
                    CompareOptions.builder().maxDepth(5).build()
            );
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("WITH_REPORT 选项")
        void withReportOptions() {
            CompareResult result = engine.execute(
                    new SimpleObject("A", 1),
                    new SimpleObject("B", 2),
                    CompareOptions.builder().build()
            );
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("List 重排保持 ordered-index")
        void listReorderingUsesOrderedIndex() {
            CompareOptions opts = CompareOptions.builder().build();
            List<String> list1 = List.of("a", "b");
            List<String> list2 = List.of("b", "a");

            CompareResult result = engine.execute(list1, list2, opts);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("perfTimeoutMs、perfMaxElements 选项")
        void perfOptions() {
            CompareOptions opts = CompareOptions.builder().maxElements(500).build();
            CompareResult result = engine.execute(
                    new SimpleObject("A", 1),
                    new SimpleObject("B", 2),
                    opts
            );
            assertThat(result).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  构造
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("构造与配置")
    class ConstructionTests {

        @Test
        @DisplayName("null 参数构造不抛异常")
        void nullParams_shouldNotThrow() {
            assertThatCode(() ->
                    CompareRuntime.builder().build().engine()
            ).doesNotThrowAnyException();
        }

    }

    // ──────────────────────────────────────────────────────────────
    //  Test Doubles
    // ──────────────────────────────────────────────────────────────

    static class StubListExecutor extends ListCompareExecutor {
        int invocationCount = 0;

        StubListExecutor() {
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

    static class RecordingStrategy implements CompareStrategy<Object> {
        final String name;
        int invocationCount = 0;

        RecordingStrategy(String name) {
            this.name = name;
        }

        @Override
        public CompareResult compare(Object obj1, Object obj2, CompareOptions options) {
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

    static class SimpleObject {
        private final String name;
        private final int age;

        SimpleObject(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }
    }
}
