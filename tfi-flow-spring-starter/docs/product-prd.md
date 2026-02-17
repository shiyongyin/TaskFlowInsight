# tfi-flow-spring-starter 产品需求文档 (PRD)

> **版本**: v3.0.0 / v4.0.0-routing-refactor  
> **模块**: tfi-flow-spring-starter  
> **文档日期**: 2026-02-16  
> **专家角色**: 资深产品经理  
> **审核**: 资深项目经理

---

## 1. 产品背景

### 1.1 问题陈述

在企业级 Java 应用开发中，开发团队面临以下痛点：

1. **业务流程不透明**：复杂业务逻辑嵌套调用，难以直观理解执行链路
2. **排障成本高**：生产问题定位需要逐行翻阅日志，无法快速定位问题环节
3. **安全合规风险**：日志中可能泄露敏感数据（密码、Token、手机号等）
4. **集成成本高**：引入流程追踪工具通常需要大量代码改造
5. **ThreadLocal 泄漏**：在线程池场景下，上下文信息未及时清理导致内存泄漏

### 1.2 产品定位

`tfi-flow-spring-starter` 是一个 **Spring Boot 自动配置起步依赖**，为 Spring Boot 应用提供 **零配置、零侵入** 的业务流程追踪能力。

**一句话描述**：引入一个 Starter，加一个注解，业务流程自动可视化。

### 1.3 目标用户

| 用户角色 | 核心诉求 |
|---------|---------|
| **后端开发工程师** | 快速接入流程追踪，减少手动埋点工作 |
| **技术负责人/架构师** | 掌握系统业务流程全景，评估架构健康度 |
| **QA 工程师** | 通过流程树验证业务逻辑完整性 |
| **运维工程师** | 监控上下文生命周期，预防 ThreadLocal 泄漏 |
| **安全审计人员** | 确保日志脱敏合规 |

---

## 2. 产品目标与成功指标

### 2.1 产品目标

| 目标 | 描述 | 优先级 |
|------|------|--------|
| G1 | 零代码侵入集成 | P0 |
| G2 | 注解驱动的流程追踪 | P0 |
| G3 | 自动数据脱敏 | P0 |
| G4 | ThreadLocal 泄漏预防 | P1 |
| G5 | SpEL 动态条件与命名 | P1 |
| G6 | 采样率控制 | P2 |

### 2.2 成功指标（KPIs）

| 指标 | 目标值 | 度量方式 |
|------|--------|---------|
| 集成时间 | ≤ 5 分钟 | 从添加依赖到首次追踪输出 |
| 运行时开销 | ≤ 3% 额外延迟 | JMH 基准测试 |
| 内存开销 | ≤ 50KB/活跃上下文 | 堆内存分析 |
| 脱敏覆盖率 | 100% 已知敏感字段 | 安全审计 |
| ThreadLocal 泄漏率 | 0% | 长时间运行压测 |

---

## 3. 功能需求

### 3.1 功能概览（Feature Map）

```
tfi-flow-spring-starter
├── F1: 自动配置集成
│   ├── F1.1: Spring Boot 2.x / 3.x 双版本支持
│   ├── F1.2: IDE 配置自动补全
│   └── F1.3: 条件化组件加载
│
├── F2: 注解驱动流程追踪 (@TfiTask)
│   ├── F2.1: 方法级 Stage 创建
│   ├── F2.2: 参数/返回值/异常记录
│   ├── F2.3: SpEL 动态任务名
│   ├── F2.4: SpEL 条件追踪
│   └── F2.5: 概率采样
│
├── F3: 数据脱敏
│   ├── F3.1: 字段名敏感检测
│   ├── F3.2: 值内容敏感检测
│   ├── F3.3: 邮箱/电话/信用卡脱敏
│   └── F3.4: 多级脱敏策略
│
├── F4: 上下文生命周期管理
│   ├── F4.1: 上下文最大存活时间
│   ├── F4.2: 泄漏检测
│   └── F4.3: 自动清理
│
└── F5: SpEL 安全求值
    ├── F5.1: 表达式白名单验证
    ├── F5.2: 注入攻击防御
    └── F5.3: 表达式编译缓存
```

### 3.2 功能详细描述

#### F1: 自动配置集成

