package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TFI API集成测试
 * 测试真实场景下的API使用
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
@SpringBootTest
class TFIIntegrationTest {
    
    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.clear();
    }
    
    @AfterEach
    void tearDown() {
        TFI.clear();
    }
    
    /**
     * 模拟Web请求处理场景
     */
    @Test
    void testWebRequestHandling() {
        // 开始请求会话
        TFI.startSession("HTTP Request - GET /api/users");
        
        // 请求处理主流程
        try (TaskContext request = TFI.start("Handle Request")) {
            request.attribute("method", "GET")
                   .attribute("path", "/api/users")
                   .attribute("ip", "192.168.1.100");
            
            // 认证阶段
            try (TaskContext auth = request.subtask("Authentication")) {
                auth.message("Checking JWT token")
                    .attribute("userId", "user123")
                    .success();
            }
            
            // 授权阶段
            try (TaskContext authz = request.subtask("Authorization")) {
                authz.message("Checking permissions")
                     .attribute("role", "admin")
                     .success();
            }
            
            // 业务逻辑处理
            try (TaskContext business = request.subtask("Business Logic")) {
                business.message("Fetching user list from database");
                
                // 模拟数据库查询
                try (TaskContext db = business.subtask("Database Query")) {
                    db.message("SELECT * FROM users")
                      .attribute("rows", 25)
                      .attribute("time", "15ms")
                      .success();
                }
                
                business.message("Processing results")
                        .success();
            }
            
            // 响应生成
            try (TaskContext response = request.subtask("Generate Response")) {
                response.message("Serializing to JSON")
                        .attribute("status", 200)
                        .attribute("size", "2.5KB")
                        .success();
            }
            
            request.success();
        }
        
        // 验证会话结构
        Session session = TFI.getCurrentSession();
        assertThat(session).isNotNull();
        // Session类没有getTotalTasks和getMaxDepth方法，通过rootTask验证
        TaskNode rootTask = session.getRootTask();
        assertThat(rootTask).isNotNull();
        assertThat(rootTask.getChildren()).isNotEmpty();
        
        TFI.endSession();
    }
    
    /**
     * 模拟批处理任务场景
     */
    @Test
    void testBatchProcessing() {
        TFI.startSession("Batch Processing Job");
        
        List<String> items = List.of("item1", "item2", "item3", "item4", "item5");
        List<String> results = new ArrayList<>();
        
        try (TaskContext batch = TFI.start("Process Batch")) {
            batch.attribute("totalItems", items.size())
                 .message("Starting batch processing");
            
            for (int i = 0; i < items.size(); i++) {
                String item = items.get(i);
                
                try (TaskContext itemTask = batch.subtask("Process " + item)) {
                    itemTask.attribute("index", i)
                            .attribute("item", item);
                    
                    // 模拟处理
                    if (item.equals("item3")) {
                        itemTask.error("Failed to process item3")
                                .fail();
                    } else {
                        itemTask.message("Processing completed")
                                .success();
                        results.add(item + "_processed");
                    }
                }
            }
            
            batch.attribute("successCount", results.size())
                 .attribute("failureCount", items.size() - results.size());
            
            if (results.size() == items.size()) {
                batch.success();
            } else {
                batch.warn("Batch completed with errors");
            }
        }
        
        // 验证处理结果
        assertThat(results).hasSize(4);
        assertThat(results).doesNotContain("item3_processed");
        
        TFI.endSession();
    }
    
    /**
     * 模拟异步任务处理场景
     */
    @Test
    void testAsyncTaskProcessing() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        try {
            TFI.startSession("Async Processing");
            
            // 主任务
            try (TaskContext main = TFI.start("Main Task")) {
                main.message("Starting async operations");
                
                // 创建多个异步任务
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                
                for (int i = 0; i < 3; i++) {
                    final int taskNum = i;
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        TFI.run("Async Task " + taskNum, () -> {
                            TFI.message("Processing in thread: " + Thread.currentThread().getName(), MessageType.PROCESS);
                            TFI.message("Task number: " + taskNum, MessageType.PROCESS);
                            
                            // 模拟处理
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                TFI.error("Task interrupted");
                            }
                            
                            TFI.message("Async task completed", MessageType.PROCESS);
                        });
                    }, executor);
                    
                    futures.add(future);
                }
                
                // 等待所有异步任务完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);
                
                main.message("All async tasks completed")
                    .success();
            }
            
            TFI.endSession();
        } finally {
            executor.shutdown();
        }
    }
    
    /**
     * 模拟错误处理和恢复场景
     */
    @Test
    void testErrorHandlingAndRecovery() {
        TFI.startSession("Error Handling Test");
        
        try (TaskContext main = TFI.start("Resilient Operation")) {
            main.message("Starting operation with retry logic");
            
            boolean success = false;
            int maxRetries = 3;
            
            for (int attempt = 1; attempt <= maxRetries && !success; attempt++) {
                try (TaskContext retry = main.subtask("Attempt " + attempt)) {
                    retry.attribute("attemptNumber", attempt);
                    
                    // 模拟可能失败的操作
                    if (attempt < 2) {
                        // 前两次失败
                        throw new RuntimeException("Connection timeout");
                    }
                    
                    retry.message("Operation succeeded")
                         .success();
                    success = true;
                    
                } catch (Exception e) {
                    TFI.error("Attempt " + attempt + " failed", e);
                    
                    if (attempt < maxRetries) {
                        TFI.message("Retrying after failure...", MessageType.ALERT);
                        try {
                            Thread.sleep(100); // 退避策略
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
            
            if (success) {
                main.message("Operation completed after retries")
                    .success();
            } else {
                main.error("Operation failed after all retries")
                    .fail();
            }
        }
        
        TFI.endSession();
    }
    
    /**
     * 测试复杂的嵌套任务结构
     */
    @Test
    void testComplexNestedStructure() {
        TFI.startSession("Complex Workflow");
        
        try (TaskContext workflow = TFI.start("Main Workflow")) {
            workflow.tag("complex")
                    .tag("nested");
            
            // 第一阶段
            try (TaskContext phase1 = workflow.subtask("Phase 1: Initialization")) {
                phase1.message("Initializing components");
                
                try (TaskContext init1 = phase1.subtask("Init Database")) {
                    init1.message("Connecting to database").success();
                }
                
                try (TaskContext init2 = phase1.subtask("Init Cache")) {
                    init2.message("Warming up cache").success();
                }
                
                phase1.success();
            }
            
            // 第二阶段
            try (TaskContext phase2 = workflow.subtask("Phase 2: Processing")) {
                phase2.message("Processing data");
                
                try (TaskContext process = phase2.subtask("Data Processing")) {
                    for (int i = 0; i < 3; i++) {
                        try (TaskContext step = process.subtask("Step " + (i + 1))) {
                            step.message("Executing step").success();
                        }
                    }
                    process.success();
                }
                
                phase2.success();
            }
            
            // 第三阶段
            try (TaskContext phase3 = workflow.subtask("Phase 3: Cleanup")) {
                phase3.message("Cleaning up resources").success();
            }
            
            workflow.success();
        }
        
        // 导出并验证结构
        Map<String, Object> exported = TFI.exportToMap();
        assertThat(exported).isNotEmpty();
        assertThat(exported).containsKey("tasks");
        
        TFI.endSession();
    }
    
    /**
     * 性能测试 - 大量任务
     */
    @Test
    void testPerformanceWithManyTasks() {
        TFI.startSession("Performance Test");
        
        long startTime = System.currentTimeMillis();
        
        try (TaskContext perf = TFI.start("Performance Test")) {
            for (int i = 0; i < 1000; i++) {
                TFI.message("Message " + i, MessageType.PROCESS);
                
                if (i % 100 == 0) {
                    try (TaskContext milestone = perf.subtask("Milestone " + (i / 100))) {
                        milestone.attribute("progress", i)
                                 .message("Reached milestone")
                                 .success();
                    }
                }
            }
            
            perf.success();
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        // 验证性能 - 1000个消息应该在合理时间内完成
        assertThat(duration).isLessThan(1000); // 小于1秒
        
        Session session = TFI.getCurrentSession();
        // Session类没有getTotalTasks方法，只验证会话存在
        assertThat(session).isNotNull();
        
        TFI.endSession();
    }
    
    /**
     * 测试导出功能的完整性
     */
    @Test
    void testExportCompleteness() {
        TFI.startSession("Export Test Session");
        
        try (TaskContext task = TFI.start("Test Task")) {
            task.message("Info message")
                .debug("Debug message")
                .warn("Warning message")
                .error("Error message")
                .attribute("stringAttr", "value")
                .attribute("numberAttr", 42)
                .attribute("booleanAttr", true)
                .tag("tag1")
                .tag("tag2");
            
            try (TaskContext subtask = task.subtask("Subtask")) {
                subtask.message("Subtask message").success();
            }
            
            task.success();
        }
        
        // 测试JSON导出
        String json = TFI.exportToJson();
        assertThat(json).isNotNull();
        assertThat(json).contains("Test Task");
        assertThat(json).contains("Subtask");
        assertThat(json).contains("Info message");
        
        // 测试Map导出
        Map<String, Object> map = TFI.exportToMap();
        assertThat(map).isNotEmpty();
        assertThat(map).containsKeys("sessionId", "sessionName", "tasks");
        
        // 测试控制台导出（不应该抛出异常）
        TFI.exportToConsole();
        
        TFI.endSession();
    }
    
    // ==================== 变更追踪集成测试 ====================
    
    /**
     * 测试用户对象（用于变更追踪）
     */
    public static class TestUser {
        public String name;
        public int age;
        public String email;
        
        public TestUser() {}
        
        public TestUser(String name, int age, String email) {
            this.name = name;
            this.age = age;
            this.email = email;
        }
        
        public void setName(String name) { this.name = name; }
        public void setAge(int age) { this.age = age; }
        public void setEmail(String email) { this.email = email; }
    }

    /**
     * 测试产品对象（用于变更追踪）
     */
    public static class TestProduct {
        public String name;
        public double price;
        public int stock;
        
        public TestProduct() {}
        
        public TestProduct(String name, double price, int stock) {
            this.name = name;
            this.price = price;
            this.stock = stock;
        }
        
        public void setName(String name) { this.name = name; }
        public void setPrice(double price) { this.price = price; }
        public void setStock(int stock) { this.stock = stock; }
    }
    
    /**
     * 场景：基础变更追踪流程 - 单对象追踪
     */
    @Test
    void testBasicChangeTrackingFlow() {
        // 启用变更追踪
        TFI.setChangeTrackingEnabled(true);
        
        TFI.startSession("Change Tracking Test");
        
        try (TaskContext task = TFI.start("Basic Change Tracking")) {
            // 创建测试对象
            TestUser user = new TestUser("Alice", 25, "alice@example.com");
            
            // 开始追踪
            TFI.track("user", user);
            task.message("Started tracking user object");
            
            // 修改对象
            user.setAge(26);
            user.setEmail("alice.new@example.com");
            task.message("Modified user age and email");
            
            // 获取变更记录
            List<ChangeRecord> changes = TFI.getChanges();
            assertThat(changes).hasSize(2);
            
            // 验证变更内容
            assertThat(changes).extracting(ChangeRecord::getFieldName)
                              .containsExactlyInAnyOrder("age", "email");
            
            ChangeRecord ageChange = changes.stream()
                .filter(c -> "age".equals(c.getFieldName()))
                .findFirst()
                .orElseThrow();
            
            assertThat(ageChange.getOldValue()).isEqualTo(25);
            assertThat(ageChange.getNewValue()).isEqualTo(26);
            assertThat(ageChange.getObjectName()).isEqualTo("user");
            
            task.attribute("changesDetected", changes.size())
                .success();
        } // 结束任务会自动刷新变更到任务节点
        
        TFI.endSession();
    }
    
    /**
     * 场景：批量变更追踪 - 多对象并发追踪
     */
    @Test
    void testBatchChangeTracking() {
        TFI.setChangeTrackingEnabled(true);
        TFI.startSession("Batch Change Tracking");
        
        try (TaskContext task = TFI.start("Batch Tracking")) {
            // 创建多个测试对象
            TestUser user1 = new TestUser("Bob", 30, "bob@example.com");
            TestUser user2 = new TestUser("Charlie", 35, "charlie@example.com");
            TestProduct product = new TestProduct("Laptop", 999.99, 10);
            
            // 批量追踪
            Map<String, Object> targets = Map.of(
                "user1", user1,
                "user2", user2,
                "product", product
            );
            TFI.trackAll(targets);
            task.message("Started batch tracking for 3 objects");
            
            // 批量修改
            user1.setAge(31);
            user1.setName("Robert");
            user2.setEmail("charlie.updated@example.com");
            product.setPrice(899.99);
            product.setStock(8);
            
            task.message("Applied changes to all tracked objects");
            
            // 获取所有变更
            List<ChangeRecord> allChanges = TFI.getChanges();
            assertThat(allChanges).hasSize(5);
            
            // 验证变更分布
            Map<String, Long> changesByObject = allChanges.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    ChangeRecord::getObjectName,
                    java.util.stream.Collectors.counting()
                ));
            
            assertThat(changesByObject)
                .containsEntry("user1", 2L)  // age + name
                .containsEntry("user2", 1L)  // email
                .containsEntry("product", 2L); // price + stock
            
            task.attribute("totalChanges", allChanges.size())
                .attribute("objectsChanged", changesByObject.size())
                .success();
        }
        
        TFI.endSession();
    }
    
    /**
     * 场景：Lambda方式变更追踪 - 自动生命周期管理
     */
    @Test
    void testLambdaChangeTracking() {
        TFI.setChangeTrackingEnabled(true);
        TFI.startSession("Lambda Change Tracking");
        
        try (TaskContext task = TFI.start("Lambda Tracking")) {
            TestUser user = new TestUser("Dave", 40, "dave@example.com");
            
            // 使用lambda方式追踪（自动管理生命周期）
            TFI.withTracked("user", user, () -> {
                user.setAge(41);
                user.setEmail("dave.updated@example.com");
                task.message("Modified user in lambda context");
            });
            
            // 验证变更已被自动处理和清理
            List<ChangeRecord> remainingChanges = TFI.getChanges();
            assertThat(remainingChanges).isEmpty();
            
            // 验证任务节点收到了变更消息
            TaskNode currentTask = TFI.getCurrentTask();
            assertThat(currentTask).isNotNull();
            
            boolean hasChangeMessage = currentTask.getMessages().stream()
                .anyMatch(msg -> msg.getType() == MessageType.CHANGE);
            assertThat(hasChangeMessage).isTrue();
            
            task.message("Lambda tracking completed successfully")
                .success();
        }
        
        TFI.endSession();
    }
    
    /**
     * 场景：变更追踪异常处理 - 禁用状态和错误恢复
     */
    @Test
    void testChangeTrackingErrorHandling() {
        TFI.startSession("Change Tracking Error Handling");
        
        try (TaskContext task = TFI.start("Error Handling Test")) {
            TestUser user = new TestUser("Eve", 28, "eve@example.com");
            
            // 测试禁用状态
            TFI.setChangeTrackingEnabled(false);
            TFI.track("user", user); // 应该安全无操作
            user.setAge(29);
            
            List<ChangeRecord> noChanges = TFI.getChanges();
            assertThat(noChanges).isEmpty();
            task.message("Verified disabled state handling");
            
            // 重新启用追踪
            TFI.setChangeTrackingEnabled(true);
            
            // 测试无效参数处理
            TFI.track(null, user); // null名称
            TFI.track("user", null); // null对象
            TFI.track("", user); // 空名称
            TFI.track("  ", user); // 空白名称
            
            // 这些操作不应抛出异常
            List<ChangeRecord> stillNoChanges = TFI.getChanges();
            assertThat(stillNoChanges).isEmpty();
            task.message("Verified invalid parameter handling");
            
            // 测试正常追踪恢复
            TFI.track("user", user);
            user.setAge(30);
            
            List<ChangeRecord> recoveredChanges = TFI.getChanges();
            assertThat(recoveredChanges).hasSize(1);
            assertThat(recoveredChanges.get(0).getFieldName()).isEqualTo("age");
            
            task.message("Verified tracking recovery after errors")
                .success();
        }
        
        TFI.endSession();
    }
    
    /**
     * 场景：多层任务变更追踪 - 嵌套任务中的变更处理
     */
    @Test
    void testNestedTaskChangeTracking() {
        TFI.setChangeTrackingEnabled(true);
        TFI.startSession("Nested Task Change Tracking");
        
        try (TaskContext level1 = TFI.start("Level 1 Task")) {
            TestUser user = new TestUser("Frank", 45, "frank@example.com");
            TFI.track("level1-user", user);
            
            user.setAge(46);
            level1.message("Modified user in level 1");
            
            try (TaskContext level2 = level1.subtask("Level 2 Task")) {
                TestProduct product = new TestProduct("Phone", 599.99, 20);
                TFI.track("level2-product", product);
                
                product.setPrice(549.99);
                level2.message("Modified product in level 2");
                
                try (TaskContext level3 = level2.subtask("Level 3 Task")) {
                    user.setEmail("frank.level3@example.com");
                    product.setStock(18);
                    level3.message("Modified both objects in level 3");
                    
                    // 获取所有累积的变更
                    List<ChangeRecord> allChanges = TFI.getChanges();
                    assertThat(allChanges).hasSize(4);
                    
                    // 验证变更来源
                    long userChanges = allChanges.stream()
                        .filter(c -> "level1-user".equals(c.getObjectName()))
                        .count();
                    long productChanges = allChanges.stream()
                        .filter(c -> "level2-product".equals(c.getObjectName()))
                        .count();
                    
                    assertThat(userChanges).isEqualTo(2); // age + email
                    assertThat(productChanges).isEqualTo(2); // price + stock
                    
                    level3.attribute("totalChanges", allChanges.size())
                          .success();
                }
                level2.success();
            }
            level1.success();
        } // 所有变更会在level1结束时刷新到任务节点
        
        TFI.endSession();
    }
    
    /**
     * 场景：大规模变更追踪性能测试
     */
    @Test
    void testLargeScaleChangeTracking() {
        TFI.setChangeTrackingEnabled(true);
        TFI.startSession("Large Scale Change Tracking");
        
        try (TaskContext task = TFI.start("Performance Test")) {
            long startTime = System.currentTimeMillis();
            
            // 创建大量对象进行追踪
            Map<String, Object> targets = new java.util.HashMap<>();
            List<TestUser> users = new ArrayList<>();
            
            for (int i = 0; i < 50; i++) { // 适度规模，避免过度测试
                TestUser user = new TestUser("User" + i, 20 + i, "user" + i + "@test.com");
                users.add(user);
                targets.put("user" + i, user);
            }
            
            // 批量追踪
            TFI.trackAll(targets);
            task.message("Started tracking " + targets.size() + " objects");
            
            // 批量修改
            for (int i = 0; i < users.size(); i++) {
                TestUser user = users.get(i);
                user.setAge(user.age + 1);
                if (i % 2 == 0) {
                    user.setEmail("updated.user" + i + "@test.com");
                }
            }
            
            // 获取变更（预期：50个age变更 + 25个email变更 = 75个变更）
            List<ChangeRecord> changes = TFI.getChanges();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // 验证结果
            assertThat(changes).hasSizeBetween(50, 75); // 至少50个age变更
            assertThat(duration).isLessThan(1000); // 应该在1秒内完成
            
            task.attribute("objectsTracked", targets.size())
                .attribute("changesDetected", changes.size())
                .attribute("durationMs", duration)
                .message("Large scale tracking completed in " + duration + "ms")
                .success();
        }
        
        TFI.endSession();
    }
}