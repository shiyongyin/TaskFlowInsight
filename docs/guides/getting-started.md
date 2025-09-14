# 快速开始指南

## 概述

本指南将帮助您在 5 分钟内快速上手 TaskFlow Insight，体验任务追踪和性能分析功能。

## 前置条件

- JDK 8 或更高版本
- Maven 3.6+ 或 Gradle 6.0+
- IDE（推荐 IntelliJ IDEA 或 VSCode）

## 步骤 1: 添加依赖

### Maven 项目

```xml
<dependencies>
    <!-- TFI Core -->
    <dependency>
        <groupId>com.syy</groupId>
        <artifactId>taskflow-insight-core</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Spring Boot Starter (可选，推荐) -->
    <dependency>
        <groupId>com.syy</groupId>
        <artifactId>taskflow-insight-spring-boot-starter</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

### Gradle 项目

```gradle
dependencies {
    implementation 'com.syy:taskflow-insight-core:1.0.0'
    // Spring Boot 项目
    implementation 'com.syy:taskflow-insight-spring-boot-starter:1.0.0'
}
```

## 步骤 2: 基础配置

### Spring Boot 项目

创建 `application.yml`:

```yaml
tfi:
  enabled: true
  auto-track:
    enabled: true
    packages:
      - com.example  # 替换为您的包名
  exporters:
    console:
      enabled: true
```

### 普通 Java 项目

```java
public class TFIConfig {
    static {
        // 启用 TFI
        TFI.enable();
    }
}
```

## 步骤 3: 编写第一个追踪示例

### 方式一: 手动 API

```java
public class OrderService {
    
    public void processOrder(String orderId) {
        TFI.start("processOrder");
        try {
            TFI.message("开始处理订单: " + orderId);
            
            // 子任务
            validateOrder(orderId);
            calculatePrice(orderId);
            saveOrder(orderId);
            
            TFI.message("订单处理完成");
        } catch (Exception e) {
            TFI.recordException(e);
            throw e;
        } finally {
            TFI.stop();
            
            // 输出任务树
            TFI.printTree();
        }
    }
    
    private void validateOrder(String orderId) {
        TFI.start("validateOrder");
        try {
            Thread.sleep(50); // 模拟验证耗时
            TFI.checkpoint("订单验证通过");
        } catch (InterruptedException e) {
            TFI.recordException(e);
        } finally {
            TFI.stop();
        }
    }
    
    private void calculatePrice(String orderId) {
        TFI.start("calculatePrice");
        try {
            Thread.sleep(100); // 模拟计算耗时
            TFI.message("价格计算完成: 199.99");
        } catch (InterruptedException e) {
            TFI.recordException(e);
        } finally {
            TFI.stop();
        }
    }
    
    private void saveOrder(String orderId) {
        TFI.start("saveOrder");
        try {
            Thread.sleep(30); // 模拟保存耗时
            TFI.message("订单保存成功");
        } catch (InterruptedException e) {
            TFI.recordException(e);
        } finally {
            TFI.stop();
        }
    }
}
```

### 方式二: 注解 API (推荐)

```java
@Service
public class OrderService {
    
    @TFITask("processOrder")
    public void processOrder(String orderId) {
        TFI.message("开始处理订单: " + orderId);
        
        validateOrder(orderId);
        calculatePrice(orderId);
        saveOrder(orderId);
        
        TFI.message("订单处理完成");
    }
    
