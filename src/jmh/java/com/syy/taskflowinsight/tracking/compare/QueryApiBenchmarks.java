package com.syy.taskflowinsight.tracking.compare;

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
                FieldChange fc = FieldChange.builder()
                    .fieldName(prop)
                    .fieldPath(obj + "." + prop)
                    .oldValue(i)
                    .newValue(i + 1)
                    .changeType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE)
                    .build();
                changes.add(fc);
            }

            for (int i = 0; i < container; i++) {
                boolean list = (i % 2 == 0);
                FieldChange.ContainerType ct = list
                    ? FieldChange.ContainerType.LIST
                    : FieldChange.ContainerType.MAP;
                FieldChange.ElementOperation op;
                switch (i % 3) {
                    case 0 -> op = FieldChange.ElementOperation.ADD;
                    case 1 -> op = FieldChange.ElementOperation.REMOVE;
                    default -> op = FieldChange.ElementOperation.MODIFY;
                }

                FieldChange.FieldChangeBuilder b = FieldChange.builder()
                    .fieldName(list ? ("[" + i + "]") : ("k" + i))
                    .oldValue(list ? null : i)
                    .newValue(list ? i : (i + 1))
                    .changeType(list ? com.syy.taskflowinsight.tracking.ChangeType.CREATE : com.syy.taskflowinsight.tracking.ChangeType.UPDATE)
                    .elementEvent(FieldChange.ContainerElementEvent.builder()
                        .containerType(ct)
                        .operation(op)
                        .index(list ? i : null)
                        .mapKey(list ? null : ("k" + i))
                        .build());

                changes.add(b.build());
            }

            result = CompareResult.builder()
                .object1(null)
                .object2(null)
                .changes(changes)
                .identical(false)
                .build();
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

