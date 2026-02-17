# tfi-flow-core 测试方案

> **负责人**: 王磊（资深测试专家）| **版本**: v4.0 | **评审日期**: 2026-02-15

---

## 一、测试策略总览

### 1.1 测试金字塔

```
            /\
           /  \        性能基准测试（JMH）
          / 10 \       BM-001 ~ BM-010
         /______\
        /        \     集成测试
       /    35    \    FlowLifecycle / Async / MemoryLeak
      /____________\
     /              \   单元测试
    /      393       \  28 个测试类，393 个测试方法
   /__________________\
   
   总计: 428 个测试用例，100% 通过
```

### 1.2 质量门禁

| 指标 | 门禁值 | 当前值 | 状态 |
|------|--------|--------|------|
| 测试通过率 | 100% | 100% | ✅ |
| 指令覆盖率 | ≥ 80% | 81.7% | ✅ |
| 分支覆盖率 | ≥ 70% | 70.3% | ✅ |
| Checkstyle 违规 | 0 | 0 | ✅ |
| SpotBugs 缺陷 | 0 | 0 | ✅ |

### 1.3 测试技术栈

| 工具 | 用途 | 版本 |
|------|------|------|
| JUnit Jupiter | 单元/集成测试框架 | 5.x |
| AssertJ | 流式断言 | 3.x |
| JaCoCo | 代码覆盖率 | 0.8.12 |
| JMH | 性能基准测试 | 1.37 |
| Checkstyle | 代码规范检查 | 3.6.0 |
| SpotBugs | 静态缺陷分析 | 4.8.6 |

---

## 二、白盒测试

### 2.1 测试类清单

#### API 层（5 个测试类，132 个用例）

| 测试类 | 测试目标 | 用例数 | 关键覆盖点 |
|--------|----------|--------|------------|
| TfiFlowTest | TfiFlow 门面 | 28 | 启用/禁用、会话、任务、消息、导出、异常安全 |
| TfiFlowEdgeCaseTest | TfiFlow 边界条件 | 31 | null 参数、空字符串、禁用态返回值、Provider 路径 |
| TfiFlowProviderPathTest | Provider SPI 路径 | 17 | DefaultFlowProvider 注册后的完整流程 |
| TaskContextImplTest | TaskContext 实现 | 29 | 链式调用、子任务、close 行为、异常标记 |
| NullTaskContextTest | 空对象实现 | 17 | 所有方法返回 this/默认值，close 幂等 |

#### Context 层（5 个测试类，89 个用例）

| 测试类 | 测试目标 | 用例数 | 关键覆盖点 |
|--------|----------|--------|------------|
| SafeContextManagerTest | 全局上下文管理 | 27 | 注册/注销、泄漏检测、异步传播、LeakListener |
| ThreadContextTest | ThreadContext 静态 API | 24 | create/current/clear/propagate/statistics |
| ZeroLeakThreadLocalManagerTest | 零泄漏管理 | 14 | 健康检查、清理、反射路径 |
| ZeroLeakNestedStageTest | 嵌套 Stage 跟踪 | 19 | registerNestedStage/cleanup/batch/status |
| ContextPropagatingExecutorTest | 上下文传播执行器 | 5 | execute/submit/上下文传播验证 |

#### Model 层（3 个测试类，76 个用例）

| 测试类 | 测试目标 | 用例数 | 关键覆盖点 |
|--------|----------|--------|------------|
| SessionTest | 会话生命周期 | 20 | 创建、激活、完成、错误、状态机、耗时计算 |
| TaskNodeTest | 任务树节点 | 31 | 父子关系、消息添加、状态转换、耗时统计 |
| MessageTest | 消息对象 | 25 | 工厂方法、类型、标签、不可变性、equals/hashCode |

#### SPI 层（3 个测试类，43 个用例）

| 测试类 | 测试目标 | 用例数 | 关键覆盖点 |
|--------|----------|--------|------------|
| ProviderRegistryTest | 注册中心 | 14 | register/unregister/lookup/priority/clearAll |
| ProviderRegistryExtendedTest | 扩展测试 | 8 | loadProviders/系统属性白名单/ServiceLoader |
| DefaultFlowProviderTest | 默认 Provider | 21 | 会话/任务/消息/清理全流程 |

#### Exporter 层（3 个测试类，30 个用例）

| 测试类 | 测试目标 | 用例数 | 关键覆盖点 |
|--------|----------|--------|------------|
| ConsoleExporterTest | 控制台导出 | 14 | emoji 图标、树连线、状态、耗时、嵌套层级 |
| JsonExporterTest | JSON 导出 | 11 | 格式正确性、COMPAT/ENHANCED 模式、Writer 输出 |
| MapExporterTest | Map 导出 | 5 | 结构完整性、嵌套任务、消息列表 |

