# tfi-flow-core 产品需求文档（PRD）

> **负责人**: 张琳（资深产品经理）| **版本**: v4.0 | **评审日期**: 2026-02-15

---

## 一、产品愿景

### 1.1 一句话定位

> **让业务流程"自己说话"**——零侵入、零依赖、零泄漏的 Java 执行流可视化内核。

### 1.2 核心价值主张

| 价值 | 说明 |
|------|------|
| **零侵入** | 5 行代码接入，不改变原有业务逻辑 |
| **零依赖** | 运行时仅依赖 slf4j-api，无框架锁定 |
| **零泄漏** | 四道防线保证 ThreadLocal 不泄漏 |
| **零异常** | 门面层永不向业务代码抛出异常 |
| **可扩展** | SPI 机制支持自定义 Provider |

### 1.3 解决的核心问题

| 场景 | 痛点 | TFI 方案 |
|------|------|----------|
| 复杂业务流程 | 执行流程不可见，出问题后难定位 | 自动生成执行树，清晰展示调用链 |
| 异步处理 | 跨线程上下文丢失 | ContextSnapshot + 自动传播 |
| 性能诊断 | 不知道哪个步骤慢 | 纳秒级耗时统计 + 树状展示 |
| 日志混乱 | 多线程日志交织 | 结构化消息 + Session 隔离 |

---

## 二、目标用户

### 2.1 用户画像

| 用户类型 | 典型角色 | 核心需求 |
|----------|----------|----------|
| **库集成者** | 框架/中间件开发者 | 嵌入自有框架，提供流程可视化能力 |
| **应用开发者** | 业务系统后端工程师 | 快速追踪业务流程，定位性能瓶颈 |
| **运维工程师** | SRE / DevOps | 生产环境流程可观测，故障快速定位 |
| **测试工程师** | QA / 自动化测试 | 验证业务流程完整性，断言执行路径 |

### 2.2 使用场景优先级

| 场景 | 优先级 | 频率 |
|------|--------|------|
| 开发阶段：调试业务流程 | P0 | 高 |
| 测试阶段：验证执行路径 | P0 | 高 |
| 生产环境：按需开启追踪 | P1 | 中 |
| 性能分析：识别慢操作 | P1 | 中 |
| 故障诊断：回溯执行流 | P2 | 低 |

---

## 三、功能规格

### F1：会话管理

| 功能项 | 说明 | API |
|--------|------|-----|
| 创建会话 | 开始一个命名会话，返回会话 ID | `TfiFlow.startSession(name)` |
| 结束会话 | 正常/异常结束，状态转为 COMPLETED/ERROR | `TfiFlow.endSession()` |
| 查询会话 | 获取当前线程的活跃会话 | `TfiFlow.getCurrentSession()` |
| 自动会话 | 调用 `stage()` 时自动创建会话 | 内部逻辑 |
| 状态机 | RUNNING → COMPLETED / ERROR | `Session.complete()` / `error()` |

**验收标准**：
- [ ] 会话 ID 全局唯一（UUID）
- [ ] 同一线程同时只有一个活跃会话
- [ ] 禁用状态下 `startSession()` 返回 null
- [ ] 状态转换满足 FSM 约束

### F2：任务 / 阶段管理

| 功能项 | 说明 | API |
|--------|------|-----|
| 创建 Stage | AutoCloseable 任务块 | `TfiFlow.stage(name)` |
| 函数式 Stage | 执行函数并自动管理生命周期 | `TfiFlow.stage(name, func)` |
| 启停式任务 | 手动 start/stop | `TfiFlow.start(name)` / `stop()` |
| Runnable 包装 | 在任务中执行 Runnable | `TfiFlow.run(name, runnable)` |
| Callable 包装 | 在任务中执行 Callable | `TfiFlow.call(name, callable)` |
| 子任务 | 创建嵌套子任务 | `TaskContext.subtask(name)` |
| 自动耗时 | 纳秒级自动计时 | `TaskNode.getSelfDurationNanos()` |

**验收标准**：
- [ ] try-with-resources 自动关闭
- [ ] 嵌套 stage 形成正确的父子关系
- [ ] 异常时 stage 自动标记 FAILED
- [ ] 禁用时返回 NullTaskContext（no-op）

### F3：消息记录

| 功能项 | 说明 | API |
|--------|------|-----|
| 类型消息 | 指定 MessageType | `TfiFlow.message(content, type)` |
| 标签消息 | 自定义显示标签 | `TfiFlow.message(content, label)` |
| 错误消息 | 记录异常信息 | `TfiFlow.error(content, throwable)` |
| 链式消息 | TaskContext 链式调用 | `stage.message().debug().warn()` |

