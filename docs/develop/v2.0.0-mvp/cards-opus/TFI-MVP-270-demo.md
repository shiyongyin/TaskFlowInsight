# TFI-MVP-270 Demo示例

## 任务概述
创建ChangeTrackingDemo展示TaskFlowInsight的核心价值，演示显式API与便捷API的用法，提供订单支付场景的完整示例。

## 核心目标
- [ ] 在demo包下创建ChangeTrackingDemo.java
- [ ] 演示显式API和withTracked便捷API
- [ ] 使用订单支付场景展示2个字段变更
- [ ] 提供Console和JSON导出示例
- [ ] 确保示例代码简洁清晰
- [ ] 为README快速入门提供引用

## 实现清单

### 1. 变更追踪Demo主类
```java
package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.core.TFI;
import com.syy.taskflowinsight.core.ChangeTracker;
import com.syy.taskflowinsight.core.ChangeRecord;
import com.syy.taskflowinsight.core.export.ConsoleExporter;
import com.syy.taskflowinsight.core.export.JsonExporter;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * TaskFlowInsight变更追踪Demo示例
 * 
 * 演示如何使用TFI追踪业务对象的变更，包括：
 * 1. 显式API用法（start -> track -> modify -> getChanges -> stop）
 * 2. 便捷API用法（withTracked一站式处理）
 * 3. Console和JSON导出示例
 */
public class ChangeTrackingDemo {
    
    public static void main(String[] args) {
        System.out.println("=== TaskFlowInsight变更追踪Demo ===");
        System.out.println();
        
        // Demo 1: 显式API示例
        demonstrateExplicitAPI();
        
        System.out.println();
        System.out.println("=" .repeat(50));
        System.out.println();
        
        // Demo 2: 便捷API示例
        demonstrateConvenientAPI();
        
        System.out.println();
        System.out.println("=" .repeat(50));
        System.out.println();
        
        // Demo 3: 复杂场景示例
        demonstrateComplexScenario();
    }
    
    /**
     * Demo 1: 显式API使用示例
     * 适用于需要精细控制追踪生命周期的场景
     */
    private static void demonstrateExplicitAPI() {
        System.out.println("🔍 Demo 1: 显式API使用示例");
        System.out.println("场景：订单支付流程追踪");
        System.out.println();
        
        // 1. 启动追踪器
        ChangeTracker tracker = TFI.start("order-payment-process");
        
        try {
            // 2. 创建订单对象
            Order order = new Order("ORDER-20241210-001", "PENDING", 0.0);
            Payment payment = new Payment("PAY-001", "PENDING", 0.0);
            
            System.out.println("📋 初始状态:");
            System.out.println("   订单: " + order);
            System.out.println("   支付: " + payment);
            System.out.println();
            
            // 3. 开始追踪对象
            tracker.track("order", order);
            tracker.track("payment", payment);
            
            // 4. 模拟业务逻辑 - 订单确认
            System.out.println("⚡ 执行业务逻辑：订单确认与支付");
            order.setStatus("CONFIRMED");
            order.setAmount(299.99);
            
            // 5. 模拟支付处理
            payment.setStatus("PROCESSING");
            payment.setAmount(299.99);
            
            System.out.println("📋 变更后状态:");
            System.out.println("   订单: " + order);
            System.out.println("   支付: " + payment);
            System.out.println();
            
            // 6. 获取变更记录
            List<ChangeRecord> changes = tracker.getChanges();
            
            System.out.println("📊 检测到 " + changes.size() + " 个字段变更:");
            for (ChangeRecord change : changes) {
                System.out.println("   • " + formatChange(change));
            }
            System.out.println();
            
            // 7. Console导出示例
            System.out.println("📺 Console导出:");
            ConsoleExporter.exportCurrentTask();
            System.out.println();
            
            // 8. JSON导出示例
            System.out.println("📄 JSON导出:");
            String jsonOutput = JsonExporter.exportCurrentTaskAsString();
            System.out.println(jsonOutput);
            
        } finally {
            // 9. 停止追踪器（重要：确保资源清理）
            TFI.stop();
        }
    }
    
    /**
     * Demo 2: 便捷API使用示例
     * 适用于简单场景，自动管理追踪生命周期
     */
    private static void demonstrateConvenientAPI() {
        System.out.println("🚀 Demo 2: 便捷API使用示例");
        System.out.println("场景：订单状态更新");
        System.out.println();
        
        // 创建订单对象
        Order order = new Order("ORDER-20241210-002", "PENDING", 0.0);
        
        System.out.println("📋 初始状态: " + order);
        System.out.println();
        
        // 使用withTracked便捷API
        System.out.println("⚡ 使用withTracked便捷API执行业务逻辑:");
        TFI.withTracked("order-status-update", order, () -> {
            
            // 业务逻辑：订单状态流转
            System.out.println("   1. 订单支付中...");
            order.setStatus("PAYING");
            order.setAmount(159.50);
            
            System.out.println("   2. 支付完成");
            order.setStatus("PAID");
            
        }, "status", "amount"); // 指定要追踪的字段
        
        System.out.println("📋 最终状态: " + order);
        System.out.println();
        System.out.println("💡 注意：withTracked已自动完成变更输出和资源清理");
    }
    
    /**
     * Demo 3: 复杂场景示例
     * 演示嵌套任务和多对象追踪
     */
    private static void demonstrateComplexScenario() {
        System.out.println("🏗️ Demo 3: 复杂场景示例");
        System.out.println("场景：电商订单完整流程");
        System.out.println();
        
        ChangeTracker mainTracker = TFI.start("ecommerce-order-flow");
        
        try {
            // 创建业务对象
            Order order = new Order("ORDER-20241210-003", "CART", 0.0);
            Payment payment = new Payment("PAY-003", "PENDING", 0.0);
            Shipping shipping = new Shipping("SHIP-003", "WAITING", "");
            
            System.out.println("📋 初始状态:");
            System.out.println("   订单: " + order);
            System.out.println("   支付: " + payment);
            System.out.println("   物流: " + shipping);
            System.out.println();
            
            // 追踪多个对象
            mainTracker.track("order", order);
            mainTracker.track("payment", payment);
            mainTracker.track("shipping", shipping);
            
            // 第一阶段：订单确认
            System.out.println("🔄 第一阶段：订单确认");
            order.setStatus("CONFIRMED");
            order.setAmount(588.88);
            payment.setStatus("PROCESSING");
            
            // 第二阶段：支付完成
            System.out.println("🔄 第二阶段：支付处理");
            payment.setStatus("SUCCESS");
            payment.setAmount(588.88);
            order.setStatus("PAID");
            
            // 第三阶段：物流安排
            System.out.println("🔄 第三阶段：物流安排");
            shipping.setStatus("PREPARING");
            shipping.setTrackingNumber("SF1234567890");
            order.setStatus("SHIPPED");
            
            System.out.println();
            
            // 获取所有变更
            List<ChangeRecord> allChanges = mainTracker.getChanges();
            System.out.println("📊 复杂流程共检测到 " + allChanges.size() + " 个变更:");
            
            // 按对象分组显示变更
            allChanges.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    change -> change.getFieldPath().split("\\.")[0]))
                .forEach((objectName, changes) -> {
                    System.out.println("   📦 " + objectName + " (" + changes.size() + "个变更):");
                    changes.forEach(change -> 
                        System.out.println("      • " + formatChange(change)));
                });
            
            System.out.println();
            
            // 最终状态展示
            System.out.println("📋 最终状态:");
            System.out.println("   订单: " + order);
            System.out.println("   支付: " + payment);
            System.out.println("   物流: " + shipping);
            System.out.println();
            
            // 完整任务树导出
            System.out.println("🌲 完整任务树 Console导出:");
            ConsoleExporter.exportCurrentTask();
            
        } finally {
            TFI.stop();
        }
    }
    
    /**
     * 格式化变更记录为可读字符串
     */
    private static String formatChange(ChangeRecord change) {
        return String.format("%s: %s → %s", 
            change.getFieldPath().substring(change.getFieldPath().indexOf('.') + 1),
            change.getOldValueRepr(), 
            change.getNewValueRepr());
    }
    
    // ===========================================
    // 业务对象定义
    // ===========================================
    
    /**
     * 订单对象
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Order {
        private String orderId;
        private String status;
        private Double amount;
        
        @Override
        public String toString() {
            return String.format("Order{id='%s', status='%s', amount=%.2f}", 
                orderId, status, amount);
        }
    }
    
    /**
     * 支付对象  
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payment {
        private String paymentId;
        private String status;
        private Double amount;
        
        @Override
        public String toString() {
            return String.format("Payment{id='%s', status='%s', amount=%.2f}", 
                paymentId, status, amount);
        }
    }
    
    /**
     * 物流对象
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Shipping {
        private String shippingId;
        private String status;
        private String trackingNumber;
        
        @Override
        public String toString() {
            return String.format("Shipping{id='%s', status='%s', tracking='%s'}", 
                shippingId, status, trackingNumber);
        }
    }
}
```