#### 其他（9 个测试类，58 个用例）

| 测试类 | 测试目标 | 用例数 |
|--------|----------|--------|
| EnumsTest (SessionStatus) | 会话状态枚举 | 6 |
| EnumsTest (TaskStatus) | 任务状态枚举 | 6 |
| EnumsTest (MessageType) | 消息类型枚举 | 8 |
| ConfigDefaultsTest | 配置常量 | 6 |
| DiagnosticLoggerTest | 诊断日志器 | 7 |
| FlowLifecycleIntegrationTest | 全流程集成 | 6 |
| AsyncContextPropagationTest | 异步传播集成 | 22 |
| MemoryLeakTest | 内存泄漏检测 | 7 |

### 2.2 覆盖率分析

#### 按包覆盖率

| 包 | 指令覆盖率 | 分支覆盖率 | 说明 |
|----|-----------|-----------|------|
| api | ~90% | ~80% | 高覆盖，含边界测试 |
| model | ~95% | ~85% | 高覆盖，状态机全路径 |
| spi | ~85% | ~75% | 中高覆盖，含 ServiceLoader |
| context | ~75% | ~65% | 中覆盖，反射清理路径较难测试 |
| exporter | ~85% | ~70% | 中高覆盖 |
| enums | ~100% | ~100% | 完全覆盖 |

#### 低覆盖风险区域

| 类 | 未覆盖路径 | 风险评估 | 改进建议 |
|----|-----------|----------|----------|
| ZeroLeakThreadLocalManager | 反射清理、死线程检测 | 中 | Mock Thread 状态 |
| SafeContextManager | 执行器拒绝策略、shutdown 竞态 | 低 | 压力测试覆盖 |
| TfiFlow | Provider 异常路径 | 低 | 注入故障 Provider |

### 2.3 关键白盒路径

#### 路径 1：TfiFlow 双路径选择

```
TfiFlow.stage(name)
├── enabled=false → return NullTaskContext.INSTANCE
├── getFlowProvider() != null
│   ├── provider.currentSession() == null → startSession("auto-session")
│   ├── provider.startTask(name) → TaskContextImpl
│   └── provider.startTask(name) == null → NullTaskContext
└── getFlowProvider() == null（传统路径）
    ├── ManagedThreadContext.current() == null → create("auto-session")
    ├── getCurrentSession() == null → startSession("auto-session")
    └── startTask(name) → TaskContextImpl
```

#### 路径 2：Session 状态转换

```
RUNNING ──complete()──→ COMPLETED
   │
   └──error()──→ ERROR

非法转换（抛 IllegalStateException）：
COMPLETED → complete() ✗
COMPLETED → error()    ✗
ERROR     → complete() ✗
ERROR     → error()    ✗
```

#### 路径 3：泄漏检测

```
detectAndCleanLeaks()
├── for each (threadId, context) in activeContexts:
│   ├── !isThreadAlive(threadId) → leaked (dead thread)
│   ├── contextAge > timeoutMillis → leaked (timeout)
│   └── alive && !timeout → safe
├── for each leaked:
│   ├── activeContexts.remove(threadId)
│   ├── context.close()
│   └── notifyLeakListeners(context)
```

---

## 三、黑盒测试

### 3.1 等价类划分

#### TfiFlow.startSession(name)

| 输入 | 等价类 | 预期结果 |
|------|--------|----------|
| `"订单处理"` | 正常字符串 | 返回非 null sessionId |
| `" 前后空格 "` | 含空格字符串 | trim 后正常处理 |
| `""` | 空字符串 | 返回 null |
| `null` | null 输入 | 返回 null |
| disabled 状态 | 功能禁用 | 返回 null |

#### TfiFlow.stage(name)

| 输入 | 等价类 | 预期结果 |
|------|--------|----------|
| `"验证"` | 正常字符串 | 返回 TaskContextImpl |
| `null` | null 输入 | 返回 NullTaskContext |
| `""` | 空字符串 | 返回 NullTaskContext |
| disabled | 功能禁用 | 返回 NullTaskContext |

### 3.2 边界值分析

| 测试项 | 边界条件 | 预期行为 |
|--------|----------|----------|
| 嵌套深度 | 1 层 / 10 层 / 100 层 | 正确构建树，无栈溢出 |
| 消息数量 | 0 / 1 / 10000 条 | 正确记录，内存可控 |
| 会话名长度 | 1 字符 / 1000 字符 | 正常处理 |
| 并发线程 | 1 / 4 / 100 线程 | 线程隔离，无竞态 |
| 快速开关 | 连续 enable/disable 1000 次 | 状态一致 |

