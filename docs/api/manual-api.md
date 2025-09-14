# 手动 API

手动 API 提供最细粒度的控制，适合复杂的业务场景和精确的性能分析。

## 核心接口

### TFI 主入口

```java
public class TFI {
    // 任务管理
    public static TaskContext start(String taskName);
    public static TaskContext start(String taskName, Map<String, Object> attributes);
    public static void stop();
    public static void stop(Object endContext);
    public static void stop(Object endContext, String strategyId);
    
    // 消息记录
    public static void message(String content);
    public static void message(MessageType type, String content);
    public static void message(MessageType type, String content, Map<String, Object> data);
    
    // 异常处理
    public static void recordException(Throwable throwable);
    public static void recordException(String message, Throwable throwable);
    
    // 变更追踪
    public static void trackChanges(Object target, String... fieldNames);
    public static void recordChange(String fieldPath, Object oldValue, Object newValue);
    
    // 检查点和流程控制
    public static void checkpoint(String message);
    public static void decision(String point, String choice);
    public static void loop(String operation, int count);
    
    // 状态查询
    public static boolean isActive();
    public static TaskContext getCurrentTask();
    public static Session getCurrentSession();
    public static String getCurrentTaskPath();
    
    // 导出和打印
    public static void printTree();
    public static void printTree(PrintOptions options);
    public static String exportJson();
    public static String exportJson(ExportOptions options);
    
    // 配置和控制
    public static void enable();
    public static void disable();
    public static boolean isEnabled();
    public static void clearThreadLocal();
}
```

## 基础用法

### 简单任务追踪

```java
public void processOrder(Order order) {
    TFI.start("processOrder");
    try {
        // 记录业务消息
        TFI.message("开始处理订单: " + order.getId());
        
        // 子任务
        validateOrder(order);
        calculatePrice(order);
        saveOrder(order);
        
        TFI.message("订单处理完成");
    } catch (Exception e) {
        TFI.recordException(e);
        throw e;
    } finally {
        TFI.stop();
    }
}

private void validateOrder(Order order) {
    TFI.start("validateOrder");
    try {
        // 验证逻辑
        if (order.getItems().isEmpty()) {
            TFI.message(MessageType.EXCEPTION, "订单项为空");
            throw new ValidationException("订单项不能为空");
        }
        
        TFI.checkpoint("订单验证通过");
    } finally {
        TFI.stop();
    }
}
```

### 变更追踪

```java
public void updateOrderStatus(Order order, OrderStatus newStatus) {
    TFI.start("updateOrderStatus");
    
    // 追踪对象变更
    TFI.trackChanges(order, "status", "updateTime");
    
    try {
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        order.setUpdateTime(new Date());
        
        // 手动记录变更（可选，trackChanges会自动记录）
        TFI.recordChange("status", oldStatus, newStatus);
        
        TFI.message("订单状态更新: " + oldStatus + " -> " + newStatus);
    } finally {
        TFI.stop();
    }
}
```

### 带属性的任务

```java
public void processPayment(Payment payment) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("paymentId", payment.getId());
    attributes.put("amount", payment.getAmount());
    attributes.put("method", payment.getMethod());
    
    TFI.start("processPayment", attributes);
    try {
        // 支付处理逻辑
        callPaymentGateway(payment);
        
        // 记录指标数据
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("gateway_response_time", gatewayResponseTime);
        metrics.put("transaction_fee", transactionFee);
        TFI.message(MessageType.METRIC, "支付完成", metrics);
        
    } finally {
        TFI.stop();
    }
}
```

### 循环和批处理

```java
public void processBatch(List<Item> items) {
    TFI.start("processBatch");
    try {
        TFI.loop("processItems", items.size());
        
        int successCount = 0;
        int failureCount = 0;
        
        for (Item item : items) {
            TFI.start("processItem");
            try {
                processItem(item);
                successCount++;
                TFI.message("处理成功: " + item.getId());
            } catch (Exception e) {
                failureCount++;
                TFI.recordException("处理失败: " + item.getId(), e);
            } finally {
                TFI.stop();
            }
        }
        
        // 记录批处理结果
        Map<String, Object> result = new HashMap<>();
        result.put("total", items.size());
        result.put("success", successCount);
        result.put("failure", failureCount);
        TFI.message(MessageType.METRIC, "批处理完成", result);
        
    } finally {
        TFI.stop();
    }
}
```

## 高级用法

### 条件性追踪

```java
public void conditionalTracking(User user) {
    boolean shouldTrace = user.isVip() || isDebugMode();
    
    if (shouldTrace) {
        TFI.start("processVipUser");
    }
    
    try {
        // 业务逻辑
        processUser(user);
        
        if (shouldTrace) {
            TFI.message("VIP用户处理完成");
        }
    } finally {
        if (shouldTrace) {
            TFI.stop();
        }
    }
}
```