**用户故事**：作为后端开发，我希望只需添加 Maven 依赖即可启用 TFI 流程追踪，无需手动编写配置类。

**验收标准**：
- [x] 添加 `tfi-flow-spring-starter` 依赖后，Spring Boot 应用自动配置上下文管理
- [x] 支持 Spring Boot 2.7+ 和 3.x
- [x] IDE（IntelliJ IDEA / VS Code）能自动补全 `tfi.context.*` 和 `tfi.annotation.*` 配置

**配置项**：

```yaml
# application.yml 示例
tfi:
  annotation:
    enabled: true                          # 启用 @TfiTask 注解处理
  context:
    max-age-millis: 3600000                # 上下文最大存活 1 小时
    leak-detection-enabled: false          # 泄漏检测（生产建议开启）
    leak-detection-interval-millis: 60000  # 泄漏检测间隔
    cleanup-enabled: false                 # 自动清理（线程池场景建议开启）
    cleanup-interval-millis: 60000         # 清理间隔
```

---

#### F2: 注解驱动流程追踪

**用户故事**：作为后端开发，我希望通过 `@TfiTask` 注解标记业务方法，自动生成流程追踪记录。

**使用示例**：

```java
@TfiTask("处理订单")
public Order processOrder(String orderId, BigDecimal amount) {
    // 业务逻辑...
    return order;
}

// SpEL 动态命名
@TfiTask(value = "#{#methodName + '-' + #className}", 
         condition = "#{#className.contains('Service')}")
public void complexMethod() { ... }

// 采样控制
@TfiTask(value = "高频操作", samplingRate = 0.1)  // 仅追踪 10%
public void highFrequencyOp() { ... }
```

**功能矩阵**：

| 子功能 | 属性 | 说明 | 默认值 |
|--------|------|------|--------|
| 任务命名 | `value()` / `name()` | 支持字面量或 SpEL | 方法名 |
| 参数记录 | `logArgs()` | 记录方法入参（自动脱敏） | `true` |
| 返回值记录 | `logResult()` | 记录返回值（自动脱敏） | `true` |
| 异常记录 | `logException()` | 记录异常信息 | `true` |
| 条件追踪 | `condition()` | SpEL 条件表达式 | 空（始终追踪） |
| 采样率 | `samplingRate()` | 0.0~1.0 概率采样 | `1.0`（全量） |
| 深度追踪 | `deepTracking()` | 对象状态变更追踪 | `false` |

---

#### F3: 数据脱敏

**用户故事**：作为安全审计人员，我希望 TFI 追踪日志中不会泄露密码、Token 等敏感数据。

**脱敏规则**：

| 检测方式 | 匹配规则 | 脱敏效果 |
|---------|---------|---------|
| 字段名关键词 | 包含 password/token/secret 等 19 个关键词 | `p***d` |
| 邮箱模式 | `xxx@domain.com` | `x***@domain.com` |
| 电话模式 | `123-456-7890` | `123-***-7890` |
| 信用卡模式 | `1234-5678-9012-3456` | `****-****-****-3456` |

**脱敏级别**：
- **STRONG**：仅保留首尾字符（默认，用于敏感字段名匹配）
- **MEDIUM**：保留前后各 1/4 字符
- **WEAK**：保留前后各 3 字符

---

#### F4: 上下文生命周期管理

**用户故事**：作为运维工程师，我希望 TFI 能自动管理 ThreadLocal 上下文的生命周期，防止在线程池环境下发生内存泄漏。

**功能说明**：

| 功能 | 说明 | 配置 |
|------|------|------|
| 最大存活时间 | 超过阈值的上下文自动标记为过期 | `tfi.context.max-age-millis` |
| 泄漏检测 | 定期扫描未释放的上下文并输出告警日志 | `tfi.context.leak-detection-enabled` |
| 自动清理 | 定期清理过期的 ThreadLocal 上下文 | `tfi.context.cleanup-enabled` |

---

#### F5: SpEL 安全求值

**用户故事**：作为架构师，我希望 SpEL 表达式求值具备安全防护机制，防止恶意表达式注入。

**安全机制**：

