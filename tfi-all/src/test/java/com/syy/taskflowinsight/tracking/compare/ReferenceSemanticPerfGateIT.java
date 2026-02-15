package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 性能门禁（verify 阶段，Failsafe 执行）
 * - 默认软门禁：仅打印耗时与建议目标，不失败
 * - 启用硬门禁：-Dtfi.perf.strict=true 时，如果超过目标阈值（默认 1000ns/op）则失败
 */
public class ReferenceSemanticPerfGateIT {

    @Entity
    static class Customer {
        @Key
        private final String id;
        Customer(String id) { this.id = id; }
        public String getId() { return id; }
    }

    @Entity
    static class Order {
        @Key
        private final String id;
        @ShallowReference
        private final Customer customer;
        Order(String id, Customer customer) { this.id = id; this.customer = customer; }
        public String getId() { return id; }
        public Customer getCustomer() { return customer; }
    }

    @Test
    void detect_reference_change_gate() {
        // Arrange
        Order before = new Order("O1", new Customer("C1"));
        Order after  = new Order("O1", new Customer("C2"));
        String path = "Order.customer";

        // Warmup
        for (int i = 0; i < 5000; i++) {
            var d = com.syy.taskflowinsight.tracking.detector.DiffFacade
                .detectReferenceChange(before, after, path, "Customer[C1]", "Customer[C2]");
            assertNotNull(d);
        }

        // Benchmark
        int iterations = 200_000; // 更高精度
        long start = System.nanoTime();
        int hits = 0;
        for (int i = 0; i < iterations; i++) {
            var d = com.syy.taskflowinsight.tracking.detector.DiffFacade
                .detectReferenceChange(before, after, path, "Customer[C1]", "Customer[C2]");
            if (d != null) hits++;
        }
        long elapsed = System.nanoTime() - start;
        double nsPerOp = elapsed / (double) iterations;

        System.out.printf("[PerfGate] detectReferenceChange: %.2f ns/op (%.3f us/op), iterations=%d, hits=%d%n",
            nsPerOp, nsPerOp / 1000.0, iterations, hits);

        // Gate
        boolean strict = Boolean.parseBoolean(System.getProperty("tfi.perf.strict", "false"));
        long target = Long.parseLong(System.getProperty("tfi.perf.target.ns", "1000"));

        if (strict) {
            assertTrue(nsPerOp <= target, String.format(
                "Perf gate failed: %.2f ns/op exceeds target %dns", nsPerOp, target));
        } else {
            // 软门禁：仅建议
            if (nsPerOp > target) {
                System.out.printf("[PerfGate][WARN] Average %.2f ns/op exceeds soft target %dns%n", nsPerOp, target);
            }
        }
        assertEquals(iterations, hits);
    }
}

