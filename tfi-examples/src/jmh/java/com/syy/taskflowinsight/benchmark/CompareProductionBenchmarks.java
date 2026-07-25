package com.syy.taskflowinsight.benchmark;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.ops.compare.ObservedCompareOperations;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.EntityKeySegment;
import com.syy.taskflowinsight.tracking.path.IndexSegment;
import com.syy.taskflowinsight.tracking.path.MapKeySegment;
import com.syy.taskflowinsight.tracking.path.PathSegment;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import com.syy.taskflowinsight.tracking.path.SetMemberSegment;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Compare 生产非 identity workload 的统一 JMH state。
 *
 * <p>fixture 在 trial 开始时构造并由 semantic oracle 预验证；测量只包含正式 Operations 调用。</p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CompareProductionBenchmarks {

    /** Runner 每次只注入一个封闭 scenario，禁止在 benchmark 内动态随机选择。 */
    @Param({
            "NESTED_POJO", "LIST", "MAP", "SET_SCALAR",
            "SET_ENTITY", "SET_AMBIGUOUS", "OBSERVED_COMPARE"
    })
    public String scenario;

    /** 当前 trial 唯一的 Compare 调用入口。 */
    private CompareOperations operations;

    /** 与 after 不同引用的结构化变更前输入。 */
    private Object before;

    /** 与 before 同类型且至少有一个语义差异的变更后输入。 */
    private Object after;

    /** trial setup 已验证的稳定语义事实。 */
    private SemanticFact semanticFact;

    /** 仅 observed 场景持有并在 trial 结束时关闭的 Spring Context。 */
    private ConfigurableApplicationContext observedContext;

    /** 构造 fixture、选择正式入口并在计时前验证 oracle。 */
    @Setup(Level.Trial)
    public void setup() {
        CompareProductionBenchmarkRunner.Scenario selected =
                CompareProductionBenchmarkRunner.Scenario.valueOf(scenario);
        boolean observed = selected == CompareProductionBenchmarkRunner.Scenario.OBSERVED_COMPARE;
        if (observed) {
            observedContext = new SpringApplicationBuilder(ObservedBenchmarkApplication.class)
                    .web(WebApplicationType.NONE)
                    .logStartupInfo(false)
                    .run("--spring.main.banner-mode=off", "--tfi.compare.tracking.enabled=false");
            operations = observedContext.getBean(CompareOperations.class);
            if (!(operations instanceof ObservedCompareOperations)) {
                throw new IllegalStateException("observed workload did not select decorator");
            }
        } else {
            operations = CompareRuntime.builder().build().engine();
        }

        Fixture fixture = fixtureFor(selected);
        before = fixture.before();
        after = fixture.after();
        CompareResult result = operations.compare(before, after);
        semanticFact = fixture.oracle().verify(selected, result, observed, before != after);
        BenchmarkForkEvidence.captureIfRequested(operations, semanticFact);
    }

    /** 关闭 observed 场景的 Context；其他场景没有外部生命周期。 */
    @TearDown(Level.Trial)
    public void tearDown() {
        if (observedContext != null) {
            observedContext.close();
            observedContext = null;
        }
    }

    /**
     * 执行一个经过 setup oracle 验证的正式比较。
     *
     * @return JMH 必须消费的 canonical 结果
     */
    @Benchmark
    public CompareResult compare() {
        return operations.compare(before, after);
    }

    /**
     * 不启动 JMH 即验证七个 fixture，供 runner contract 测试拒绝 identity 假 workload。
     *
     * @return 与 scenario 枚举一一对应的语义事实
     */
    static List<SemanticFact> validateAllScenarios() {
        List<SemanticFact> facts = new ArrayList<>();
        for (CompareProductionBenchmarkRunner.Scenario value
                : CompareProductionBenchmarkRunner.Scenario.values()) {
            CompareProductionBenchmarks benchmark = new CompareProductionBenchmarks();
            benchmark.scenario = value.name();
            try {
                benchmark.setup();
                facts.add(benchmark.semanticFact);
            } finally {
                benchmark.tearDown();
            }
        }
        return List.copyOf(facts);
    }

    private static Fixture fixtureFor(CompareProductionBenchmarkRunner.Scenario selected) {
        return switch (selected) {
            case NESTED_POJO, OBSERVED_COMPARE -> nestedFixture();
            case LIST -> listFixture();
            case MAP -> mapFixture();
            case SET_SCALAR -> scalarSetFixture();
            case SET_ENTITY -> entitySetFixture(false);
            case SET_AMBIGUOUS -> entitySetFixture(true);
        };
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ObservedBenchmarkApplication {
    }

    private static Fixture nestedFixture() {
        return new Fixture(
                new NestedRoot(new NestedBranch("stable", 41), "tenant-a"),
                new NestedRoot(new NestedBranch("changed", 41), "tenant-a"),
                SemanticOracle.different(expectedChange(
                        ChangeKind.MODIFY,
                        ComparePath.root()
                                .append(new PropertySegment("branch"))
                                .append(new PropertySegment("name")))));
    }

    private static Fixture listFixture() {
        List<String> before = new ArrayList<>();
        List<String> after = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            before.add("item-" + index);
            after.add(index == 31 ? "changed-31" : "item-" + index);
        }
        return new Fixture(
                List.copyOf(before), List.copyOf(after),
                SemanticOracle.different(expectedChange(
                        ChangeKind.MODIFY,
                        ComparePath.root().append(new IndexSegment(31)))));
    }

    private static Fixture mapFixture() {
        Map<String, String> before = new LinkedHashMap<>();
        Map<String, String> after = new LinkedHashMap<>();
        for (int index = 0; index < 32; index++) {
            before.put("key-" + index, "value-" + index);
            after.put("key-" + index, index == 17 ? "changed-17" : "value-" + index);
        }
        return new Fixture(before, after, SemanticOracle.different(expectedChange(
                ChangeKind.MODIFY,
                ComparePath.root().append(new MapKeySegment(
                        ValueSnapshot.captureSupported("key-17", 64))))));
    }

    private static Fixture scalarSetFixture() {
        Set<Integer> before = new LinkedHashSet<>();
        Set<Integer> after = new LinkedHashSet<>();
        for (int value = 0; value < 32; value++) {
            before.add(value);
            after.add(value == 31 ? 100 : value);
        }
        return new Fixture(before, after, SemanticOracle.different(
                expectedChange(
                        ChangeKind.REMOVE,
                        ComparePath.root().append(new SetMemberSegment(
                                ValueSnapshot.captureSupported(31, 64)))),
                expectedChange(
                        ChangeKind.ADD,
                        ComparePath.root().append(new SetMemberSegment(
                                ValueSnapshot.captureSupported(100, 64))))));
    }

    private static Fixture entitySetFixture(boolean ambiguous) {
        Set<BenchmarkEntity> before = new LinkedHashSet<>();
        Set<BenchmarkEntity> after = new LinkedHashSet<>();
        if (ambiguous) {
            before.add(new BenchmarkEntity(1, "before-a"));
            before.add(new BenchmarkEntity(1, "before-b"));
            after.add(new BenchmarkEntity(1, "after-a"));
            after.add(new BenchmarkEntity(1, "after-b"));
            return new Fixture(before, after, SemanticOracle.ambiguous());
        }
        for (int id = 0; id < 32; id++) {
            before.add(new BenchmarkEntity(id, "name-" + id));
            after.add(new BenchmarkEntity(id, id == 19 ? "changed-19" : "name-" + id));
        }
        ComparePath changedName = ComparePath.root()
                .append(new EntityKeySegment(
                        BenchmarkEntity.class.getName(),
                        List.of(ValueSnapshot.captureSupported(19, 64))))
                .append(new PropertySegment("name"));
        return new Fixture(
                before, after,
                SemanticOracle.different(expectedChange(ChangeKind.MODIFY, changedName)));
    }

    private static String expectedChange(ChangeKind kind, ComparePath path) {
        return changeToken(kind, path);
    }

    private static String changeToken(FieldChange change) {
        ComparePath path = change.after()
                .or(change::before)
                .orElseThrow()
                .path();
        return changeToken(change.kind(), path);
    }

    private static String changeToken(ChangeKind kind, ComparePath path) {
        StringBuilder token = new StringBuilder(kind.name());
        for (PathSegment segment : path.segments()) {
            token.append('|');
            for (String fact : segment.canonicalTextFacts()) {
                token.append(fact.length()).append(':').append(fact);
            }
        }
        return token.toString();
    }

    /** 多层普通对象 fixture 的根节点。 */
    private record NestedRoot(
            /** 继续进入一层字段遍历的分支。 */ NestedBranch branch,
            /** 保持不变以防只测单字段对象。 */ String tenant) {
    }

    /** 多层普通对象中实际发生字段变化的叶分支。 */
    private record NestedBranch(
            /** before/after 不同的业务文本。 */ String name,
            /** 保持不变的数值字段。 */ int quantity) {
    }

    /** Entity Set fixture 的稳定 identity 与可变内容。 */
    @Entity
    private static final class BenchmarkEntity {

        /** 跨侧配对使用的 canonical identity。 */
        @Key
        private final int id;

        /** identity 配对后继续比较的内容。 */
        private final String name;

        private BenchmarkEntity(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** 一个 scenario 的输入与预期真值，不包含计时或环境事实。 */
    private record Fixture(
            /** 不同引用的变更前结构。 */ Object before,
            /** 与 before 同类型的变更后结构。 */ Object after,
            /** setup 阶段必须通过的语义预言。 */ SemanticOracle oracle) {
    }

    /** 固定 outcome/completion/change/limitation 的语义预言。 */
    private record SemanticOracle(
            /** 预期业务真值。 */ CompareOutcome outcome,
            /** 预期执行完整度。 */ CompareCompletion completion,
            /** 必须精确匹配的 kind 与 typed canonical path 闭集。 */ List<String> changeTokens,
            /** 必须精确匹配的 limitation code。 */ List<CompareLimitationCode> limitationCodes) {

        private static SemanticOracle different(String... changeTokens) {
            return new SemanticOracle(
                    CompareOutcome.DIFFERENT,
                    CompareCompletion.COMPLETE,
                    sortedTokens(List.of(changeTokens)),
                    List.of());
        }

        private static SemanticOracle ambiguous() {
            return new SemanticOracle(
                    CompareOutcome.INDETERMINATE,
                    CompareCompletion.PARTIAL,
                    List.of(),
                    List.of(CompareLimitationCode.KEY_AMBIGUOUS));
        }

        private SemanticFact verify(
                CompareProductionBenchmarkRunner.Scenario scenario,
                CompareResult result,
                boolean observedDecorator,
                boolean distinctInputs) {
            List<CompareLimitationCode> actualLimitations = result.getLimitations().stream()
                    .map(limitation -> limitation.code())
                    .distinct()
                    .sorted(Comparator.comparing(CompareLimitationCode::name))
                    .toList();
            List<String> actualChanges = sortedTokens(result.getChanges().stream()
                    .map(CompareProductionBenchmarks::changeToken)
                    .toList());
            if (!distinctInputs
                    || result.getOutcome() != outcome
                    || result.getCompletion() != completion
                    || !actualChanges.equals(changeTokens)
                    || !actualLimitations.equals(limitationCodes)) {
                throw new IllegalStateException("production benchmark semantic oracle failed");
            }
            return new SemanticFact(
                    scenario,
                    result.getOutcome(),
                    result.getCompletion(),
                    result.getChanges().size(),
                    actualChanges,
                    actualLimitations,
                    distinctInputs,
                    observedDecorator);
        }

        private static List<String> sortedTokens(List<String> tokens) {
            return tokens.stream().sorted().toList();
        }
    }

    /** Runner 写入 raw evidence 的 workload 语义事实。 */
    record SemanticFact(
            /** 固定场景 ID。 */ CompareProductionBenchmarkRunner.Scenario scenario,
            /** 一次 setup compare 的业务真值。 */ CompareOutcome outcome,
            /** 一次 setup compare 的执行完整度。 */ CompareCompletion completion,
            /** setup 结果中的 canonical change 数。 */ int changeCount,
            /** 精确 kind 与 typed canonical path 闭集。 */ List<String> changeTokens,
            /** 去重并按 code 排序的 limitation 集合。 */ List<CompareLimitationCode> limitationCodes,
            /** before/after 是否为不同引用。 */ boolean distinctInputs,
            /** 是否实际选择了 Spring metrics decorator。 */ boolean observedDecorator) {
    }
}
