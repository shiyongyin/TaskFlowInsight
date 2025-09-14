# TFI API 文档

## 概述

TaskFlow Insight 提供了多种 API 方式来满足不同的使用场景：

- **手动 API**: 最灵活的编程接口，适合复杂场景
- **作用域 API**: 简化的函数式接口，自动管理生命周期
- **注解 API**: 声明式接口，零侵入集成
- **AOP 集成**: 切面编程，批量追踪

## API 参考

- [手动 API](./manual-api.md) - 完全手动控制的编程接口
- [作用域 API](./scoped-api.md) - 函数式风格的简化接口
- [注解 API](./annotation-api.md) - 声明式注解驱动接口
- [查询 API](./query-api.md) - 会话查询和导出接口
- [配置 API](./configuration-api.md) - 运行时配置接口

## 使用场景对比

| 场景 | 推荐 API | 优势 | 示例 |
|------|----------|------|------|
| 复杂业务流程追踪 | 手动 API | 精确控制、灵活度高 | 订单处理、支付流程 |
| 简单方法监控 | 作用域 API | 代码简洁、自动管理 | 工具方法、计算函数 |
| 现有代码集成 | 注解 API | 零侵入、批量启用 | 大规模改造 |
| 性能瓶颈定位 | AOP + 查询 API | 全面覆盖、深度分析 | 系统调优 |

## 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>taskflow-insight-core</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Spring Boot Starter (可选) -->
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>taskflow-insight-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 基础使用

```java
import com.syy.taskflowinsight.api.TFI;

public class QuickStartExample {
    
    public void processOrder(Order order) {
        // 方式1: 手动API
        TFI.start("processOrder");
        try {
            validateOrder(order);
            calculatePrice(order);
            saveOrder(order);
        } finally {
            TFI.stop();
            TFI.printTree(); // 输出任务树
        }
    }
    
    // 方式2: 作用域API  
    public void processOrderScoped(Order order) {
        TFI.task("processOrder").execute(() -> {
            validateOrder(order);
            calculatePrice(order);  
            saveOrder(order);
        }).report();
    }
    
    // 方式3: 注解API
    @TFITask("processOrder")
    public void processOrderAnnotated(Order order) {
        validateOrder(order);
        calculatePrice(order);
        saveOrder(order);
    }
}
```

## 核心概念

### 会话 (Session)
一个完整的任务执行上下文，从根任务开始到结束的完整执行树。

### 任务节点 (TaskNode)  
任务树中的单个节点，记录任务的执行信息和性能数据。

### 消息 (Message)
任务执行过程中的业务信息、性能指标、异常信息等。

### 变更记录 (ChangeRecord)
对象状态变化的追踪记录。

## 错误处理

### 异常安全

所有 TFI API 都是异常安全的，不会因为内部错误影响业务逻辑：

```java
public void safeExample() {
    TFI.start("safeTask");
    try {
        // 业务逻辑
        riskyOperation();
    } catch (BusinessException e) {
        TFI.recordException(e);  // 记录异常
        throw e;  // 重新抛出，不影响业务处理
    } finally {
        TFI.stop();  // 即使出现异常也会正确停止
    }
}
```

### 降级策略

当系统资源不足时，TFI 会自动降级：

- **采样降级**: 降低采样率，减少数据收集
- **深度限制**: 限制任务树深度，避免栈溢出  
- **消息截断**: 限制消息数量，防止内存溢出
- **功能禁用**: 在极端情况下完全禁用追踪

## 性能影响

### 开销评估

| 操作类型 | 典型开销 | 备注 |
|---------|----------|------|
| start/stop | ~1-2μs | 不含业务逻辑 |
| 消息记录 | ~0.5μs | 单条简单消息 |
| 变更追踪 | ~5-10μs | 取决于对象复杂度 |
| 导出JSON | ~100ms | 1000节点树形结构 |

### 最佳实践

```java
public class PerformanceBestPractices {
    
    // 好的做法: 合理的任务粒度
    public void goodExample() {
        TFI.start("processUser");  // 粗粒度任务
        
        for (User user : users) {
            processUser(user);  // 不要为每个user都创建任务
        }
        
        TFI.stop();
    }
    
    // 避免: 过细的任务粒度  
    public void avoidExample() {
        for (User user : users) {
            TFI.start("processUser:" + user.getId());  // 过细的粒度
            processUser(user);
            TFI.stop();
        }
    }
    
    // 好的做法: 条件启用
    public void conditionalTracking() {
        if (TFI.isEnabled() && shouldTrace()) {
            TFI.start("expensiveOperation");
            try {
                expensiveOperation();
            } finally {
                TFI.stop();
            }
        } else {
            expensiveOperation();  // 直接执行，无追踪开销
        }
    }
}
```