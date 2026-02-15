package com.syy.taskflowinsight.tracking.compare;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 大规模 Map/Set 容器对比基准（验证容器事件与查询API在规模下的吞吐）。
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class MapSetLargeBenchmarks {

    @State(Scope.Benchmark)
    public static class DataState {
        @Param({"1000", "10000"})
        public int size;

        public Map<String, Integer> oldMap;
        public Map<String, Integer> newMap;
        public Set<Integer> oldSet;
        public Set<Integer> newSet;
        public MapCompareStrategy mapStrategy;
        public SetCompareStrategy setStrategy;

        @Setup(Level.Trial)
        public void setup() {
            mapStrategy = new MapCompareStrategy();
            setStrategy = new SetCompareStrategy();

            oldMap = new LinkedHashMap<>();
            newMap = new LinkedHashMap<>();
            oldSet = new HashSet<>();
            newSet = new HashSet<>();

            // 40% 交集，30% 仅老侧，30% 仅新侧
            int common = (int) (size * 0.4);
            int onlyOld = (int) (size * 0.3);
            int onlyNew = size - common - onlyOld;

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

    @Benchmark
    public void map_compare(DataState s, Blackhole bh) {
        CompareResult r = s.mapStrategy.compare(s.oldMap, s.newMap, CompareOptions.DEFAULT);
        bh.consume(r.getChanges());
        bh.consume(r.getContainerChanges());
    }

    @Benchmark
    public void set_compare(DataState s, Blackhole bh) {
        CompareResult r = s.setStrategy.compare(s.oldSet, s.newSet, CompareOptions.DEFAULT);
        bh.consume(r.getChanges());
        bh.consume(r.getContainerChanges());
    }
}

