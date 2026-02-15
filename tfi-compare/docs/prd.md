# TFI-Compare 产品需求文档 (PRD)

> **文档版本**: v2.0.0  
> **模块版本**: 3.0.0  
> **撰写角色**: 资深产品经理  
> **审阅**: 项目经理协调  
> **初版日期**: 2026-02-15  
> **更新日期**: 2026-02-15 (v2 — 代码改进后更新功能/指标状态)  

---

## 目录

1. [产品概述](#1-产品概述)
2. [目标用户与场景](#2-目标用户与场景)
3. [产品目标与成功指标](#3-产品目标与成功指标)
4. [功能需求](#4-功能需求)
5. [非功能需求](#5-非功能需求)
6. [用户旅程](#6-用户旅程)
7. [功能优先级矩阵](#7-功能优先级矩阵)
8. [竞品分析](#8-竞品分析)
9. [版本规划](#9-版本规划)
10. [风险与依赖](#10-风险与依赖)
11. [附录](#11-附录)

---

## 1. 产品概述

### 1.1 产品定位

**TFI-Compare** 是 TaskFlowInsight 生态的**核心比较与变更追踪引擎**，为 Java 应用提供「对象级 X 光」能力——自动检测任意 Java 对象在业务流程中的字段级变化，并以结构化格式输出差异报告。

### 1.2 产品愿景

> 让每一次数据变更都可追溯、可审计、可回放。

### 1.3 一句话价值主张

> 只需一行代码，即可对比任意 Java 对象的差异——支持深度嵌套、列表移动检测、精度控制、多格式导出。

### 1.4 核心价值

| 价值维度 | 描述 |
|---------|------|
| **审计合规** | 自动记录数据变更，满足金融/医疗等行业审计要求 |
| **业务可观测** | 让隐性的数据变化变成显性的结构化记录 |
| **开发效率** | 免去手写 diff 逻辑，注解驱动、零侵入 |
| **运维友好** | Prometheus 指标、降级策略、性能预算 |

---

## 2. 目标用户与场景

### 2.1 用户画像

| 角色 | 需求 | 使用场景 |
|------|------|---------|
| **Java 后端开发** | 快速对比对象差异，减少手写 diff 代码 | 业务开发、代码审查 |
| **架构师** | 可扩展的比较框架，适配多种业务类型 | 框架选型、架构设计 |
| **QA 工程师** | 验证数据变更的正确性 | 回归测试、数据校验 |
| **审计/合规人员** | 追踪数据变更历史 | 审计报告、合规检查 |
| **运维工程师** | 监控比较引擎性能 | 生产环境监控 |

### 2.2 核心使用场景

#### 场景 1: 订单变更追踪

```
用户操作: 客户修改订单的商品数量和配送地址
系统行为: TFI 自动检测 Order 对象的变更字段
输出结果: 
  - quantity: 3 → 5 (UPDATE)
  - address.city: "北京" → "上海" (UPDATE)
  - items[2]: new (CREATE)
价值: 运营可追溯每笔订单变更，客服可快速定位问题
```

#### 场景 2: 用户信息审计

```
用户操作: 管理员修改用户权限
系统行为: TFI 深度追踪 User 对象变更
输出结果:
  - roles: [ADMIN] → [ADMIN, SUPER_ADMIN] (UPDATE)
  - lastModifiedBy: null → "admin_001" (CREATE)
价值: 满足 SOX 合规要求，审计有据可查
```

#### 场景 3: 批量数据迁移验证

```
用户操作: 系统从旧表迁移数据到新表
系统行为: 使用批量比较验证迁移结果
输出结果:
  - 1000 条记录，998 条完全一致
  - 2 条存在精度偏差（金额字段小数位差异）
价值: 自动化迁移校验，替代人工抽检
```

#### 场景 4: 列表差异分析

```
用户操作: 对比两个版本的商品列表
系统行为: Entity 策略匹配 @Key(sku)，检测新增/删除/修改/移动
输出结果:
  - SKU-001: price 99.9 → 89.9 (UPDATE)
  - SKU-003: (DELETE)
  - SKU-005: (CREATE)
  - SKU-002: index 1 → 3 (MOVE)
价值: 商品管理可追踪每次上架/调价/排序变更
```

---

## 3. 产品目标与成功指标

### 3.1 产品目标

| # | 目标 | 衡量标准 |
|---|------|---------|
| G1 | 覆盖 95% 的 Java 对象比较场景 | 支持基本类型、包装类、String、Date、BigDecimal、Collection、Map、Array、嵌套对象 |
| G2 | 零代码侵入 | 仅需注解 + 配置，无需修改业务代码 |
| G3 | 毫秒级响应 | 1000 字段对象对比 < 50ms |
| G4 | 生产级可靠性 | 异常不影响业务流程（静默降级） |
| G5 | 企业级可扩展 | SPI + 自定义策略 + 多格式导出 |

### 3.2 关键成功指标 (KPI)

| 指标 | 目标值 | v1 状态 | v2 状态 |
|------|-------|---------|---------|
| API 调用成功率 | ≥ 99.9% | 待测量 | 待测量（异常隔离机制已验证） |
| P99 响应时间（1000 字段） | < 100ms | 待 benchmark | 待 benchmark |
| 功能覆盖率（Java 类型） | ≥ 95% | ~90% | ~92%（改进中） |
| 单元测试覆盖率 | ≥ 80% | ~60%（模块级缺失） | **~75%**（tfi-compare 模块内 95+ case） |
| 用户 API 满意度 | ≥ 4.0/5.0 | 待调研 | 待调研 |

---

## 4. 功能需求

### 4.1 功能全景图

```
TFI-Compare 功能矩阵
├── F1: 对象比较 (Core)
│   ├── F1.1 单对象深度比较
│   ├── F1.2 列表比较（5 种策略）
│   ├── F1.3 批量比较
│   ├── F1.4 三方合并比较
│   └── F1.5 相似度计算
├── F2: 变更追踪 (Tracking)
│   ├── F2.1 浅层追踪 (track)
│   ├── F2.2 深度追踪 (trackDeep)
│   ├── F2.3 会话级追踪
│   └── F2.4 变更查询
├── F3: 类型系统 (Type System)
│   ├── F3.1 @Entity + @Key
│   ├── F3.2 @ValueObject
│   ├── F3.3 @DiffIgnore / @DiffInclude
│   ├── F3.4 @NumericPrecision
│   ├── F3.5 @DateFormat
│   ├── F3.6 @CustomComparator
│   └── F3.7 @ShallowReference
├── F4: 导出 (Export)
│   ├── F4.1 Console 导出
│   ├── F4.2 JSON 导出
│   ├── F4.3 CSV 导出
│   ├── F4.4 XML 导出
│   ├── F4.5 Map 导出
│   └── F4.6 流式导出
├── F5: 渲染 (Render)
│   ├── F5.1 Markdown 报告
│   └── F5.2 自定义渲染样式
├── F6: 性能 (Performance)
│   ├── F6.1 PerfGuard 预算控制
│   ├── F6.2 自适应降级
│   ├── F6.3 缓存策略
│   └── F6.4 大对象优化
└── F7: 可观测 (Observability)
    ├── F7.1 Micrometer 指标
    ├── F7.2 降级事件通知
    └── F7.3 诊断日志
```

### 4.2 详细功能描述

#### F1: 对象比较

##### F1.1 单对象深度比较

| 属性 | 描述 |
|------|------|
| **入口** | `CompareService.compare(obj1, obj2)` |
| **功能** | 递归比较两个对象的所有字段差异 |
| **输入** | 任意两个 Java 对象（同类型或不同类型） |
| **输出** | `CompareResult`（包含 FieldChange 列表、相似度、耗时） |
| **配置** | `CompareOptions`（深度、null 处理、精度、超时） |
| **边界** | 最大深度 10（可配置），循环引用自动检测 |

##### F1.2 列表比较

| 策略 | 适用场景 | 特性 |
|------|---------|------|
| **Simple** | 索引固定的列表 | 按索引逐一对比 |
| **Entity** | 有唯一标识的实体列表 | 按 @Key 匹配，检测增删改 |
| **LCS** | 需要移动检测的列表 | 最长公共子序列算法 |
| **Levenshtein** | 编辑距离最小化 | 编辑距离算法 |
| **AsSet** | 顺序无关的集合 | 集合语义对比 |

##### F1.3 批量比较

| 属性 | 描述 |
|------|------|
| **入口** | `CompareService.compareBatch(pairs, options)` |
| **特性** | 自动并行处理，利用虚拟线程池（Java 21 Virtual Threads）— v2 改进 |
| **限制** | 建议单批次 ≤ 100 对 |

##### F1.4 三方合并比较

| 属性 | 描述 |
|------|------|
| **入口** | `CompareService.compareThreeWay(base, left, right, options)` |
| **输出** | `MergeResult`（合并结果 + 冲突列表） |
| **冲突类型** | FIELD_CONFLICT、TYPE_CONFLICT、NULL_CONFLICT |
| **解决策略** | LEFT_WINS、RIGHT_WINS、MANUAL |

#### F2: 变更追踪

##### F2.1 浅层追踪

```java
// API 示例
TFI.track("order", orderObj, "status", "amount");  // 指定字段
List<ChangeRecord> changes = TFI.getChanges();
```

##### F2.2 深度追踪

```java
// API 示例
TFI.trackDeep("user", userObj);  // 全字段深度追踪
List<ChangeRecord> changes = TFI.getChanges();
```

#### F3: 类型系统

| 注解 | 场景 | 示例 |
|------|------|------|
| `@Entity` + `@Key` | 有身份标识的业务对象 | `@Entity class Order { @Key Long id; }` |
| `@ValueObject` | 无身份的值对象 | `@ValueObject class Money { BigDecimal amount; }` |
| `@NumericPrecision(scale=2)` | 金额等精度敏感字段 | `@NumericPrecision(scale=2) BigDecimal price;` |
| `@DateFormat("yyyy-MM-dd")` | 日期只比较到天 | `@DateFormat("yyyy-MM-dd") Date createDate;` |
| `@DiffIgnore` | 排除非业务字段 | `@DiffIgnore Date updateTime;` |
| `@ShallowReference` | 大对象仅比引用 | `@ShallowReference Department dept;` |

#### F4: 导出

| 格式 | 用途 | 流式支持 |
|------|------|---------|
| Console | 开发调试 | 是 |
| JSON | API 返回、存储 | 是 |
| CSV | Excel 分析 | 是 |
| XML | 系统集成 | 是 |
| Map | 程序化处理 | 否 |

---

## 5. 非功能需求

### 5.1 性能需求

| 指标 | 目标 | 优先级 |
|------|------|-------|
| 简单对象对比（10 字段） | < 1ms | P0 |
| 中等对象对比（100 字段） | < 10ms | P0 |
| 复杂对象对比（1000 字段） | < 100ms | P0 |
| 列表对比（1000 元素，Entity 策略） | < 500ms | P1 |
| 列表对比（1000 元素，LCS 策略） | < 2s | P1 |
| 内存占用（单次比较） | < 10MB | P0 |

### 5.2 可靠性需求

| 需求 | 描述 |
|------|------|
| 异常隔离 | TFI 异常不得传播到业务代码 |
| 静默降级 | 性能超限时自动降级，不阻塞业务 |
| 内存安全 | 循环引用检测、深度限制、大集合摘要 |
| 零泄露 | ThreadLocal 使用保证可清理 |

### 5.3 兼容性需求

| 需求 | 描述 |
|------|------|
| Java 版本 | Java 21+ |
| Spring Boot | 3.x（可选，支持纯 Java） |
| 序列化 | 无需实现 Serializable |
| 代理兼容 | 支持 CGLIB/JDK 动态代理对象 |

### 5.4 安全需求

| 需求 | 描述 |
|------|------|
| 数据脱敏 | 导出时支持字段级脱敏（MaskRuleMatcher） |
| 无副作用 | 比较过程不修改原始对象 |
| 日志安全 | 敏感字段不出现在日志中 |

---

## 6. 用户旅程

### 6.1 开发者首次接入

```
Step 1: 添加 Maven 依赖
         └── pom.xml: tfi-compare

Step 2: 标注业务对象
         └── @Entity + @Key / @ValueObject / @DiffIgnore

Step 3: 调用 API
         └── CompareResult result = TFI.compare(before, after);

Step 4: 查看结果
         └── result.getChanges().forEach(...)

Step 5: 选择导出格式
         └── ChangeJsonExporter.export(changes)

Step 6: 配置优化（可选）
         └── application.yml: tfi.compare.*
```

### 6.2 进阶用户定制

```
Step 1: 注册自定义策略
         └── compareService.registerStrategy(Money.class, moneyStrategy)

Step 2: 实现 SPI Provider
         └── implements ComparisonProvider + META-INF/services

Step 3: 配置性能预算
         └── CompareOptions.builder().perfBudgetMs(1000).build()

Step 4: 监控生产指标
         └── /actuator/prometheus → tfi_compare_*
```

---

## 7. 功能优先级矩阵

| 功能 | 优先级 | 状态 | 版本 |
|------|-------|------|------|
| 单对象深度比较 | P0 | ✅ 已完成 | v1.0 |
| 列表比较（Simple/Entity） | P0 | ✅ 已完成 | v2.0 |
| 变更追踪（浅/深） | P0 | ✅ 已完成 | v1.0 |
| JSON/Console 导出 | P0 | ✅ 已完成 | v1.0 |
| 注解类型系统 | P0 | ✅ 已完成 | v2.0 |
| 列表比较（LCS/Levenshtein） | P1 | ✅ 已完成 | v3.0 |
| 三方合并比较 | P1 | ✅ 已完成 | v3.0 |
| 流式导出 | P1 | ✅ 已完成 | v3.0 |
| PerfGuard 性能预算 | P1 | ✅ 已完成 | v3.0 |
| 自适应降级 | P1 | ✅ 已完成 | v3.0 |
| SPI Provider 机制 | P1 | ✅ 已完成 | v4.0 |
| CSV/XML 导出 | P2 | ✅ 已完成 | v3.0 |
| Markdown 渲染 | P2 | ✅ 已完成 | v3.0 |
| 数据脱敏导出 | P2 | ✅ 已完成 | v3.0 |
| GraalVM native 支持 | P3 | ❌ 未开始 | 未规划 |
| Web UI 可视化 | P3 | ❌ 未开始 | 未规划 |

---

## 8. 竞品分析

### 8.1 竞品对比

| 特性 | **TFI-Compare** | Javers | java-object-diff | Apache Commons |
|------|-----------------|--------|-------------------|----------------|
| 深度对象比较 | ✅ | ✅ | ✅ | ❌ (浅比较) |
| 列表 Move 检测 | ✅ (LCS) | ✅ | ❌ | ❌ |
| @Key 实体匹配 | ✅ | ✅ | ❌ | ❌ |
| 精度控制 | ✅ (@NumericPrecision) | ❌ | ❌ | ❌ |
| 日期格式化 | ✅ (@DateFormat) | ❌ | ❌ | ❌ |
| 自定义比较器 | ✅ (SPI + 注解) | ✅ | ✅ | ❌ |
| 变更追踪 | ✅ (Snapshot-based) | ✅ (Commit-based) | ❌ | ❌ |
| 性能降级 | ✅ (PerfGuard) | ❌ | ❌ | ❌ |
| 多格式导出 | ✅ (JSON/CSV/XML/Map) | ✅ (JSON) | ❌ | ❌ |
| Spring Boot 自动配置 | ✅ | ✅ | ❌ | ❌ |
| 纯 Java 支持 | ✅ | ✅ | ✅ | ✅ |
| 三方合并 | ✅ | ❌ | ❌ | ❌ |
| Prometheus 指标 | ✅ | ❌ | ❌ | ❌ |

### 8.2 差异化优势

1. **精度控制注解**: `@NumericPrecision` + `@DateFormat` 是独有特性
2. **PerfGuard**: 性能预算 + 自适应降级是 TFI 独有
3. **5 种列表策略**: 覆盖最广
4. **三方合并**: 冲突检测 + 解决策略
5. **流式导出**: 大数据量导出不 OOM

---

## 9. 版本规划

### 9.1 已发布版本

| 版本 | 主要特性 | 发布时间 |
|------|---------|---------|
| v1.0 | 基础对象比较、变更追踪、Console/JSON 导出 | 2025-01 |
| v2.0 | 注解类型系统、Entity/Simple 列表策略、深度追踪 | 2025-06 |
| v3.0 | LCS/Levenshtein、PerfGuard、降级、流式导出、三方合并 | 2025-10 |

### 9.2 v3.0.0 改进项（v2 新增）

| 改进项 | 状态 | 描述 |
|--------|------|------|
| System.out 清除 | ✅ 已完成 | 全部替换为 SLF4J logger.debug() |
| CompareService 职责拆分 | ✅ 已完成 | 新增 CompareReportGenerator + ThreeWayMergeService |
| DiffDetector volatile 修复 | ✅ 已完成 | 6 个静态字段加 volatile |
| compareBatch 虚拟线程池 | ✅ 已完成 | parallelStream → Virtual Thread Executor |
| 模块级单元测试 | ✅ 已完成 | 6 个测试文件，95+ test case |
| ArchUnit 架构测试 | ✅ 已完成 | 6 条架构约束规则 |

### 9.3 当前开发 (v4.0.0)

| 特性 | 状态 | 描述 |
|------|------|------|
| SPI Provider 路由 | 开发中 | ComparisonProvider / TrackingProvider / RenderProvider |
| Provider 发现机制 | 开发中 | Spring Bean → ServiceLoader → Default 优先级链 |
| Provider 模式配置 | 开发中 | auto / spring-only / service-loader-only |

### 9.4 未来规划

| 版本 | 特性 | 优先级 |
|------|------|-------|
| v4.1 | 变更回放（Replay） | P2 |
| v4.2 | Web UI 差异可视化 | P3 |
| v5.0 | 增量比较（仅比较变化部分） | P2 |
| v5.0 | GraalVM native-image 支持 | P3 |

---

## 10. 风险与依赖

### 10.1 技术风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| 大对象图 OOM | 高 | 中 | max-depth + 大集合摘要 + PerfGuard |
| 循环引用死循环 | 高 | 低 | ObjectSnapshotDeep 内置循环检测 |
| 反射性能瓶颈 | 中 | 中 | ReflectionMetaCache + Caffeine |
| Spring 版本升级不兼容 | 中 | 低 | spring-boot-autoconfigure optional |
| 并发安全问题 | 高 | 低 | ThreadLocal + ConcurrentHashMap + 代码审查 |

### 10.2 外部依赖

| 依赖 | 风险 | 缓解 |
|------|------|------|
| tfi-flow-core | 内部依赖，版本同步 | 同仓库管理 |
| Caffeine | 社区活跃，低风险 | optional 依赖 |
| Micrometer | Spring 生态标配 | optional 依赖 |
| Jackson | 广泛使用 | optional 依赖 |

---

## 11. 附录

### 11.1 术语表

| 术语 | 定义 |
|------|------|
| **Entity** | 有唯一标识的业务对象，列表中通过 @Key 匹配 |
| **ValueObject** | 无身份标识的值对象，基于所有字段值比较 |
| **FieldChange** | 字段级变更记录，包含路径、旧值、新值、变更类型 |
| **CompareResult** | 比较结果集合，包含所有 FieldChange |
| **PerfGuard** | 性能守卫，在预算内控制比较执行 |
| **SSOT** | Single Source of Truth，唯一真相源 |
| **SPI** | Service Provider Interface，服务提供者接口 |
| **降级** | 性能超限时自动切换到更简单的策略 |

### 11.2 参考文档

- [TFI QuickStart](../src/main/java/com/syy/taskflowinsight/tracking/docs/QuickStart.md)
- [Package Design](../src/main/java/com/syy/taskflowinsight/tracking/docs/PACKAGE_DESIGN.md)
- [Configuration Guide](../src/main/java/com/syy/taskflowinsight/tracking/docs/Configuration.md)
- [Performance Best Practices](../src/main/java/com/syy/taskflowinsight/tracking/docs/Performance-BestPractices.md)

---

*文档由资深产品经理撰写，项目经理审阅*