### 3.3 状态转换测试

#### Session 状态机

| 初始状态 | 操作 | 预期结果 |
|----------|------|----------|
| RUNNING | complete() | → COMPLETED |
| RUNNING | error() | → ERROR |
| RUNNING | error("msg") | → ERROR + 错误消息 |
| COMPLETED | complete() | IllegalStateException |
| COMPLETED | error() | IllegalStateException |
| ERROR | complete() | IllegalStateException |

#### TaskNode 状态机

| 初始状态 | 操作 | 预期结果 |
|----------|------|----------|
| RUNNING | complete() | → COMPLETED |
| RUNNING | fail() | → FAILED |
| COMPLETED | complete() | IllegalStateException |
| FAILED | fail() | IllegalStateException |

---

## 四、功能测试

### 4.1 端到端测试场景

#### FT-001：完整订单流程

```java
TfiFlow.startSession("订单处理");
try (var stage1 = TfiFlow.stage("参数验证")) {
    stage1.message("验证通过");
}
try (var stage2 = TfiFlow.stage("库存检查")) {
    stage2.message("库存充足").debug("当前库存: 100");
}
try (var stage3 = TfiFlow.stage("支付处理")) {
    stage3.message("支付成功");
}
TfiFlow.exportToConsole();
TfiFlow.endSession();
```

**断言**：
- 会话状态为 COMPLETED
- 根任务下有 3 个子任务
- 每个子任务包含对应消息
- Console 输出包含 emoji 树结构

#### FT-002：异常处理流程

```java
TfiFlow.startSession("异常流程");
try (var stage = TfiFlow.stage("可能失败的操作")) {
    throw new RuntimeException("模拟异常");
} catch (Exception e) {
    TfiFlow.error("操作失败", e);
}
```

**断言**：
- Stage 自动标记为 FAILED
- 错误消息包含异常信息

#### FT-003：异步上下文传播

```java
ExecutorService pool = ContextPropagatingExecutor.wrap(
    Executors.newFixedThreadPool(2));
TfiFlow.startSession("异步测试");

Future<?> future = pool.submit(() -> {
    // 子线程应能访问父线程的 Session
    assertNotNull(TfiFlow.getCurrentSession());
});
future.get();
```

#### FT-004：禁用模式

```java
TfiFlow.disable();
String sessionId = TfiFlow.startSession("不应创建");
assertNull(sessionId);
TaskContext ctx = TfiFlow.stage("不应创建");
assertEquals(NullTaskContext.INSTANCE, ctx);
TfiFlow.enable();
```

#### FT-005：SPI 自定义 Provider

```java
FlowProvider custom = new CustomFlowProvider(); // priority=100
ProviderRegistry.register(FlowProvider.class, custom);
// 后续调用应走 custom Provider
TfiFlow.startSession("custom");
verify(custom).startSession("custom");
```

#### FT-006：多格式导出一致性

```java
TfiFlow.startSession("export-test");
try (var s = TfiFlow.stage("task")) { s.message("msg"); }
String json = TfiFlow.exportToJson();
Map<String, Object> map = TfiFlow.exportToMap();
// JSON 和 Map 应包含相同的结构化数据
assertTrue(json.contains("export-test"));
assertEquals("export-test", map.get("sessionName"));
```

### 4.2 功能测试矩阵

| 功能 | 正常 | 异常 | 边界 | 并发 | 禁用态 |
|------|------|------|------|------|--------|
| F1 会话管理 | ✅ | ✅ | ✅ | ✅ | ✅ |
| F2 任务管理 | ✅ | ✅ | ✅ | ✅ | ✅ |
| F3 消息记录 | ✅ | ✅ | ✅ | — | ✅ |
| F4 导出 | ✅ | ✅ | — | — | ✅ |
| F5 异步传播 | ✅ | ✅ | ✅ | ✅ | — |
| F6 SPI | ✅ | ✅ | ✅ | — | — |
| F7 全局控制 | ✅ | — | ✅ | ✅ | — |

---

## 五、性能测试

### 5.1 JMH 基准测试

#### 5.1.1 Benchmark 设计

