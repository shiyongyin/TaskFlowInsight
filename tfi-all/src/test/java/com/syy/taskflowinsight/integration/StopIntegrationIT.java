package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.model.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TFI.stop 集成测试
 * 验证变更追踪与Console/JSON导出
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 */
class StopIntegrationIT {
    
    /** 测试业务对象 */
    static class Order {
        private String orderId = "ORD-001";
        private String status = "PENDING";
        private Double amount = 100.0;
        private String customer = "Alice";
        
        public void setStatus(String status) { this.status = status; }
        public void setAmount(Double amount) { this.amount = amount; }
        public String getStatus() { return status; }
        public Double getAmount() { return amount; }
    }
    
    private PrintStream originalOut;
    private ByteArrayOutputStream capturedOut;
    
    @BeforeEach
    void setUp() {
        // 捕获控制台输出
        originalOut = System.out;
        capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
        
        // 启用TFI和变更追踪
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
        
        // 清理上下文
        TFI.clear();
    }
    
    @AfterEach
    void tearDown() {
        // 恢复控制台输出
        System.setOut(originalOut);
        
        // 清理
        TFI.endSession();
        TFI.clear();
    }
    
    @Test
    void testStopFlushesChangesToConsole() {
        // Given
        TFI.startSession("TestSession");
        TFI.start("ProcessOrder");
        
        Order order = new Order();
        
        // 追踪对象
        TFI.track("Order", order, "status", "amount");
        
        // 修改对象
        order.setStatus("PAID");
        order.setAmount(150.0);
        
        // When
        TFI.stop();
        TFI.exportToConsole();
        
        // Then
        String consoleOutput = capturedOut.toString();
        System.setOut(originalOut);
        System.out.println("Console Output:\n" + consoleOutput);
        
        // 验证包含CHANGE消息
        assertTrue(consoleOutput.contains("变更记录"), "应包含变更记录标签");
        assertTrue(consoleOutput.contains("Order.status: PENDING → PAID"), 
                   "应包含status变更: " + consoleOutput);
        assertTrue(consoleOutput.contains("Order.amount: 100.0 → 150.0"), 
                   "应包含amount变更: " + consoleOutput);
    }
    
    @Test
    void testStopFlushesChangesToJson() {
        // Given
        TFI.startSession("JsonTestSession");
        TFI.start("UpdateProduct");
        
        Order order = new Order();
        TFI.track("Order", order);
        
        // 修改对象
        order.setStatus("SHIPPED");
        
        // When
        TFI.stop();
        String json = TFI.exportToJson();
        
        // Then
        assertNotNull(json);
        System.out.println("JSON Output:\n" + json);
        
        // 验证JSON包含变更
        assertTrue(json.contains("\"type\":\"CHANGE\"") || json.contains("\"type\": \"CHANGE\""), 
                   "JSON应包含CHANGE类型消息");
        assertTrue(json.contains("Order.status") && json.contains("SHIPPED"), 
                   "JSON应包含status变更信息");
    }
    
    @Test
    void testMultipleTrackedObjects() {
        // Given
        TFI.startSession("MultiObjectSession");
        TFI.start("BatchUpdate");
        
        Order order1 = new Order();
        Order order2 = new Order();
        
        TFI.track("Order1", order1, "status");
        TFI.track("Order2", order2, "amount");
        
        // 修改对象
        order1.setStatus("COMPLETED");
        order2.setAmount(200.0);
        
        // When
        TFI.stop();
        TFI.exportToConsole();
        
        // Then
        String output = capturedOut.toString();
        assertTrue(output.contains("Order1.status: PENDING → COMPLETED"));
        assertTrue(output.contains("Order2.amount: 100.0 → 200.0"));
    }
    
    @Test
    void testNoChangesNoOutput() {
        // Given
        TFI.startSession("NoChangeSession");
        TFI.start("NoChangeTask");
        
        Order order = new Order();
        TFI.track("Order", order);
        
        // 不修改对象
        
        // When
        TFI.stop();
        TFI.exportToConsole();
        
        // Then
        String output = capturedOut.toString();
        assertFalse(output.contains("Order.status"), "不应包含未变更的字段");
    }
    
    @Test
    void testClearAllTrackingWorks() {
        // Given
        TFI.start("ClearTest");
        
        Order order = new Order();
        TFI.track("Order", order);
        order.setStatus("CHANGED");
        
        // When
        TFI.clearAllTracking();
        TFI.stop();
        TFI.exportToConsole();
        
        // Then
        String output = capturedOut.toString();
        assertFalse(output.contains("CHANGED"), "清理后不应有变更记录");
    }
    
    @Test
    void testDisabledChangeTracking() {
        // Given
        TFI.setChangeTrackingEnabled(false);
        TFI.start("DisabledTrackingTask");
        
        Order order = new Order();
        TFI.track("Order", order);
        order.setStatus("MODIFIED");
        
        // When
        TFI.stop();
        TFI.exportToConsole();
        
        // Then
        String output = capturedOut.toString();
        assertFalse(output.contains("MODIFIED"), "禁用时不应记录变更");
    }
}