### 跨方法追踪

```java
public class OrderService {
    
    public void processOrder(Order order) {
        TFI.start("processOrder");
        try {
            step1_validate(order);
            step2_calculate(order);  
            step3_persist(order);
        } finally {
            TFI.stop();
        }
    }
    
    private void step1_validate(Order order) {
        // 继续在同一个会话中添加子任务
        TFI.start("validateOrder");
        try {
            // 验证逻辑
        } finally {
            TFI.stop();
        }
    }
    
    // 其他步骤类似...
}
```

### 异步任务追踪

```java
@Service
public class AsyncOrderService {
    
    public CompletableFuture<Order> processOrderAsync(Order order) {
        // 捕获当前会话信息
        String sessionId = TFI.getCurrentSession().getSessionId();
        String parentTaskPath = TFI.getCurrentTaskPath();
        
        return CompletableFuture.supplyAsync(() -> {
            // 在异步线程中关联父会话
            TFI.startChildSession(sessionId, parentTaskPath);
            TFI.start("asyncProcessOrder");
            
            try {
                // 异步处理逻辑
                return processOrder(order);
            } finally {
                TFI.stop();
                TFI.endSession();
            }
        });
    }
}
```

## 导出和查询

### 控制台输出

```java
public void printReport() {
    // 基础输出
    TFI.printTree();
    
    // 带选项输出
    PrintOptions options = PrintOptions.builder()
        .maxDepth(5)
        .showMessages(true)
        .showMetrics(true)
        .showChanges(true)
        .colorize(true)
        .build();
    
    TFI.printTree(options);
}
```

### JSON 导出

```java
public String exportToJson() {
    // 基础导出
    String json = TFI.exportJson();
    
    // 带选项导出
    ExportOptions options = ExportOptions.builder()
        .maxDepth(10)
        .maxNodes(1000)
        .includeMessages(true)
        .includeChanges(true)
        .prettyPrint(true)
        .build();
    
    return TFI.exportJson(options);
}
```

### 会话查询

```java
public void querySession() {
    // 获取当前会话
    Session current = TFI.getCurrentSession();
    
    // 获取指定会话
    Session session = TFI.getSession("session-uuid");
    
    // 获取线程的最近会话
    List<Session> recent = TFI.getRecentSessions(Thread.currentThread().getId(), 5);
    
    // 遍历任务树
    current.getRoot().walk((node, depth) -> {
        System.out.println("Task: " + node.getName() + 
                          ", Duration: " + node.getAccDurationMs() + "ms");
        return true; // 继续遍历
    });
}
```

## 错误处理

### 异常安全模式

```java
public class SafeUsagePattern {
    
    public void safePattern() {
        TFI.start("safeTask");
        try {
            riskyOperation();
        } catch (Exception e) {
            TFI.recordException(e);
            // 根据需要重新抛出或处理异常
            handleException(e);
        } finally {
            TFI.stop(); // 确保总是调用stop
        }
    }
    
    // 使用 try-with-resources 的安全模式
    public void tryWithResourcesPattern() {
        try (TaskScope scope = TFI.scope("scopedTask")) {
            riskyOperation();
            // 自动调用 stop，即使出现异常
        }
    }
}
```

### 状态检查

```java
public void stateCheckPattern() {
    if (TFI.isEnabled() && TFI.isActive()) {
        TFI.message("当前有活动任务");
    }
    
    // 避免在没有活动任务时调用某些操作
    if (TFI.getCurrentTask() != null) {
        TFI.message("记录消息");
    }
    
    // 检查任务深度，避免过深嵌套
    if (TFI.getCurrentTask().getDepth() > 50) {
        TFI.message(MessageType.WARN, "任务嵌套过深");
    }
}
```

## 性能优化

### 懒加载和条件执行

```java
public class PerformanceOptimized {
    
    public void optimizedLogging() {
        // 避免不必要的字符串拼接
        if (TFI.isEnabled()) {
            TFI.message("Processing user: " + expensiveToString(user));
        }
        
        // 使用懒加载消息
        TFI.message(() -> "Lazy message: " + computeExpensiveInfo());
    }
    
    public void batchOperations(List<Item> items) {
        // 避免为每个item创建任务
        TFI.start("processBatch");
        try {
            // 批量处理，只记录关键信息
            for (int i = 0; i < items.size(); i++) {
                processItem(items.get(i));
                
                // 每100个记录一次进度
                if (i % 100 == 0) {
                    TFI.message("Processed: " + i + "/" + items.size());
                }
            }
        } finally {
            TFI.stop();
        }
    }
}
```