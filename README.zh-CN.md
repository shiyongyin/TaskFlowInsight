# TaskFlowInsight

<div align="center">

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
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
        TFI.track("order", order);

        validateOrder(order);
        processPayment(order);

        return OrderResult.success(order);
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

## 为什么选择 TFI？

| 特性 | TaskFlowInsight | JaVers | APM 工具 | 手动日志 |
|------|----------------|--------|----------|----------|
| **配置时间** | < 2 分钟 | ~1 小时 | 数小时/天 | N/A |
| **流程可视化** | ✅ 树形可视化 | ❌ | ⚠️ 仅追踪 | ❌ 分散 |
| **变更追踪** | ✅ 深度比较 | ✅ 基础审计 | ❌ | ❌ |
| **内存占用** | **<5 MB** | ~20 MB | 50-100 MB | ~0 |
| **性能影响** | **<1% CPU** | ~3% | 5-15% | ~0 |
| **吞吐量** | **66,000+ TPS** | ~20,000 | N/A | N/A |
| **Spring 集成** | ✅ 深度集成 | ⚠️ 基础集成 | ✅ | N/A |
| **成本** | **免费开源** | 免费开源 | $$$$ | 免费 |

---

## 🎯 核心功能

### 1. 流程可视化
- **嵌套任务树**：会话 → 任务 → 阶段 → 消息
- **精确计时**：微秒级测量
- **异常捕获**：完整上下文和堆栈跟踪
- **异步支持**：`TFIAwareExecutor` 实现 ThreadLocal 上下文传播

### 2. 智能变更追踪
- **快照策略**：浅层（标量）+ 深层（嵌套对象）
- **实体 vs 值对象**：`@Entity`/`@ValueObject` 类型系统
- **路径去重**：PathDeduplicator 消除冗余变更路径
- **LCS 算法**：智能检测列表元素移动

```java
TFI.track("order", orderObject);       // 浅层追踪
TFI.trackDeep("user", userObject);     // 深层追踪
List<ChangeRecord> changes = TFI.getChanges();
```

### 3. 高级比较 API

```java
CompareResult result = TFI.compare(before, after);
String report = TFI.render(result, "standard");
```

### 4. 类型系统注解

```java
@Entity
public class Order {
    @Key                              // 列表匹配主键
    private String orderId;

    @NumericPrecision(scale = 2)      // 小数精度控制
    private BigDecimal amount;

    @DateFormat("yyyy-MM-dd HH:mm:ss") // 日期格式化
    private Date createdAt;

    @DiffIgnore                        // 排除比较
    private String internalNotes;
}
```

### 5. 企业级监控

```bash
curl http://localhost:19090/actuator/taskflow          # TFI 状态
curl http://localhost:19090/actuator/taskflow-context   # 上下文信息
curl http://localhost:19090/actuator/prometheus | grep tfi  # Prometheus 指标
```

### 6. 线程安全 & 零泄漏
- **ThreadLocal 隔离**：每个线程独立上下文
- **AutoCloseable 模式**：`try-with-resources` 自动清理
- **泄漏检测**：`ZeroLeakThreadLocalManager` 监控
- **异步传播**：`TFIAwareExecutor` 用于线程池

---

## 🔬 比较引擎

TFI 的比较引擎是业界**唯一**结合类型系统、路径去重和 LCS 算法的智能差异检测系统。

<details>
<summary>痛点 1：手动对比太繁琐</summary>

```java
// 传统方式：50+ 字段逐个比较...
if (!Objects.equals(old.getStatus(), new.getStatus())) {
    log.info("status changed: {} -> {}", old.getStatus(), new.getStatus());
}
// ... 重复 50 次 ...

// TFI 方式：一行搞定
TFI.track("order", order);
// 自动输出：order.status: 待处理 → 已验证
```
</details>

<details>
<summary>痛点 2：集合比较困难</summary>

```java
@Entity
public class Item {
    @Key
    private String itemId;
    private int quantity;
}

// TFI 自动处理：
// ✅ 基于 @Key 匹配元素
// ✅ 添加/删除/修改检测
// ✅ LCS 算法检测位置移动
```
</details>

<details>
<summary>痛点 3：浮点数/日期精度问题</summary>

```java
@NumericPrecision(scale = 2)   // 控制到 2 位小数
private BigDecimal amount;

@DateFormat("yyyy-MM-dd")      // 只比较日期部分
private Date transactionDate;
```
</details>

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

**技术栈：** Java 21, Spring Boot 3.5.5, Spring AOP, Caffeine 3.1.8, Micrometer + Prometheus

---

## 🚀 性能

| 指标 | 数值 |
|------|------|
| **内存占用** | < 5 MB |
| **CPU 开销** | < 1% |
| **延迟增加** | < 15 μs/操作 |
| **吞吐量** | 66,000+ TPS |
| **缓存命中率** | 95%+ |

```bash
# 运行 JMH 基准测试
./mvnw -pl tfi-examples -P bench exec:java \
  -Dexec.mainClass=com.syy.taskflowinsight.benchmark.BenchmarkRunner
```

---

## 🔧 配置

TFI 开箱即用，通过 `application.yml` 自定义：

```yaml
tfi:
  enabled: true
  annotation:
    enabled: true
  change-tracking:
    enabled: true
    snapshot:
      enable-deep: true
      max-depth: 10
  compare:
    auto-route:
      entity.enabled: true
      lcs.enabled: true
  api:
    routing:
      enabled: false        # v4.0.0 Provider 路由（实验性）
      provider-mode: auto
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

- [快速开始指南](QUICKSTART.md)
- [入门教程](GETTING-STARTED.md)
- [真实案例](EXAMPLES.md) — 电商、工作流、金融、游戏等 11 个场景
- [FAQ](FAQ.md)
- [故障排除](TROUBLESHOOTING.md)
- [v3→v4 迁移指南](docs/MIGRATION_GUIDE_v3_to_v4.md)
- [贡献指南](CONTRIBUTING.md)

---

## 🗺️ 路线图

### ✅ v3.0.0（当前稳定版）
- 统一架构：DiffFacade + SnapshotProvider
- 完整类型系统：`@Entity`/`@Key`/`@NumericPrecision`/`@DateFormat`
- LCS 算法移动检测 + PathDeduplicator 路径去重
- Spring Boot Actuator + Prometheus 监控
- 350+ 测试类，85%+ 覆盖率

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

[Apache 2.0](LICENSE)

---

<div align="center">

**TaskFlowInsight** — 业务优先的 Java 可观测性

*如果 TFI 对你有帮助，请给我们一个 ⭐*

[GitHub](https://github.com/shiyongyin/TaskFlowInsight) | [Issues](https://github.com/shiyongyin/TaskFlowInsight/issues) | [Discussions](https://github.com/shiyongyin/TaskFlowInsight/discussions)

</div>
