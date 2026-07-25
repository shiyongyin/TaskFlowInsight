package com.syy.taskflowinsight.demo.chapters;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.demo.core.DemoChapter;
import com.syy.taskflowinsight.demo.util.DemoUI;
import com.syy.taskflowinsight.demo.util.DemoUtils;
import com.syy.taskflowinsight.spi.DefaultTrackingProvider;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 第6章：变更追踪功能演示章节。
 *
 * <p>演示内容：
 * <ul>
 *   <li>显式TrackingExecutor词法作用域</li>
 *   <li>与Flow stage组合但不建立全局history</li>
 *   <li>直接消费typed CompareResult</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class ChangeTrackingChapter implements DemoChapter {

    /** executor可复用，但每次调用的baseline与结果都局限在execute词法作用域。 */
    private static final TrackingExecutor TRACKING_EXECUTOR =
            new TrackingExecutor(new DefaultTrackingProvider());
    
    /**
     * 演示用订单模型（内联最小化版本）。
     *
     * <p>不使用共享 {@code model.Order} 的原因：本章需要 {@code createdAt}
     * 和 {@code customerName} 等共享模型未包含的字段来演示多字段追踪。</p>
     */
    static class DemoOrder {
        /** 演示对象的业务主键。 */
        private String orderId;
        /** 当前订单状态。 */
        private String status;
        /** 当前订单金额。 */
        private Double amount;
        /** 当前客户名称。 */
        private String customerName;
        /** 订单创建时间。 */
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
            "显式TrackingExecutor同时拥有baseline、action和typed result",
            "Flow只拥有stage生命周期，不保存Compare history",
            "变更记录直接来自CompareResult",
            "支持多种数据类型：String、Number、Boolean、Date",
            "自动转义和截断超长值（最大8192字符）"
        );
    }
    
    @Override
    public void run() {
        DemoUI.printChapterHeader(getChapterNumber(), getTitle(), getDescription());
        
        // 场景1：显式executor与Flow组合
        System.out.println("\n" + "=".repeat(60));
        System.out.println("场景1：显式TrackingExecutor词法作用域");
        System.out.println("=".repeat(60));
        demoExplicitAPI();
        
        DemoUtils.sleep(1000);
        
        // 场景2：不同action使用独立scope
        System.out.println("\n" + "=".repeat(60));
        System.out.println("场景2：独立tracking scope");
        System.out.println("=".repeat(60));
        demoConvenientAPI();
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("说明：结果由每次TrackingExecutor调用直接返回，不存在跨调用查询或清理。");
        System.out.println("=".repeat(60));
    }
    
    /**
     * 场景1：显式executor与Flow stage组合。
     */
    private void demoExplicitAPI() {
        System.out.println("\n代码示例：");
        System.out.println("  CompareResult result = executor.withTracked(\"order\", order, action, options);");
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
        
        CompareResult result = TRACKING_EXECUTOR.withTracked(
                "order",
                order,
                () -> {
                    System.out.println("\n执行支付处理...");
                    DemoUtils.sleep(500);
                    order.setStatus("PAID");
                    order.setAmount(1299.00);
                },
                CompareOptions.builder().build());
        System.out.println("  捕获变更: " + result.getChanges().size());
        
        // 停止任务，触发变更记录
        TFI.stop();
        
        System.out.println("\n更新后订单状态：");
        System.out.println("  状态: " + order.getStatus());
        System.out.println("  金额: " + order.getAmount());
        
        TFI.endSession();
    }
    
    /**
     * 场景2：第二个action拥有独立的显式scope。
     */
    private void demoConvenientAPI() {
        System.out.println("\n代码示例：");
        System.out.println("  TFI.startSession(\"OrderProcessing\");");
        System.out.println("  TFI.start(\"order-shipping\");");
        System.out.println("  ");
        System.out.println("  executor.withTracked(\"order\", order, () -> {");
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
        
        CompareResult result = TRACKING_EXECUTOR.withTracked(
                "order",
                order,
                () -> {
                    DemoUtils.sleep(500);
                    order.setStatus("SHIPPED");
                    order.setCustomerName("Bob Johnson");
                    System.out.println("  [Lambda内] 订单状态更新为: " + order.getStatus());
                    System.out.println("  [Lambda内] 客户名称更新为: " + order.getCustomerName());
                },
                CompareOptions.builder().build());
        System.out.println("  捕获变更: " + result.getChanges().size());
        
        System.out.println("\n更新后订单状态：");
        System.out.println("  状态: " + order.getStatus());
        System.out.println("  客户: " + order.getCustomerName());
        
        TFI.stop();
        TFI.endSession();
    }
}
