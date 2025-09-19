# TaskFlowInsight 入门指南 🚀

> **5分钟从零到运行** - 这是你开始使用TaskFlowInsight的第一步！

## 🎯 这个指南适合谁？

- 第一次接触TaskFlowInsight的开发者
- 想要快速体验功能的技术决策者
- 需要集成到现有项目的工程师

## 📋 前置要求

```bash
✅ Java 21 或更高版本
✅ Maven 3.6+ 或 Gradle 7+
✅ 任意IDE (推荐 IntelliJ IDEA 或 VS Code)
```

**检查你的环境：**
```bash
java -version   # 应该显示 21.x.x
mvn -version    # 应该显示 3.6+
```

## 🚀 30秒快速体验

### 步骤 1: 克隆项目
```bash
git clone https://github.com/shiyongyin/TaskFlowInsight.git
cd TaskFlowInsight
```

### 步骤 2: 一键运行演示
```bash
./mvnw exec:java -Dexec.mainClass="com.syy.taskflowinsight.demo.TaskFlowInsightDemo"
```

**期待的输出：**
```
[订单-12345] 创建订单流程 ━━━━━━━━━━━━━━━━━━━━━ 234ms
│
├─ 📝 参数校验 .......................... 12ms ✓
├─ 📦 库存检查 .......................... 45ms ✓
│  └─ SKU-001: 100 → 99 (扣减成功)
├─ 💰 价格计算 ......................... 177ms ✓
└─ 📧 通知发送 .......................... 23ms ✓
```

**🎉 恭喜！你已经成功运行了TaskFlowInsight！**

## 🔧 集成到你的项目

### 方式一：Spring Boot项目 (推荐)

#### 1. 添加依赖
```xml
<!-- Maven -->
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>TaskFlowInsight</artifactId>
    <version>2.1.0</version>
</dependency>
```

```gradle
// Gradle
implementation 'com.syy:TaskFlowInsight:2.1.0'
```

#### 2. 配置文件
```yaml
# application.yml
tfi:
  enabled: true
  auto-export: true
  max-sessions: 1000
  
management:
  endpoints:
    web:
      exposure:
        include: "*"
```

#### 3. 第一个追踪
```java
@RestController
public class OrderController {
    
    @TfiTask("处理订单")  // 注解方式 - 最简单
    @PostMapping("/orders")
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        
        // 你的业务逻辑保持不变
        Order result = orderService.process(order);
        
        return ResponseEntity.ok(result);
    }
}
```

#### 4. 查看结果
```bash
# 启动应用后访问
curl http://localhost:8080/actuator/tfi/sessions  # 查看所有会话
curl http://localhost:8080/actuator/tfi/metrics   # 查看性能指标
```

### 方式二：普通Java项目

#### 1. 手动初始化
```java
// 在你的应用启动时
TFI.configure()
   .maxSessions(100)
   .autoExport(true)
   .build();
```

#### 2. 编程式使用
```java
public class BusinessService {
    
    public void processOrder(String orderId) {
        TFI.start("订单处理流程");
        
        TFI.stage("参数校验");
        validateOrder(orderId);
        
        TFI.stage("库存检查");
        Order order = checkInventory(orderId);
        TFI.track("order", order);  // 追踪对象变化
        
        TFI.stage("价格计算");
        calculatePrice(order);
        
        TFI.end();  // 自动输出流程树
    }
}
```

## 🎨 三种使用方式对比

| 方式 | 适用场景 | 代码侵入性 | 功能完整度 |
|------|----------|------------|------------|
| **注解驱动** | Spring Boot项目 | 极低 (仅加注解) | ⭐⭐⭐⭐⭐ |
| **编程式API** | 任何Java项目 | 中等 (添加API调用) | ⭐⭐⭐⭐⭐ |
| **监控集成** | 生产环境 | 无 (配置即可) | ⭐⭐⭐⭐ |

## 🔍 实时监控体验

启动项目后，可以通过以下端点查看实时数据：

```bash
# 健康检查
curl http://localhost:19090/actuator/tfi/health

# 性能指标  
curl http://localhost:19090/actuator/tfi/metrics

# 活跃会话
curl http://localhost:19090/actuator/tfi/context

# 导出JSON格式
curl http://localhost:19090/actuator/tfi/export?format=json
```

## 🎯 你的第一个完整示例

创建一个新文件 `MyFirstTfiExample.java`：

```java
package com.example;

import com.syy.taskflowinsight.api.TFI;

public class MyFirstTfiExample {
    public static void main(String[] args) {
        // 开始追踪
        TFI.start("我的第一个TFI示例");
        
        // 模拟业务步骤
        TFI.stage("初始化数据");
        simulateWork(100);
        
        TFI.stage("处理业务逻辑");
        String result = "处理完成";
        TFI.track("result", result);  // 追踪结果
        simulateWork(200);
        
        TFI.stage("保存结果");
        simulateWork(50);
        
        // 结束并自动输出
        TFI.end();
    }
    
    private static void simulateWork(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

运行这个示例：
```bash
javac -cp "target/classes:target/dependency/*" MyFirstTfiExample.java
java -cp ".:target/classes:target/dependency/*" com.example.MyFirstTfiExample
```

## 🎪 进阶体验

### 异步场景追踪
```java
@TfiTask("异步订单处理")
@Async
public CompletableFuture<Order> processOrderAsync(String orderId) {
    // TFI自动处理异步上下文传播
    return CompletableFuture.completedFuture(orderService.process(orderId));
}
```

### 数据脱敏保护
```java
@TfiTrack(value = "userInfo", mask = "phone,email")
public void processUserData(User user) {
    // 敏感信息会自动脱敏：phone: 138****1234
}
```

### 错误处理
```java
try {
    riskyOperation();
} catch (Exception e) {
    TFI.error("支付失败", e);  // 错误会被完整记录
    throw e;
}
```

## 📚 下一步学习

1. **[查看更多示例](EXAMPLES.md)** - 电商、审批流、数据同步等实际场景
2. **[部署到生产环境](DEPLOYMENT.md)** - 生产级配置和最佳实践
3. **[性能调优](docs/PERFORMANCE-TUNING.md)** - 大规模使用的优化建议
4. **[API参考](docs/api/README.md)** - 完整的API文档

## ❓ 遇到问题？

- **[常见问题FAQ](FAQ.md)** - 90%的问题都能在这里找到答案
- **[故障排除](TROUBLESHOOTING.md)** - 详细的问题诊断指南
- **[GitHub Issues](https://github.com/shiyongyin/TaskFlowInsight/issues)** - 报告Bug或请求新功能

## 🎊 欢迎反馈

如果这个指南帮助到了你，请给我们一个 ⭐ Star！

如果遇到任何问题，欢迎：
- 提交 [Issue](https://github.com/shiyongyin/TaskFlowInsight/issues)
- 参与 [Discussions](https://github.com/shiyongyin/TaskFlowInsight/discussions)
- 贡献代码 [Pull Request](https://github.com/shiyongyin/TaskFlowInsight/pulls)

---

**恭喜！🎉 你已经掌握了TaskFlowInsight的基础使用。现在开始将它集成到你的项目中吧！**