| 防御层 | 策略 | 说明 |
|--------|------|------|
| 第 1 层 | 长度限制 | 表达式 ≤ 1000 字符 |
| 第 2 层 | 关键词黑名单 | 禁止 `class`, `runtime`, `exec` 等 16 个危险模式 |
| 第 3 层 | 嵌套深度限制 | 括号嵌套 ≤ 10 层 |
| 第 4 层 | 上下文隔离 | `SimpleEvaluationContext` 禁用反射和类型引用 |

---

## 4. 非功能需求

### 4.1 性能需求

| 指标 | 目标 | 说明 |
|------|------|------|
| AOP 拦截延迟 | ≤ 0.5ms（P99） | 不含业务方法执行时间 |
| SpEL 首次求值 | ≤ 2ms | 含表达式编译 |
| SpEL 缓存命中 | ≤ 0.1ms | 编译后缓存 |
| 脱敏处理 | ≤ 0.3ms/字段 | 含正则匹配 |
| 内存占用 | ≤ 5MB | Starter 自身常驻内存 |

### 4.2 可用性需求

| 指标 | 目标 |
|------|------|
| 启动影响 | ≤ 100ms 额外启动时间 |
| 故障隔离 | TFI 异常不影响业务流程 |
| 优雅降级 | `tfi.annotation.enabled=false` 时完全无开销 |

### 4.3 安全需求

| 指标 | 目标 |
|------|------|
| SpEL 注入 | 100% 防御（四层安全机制） |
| 数据泄露 | 0 敏感字段泄露 |
| 依赖安全 | 无已知 CVE 漏洞依赖 |

### 4.4 兼容性需求

| 环境 | 最低版本 |
|------|---------|
| Java | 21+ |
| Spring Boot | 2.7+ / 3.x |
| Spring Framework | 5.3+ / 6.x |
| Build Tool | Maven 3.6+ |

---

## 5. 用户旅程（User Journey）

### 5.1 开发者首次集成

```
Step 1: 添加 Maven 依赖
          │
          ▼
Step 2: application.yml 添加 tfi.annotation.enabled=true
          │
          ▼
Step 3: 在业务方法上添加 @TfiTask("业务名称")
          │
          ▼
Step 4: 启动应用 → 自动输出流程追踪
          │
          ▼
Step 5: 查看控制台/日志 → 看到层级化的业务流程树
```

**预期集成时间**：≤ 5 分钟

### 5.2 生产环境运维

```
Step 1: 配置 tfi.context.leak-detection-enabled=true
          │
          ▼
Step 2: 配置 tfi.context.cleanup-enabled=true
          │
          ▼
Step 3: 监控日志中的泄漏告警
          │
          ▼
Step 4: 根据告警调整 max-age-millis 和 cleanup-interval-millis
```

### 5.3 安全审计流程

```
Step 1: 审查 application.yml 中的 TFI 配置
          │
          ▼
Step 2: 验证 @TfiTask 标注的方法不直接输出敏感字段
          │
          ▼
Step 3: 检查 UnifiedDataMasker 的脱敏关键词覆盖
          │
          ▼
Step 4: 验证 SpEL 表达式安全策略生效
```

---

## 6. 竞品分析

### 6.1 竞品对比

| 特性 | tfi-flow-spring-starter | Spring Sleuth / Micrometer Tracing | Jaeger / Zipkin | 手动日志埋点 |
|------|------------------------|------------------------------------|-----------------|------------|
| **集成方式** | Starter + 注解 | Starter + 自动装配 | Agent / Starter | 手动编写 |
| **追踪粒度** | 业务方法级 | HTTP/RPC 调用级 | 分布式 Span 级 | 自定义 |
| **业务语义** | ✅ 支持（任务名、消息） | ❌ 仅技术指标 | ❌ 仅技术指标 | ✅ 自定义 |
| **数据脱敏** | ✅ 内置 | ❌ 无 | ❌ 无 | 需自行实现 |
| **SpEL 条件** | ✅ 安全求值 | ❌ 不支持 | ❌ 不支持 | N/A |
| **采样控制** | ✅ 方法级 | ✅ 全局级 | ✅ 全局级 | N/A |
| **ThreadLocal 管理** | ✅ 泄漏检测+自动清理 | 部分 | ❌ | 需自行实现 |
| **侵入性** | 极低（注解） | 低 | 低-中 | 高 |

### 6.2 差异化优势

