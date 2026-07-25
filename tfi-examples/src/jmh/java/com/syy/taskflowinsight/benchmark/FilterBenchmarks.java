package com.syy.taskflowinsight.benchmark;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import com.syy.taskflowinsight.tracking.snapshot.filter.PathPattern;
import com.syy.taskflowinsight.tracking.snapshot.filter.PathPatternCompiler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Compare有界遍历与 typed path pattern 的JMH性能基准。
 *
 * 目标:
 * - 对比完整容器遍历与关闭容器内容后的请求局部内核成本
 * - 分离构造期 pattern 编译与运行期无状态匹配成本
 *
 * 运行方式:
 * ./mvnw clean test-compile exec:exec@run-benchmarks
 * 或使用JMH运行器直接运行
 *
 * 基准配置:
 * - Warmup: 3 iterations, 1 second each
 * - Measurement: 5 iterations, 2 seconds each
 * - Fork: 1
 * - Mode: Average time (ns/op)
 *
 * @author TaskFlow Insight Team
 * @since 2025-10-09
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Thread)
public class FilterBenchmarks {

    // ========== Benchmark 1: Bounded Large Object Traversal ==========

    /**
     * 状态：大对象有界遍历基准。
     * 两组选项只改变容器内容准入，确保都经过同一Runtime与请求局部ledger。
     *
     * @since 4.0.0
     */
    @State(Scope.Thread)
    public static class LargeObjectFilterState {

        /** 每个JMH线程独占的immutable Runtime，执行期不保存请求状态。 */
        CompareRuntime runtime;

        /** 允许进入容器成员的完整遍历基线。 */
        CompareOptions fullTraversalOptions;

        /** 关闭容器成员后的有界对照，默认值仍由同一Runtime Policy提供。 */
        CompareOptions boundedTraversalOptions;

        /** 变更前合成对象；与after分离引用以避免root identity短路。 */
        LargeObjectGenerator.LargeBusinessObject before;

        /** 内容相同但引用独立的变更后对象。 */
        LargeObjectGenerator.LargeBusinessObject after;

        /** trial内复用冻结Runtime和等价输入，避免构造成本污染遍历对照。 */
        @Setup(Level.Trial)
        public void setup() {
            runtime = CompareRuntime.builder().build();
            fullTraversalOptions = CompareOptions.builder(runtime.policy())
                    .maxDepth(3)
                    .build();
            boundedTraversalOptions = CompareOptions.builder(runtime.policy())
                    .maxDepth(3)
                    .includeCollectionContents(false)
                    .build();
            before = LargeObjectGenerator.generateLargeObject();
            after = LargeObjectGenerator.generateLargeObject();
        }

    }

    /**
     * 基准1：允许容器成员进入snapshot的完整遍历基线。
     *
     * @param state 当前线程的冻结Runtime与输入
     * @param blackhole 防止JIT消除比较结果
     */
    @Benchmark
    public void baseline_NoFiltering(LargeObjectFilterState state, Blackhole blackhole) {
        CompareResult result = state.runtime.engine().compare(
                state.before, state.after, state.fullTraversalOptions);
        blackhole.consume(result);
    }

    /**
     * 基准2：关闭容器成员后的有界遍历对照。
     *
     * 该对照用于量化容器成员准入成本，不再代表旧PathFilter规则。
     *
     * @param state 当前线程的冻结Runtime与输入
     * @param blackhole 防止JIT消除比较结果
     */
    @Benchmark
    public void filterLargeObject(LargeObjectFilterState state, Blackhole blackhole) {
        CompareResult result = state.runtime.engine().compare(
                state.before, state.after, state.boundedTraversalOptions);
        blackhole.consume(result);
    }

    // ========== Benchmark 2: Typed Pattern Matching ==========

    /**
     * 状态：typed path pattern 基准。
     * Pattern 在 trial 构造期编译，运行期只消费 ComparePath，符合生产执行边界。
     *
     * @since 4.0.0
     */
    @State(Scope.Thread)
    public static class PatternMatchState {

        /** 高频匹配使用的 typed path 样本。 */
        ComparePath[] testPaths;

        /** 构造期编译并冻结的 pattern 集合。 */
        PathPattern[] patterns;

        /** 单独测量编译成本时使用的有界 grammar 源。 */
        String[] patternSources;

        /** 构造typed路径与已编译pattern，使运行期基准不混入初始化成本。 */
        @Setup(Level.Trial)
        public void setup() {
            ComparePath root = ComparePath.root();
            testPaths = new ComparePath[]{
                root.append(new PropertySegment("order")).append(new PropertySegment("orderId")),
                root.append(new PropertySegment("order")).append(new PropertySegment("items")),
                root.append(new PropertySegment("user")).append(new PropertySegment("username")),
                root.append(new PropertySegment("user")).append(new PropertySegment("password")),
                root.append(new PropertySegment("internal")).append(new PropertySegment("token")),
                root.append(new PropertySegment("debug")).append(new PropertySegment("trace"))
            };

            patternSources = new String[]{
                "PROPERTY:order/PROPERTY:*",
                "PROPERTY:user/PROPERTY:*",
                "PROPERTY:user/PROPERTY:*password",
                "PROPERTY:internal/PROPERTY:*",
                "PROPERTY:debug/PROPERTY:*"
            };
            patterns = Arrays.stream(patternSources)
                    .map(source -> PathPatternCompiler.compileCaseSensitive(source, 8, 64, 256))
                    .toArray(PathPattern[]::new);
        }
    }

    /**
     * 基准3：已编译 pattern 的运行期匹配成本。
     *
     * @param state 当前线程的路径与pattern样本
     * @param blackhole 防止JIT消除匹配计数
     */
    @Benchmark
    public void typedPatternMatching(PatternMatchState state, Blackhole blackhole) {
        int matchCount = 0;
        for (ComparePath path : state.testPaths) {
            for (PathPattern pattern : state.patterns) {
                if (pattern.matches(path)) {
                    matchCount++;
                }
            }
        }
        blackhole.consume(matchCount);
    }

    /**
     * 基准4：构造期 grammar 校验与 pattern 编译成本。
     * 该成本不应混入每次路径匹配，因此与运行期基准分开测量。
     *
     * @param state 当前线程的pattern源文本
     * @param blackhole 防止JIT消除编译结果
     */
    @Benchmark
    public void typedPatternCompilation(PatternMatchState state, Blackhole blackhole) {
        for (String source : state.patternSources) {
            blackhole.consume(PathPatternCompiler.compileCaseSensitive(source, 8, 64, 256));
        }
    }
}
