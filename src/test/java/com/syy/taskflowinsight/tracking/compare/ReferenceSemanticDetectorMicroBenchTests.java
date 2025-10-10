package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 微基准：直接调用 DiffFacade.detectReferenceChange() 的热路径开销
 * 说明：单元测试环境对微基准波动较大，以下不做硬性时延断言，仅记录指标并校验功能正确性。
 */
class ReferenceSemanticDetectorMicroBenchTests {

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
    void detect_reference_change_hot_path_benchmark() {
        // 准备对象与路径
        Order before = new Order("O1", new Customer("C1"));
        Order after  = new Order("O1", new Customer("C2"));
        String path = "Order.customer";

        // 预热
        for (int i = 0; i < 2000; i++) {
            var detail = com.syy.taskflowinsight.tracking.detector.DiffFacade
                .detectReferenceChange(before, after, path, "Customer[C1]", "Customer[C2]");
            assertNotNull(detail);
        }

        // 基准
        int iterations = 100_000; // 提高统计精度
        long start = System.nanoTime();
        int nonNull = 0;
        for (int i = 0; i < iterations; i++) {
            var detail = com.syy.taskflowinsight.tracking.detector.DiffFacade
                .detectReferenceChange(before, after, path, "Customer[C1]", "Customer[C2]");
            if (detail != null) nonNull++;
        }
        long elapsed = System.nanoTime() - start;

        double nsPerOp = elapsed / (double) iterations;
        System.out.printf("detectReferenceChange(): avg %.2f ns/op (%.3f us/op) over %d iterations, hits=%d\n",
            nsPerOp, nsPerOp / 1000.0, iterations, nonNull);

        assertEquals(iterations, nonNull);
        // 不做硬阈值断言，避免 CI 波动；如需阈值，可将下行解注释并视环境调整：
        // assertTrue(nsPerOp < 2000.0, String.format("Avg %.2f ns exceeds 2us target", nsPerOp));
    }
}

