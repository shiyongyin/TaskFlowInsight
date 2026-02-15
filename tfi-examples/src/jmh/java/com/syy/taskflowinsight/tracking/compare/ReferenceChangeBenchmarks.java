package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * 引用变更检测基准：深度比较 + @ShallowReference 检测吞吐。
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class ReferenceChangeBenchmarks {

    @Entity
    public static class Customer {
        @Key String id; String name;
        Customer(String id, String name) { this.id = id; this.name = name; }
    }

    public static class Order {
        @ShallowReference Customer assignee;
        @ShallowReference Customer reviewer;
        Order(Customer a, Customer r) { this.assignee = a; this.reviewer = r; }
    }

    @State(Scope.Thread)
    public static class DataState {
        public CompareService svc;
        public Order oldOrder;
        public Order newOrder;

        @Setup(Level.Trial)
        public void setup() {
            svc = CompareService.createDefault(CompareOptions.typeAware());
            oldOrder = new Order(new Customer("C1", "Alice"), new Customer("R1", "Ray"));
            newOrder = new Order(new Customer("C2", "Bob"), new Customer("R1", "Ray")); // assignee 切换
        }
    }

    @Benchmark
    public void bench_reference_change_detection(DataState s, Blackhole bh) {
        CompareResult r = s.svc.compare(s.oldOrder, s.newOrder, CompareOptions.typeAware());
        bh.consume(r.getReferenceChanges());
    }
}

