package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceSemanticConcurrencyTests {

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
    void concurrent_compare_should_be_stable_and_detect_reference_change() throws InterruptedException, ExecutionException {
        int threads = 100;
        int tasksPerThread = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            tasks.add(() -> {
                CompareService svc = CompareService.createDefault(CompareOptions.DEEP);
                int detected = 0;
                for (int i = 0; i < tasksPerThread; i++) {
                    Order before = new Order("O" + i, new Customer("C1"));
                    Order after  = new Order("O" + i, new Customer("C2"));
                    CompareResult r = svc.compare(before, after, CompareOptions.DEEP);
                    detected += r.getReferenceChanges().size();
                }
                return detected;
            });
        }

        int total = 0;
        List<Future<Integer>> futures = pool.invokeAll(tasks);
        for (Future<Integer> f : futures) {
            total += f.get();
        }
        pool.shutdownNow();

        assertEquals(threads * tasksPerThread, total, "Each compare should detect exactly one reference change");
    }
}

