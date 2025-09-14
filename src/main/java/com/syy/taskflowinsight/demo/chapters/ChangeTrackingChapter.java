package com.syy.taskflowinsight.demo.chapters;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.demo.core.DemoChapter;
import com.syy.taskflowinsight.demo.util.DemoUI;
import com.syy.taskflowinsight.demo.util.DemoUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 变更追踪功能演示章节
 * 
 * 演示内容：
 * 1. 显式API方式：TFI.start() → track() → mutate → stop()
 * 2. 便捷API方式：TFI.withTracked()
 * 3. 展示Console和JSON格式的CHANGE输出
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 */
public class ChangeTrackingChapter implements DemoChapter {
    
    /**
     * 演示用订单模型（内联最小化版本）
     */
    static class DemoOrder {
        private String orderId;
        private String status;
        private Double amount;
        private String customerName;
        private Date createdAt;
        
        public DemoOrder(String orderId, String status, Double amount, String customerName) {
            this.orderId = orderId;
            this.status = status;
            this.amount = amount;
            this.customerName = customerName;
            this.createdAt = new Date();
        }
        
        // Getters and Setters
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
        
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        
        public Date getCreatedAt() { return createdAt; }
        public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    }
    
    @Override
    public int getChapterNumber() {
        return 6;
    }
    
    @Override
    public String getTitle() {
        return "变更追踪功能";
    }
    
    @Override
    public String getDescription() {
        return "演示对象字段变更的自动追踪与记录";
    }
    
    @Override
    public List<String> getSummaryPoints() {
        return Arrays.asList(
            "显式API方式：手动调用track()和stop()管理生命周期",
            "便捷API方式：使用withTracked()自动管理生命周期",
            "变更记录格式：Object.field: OLD → NEW",
            "支持多种数据类型：String、Number、Boolean、Date",
            "自动转义和截断超长值（最大8192字符）"
        );
    }
    
    @Override
    public void run() {
        DemoUI.printChapterHeader(getChapterNumber(), getTitle(), getDescription());
        
        // 场景1：显式API方式
        System.out.println("\n" + "=".repeat(60));
        System.out.println("场景1：显式API方式（手动管理追踪生命周期）");
        System.out.println("=".repeat(60));
        demoExplicitAPI();
        
        DemoUtils.sleep(1000);
        
        // 场景2：便捷API方式
        System.out.println("\n" + "=".repeat(60));
        System.out.println("场景2：便捷API方式（自动管理追踪生命周期）");
        System.out.println("=".repeat(60));
        demoConvenientAPI();
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("说明：withTracked在finally块中已即时刷写并清理变更，");
        System.out.println("      因此stop阶段不会重复输出这些变更。");
        System.out.println("=".repeat(60));
    }
    
    /**
     * 场景1：显式API演示
     * 流程：TFI.start() → TFI.track() → 修改字段 → TFI.stop()
     */
    private void demoExplicitAPI() {
        System.out.println("\n代码示例：");
        System.out.println("  TFI.startSession(\"OrderProcessing\");");
        System.out.println("  TFI.start(\"order-payment\");");
        System.out.println("  TFI.track(\"order\", order, \"status\", \"amount\");");
        System.out.println("  // 执行业务逻辑：修改订单状态和金额");
        System.out.println("  order.setStatus(\"PAID\");");
        System.out.println("  order.setAmount(1299.00);");
        System.out.println("  TFI.stop();");
        System.out.println();
        
        // 实际执行
        TFI.startSession("OrderProcessing-Explicit");
        TFI.start("order-payment");
        
        // 创建订单对象
        DemoOrder order = new DemoOrder("ORD-001", "PENDING", 999.00, "Alice");
        System.out.println("初始订单状态：");
        System.out.println("  订单ID: " + order.getOrderId());
        System.out.println("  状态: " + order.getStatus());
        System.out.println("  金额: " + order.getAmount());
        System.out.println("  客户: " + order.getCustomerName());
        
        // 开始追踪
        TFI.track("order", order, "status", "amount", "customerName");
        
        // 模拟业务处理：支付成功，更新状态和金额
        System.out.println("\n执行支付处理...");
        DemoUtils.sleep(500);
        order.setStatus("PAID");
        order.setAmount(1299.00);  // 包含税费
        
        // 停止任务，触发变更记录
        TFI.stop();
        
        System.out.println("\n更新后订单状态：");
        System.out.println("  状态: " + order.getStatus());
        System.out.println("  金额: " + order.getAmount());
        
        // 导出Console格式
        System.out.println("\n--- Console输出 ---");
        TFI.exportToConsole();
        
        // 导出JSON格式
        System.out.println("\n--- JSON输出片段 ---");
        String json = TFI.exportToJson();
        if (json != null && json.length() > 0) {
            // 只显示包含CHANGE的部分
            String[] lines = json.split("\n");
            boolean inMessages = false;
            int messageCount = 0;
            for (String line : lines) {
                if (line.contains("\"messages\"")) {
                    inMessages = true;
                }
                if (inMessages && (line.contains("CHANGE") || line.contains("order."))) {
                    System.out.println(line);
                    messageCount++;
                    if (messageCount > 5) break; // 限制输出行数
                }
            }
        }
        
        TFI.endSession();
    }
    