**消息类型**：

| MessageType | 显示名 | 用途 |
|-------------|--------|------|
| PROCESS | 📋 流程记录 | 业务流程步骤 |
| METRIC | 📊 指标数据 | 性能/业务指标 |
| CHANGE | 🔄 变更记录 | 数据变更通知 |
| ALERT | ⚠️ 异常提示 | 告警/错误信息 |

### F4：导出功能

| 格式 | API | 输出样例 |
|------|-----|----------|
| Console 树 | `exportToConsole()` | emoji 树状文本（📋/🔧/💬 + ├──/└──） |
| JSON | `exportToJson()` | 结构化 JSON 字符串 |
| Map | `exportToMap()` | `Map<String, Object>` 嵌套结构 |

**Console 输出样例**：
```
📋 订单处理 [COMPLETED] (150ms)
├── 🔧 参数验证 [COMPLETED] (2ms)
│   └── 💬 [📋流程记录] 验证通过
├── 🔧 库存检查 [COMPLETED] (45ms)
│   ├── 💬 [📋流程记录] 库存充足
│   └── 💬 [📊指标数据] 当前库存: 100
└── 🔧 支付处理 [COMPLETED] (103ms)
    ├── 💬 [📋流程记录] 支付成功
    └── 💬 [🔄变更记录] 余额扣减: ¥299
```

### F5：异步上下文传播

| 功能项 | 说明 | API |
|--------|------|-----|
| 快照创建 | 捕获当前线程上下文 | `context.createSnapshot()` |
| 快照恢复 | 在目标线程恢复上下文 | `snapshot.restore()` |
| 装饰执行器 | 自动传播上下文的线程池 | `ContextPropagatingExecutor.wrap()` |
| TFI 感知执行器 | 内置上下文传播线程池 | `TFIAwareExecutor.newFixedThreadPool()` |
| 异步执行 | 管理器内置异步 | `SafeContextManager.executeAsync()` |

### F6：SPI 扩展

| 扩展点 | 接口 | 默认实现 |
|--------|------|----------|
| 流程提供者 | `FlowProvider` | `DefaultFlowProvider`（priority=0） |
| 导出提供者 | `ExportProvider` | `DefaultExportProvider`（priority=-1000） |

**发现机制**：
1. 手动注册：`ProviderRegistry.register(type, instance)`
2. ServiceLoader：`META-INF/services/` 自动发现
3. 优先级仲裁：高 priority 值优先
4. 白名单过滤：`setAllowedProviders()`

### F7：全局控制

| 功能项 | 说明 | API |
|--------|------|-----|
| 全局开关 | 启用/禁用所有功能 | `enable()` / `disable()` |
| 状态查询 | 检查是否启用 | `isEnabled()` |
| 上下文清理 | 清理当前线程上下文 | `clear()` |
| Provider 注册 | 注册自定义 Provider | `registerFlowProvider()` |

---

## 四、非功能需求

### 4.1 性能要求

| 指标 | 要求 | 当前值 |
|------|------|--------|
| 禁用态开销 | < 5ns/op | ~0.5ns/op (1.84B ops/s) |
| Stage 创建/关闭 | < 100μs/op | ~43μs/op (23K ops/s) |
| 消息记录 | < 10μs/op | ~1.25μs/op (800K ops/s) |
| JSON 导出 | < 10μs/op | ~2.85μs/op (351K ops/s) |
| Registry 查找 | < 100ns/op | ~5.4ns/op (185M ops/s) |

### 4.2 可靠性要求

| 要求 | 实现方式 |
|------|----------|
| 不影响业务 | 门面层 try-catch(Throwable) |
| 不泄漏资源 | 四道防线（AutoCloseable → 泄漏检测 → 嵌套跟踪 → Shutdown Hook） |
| 不阻塞业务 | 异步任务使用 CallerRunsPolicy |
| 正常降级 | 禁用时全部 no-op |

### 4.3 兼容性要求

| 要求 | 说明 |
|------|------|
| Java 版本 | Java 21+（使用 `threadId()` API） |
| 框架无关 | 不依赖任何框架，可嵌入任意 Java 应用 |
| 日志框架 | 通过 SLF4J 桥接，用户选择具体实现 |

### 4.4 安全性要求

