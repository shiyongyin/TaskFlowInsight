# tfi-flow-spring-starter 测试方案文档

> **版本**: v3.0.0 / v4.0.0-routing-refactor  
> **模块**: tfi-flow-spring-starter  
> **文档日期**: 2026-02-16  
> **专家角色**: 资深测试专家  
> **审核**: 资深项目经理

---

## 1. 测试策略概述

### 1.1 测试目标

确保 `tfi-flow-spring-starter` 模块的 **功能正确性、安全可靠性、性能达标** 和 **集成稳定性**，具体包括：

1. AOP 切面（`@TfiTask`）正确拦截并生成 Flow Stage
2. SpEL 安全求值器阻断所有注入攻击
3. 数据脱敏覆盖所有敏感场景
4. 自动配置在不同 Spring Boot 版本下正常工作
5. 运行时开销在可接受范围内

### 1.2 测试层次

```
┌───────────────────────────────────────────────┐
│           E2E 集成测试 (端到端)                  │
│     验证 Starter 在真实 Spring Boot 应用中工作    │
├───────────────────────────────────────────────┤
│           集成测试 (Spring Context)               │
│     验证组件间协作、自动配置、条件加载             │
├───────────────────────────────────────────────┤
│           单元测试 (White-box)                    │
│     验证每个类的每个方法的逻辑正确性              │
├───────────────────────────────────────────────┤
│           性能测试 (JMH / 压测)                   │
│     验证 AOP 拦截、SpEL 求值、脱敏的性能          │
└───────────────────────────────────────────────┘
```

### 1.3 测试覆盖率目标

| 指标 | 目标 |
|------|------|
| 行覆盖率（Line Coverage） | ≥ 85% |
| 分支覆盖率（Branch Coverage） | ≥ 80% |
| 方法覆盖率（Method Coverage） | ≥ 90% |
| 变异测试分数（Mutation Score） | ≥ 70% |

---

## 2. 白盒测试方案

### 2.1 TfiAnnotationAspect 白盒测试

#### 2.1.1 around() 方法

| 测试编号 | 测试场景 | 输入条件 | 预期输出 | 覆盖路径 |
|---------|---------|---------|---------|---------|
| WB-ASP-001 | 正常执行完整流程 | `samplingRate=1.0`, 无 condition, 有 taskName | 创建 Stage → 执行方法 → 记录返回值 → Stage 关闭 | 主路径 |
| WB-ASP-002 | 采样率为 0（跳过） | `samplingRate=0.0` | 直接执行方法，不创建 Stage | 短路路径 |
| WB-ASP-003 | 采样率为 0.5 | `samplingRate=0.5` | 约 50% 调用创建 Stage | 概率路径 |
| WB-ASP-004 | 条件为 false（跳过） | `condition="false"` | 直接执行方法，不创建 Stage | 条件短路 |
| WB-ASP-005 | 条件求值异常 | `condition="invalid_expr"` | 不追踪（默认 false） | 异常处理路径 |
| WB-ASP-006 | 目标方法抛出异常 | 方法抛出 `RuntimeException` | Stage 标记 fail → 记录异常 → 重新抛出 | 异常路径 |
| WB-ASP-007 | logArgs=true | `logArgs=true` | 每个参数通过 DataMasker 脱敏后记录 | 参数记录路径 |
| WB-ASP-008 | logArgs=false | `logArgs=false` | 不记录参数 | 跳过记录 |
| WB-ASP-009 | logResult=true 且返回 null | `logResult=true`, 方法返回 null | 不记录返回值 | null 保护 |
| WB-ASP-010 | logException=false | `logException=false`, 方法异常 | Stage 标记 fail，但不记录异常消息 | 异常不记录 |
| WB-ASP-011 | deepTracking=true | `deepTracking=true` | 输出 debug 日志，不执行深度追踪 | 委托路径 |
| WB-ASP-012 | taskName 为空 | `value=""`, `name=""` | 使用方法签名名称 | 默认名称路径 |
| WB-ASP-013 | taskName 包含 SpEL | `value="#{#methodName}"` | SpEL 解析后的任务名 | SpEL 路径 |
| WB-ASP-014 | SpEL 解析失败 | `value="#{invalid}"` | 回退到原始字符串 | SpEL 异常路径 |

