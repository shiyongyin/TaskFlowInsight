package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 变更追踪功能集成测试。
 *
 * <p>验证 TFI 的 track -&gt; mutate -&gt; getChanges -&gt; export 完整闭环，
 * 包括单对象追踪和批量追踪两种场景。</p>
 *
 * @since 2.0.0
 */
class ChangeTrackingDemoTest {

    /** 示例业务对象：订单。 */
    static class Order {
        private String orderId;
        private String status;
        private Double amount;
        private String customer;

        Order(String orderId, String status, Double amount, String customer) {
            this.orderId = orderId;
            this.status = status;
            this.amount = amount;
            this.customer = customer;
        }

        public void setStatus(String status) { this.status = status; }
        public void setAmount(Double amount) { this.amount = amount; }
        public String getStatus() { return status; }
        public Double getAmount() { return amount; }
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

    @Test
    @DisplayName("单对象追踪: status + amount 变更被正确捕获")
    void testSingleObjectTracking() {
        TFI.startSession("OrderProcessingSession");
        TFI.start("ProcessPayment");

        Order order = new Order("ORD-001", "PENDING", 100.0, "Alice");
        TFI.track("Order", order, "status", "amount");

        order.setStatus("PAID");
        order.setAmount(95.0);

        List<ChangeRecord> changes = TFI.getChanges();
        assertThat(changes).hasSize(2);
        assertThat(changes).extracting(ChangeRecord::getFieldName)
                .containsExactlyInAnyOrder("status", "amount");

        TFI.clearAllTracking();
        TFI.stop();
        TFI.endSession();
    }

    @Test
    @DisplayName("批量追踪: 2 个订单状态同时变更")
    void testBatchTracking() {
        TFI.startSession("BatchSession");
        TFI.start("BatchShipment");

        Order order2 = new Order("ORD-002", "PAID", 200.0, "Bob");
        Order order3 = new Order("ORD-003", "PAID", 150.0, "Charlie");

        TFI.track("Order2", order2, "status");
        TFI.track("Order3", order3, "status");

        order2.setStatus("SHIPPED");
        order3.setStatus("SHIPPED");

        List<ChangeRecord> changes = TFI.getChanges();
        assertThat(changes).hasSize(2);
        assertThat(changes).allSatisfy(c ->
                assertThat(c.getNewValue()).hasToString("SHIPPED"));

        TFI.clearAllTracking();
        TFI.stop();
        TFI.endSession();
    }

    @Test
    @DisplayName("exportToConsole / exportToJson 不抛异常")
    void testExportDoesNotThrow() {
        TFI.startSession("ExportSession");
        TFI.start("ExportTest");

        Order order = new Order("ORD-004", "NEW", 50.0, "Dave");
        TFI.track("Order", order, "status");
        order.setStatus("CONFIRMED");

        TFI.stop();
        TFI.endSession();

        assertDoesNotThrow(() -> TFI.exportToConsole());
        assertDoesNotThrow(() -> {
            String json = TFI.exportToJson();
            assertThat(json).isNotEmpty();
        });
    }
}