    /**
     * 场景2：便捷API演示
     * 流程：TFI.withTracked() 自动管理生命周期
     */
    private void demoConvenientAPI() {
        System.out.println("\n代码示例：");
        System.out.println("  TFI.startSession(\"OrderProcessing\");");
        System.out.println("  TFI.start(\"order-shipping\");");
        System.out.println("  ");
        System.out.println("  TFI.withTracked(\"order\", order, () -> {");
        System.out.println("      // 业务逻辑在lambda中执行");
        System.out.println("      order.setStatus(\"SHIPPED\");");
        System.out.println("      order.setCustomerName(\"Alice Smith\");");
        System.out.println("  }, \"status\", \"customerName\");");
        System.out.println("  ");
        System.out.println("  TFI.stop();");
        System.out.println();
        
        // 实际执行
        TFI.startSession("OrderProcessing-Convenient");
        TFI.start("order-shipping");
        
        // 创建订单对象
        DemoOrder order = new DemoOrder("ORD-002", "PAID", 1299.00, "Bob");
        System.out.println("初始订单状态：");
        System.out.println("  订单ID: " + order.getOrderId());
        System.out.println("  状态: " + order.getStatus());
        System.out.println("  金额: " + order.getAmount());
        System.out.println("  客户: " + order.getCustomerName());
        
        System.out.println("\n执行发货处理...");
        
        // 使用便捷API：自动管理追踪生命周期
        TFI.withTracked("order", order, () -> {
            DemoUtils.sleep(500);
            // 在lambda中执行业务逻辑
            order.setStatus("SHIPPED");
            order.setCustomerName("Bob Johnson"); // 补充完整姓名
            System.out.println("  [Lambda内] 订单状态更新为: " + order.getStatus());
            System.out.println("  [Lambda内] 客户名称更新为: " + order.getCustomerName());
        }, "status", "customerName", "amount");
        
        System.out.println("\n更新后订单状态：");
        System.out.println("  状态: " + order.getStatus());
        System.out.println("  客户: " + order.getCustomerName());
        
        // 注意：此时stop不会重复输出withTracked中的变更
        TFI.stop();
        
        // 导出Console格式
        System.out.println("\n--- Console输出 ---");
        TFI.exportToConsole();
        
        // 导出JSON格式
        System.out.println("\n--- JSON输出片段 ---");
        String json = TFI.exportToJson();
        if (json != null && json.length() > 0) {
            // 只显示包含CHANGE的部分
            String[] lines = json.split("\n");
            boolean inMessages = false;
            int messageCount = 0;
            for (String line : lines) {
                if (line.contains("\"messages\"")) {
                    inMessages = true;
                }
                if (inMessages && (line.contains("CHANGE") || line.contains("order."))) {
                    System.out.println(line);
                    messageCount++;
                    if (messageCount > 5) break; // 限制输出行数
                }
            }
        }
        
        TFI.endSession();
    }
}