#### 2.1.2 shouldSample() 方法

| 测试编号 | 输入 | 预期 | 说明 |
|---------|------|------|------|
| WB-ASP-020 | `samplingRate = 0.0` | `false` | 边界值：不采样 |
| WB-ASP-021 | `samplingRate = -1.0` | `false` | 边界值：负数 |
| WB-ASP-022 | `samplingRate = 1.0` | `true` | 边界值：全量采样 |
| WB-ASP-023 | `samplingRate = 1.5` | `true` | 边界值：超过 1 |
| WB-ASP-024 | `samplingRate = 0.5` | ~50% true | 统计验证（N=10000） |

#### 2.1.3 buildContext() 方法

| 测试编号 | 输入 | 预期 |
|---------|------|------|
| WB-ASP-030 | `ProceedingJoinPoint` mock | Map 包含 `methodName` 和 `className` |
| WB-ASP-031 | 方法名含特殊字符 | 正确获取方法名 |

#### 2.1.4 resolveTaskName() 方法

| 测试编号 | 输入 | 预期 |
|---------|------|------|
| WB-ASP-040 | `value="test"`, `name=""` | `"test"` |
| WB-ASP-041 | `value=""`, `name="test"` | `"test"` |
| WB-ASP-042 | `value=""`, `name=""` | 方法签名名称 |
| WB-ASP-043 | `value="${xxx}"` | SpEL 求值结果 |
| WB-ASP-044 | `value="#{xxx}"` | SpEL 求值结果 |
| WB-ASP-045 | SpEL 求值返回空串 | 方法签名名称（兜底） |

---

### 2.2 SafeSpELEvaluator 白盒测试

#### 2.2.1 validateExpression() 方法

| 测试编号 | 输入 | 预期 | 说明 |
|---------|------|------|------|
| WB-SPEL-001 | 长度 1001 的表达式 | 抛出 `IllegalArgumentException` | 长度限制 |
| WB-SPEL-002 | 长度 1000 的表达式 | 通过验证 | 边界值 |
| WB-SPEL-003 | 包含 `"class"` | 抛出 `SecurityException` | 黑名单 |
| WB-SPEL-004 | 包含 `"getClass"` | 抛出 `SecurityException` | 黑名单 |
| WB-SPEL-005 | 包含 `"runtime"` | 抛出 `SecurityException` | 黑名单 |
| WB-SPEL-006 | 包含 `"exec"` | 抛出 `SecurityException` | 黑名单 |
| WB-SPEL-007 | 包含 `"reflect"` | 抛出 `SecurityException` | 黑名单 |
| WB-SPEL-008 | 包含 `"unsafe"` | 抛出 `SecurityException` | 黑名单 |
| WB-SPEL-009 | 包含 `"classloader"` | 抛出 `SecurityException` | 黑名单 |
| WB-SPEL-010 | 嵌套 11 层括号 | 抛出 `SecurityException` | 嵌套限制 |
| WB-SPEL-011 | 嵌套 10 层括号 | 通过验证 | 边界值 |
| WB-SPEL-012 | 正常表达式 `"#methodName"` | 通过验证 | 正常路径 |
| WB-SPEL-013 | 空字符串 | 由外层 hasText 短路 | 空值保护 |

#### 2.2.2 evaluateExpression() 方法

| 测试编号 | 输入 | 预期 |
|---------|------|------|
| WB-SPEL-020 | 空表达式 | 返回 null |
| WB-SPEL-021 | 合法表达式 + Map 根对象 | 正确求值 |
| WB-SPEL-022 | 合法表达式 + POJO 根对象 | 正确求值 |
| WB-SPEL-023 | 非法表达式（语法错误） | 返回 null + warn 日志 |
| WB-SPEL-024 | 类型不匹配 | 返回 null + warn 日志 |

#### 2.2.3 createEvaluationContext() 方法

