package com.syy.taskflowinsight.bench;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.ContainerEvents;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * EntityListDiffResult.from 基准
 * 运行：mvn -P bench -DskipTests package && java -jar target/benchmarks.jar EntityListDiffResultBench
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class EntityListDiffResultBench {

    @State(Scope.Thread)
    public static class Data {
        @Param({"100", "1000"})
        public int size;

        public CompareResult withEvents;
        public CompareResult withoutEvents;

        @Setup
        public void setup() {
            List<FieldChange> a = new ArrayList<>();
            List<FieldChange> b = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                String key = "entity[id=" + i + "]";
                // with events
                a.add(FieldChange.builder()
                    .fieldPath(key + ".name")
                    .changeType(ChangeType.UPDATE)
                    .oldValue("n" + i)
                    .newValue("n'" + i)
                    .elementEvent(ContainerEvents.listModify(i, key, "name"))
                    .build());
                // without events (forces degrade)
                b.add(FieldChange.builder()
                    .fieldPath(key + ".name")
                    .changeType(ChangeType.UPDATE)
                    .oldValue("n" + i)
                    .newValue("n'" + i)
                    .build());
            }
            withEvents = CompareResult.builder().changes(a).build();
            withoutEvents = CompareResult.builder().changes(b).build();
        }
    }

    @Benchmark
    public EntityListDiffResult fromWithEvents(Data d) {
        return EntityListDiffResult.from(d.withEvents);
    }

    @Benchmark
    public EntityListDiffResult fromWithoutEvents(Data d) {
        return EntityListDiffResult.from(d.withoutEvents);
    }
}

