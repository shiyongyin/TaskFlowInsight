package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.api.TFI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Date;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 导出验证集成测试
 * 验证Console和JSON导出中正确包含CHANGE消息
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 */
class ExportVerificationIT {
    
    /** 测试业务对象 */
    static class Product {
        private String sku;
        private String name;
        private Double price;
        private Integer stock;
        private Boolean available;
        private Date lastUpdated;
        
        public Product(String sku, String name, Double price, Integer stock) {
            this.sku = sku;
            this.name = name;
            this.price = price;
            this.stock = stock;
            this.available = true;
            this.lastUpdated = new Date();
        }
        
        public void setName(String name) { this.name = name; }
        public void setPrice(Double price) { this.price = price; }
        public void setStock(Integer stock) { this.stock = stock; }
        public void setAvailable(Boolean available) { this.available = available; }
        public void setLastUpdated(Date lastUpdated) { this.lastUpdated = lastUpdated; }
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
        TFI.clear();
    }
    
    @AfterEach
    void tearDown() {
        // 恢复控制台
        System.setOut(originalOut);
        
        // 清理
        TFI.endSession();
        TFI.clear();
    }
    
    @Test
    void testConsoleExportContainsChangeMessages() {
        // Given
        TFI.startSession("ProductUpdateSession");
        TFI.start("UpdateProduct");
        
        Product product = new Product("SKU-001", "Laptop", 999.99, 10);
        
        // 追踪产品
        TFI.track("Product", product, "name", "price", "stock", "available");
        
        // 修改产品属性
        product.setName("Gaming Laptop");
        product.setPrice(1299.99);
        product.setStock(5);
        product.setAvailable(false);
        
        // When
        TFI.stop();
        TFI.exportToConsole();
        
        // Then
        String consoleOutput = capturedOut.toString();
        System.setOut(originalOut);
        System.out.println("Console Export:\n" + consoleOutput);
        
        // 验证包含变更记录标签
        assertTrue(consoleOutput.contains("变更记录"), "应包含变更记录标签");
        
        // 验证具体的变更消息格式：<obj>.<field>: <old> → <new>
        assertTrue(consoleOutput.contains("Product.name: Laptop → Gaming Laptop"),
                   "应包含name变更");
        assertTrue(consoleOutput.contains("Product.price: 999.99 → 1299.99"),
                   "应包含price变更");
        assertTrue(consoleOutput.contains("Product.stock: 10 → 5"),
                   "应包含stock变更");
        assertTrue(consoleOutput.contains("Product.available: true → false"),
                   "应包含available变更");
        
        // 验证任务结构
        assertTrue(consoleOutput.contains("UpdateProduct"));
        assertTrue(consoleOutput.contains("ProductUpdateSession"));
    }
    
    @Test
    void testJsonExportContainsChangeMessages() {
        // Given
        TFI.startSession("JsonExportSession");
        TFI.start("ProcessOrder");
        
        Product product = new Product("SKU-002", "Mouse", 29.99, 100);
        TFI.track("Product", product, "price", "stock");
        
        // 修改
        product.setPrice(24.99);
        product.setStock(95);
        
        // When
        TFI.stop();
        String json = TFI.exportToJson();
        
        // Then
        assertNotNull(json);
        System.out.println("JSON Export:\n" + json);
        
        // 验证JSON包含CHANGE类型消息
        assertTrue(json.contains("\"type\":\"CHANGE\"") || json.contains("\"type\": \"CHANGE\""),
                   "JSON应包含CHANGE类型");
        
        // 验证包含变更内容（格式化后的消息）
        assertTrue(json.contains("Product.price") && json.contains("29.99") && json.contains("24.99"),
                   "JSON应包含price变更信息");
        assertTrue(json.contains("Product.stock") && json.contains("100") && json.contains("95"),
                   "JSON应包含stock变更信息");
        
        // 验证JSON结构完整性（不硬编码nodeId）
        assertTrue(json.contains("\"sessionId\""));
        assertTrue(json.contains("\"rootTask\""));
        assertTrue(json.contains("\"messages\""));
        assertTrue(json.contains("\"content\""));
    }
    
    @Test
    void testMultipleTasksWithChanges() {
        // Given
        TFI.startSession("MultiTaskSession");
        
        // 第一个任务
        TFI.start("Task1");
        Product p1 = new Product("P1", "Item1", 10.0, 5);
        TFI.track("Product1", p1, "price");
        p1.setPrice(15.0);
        TFI.stop();
        
        // 第二个任务
        TFI.start("Task2");
        Product p2 = new Product("P2", "Item2", 20.0, 10);
        TFI.track("Product2", p2, "stock");
        p2.setStock(8);
        TFI.stop();
        
        // When
        TFI.exportToConsole();
        String output = capturedOut.toString();
        
        // Then
        System.setOut(originalOut);
        System.out.println("Multi-task Output:\n" + output);
        
        // 验证两个任务的变更都存在
        assertTrue(output.contains("Task1"));
        assertTrue(output.contains("Product1.price: 10.0 → 15.0"));
        
        assertTrue(output.contains("Task2"));
        assertTrue(output.contains("Product2.stock: 10 → 8"));
    }
    
    @Test
    void testSpecialCharactersInChanges() {
        // Given
        TFI.startSession("SpecialCharSession");
        TFI.start("HandleSpecialChars");
        
        Product product = new Product("SKU-003", "Product\nwith\ttabs", 99.99, 1);
        TFI.track("Product", product, "name");
        
        product.setName("Product\"with\"quotes");
        
        // When
        TFI.stop();
        TFI.exportToConsole();
        
        // Then
        String output = capturedOut.toString();
        System.setOut(originalOut);
        
        // 验证特殊字符被正确转义
        assertTrue(output.contains("Product.name:"));
        assertTrue(output.contains("→"));
        
        // JSON导出也应该正确处理
        String json = TFI.exportToJson();
        assertNotNull(json);
        assertTrue(json.contains("Product.name"));
    }
    
    @Test
    void testNoChangesNoChangeMessages() {
        // Given
        TFI.startSession("NoChangeSession");
        TFI.start("NoChangeTask");
        
        Product product = new Product("SKU-004", "Static", 50.0, 20);
        TFI.track("Product", product, "price", "stock");
        
        // 不修改任何值
        
        // When
        TFI.stop();
        TFI.exportToConsole();
        
        // Then
        String output = capturedOut.toString();
        System.setOut(originalOut);
        
        // 不应包含任何变更消息
        assertFalse(output.contains("Product.price"));
        assertFalse(output.contains("Product.stock"));
        assertFalse(output.contains("→"));
    }
}