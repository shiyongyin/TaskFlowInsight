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
<version>0.0.1-SNAPSHOT</version>
</dependency>
```

```gradle
// Gradle
implementation 'com.syy:TaskFlowInsight:0.0.1-SNAPSHOT'
```

#### 2. 配置文件
```yaml
# application.yml
tfi:
  enabled: true
  max-sessions: 1000
  
management:
  endpoints:
    web:
      exposure:
        include: ["health","info","taskflow","prometheus"]

server:
  port: 19090
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
# 启动应用后访问（默认 19090 端口）
curl http://localhost:19090/actuator/taskflow          # TFI 概览（只读）
curl http://localhost:19090/tfi/metrics/summary        # 指标摘要（REST 控制器）
```

### 方式二：普通Java项目

无需手动初始化，TFI 在非 Spring 环境会使用安全的默认配置。

#### 编程式使用
```java
public class BusinessService {
    
    public void processOrder(String orderId) {
        TFI.start("订单处理流程");
        try {
            try (var s = TFI.stage("参数校验")) {
                validateOrder(orderId);
            }

            try (var s = TFI.stage("库存检查")) {
                Order order = checkInventory(orderId);
                TFI.track("order", order);  // 追踪对象变化
            }

            try (var s = TFI.stage("价格计算")) {
                calculatePrice(order);
            }
        } finally {
            TFI.stop();
            TFI.exportToConsole(); // 可选：输出流程树
        }
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
# 健康检查（Spring 通用）
curl http://localhost:19090/actuator/health

# TFI 概览（只读、安全脱敏）
curl http://localhost:19090/actuator/taskflow

# 指标摘要（REST 控制器）
curl http://localhost:19090/tfi/metrics/summary

# 上下文诊断（按需开启）
curl http://localhost:19090/actuator/taskflow-context
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
        try {
            // 模拟业务步骤
            try (var s = TFI.stage("初始化数据")) {
                simulateWork(100);
            }

            try (var s = TFI.stage("处理业务逻辑")) {
                String result = "处理完成";
                TFI.track("result", result);  // 追踪结果
                simulateWork(200);
            }

            try (var s = TFI.stage("保存结果")) {
                simulateWork(50);
            }
        } finally {
            // 结束并输出
            TFI.stop();
            TFI.exportToConsole();
        }
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
public void processUserData(User user) {
    // 示例：追踪用户对象，敏感字段建议通过排除字段或统一脱敏策略处理
    TFI.track("userInfo", user);
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

## ⚡ 列表对比与渲染（一行用法）

最简用法：一行对比 + 一行渲染（自动识别实体列表，输出 Markdown 报告）。

```java
@Autowired
private com.syy.taskflowinsight.api.TfiListDiffFacade listDiff;

List<User> oldList = List.of(new User(1L, "Alice"), new User(2L, "Bob"));
List<User> newList = List.of(new User(1L, "Alice"), new User(3L, "Charlie"));

// 一行对比
var result = listDiff.diff(oldList, newList);

// 一行渲染（标准样式，Markdown）
String report = listDiff.render(result);
System.out.println(report);
```

也可以使用静态入口（需要 Spring Boot 启动完成）：

```java
var result = com.syy.taskflowinsight.api.TfiListDiff.diff(oldList, newList);
String report = com.syy.taskflowinsight.api.TfiListDiff.render(result, "detailed");
```

示例实体（触发自动路由到 ENTITY 策略）：

```java
import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;

@Entity
static class User {
    @Key
    Long id;
    String name;
    User(Long id, String name) { this.id = id; this.name = name; }
}
```

### 样式选择

- 简洁：`"simple"` 或 `RenderStyle.simple()`
- 标准：`"standard"`（默认）或 `RenderStyle.standard()`
- 详细：`"detailed"` 或 `RenderStyle.detailed()`

```java
import com.syy.taskflowinsight.tracking.render.RenderStyle;

String md1 = listDiff.render(result, "simple");
String md2 = listDiff.render(result, RenderStyle.detailed());
```

### 浅引用复合键配置（@ShallowReference）

为提升引用对象（含复合主键）的可识别性，可配置浅引用字段保留复合键摘要：

```properties
# application.properties / application.yml 等价
tfi.change-tracking.snapshot.shallow-reference-mode=COMPOSITE_STRING
# 可选值：VALUE_ONLY（默认，向后兼容）/ COMPOSITE_STRING / COMPOSITE_MAP
```

示例：当 `@ShallowReference` 指向的实体具有多个 `@Key` 字段时，
- COMPOSITE_STRING 输出类似：`[id=1001,region=US]`
- COMPOSITE_MAP 输出为结构化 Map（更利于二次处理）

### 列表自动路由配置（可关闭）

默认在未指定策略时，实体列表（含 @Entity 或 @Key）会自动路由到 `ENTITY` 策略。可以通过配置关闭：

```properties
tfi.compare.auto-route.entity.enabled=false
```

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