### 2. Spring Boot集成Demo
```java
package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.core.TFI;
import com.syy.taskflowinsight.core.ChangeTracker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Boot集成Demo示例
 * 演示在Spring环境中如何使用TFI
 */
@Service
public class SpringIntegrationDemo {
    
    /**
     * 服务层方法示例：用户注册
     */
    @Transactional
    public void registerUser(String username, String email) {
        ChangeTracker tracker = TFI.start("user-registration");
        
        try {
            // 创建用户对象
            User user = new User();
            tracker.track("user", user);
            
            // 业务逻辑
            user.setUsername(username);
            user.setEmail(email);
            user.setStatus("ACTIVE");
            user.setCreatedTime(new java.util.Date());
            
            // 模拟保存到数据库
            saveUser(user);
            
            System.out.println("✅ 用户注册完成，变更已追踪");
            
        } finally {
            TFI.stop();
        }
    }
    
    /**
     * 使用便捷API的服务方法
     */
    public void updateUserProfile(Long userId, String newEmail, String newPhone) {
        // 查询用户
        User user = findUserById(userId);
        
        // 使用便捷API追踪变更
        TFI.withTracked("user-profile-update", user, () -> {
            user.setEmail(newEmail);
            user.setPhone(newPhone);
            user.setUpdatedTime(new java.util.Date());
            
            // 保存变更
            saveUser(user);
            
        }, "email", "phone", "updatedTime");
        
        System.out.println("✅ 用户资料更新完成");
    }
    
    // 模拟数据访问方法
    private void saveUser(User user) {
        // 模拟数据库保存
        System.out.println("💾 保存用户: " + user.getUsername());
    }
    
    private User findUserById(Long userId) {
        // 模拟数据库查询
        User user = new User();
        user.setUserId(userId);
        user.setUsername("john_doe");
        user.setEmail("john@example.com");
        user.setPhone("555-0123");
        user.setStatus("ACTIVE");
        return user;
    }
    
    /**
     * 用户对象
     */
    public static class User {
        private Long userId;
        private String username;
        private String email;
        private String phone;
        private String status;
        private java.util.Date createdTime;
        private java.util.Date updatedTime;
        
        // Getters and Setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public java.util.Date getCreatedTime() { return createdTime; }
        public void setCreatedTime(java.util.Date createdTime) { this.createdTime = createdTime; }
        public java.util.Date getUpdatedTime() { return updatedTime; }
        public void setUpdatedTime(java.util.Date updatedTime) { this.updatedTime = updatedTime; }
    }
}
```

