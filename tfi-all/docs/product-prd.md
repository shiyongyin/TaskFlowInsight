# TaskFlowInsight (TFI) 产品需求文档 (PRD)

> **版本**: v3.0.0 / v4.0.0-routing-refactor  
> **编写角色**: 资深产品经理  
> **更新日期**: 2026-02-16  
> **文档状态**: 正式版

---

## 目录

- [1. 产品概述](#1-产品概述)
- [2. 目标用户](#2-目标用户)
- [3. 核心价值主张](#3-核心价值主张)
- [4. 功能清单](#4-功能清单)
- [5. 功能详细说明](#5-功能详细说明)
- [6. 用户场景与用例](#6-用户场景与用例)
- [7. 非功能性需求](#7-非功能性需求)
- [8. 竞品分析](#8-竞品分析)
- [9. 版本路线图](#9-版本路线图)
- [10. 验收标准](#10-验收标准)
- [11. 风险与约束](#11-风险与约束)

---

## 1. 产品概述

### 1.1 产品定位

TaskFlowInsight (TFI) 是一款面向 Java 开发者的**业务流程可视化与变更追踪开发者工具库**。它以"让业务流程自己说话"为核心理念，通过非侵入式的方式自动生成流程执行树和对象状态变化报告，为开发者提供"X 光式"的代码执行洞察能力。

### 1.2 产品形态

- **类型**: Java 库 (Library / SDK)
- **分发方式**: Maven 依赖包
- **集成方式**: Spring Boot Starter 自动配置 / 纯 Java 手动集成
- **运行环境**: JDK 21+, Spring Boot 3.x

### 1.3 一句话描述

> TFI 是一个非侵入、零泄漏的 Java 业务流程可视化工具，让开发者用一行代码追踪业务执行路径和对象变更。

---

## 2. 目标用户

### 2.1 用户画像

| 角色 | 场景 | 痛点 | TFI 价值 |
|------|------|------|----------|
| **Java 后端开发** | 复杂业务流程调试 | 看不到执行路径，只能加日志 | 一行代码可视化执行树 |
| **业务架构师** | 流程文档编写 | 代码实现与文档不同步 | 自动生成流程文档 |
| **质量工程师** | 回归测试验证 | 难以精确验证对象变更 | 自动变更检测与报告 |
| **技术团队 Leader** | Code Review | 难以理解业务流程 | 流程树 + 变更记录一目了然 |
| **DevOps 工程师** | 生产问题排查 | 缺少业务级监控 | Actuator 端点实时观测 |

### 2.2 用户规模预期

| 阶段 | 时间 | 目标 |
|------|------|------|
| 种子期 | v3.0 | 内部团队使用，5-10 个项目集成 |
| 成长期 | v4.0 | 开源社区推广，50+ 项目集成 |
| 成熟期 | v5.0 | 企业级客户，200+ 项目集成 |

---

## 3. 核心价值主张

### 3.1 价值三角

```
        ┌─────────────────────┐
        │    业务可视化         │
        │  "看见"执行流程       │
        └──────────┬──────────┘
                   │
       ┌───────────┴───────────┐
       │                       │
┌──────┴──────┐         ┌──────┴──────┐
│  变更追踪    │         │  零侵入      │
│ "捕捉"状态变化│         │ "无感"接入    │
└─────────────┘         └─────────────┘
```

### 3.2 核心差异化优势

| 特性 | 描述 | 竞品对比 |
|------|------|----------|
| **零侵入** | 不修改业务类，注解可选 | 多数追踪工具需侵入 |
| **零泄漏** | 自动检测和清理上下文泄漏 | 独有特性 |
| **异常安全** | TFI 永不抛出异常到业务代码 | 独有设计原则 |
| **多粒度追踪** | 浅层字段 + 深度对象图 | 多数只支持单一粒度 |
| **智能比较** | @Entity/@ValueObject 类型系统驱动 | 基于注解的智能策略选择 |
| **Spring 生态原生** | Actuator + Micrometer + AOP | 深度 Spring 集成 |

---

## 4. 功能清单

### 4.1 功能模块总览

| 模块 | 功能 | 优先级 | 版本 | 状态 |
|------|------|--------|------|------|
| **F1** | 流程追踪 (Task Flow) | P0 | v1.0 | ✅ 已完成 |
| **F2** | 变更追踪 (Change Tracking) | P0 | v2.0 | ✅ 已完成 |
| **F3** | 对象比较 (Object Compare) | P0 | v2.0 | ✅ 已完成 |
| **F4** | 导出系统 (Export) | P1 | v2.0 | ✅ 已完成 |
| **F5** | Spring 集成 | P0 | v2.0 | ✅ 已完成 |
| **F6** | 注解驱动 (@TfiTask/@TfiTrack) | P1 | v2.5 | ✅ 已完成 |
| **F7** | Actuator 运维端点 | P1 | v3.0 | ✅ 已完成 |
| **F8** | 度量指标 (Metrics) | P2 | v3.0 | ✅ 已完成 |
| **F9** | 健康检查 (Health) | P2 | v3.0 | ✅ 已完成 |
| **F10** | Provider 路由 (SPI) | P1 | v4.0 | 🔄 开发中 |
| **F11** | 性能仪表板 | P2 | v3.0 | ✅ 已完成 |
| **F12** | 渲染系统 (Markdown/Style) | P2 | v3.0 | ✅ 已完成 |

---

## 5. 功能详细说明

### 5.1 F1 - 流程追踪

#### 5.1.1 功能描述

自动记录业务执行流程，构建层级化的执行树（Session → Task → Stage → Message），支持嵌套和异步传播。

#### 5.1.2 核心能力

| 子功能 | 描述 | API |
|--------|------|-----|
| 会话管理 | 创建/结束追踪会话 | `TFI.startSession()`, `TFI.endSession()` |
| 阶段创建 | AutoCloseable 阶段 | `TFI.stage("name")` |
| 函数式阶段 | Lambda + 返回值 | `TFI.stage("name", () -> result)` |
| 消息记录 | 记录关键节点信息 | `TFI.message()`, `TFI.error()` |
| 异步传播 | 跨线程上下文传递 | `TFI.wrapExecutor()` |
| 全局开关 | 运行时启停 | `TFI.enable()`, `TFI.disable()` |

#### 5.1.3 用户故事

> **作为** Java 后端开发  
> **我希望** 通过简单的代码包裹记录业务流程  
> **以便** 在调试时清晰看到执行路径和耗时

#### 5.1.4 验收标准

- [x] `try (var stage = TFI.stage("xxx"))` 语法可正常使用
- [x] 嵌套 stage 正确构建父子关系
- [x] 异步线程中可以访问父线程上下文
- [x] `TFI.disable()` 后所有操作为 no-op

### 5.2 F2 - 变更追踪

#### 5.2.1 功能描述

追踪 Java 对象的状态变化，在记录基线快照后，自动检测字段值的增删改。

#### 5.2.2 核心能力

| 子功能 | 描述 | API |
|--------|------|-----|
| 浅层追踪 | 指定字段追踪 | `TFI.track("name", obj, "field1", "field2")` |
| 全量追踪 | 追踪所有字段 | `TFI.trackAll("name", obj)` |
| 深度追踪 | 递归对象图追踪 | `TFI.trackDeep("name", obj)` |
| 获取变更 | 获取变更记录列表 | `TFI.getChanges()` |
| 清理追踪 | 释放资源 | `TFI.clearAllTracking()` |
| 条件追踪 | 带选项的追踪 | `TFI.track("name", obj, TrackingOptions)` |

#### 5.2.3 用户故事

> **作为** 质量工程师  
> **我希望** 自动检测对象在业务流程中的状态变化  
> **以便** 精确验证业务逻辑是否正确修改了目标字段

#### 5.2.4 验收标准

- [x] 浅层追踪检测到指定字段的值变更
- [x] 深度追踪递归检测嵌套对象变更
- [x] 支持 `@DiffIgnore` 排除字段
- [x] 最大追踪对象数可配置（默认 1000）
- [x] ThreadLocal 隔离确保多线程安全

### 5.3 F3 - 对象比较

#### 5.3.1 功能描述

对两个 Java 对象进行深度对比，输出结构化的差异报告，支持自定义比较策略和类型系统注解。

#### 5.3.2 核心能力

| 子功能 | 描述 | API |
|--------|------|-----|
| 简单比较 | 两个对象比较 | `TFI.compare(obj1, obj2)` |
| 构建器 | 流式配置比较 | `TFI.comparator().source(a).target(b).build()` |
| 渲染报告 | Markdown 报告 | `TFI.render(result, "standard")` |
| 类型系统 | 注解驱动策略选择 | `@Entity`, `@ValueObject`, `@Key` |
| 精度控制 | 数值/日期精度 | `@NumericPrecision(scale=2)` |

#### 5.3.3 用户故事

> **作为** 业务架构师  
> **我希望** 能够比较两个版本的业务对象并生成结构化报告  
> **以便** 快速了解数据变化并纳入审计记录

### 5.4 F4 - 导出系统

#### 5.4.1 核心能力

| 导出格式 | 描述 | API |
|----------|------|-----|
| Console | 格式化控制台输出 | `TFI.exportToConsole()` |
| JSON | JSON 字符串导出 | `TFI.exportToJson()` |
| Map | 结构化 Map 导出 | `TFI.exportToMap()` |
| Markdown | 渲染报告 | `TFI.render(result, style)` |
| CSV | 变更记录 CSV | `ChangeExporter` |
| XML | 变更记录 XML | `ChangeXmlExporter` |
| Streaming | 流式导出 | `StreamingChangeExporter` |

### 5.5 F5 - Spring 集成

#### 5.5.1 自动配置

```java
@SpringBootApplication
public class MyApplication { }
```

引入 `tfi-flow-spring-starter` 和 `tfi-compare-spring-starter` 后由 Boot 自动配置发现能力；
Compare 使用 `tfi.compare.*`，注解 advice 使用 `tfi.annotation.enabled` 显式启用。

#### 5.5.2 注解驱动 (F6)

```java
@TfiTask(name = "处理订单", condition = "#order.amount > 100")
public Order processOrder(Order order) {
    // 业务逻辑
}

@TfiTrack(fields = {"status", "amount"})
public void updateOrder(Order order) {
    // 自动追踪 status 和 amount 变化
}
```

### 5.6 F7/F8/F9 - 运维功能

| 端点 | 路径 | 功能 |
|------|------|------|
| TFI 概览 | `/actuator/taskflow` | 当前会话和任务概览 |
| 上下文诊断 | `/actuator/taskflow-context` | 上下文泄漏检测 |
| TFI 指标 | `/actuator/tfi-metrics` | 性能指标读取/重置 |
| 健康检查 | `/actuator/health` | 内存/CPU/缓存/错误率评分 |
| 性能仪表板 | `/actuator/tfi-advanced/*` | 完整性能 REST API |

### 5.7 F10 - Provider 路由 (v4.0.0)

#### 5.7.1 功能描述

允许用户通过 SPI 机制替换 TFI 内部的任何子系统实现，包括流程管理、变更追踪、对象比较、渲染和导出。

#### 5.7.2 Provider 类型

| Provider | 职责 | 默认实现 |
|----------|------|----------|
| FlowProvider | 会话/任务/消息流程 | DefaultFlowProvider |
| TrackingProvider | 变更追踪 | DefaultTrackingProvider |
| ComparisonProvider | 对象比较 | DefaultComparisonProvider |
| RenderProvider | Markdown 渲染 | DefaultRenderProvider |
| ExportProvider | 导出 | DefaultExportProvider |

#### 5.7.3 注册方式

```java
// 方式1: 编程注册
TFI.registerComparisonProvider(new MyComparisonProvider());

// 方式2: Spring Bean 自动发现
@Component
public class MyProvider implements ComparisonProvider { ... }

// 方式3: ServiceLoader SPI
// META-INF/services/com.syy.taskflowinsight.spi.ComparisonProvider
```

---

## 6. 用户场景与用例

### 6.1 场景一：电商订单流程追踪

```
用户下单 → 创建订单 → 扣减库存 → 计算优惠 → 发起支付 → 更新状态
```

**TFI 输出示例**:

```
📋 Session: 用户下单 #ORD-20240101
 ├── 📌 创建订单 [120ms]
 │    └── ✉️ 订单ID: ORD-20240101
 ├── 📌 扣减库存 [45ms]
 │    └── ✉️ SKU-001: 100 → 99
 ├── 📌 计算优惠 [30ms]
 │    └── ✉️ 优惠金额: ¥50.00
 ├── 📌 发起支付 [200ms]
 │    └── ✉️ 支付流水: PAY-20240101
 └── 📌 更新状态 [15ms]
      └── ✉️ PENDING → PAID

📊 变更追踪:
 • order.status: PENDING → PAID
 • order.amount: 500.00 → 450.00
 • inventory.sku001.quantity: 100 → 99
```

### 6.2 场景二：审计日志生成

```java
Order before = orderService.getOrder(orderId);
TFI.track("order", before, "status", "amount", "items");

orderService.modifyOrder(orderId, request);

List<ChangeRecord> changes = TFI.getChanges();
auditService.log(changes); // 自动生成审计记录
```

### 6.3 场景三：回归测试验证

```java
@Test
void shouldUpdateOrderCorrectly() {
    Order order = createTestOrder();
    TFI.trackAll("order", order);
    
    orderService.process(order);
    
    List<ChangeRecord> changes = TFI.getChanges();
    assertThat(changes)
        .extracting(ChangeRecord::fieldName)
        .contains("status", "processedAt")
        .doesNotContain("id", "createdAt");
}
```

### 6.4 场景四：生产问题排查

```
GET /actuator/taskflow
→ 返回当前活跃会话、任务堆栈、泄漏检测报告

GET /actuator/taskflow-context
→ 返回上下文诊断信息、活跃线程数、泄漏风险

GET /actuator/health
→ 返回 TFI 组件健康状态 (内存/CPU/缓存/错误率)
```

### 6.5 场景五：配置对比 (v4.0.0)

```java
// 对比两个配置版本
CompareResult result = TFI.compare(configV1, configV2);
String report = TFI.render(result, "detailed");
System.out.println(report);

// 输出 Markdown 格式差异报告
```

---

## 7. 非功能性需求

### 7.1 性能需求

| 指标 | 目标 | 当前状态 |
|------|------|----------|
| Stage 创建 + 关闭 | < 50μs (P95) | ✅ 达标 |
| 浅层快照 (2 字段) | < 50μs (P50) | ✅ 达标 |
| Diff 检测 (2 字段) | < 200μs (P95) | ✅ 达标 |
| 深度快照 (10 层) | < 5ms (P95) | ✅ 达标 |
| TFI 禁用时开销 | < 10ns | ✅ 达标 (完全 no-op) |
| Provider 路由开销 | < 5% 回归 | 🔄 CI 监控中 |

### 7.2 可靠性需求

| 需求 | 描述 | 实现状态 |
|------|------|----------|
| 异常安全 | TFI 不向业务代码抛出异常 | ✅ 已实现 |
| 零泄漏 | 上下文自动检测和清理泄漏 | ✅ 已实现 |
| 线程安全 | 所有公共 API 线程安全 | ✅ 已实现 |
| 优雅降级 | TFI 禁用 = 完全 no-op | ✅ 已实现 |
| 资源限制 | 最大追踪对象数可配置 | ✅ 已实现 |
| 降级策略 | 超过阈值自动降级 | ✅ 已实现 |

### 7.3 兼容性需求

| 维度 | 要求 |
|------|------|
| JDK | 21+ |
| Spring Boot | 3.x |
| API 兼容 | japicmp 保证向后兼容 |
| 非 Spring | 纯 Java 环境可用 (静态回退) |

### 7.4 安全需求

| 需求 | 描述 |
|------|------|
| 数据脱敏 | `UnifiedDataMasker` 支持敏感字段脱敏 |
| 端点安全 | Actuator 端点可配置只读 |
| 无外部通信 | TFI 不发起任何外部网络请求 |
| 内存安全 | 快照大小限制、超时清理 |

---

## 8. 竞品分析

### 8.1 竞品矩阵

| 特性 | TFI | Javers | Hibernate Envers | Spring AOP Log |
|------|-----|--------|------------------|----------------|
| 流程追踪 | ✅ | ❌ | ❌ | 部分 |
| 变更追踪 | ✅ | ✅ | ✅ | ❌ |
| 对象比较 | ✅ | ✅ | ❌ | ❌ |
| 注解驱动 | ✅ | ✅ | ✅ | ✅ |
| 异常安全 | ✅ | ❌ | ❌ | ❌ |
| 零泄漏 | ✅ | ❌ | N/A | ❌ |
| Actuator | ✅ | ❌ | ❌ | ❌ |
| 非 Spring | ✅ | ✅ | ❌ | ❌ |
| 类型系统 | ✅ (@Entity/@ValueObject) | ✅ | ❌ | ❌ |
| SPI 扩展 | ✅ (v4.0) | 部分 | ❌ | ❌ |
| 性能监控 | ✅ | ❌ | ❌ | ❌ |

### 8.2 差异化定位

TFI 的独特定位在于**流程追踪 + 变更追踪的一站式方案**，这是 Javers 和 Envers 所不具备的。同时，TFI 的异常安全和零泄漏设计使其在生产环境中更加安全可靠。

---

## 9. 版本路线图

### 9.1 已发布版本

| 版本 | 发布时间 | 核心功能 |
|------|----------|----------|
| v1.0 | - | 基础流程追踪 (Session/Task/Stage/Message) |
| v2.0 | - | 变更追踪 + 对象比较 + 导出系统 |
| v2.5 | - | 注解驱动 (@TfiTask/@TfiTrack) |
| v3.0 | - | Actuator 端点 + 度量指标 + 健康检查 + 多模块重构 |

### 9.2 当前版本 (v4.0.0)

| 功能 | 状态 | 说明 |
|------|------|------|
| Provider 路由 (SPI) | 🔄 开发中 | 可插拔子系统替换 |
| ServiceLoader 支持 | 🔄 开发中 | META-INF/services 自动发现 |
| 路由性能门槛 | 🔄 测试中 | CI 回归 < 5% |
| API 兼容性保证 | ✅ | japicmp 集成 |

### 9.3 规划版本

| 版本 | 规划功能 | 优先级 |
|------|----------|--------|
| v4.1 | OpenTelemetry 集成 | P1 |
| v4.2 | Web UI 可视化面板 | P2 |
| v5.0 | GraalVM Native Image 支持 | P2 |
| v5.0 | 分布式追踪上下文传播 | P1 |

---

## 10. 验收标准

### 10.1 功能验收

| 编号 | 验收项 | 验收方法 |
|------|--------|----------|
| AC-1 | 流程追踪生成正确的层级树 | 单元测试 + 集成测试 |
| AC-2 | 变更追踪检测所有字段变化 | 参数化测试 + 黄金文件 |
| AC-3 | 对象比较支持所有策略 | 策略矩阵测试 |
| AC-4 | 导出格式符合规范 | ApprovalTests |
| AC-5 | Actuator 端点可正常访问 | Spring Boot 集成测试 |
| AC-6 | Provider 路由正确分发 | SPI 测试 + 路由测试 |
| AC-7 | TFI 禁用时零开销 | JMH 基准测试 |

### 10.2 质量验收

| 编号 | 验收项 | 标准 |
|------|--------|------|
| QA-1 | 代码覆盖率 | ≥ 50% 指令覆盖率 (JaCoCo) |
| QA-2 | 静态分析 | SpotBugs 0 High 缺陷 |
| QA-3 | 编码规范 | Checkstyle Google 规范 |
| QA-4 | 代码质量 | PMD 无 Critical 违规 |
| QA-5 | API 兼容 | japicmp 无破坏性变更 |
| QA-6 | 性能基准 | JMH 路由回归 < 5% |

---

## 11. 风险与约束

### 11.1 技术风险

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 深度追踪内存消耗 | 大对象图导致 OOM | `max-depth` 配置 + 降级策略 |
| ThreadLocal 泄漏 | 线程池场景 | 零泄漏检测 + `TFI.clear()` |
| Provider 路由性能 | 额外间接调用开销 | CI 性能门槛 < 5% |
| JDK 升级兼容 | 反射 API 变更 | 最小化反射使用 |

### 11.2 产品风险

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 用户学习成本 | 降低采用率 | 完善示例 + 渐进式教学 |
| 与已有工具重叠 | 用户犹豫 | 强调一站式 + 差异化价值 |
| 社区活跃度 | 长期维护 | 开源运营 + 企业支持 |

### 11.3 约束条件

| 约束 | 描述 |
|------|------|
| JDK 版本 | 仅支持 JDK 21+（使用 Records、虚拟线程） |
| Spring 版本 | 仅支持 Spring Boot 3.x |
| 构建工具 | Maven（不支持 Gradle） |
| 语言 | 仅支持 Java（不支持 Kotlin/Scala 特殊语法） |

---

> **文档编写**: 资深产品经理  
> **审核**: 项目经理  
> **下次评审日期**: 依据产品迭代节奏
