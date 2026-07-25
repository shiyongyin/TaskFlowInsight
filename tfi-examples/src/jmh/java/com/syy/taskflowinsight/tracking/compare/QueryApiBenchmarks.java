package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.compare.internal.CompareResultReducer;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.IndexSegment;
import com.syy.taskflowinsight.tracking.path.MapKeySegment;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH 基准：CompareResult 便捷查询 API
 * - groupByObject
 * - groupByProperty
 * - groupByContainerOperation
 * - getChangeCountByType
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class QueryApiBenchmarks {

    @State(Scope.Thread)
    public static class DataState {
        /** 当前基准轮次生成的canonical change数量。 */
        @Param({"1000", "10000"})
        public int size;

        public CompareResult result;

        @Setup(Level.Trial)
        public void setup() {
            List<FieldChange> changes = new ArrayList<>(size);
            Random rnd = new Random(42);

            // 70% 标量字段，30% 容器元素（LIST/MAP）
            int scalar = (int) (size * 0.7);
            int container = size - scalar;

            for (int i = 0; i < scalar; i++) {
                String obj = (i % 2 == 0) ? "order" : "customer";
                String prop = (i % 3 == 0) ? "status" : (i % 3 == 1) ? "price" : "amount";
                FieldChange fc = FieldChange.at(ChangeKind.MODIFY, ComparePath.root().append(new PropertySegment(obj + "." + prop)), i, i + 1);
                changes.add(fc);
            }

            for (int i = 0; i < container; i++) {
                boolean list = (i % 2 == 0);
                ComparePath path = list
                        ? ComparePath.root().append(
                                new IndexSegment(i))
                        : ComparePath.root().append(
                                new MapKeySegment(
                                        ValueSnapshot.ofString("k" + i, 4096)));
                changes.add(FieldChange.at(
                        list ? ChangeKind.ADD : ChangeKind.MODIFY,
                        path,
                        list ? null : i,
                        list ? i : i + 1));
            }

            result = CompareResultReducer.complete(changes);
        }
    }

    @Benchmark
    public void bench_groupByObject(DataState s, Blackhole bh) {
        bh.consume(s.result.groupByObject());
    }

    @Benchmark
    public void bench_groupByProperty(DataState s, Blackhole bh) {
        bh.consume(s.result.groupByProperty());
    }

    @Benchmark
    public void bench_groupByContainerOperation(DataState s, Blackhole bh) {
        bh.consume(s.result.groupByContainerOperation());
    }

    @Benchmark
    public void bench_getChangeCountByType(DataState s, Blackhole bh) {
        bh.consume(s.result.getChangeCountByType());
    }
}
