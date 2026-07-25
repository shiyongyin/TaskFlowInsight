package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 大规模Map/Set容器比较基准。
 *
 * <p>基准必须经{@link CompareRuntime}执行完整生产路由，避免直接调用策略得到绕过请求预算的乐观结果。</p>
 *
 * @since 4.0.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class MapSetLargeBenchmarks {

    /** 基准复用生产同构的冻结执行图，避免直接策略调用绕过路由与共享预算。 */
    private static final CompareRuntime RUNTIME = CompareRuntime.builder().build();

    /** 选项必须与基准 runtime 的 policy 同源，否则会测到输入校验失败而非容器比较。 */
    private static final CompareOptions OPTIONS = CompareOptions.defaults(RUNTIME.policy());

    /**
     * 以固定交集和单侧差异比例构造Map/Set，使不同规模测量保持同一业务轴。
     *
     * @since 4.0.0
     */
    @State(Scope.Benchmark)
    public static class DataState {
        /** 单侧容器的目标元素数，用于观察固定变化比例随规模增长的成本。 */
        @Param({"1000", "10000"})
        public int size;

        /** 变更前Map；LinkedHashMap只控制构造可复现性，不参与结果排序。 */
        public Map<String, Integer> oldMap;

        /** 变更后Map；与oldMap保持40%同key但不同value。 */
        public Map<String, Integer> newMap;

        /** 变更前Set；与newSet保持40%交集。 */
        public Set<Integer> oldSet;

        /** 变更后Set；用于保留无序容器的独立性能轴。 */
        public Set<Integer> newSet;

        /** 每个trial只构造一次输入，避免数据生成成本进入比较吞吐。 */
        @Setup(Level.Trial)
        public void setup() {
            oldMap = new LinkedHashMap<>();
            newMap = new LinkedHashMap<>();
            oldSet = new HashSet<>();
            newSet = new HashSet<>();

            // 40% 交集，30% 仅老侧，30% 仅新侧
            int common = (int) (size * 0.4);
            int onlyOld = (int) (size * 0.3);

            for (int i = 0; i < common; i++) {
                oldMap.put("k" + i, i);
                newMap.put("k" + i, i + 1); // 修改
                oldSet.add(i);
                newSet.add(i);
            }
            for (int i = common; i < common + onlyOld; i++) {
                oldMap.put("k" + i, i);
                oldSet.add(i);
            }
            for (int i = common + onlyOld; i < size; i++) {
                newMap.put("k" + i, i);
                newSet.add(i);
            }
        }
    }

    /**
     * keyed Entity在List、Set与Map value三条生产路径上的同轴输入。
     *
     * <p>规模独立于scalar大容器基准，避免MOVE与字段遍历成本被更大的element预算截断信号淹没。</p>
     */
    @State(Scope.Benchmark)
    public static class EntityDataState {

        /** 单侧Entity数量，覆盖小批量与常见千级集合。 */
        @Param({"100", "1000"})
        public int size;

        /** 变更前keyed List，按ID升序。 */
        public List<BenchEntity> oldList;

        /** 变更后keyed List，按ID降序并包含字段变化。 */
        public List<BenchEntity> newList;

        /** 变更前Entity Set，identity只由显式Key决定。 */
        public Set<BenchEntity> oldSet;

        /** 变更后Entity Set，保持相同identity并修改内容。 */
        public Set<BenchEntity> newSet;

        /** 变更前Map value Entity，Map key负责entry配对。 */
        public Map<String, BenchEntity> oldMap;

        /** 变更后Map value Entity，用于测量配对后的字段遍历。 */
        public Map<String, BenchEntity> newMap;

        /** 每个trial只构造一次三条同轴输入。 */
        @Setup(Level.Trial)
        public void setup() {
            oldList = new ArrayList<>(size);
            newList = new ArrayList<>(size);
            oldSet = new LinkedHashSet<>();
            newSet = new LinkedHashSet<>();
            oldMap = new LinkedHashMap<>();
            newMap = new LinkedHashMap<>();
            for (int index = 0; index < size; index++) {
                BenchEntity before = new BenchEntity(index, "before-" + index);
                BenchEntity after = new BenchEntity(
                        index,
                        index % 10 == 0 ? "after-" + index : "before-" + index);
                oldList.add(before);
                newList.add(after);
                oldSet.add(before);
                newSet.add(after);
                oldMap.put("k" + index, before);
                newMap.put("k" + index, after);
            }
            Collections.reverse(newList);
        }
    }

    /** 基准Entity的equals只表达ID，用于防止性能路径重新依赖内容equals。 */
    @Entity
    public static final class BenchEntity {

        /** 三种容器共享的exact candidate identity。 */
        @Key
        private final int id;

        /** identity配对后继续遍历的内容字段。 */
        private final String name;

        public BenchEntity(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BenchEntity entity && id == entity.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    /**
     * 测量Map exact-key比较及结果消费成本。
     *
     * @param s 当前trial的容器输入
     * @param bh 防止JIT消除结果读取
     */
    @Benchmark
    public void map_compare(DataState s, Blackhole bh) {
        CompareResult r = RUNTIME.engine().compare(s.oldMap, s.newMap, OPTIONS);
        bh.consume(r.getChanges());
        bh.consume(r.getContainerChanges());
    }

    /**
     * 保留Set的独立性能轴，避免Map修正被无序集合成本混淆。
     *
     * @param s 当前trial的容器输入
     * @param bh 防止JIT消除结果读取
     */
    @Benchmark
    public void set_compare(DataState s, Blackhole bh) {
        CompareResult r = RUNTIME.engine().compare(s.oldSet, s.newSet, OPTIONS);
        bh.consume(r.getChanges());
        bh.consume(r.getContainerChanges());
    }

    /** keyed List同时测量unique-key MOVE与字段深比较。 */
    @Benchmark
    public void entity_list_compare(EntityDataState state, Blackhole blackhole) {
        CompareResult result = RUNTIME.engine().compare(state.oldList, state.newList, OPTIONS);
        blackhole.consume(result.getChanges());
        blackhole.consume(result.getLimitations());
    }

    /** Entity Set测量exact identity配对后的内容遍历。 */
    @Benchmark
    public void entity_set_compare(EntityDataState state, Blackhole blackhole) {
        CompareResult result = RUNTIME.engine().compare(state.oldSet, state.newSet, OPTIONS);
        blackhole.consume(result.getChanges());
        blackhole.consume(result.getLimitations());
    }

    /** Map value Entity由Map key配对后复用相同descriptor深比较。 */
    @Benchmark
    public void entity_map_value_compare(EntityDataState state, Blackhole blackhole) {
        CompareResult result = RUNTIME.engine().compare(state.oldMap, state.newMap, OPTIONS);
        blackhole.consume(result.getChanges());
        blackhole.consume(result.getLimitations());
    }
}