    @TFITask("validateOrder")
    private void validateOrder(String orderId) {
        try {
            Thread.sleep(50);
            TFI.checkpoint("订单验证通过");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    
    @TFITask("calculatePrice")
    private void calculatePrice(String orderId) {
        try {
            Thread.sleep(100);
            TFI.message("价格计算完成: 199.99");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    
    @TFITask("saveOrder")
    private void saveOrder(String orderId) {
        try {
            Thread.sleep(30);
            TFI.message("订单保存成功");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
```

## 步骤 4: 运行和查看结果

### 创建测试类

```java
public class QuickStartTest {
    public static void main(String[] args) {
        OrderService orderService = new OrderService();
        orderService.processOrder("ORDER-001");
    }
}
```

### 期望输出

```
═══════════════════════════════════════════════════════
  TaskFlow Insight Report
  Time: 2024-12-28 10:30:45
  Thread: main
═══════════════════════════════════════════════════════

processOrder (185ms)
├── validateOrder (52ms)
│   └── [CHECKPOINT] 订单验证通过
├── calculatePrice (103ms)
│   └── [INFO] 价格计算完成: 199.99
└── saveOrder (32ms)
    └── [INFO] 订单保存成功

📊 Performance Summary:
  - Total Time: 185ms
  - Self Time: 0ms
  - Critical Path: processOrder → calculatePrice

💡 Messages:
  - [INFO] 开始处理订单: ORDER-001
  - [INFO] 订单处理完成

═══════════════════════════════════════════════════════
```

## 步骤 5: 高级功能体验

### 变更追踪

```java
@TFITask("updateOrder")
public void updateOrderStatus(Order order) {
    // 追踪对象变更
    TFI.trackChanges(order, "status", "updateTime");
    
    order.setStatus(OrderStatus.CONFIRMED);
    order.setUpdateTime(new Date());
    
    TFI.message("订单状态已更新");
}
```

### JSON 导出

```java
public void exportReport() {
    // 执行业务逻辑
    orderService.processOrder("ORDER-002");
    
    // 导出 JSON 报告
    String json = TFI.exportJson();
    System.out.println(json);
    
    // 或保存到文件
    Files.write(Paths.get("report.json"), json.getBytes());
}
```

### Web 端点监控 (Spring Boot)

```java
@RestController
public class OrderController {
    
    @Autowired
    private OrderService orderService;
    
    @TFITask("processOrderEndpoint")
    @PostMapping("/orders/{orderId}/process")
    public ResponseEntity<String> processOrder(@PathVariable String orderId) {
        orderService.processOrder(orderId);
        return ResponseEntity.ok("订单处理完成");
    }
}
```

访问 `http://localhost:8080/actuator/tfi` 查看实时监控数据。

## 步骤 6: 配置优化

### 性能调优

```yaml
tfi:
  performance:
    sampling-rate: 0.1      # 10% 采样，减少开销
    async-export: true      # 异步导出
    threshold-ms: 100       # 只追踪耗时超过100ms的任务
    
  tracking:
    max-depth: 20           # 限制最大嵌套深度
    max-messages-per-task: 100  # 限制消息数量
```

### 生产环境配置

```yaml
tfi:
  enabled: true
  performance:
    sampling-rate: 0.01     # 1% 采样
  exporters:
    console:
      enabled: false        # 禁用控制台输出
    json:
      enabled: true         # 启用文件导出
      output-dir: /var/log/tfi
  integrations:
    micrometer:
      enabled: true         # 启用指标集成
```

## 常见问题

### Q: 如何减少性能影响？

A: 调整采样率和启用异步导出：

```yaml
tfi:
  performance:
    sampling-rate: 0.1
    async-export: true
```

### Q: 如何在生产环境中使用？

A: 建议使用低采样率和文件导出：

```yaml
tfi:
  performance:
    sampling-rate: 0.01
  exporters:
    console:
      enabled: false
    json:
      enabled: true
```

### Q: 如何集成到现有项目？

A: 渐进式采用，先在关键路径启用：

```java
// 只在VIP用户或调试模式下启用
@TFITask(condition = "#{user.vip or @environment.getProperty('debug') == 'true'}")
public void processVipOrder(Order order) { ... }
```

### Q: 如何查看历史数据？

A: 使用会话查询 API：

```java
// 查看最近的会话
List<Session> recent = TFI.getRecentSessions(Thread.currentThread().getId(), 10);

// 查看指定会话
Session session = TFI.getSession("session-uuid");
```

## 下一步

- 阅读 [API 文档](../api/) 了解更多功能
- 查看 [集成指南](../integration/) 学习与其他框架集成
- 参考 [最佳实践](./best-practices.md) 优化使用方式
- 探索 [示例项目](https://github.com/your-org/tfi-examples) 获取更多灵感

## 技术支持

- 📖 [文档中心](https://taskflow-insight.io/docs)
- 💬 [GitHub Discussions](https://github.com/your-org/taskflow-insight/discussions)
- 🐛 [问题反馈](https://github.com/your-org/taskflow-insight/issues)
- 📧 [邮件支持](mailto:support@taskflow-insight.io)