| 测试编号 | 输入 | 预期 |
|---------|------|------|
| WB-SPEL-030 | Map 对象 | 返回 `StandardEvaluationContext`，TypeLocator 禁用 |
| WB-SPEL-031 | 非 Map 对象 | 返回 `SimpleEvaluationContext` |
| WB-SPEL-032 | Map 中 TypeLocator 调用 | 抛出 `SecurityException` |

#### 2.2.4 缓存测试

| 测试编号 | 场景 | 预期 |
|---------|------|------|
| WB-SPEL-040 | 同一表达式求值两次 | 第二次命中缓存 |
| WB-SPEL-041 | `clearCache()` 后求值 | 重新编译 |
| WB-SPEL-042 | `getCacheStats()` | 返回正确统计 |

---

### 2.3 UnifiedDataMasker 白盒测试

#### 2.3.1 maskValue() 方法

| 测试编号 | fieldName | value | 预期 |
|---------|-----------|-------|------|
| WB-MASK-001 | `"password"` | `"abc123"` | `"a***3"` |
| WB-MASK-002 | `"userPassword"` | `"secret"` | `"s***t"` |
| WB-MASK-003 | `"apiKey"` | `"key123"` | `"k***3"` |
| WB-MASK-004 | `"token"` | `"tk"` | `"***"` (长度≤2) |
| WB-MASK-005 | `"name"` | `"user@email.com"` | `"u***@email.com"` |
| WB-MASK-006 | `"note"` | `"Call 123-456-7890"` | `"Call 123-***-7890"` |
| WB-MASK-007 | `"note"` | `"Card: 1234-5678-9012-3456"` | `"Card: ****-****-****-3456"` |
| WB-MASK-008 | `"name"` | `"John"` | `"John"`（非敏感） |
| WB-MASK-009 | `null` | `"value"` | `"value"`（null 字段名） |
| WB-MASK-010 | `"field"` | `null` | `null` |
| WB-MASK-011 | `""` | `"value"` | `"value"`（空字段名） |

#### 2.3.2 敏感关键词全覆盖测试

| 测试编号 | 关键词 | 说明 |
|---------|--------|------|
| WB-MASK-020 | `password` | 密码 |
| WB-MASK-021 | `token` | 令牌 |
| WB-MASK-022 | `secret` | 密钥 |
| WB-MASK-023 | `key` | 密钥 |
| WB-MASK-024 | `credential` | 凭证 |
| WB-MASK-025 | `apikey` | API 密钥 |
| WB-MASK-026 | `accesstoken` | 访问令牌 |
| WB-MASK-027 | `refreshtoken` | 刷新令牌 |
| WB-MASK-028 | `privatekey` | 私钥 |
| WB-MASK-029 | `oauth` | OAuth |
| WB-MASK-030 | `jwt` | JWT |
| WB-MASK-031 | `session` | 会话 |
| WB-MASK-032 | `cookie` | Cookie |
| WB-MASK-033 | `auth` | 认证 |
| WB-MASK-034 | `pin` | PIN |
| WB-MASK-035 | `cvv` | CVV |
| WB-MASK-036 | `ssn` | 社保号 |
| WB-MASK-037 | `passport` | 护照 |
| WB-MASK-038 | `license` | 证照 |

#### 2.3.3 脱敏策略边界测试

| 测试编号 | 策略 | 输入长度 | 预期 |
|---------|------|---------|------|
| WB-MASK-050 | STRONG | 1 字符 | `"***"` |
| WB-MASK-051 | STRONG | 2 字符 | `"***"` |
| WB-MASK-052 | STRONG | 3 字符 | `"a***c"` |
| WB-MASK-053 | MEDIUM | 4 字符 | `"****"` |
| WB-MASK-054 | MEDIUM | 8 字符 | `"ab****gh"` |
| WB-MASK-055 | WEAK | 6 字符 | `"a***f"` |
| WB-MASK-056 | WEAK | 7 字符 | `"abc***efg"` |
| WB-MASK-057 | 空字符串 | 0 | 返回空串 |

---

### 2.4 ContextMonitoringAutoConfiguration 白盒测试

