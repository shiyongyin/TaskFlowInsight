# TaskFlowInsight (tfi-all) 测试方案

> **版本**: v3.0.0 / v4.0.0-routing-refactor  
> **编写角色**: 资深测试专家  
> **更新日期**: 2026-02-16  
> **文档状态**: 正式版

---

## 目录

- [1. 测试概述](#1-测试概述)
- [2. 测试策略](#2-测试策略)
- [3. 白盒测试方案](#3-白盒测试方案)
- [4. 黑盒测试方案](#4-黑盒测试方案)
- [5. 功能测试方案](#5-功能测试方案)
- [6. 性能测试方案](#6-性能测试方案)
- [7. 专项测试方案](#7-专项测试方案)
- [8. 测试环境](#8-测试环境)
- [9. 测试工具链](#9-测试工具链)
- [10. 测试度量与质量门禁](#10-测试度量与质量门禁)
- [11. 当前测试现状分析](#11-当前测试现状分析)
- [12. 测试改进计划](#12-测试改进计划)

---

## 1. 测试概述

### 1.1 测试目标

确保 TaskFlowInsight (TFI) 在以下维度达到生产就绪标准：

| 维度 | 目标 |
|------|------|
| **功能正确性** | 所有 API 行为符合设计规约 |
| **可靠性** | 异常安全、零泄漏、线程安全 |
| **性能** | 满足延迟和吞吐量 SLA |
| **兼容性** | API 向后兼容，跨版本无断裂 |
| **可维护性** | 测试代码本身可维护、可扩展 |

### 1.2 测试范围

| 模块 | 测试级别 | 说明 |
|------|----------|------|
| **tfi-all** | 集成测试主阵地 | 395 个测试类，覆盖全链路 |
| **tfi-flow-core** | 单元测试 | 上下文管理、模型、SPI |
| **tfi-compare** | 单元 + 集成 | 比较引擎、变更追踪、快照 |
| **tfi-flow-spring-starter** | 集成测试 | AOP 切面、Spring 自动配置 |
| **tfi-ops-spring** | 集成测试 | Actuator 端点、指标、健康 |
| **tfi-examples** | 冒烟测试 + 基准 | Demo 可运行性、JMH 基准 |

### 1.3 测试金字塔

```
                    ┌─────────┐
                    │  E2E    │  ~5%   端到端场景测试
                    │ 测试    │
                ┌───┴─────────┴───┐
                │   集成测试       │  ~25%  Spring 上下文、跨模块
                │                 │
            ┌───┴─────────────────┴───┐
            │       单元测试           │  ~70%  隔离、Mock、快速
            │                         │
            └─────────────────────────┘
```

---

## 2. 测试策略

### 2.1 分层测试策略

| 层级 | 测试类型 | 命名约定 | 执行条件 | 框架 |
|------|----------|----------|----------|------|
| L1 单元 | 隔离测试 | `*Test.java` / `*Tests.java` | 每次构建 | JUnit 5 + Mockito + AssertJ |
| L2 集成 | Spring 上下文 | `*IntegrationTest.java` / `*IT.java` | 每次构建 | @SpringBootTest |
| L3 性能 | 基准 + 门禁 | `*PerformanceTest.java` / `*PerfGateIT.java` | `-Pperf` Profile | JMH + @EnabledIfSystemProperty |
| L4 架构 | 依赖规则 | `*ArchTests.java` | 每次构建 | ArchUnit |
| L5 属性 | 随机输入 | `*PropertyTests.java` | 每次构建 | jqwik |
| L6 快照 | 黄金文件 | `*GoldenTest.java` | 每次构建 | ApprovalTests |
| L7 混沌 | 压力注入 | `*ChaosTests.java` | 手动触发 | 自定义 |

### 2.2 测试执行命令

```bash
# L1-L2: 标准测试 (每次构建)
./mvnw test

# L3: 性能测试 (按需)
./mvnw test -Pperf
./mvnw verify -Pperf    # 包含 PerfGateIT

# L4: 架构测试 (包含在标准测试中)
./mvnw test -Dtest=*ArchTests

# 覆盖率报告
./mvnw clean test jacoco:report
# 报告路径: tfi-all/target/site/jacoco/index.html

# API 兼容性
./mvnw verify -Papi-compat

# JMH 基准测试
./mvnw -q -P bench -DskipTests exec:java \
  -Dexec.mainClass=com.syy.taskflowinsight.benchmark.BenchmarkRunner
```

---

## 3. 白盒测试方案

### 3.1 测试范围

白盒测试基于代码结构和逻辑路径设计测试用例，覆盖以下关键代码路径。

### 3.2 TFI Facade 白盒测试

#### 3.2.1 分支覆盖

| 方法 | 关键分支 | 测试用例 |
|------|----------|----------|
| `stage(name)` | TFI 启用/禁用 | WB-F-001: 启用时返回真实 TaskContext |
| | | WB-F-002: 禁用时返回 NullTaskContext |
| | Provider 路由启用/禁用 | WB-F-003: 路由启用时走 FlowProvider |
| | | WB-F-004: 路由禁用时走 Legacy 路径 |
| | name 为 null/empty | WB-F-005: null 参数处理 |
| `track(name, obj, fields)` | 变更追踪启用/禁用 | WB-T-001: 启用时记录基线快照 |
| | | WB-T-002: 禁用时 no-op |
| | 超过最大追踪数 | WB-T-003: 超限降级处理 |
| | | WB-T-004: 超限日志警告 |
| `compare(a, b)` | 两对象相同 | WB-C-001: 相同对象返回空变更 |
| | null 参数 | WB-C-002: null 参数安全处理 |
| | Provider 路由 | WB-C-003: 路由到 ComparisonProvider |

#### 3.2.2 异常路径覆盖

| 编号 | 场景 | 期望行为 | 测试类 |
|------|------|----------|--------|
| WB-EX-001 | stage 内抛出异常 | 异常传播，stage 自动关闭 | TFIStageAPITest |
| WB-EX-002 | Provider 抛出异常 | 降级到 Legacy，记录日志 | TFIPhase2RoutingTest |
| WB-EX-003 | 快照过程异常 | 返回空变更列表，记录日志 | TFIBoundaryTest |
| WB-EX-004 | DiffDetector 异常 | 返回空变更列表 | DiffDetectorTest |
| WB-EX-005 | 递归深度超限 | 中断递归，返回已收集结果 | ObjectSnapshotDeepTest |

### 3.3 上下文管理白盒测试

| 编号 | 路径 | 测试点 |
|------|------|--------|
| WB-CTX-001 | 正常生命周期 | register → use → unregister |
| WB-CTX-002 | 嵌套 stage | 多层 stage 正确维护 taskStack |
| WB-CTX-003 | 跨线程传播 | createSnapshot → restoreFromSnapshot |
| WB-CTX-004 | 泄漏检测 | 超时未关闭触发检测 |
| WB-CTX-005 | 死线程清理 | WeakReference + ReferenceQueue 清理 |
| WB-CTX-006 | 并发注册 | ConcurrentHashMap 并发安全 |

### 3.4 比较引擎白盒测试

#### 3.4.1 策略选择路径

| 编号 | 输入类型 | 期望策略 | 测试用例 |
|------|----------|----------|----------|
| WB-CMP-001 | 两个 POJO | DetailedCompareStrategy | CompareEngineTests |
| WB-CMP-002 | 两个 Map | MapCompareStrategy | MapKeyNoEntityTests |
| WB-CMP-003 | 两个 @Entity List | EntityListStrategy | EntityListStrategyContainerEventTests |
| WB-CMP-004 | 两个 @ValueObject List | AsSetListStrategy | ListLifecycleTypedViewIT |
| WB-CMP-005 | 两个数组 | ArrayCompareStrategy | CompareEngineTests |
| WB-CMP-006 | 带 @NumericPrecision | NumericCompareStrategy | PrecisionControllerTest |
| WB-CMP-007 | LCS 启用 | LevenshteinListStrategy | LevenshteinEditDistanceTests |

#### 3.4.2 路径去重白盒

| 编号 | 场景 | 测试点 |
|------|------|--------|
| WB-PATH-001 | 简单字段路径 | `order.status` 格式正确 |
| WB-PATH-002 | Map key 路径 | `map["key"]` 特殊字符转义 |
| WB-PATH-003 | 数组索引路径 | `items[0]` 边界索引 |
| WB-PATH-004 | 候选路径爆炸 | Top-N 裁剪启动 |
| WB-PATH-005 | 快速路径回退 | 变更数超阈值走传统去重 |

### 3.5 代码覆盖率要求

| 模块 | 指令覆盖率 | 分支覆盖率 | 方法覆盖率 |
|------|------------|------------|------------|
| tfi-all (main) | ≥ 50% | ≥ 40% | ≥ 60% |
| tfi-flow-core | ≥ 60% | ≥ 50% | ≥ 70% |
| tfi-compare | ≥ 55% | ≥ 45% | ≥ 65% |
| tfi-ops-spring | ≥ 50% | ≥ 40% | ≥ 60% |

**排除覆盖率**: `com.syy.taskflowinsight.model.*`, `com.syy.taskflowinsight.demo.**`

---

## 4. 黑盒测试方案

### 4.1 测试范围

黑盒测试不关注内部实现，仅基于 API 规约和用户场景设计用例。

### 4.2 TFI Facade 黑盒测试

#### 4.2.1 流程追踪测试用例

| 编号 | 输入 | 期望输出 | 优先级 |
|------|------|----------|--------|
| BB-FLOW-001 | `TFI.stage("test")` → 使用 → 关闭 | getCurrentSession 包含该 stage | P0 |
| BB-FLOW-002 | 嵌套 3 层 stage | exportToConsole 显示 3 层树 | P0 |
| BB-FLOW-003 | `TFI.message("info", MessageType.INFO)` | 消息出现在当前任务中 | P0 |
| BB-FLOW-004 | `TFI.error("err", exception)` | 错误消息包含异常信息 | P0 |
| BB-FLOW-005 | `TFI.disable()` → 所有操作 | 所有操作返回安全默认值 | P0 |
| BB-FLOW-006 | `TFI.run("task", () -> { ... })` | 任务执行后自动关闭 | P1 |
| BB-FLOW-007 | `TFI.call("task", () -> result)` | 返回 lambda 结果 | P1 |
| BB-FLOW-008 | 无 startSession 直接 stage | 自动创建默认 Session | P1 |

#### 4.2.2 变更追踪测试用例

| 编号 | 输入 | 期望输出 | 优先级 |
|------|------|----------|--------|
| BB-TRACK-001 | track → 修改字段 → getChanges | 返回包含变更记录 | P0 |
| BB-TRACK-002 | track → 不修改 → getChanges | 返回空列表 | P0 |
| BB-TRACK-003 | trackDeep → 修改嵌套对象 → getChanges | 检测到嵌套变更 | P0 |
| BB-TRACK-004 | track 多个对象 → getChanges | 返回所有对象变更 | P0 |
| BB-TRACK-005 | clearAllTracking → getChanges | 返回空列表 | P1 |
| BB-TRACK-006 | track → getChanges → 再次修改 → getChanges | 第二次仅返回新变更 | P1 |
| BB-TRACK-007 | @DiffIgnore 字段修改 | 不出现在变更列表中 | P1 |
| BB-TRACK-008 | @NumericPrecision 字段精度变化 | 精度内变化不报告 | P2 |

#### 4.2.3 对象比较测试用例

| 编号 | 输入 | 期望输出 | 优先级 |
|------|------|----------|--------|
| BB-CMP-001 | compare(objA, objB) 不同字段值 | CompareResult 包含差异 | P0 |
| BB-CMP-002 | compare(objA, objA) 相同对象 | CompareResult 无差异 | P0 |
| BB-CMP-003 | compare(null, obj) | 安全处理，不抛异常 | P0 |
| BB-CMP-004 | compare 含 @Entity List | 按 @Key 匹配 | P0 |
| BB-CMP-005 | render(result, "standard") | 返回 Markdown 格式字符串 | P1 |
| BB-CMP-006 | comparator().source(a).target(b).build() | 流式 API 正确工作 | P1 |

#### 4.2.4 导出测试用例

| 编号 | 输入 | 期望输出 | 优先级 |
|------|------|----------|--------|
| BB-EXP-001 | exportToConsole() | 格式化文本输出到 stdout | P0 |
| BB-EXP-002 | exportToJson() | 合法 JSON 字符串 | P0 |
| BB-EXP-003 | exportToMap() | 非空 Map 结构 | P1 |
| BB-EXP-004 | 空 Session 导出 | 空结果或默认结构 | P1 |

### 4.3 Actuator 端点黑盒测试

| 编号 | 请求 | 期望响应 | 优先级 |
|------|------|----------|--------|
| BB-ACT-001 | `GET /actuator/taskflow` | 200, JSON 包含 status 和 tasks | P0 |
| BB-ACT-002 | `GET /actuator/taskflow-context` | 200, JSON 包含 context 诊断 | P1 |
| BB-ACT-003 | `GET /actuator/tfi-metrics` | 200, JSON 包含 metrics 数据 | P1 |
| BB-ACT-004 | `GET /actuator/health` | 200, TFI 组件状态 UP/DOWN | P0 |
| BB-ACT-005 | Actuator 禁用配置 | 端点返回 404 | P1 |

---

## 5. 功能测试方案

### 5.1 核心功能测试矩阵

| 功能模块 | 测试场景数 | 自动化率 | 关键测试类 |
|----------|------------|----------|------------|
| 流程追踪 | 25+ | 100% | TFIStageAPITest, TFIComprehensiveApiTests |
| 变更追踪 | 30+ | 100% | ChangeTrackerTests, DiffDetectorTests |
| 对象比较 | 50+ | 100% | CompareEngineTests, EntityListStrategy*Tests |
| 路径系统 | 40+ | 100% | PathBuilderTest, PathDeduplicatorTest |
| 快照系统 | 30+ | 100% | ObjectSnapshotTests, SnapshotFilter*Tests |
| 导出系统 | 20+ | 100% | ConsoleExporter*Tests, JsonExporter*Tests |
| 注解系统 | 15+ | 100% | TfiAnnotationAspectTest, TypeSystem*Tests |
| Actuator | 25+ | 100% | TfiEndpoint*Tests, SecureTfiEndpoint*Tests |
| SPI 路由 | 20+ | 100% | ProviderRegistry*Tests, TFIRouting*Tests |
| 精度控制 | 15+ | 100% | Precision*Tests, NumericDetector*Tests |
| 降级监控 | 10+ | 100% | Degradation*Tests |

### 5.2 端到端功能测试

#### 5.2.1 E2E-001: 完整业务流程追踪

```
前置条件: TFI 启用, Spring 上下文加载
步骤:
  1. TFI.startSession("order-process")
  2. try (var stage1 = TFI.stage("createOrder"))
     - TFI.message("Order created: ORD-001")
  3. try (var stage2 = TFI.stage("payment"))
     - try (var stage2a = TFI.stage("validate"))
     - try (var stage2b = TFI.stage("charge"))
  4. TFI.endSession()
  5. String json = TFI.exportToJson()
期望:
  - JSON 包含 3 层嵌套结构
  - 所有 message 正确关联到对应 stage
  - Session 状态为 COMPLETED
```

#### 5.2.2 E2E-002: 变更追踪 + 比较全链路

```
前置条件: TFI 启用, 变更追踪启用
步骤:
  1. 创建 Order 对象 (status=PENDING, amount=100.00)
  2. TFI.track("order", order, "status", "amount")
  3. 修改 order.status = "PAID", order.amount = 90.00
  4. List<ChangeRecord> changes = TFI.getChanges()
  5. 创建 Order before/after 对比对象
  6. CompareResult result = TFI.compare(before, after)
  7. String report = TFI.render(result, "standard")
期望:
  - changes 包含 2 条记录 (status, amount)
  - CompareResult 包含对应差异
  - report 为合法 Markdown 格式
```

#### 5.2.3 E2E-003: Provider 路由全链路 (v4.0.0)

```
前置条件: tfi.api.routing.enabled=true
步骤:
  1. 注册自定义 FlowProvider
  2. TFI.stage("test") → 应走自定义 Provider
  3. 注销自定义 Provider
  4. TFI.stage("test") → 应走默认 Provider
期望:
  - 自定义 Provider 被正确调用
  - 注销后回退到默认实现
  - 无异常抛出
```

### 5.3 边界条件测试

| 编号 | 场景 | 输入 | 期望 |
|------|------|------|------|
| BC-001 | 空字符串 stage name | `TFI.stage("")` | 安全处理 |
| BC-002 | 超长 stage name | 1000 字符 | 截断或安全处理 |
| BC-003 | 追踪 null 对象 | `TFI.track("x", null)` | 不抛异常 |
| BC-004 | 追踪循环引用对象 | A → B → A | 检测循环，安全终止 |
| BC-005 | 1000 个并发 stage | 多线程同时创建 | 线程安全，无数据混乱 |
| BC-006 | 超大对象图 (10 层) | 深度快照 | 在 max-depth 处截断 |
| BC-007 | 超过 MAX_TRACKED_OBJECTS | 1001 个追踪对象 | 降级处理 + 日志警告 |
| BC-008 | getChanges 调用两次 | 连续调用 | 第二次基于新基线 |

---

## 6. 性能测试方案

### 6.1 性能测试目标

| 指标 | SLA | 测量方法 |
|------|-----|----------|
| Stage 创建 + 关闭延迟 | P50 < 20μs, P95 < 50μs | JMH |
| 浅层快照延迟 (2 字段) | P50 < 50μs | JMH |
| Diff 检测延迟 (2 字段) | P95 < 200μs | JMH |
| 深度快照延迟 (10 层) | P95 < 5ms | JMH |
| TFI 禁用开销 | < 10ns | JMH |
| Provider 路由开销 | 回归 < 5% | CI PerfGate |
| 内存占用 (100 个追踪) | < 10MB 增量 | SnapshotMemoryTest |

### 6.2 JMH 基准测试清单

| 基准类 | 测试内容 | 指标 |
|--------|----------|------|
| `TFIRoutingBenchmark` | Provider 路由 vs Legacy 延迟 | ops/ms |
| `ProviderRegistryBenchmark` | Provider 查找性能 | ops/ms |
| `ReferenceChangeBenchmarks` | 引用语义变更检测 | ops/ms |
| `QueryApiBenchmarks` | 查询 API 性能 | ops/ms |
| `P1PerformanceBenchmark` | P1 级性能基准 | ops/ms |
| `P1MemoryBenchmark` | 内存分配基准 | bytes/op |
| `MapSetLargeBenchmarks` | 大 Map/Set 比较 | ops/ms |
| `FilterBenchmarks` | 快照过滤性能 | ops/ms |
| `TypedViewBenchmark` | 类型化视图查询 | ops/ms |

### 6.3 性能门禁 (CI 集成)

#### 6.3.1 PerfGateIT 测试

```java
@EnabledIfSystemProperty(named = "tfi.perf.enabled", matches = "true")
class TfiRoutingPerfGateIT {
    // 路由性能回归 < 5%
    // 失败则 CI 构建失败 (tfi.perf.strict=true)
}
```

#### 6.3.2 GitHub Actions 性能流水线

```yaml
# perf-gate.yml
triggers: push/PR on main, workflow_dispatch
steps:
  1. JMH 路由基准测试
  2. PerfGateIT (tfi.perf.strict=true)
  3. 回归 < 5% 则通过
```

### 6.4 压力测试方案

| 编号 | 场景 | 并发数 | 持续时间 | 关注指标 |
|------|------|--------|----------|----------|
| STRESS-001 | 并发 stage 创建 | 100 线程 | 60s | 吞吐量、P99 延迟 |
| STRESS-002 | 并发变更追踪 | 50 线程 | 60s | 内存占用、GC 频率 |
| STRESS-003 | 并发对象比较 | 50 线程 | 60s | CPU 使用率 |
| STRESS-004 | Provider 并发注册/查询 | 100 线程 | 30s | 数据一致性 |
| STRESS-005 | 大对象图深度追踪 | 10 线程 | 30s | 内存峰值、OOM 风险 |

### 6.5 内存测试方案

| 编号 | 场景 | 关注点 |
|------|------|--------|
| MEM-001 | 长时间运行追踪不清理 | 内存泄漏 |
| MEM-002 | 大量 Session 累积 | 内存增长曲线 |
| MEM-003 | 深度快照大对象 | 单次快照内存峰值 |
| MEM-004 | Caffeine 缓存淘汰 | 缓存大小控制 |
| MEM-005 | ThreadLocal 清理 | 线程池场景泄漏检测 |

---

## 7. 专项测试方案

### 7.1 线程安全测试

| 编号 | 场景 | 测试方法 |
|------|------|----------|
| TS-001 | 多线程并发 TFI.stage() | 100 线程并发，验证 Session 隔离 |
| TS-002 | 多线程并发 track/getChanges | 50 线程并发，验证数据不混乱 |
| TS-003 | 异步上下文传播 | 父子线程上下文验证 |
| TS-004 | Provider 并发注册/注销 | 读写锁并发测试 |
| TS-005 | SafeContextManager 并发访问 | ConcurrentHashMap 压力测试 |

### 7.2 异常安全测试

| 编号 | 场景 | 注入异常 | 期望 |
|------|------|----------|------|
| ES-001 | stage 内业务异常 | RuntimeException | stage 自动关闭，异常透传 |
| ES-002 | Provider 内部异常 | 自定义异常 | 降级到 Legacy，记录日志 |
| ES-003 | 快照过程反射异常 | IllegalAccessException | 返回空结果 |
| ES-004 | Diff 比较异常 | NPE | 返回空变更列表 |
| ES-005 | 导出过程异常 | IOException | 安全失败，记录日志 |

### 7.3 架构测试 (ArchUnit)

| 编号 | 规则 | 测试类 |
|------|------|--------|
| ARCH-001 | API 层不依赖 Spring | ApiNoSpringDependencyTests |
| ARCH-002 | SPI 接口不依赖实现 | SpiArchTests |
| ARCH-003 | 模型类无业务逻辑 | TFIArchitectureTest |
| ARCH-004 | 循环依赖检测 | TFIArchitectureTest |

### 7.4 API 兼容性测试

```bash
./mvnw verify -Papi-compat
# japicmp 对比 v3.0.0 基线
# 聚焦: com.syy.taskflowinsight.api.TFI
# 不允许: 删除公共方法、修改方法签名
# 允许: 新增方法、新增类
```

### 7.5 属性测试 (Property-based)

```java
@Property
void stageShouldNeverThrow(@ForAll String name) {
    // 任何输入的 stage name 都不应该抛出异常
    assertDoesNotThrow(() -> {
        try (var stage = TFI.stage(name)) {
            // no-op
        }
    });
}
```

### 7.6 快照/黄金文件测试

| 测试类 | 验证内容 |
|--------|----------|
| `TfiRoutingGoldenTest` | 路由行为输出与黄金文件匹配 |
| `ObjectSnapshotDeepAnnotationVsRulesGoldenTests` | 深度快照注解 vs 规则输出一致 |
| `ContainerEventsGoldenIntegrationTests` | 容器事件输出格式固定 |

---

## 8. 测试环境

### 8.1 环境配置

| 环境 | JDK | Spring Boot | 用途 |
|------|-----|-------------|------|
| 本地开发 | 21 | 3.5.5 | 开发者日常测试 |
| CI (GitHub Actions) | 21 | 3.5.5 | 自动化构建 + 测试 |
| 性能测试 | 21 | 3.5.5 | JMH 基准 + 压力测试 |

### 8.2 测试配置文件

| 文件 | 位置 | 用途 |
|------|------|------|
| `logback-test.xml` | `tfi-all/src/test/resources/` | 测试日志级别 (DiffDetector=DEBUG) |
| `TestApplication.java` | `tfi-all/src/test/java/` | 测试用 Spring Boot 应用 |
| System Properties | Maven Surefire | `tfi.perf.enabled=false` (默认) |

---

## 9. 测试工具链

### 9.1 工具矩阵

| 工具 | 版本 | 用途 | 配置 |
|------|------|------|------|
| JUnit 5 | 5.x (Spring Boot Managed) | 测试框架 | spring-boot-starter-test |
| Mockito | 5.x | Mock 框架 | @ExtendWith(MockitoExtension) |
| AssertJ | 3.x | 流式断言 | assertThat() 链式调用 |
| ArchUnit | 1.3.0 | 架构测试 | archunit-junit5 |
| jqwik | 1.8.4 | 属性测试 | @Property 注解 |
| ApprovalTests | 24.3.0 | 快照测试 | Approvals.verify() |
| JaCoCo | 0.8.12 | 覆盖率 | jacoco-maven-plugin |
| JMH | 1.37 | 微基准测试 | jmh-core + generator |
| SpotBugs | 4.8.6.2 | 静态缺陷 | spotbugs-maven-plugin |
| Checkstyle | 3.4.0 | 风格检查 | google_checks.xml |
| PMD | 3.25.0 | 规则检查 | 6 规则集 |
| japicmp | 0.24.2 | API 兼容 | -Papi-compat Profile |

---

## 10. 测试度量与质量门禁

### 10.1 质量门禁 (Quality Gate)

| 门禁 | 阈值 | 执行阶段 | 失败策略 |
|------|------|----------|----------|
| 单元测试通过率 | 100% | `mvnw test` | 构建失败 |
| JaCoCo 指令覆盖率 | ≥ 50% | `mvnw test` (tfi-all) | 构建失败 |
| SpotBugs High 缺陷 | 0 | `mvnw verify` | 警告 (failOnError=false) |
| Checkstyle 违规 | ≤ 30000 | `mvnw verify` | 警告 |
| PMD Critical | 0 | `mvnw verify` | 警告 (failOnViolation=false) |
| API 兼容性 | 无破坏变更 | `-Papi-compat` | 构建失败 |
| 性能回归 | < 5% | `-Pperf` | CI 失败 (strict) |

### 10.2 测试报告

| 报告 | 路径 | 生成命令 |
|------|------|----------|
| JaCoCo 覆盖率 | `tfi-all/target/site/jacoco/index.html` | `mvnw clean test jacoco:report` |
| Surefire 测试报告 | `tfi-all/target/surefire-reports/` | `mvnw test` |
| SpotBugs 报告 | `target/spotbugsXml.xml` | `mvnw spotbugs:check` |
| Checkstyle 报告 | `target/checkstyle-result.xml` | `mvnw checkstyle:check` |
| PMD 报告 | `target/pmd.xml` | `mvnw pmd:check` |

---

## 11. 当前测试现状分析

### 11.1 测试覆盖热力图

| 包/模块 | 测试密度 | 覆盖评级 |
|---------|----------|----------|
| api (TFI Facade) | ★★★★★ | 优秀 |
| actuator (端点) | ★★★★★ | 优秀 |
| tracking.compare (比较) | ★★★★★ | 优秀 |
| tracking.compare.list (列表比较) | ★★★★★ | 优秀 |
| tracking.snapshot (快照) | ★★★★☆ | 良好 |
| tracking.path (路径) | ★★★★★ | 优秀 |
| tracking.detector (差异检测) | ★★★★☆ | 良好 |
| tracking.precision (精度) | ★★★★☆ | 良好 |
| tracking.monitoring (降级) | ★★★★☆ | 良好 |
| exporter (导出) | ★★★☆☆ | 中等 |
| spi (Provider) | ★★★★☆ | 良好 |
| context (上下文) | ★★★☆☆ | 中等 |
| config (配置) | ★★☆☆☆ | 不足 |
| core (TfiCore) | ★☆☆☆☆ | 缺失 |

### 11.2 已知测试空白

| 编号 | 空白区域 | 风险等级 | 建议 |
|------|----------|----------|------|
| GAP-001 | `TfiCore` 无专用单元测试 | 中 | 新增 TfiCoreTest |
| GAP-002 | Change Exporter (JSON/CSV/Console/Map) 未直接测试 | 中 | 新增 Exporter 测试 |
| GAP-003 | `ConfigurationResolver` 系列未测试 | 低 | 新增配置解析测试 |
| GAP-004 | `ThreeWayMergeService` 无测试 | 中 | 新增三方合并测试 |
| GAP-005 | `ConcurrentRetryUtil` 未测试 | 低 | 新增并发重试测试 |
| GAP-006 | `DiffRegistry` / `ObjectTypeResolver` 未直接测试 | 低 | 考虑通过集成测试覆盖 |

---

## 12. 测试改进计划

### 12.1 短期 (1-2 周)

| 优先级 | 任务 | 预期效果 |
|--------|------|----------|
| P0 | 补充 TfiCore 单元测试 | 消除核心类测试空白 |
| P0 | 补充 Change Exporter 直接测试 | 提升导出模块覆盖率 |
| P1 | 补充 ThreeWayMergeService 测试 | 覆盖合并场景 |

### 12.2 中期 (1 个月)

| 优先级 | 任务 | 预期效果 |
|--------|------|----------|
| P1 | 提升 JaCoCo 覆盖率门槛到 60% | 更高质量保证 |
| P1 | 添加 Mutation Testing (PIT) | 评估测试质量 |
| P2 | 统一测试命名规范 (全部 *Tests) | 代码一致性 |

### 12.3 长期 (1 个季度)

| 优先级 | 任务 | 预期效果 |
|--------|------|----------|
| P2 | 增加 Contract Testing | API 消费者契约验证 |
| P2 | 增加 Chaos Engineering 测试 | 系统韧性验证 |
| P3 | 自动化性能回归报告 | 趋势监控 |

---

> **文档编写**: 资深测试专家  
> **审核**: 项目经理  
> **下次评审日期**: 依据测试迭代计划
