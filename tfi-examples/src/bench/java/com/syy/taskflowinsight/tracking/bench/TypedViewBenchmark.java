package com.syy.taskflowinsight.tracking.bench;

import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
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
                ComparePath path = ComparePath.root().append(new PropertySegment("items[" + i + "]"));
                baseline[i] = FieldChange.at(ChangeKind.ADD, path, null, i);

                enhanced[i] = FieldChange.at(ChangeKind.ADD, path, null, i);
            }
        }
    }

    @Benchmark
    public void baseline_noElementEvent(BenchState s, Blackhole bh) {
        for (FieldChange fc : s.baseline) {
            bh.consume(fc.kind());
        }
    }

    @Benchmark
    public void enhanced_withElementEvent(BenchState s, Blackhole bh) {
        for (FieldChange fc : s.enhanced) {
            bh.consume(fc.after());
        }
    }
}