| 测试编号 | 场景 | 预期 |
|---------|------|------|
| WB-CFG-001 | 默认配置 | SafeContextManager 配置 maxAge=3600000 |
| WB-CFG-002 | 自定义 maxAgeMillis=7200000 | SafeContextManager maxAge=7200000 |
| WB-CFG-003 | maxAgeMillis=0（非法） | 回退默认值 3600000 |
| WB-CFG-004 | maxAgeMillis=-1（非法） | 回退默认值 3600000 |
| WB-CFG-005 | leakDetectionEnabled=true | SafeContextManager 开启泄漏检测 |
| WB-CFG-006 | cleanupEnabled=true | ZeroLeakThreadLocalManager 开启清理 |

---

## 3. 黑盒测试方案

### 3.1 功能测试用例

#### 3.1.1 自动配置测试

| 测试编号 | 场景 | 步骤 | 预期 |
|---------|------|------|------|
| BB-CFG-001 | Starter 自动装配 | 引入依赖，启动 Spring Boot 应用 | 应用正常启动，日志显示 TFI 配置应用成功 |
| BB-CFG-002 | 注解未启用 | `tfi.annotation.enabled` 未设置 | `TfiAnnotationAspect` Bean 不存在 |
| BB-CFG-003 | 注解启用 | `tfi.annotation.enabled=true` | `TfiAnnotationAspect` Bean 存在 |
| BB-CFG-004 | IDE 配置补全 | 在 IDEA 中输入 `tfi.context.` | 显示所有配置项 |

#### 3.1.2 注解追踪测试

| 测试编号 | 场景 | 步骤 | 预期 |
|---------|------|------|------|
| BB-ANN-001 | 基本追踪 | `@TfiTask("测试")` 方法被调用 | 控制台/日志包含 "测试" Stage |
| BB-ANN-002 | 参数记录 | `@TfiTask(logArgs=true)` + 传参 | 日志包含脱敏后的参数值 |
| BB-ANN-003 | 返回值记录 | `@TfiTask(logResult=true)` + 返回值 | 日志包含脱敏后的返回值 |
| BB-ANN-004 | 异常记录 | `@TfiTask(logException=true)` + 异常 | 日志包含异常信息，异常正常传播 |
| BB-ANN-005 | 采样控制 | `@TfiTask(samplingRate=0.0)` | 无追踪记录 |
| BB-ANN-006 | 条件追踪 | `@TfiTask(condition="false")` | 无追踪记录 |
| BB-ANN-007 | SpEL 动态名 | `@TfiTask("#{#methodName}")` | Stage 名为方法名 |
| BB-ANN-008 | 嵌套追踪 | 方法 A（@TfiTask） → 方法 B（@TfiTask） | 生成父子层级 Stage |

#### 3.1.3 脱敏测试

| 测试编号 | 场景 | 输入 | 预期 |
|---------|------|------|------|
| BB-MASK-001 | 密码字段 | `password="mysecret"` | 日志显示 `"m***t"` |
| BB-MASK-002 | 邮箱值 | `email="test@example.com"` | 日志显示 `"t***@example.com"` |
| BB-MASK-003 | 电话值 | `phone="123-456-7890"` | 日志显示 `"123-***-7890"` |
| BB-MASK-004 | 信用卡值 | `card="1234 5678 9012 3456"` | 日志显示 `"****-****-****-3456"` |
| BB-MASK-005 | 普通字段 | `name="John"` | 日志显示 `"John"`（不脱敏） |

#### 3.1.4 安全测试

| 测试编号 | 场景 | 输入 | 预期 |
|---------|------|------|------|
| BB-SEC-001 | SpEL 反射注入 | `condition="T(Runtime).getRuntime().exec('ls')"` | 被安全机制阻断 |
| BB-SEC-002 | SpEL Class 注入 | `condition="''.class.forName('java.lang.Runtime')"` | 被安全机制阻断 |
| BB-SEC-003 | SpEL 超长表达式 | 1001 字符表达式 | 拒绝执行 |
| BB-SEC-004 | SpEL 深度嵌套 | 11 层括号嵌套 | 拒绝执行 |
| BB-SEC-005 | 正常 SpEL 表达式 | `condition="#methodName == 'test'"` | 正常求值 |

---

### 3.2 兼容性测试