### 3. 异步处理Demo
```java
package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.core.TFI;
import com.syy.taskflowinsight.core.ChangeTracker;
import com.syy.taskflowinsight.core.executor.TFIAwareExecutor;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;

/**
 * 异步处理Demo示例
 * 演示在异步环境中如何正确使用TFI
 */
public class AsyncProcessingDemo {
    
    public static void main(String[] args) throws Exception {
        System.out.println("🔄 异步处理Demo示例");
        System.out.println();
        
        demonstrateAsyncWithTFIAware();
        
        System.out.println();
        System.out.println("=" .repeat(40));
        System.out.println();
        
        demonstrateCompletableFutureIntegration();
    }
    
    /**
     * 使用TFIAware执行器的异步处理
     */
    private static void demonstrateAsyncWithTFIAware() throws Exception {
        System.out.println("📡 TFIAware执行器异步示例");
        
        ChangeTracker mainTracker = TFI.start("async-main-task");
        
        try {
            // 主任务中的对象
            AsyncOrder mainOrder = new AsyncOrder("MAIN-ORDER", "CREATED", 0.0);
            mainTracker.track("mainOrder", mainOrder);
            
            mainOrder.setStatus("PROCESSING");
            
            // 创建TFI感知的执行器
            TFIAwareExecutor executor = new TFIAwareExecutor(
                Executors.newFixedThreadPool(2));
            
            // 提交异步任务
            Future<String> asyncResult = executor.submit(() -> {
                // 子任务有自己的追踪上下文
                ChangeTracker asyncTracker = TFI.start("async-sub-task");
                
                try {
                    AsyncOrder asyncOrder = new AsyncOrder("ASYNC-ORDER", "PENDING", 0.0);
                    asyncTracker.track("asyncOrder", asyncOrder);
                    
                    // 模拟异步处理
                    Thread.sleep(100);
                    asyncOrder.setStatus("COMPLETED");
                    asyncOrder.setAmount(199.99);
                    
                    return "异步任务完成: " + asyncOrder.getOrderId();
                    
                } finally {
                    TFI.stop();
                }
            });
            
            // 等待异步结果
            String result = asyncResult.get();
            System.out.println("✅ " + result);
            
            // 主任务继续
            mainOrder.setStatus("COMPLETED");
            mainOrder.setAmount(299.99);
            
            executor.shutdown();
            
        } finally {
            TFI.stop();
        }
        
        System.out.println("🎯 异步任务追踪完成");
    }
    
    /**
     * CompletableFuture集成示例
     */
    private static void demonstrateCompletableFutureIntegration() {
        System.out.println("⚡ CompletableFuture集成示例");
        
        CompletableFuture
            .supplyAsync(() -> {
                // 第一阶段：订单创建
                return TFI.withTracked("order-creation", new AsyncOrder("CF-ORDER", "NEW", 0.0), 
                    order -> {
                        order.setStatus("CREATED");
                        order.setAmount(399.99);
                        return order;
                    }, "status", "amount");
            })
            .thenCompose(order -> 
                CompletableFuture.supplyAsync(() -> {
                    // 第二阶段：订单处理
                    return TFI.withTracked("order-processing", order, 
                        o -> {
                            try {
                                Thread.sleep(50); // 模拟处理时间
                                o.setStatus("PROCESSED");
                                return o;
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        }, "status");
                })
            )
            .thenAccept(order -> {
                // 第三阶段：完成
                TFI.withTracked("order-completion", order, 
                    o -> o.setStatus("COMPLETED"), "status");
                
                System.out.println("✅ CompletableFuture流程完成: " + order);
            })
            .join(); // 等待完成
        
        System.out.println("🎯 CompletableFuture示例完成");
    }
    
    /**
     * 异步订单对象
     */
    public static class AsyncOrder {
        private String orderId;
        private String status;  
        private Double amount;
        
        public AsyncOrder(String orderId, String status, Double amount) {
            this.orderId = orderId;
            this.status = status;
            this.amount = amount;
        }
        
        // Getters and Setters
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
        
        @Override
        public String toString() {
            return String.format("AsyncOrder{id='%s', status='%s', amount=%.2f}", 
                orderId, status, amount);
        }
    }
}
```

