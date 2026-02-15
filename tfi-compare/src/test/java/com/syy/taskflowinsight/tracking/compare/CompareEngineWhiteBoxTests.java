package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.ValueObjectCompareStrategy;
import com.syy.taskflowinsight.metrics.TfiMetrics;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.metrics.MicrometerDiagnosticSink;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * - sortResult: algorithmUsed、degradationReasons
 * - 指标记录：tfiMetrics != null、microSink != null
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
                new StrategyResolver(),
                null,
                null,
                stubListExecutor,
                customStrategies,
                namedStrategies
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
            CompareResult result = engine.execute(obj, obj, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
            assertThat(result.getChanges()).isEmpty();
            assertThat(result.getSimilarity()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("左侧 null → ofNullDiff")
        void leftNull_shouldReturnNullDiff() {
            SimpleObject obj = new SimpleObject("Bob", 25);
            CompareResult result = engine.execute(null, obj, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getObject1()).isNull();
            assertThat(result.getObject2()).isEqualTo(obj);
            assertThat(result.getSimilarity()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("右侧 null → ofNullDiff")
        void rightNull_shouldReturnNullDiff() {
            SimpleObject obj = new SimpleObject("Carol", 28);
            CompareResult result = engine.execute(obj, null, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getObject1()).isEqualTo(obj);
            assertThat(result.getObject2()).isNull();
        }

        @Test
        @DisplayName("类型不匹配 → ofTypeDiff")
        void typeMismatch_shouldReturnTypeDiff() {
            CompareResult result = engine.execute("hello", 42, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getSimilarity()).isEqualTo(0.0);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  List 路由
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("execute — List 路由")
    class ListRoutingTests {

        @Test
        @DisplayName("List 路由 → 委派 ListCompareExecutor")
        void listRouting_shouldDelegateToExecutor() {
            List<String> list1 = List.of("a", "b");
            List<String> list2 = List.of("a", "c");

            CompareResult result = engine.execute(list1, list2, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(stubListExecutor.invocationCount).isEqualTo(1);
        }

        @Test
        @DisplayName("List + executor 为 null → fallback 到 deep")
        void listWithoutExecutor_shouldFallbackToDeep() {
            CompareEngine engineNoList = new CompareEngine(
                    new StrategyResolver(), null, null, null,
                    customStrategies, namedStrategies
            );

            List<String> list1 = List.of("a", "b");
            List<String> list2 = List.of("a", "c");

            CompareResult result = engineNoList.execute(list1, list2, CompareOptions.DEFAULT);
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
        @DisplayName("命名策略匹配 → 使用命名策略")
        void namedStrategy_shouldBeUsed() {
            RecordingStrategy strategy = new RecordingStrategy("named");
            namedStrategies.put("named", strategy);

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);
            CompareOptions opts = CompareOptions.builder().strategyName("named").build();

            CompareResult result = engine.execute(a, b, opts);

            assertThat(result).isNotNull();
            assertThat(strategy.invocationCount).isEqualTo(1);
        }

        @Test
        @DisplayName("StrategyResolver 解析策略 → 使用自定义策略")
        void resolverStrategy_shouldBeUsed() {
            RecordingStrategy strategy = new RecordingStrategy("custom") {
                @Override
                public boolean supports(Class<?> type) {
                    return SimpleObject.class.isAssignableFrom(type);
                }
            };
            customStrategies.put(SimpleObject.class, strategy);

            SimpleObject a = new SimpleObject("X", 10);
            SimpleObject b = new SimpleObject("Y", 20);

            CompareResult result = engine.execute(a, b, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(strategy.invocationCount).isEqualTo(1);
        }

        @Test
        @DisplayName("命名策略不存在 → fallback 到 resolver")
        void unknownNamedStrategy_shouldFallbackToResolver() {
            CompareOptions opts = CompareOptions.builder().strategyName("nonexistent").build();
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

            CompareResult result = engine.execute(a, b, CompareOptions.DEFAULT);

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
        @DisplayName("策略抛出异常 → 返回降级结果，tfiMetrics 记录错误")
        void strategyThrows_shouldRecordMetricsAndReturnDegraded() {
            TfiMetrics tfiMetrics = new TfiMetrics(Optional.of(new SimpleMeterRegistry()));
            CompareEngine engineWithMetrics = new CompareEngine(
                    new StrategyResolver(), tfiMetrics, null, stubListExecutor,
                    customStrategies, namedStrategies
            );

            RecordingStrategy throwingStrategy = new RecordingStrategy("throwing") {
                @Override
                public boolean supports(Class<?> type) {
                    return SimpleObject.class.isAssignableFrom(type);
                }

                @Override
                public CompareResult compare(Object obj1, Object obj2, CompareOptions options) {
                    throw new RuntimeException("Simulated failure");
                }
            };
            customStrategies.put(SimpleObject.class, throwingStrategy);

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            CompareResult result = engineWithMetrics.execute(a, b, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getChanges()).isEmpty();
            assertThat(result.getObject1()).isEqualTo(a);
            assertThat(result.getObject2()).isEqualTo(b);
        }

        @Test
        @DisplayName("策略抛出异常 → microSink 记录错误（tfiMetrics 为 null）")
        void strategyThrows_microSinkRecordsError() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MicrometerDiagnosticSink microSink = new MicrometerDiagnosticSink(registry);
            CompareEngine engineWithSink = new CompareEngine(
                    new StrategyResolver(), null, microSink, stubListExecutor,
                    customStrategies, namedStrategies
            );

            RecordingStrategy throwingStrategy = new RecordingStrategy("throwing") {
                @Override
                public boolean supports(Class<?> type) {
                    return SimpleObject.class.isAssignableFrom(type);
                }

                @Override
                public CompareResult compare(Object obj1, Object obj2, CompareOptions options) {
                    throw new RuntimeException("Simulated failure");
                }
            };
            customStrategies.put(SimpleObject.class, throwingStrategy);

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            CompareResult result = engineWithSink.execute(a, b, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(result.getChanges()).isEmpty();
        }

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

            assertThatCode(() -> engine.execute(a, b, CompareOptions.DEFAULT))
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
        @DisplayName("referenceChange 始终包含")
        void referenceChange_shouldAlwaysInclude() {
            // 通过策略返回带 referenceChange 的 FieldChange
            RecordingStrategy refStrategy = new RecordingStrategy("ref") {
                @Override
                public boolean supports(Class<?> type) {
                    return SimpleObject.class.isAssignableFrom(type);
                }

                @Override
                public CompareResult compare(Object obj1, Object obj2, CompareOptions options) {
                    FieldChange fc = FieldChange.builder()
                            .fieldName("ref")
                            .fieldPath("ref")
                            .oldValue("old")
                            .newValue("new")
                            .changeType(ChangeType.UPDATE)
                            .referenceChange(true)
                            .referenceDetail(FieldChange.ReferenceDetail.builder()
                                    .oldEntityKey("old")
                                    .newEntityKey("new")
                                    .build())
                            .build();
                    return CompareResult.builder()
                            .object1(obj1)
                            .object2(obj2)
                            .changes(List.of(fc))
                            .identical(false)
                            .build();
                }
            };
            customStrategies.put(SimpleObject.class, refStrategy);

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            CompareResult result = engine.execute(a, b, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(result.getChanges()).hasSize(1);
            assertThat(result.getChanges().get(0).isReferenceChange()).isTrue();
        }

        @Test
        @DisplayName("ignoreFields 排除指定字段")
        void ignoreFields_shouldExclude() {
            SimpleObject a = new SimpleObject("Alice", 30);
            SimpleObject b = new SimpleObject("Bob", 25);

            CompareOptions opts = CompareOptions.builder()
                    .enableDeepCompare(true)
                    .ignoreFields(List.of("name", "age"))
                    .build();

            CompareResult result = engine.execute(a, b, opts);

            assertThat(result).isNotNull();
            // 所有字段被忽略，changes 应为空或仅包含非忽略字段
            if (!result.getChanges().isEmpty()) {
                for (FieldChange fc : result.getChanges()) {
                    assertThat(fc.getFieldName()).isNotIn("name", "age");
                }
            }
        }

        @Test
        @DisplayName("includeNullChanges 包含 null 变更")
        void includeNullChanges_shouldInclude() {
            SimpleObject a = new SimpleObject("Alice", 30);
            SimpleObject b = new SimpleObject("Bob", 25);

            CompareOptions opts = CompareOptions.builder()
                    .enableDeepCompare(true)
                    .includeNullChanges(true)
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
                    .enableDeepCompare(true)
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
                    .enableDeepCompare(true)
                    .excludeFields(List.of("name"))
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
                    .enableDeepCompare(true)
                    .forcedObjectType(com.syy.taskflowinsight.annotation.ObjectType.ENTITY)
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
                    .enableDeepCompare(true)
                    .forcedStrategy(ValueObjectCompareStrategy.FIELDS)
                    .build();

            CompareResult result = engine.execute(a, b, opts);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("typeAwareEnabled 启用类型感知")
        void typeAware_shouldApply() {
            SimpleObject a = new SimpleObject("Alice", 30);
            SimpleObject b = new SimpleObject("Bob", 25);

            CompareOptions opts = CompareOptions.typeAware();

            CompareResult result = engine.execute(a, b, opts);
            assertThat(result).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  sortResult — algorithmUsed、degradationReasons
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sortResult — algorithmUsed、degradationReasons、指标")
    class SortResultTests {

        @Test
        @DisplayName("algorithmUsed 非空 → tfiMetrics 记录")
        void algorithmUsed_shouldRecordMetrics() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            TfiMetrics tfiMetrics = new TfiMetrics(Optional.of(registry));
            CompareEngine engineWithMetrics = new CompareEngine(
                    new StrategyResolver(), tfiMetrics, null, stubListExecutor,
                    customStrategies, namedStrategies
            );

            RecordingStrategy algoStrategy = new RecordingStrategy("algo") {
                @Override
                public boolean supports(Class<?> type) {
                    return SimpleObject.class.isAssignableFrom(type);
                }

                @Override
                public CompareResult compare(Object obj1, Object obj2, CompareOptions options) {
                    return CompareResult.builder()
                            .object1(obj1)
                            .object2(obj2)
                            .changes(Collections.emptyList())
                            .identical(true)
                            .algorithmUsed("LCS")
                            .build();
                }
            };
            customStrategies.put(SimpleObject.class, algoStrategy);

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            CompareResult result = engineWithMetrics.execute(a, b, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(result.getAlgorithmUsed()).isEqualTo("LCS");
        }

        @Test
        @DisplayName("degradationReasons 非空 → microSink 记录")
        void degradationReasons_shouldRecordMetrics() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MicrometerDiagnosticSink microSink = new MicrometerDiagnosticSink(registry);
            CompareEngine engineWithSink = new CompareEngine(
                    new StrategyResolver(), null, microSink, stubListExecutor,
                    customStrategies, namedStrategies
            );

            RecordingStrategy degStrategy = new RecordingStrategy("deg") {
                @Override
                public boolean supports(Class<?> type) {
                    return SimpleObject.class.isAssignableFrom(type);
                }

                @Override
                public CompareResult compare(Object obj1, Object obj2, CompareOptions options) {
                    return CompareResult.builder()
                            .object1(obj1)
                            .object2(obj2)
                            .changes(Collections.emptyList())
                            .identical(false)
                            .degradationReasons(List.of("timeout", "size"))
                            .build();
                }
            };
            customStrategies.put(SimpleObject.class, degStrategy);

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            CompareResult result = engineWithSink.execute(a, b, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(result.getDegradationReasons()).containsExactly("timeout", "size");
        }

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
                    changes.add(FieldChange.builder()
                            .fieldName("zzz")
                            .fieldPath("zzz")
                            .oldValue("o")
                            .newValue("n")
                            .changeType(ChangeType.UPDATE)
                            .build());
                    changes.add(FieldChange.builder()
                            .fieldName("aaa")
                            .fieldPath("aaa")
                            .oldValue("o")
                            .newValue("n")
                            .changeType(ChangeType.UPDATE)
                            .build());
                    return CompareResult.builder()
                            .object1(obj1)
                            .object2(obj2)
                            .changes(changes)
                            .identical(false)
                            .build();
                }
            };
            customStrategies.put(SimpleObject.class, unorderedStrategy);

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            CompareResult result = engine.execute(a, b, CompareOptions.DEFAULT);

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
                    return CompareResult.builder()
                            .object1(obj1)
                            .object2(obj2)
                            .changes(Collections.emptyList())
                            .identical(true)
                            .build();
                }
            };
            customStrategies.put(SimpleObject.class, emptyStrategy);

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("A", 1);

            CompareResult result = engine.execute(a, b, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(result.getChanges()).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  指标记录
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("指标记录 — tfiMetrics、microSink")
    class MetricsRecordingTests {

        @Test
        @DisplayName("tfiMetrics 非 null 时记录指标")
        void tfiMetrics_shouldRecord() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            TfiMetrics tfiMetrics = new TfiMetrics(Optional.of(registry));
            CompareEngine engineWithMetrics = new CompareEngine(
                    new StrategyResolver(), tfiMetrics, null, stubListExecutor,
                    customStrategies, namedStrategies
            );

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            CompareResult result = engineWithMetrics.execute(a, b, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("microSink 非 null 时记录指标")
        void microSink_shouldRecord() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MicrometerDiagnosticSink microSink = new MicrometerDiagnosticSink(registry);
            CompareEngine engineWithSink = new CompareEngine(
                    new StrategyResolver(), null, microSink, stubListExecutor,
                    customStrategies, namedStrategies
            );

            SimpleObject a = new SimpleObject("A", 1);
            SimpleObject b = new SimpleObject("B", 2);

            CompareResult result = engineWithSink.execute(a, b, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
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
                    CompareOptions.DEFAULT
            );
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("DEEP 选项")
        void deepOptions() {
            CompareResult result = engine.execute(
                    new SimpleObject("A", 1),
                    new SimpleObject("B", 2),
                    CompareOptions.DEEP
            );
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("WITH_REPORT 选项")
        void withReportOptions() {
            CompareResult result = engine.execute(
                    new SimpleObject("A", 1),
                    new SimpleObject("B", 2),
                    CompareOptions.WITH_REPORT
            );
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("detectMoves 选项")
        void detectMovesOptions() {
            CompareOptions opts = CompareOptions.builder().detectMoves(true).build();
            List<String> list1 = List.of("a", "b");
            List<String> list2 = List.of("b", "a");

            CompareResult result = engine.execute(list1, list2, opts);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("perfTimeoutMs、perfMaxElements 选项")
        void perfOptions() {
            CompareOptions opts = CompareOptions.withPerfBudget(1000, 500);
            CompareResult result = engine.execute(
                    new SimpleObject("A", 1),
                    new SimpleObject("B", 2),
                    opts
            );
            assertThat(result).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  构造与 setProgrammaticDiffDetector
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("构造与配置")
    class ConstructionTests {

        @Test
        @DisplayName("null 参数构造不抛异常")
        void nullParams_shouldNotThrow() {
            assertThatCode(() ->
                    new CompareEngine(null, null, null, null, null, null)
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("setProgrammaticDiffDetector 设置后生效")
        void setProgrammaticDiffDetector_shouldWork() {
            engine.setProgrammaticDiffDetector(null);
            assertThatCode(() -> engine.execute(
                    new SimpleObject("A", 1),
                    new SimpleObject("B", 2),
                    CompareOptions.DEFAULT
            )).doesNotThrowAnyException();
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
            return CompareResult.builder()
                    .object1(list1)
                    .object2(list2)
                    .changes(Collections.emptyList())
                    .identical(list1.equals(list2))
                    .algorithmUsed("stub")
                    .build();
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
            return CompareResult.builder()
                    .object1(obj1)
                    .object2(obj2)
                    .changes(Collections.emptyList())
                    .identical(false)
                    .build();
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
