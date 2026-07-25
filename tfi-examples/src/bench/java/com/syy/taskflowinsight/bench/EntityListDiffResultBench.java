package com.syy.taskflowinsight.bench;

import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.compare.internal.CompareResultReducer;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
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
        /** 当前基准轮次构造的实体字段变更数量。 */
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
                a.add(FieldChange.at(ChangeKind.MODIFY, ComparePath.root().append(new PropertySegment(key + ".name")), "n" + i, "n'" + i));
                // without events (forces degrade)
                b.add(FieldChange.at(ChangeKind.MODIFY, ComparePath.root().append(new PropertySegment(key + ".name")), "n" + i, "n'" + i));
            }
            withEvents = CompareResultReducer.complete(a);
            withoutEvents = CompareResultReducer.complete(b);
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
