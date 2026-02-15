package com.syy.taskflowinsight.tracking.bench;

import com.syy.taskflowinsight.tracking.compare.ContainerEvents;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class TypedViewBenchmark {

    @State(Scope.Thread)
    public static class BenchState {
        @Param({"100", "1000", "10000"})
        public int size;

        FieldChange[] baseline;
        FieldChange[] enhanced;

        @Setup
        public void setup() {
            baseline = new FieldChange[size];
            enhanced = new FieldChange[size];
            for (int i = 0; i < size; i++) {
                baseline[i] = FieldChange.builder()
                    .fieldName("items[" + i + "]")
                    .fieldPath("items[" + i + "]")
                    .oldValue(null)
                    .newValue(i)
                    .changeType(com.syy.taskflowinsight.tracking.ChangeType.CREATE)
                    .build();

                enhanced[i] = FieldChange.builder()
                    .fieldName("items[" + i + "]")
                    .fieldPath("items[" + i + "]")
                    .oldValue(null)
                    .newValue(i)
                    .changeType(com.syy.taskflowinsight.tracking.ChangeType.CREATE)
                    .elementEvent(ContainerEvents.listAdd(i, "item[" + i + "]"))
                    .build();
            }
        }
    }

    @Benchmark
    public void baseline_noElementEvent(BenchState s, Blackhole bh) {
        for (FieldChange fc : s.baseline) {
            // Expect null (non-container) → toTypedView returns null
            Object view = fc.toTypedView();
            bh.consume(view);
        }
    }

    @Benchmark
    public void enhanced_withElementEvent(BenchState s, Blackhole bh) {
        for (FieldChange fc : s.enhanced) {
            Object view = fc.toTypedView();
            bh.consume(view);
        }
    }
}