| 要求 | 实现方式 |
|------|----------|
| Provider 白名单 | `ProviderRegistry.setAllowedProviders()` |
| 无敏感信息 | Message 内容由用户控制，框架不收集系统信息 |
| 线程隔离 | ThreadLocal 隔离不同线程的上下文 |

---

## 五、用户故事

### US-001：业务流程追踪

> 作为一名**应用开发者**，我希望用最少的代码记录业务执行流程，以便在开发和调试时快速理解代码执行路径。

**验收标准**：
```java
// 5 行代码完成基本使用
TfiFlow.startSession("订单处理");
try (var stage = TfiFlow.stage("参数验证")) {
    stage.message("验证通过");
}
TfiFlow.exportToConsole();
TfiFlow.endSession();
```
- [x] 输出树状结构包含任务名、状态、耗时
- [x] 支持 try-with-resources 自动关闭
- [x] 嵌套 stage 形成层级结构

### US-002：异步流程串联

> 作为一名**应用开发者**，我希望在异步线程池中保持追踪上下文，以便查看完整的跨线程执行流。

**验收标准**：
```java
ExecutorService executor = ContextPropagatingExecutor.wrap(
    Executors.newFixedThreadPool(4));
// 子线程自动继承父线程的 Session 上下文
```
- [x] 快照创建和恢复正确传播上下文
- [x] 子线程执行完毕后自动清理
- [x] 多线程并发安全

### US-003：SPI 自定义扩展

> 作为一名**库集成者**，我希望通过 SPI 替换默认的流程实现，以便集成到自有框架中。

**验收标准**：
```java
// 方式一：ServiceLoader 自动发现
// META-INF/services/com.syy.taskflowinsight.spi.FlowProvider
// 方式二：手动注册
ProviderRegistry.register(FlowProvider.class, myProvider);
```
- [x] ServiceLoader 自动加载
- [x] 优先级仲裁正确（高 priority 覆盖低 priority）
- [x] 白名单过滤有效

### US-004：生产环境按需开关

> 作为一名**运维工程师**，我希望能在不重启服务的情况下开关追踪功能，以便在需要诊断时开启，平时关闭减少开销。

**验收标准**：
- [x] `TfiFlow.disable()` 后所有操作为 no-op
- [x] `TfiFlow.enable()` 后立即恢复功能
- [x] 禁用态开销 < 5ns/op
- [x] 开关切换线程安全

### US-005：多格式导出

> 作为一名**应用开发者**，我希望将执行流导出为不同格式，以便集成到日志系统、监控平台或测试断言中。

**验收标准**：
- [x] Console：emoji 树状输出，人类可读
- [x] JSON：结构化数据，可解析
- [x] Map：程序化处理，测试断言

### US-006：零泄漏保证

> 作为一名**库集成者**，我希望在长时间运行的服务中使用时不会出现内存泄漏，即使偶尔忘记关闭上下文。

**验收标准**：
- [x] 泄漏检测可配置开启
- [x] 死线程上下文自动清理
- [x] 超时上下文自动清理
- [x] Shutdown Hook 兜底清理

---

## 六、版本路线图

| 版本 | 时间 | 核心交付 |
|------|------|----------|
| **v3.0.0** (当前) | 2025-Q4 | 纯 Java 内核发布，SPI 架构，四道防线 |
| **v3.1.0** | 2026-Q1 | ExportProvider 集成，HTML 导出，自定义渲染模板 |
| **v4.0.0** | 2026-Q2 | 多 Provider 路由，条件激活，@TfiTask AOP |
| **v4.1.0** | 2026-Q3 | OpenTelemetry 集成，Trace ID 关联 |
| **v5.0.0** | 2026-Q4 | 虚拟线程原生支持，Scoped Value 替换 ThreadLocal |

---

## 七、竞品对比

| 特性 | tfi-flow-core | Spring Sleuth | OpenTelemetry Java |
|------|---------------|---------------|-------------------|
| 框架依赖 | 无 | Spring Boot | 无（但重量级） |
| 运行时依赖 | 1（slf4j） | 20+ | 10+ |
| 执行树可视化 | 原生支持 | 需第三方 | 需第三方 |
| 接入成本 | 5 行代码 | Spring 配置 | 配置 + Agent |
| 禁用态开销 | < 1ns | N/A | ~10ns |
| 内存泄漏防护 | 四道防线 | 依赖 Spring 生命周期 | ThreadLocal 手动管理 |
| 体积 | ~50KB JAR | ~500KB+ | ~2MB+ |

---

*本文档由产品经理张琳编写，基于对 tfi-flow-core 用户价值和市场定位的深入分析。*