| 测试编号 | 环境 | 步骤 | 预期 |
|---------|------|------|------|
| BB-COMPAT-001 | Spring Boot 3.3.x + Java 21 | 集成测试 | 正常工作 |
| BB-COMPAT-002 | Spring Boot 3.2.x + Java 21 | 集成测试 | 正常工作 |
| BB-COMPAT-003 | Spring Boot 3.5.x + Java 21 | 集成测试 | 正常工作 |
| BB-COMPAT-004 | Maven 3.8 构建 | `mvn clean package` | 正常打包 |
| BB-COMPAT-005 | Maven 3.9 构建 | `mvn clean package` | 正常打包 |

---

## 4. 性能测试方案

### 4.1 性能测试目标

| 指标 | 目标值 | 测试方法 |
|------|--------|---------|
| AOP 拦截延迟（P50） | ≤ 0.2ms | JMH Benchmark |
| AOP 拦截延迟（P99） | ≤ 0.5ms | JMH Benchmark |
| SpEL 首次求值延迟 | ≤ 2ms | JMH Benchmark |
| SpEL 缓存命中延迟 | ≤ 0.1ms | JMH Benchmark |
| 脱敏处理延迟 | ≤ 0.3ms/字段 | JMH Benchmark |
| 内存占用（Starter） | ≤ 5MB | JFR / VisualVM |
| 吞吐量影响 | ≤ 3% TPS 下降 | Apache JMeter |

### 4.2 JMH 基准测试用例

#### 4.2.1 AOP 拦截基准

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
public class TfiAnnotationAspectBenchmark {

    @Benchmark
    public void aspectIntercept_fullFlow();     // 完整拦截流程

    @Benchmark
    public void aspectIntercept_samplingSkip(); // 采样跳过

    @Benchmark
    public void aspectIntercept_conditionSkip(); // 条件跳过

    @Benchmark
    public void baseline_noAspect();            // 无 AOP 基线
}
```

#### 4.2.2 SpEL 求值基准

```java
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class SafeSpELBenchmark {

    @Benchmark
    public void evaluate_simpleLiteral();       // 简单字面量

    @Benchmark
    public void evaluate_variableAccess();      // 变量访问

    @Benchmark
    public void evaluate_conditionExpression();  // 条件表达式

    @Benchmark
    public void validate_normalExpression();     // 安全验证
}
```

#### 4.2.3 脱敏基准

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class UnifiedDataMaskerBenchmark {

    @Benchmark
    public void mask_sensitiveField();          // 敏感字段名

    @Benchmark
    public void mask_emailValue();              // 邮箱值

    @Benchmark
    public void mask_phoneValue();              // 电话值

    @Benchmark
    public void mask_creditCardValue();         // 信用卡值

    @Benchmark
    public void mask_normalValue();             // 普通值（无脱敏）
}
```

### 4.3 压力测试方案

#### 4.3.1 并发压测

| 场景 | 并发线程 | 持续时间 | 关注指标 |
|------|---------|---------|---------|
| 低负载 | 10 | 5min | 延迟、错误率 |
| 中负载 | 50 | 10min | 延迟、内存、CPU |
| 高负载 | 200 | 15min | 延迟P99、GC、内存 |
| 极限负载 | 500 | 30min | 系统稳定性、OOM |

#### 4.3.2 长时间运行测试（Soak Test）

| 指标 | 验证点 |
|------|--------|
| 内存 | SpEL 缓存不无限增长 |
| ThreadLocal | 无泄漏（ZeroLeakManager 工作正常） |
| CPU | 无异常 CPU spike |
| 日志 | 无频繁 WARN/ERROR |

**运行参数**：50 并发，持续 4 小时，采样率 100%

#### 4.3.3 SpEL 缓存压测

| 场景 | 说明 | 预期 |
|------|------|------|
| 高基数表达式 | 10000 种不同表达式 | 缓存内存增长可控 |
| 重复表达式 | 10 种表达式重复百万次 | 缓存命中率 >99% |
| 清理后恢复 | clearCache() 后继续使用 | 无性能断崖 |

---

## 5. 回归测试方案

### 5.1 回归测试范围

每次代码变更后，执行以下回归测试集：

