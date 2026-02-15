package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compare() 路径的轻量性能基线测试。
 * 说明：JIT/环境差异较大，本用例提供软性阈值，主要用于回归监测。
 */
class ReferenceSemanticComparePerfTests {

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
    void compare_should_be_fast_enough_for_small_objects() {
        CompareService service = CompareService.createDefault(CompareOptions.DEEP);

        Order before = new Order("O1", new Customer("C1"));
        Order after  = new Order("O1", new Customer("C2"));

        // Warmup
        for (int i = 0; i < 50; i++) {
            service.compare(before, after, CompareOptions.DEEP);
        }

        int iterations = 1000;
        long start = System.nanoTime();
        int detected = 0;
        for (int i = 0; i < iterations; i++) {
            CompareResult r = service.compare(before, after, CompareOptions.DEEP);
            detected += r.getReferenceChanges().size();
        }
        long elapsed = System.nanoTime() - start;

        double avgNs = elapsed / (double) iterations; // 每次 compare 平均耗时
        System.out.printf("compare(): avg %.2f ns/op (%.3f us/op) over %d iterations, detected=%d\n",
            avgNs, avgNs / 1000.0, iterations, detected);

        // 软性阈值：< 1 ms/次；真实 1µs 目标应以更细粒度（仅检测函数）评估
        assertTrue(avgNs < 1_000_000.0, String.format("Avg compare time %.2f ns exceeds 1ms", avgNs));
        assertEquals(iterations, detected, "Each compare should detect exactly one reference change");
    }
}