### 4. 单元测试Demo
```java
package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.core.TFI;
import com.syy.taskflowinsight.core.ChangeTracker;
import com.syy.taskflowinsight.core.ChangeRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TFI单元测试Demo示例
 * 演示如何在单元测试中使用TFI
 */
@DisplayName("TFI单元测试Demo")
class TFIUnitTestDemo {
    
    @AfterEach
    void cleanup() {
        // 确保每个测试后清理TFI状态
        TFI.endSession();
    }
    
    @Test
    @DisplayName("基础变更追踪测试")
    void testBasicChangeTracking() {
        // Given: 准备测试对象
        TestProduct product = new TestProduct("PROD-001", "iPhone", 999.99);
        
        // When: 使用TFI追踪变更
        ChangeTracker tracker = TFI.start("product-update-test");
        
        try {
            tracker.track("product", product);
            
            // 执行业务变更
            product.setName("iPhone Pro");
            product.setPrice(1199.99);
            
            List<ChangeRecord> changes = tracker.getChanges();
            
            // Then: 验证变更记录
            assertNotNull(changes, "变更记录不应为空");
            assertEquals(2, changes.size(), "应该有2个字段变更");
            
            // 验证具体变更内容
            boolean hasNameChange = changes.stream()
                .anyMatch(c -> c.getFieldPath().endsWith(".name") 
                    && "iPhone".equals(c.getOldValueRepr())
                    && "iPhone Pro".equals(c.getNewValueRepr()));
            assertTrue(hasNameChange, "应该包含名称变更");
            
            boolean hasPriceChange = changes.stream()
                .anyMatch(c -> c.getFieldPath().endsWith(".price")
                    && "999.99".equals(c.getOldValueRepr()) 
                    && "1199.99".equals(c.getNewValueRepr()));
            assertTrue(hasPriceChange, "应该包含价格变更");
            
        } finally {
            TFI.stop();
        }
    }
    
    @Test
    @DisplayName("便捷API测试")
    void testConvenientAPI() {
        // Given
        TestProduct product = new TestProduct("PROD-002", "iPad", 599.99);
        String originalName = product.getName();
        Double originalPrice = product.getPrice();
        
        // When: 使用便捷API
        TFI.withTracked("convenient-api-test", product, () -> {
            product.setName("iPad Pro");
            product.setPrice(799.99);
        }, "name", "price");
        
        // Then: 验证对象状态已改变
        assertEquals("iPad Pro", product.getName());
        assertEquals(799.99, product.getPrice());
        assertNotEquals(originalName, product.getName());
        assertNotEquals(originalPrice, product.getPrice());
        
        // 注意：便捷API已自动完成追踪和清理，这里主要验证对象状态
    }
    
    @Test
    @DisplayName("无变更场景测试") 
    void testNoChangeScenario() {
        // Given
        TestProduct product = new TestProduct("PROD-003", "MacBook", 1299.99);
        
        // When: 追踪但不做变更
        ChangeTracker tracker = TFI.start("no-change-test");
        
        try {
            tracker.track("product", product);
            
            // 不做任何变更
            List<ChangeRecord> changes = tracker.getChanges();
            
            // Then
            assertNotNull(changes, "变更记录不应为空");
            assertTrue(changes.isEmpty(), "应该没有变更记录");
            
        } finally {
            TFI.stop();
        }
    }
    
    @Test
    @DisplayName("多对象追踪测试")
    void testMultipleObjectTracking() {
        // Given
        TestProduct product = new TestProduct("PROD-004", "Watch", 299.99);
        TestCustomer customer = new TestCustomer("CUST-001", "John Doe", "john@example.com");
        
        // When
        ChangeTracker tracker = TFI.start("multi-object-test");
        
        try {
            tracker.track("product", product);
            tracker.track("customer", customer);
            
            // 修改两个对象
            product.setPrice(349.99);
            customer.setEmail("john.doe@example.com");
            
            List<ChangeRecord> changes = tracker.getChanges();
            
            // Then
            assertEquals(2, changes.size(), "应该有2个变更");
            
            long productChanges = changes.stream()
                .filter(c -> c.getFieldPath().startsWith("product."))
                .count();
            assertEquals(1, productChanges, "产品应该有1个变更");
            
            long customerChanges = changes.stream()
                .filter(c -> c.getFieldPath().startsWith("customer."))
                .count();
            assertEquals(1, customerChanges, "客户应该有1个变更");
            
        } finally {
            TFI.stop();
        }
    }
    
    // 测试数据类
    public static class TestProduct {
        private String productId;
        private String name;
        private Double price;
        
        public TestProduct(String productId, String name, Double price) {
            this.productId = productId;
            this.name = name;
            this.price = price;
        }
        
        // Getters and Setters
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
    }
    
    public static class TestCustomer {
        private String customerId;
        private String name;
        private String email;
        
        public TestCustomer(String customerId, String name, String email) {
            this.customerId = customerId;
            this.name = name;
            this.email = email;
        }
        
        // Getters and Setters  
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
}
```

