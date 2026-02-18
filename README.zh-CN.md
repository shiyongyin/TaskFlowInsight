# TaskFlowInsight

<div align="center">

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Test Coverage](https://img.shields.io/badge/Coverage-85%25-brightgreen.svg)](.)
[![tfi-all CI](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml/badge.svg)](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml)
[![tfi-compare CI](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-compare-ci.yml/badge.svg)](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-compare-ci.yml)
[![tfi-flow-core CI](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-flow-core-ci.yml/badge.svg)](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-flow-core-ci.yml)

**业务优先的 Java 可观测性**
一个轻量级库中实现流程可视化 + 变更追踪

**[English](README.md)** | [快速开始](#-快速开始) | [模块结构](#-模块结构) | [核心功能](#-核心功能) | [文档](#-文档)

</div>

---

## 什么是 TaskFlowInsight？

TaskFlowInsight (TFI) 是一个轻量级 Java 库，为你的业务逻辑带来 **X光透视能力**。它能自动可视化执行流程并智能追踪对象变更 — **无需任何配置**。

可以把它理解为**面向业务开发者的 APM**：传统 APM 工具监控基础设施（CPU、内存、网络），而 TFI 聚焦于开发者最关心的 — **理解业务逻辑的执行过程**。

```java
@TfiTask("处理订单")
public void processOrder(Order order) {
    validateOrder(order);        // ← 自动追踪
    TFI.track("order", order);   // ← 自动检测变更
    processPayment(order);
}
```

**输出：**
```
[订单-12345] 处理订单 ━━━━━━━━━━━━━━━━ 234ms ✓
├─ 验证订单 ........................ 45ms ✓
│  └─ order.status: 待处理 → 已验证
└─ 处理支付 ...................... 189ms ✓
   └─ order.payment: null → 已支付
```

---

## 📦 模块结构

TFI 采用 Maven 多模块架构，按职责拆分为 6 个模块：

```
TaskFlowInsight (parent)
├── tfi-flow-core           核心流程引擎（Session/Task/Stage/Message）
├── tfi-flow-spring-starter Spring Boot 自动配置 + AOP 注解支持
├── tfi-compare             智能比较引擎（深度对象比较 + 变更追踪）
├── tfi-ops-spring          运维监控（Actuator/Metrics/Store/Performance）
├── tfi-examples            示例与演示（Demo/Benchmark）
└── tfi-all                 全功能聚合模块（一站式引入）
```

**模块依赖关系：**
```
tfi-flow-core  ←─  tfi-flow-spring-starter  ←─┐
      ↑                                        │
tfi-compare  ←──  tfi-ops-spring  ←────────────┤
                                               │
                  tfi-all (聚合全部模块)  ──────┘
                  tfi-examples (依赖全部模块)
```

---

## 为什么选择 TFI？

### 问题所在
现代业务应用具有**复杂的工作流**，难以调试：
- ❓ 哪些步骤执行了？每步耗时多久？
- ❓ 对象在处理过程中发生了什么变化？
- ❓ 工作流为什么失败了？

**传统解决方案的不足：**
- **手动日志**：繁琐、分散、非结构化
- **APM 工具**：昂贵、聚焦基础设施、配置复杂
- **JaVers**：仅支持审计、无流程可视化、需要配置

### 解决方案
TFI 在一个轻量级包中提供**双核心能力**：

| 能力 | 你能得到什么 |
|------|-------------|
| **🎯 流程可视化** | 自动生成带精确计时的层次化流程树 |
| **🔍 变更追踪** | 智能深度对象比较与差异检测 |
| **📊 实时监控** | Spring Boot Actuator 集成 + Prometheus 指标 |
| **🚀 零配置** | 添加 `@TfiTask` 即可使用 |
| **⚡ 生产就绪** | <5MB 内存，<1% CPU，66K+ TPS |

---

## TFI 有何不同？

| 特性 | TaskFlowInsight | JaVers | APM 工具 | 手动日志 |
|------|----------------|--------|----------|----------|
| **配置时间** | < 2 分钟 | ~1 小时 | 数小时/天 | N/A |
| **流程可视化** | ✅ 树形可视化 | ❌ | ⚠️ 仅追踪 | ❌ 分散 |
| **变更追踪** | ✅ 深度比较 | ✅ 基础审计 | ❌ | ❌ |
| **内存占用** | **<5 MB** | ~20 MB | 50-100 MB | ~0 |
| **性能影响** | **<1% CPU** | ~3% | 5-15% | ~0 |
| **吞吐量** | **66,000+ TPS** | ~20,000 | N/A | N/A |
| **配置复杂度** | **零配置** | 中等 | 复杂 | 无需配置 |
| **Spring 集成** | ✅ 深度集成 | ⚠️ 基础集成 | ✅ | N/A |
| **业务上下文** | ✅ 内置支持 | ⚠️ 有限 | ❌ 需要自定义 | ❌ |
| **成本** | **免费开源** | 免费开源 | $$$$ | 免费 |

**TFI 的独特定位**：业界**唯一**结合流程可视化 + 变更追踪的企业级性能库。

---

## ⚡ 快速开始

### 前置要求
- Java 21+
- Maven 3.9+（或使用项目内置 wrapper）
- Spring Boot 3.x（可选但推荐）

### 1. 添加依赖

**全功能引入（推荐）：**
```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-all</artifactId>
    <version>3.0.0</version>
</dependency>
```

**按需引入：**
```xml
<!-- 仅流程追踪 -->
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-flow-spring-starter</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- 仅比较引擎 -->
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-compare</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- 运维监控（Actuator + Metrics） -->
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-ops-spring</artifactId>
    <version>3.0.0</version>
</dependency>
```

### 2. 启用 TFI（Spring Boot）

```java
@SpringBootApplication
@EnableTfi
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### 3. 开始追踪

**方式一：注解驱动（推荐）**
```java
@Service
public class OrderService {

    @TfiTask("处理订单")
    public OrderResult processOrder(String orderId) {
        Order order = fetchOrder(orderId);

        // 自动追踪变更
        TFI.track("order", order);

        validateOrder(order);
        processPayment(order);

        return OrderResult.success(order);
    }

    @TfiTask("验证订单")
    private void validateOrder(Order order) {
        // 验证逻辑 - 自动追踪
    }
}
```

**方式二：编程式 API**
```java
public void processOrder() {
    TFI.start("处理订单");
    try {
        try (var stage = TFI.stage("验证参数")) {
            // 业务逻辑
        }

        try (var stage = TFI.stage("检查库存")) {
            // 业务逻辑
        }

        TFI.exportToConsole();
    } finally {
        TFI.stop();
    }
}
```

### 4. 从源码构建

```bash
git clone https://github.com/shiyongyin/TaskFlowInsight.git
cd TaskFlowInsight

# 编译全部模块
./mvnw clean install

# 运行指定模块测试
./mvnw test -pl tfi-all
./mvnw test -pl tfi-compare

# 测试 + 覆盖率报告
./mvnw clean verify jacoco:report -pl tfi-all
# 报告路径：tfi-all/target/site/jacoco/index.html

# 运行演示
./mvnw spring-boot:run -pl tfi-examples
```

---

## 🎯 核心功能

### 1. 流程可视化
自动层次化执行追踪：
- **嵌套任务树**：会话 → 任务 → 阶段 → 消息
- **精确计时**：微秒级测量
- **异常捕获**：完整上下文和堆栈跟踪
- **异步支持**：ThreadLocal 上下文传播

```java
@TfiTask("创建订单")
public OrderResult createOrder(CreateOrderRequest request) {
    validateInventory(request.getProducts());  // 子任务 1
    calculatePrice(request);                   // 子任务 2
    processPayment(request.getPayment());      // 子任务 3
    initiateShipment(request);                 // 子任务 4

    return OrderResult.success();
}
```

### 2. 智能变更追踪
深度对象比较与智能差异检测：
- **快照策略**：浅层（标量）+ 深层（嵌套对象）
- **类型感知**：基本类型、集合、日期、BigDecimal、自定义对象
- **实体 vs 值对象**：基于类型系统的智能列表比较
- **路径去重**：消除冗余变更路径
- **可配置精度**：控制数值/日期比较精度

```java
// 追踪对象变更
TFI.track("order", orderObject);  // 浅层追踪
TFI.trackDeep("user", userObject); // 深层追踪

// 获取所有变更
List<ChangeRecord> changes = TFI.getChanges();
// 输出：order.status: 待处理 → 已支付
//       order.amount: 1000.00 → 850.00
```

### 3. 高级比较 API
灵活的比较与内置模板：

```java
// 简单一行式
CompareResult result = TFI.compare(before, after);

// 基于模板的比较
CompareResult auditResult = TFI.comparator()
    .useTemplate(ComparisonTemplate.AUDIT)  // AUDIT/DEBUG/FAST/PERFORMANCE
    .withMaxDepth(5)
    .compare(oldObj, newObj);

// 渲染为 Markdown
String report = TFI.render(result, "standard"); // simple/standard/detailed
```

**可用模板：**
- `AUDIT`：完整变更记录，用于合规审计
- `DEBUG`：详细诊断信息，用于故障排除
- `FAST`：性能优化的浅层比较
- `PERFORMANCE`：平衡深度与速度

### 4. 类型系统注解
细粒度控制比较行为：

```java
@Entity  // 具有唯一标识的对象
public class Order {
    @Key  // 用于列表匹配
    private String orderId;

    @NumericPrecision(scale = 2)  // 控制小数比较精度
    private BigDecimal amount;

    @DateFormat("yyyy-MM-dd HH:mm:ss")  // 日期格式化
    private Date createdAt;

    @DiffIgnore  // 排除比较
    private String internalNotes;
}

@ValueObject  // 基于值的比较（无标识）
public class Money {
    private BigDecimal amount;
    private String currency;
}
```

### 5. 企业级监控
生产就绪的可观测性：
- **Spring Boot Actuator**：`/actuator/taskflow` 端点
- **Prometheus 指标**：自定义 TFI 指标导出
- **健康指标**：系统健康检查
- **性能降级**：自动检测并适配（可选）
- **数据脱敏**：自动 PII 保护

```bash
# 检查健康状态
curl http://localhost:19090/actuator/health

# 查看 TFI 指标
curl http://localhost:19090/actuator/taskflow

# Prometheus 采集
curl http://localhost:19090/actuator/prometheus | grep tfi
```

### 6. 线程安全 & 零泄漏
为并发生产环境而构建：
- **ThreadLocal 隔离**：每个线程独立上下文
- **AutoCloseable 模式**：`try-with-resources` 自动清理
- **弱引用**：防止内存保留
- **泄漏检测**：`ZeroLeakThreadLocalManager` 监控
- **异步传播**：`TFIAwareExecutor` 用于线程池

---

## 🔬 业界最智能的比较引擎

TFI 的**变更追踪能力**由深度比较引擎驱动。这不仅仅是简单的对象比较 — 它是业界**唯一**结合类型系统、路径去重和算法优化的智能差异检测系统。

### 三大用户痛点 → TFI 解决方案

#### 痛点 1：手动对比太繁琐 😫

<details>
<summary>展开查看传统方式 vs TFI 方式</summary>

**传统方式（痛苦）：**
```java
// 需要为 50+ 字段手写这样的代码...
if (!Objects.equals(old.getStatus(), new.getStatus())) {
    log.info("status changed: {} -> {}", old.getStatus(), new.getStatus());
}
if (!Objects.equals(old.getAmount(), new.getAmount())) {
    log.info("amount changed: {} -> {}", old.getAmount(), new.getAmount());
}
// ... 继续重复 47 次 ...
```

**TFI 方式（优雅）：**
```java
TFI.track("order", order);
// ✅ 自动检测所有变更，一行代码搞定！

// 输出示例：
// order.status: 待处理 → 已验证
// order.amount: 1000.00 → 850.00
// order.customerName: 张三 → 李四
```
</details>

#### 痛点 2：集合比较困难 🤯

<details>
<summary>展开查看集合匹配的复杂度</summary>

**传统方式（复杂）：**
```java
List<Item> oldItems = oldOrder.getItems();
List<Item> newItems = newOrder.getItems();

// ❓ 如何判断哪个 Item 被添加/删除/修改？
// ❓ 如何匹配两个列表中的对应元素？
// ❓ 如何检测元素位置移动？

// 需要自己实现复杂的匹配逻辑...
// ❌ 位置移动检测？太复杂，放弃了...
```

**TFI 方式（智能）：**
```java
@Entity  // 标记为实体
public class Item {
    @Key  // 用此字段匹配列表元素
    private String itemId;
    private int quantity;
    private BigDecimal price;
}

// TFI 自动处理：
// - ✅ 元素匹配（基于 @Key）
// - ✅ 添加/删除检测
// - ✅ 字段变更检测
// - ✅ 位置移动检测（LCS 算法）
```
</details>

#### 痛点 3：浮点数/日期比较精度问题 🐛

<details>
<summary>展开查看精度控制</summary>

**TFI 方式（正确且可控）：**
```java
@Entity
public class Transaction {
    @NumericPrecision(scale = 2)  // 控制到 2 位小数
    private BigDecimal amount;

    @NumericPrecision(scale = 4)  // 不同字段不同精度
    private BigDecimal exchangeRate;

    @DateFormat("yyyy-MM-dd")  // 只比较日期部分，忽略时间
    private Date transactionDate;

    @DateFormat("yyyy-MM-dd HH:mm:ss")  // 精确到秒
    private Date createdAt;
}
```
</details>

---

### 技术深度展示

#### 1. LCS 算法检测列表移动 🧠

TFI 使用**最长公共子序列（LCS）算法**智能检测列表元素的移动，而不仅仅是简单的添加/删除。

```java
// 示例场景
List<Task> oldTasks = [A, B, C, D, E];
List<Task> newTasks = [A, C, B, E, D];

// TFI LCS 算法输出（正确）：
// ✅ tasks[1] MOVED from index 1 to index 2  (B: 位置1 → 位置2)
// ✅ tasks[2] MOVED from index 2 to index 1  (C: 位置2 → 位置1)
```

#### 2. 路径去重系统 🎯

TFI 的 **PathDeduplicator** 自动消除冗余变更路径，只保留最精确的叶子节点变更。

```java
// 原始变更（冗余）：
order.items[0].product.price: 100 → 120
order.items[0].product: Product{...} → Product{...}
order.items[0]: Item{...} → Item{...}
order: Order{...} → Order{...}

// PathDeduplicator 去重后（清晰）：
✅ order.items[0].product.price: 100 → 120
```

#### 3. 类型感知比较 🏷️

TFI 通过 `@Entity` 和 `@ValueObject` 注解区分两种语义：

```java
@Entity  // 有唯一标识的对象
public class User {
    @Key  // 用于列表匹配
    private String userId;
    private String name;
}
// ✅ 即使 name 变化，只要 userId 相同，就是"同一个用户被修改"

@ValueObject  // 无标识，纯值比较
public class Money {
    private BigDecimal amount;
    private String currency;
}
// ✅ 适合不可变对象、配置项等场景
```

---

### TFI vs JaVers 深度对比

| 维度 | **TaskFlowInsight** | JaVers |
|------|-------------------|--------|
| **核心定位** | 🐛 调试工具（实时） | 📋 审计系统（持久化） |
| **配置复杂度** | ⚡ 零配置（`@TfiTask`） | ⚙️ 中等（Repository + Entity 映射） |
| **性能（TPS）** | **66,000+** ⚡ | ~20,000 (3.3x 差距) |
| **内存占用** | **<5 MB** 🪶 | ~20 MB |
| **流程可视化** | ✅ 内置树形结构 | ❌ 无 |
| **类型系统** | `@Entity`/`@ValueObject`/`@Key` | `@Entity`（仅 JPA） |
| **路径去重** | ✅ PathDeduplicator | ❌ 原始路径 |
| **LCS 算法** | ✅ 移动检测 | ❌ 仅添加/删除 |
| **精度控制** | `@NumericPrecision`/`@DateFormat` | 有限 |
| **数据持久化** | ❌ 内存中（会话清理） | ✅ 数据库 |
| **目标用户** | 👨‍💻 开发者/测试工程师 | 🏢 合规/审计团队 |

---

### 真实调试场景：电商订单支付失败

**TFI 自动输出：**
```
[Order-12345] 处理订单 ━━━━━━━━━━━━━ 234ms ✗
├─ 获取订单 ...................... 12ms ✓
│  └─ order.status: null → PENDING
│  └─ order.payment: null
├─ 验证订单 ...................... 45ms ✓
│  └─ order.status: PENDING → VALIDATED
│  └─ order.payment: null (unchanged)  ← ⚠️ 发现问题
├─ 处理支付 ..................... 177ms ✗
│  └─ 🔴 NullPointerException: order.payment is null
└─ ❌ 失败原因：payment 对象未初始化

🎯 根因分析：payment 字段在 validateOrder 后仍为 null
💡 解决方案：在 validateOrder 和 processPayment 之间添加 initializePayment()
```

**价值对比：**
- ✅ **流程可视化**：清晰看到执行了哪些步骤，每步耗时
- ✅ **变更追踪**：自动检测 order.payment 始终为 null
- ⏰ **诊断时间**：从 30-60 分钟降低到 **30 秒**

---

## 💡 真实案例

### 电商订单处理
```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    @TfiTask("创建订单")
    @PostMapping
    public ResponseEntity<OrderResult> createOrder(@RequestBody CreateOrderRequest request) {
        User user = validateUser(request.getUserId());
        List<Product> products = validateProducts(request.getProductIds());

        InventoryResult inventory = checkInventory(products);
        TFI.track("inventory", inventory);

        PriceResult price = calculatePrice(products, user.getVipLevel());
        TFI.track("pricing", price);

        Order order = createOrder(user, products, price);
        PaymentResult payment = processPayment(order, request.getPaymentInfo());

        if (payment.isSuccess()) {
            updateInventory(inventory);
            return ResponseEntity.ok(OrderResult.success(order, payment));
        } else {
            TFI.error("支付失败", new PaymentException(payment.getErrorMessage()));
            return ResponseEntity.badRequest().body(OrderResult.failure("支付失败"));
        }
    }
}
```

### 审批工作流
```java
@Service
public class ApprovalService {

    @TfiTask("审批链")
    public ApprovalResult processApproval(LeaveRequest request) {
        TFI.trackDeep("request", request);

        for (Approver approver : getApprovalChain()) {
            ApprovalDecision decision = approver.review(request);
            TFI.track("decision", decision);

            if (decision.isRejected()) {
                return ApprovalResult.rejected(decision.getReason());
            }
        }

        return ApprovalResult.approved();
    }
}
```

### 数据同步 (ETL)
```java
@TfiTask("ETL 同步")
public SyncResult syncData(DataSource source, DataTarget target) {
    List<Record> records = source.fetchRecords();
    int successCount = 0;

    for (Record record : records) {
        try (var stage = TFI.stage("转换记录 " + record.getId())) {
            Record transformed = transformRecord(record);
            TFI.track("record-" + record.getId(), transformed);
            target.save(transformed);
            successCount++;
        } catch (Exception e) {
            TFI.error("转换失败：记录 " + record.getId(), e);
        }
    }

    return SyncResult.completed(successCount, records.size());
}
```

**📚 查看 [EXAMPLES.md](EXAMPLES.md) 了解 11 个完整真实场景**

---

## 🏗️ 架构

```
┌──────────────────────────────────────────────────────┐
│                    tfi-all (聚合)                      │
├──────────────────────────────────────────────────────┤
│  tfi-flow-spring-starter  │  tfi-ops-spring          │
│  • @TfiTask AOP 切面      │  • Actuator 端点         │
│  • Spring 自动配置        │  • Prometheus 指标        │
│  • SpEL 支持              │  • Caffeine Store        │
│                           │  • 性能监控/降级          │
├───────────────────────────┼──────────────────────────┤
│  tfi-flow-core            │  tfi-compare             │
│  • Session/Task/Stage     │  • CompareService        │
│  • SafeContextManager     │  • DiffDetector/Facade   │
│  • ZeroLeakThreadLocal    │  • SnapshotProvider      │
│  • TFI API 门面           │  • PathDeduplicator      │
│  • 导出器(Console/JSON)   │  • LCS/类型系统/缓存     │
└───────────────────────────┴──────────────────────────┘
```

### 设计理念
1. **零泄漏保证**：所有上下文使用 try-with-resources 或显式清理
2. **优雅降级**：禁用 TFI 后成为完全无操作（零开销）
3. **异常安全**：TFI 永不向用户代码传播异常
4. **性能优先**：快速路径检查、延迟初始化、激进缓存
5. **线程安全**：所有公共 API 并发安全

### 技术栈
- **Java 21**：现代语言特性（记录类型、模式匹配、虚拟线程就绪）
- **Spring Boot 3.5.5**：最新企业框架
- **Spring AOP**：注解处理（`@TfiTask`、`@TfiTrack`）
- **Caffeine 3.1.8**：高性能缓存
- **Micrometer + Prometheus**：供应商中立的指标门面

---

## 🚀 性能

| 指标 | 数值 | 说明 |
|------|------|------|
| **内存占用** | < 5 MB | 比竞品轻 10 倍 |
| **CPU 开销** | < 1% | 对吞吐量影响可忽略 |
| **延迟增加** | < 15 μs | 每次操作亚毫秒级 |
| **吞吐量** | **66,000+ TPS** | 基准测试验证 |
| **缓存命中率** | 95%+ | Caffeine 优化 |
| **测试覆盖** | 85%+ | 350+ 测试类 |

```bash
# 运行 JMH 基准测试
./mvnw -pl tfi-examples -P bench exec:java \
  -Dexec.mainClass=com.syy.taskflowinsight.benchmark.BenchmarkRunner
```

---

## 🔧 配置

TFI **开箱即用**，具有合理的默认值。通过 `application.yml` 自定义：

```yaml
tfi:
  enabled: true  # 主开关

  annotation:
    enabled: true  # @TfiTask/@TfiTrack 支持

  change-tracking:
    enabled: true
    snapshot:
      enable-deep: true  # 深度对象遍历
      max-depth: 10      # 防止无限递归

  compare:
    auto-route:
      entity:
        enabled: true  # 自动检测 @Entity 用于列表比较
      lcs:
        enabled: true  # LCS 算法用于移动检测
    numeric:
      float-tolerance: 1e-12
      relative-tolerance: 1e-9
    datetime:
      default-format: "yyyy-MM-dd HH:mm:ss"
      tolerance-ms: 0

  api:
    routing:
      enabled: false        # v4.0.0 Provider 路由（实验性）
      provider-mode: auto

  render:
    masking:
      enabled: true  # PII 保护
    mask-fields:
      - password
      - secret
      - token
      - internal*  # 支持通配符
```

应用默认端口：**19090**

---

## 🧪 CI/CD

每个模块都有独立的 GitHub Actions CI：

| Workflow | 覆盖模块 | 内容 |
|----------|----------|------|
| tfi-flow-core CI | tfi-flow-core | 测试 + JaCoCo + 静态分析 |
| tfi-flow-spring-starter CI | tfi-flow-spring-starter | 测试 + JaCoCo + 静态分析 |
| tfi-compare CI | tfi-compare | 测试 + JaCoCo + 静态分析 + OWASP 依赖扫描 |
| tfi-ops-spring CI | tfi-ops-spring | 测试 + JaCoCo + 静态分析 |
| tfi-examples CI | tfi-examples | 编译 + 测试 |
| tfi-all CI | tfi-all | 测试 + JaCoCo + 静态分析 + API 兼容性 |
| TFI Routing Perf Gate | tfi-examples + tfi-all | JMH 基准 + 性能门禁 |

---

## 📚 文档

### 用户指南
- [📖 快速开始指南](QUICKSTART.md) - 3 分钟快速上手
- [📘 入门教程](GETTING-STARTED.md) - 全面的教程
- [💡 11 个真实案例](EXAMPLES.md) - 电商、工作流、金融、游戏

### 参考文档
- [🔧 v3→v4 迁移指南](docs/MIGRATION_GUIDE_v3_to_v4.md)
- [🏛️ 架构概览](CLAUDE.md) - 系统设计与原则

### 支持
- [❓ FAQ](FAQ.md) - 常见问题解答
- [🩺 故障排除](TROUBLESHOOTING.md) - 诊断程序
- [🐛 GitHub Issues](https://github.com/shiyongyin/TaskFlowInsight/issues) - Bug 报告与功能请求

---

## 🗺️ 路线图

### ✅ v3.0.0（当前稳定版）
- **统一架构**：DiffFacade + SnapshotProvider（Spring/非Spring 自动切换）
- **完整类型系统**：`@Entity`、`@Key`、`@NumericPrecision`、`@DateFormat`、`@CustomComparator`
- **高级比较**：EntityListStrategy（移动检测）、LCS 算法、精度控制
- **路径系统**：PathDeduplicator 生成清晰的差异输出
- **监控**：DegradationManager（自适应负载）、Prometheus 指标
- **测试**：350+ 测试类，85%+ 覆盖率

### 🔨 v4.0.0（开发中）
- Provider 路由机制（`tfi.api.routing`）
- 多模块 Maven 架构拆分
- 独立模块 CI/CD
- 引用变更语义增强

---

## 🤝 贡献

```bash
git clone https://github.com/shiyongyin/TaskFlowInsight.git
cd TaskFlowInsight
./mvnw clean install           # 构建全部模块
./mvnw test -pl tfi-compare    # 运行指定模块测试
```

欢迎贡献！查看 [CONTRIBUTING.md](CONTRIBUTING.md) 了解指南。

---

## 📄 许可证

TaskFlowInsight 是根据 [Apache 2.0 许可证](LICENSE)发布的开源软件。

---

## 🙏 致谢

使用一流技术构建：
- [Spring Boot](https://spring.io/projects/spring-boot) - 企业应用框架
- [Caffeine](https://github.com/ben-manes/caffeine) - 高性能缓存库
- [Micrometer](https://micrometer.io/) - 供应商中立的指标门面
- 灵感来自 [JaVers](https://javers.org/) - 对象审计和差异框架

---

<div align="center">

**TaskFlowInsight** — 业务优先的 Java 可观测性

*如果 TFI 对你有帮助，请在 GitHub 上给我们一个 ⭐*

[文档](GETTING-STARTED.md) | [示例](EXAMPLES.md) | [GitHub](https://github.com/shiyongyin/TaskFlowInsight) | [Issues](https://github.com/shiyongyin/TaskFlowInsight/issues)

</div>