1. **业务语义追踪**：不同于技术级 Tracing，TFI 追踪的是业务语义（"处理订单"而非 "HTTP POST /api/orders"）
2. **内置安全**：SpEL 安全求值 + 自动数据脱敏，开箱即用
3. **ThreadLocal 泄漏防护**：在框架层面解决常见的 ThreadLocal 泄漏问题
4. **轻量级**：仅关注 Flow 追踪，不引入分布式追踪的复杂性

---

## 7. 约束与假设

### 7.1 约束

| 约束 | 说明 |
|------|------|
| 仅支持 Spring 环境 | `@Component` 扫描和 `@ConditionalOnProperty` 依赖 Spring |
| 同步方法追踪 | 当前 AOP 切面不支持 `CompletableFuture` / `Mono` / `Flux` 返回值 |
| 深度追踪依赖 `tfi-compare` | `@TfiTask(deepTracking=true)` 需要额外引入 `tfi-compare` 模块 |

### 7.2 假设

| 假设 | 说明 |
|------|------|
| 应用使用 Spring Boot | Starter 机制依赖 Spring Boot 自动配置 |
| AOP 代理模式为默认 | 使用 Spring AOP（JDK/CGLIB 代理），非 AspectJ Load-Time Weaving |
| 方法参数名保留 | 需要 `-parameters` 编译选项或 Lombok |

---

## 8. 未来规划（Roadmap）

### 8.1 短期（v4.1.0）

| 功能 | 优先级 | 描述 |
|------|--------|------|
| 可配置脱敏关键词 | P1 | 支持通过 `application.yml` 自定义敏感关键词列表 |
| SpEL 上下文增强 | P2 | 将方法参数暴露到 SpEL 上下文（安全审核后） |
| 表达式缓存限制 | P2 | 防止缓存无限增长 |

### 8.2 中期（v5.0.0）

| 功能 | 优先级 | 描述 |
|------|--------|------|
| 响应式支持 | P1 | WebFlux / R2DBC 环境下的 Context 传播 |
| 异步方法追踪 | P1 | 支持 `@Async`, `CompletableFuture` 返回值 |
| 脱敏策略扩展 | P2 | SPI 扩展点，允许用户自定义脱敏规则 |

### 8.3 长期（v6.0.0）

| 功能 | 优先级 | 描述 |
|------|--------|------|
| GraalVM Native Image | P2 | AOT 编译支持 |
| 分布式追踪桥接 | P3 | 与 Micrometer Tracing / OpenTelemetry 打通 |
| 低代码流程编排 | P3 | 可视化配置追踪规则，无需代码修改 |

---

## 9. 风险评估

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| AOP 代理不生效（自调用） | 高 | 中 | 文档提示 + 示例代码 |
| SpEL 性能瓶颈 | 中 | 低 | 表达式缓存 + 采样控制 |
| 脱敏遗漏 | 高 | 低 | 持续扩充关键词 + 安全审计 |
| ThreadLocal 泄漏误报 | 低 | 中 | 可调节检测阈值 |
| 版本升级不兼容 | 中 | 低 | japicmp API 兼容性检查 |

---

## 10. 验收标准

### 10.1 功能验收

- [ ] Maven 依赖引入后，Spring Boot 应用正常启动
- [ ] `@TfiTask` 注解的方法被正确拦截，生成 Flow Stage
- [ ] `logArgs=true` 时，方法参数被脱敏后记录
- [ ] `logResult=true` 时，返回值被脱敏后记录
- [ ] `logException=true` 时，异常被正确记录并传播
- [ ] `condition` SpEL 表达式为 `false` 时跳过追踪
- [ ] `samplingRate=0.5` 时约 50% 的调用被追踪
- [ ] 密码/Token/邮箱/电话/信用卡均被正确脱敏
- [ ] 泄漏检测和自动清理按配置正常工作

### 10.2 非功能验收

- [ ] 追踪开销 ≤ 0.5ms/调用
- [ ] TFI 异常不影响业务方法返回值
- [ ] `tfi.annotation.enabled=false` 时无 AOP 拦截开销
- [ ] Spring Boot 2.7 和 3.x 均能正常工作

---

*文档编写：资深产品经理*  
*审核确认：资深项目经理*