### 5. README快速入门示例
```markdown
# TaskFlowInsight Quick Start

## 基础用法

### 1. 显式API（推荐用于复杂场景）

```java
import com.syy.taskflowinsight.core.TFI;
import com.syy.taskflowinsight.core.ChangeTracker;

// 启动追踪
ChangeTracker tracker = TFI.start("order-processing");

try {
    // 追踪对象
    Order order = new Order("ORDER-001", "PENDING", 0.0);
    tracker.track("order", order);
    
    // 执行业务逻辑
    order.setStatus("PAID");
    order.setAmount(299.99);
    
    // 查看变更记录（可选）
    List<ChangeRecord> changes = tracker.getChanges();
    
} finally {
    // 停止追踪并输出结果
    TFI.stop();
}
```

### 2. 便捷API（推荐用于简单场景）

```java
Order order = new Order("ORDER-002", "PENDING", 0.0);

TFI.withTracked("order-update", order, () -> {
    order.setStatus("COMPLETED");
    order.setAmount(199.99);
}, "status", "amount");
// 自动完成追踪、输出和清理
```

## 运行Demo

```bash
# 编译项目
./mvnw compile

# 运行完整Demo
java -cp target/classes com.syy.taskflowinsight.demo.ChangeTrackingDemo
```

查看更多示例：`src/main/java/com/syy/taskflowinsight/demo/`
```

## 验证步骤
- [ ] Demo运行无异常
- [ ] Console输出包含CHANGE消息
- [ ] JSON导出符合预期格式
- [ ] 显式API和便捷API都正常工作
- [ ] 多对象追踪场景正常
- [ ] 异步处理示例正常
- [ ] 单元测试示例通过

## 完成标准
- [ ] 示例代码简洁清楚
- [ ] 注释详细易懂
- [ ] README快速入门引用该示例
- [ ] 涵盖常见使用场景
- [ ] 提供Spring Boot集成示例
- [ ] 包含异步处理示例
- [ ] 包含单元测试示例