| 级别 | 范围 | 执行时机 | 预期耗时 |
|------|------|---------|---------|
| P0 | 核心功能冒烟 | 每次 commit | ≤ 30s |
| P1 | 全量单元测试 | 每次 PR | ≤ 2min |
| P2 | 集成测试 | 每日 nightly | ≤ 10min |
| P3 | 性能回归 | 每周 / 发版前 | ≤ 30min |

### 5.2 P0 冒烟测试清单

- [ ] `@TfiTask` 注解拦截生效
- [ ] SpEL 条件求值正常
- [ ] 数据脱敏对密码字段生效
- [ ] 异常正常传播不被吞没
- [ ] Spring Boot 应用正常启动

---

## 6. 测试环境要求

### 6.1 硬件要求

| 资源 | 最低配置 | 推荐配置 |
|------|---------|---------|
| CPU | 2 核 | 4 核 |
| 内存 | 2GB | 4GB |
| 磁盘 | 1GB | 5GB |

### 6.2 软件要求

| 工具 | 版本 | 用途 |
|------|------|------|
| JDK | 21+ | 编译和运行 |
| Maven | 3.8+ | 构建 |
| JUnit 5 | 5.10+ | 单元测试 |
| Mockito | 5.x | Mock 框架 |
| AssertJ | 3.x | 断言库 |
| JMH | 1.37+ | 性能基准 |
| JaCoCo | 0.8.x | 覆盖率 |
| Spring Boot Test | 3.x | 集成测试 |

---

## 7. 测试用例优先级与执行计划

### 7.1 优先级矩阵

| 优先级 | 用例数 | 描述 |
|--------|-------|------|
| P0 (必须) | 25 | 核心功能：AOP 拦截、脱敏、异常透明 |
| P1 (重要) | 35 | 安全测试、SpEL 求值、配置管理 |
| P2 (一般) | 20 | 边界条件、兼容性、性能 |
| P3 (次要) | 10 | 缓存统计、日志输出验证 |

### 7.2 执行计划

| 阶段 | 周期 | 活动 |
|------|------|------|
| 第 1 周 | 单元测试编写 | 白盒测试用例实现（P0 + P1） |
| 第 2 周 | 集成测试编写 | 黑盒测试 + Spring Context 集成测试 |
| 第 3 周 | 性能测试 | JMH 基准测试 + 压力测试 |
| 第 4 周 | 回归验证 | 全量执行 + 缺陷修复 + 报告 |

---

## 8. 缺陷管理

### 8.1 缺陷严重度定义

| 级别 | 定义 | 示例 |
|------|------|------|
| **S1 - 致命** | 导致应用崩溃或数据丢失 | AOP 拦截导致 NPE |
| **S2 - 严重** | 核心功能失效 | 脱敏失效泄露密码 |
| **S3 - 一般** | 功能异常但有变通方案 | SpEL 缓存统计不准确 |
| **S4 - 轻微** | UI/日志/文档问题 | 日志格式不一致 |

### 8.2 缺陷处理 SLA

| 级别 | 响应时间 | 修复时间 |
|------|---------|---------|
| S1 | 1 小时 | 4 小时 |
| S2 | 4 小时 | 1 工作日 |
| S3 | 1 工作日 | 3 工作日 |
| S4 | 3 工作日 | 下一迭代 |

---

## 9. 已知测试风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 模块当前无测试代码 | 从零开始，工作量大 | 按优先级分批实现 |
| AOP 自调用无法拦截 | 部分场景无法测试 | 文档说明 + 集成测试覆盖 |
| `Math.random()` 采样不确定性 | 概率测试可能偶尔失败 | 使用统计验证（大样本 + 允差） |
| SpEL 缓存无上限 | 长时间压测可能 OOM | 监控缓存大小，提出改进 |

---

## 附录 A：测试用例编号规则

```
{类型}-{组件缩写}-{序号}

类型：WB = 白盒, BB = 黑盒, PERF = 性能
组件：ASP = Aspect, SPEL = SpEL, MASK = Masker, CFG = Config
序号：三位数字
```

---

*文档编写：资深测试专家*  
*审核确认：资深项目经理*
