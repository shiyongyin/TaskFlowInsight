package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;

/**
 * 变更追踪功能演示
 * 展示 Phase A 的完整闭环：track -> 修改 -> stop -> 导出
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 */
public class ChangeTrackingDemo {
    
    /** 示例业务对象：订单 */
    static class Order {
        private String orderId;
        private String status;
        private Double amount;
        private String customer;
        
        public Order(String orderId, String status, Double amount, String customer) {
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
    
    public static void main(String[] args) {
        System.out.println("=== TaskFlow Insight v2.0.0 变更追踪演示 ===\n");
        
        // 启用TFI和变更追踪
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
        
        // 开始会话
        TFI.startSession("OrderProcessingSession");
        
        // 场景1：订单状态变更
        System.out.println("场景1：订单状态变更");
        TFI.start("ProcessPayment");
        
        Order order1 = new Order("ORD-001", "PENDING", 100.0, "Alice");
        
        // 追踪订单的status和amount字段
        TFI.track("Order", order1, "status", "amount");
        
        // 业务处理：支付成功，状态变更
        order1.setStatus("PAID");
        order1.setAmount(95.0); // 应用了5元优惠
        
        // 停止任务（自动刷新变更记录）
        TFI.stop();
        
        // 场景2：批量订单处理
        System.out.println("\n场景2：批量订单处理");
        TFI.start("BatchShipment");
        
        Order order2 = new Order("ORD-002", "PAID", 200.0, "Bob");
        Order order3 = new Order("ORD-003", "PAID", 150.0, "Charlie");
        
        // 批量追踪
        TFI.track("Order2", order2, "status");
        TFI.track("Order3", order3, "status");
        
        // 批量发货
        order2.setStatus("SHIPPED");
        order3.setStatus("SHIPPED");
        
        TFI.stop();
        
        // 结束会话
        TFI.endSession();
        
        // 导出报告
        System.out.println("\n=== Console导出 ===");
        TFI.exportToConsole();
        
        System.out.println("\n=== JSON导出 ===");
        String json = TFI.exportToJson();
        System.out.println(json);
        
        // 清理
        TFI.clear();
        
        System.out.println("\n=== 演示完成 ===");
    }
}