| ID | 测试项 | Warm-up | Measurement | Threads |
|----|--------|---------|-------------|---------|
| BM-001 | TfiFlow.stage() 创建+关闭 | 5 iterations | 10 iterations | 1 |
| BM-002 | TfiFlow.stage() 并发 | 5 iterations | 10 iterations | 4 |
| BM-003 | TfiFlow.message() | 5 iterations | 10 iterations | 1 |
| BM-004 | TfiFlow.exportToJson() | 5 iterations | 10 iterations | 1 |
| BM-005 | TfiFlow.exportToConsole() | 5 iterations | 10 iterations | 1 |
| BM-006 | ManagedThreadContext 创建/关闭 | 5 iterations | 10 iterations | 1 |
| BM-007 | ContextSnapshot 创建/恢复 | 5 iterations | 10 iterations | 1 |
| BM-008 | ProviderRegistry.lookup() | 5 iterations | 10 iterations | 1 |
| BM-009 | 10 层嵌套 stage | 5 iterations | 10 iterations | 1 |
| BM-010 | 禁用状态 stage（no-op 基线） | 5 iterations | 10 iterations | 1 |

#### 5.1.2 基线结果（2026-02-15）

| Benchmark | 吞吐量 | 误差范围 |
|-----------|--------|----------|
| BM-010 禁用态 No-Op | **1,839,971,084** ops/s | ± 40,017,584 |
| BM-008 Registry Lookup | **185,332,750** ops/s | ± 537,209 |
| BM-006 Context 创建/关闭 | **874,565** ops/s | ± 9,621 |
| BM-003 Message | **799,623** ops/s | ± 3,518 |
| BM-005 Console Export | **647,704** ops/s | ± 2,516 |
| BM-007 Snapshot 创建/恢复 | **414,009** ops/s | ± 2,076 |
| BM-004 JSON Export | **351,369** ops/s | ± 2,151 |
| BM-009 10层嵌套 Stage | **256,410** ops/s | ± 12,808 |
| BM-002 Stage 并发 (4T) | **84,045** ops/s | ± 1,727 |
| BM-001 Stage 创建/关闭 | **22,630** ops/s | ± 1,256 |

#### 5.1.3 运行方式

```bash
# 运行全部 benchmark
mvn exec:java -Dtfi.perf.enabled=true \
    -Dexec.mainClass=com.syy.taskflowinsight.benchmark.BenchmarkRunner \
    -Dexec.classpathScope=test

# 运行指定 benchmark
mvn exec:java -Dtfi.perf.enabled=true \
    -Dexec.mainClass=com.syy.taskflowinsight.benchmark.BenchmarkRunner \
    -Dexec.args="bm001" -Dexec.classpathScope=test
```

### 5.2 压力测试设计

| 测试项 | 参数 | 持续时间 | 监测指标 |
|--------|------|----------|----------|
| 高频 stage 创建 | 100 线程 × 10000 次 | 10 分钟 | TPS、p99 延迟、内存 |
| 大量消息写入 | 10 线程 × 1000 条/任务 | 5 分钟 | 内存增长率、GC 频率 |
| 深层嵌套 | 50 层嵌套 × 1000 次 | 5 分钟 | 栈深度、OOM |
| 长时间运行 | 1 线程持续创建/销毁 | 1 小时 | 内存泄漏、线程数 |

### 5.3 泄漏测试

| 测试项 | 方法 | 预期 |
|--------|------|------|
| 未关闭 Stage | 创建但不 close | 泄漏检测器发现并清理 |
| 死线程上下文 | 线程终止后检查 | activeContexts 自动清理 |
| 长时间上下文 | 超过 timeout | 超时清理 |
| 反复创建线程 | 循环创建/销毁 | 无 ThreadLocal 泄漏 |

---

## 六、测试执行命令

```bash
# 全量测试
mvn test

# 单个测试类
mvn test -Dtest=TfiFlowTest

# 单个测试方法
mvn test -Dtest=TfiFlowTest#sessionLifecycle

# 覆盖率报告
mvn clean test jacoco:report
# 报告路径: target/site/jacoco/index.html

# 代码规范检查
mvn checkstyle:check

# 静态缺陷分析
mvn spotbugs:check

# 性能基准测试
mvn exec:java -Dtfi.perf.enabled=true \
    -Dexec.mainClass=com.syy.taskflowinsight.benchmark.BenchmarkRunner \
    -Dexec.classpathScope=test
```

---

## 七、后续改进方向

| 优先级 | 改进项 | 预期收益 |
|--------|--------|----------|
| P0 | 分支覆盖率提升至 75%+ | 覆盖更多边界条件 |
| P1 | ArchUnit 架构约束测试 | 防止架构退化 |
| P1 | 属性测试（jqwik） | 发现更多边界 bug |
| P2 | JMH 基准自动化（CI 集成） | 性能回归自动检测 |
| P2 | 变异测试（PITest） | 评估测试质量 |
| P3 | 混沌测试（故障注入） | 验证异常安全性 |

---

*本文档由测试专家王磊编写，基于对 tfi-flow-core 428 个测试用例的全面分析。*
