package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * TFI v2.0.0-MVP 全功能验证测试。
 *
 * <p>覆盖核心会话管理、消息类型、线程安全、变更追踪 API
 * （显式 track / withTracked）、值格式化、字段选择和性能基线。</p>
 *
 * @since 2.0.0
 */
class FullFeatureVerificationTest {

    /** 测试用业务对象。 */
    static class TestOrder {
        private String orderId;
        private String status;
        private Double amount;
        private String customerName;

        TestOrder(String orderId, String status, Double amount, String customerName) {
            this.orderId = orderId;
            this.status = status;
            this.amount = amount;
            this.customerName = customerName;
        }

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
    }

    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
    }

    @AfterEach
    void tearDown() {
        TFI.clear();
    }

    // ── Phase A: 核心功能 ──────────────────────────────────────────────

    @Test
    @DisplayName("会话管理: start -> message -> stop 不抛异常")
    void testBasicSessionManagement() {
        assertDoesNotThrow(() -> {
            TFI.start("test-session");
            TFI.message("Session started", MessageType.PROCESS);
            TFI.stop();
        });
    }

    @Test
    @DisplayName("消息类型: PROCESS / METRIC / ALERT / CHANGE 全部可用")
    void testMessageTypes() {
        assertDoesNotThrow(() -> {
            TFI.start("message-test");
            TFI.message("Process message", MessageType.PROCESS);
            TFI.message("Metric message", MessageType.METRIC);
            TFI.message("Alert message", MessageType.ALERT);
            TFI.message("Change message", MessageType.CHANGE);
            TFI.stop();
        });
    }

    @Test
    @DisplayName("线程安全: 5 线程并行会话全部成功")
    void testThreadSafety() throws InterruptedException {
        AtomicInteger successCount = new AtomicInteger(0);
        Thread[] threads = new Thread[5];

        for (int i = 0; i < threads.length; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    TFI.start("thread-" + threadId);
                    TFI.message("Thread " + threadId, MessageType.PROCESS);
                    TFI.stop();
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                    // thread isolation failure
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join(2_000);
        }

        assertThat(successCount.get()).isEqualTo(threads.length);
    }

    @Test
    @DisplayName("内存管理: clear 后可重新开启会话")
    void testMemoryManagement() {
        TFI.start("memory-test");
        TFI.message("Test", MessageType.PROCESS);
        TFI.clear();

        assertDoesNotThrow(() -> {
            TFI.start("new-session");
            TFI.stop();
        });
    }

    // ── Phase B: 监控功能 ─────────────────────────────────────────────

    @Test
    @DisplayName("性能基线: 100 条消息 < 100ms")
    void testPerformanceBaseline() {
        TFI.start("perf-test");
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            TFI.message("msg-" + i, MessageType.PROCESS);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        TFI.stop();

        assertThat(elapsedMs).isLessThan(100);
    }

    // ── Phase C: 变更追踪 ─────────────────────────────────────────────

    @Test
    @DisplayName("显式 track API: 修改 2 个字段 -> 产生 2 条变更")
    void testExplicitTrackingApi() {
        TestOrder order = new TestOrder("ORD-001", "PENDING", 100.0, "Alice");

        TFI.start("explicit-api-test");
        TFI.track("order", order, "status", "amount");

        order.setStatus("PAID");
        order.setAmount(150.0);

        List<ChangeRecord> changes = TFI.getChanges();
        assertThat(changes).hasSize(2);

        TFI.clearAllTracking();
        TFI.stop();
    }

    @Test
    @DisplayName("变更记录字段值: objectName / fieldName / oldValue / newValue 正确")
    void testChangeRecordOutput() {
        TestOrder order = new TestOrder("ORD-002", "NEW", 200.0, "Bob");

        TFI.start("change-output-test");
        TFI.track("order", order, "status");
        order.setStatus("PROCESSING");

        List<ChangeRecord> changes = TFI.getChanges();
        assertThat(changes).isNotEmpty();

        ChangeRecord first = changes.get(0);
        assertThat(first.getObjectName()).isEqualTo("order");
        assertThat(first.getFieldName()).isEqualTo("status");
        assertThat(first.getOldValue()).hasToString("NEW");
        assertThat(first.getNewValue()).hasToString("PROCESSING");

        TFI.clearAllTracking();
        TFI.stop();
    }

    @Test
    @DisplayName("withTracked 便捷 API: 回调内修改后变更被清理")
    void testWithTrackedApi() {
        TestOrder order = new TestOrder("ORD-003", "CREATED", 300.0, "Charlie");

        TFI.start("withtracked-test");
        TFI.withTracked("order", order, () -> {
            order.setStatus("CONFIRMED");
            order.setAmount(350.0);
        }, "status", "amount");

        List<ChangeRecord> changes = TFI.getChanges();
        assertThat(changes).isEmpty();

        TFI.stop();
    }

    @Test
    @DisplayName("字段选择: 只追踪 status, amount 变更不记录")
    void testFieldSelection() {
        TestOrder order = new TestOrder("ORD-005", "INIT", 500.0, "Frank");

        TFI.start("field-selection-test");
        TFI.track("order", order, "status");

        order.setStatus("UPDATED");
        order.setAmount(600.0);        // 不应被追踪
        order.setCustomerName("George"); // 不应被追踪

        List<ChangeRecord> changes = TFI.getChanges();
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getFieldName()).isEqualTo("status");

        TFI.clearAllTracking();
        TFI.stop();
    }

    @Test
    @DisplayName("演示场景: 显式 track + withTracked 组合")
    void testDemoScenarios() {
        TFI.start("demo-test");

        TestOrder order = new TestOrder("DEMO-001", "PENDING", 999.0, "Demo User");
        TFI.track("demoOrder", order, "status", "amount");
        order.setStatus("PAID");
        order.setAmount(1299.0);

        List<ChangeRecord> explicit = TFI.getChanges();
        assertThat(explicit).hasSize(2);

        TFI.clearAllTracking();

        TFI.withTracked("demoOrder2", order, () -> order.setStatus("SHIPPED"), "status");

        TFI.stop();
    }
}
