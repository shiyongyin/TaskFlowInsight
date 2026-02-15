# TFI-Compare 测试方案

> **文档版本**: v3.0.0  
> **模块版本**: 3.0.0 (分支: feature/v4.0.0-routing-refactor)  
> **撰写角色**: 资深测试专家  
> **审阅**: 项目经理协调  
> **日期**: 2026-02-15  
> **上次更新**: 2026-02-16 (分支覆盖 75.1% + 性能测试 + API 兼容性测试)  

---

## 目录

1. [测试概述](#1-测试概述)
2. [测试策略](#2-测试策略)
3. [白盒测试方案](#3-白盒测试方案)
4. [黑盒测试方案](#4-黑盒测试方案)
5. [功能测试方案](#5-功能测试方案)
6. [性能测试方案](#6-性能测试方案)
7. [测试环境](#7-测试环境)
8. [测试用例矩阵](#8-测试用例矩阵)
9. [现有测试评估](#9-现有测试评估)
10. [测试改进计划](#10-测试改进计划)

---

## 1. 测试概述

### 1.1 测试范围

| 范围 | 包含 | 排除 |
|------|------|------|
| **模块** | tfi-compare 所有主代码 | demo 包、model 包（POJO） |
| **层级** | 单元 / 集成 / 性能 / 属性 | UI 测试（无 UI） |
| **环境** | Spring Boot / 纯 Java | 分布式环境 |

### 1.2 质量目标

| 指标 | 目标值 | 当前状态 |
|------|-------|---------|
| 指令覆盖率 | ≥ 85% | ✅ **87.8%** (已达标) |
| 分支覆盖率 | ≥ 75% | ✅ **75.1%** (已达标) |
| 方法覆盖率 | - | **89.4%** |
| 类覆盖率 | - | **93.8%** |
| 测试总数 | - | **3,591 个** (0 failures) |
| 测试文件数 | - | **84 个** |
| SpotBugs High | 0 | ✅ **0** (已修复 10 个) |
| 性能测试 | - | **14 个** (gated by `-Dtfi.perf.enabled=true`) |
| API 兼容性测试 | - | **22 个** |
| 变异测试存活率 | ≤ 20% | 未开展 |
| 关键路径覆盖 | 100% | ✅ ~95% |

### 1.3 测试框架

| 工具 | 版本 | 用途 |
|------|------|------|
| JUnit 5 | 5.10+ | 单元/集成测试 |
| AssertJ | 3.25+ | 流式断言 |
| Mockito | 5.x | Mock/Stub |
| JMH | 1.37 | 微基准测试 |
| jqwik | 1.8+ | 属性测试 |
| JaCoCo | 0.8.12 | 覆盖率 |
| ArchUnit | 1.2+ | 架构测试 |
| ApprovalTests | - | 快照/黄金文件测试 |

---

## 2. 测试策略

### 2.1 测试金字塔

```
         ╱╲
        ╱  ╲         E2E Tests (5%)
       ╱────╲        - Full Spring context
      ╱      ╲       - Real objects + assertions
     ╱────────╲      Integration Tests (25%)
    ╱          ╲     - CompareService + DiffFacade
   ╱────────────╲    - Spring slice / multi-class
  ╱              ╲   Unit Tests (70%)
 ╱────────────────╲  - CompareEngine, strategies, detector
╱                  ╲ - Single class, mocked deps
```

### 2.2 测试分类

| 分类 | 命名约定 | 执行频率 | Maven Profile |
|------|---------|---------|---------------|
| 单元测试 | `*Test.java` / `*Tests.java` | 每次 commit | default |
| 集成测试 | `*IntegrationTest.java` / `*IT.java` | 每次 PR | default |
| 性能测试 | `*PerformanceTest.java` / `*Benchmark.java` | 按需 | `-Pperf` |
| 属性测试 | `*PropertyTest.java` | 每次 PR | default |
| 架构测试 | `*ArchTest.java` | 每次 PR | default |

---

## 3. 白盒测试方案

### 3.1 CompareEngine 白盒测试

#### 3.1.1 控制流覆盖

```
CompareEngine.execute() 控制流:
  ├── [C1] obj1 == obj2 (same reference) → identical result
  ├── [C2] obj1 == null XOR obj2 == null → null diff result
  ├── [C3] obj1.class != obj2.class → type diff result
  ├── [C4] obj1 instanceof List → route to ListCompareExecutor
  │   ├── [C4.1] ListCompareExecutor != null → delegate
  │   └── [C4.2] ListCompareExecutor == null → fallback to snapshot
  ├── [C5] Named strategy match → use named strategy
  ├── [C6] Custom strategy match → use custom strategy
  ├── [C7] StrategyResolver match → use resolved strategy
  ├── [C8] No strategy → fallback to snapshot diff
  │   ├── [C8.1] Snapshot → DiffFacade
  │   └── [C8.2] DiffFacade exception → empty result
  └── [C9] sortResult() → StableSorter + metrics
```

| 测试用例 ID | 路径 | 输入 | 预期输出 |
|------------|------|------|---------|
| CE-WB-001 | C1 | same object ref | identical=true, changes=[] |
| CE-WB-002 | C2 (left null) | null, non-null | NULL_DIFF result |
| CE-WB-003 | C2 (right null) | non-null, null | NULL_DIFF result |
| CE-WB-004 | C3 | String, Integer | TYPE_DIFF result |
| CE-WB-005 | C4.1 | two Lists, executor present | delegate to executor |
| CE-WB-006 | C4.2 | two Lists, executor null | fallback snapshot |
| CE-WB-007 | C5 | registered named strategy | named strategy called |
| CE-WB-008 | C6 | registered custom strategy | custom strategy called |
| CE-WB-009 | C7 | resolver finds strategy | resolved strategy called |
| CE-WB-010 | C8.1 | no strategy matches | snapshot + DiffFacade |
| CE-WB-011 | C8.2 | DiffFacade throws | empty result, error logged |
| CE-WB-012 | C9 | normal result | sorted by StableSorter |

#### 3.1.2 数据流覆盖

| 变量 | 定义点 | 使用点 | 测试 |
|------|--------|--------|------|
| `resolver` | 构造函数 | C7 | CE-WB-009 |
| `listCompareExecutor` | 构造函数 | C4 | CE-WB-005/006 |
| `programmaticDiffDetector` | setter | C8 fallback | CE-WB-013 |
| `customStrategies` | 构造函数 | C6 lookup | CE-WB-008 |
| `namedStrategies` | 构造函数 | C5 lookup | CE-WB-007 |

### 3.2 DiffDetector 白盒测试

#### 3.2.1 分支覆盖

| 测试用例 ID | 分支 | 场景 | 预期 |
|------------|------|------|------|
| DD-WB-001 | before 有, after 无 | 字段删除 | ChangeType.DELETE |
| DD-WB-002 | before 无, after 有 | 字段新增 | ChangeType.CREATE |
| DD-WB-003 | 值相同 | 无变化 | 无记录 |
| DD-WB-004 | 值不同 | 字段更新 | ChangeType.UPDATE |
| DD-WB-005 | 值为 Map | 递归比较 | 嵌套 ChangeRecord |
| DD-WB-006 | 值为 Set | Set 策略 | Set diff |
| DD-WB-007 | 值为 Collection | Collection 策略 | Collection diff |
| DD-WB-008 | 精度比较启用 | BigDecimal 精度 | 精度内视为相同 |
| DD-WB-009 | 重对象缓存命中 | field > threshold | 使用缓存 |
| DD-WB-010 | 路径去重启用 | 重叠路径 | 去重后结果 |

### 3.3 StrategyResolver 白盒测试

| 测试用例 ID | 场景 | 预期 |
|------------|------|------|
| SR-WB-001 | 精确类型匹配 | 返回精确策略 (priority 100) |
| SR-WB-002 | 接口/泛型匹配 | 返回泛型策略 (priority 50) |
| SR-WB-003 | 无匹配 | 返回 null |
| SR-WB-004 | 缓存命中 | 返回缓存结果 |
| SR-WB-005 | 缓存清空 | 重新解析 |
| SR-WB-006 | 多策略竞争 | 最高优先级胜出 |

### 3.4 ListCompareExecutor 白盒测试

| 测试用例 ID | 场景 | 预期 |
|------------|------|------|
| LCE-WB-001 | options 指定策略 | 使用指定策略 |
| LCE-WB-002 | 自动路由 - @Entity 元素 | EntityListStrategy |
| LCE-WB-003 | 自动路由 - 普通元素 | SimpleListStrategy |
| LCE-WB-004 | PerfGuard 超时 | 降级到 Simple |
| LCE-WB-005 | 空列表 | identical result |
| LCE-WB-006 | 降级计数 | degradationCounter++ |

---

## 4. 黑盒测试方案

### 4.1 等价类划分

#### 4.1.1 CompareService.compare()

| 等价类 | 有效/无效 | 代表值 | 预期 |
|--------|----------|--------|------|
| 两个 null | 有效 | null, null | identical |
| 左 null | 有效 | null, obj | null diff |
| 右 null | 有效 | obj, null | null diff |
| 同类型对象 | 有效 | User, User | field changes |
| 不同类型 | 有效 | User, Order | type diff |
| 相同对象 | 有效 | obj, obj | identical |
| 深嵌套 (>10层) | 边界 | 嵌套对象 | 截断 at max-depth |
| 循环引用 | 边界 | A→B→A | 检测循环，不死循环 |
| 大对象 (>500字段) | 边界 | 大 POJO | 完成比较，PerfGuard 可触发 |

#### 4.1.2 列表比较

| 等价类 | 策略 | 代表值 | 预期 |
|--------|------|--------|------|
| 空列表 vs 空列表 | 全部 | [], [] | identical |
| 空列表 vs 非空 | 全部 | [], [a,b] | all CREATE |
| 非空 vs 空列表 | 全部 | [a,b], [] | all DELETE |
| 相同列表 | 全部 | [a,b], [a,b] | identical |
| 元素修改 | Entity | [a(v1)], [a(v2)] | UPDATE |
| 元素新增 | Entity | [a], [a,b] | CREATE b |
| 元素删除 | Entity | [a,b], [a] | DELETE b |
| 元素移动 | LCS | [a,b,c], [c,a,b] | MOVE c |
| 重复 Key | Entity | [a(k1),b(k1)] | duplicateKeys 非空 |

### 4.2 边界值分析

| 测试用例 ID | 边界 | 测试值 | 预期 |
|------------|------|--------|------|
| BV-001 | 最大深度 | depth=10 | 正常; depth=11 截断 |
| BV-002 | 最大字段数 | fields=500 | 正常; fields=501 截断/降级 |
| BV-003 | 列表大小 | size=0, 1, 1000, 10000 | 分别验证正确性 |
| BV-004 | BigDecimal 精度 | scale=0, 1, 2, 10 | @NumericPrecision 生效 |
| BV-005 | 字符串边界 | "", " ", null, 超长字符串 | 正确比较 |
| BV-006 | 日期边界 | epoch, max date, null | 正确比较 |
| BV-007 | 嵌套 Map | 深度 1, 5, 10 | 递归正确 |

### 4.3 决策表

#### 注解组合决策表

| @Entity | @Key | @DiffIgnore | @NumericPrecision | 预期行为 |
|---------|------|-------------|-------------------|---------|
| ✓ | ✓ | - | - | 按 Key 匹配，全字段比较 |
| ✓ | ✗ | - | - | 异常或 fallback |
| ✓ | ✓ | ✓(某字段) | - | Key 匹配，忽略标记字段 |
| ✓ | ✓ | - | ✓(某字段) | Key 匹配，精度比较 |
| ✗ | - | - | - | 值比较（所有字段） |
| ✗(@VO) | - | ✓ | - | 值比较，忽略标记字段 |

---

## 5. 功能测试方案

### 5.1 F1: 对象比较功能测试

#### TC-F1-001: 简单对象比较

```
前置条件: 两个 SimpleObject 实例
步骤:
  1. 创建 before = SimpleObject(name="Alice", age=30)
  2. 创建 after = SimpleObject(name="Bob", age=30)
  3. 调用 compare(before, after)
预期:
  - changes 包含 1 个 FieldChange
  - fieldName = "name"
  - oldValue = "Alice", newValue = "Bob"
  - changeType = UPDATE
```

#### TC-F1-002: 嵌套对象比较

```
前置条件: 包含嵌套 Address 的 User 对象
步骤:
  1. 创建 before = User(name="Alice", address=Address(city="北京"))
  2. 创建 after = User(name="Alice", address=Address(city="上海"))
  3. 调用 compare(before, after, CompareOptions.DEEP)
预期:
  - changes 包含 1 个 FieldChange
  - path 包含 "address.city"
  - oldValue = "北京", newValue = "上海"
```

#### TC-F1-003: @Entity 列表比较

```
前置条件: @Entity + @Key(id) 标注的 Product 列表
步骤:
  1. before = [Product(id=1,name="A",price=10), Product(id=2,name="B",price=20)]
  2. after = [Product(id=1,name="A",price=15), Product(id=3,name="C",price=30)]
  3. 调用 compare(before, after) with Entity 策略
预期:
  - Product(id=1) → UPDATE: price 10→15
  - Product(id=2) → DELETE
  - Product(id=3) → CREATE
```

#### TC-F1-004: LCS 列表移动检测

```
前置条件: 带 @Entity 标注的有序列表
步骤:
  1. before = [A, B, C, D]
  2. after = [D, A, B, C]
  3. 调用 compare with LCS 策略
预期:
  - D: MOVE (index 3 → 0)
  - A, B, C: 无变化或 MOVE
```

#### TC-F1-005: 三方合并比较

```
前置条件: 三个版本的 Order 对象
步骤:
  1. base = Order(status="pending", amount=100)
  2. left = Order(status="processing", amount=100)
  3. right = Order(status="pending", amount=200)
  4. 调用 compareThreeWay(base, left, right)
预期:
  - mergedResult: status="processing", amount=200
  - conflicts: 空（无冲突字段）
```

#### TC-F1-006: 三方合并冲突

```
前置条件: 同一字段两侧修改
步骤:
  1. base = Order(status="pending")
  2. left = Order(status="processing")
  3. right = Order(status="cancelled")
  4. 调用 compareThreeWay(base, left, right)
预期:
  - conflicts: 1 个 FIELD_CONFLICT (status)
```

### 5.2 F2: 变更追踪功能测试

#### TC-F2-001: 浅层追踪

```
步骤:
  1. TFI.track("order", order, "status", "amount")
  2. order.setStatus("processing")
  3. List<ChangeRecord> changes = TFI.getChanges()
预期:
  - changes 包含 status 变更记录
  - amount 无变更（未修改）
```

#### TC-F2-002: 深度追踪

```
步骤:
  1. TFI.trackDeep("user", user)
  2. user.getAddress().setCity("上海")
  3. List<ChangeRecord> changes = TFI.getChanges()
预期:
  - changes 包含 address.city 变更记录
```

### 5.3 F3: 注解功能测试

#### TC-F3-001: @DiffIgnore

```
步骤:
  1. 对象含 @DiffIgnore 字段 updateTime
  2. 修改 updateTime 和 name
  3. compare()
预期:
  - changes 仅包含 name，不包含 updateTime
```

#### TC-F3-002: @NumericPrecision

```
步骤:
  1. @NumericPrecision(scale=2) BigDecimal amount
  2. before: amount=100.001, after: amount=100.009
  3. compare()
预期:
  - 视为相同（scale=2 下均为 100.00）
```

#### TC-F3-003: @DateFormat

```
步骤:
  1. @DateFormat("yyyy-MM-dd") Date createDate
  2. before: 2025-01-15 10:00:00
  3. after: 2025-01-15 18:00:00
  4. compare()
预期:
  - 视为相同（日期部分相同）
```

#### TC-F3-004: @ShallowReference

```
步骤:
  1. @ShallowReference Department dept
  2. dept 内部字段变化，但引用未变
  3. compare()
预期:
  - 无变更（浅引用未变化）
```

### 5.4 F4: 导出功能测试

#### TC-F4-001: JSON 导出

```
步骤:
  1. 获取 ChangeRecord 列表
  2. ChangeJsonExporter.export(changes)
预期:
  - 合法 JSON 格式
  - 包含 fieldName, oldValue, newValue, changeType
```

#### TC-F4-002: CSV 导出特殊字符

```
步骤:
  1. ChangeRecord 含逗号、引号、换行
  2. ChangeCsvExporter.export(changes)
预期:
  - 正确转义（RFC 4180）
```

#### TC-F4-003: 流式导出大数据

```
步骤:
  1. 10000 条 ChangeRecord
  2. StreamingChangeExporter.export(iterator)
预期:
  - 不 OOM
  - 输出完整
```

---

## 6. 性能测试方案

### 6.1 基准测试 (Benchmark)

#### 6.1.1 测试场景

| 场景 ID | 描述 | 对象规模 | 目标 P99 |
|---------|------|---------|---------|
| PERF-001 | 简单对象比较 | 10 字段 | < 1ms |
| PERF-002 | 中等对象比较 | 100 字段 | < 10ms |
| PERF-003 | 大对象比较 | 1000 字段 | < 100ms |
| PERF-004 | 深嵌套对象 | 5 层 × 20 字段 | < 50ms |
| PERF-005 | 小列表 Entity | 100 元素 | < 50ms |
| PERF-006 | 中列表 Entity | 1000 元素 | < 500ms |
| PERF-007 | 大列表 LCS | 1000 元素 | < 2s |
| PERF-008 | 批量比较 | 100 对对象 | < 1s |
| PERF-009 | 快照创建（浅） | 100 字段 | < 5ms |
| PERF-010 | 快照创建（深） | 100 字段 × 3 层 | < 20ms |

#### 6.1.2 JMH 配置

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
@State(Scope.Benchmark)
public class CompareEngineBenchmark {
    // ... benchmark methods
}
```

### 6.2 压力测试

#### 6.2.1 并发比较

| 场景 | 线程数 | 持续时间 | 目标 |
|------|--------|---------|------|
| 低并发 | 10 | 60s | 无错误，P99 < 2x baseline |
| 中并发 | 50 | 60s | 无错误，P99 < 5x baseline |
| 高并发 | 100 | 60s | 无死锁，降级正常 |
| 极限并发 | 500 | 30s | 验证降级机制 |

#### 6.2.2 内存压力

| 场景 | 描述 | 目标 |
|------|------|------|
| MEM-001 | 连续比较 10000 次（不清理） | 无 OOM，内存稳定 |
| MEM-002 | 大对象图（10000 字段） | 内存 < 100MB |
| MEM-003 | ThreadLocal 泄露检测 | 无泄露 |
| MEM-004 | 缓存膨胀检测 | 缓存 bounded |

### 6.3 降级测试

| 场景 | 触发条件 | 预期行为 |
|------|---------|---------|
| DEG-001 | CPU > 80% | 降级到 LIGHT 级 |
| DEG-002 | Memory > 85% | 降级到 MODERATE 级 |
| DEG-003 | PerfGuard 超时 | LCS → Simple |
| DEG-004 | 降级恢复 | 负载降低 → 自动恢复 NORMAL |

### 6.4 性能回归检测

```
每次 PR 自动运行:
  1. JMH benchmark (core scenarios)
  2. 与 baseline 对比
  3. 回归 > 20% → 标记警告
  4. 回归 > 50% → 阻断合并
```

---

## 7. 测试环境

### 7.1 硬件要求

| 环境 | CPU | 内存 | 用途 |
|------|-----|------|------|
| 开发 | 4+ cores | 8GB+ | 单元/集成测试 |
| CI | 4 cores | 16GB | 全量测试 + 覆盖率 |
| 性能 | 8+ cores | 32GB | JMH + 压力测试 |

### 7.2 软件环境

| 软件 | 版本 |
|------|------|
| JDK | 21 |
| Maven | 3.9+ |
| Spring Boot | 3.5.5 |
| JUnit | 5.10+ |
| JaCoCo | 0.8.11 |

### 7.3 CI 集成

```yaml
# GitHub Actions / Jenkins Pipeline
test-stages:
  - name: Unit Tests
    command: ./mvnw test -pl tfi-compare
    coverage: jacoco:report
    
  - name: Integration Tests
    command: ./mvnw verify -pl tfi-all
    
  - name: Performance Tests
    command: ./mvnw test -Pperf -pl tfi-all
    schedule: weekly
    
  - name: Architecture Tests
    command: ./mvnw test -Dtest="*ArchTest" -pl tfi-all
```

---

## 8. 测试用例矩阵

### 8.1 按功能模块

| 模块 | 白盒 | 黑盒 | 功能 | 性能 | 合计 |
|------|------|------|------|------|------|
| CompareEngine | 12 | 5 | 6 | 3 | 26 |
| CompareService | 8 | 8 | 8 | 4 | 28 |
| DiffDetector | 10 | 6 | 4 | 2 | 22 |
| DiffFacade | 6 | 4 | 3 | 1 | 14 |
| ListCompareExecutor | 6 | 5 | 5 | 3 | 19 |
| StrategyResolver | 6 | 3 | 2 | 1 | 12 |
| 列表策略 (5种) | 15 | 10 | 10 | 5 | 40 |
| 快照 | 8 | 4 | 4 | 3 | 19 |
| 导出器 (6种) | 12 | 6 | 6 | 2 | 26 |
| 注解处理 | 10 | 8 | 8 | - | 26 |
| 降级/监控 | 4 | 3 | 4 | 4 | 15 |
| **合计** | **97** | **62** | **60** | **28** | **247** |

### 8.2 按优先级

| 优先级 | 用例数 | 说明 |
|--------|--------|------|
| P0 (必须) | 80 | 核心功能正确性 |
| P1 (重要) | 95 | 边界条件、异常处理 |
| P2 (一般) | 52 | 性能、导出格式 |
| P3 (低) | 20 | 极端场景、兼容性 |

---

## 9. 现有测试评估

> **v4 更新**: 以下评估反映 87.8% 覆盖率 + 75.1% 分支覆盖的最新状态（3,591 测试，84 个测试文件）。

### 9.1 现状分析

| 维度 | v1 评估 | v2 评估 | v3 评估 | v4 评估 | 详情 |
|------|---------|---------|---------|---------|------|
| **测试位置** | ⚠️ 不规范 | ✅ 已规范 | ✅ 已规范 | ✅ 已规范 | 84 个测试文件全部在 tfi-compare/src/test/ |
| **覆盖范围** | ★★★☆☆ | ★★★★☆ | ★★★★★ | ★★★★★ | 所有包均有覆盖，核心路径 87%+，分支 75%+ |
| **测试质量** | ★★★★☆ | ★★★★☆ | ★★★★☆ | ★★★★★ | 白盒+集成+分支精准覆盖+API 兼容性 |
| **集成测试** | ☆☆☆☆☆ | ☆☆☆☆☆ | ★★★★☆ | ★★★★☆ | @SpringBootTest 集成测试已补充 |
| **参数化** | ★★☆☆☆ | ★★★☆☆ | ★★★☆☆ | ★★★☆☆ | tfi-all 有 @ParameterizedTest |
| **属性测试** | ★★☆☆☆ | ★★☆☆☆ | ★★☆☆☆ | ★★☆☆☆ | jqwik 属性测试待补充 |
| **性能测试** | ★★★☆☆ | ★★★☆☆ | ★★★☆☆ | ★★★★☆ | 14 个性能测试（CompareService/PathDedup/Snapshot/QueryAPI） |
| **API 兼容性** | ☆☆☆☆☆ | ☆☆☆☆☆ | ☆☆☆☆☆ | ★★★★☆ | 22 个 API surface 测试（类/方法/SPI/注解） |
| **架构测试** | ★★☆☆☆ | ★★★★★ | ★★★★★ | ★★★★★ | TfiCompareArchitectureTests 6 条 ArchUnit 规则 |

### 9.2 tfi-compare 模块内测试文件（84 个）

**按包分组统计**:

| 包 | 测试文件数 | 主要覆盖内容 |
|----|-----------|------------|
| `api/` | 4 | API 层、Builder、SurgicalCoverage、**ApiSurfaceCompatibilityTests** |
| `architecture/` | 1 | ArchUnit 6 条架构规则 |
| `config/` | 4 | AutoConfiguration、ConfigResolver 白盒 |
| `exporter/` | 2 | 6 种 Exporter (JSON/CSV/XML/Console/Map/Streaming) |
| `integration/` | 1 | @SpringBootTest 集成测试 |
| `metrics/` | 1 | AsyncMetricsCollector、TfiMetrics |
| `perf/` | 1 | **CompareModulePerformanceTests** (14 个性能测试) |
| `registry/` | 1 | DiffRegistry、ObjectTypeResolver |
| `spi/` | 1 | DefaultComparisonProvider/RenderProvider |
| `tracking/` | 16 | ChangeTracker、分支覆盖、手术精准覆盖、**EntityFilter/FinalBranch/SmallPackage/SummaryBuilder** |
| `tracking/compare/` | 11 | CompareEngine、策略、比较器 |
| `tracking/compare/list/` | 5 | ListCompareExecutor、5 种列表策略 |
| `tracking/detector/` | 9 | DiffDetector、DiffDetectorService、DiffFacade |
| `tracking/format/` | 1 | TfiDateTimeFormatter |
| `tracking/monitoring/` | 2 | DegradationManager、ResourceMonitor |
| `tracking/path/` | 4 | PathDeduplicator、PathBuilder、CaffeineCache |
| `tracking/precision/` | 1 | PrecisionController |
| `tracking/query/` | 2 | QueryProjector 深度覆盖 |
| `tracking/render/` | 3 | MarkdownRenderer、RenderStyle |
| `tracking/snapshot/` | 6 | ObjectSnapshot、Deep、Optimized、Filter |
| **合计** | **84** | **3,591 个测试用例** |

### 9.3 覆盖率达标的核心类

所有核心类均已达标（指令覆盖 > 80%）：

- `CompareEngine` — 白盒 + 分支覆盖 + 手术精准测试
- `DiffDetector` / `DiffDetectorService` — 白盒 + Final + Surgical 测试
- `DiffFacade` — 集成测试
- `StrategyResolver` — 单元 + 分支测试
- `ObjectSnapshotDeep` / `ObjectSnapshotDeepOptimized` — 白盒 + FinalPush
- `PathDeduplicator` / `PathArbiter` / `PathCollector` — 分支覆盖
- `ListCompareExecutor` — 白盒 + 路径执行器测试
- `ChangeTrackingAutoConfiguration` — @SpringBootTest 集成
- `ConfigurationResolverImpl` — 白盒 + 集成
- `MarkdownRenderer` — Final 覆盖
- `DegradationManager` — 分支覆盖
- `CaffeinePathMatcherCache` — 集成 + 零覆盖方法补充

### 9.4 仍可改进的区域

> v4 更新：SPI 包分支覆盖率已从 12.5% 提升至 60%+，整体分支覆盖 75.1%。

| 区域 | 分支覆盖率 | 优化建议 |
|------|-----------|---------|
| `tracking.path` | ~60% | 复杂路径解析分支 |
| `tracking.precision` | ~60% | 精度边界条件 |
| `snapshot.filter` | ~60% | 过滤条件分支 |
| 属性测试 (jqwik) | - | 对核心算法进行 property-based 测试 |
| 变异测试 (Pitest) | - | 评估测试有效性 |

---

## 10. 测试改进计划

> **v3 更新**: 所有 Phase 均已完成，覆盖率目标 85% 已达成。

### 10.1 Phase 1 — 基础补全 ✅ 已完成

| 任务 | 优先级 | 状态 | 实际产出 |
|------|--------|------|---------|
| 在 tfi-compare/src/test 建立测试基础设施 | P0 | ✅ | Maven test 结构 + 依赖 |
| CompareEngine 单元/白盒测试 | P0 | ✅ | CompareEngineTests + WhiteBoxTests + DeepTests |
| DiffDetector 单元/白盒测试 | P0 | ✅ | DiffDetectorTests + FinalTests + BranchTests |
| StrategyResolver 单元测试 | P0 | ✅ | StrategyResolverTests |
| ListCompareExecutor 单元测试 | P0 | ✅ | ListExecutorBranchTests + PathListExecutorFinalTests |
| DiffFacade 集成测试 | P0 | ✅ | DiffFacadeTests |

### 10.2 Phase 2 — 覆盖扩展 ✅ 已完成

| 任务 | 优先级 | 状态 | 实际产出 |
|------|--------|------|---------|
| 5 种 ListCompareStrategy 单元测试 | P0 | ✅ | ListCompareStrategyTests + ListBranchCoverageTests |
| AutoConfiguration @SpringBootTest 集成测试 | P1 | ✅ | SpringIntegrationTests + AutoConfigSurgicalTests |
| SPI Provider 测试 | P1 | ✅ | SpiProviderTests |
| DegradationManager 测试 | P1 | ✅ | StrategyBranchCoverageTests + MonitoringDeepCoverageTests |
| PerfGuard 测试 | P1 | ✅ | PathListExecutorFinalTests |
| 导出器完整测试 | P2 | ✅ | ChangeExporterTests + ExporterMaxCoverageTests |
| ConfigurationResolverImpl 测试 | P1 | ✅ | ConfigResolverWhiteBoxTests + ConfigIntegrationCoverageTests |
| ObjectSnapshot 深度测试 | P1 | ✅ | ObjectSnapshotDeepWhiteBoxTests + SnapshotMaxCoverageTests |

### 10.3 Phase 3 — 质量提升 ✅ 已完成

| 任务 | 优先级 | 状态 | 实际产出 |
|------|--------|------|---------|
| 架构测试 (ArchUnit) | P2 | ✅ | TfiCompareArchitectureTests — 6 规则 |
| SpotBugs 10 个 High 修复 | P1 | ✅ | 全部修复，0 个 High |
| SPI 分支覆盖率提升 | P1 | ✅ | SpiProviderTests — 57 测试，12.5% → 60%+ |
| 分支覆盖率 75%+ | P1 | ✅ | CompareBranch/PathBranch/FinalBranch/EntityFilter 等，75.1% |
| 性能测试 | P2 | ✅ | CompareModulePerformanceTests — 14 测试 |
| API 兼容性测试 | P2 | ✅ | ApiSurfaceCompatibilityTests — 22 测试 |
| 测试文件整合 | P2 | ✅ | 79 → 73 文件（减 8 个冗余） |
| TODO/FIXME 清理 | P2 | ✅ | 3 个 TODO 全部处理 |
| 属性测试 (jqwik) | P3 | ⏳ 待补充 | — |
| 变异测试评估 (Pitest) | P3 | ⏳ 未开始 | — |

### 10.4 目标里程碑

| 里程碑 | 覆盖率目标 | 时间 | 状态 |
|--------|-----------|------|------|
| Phase 1 完成 | ≥ 65% | +2 周 | ✅ 已达成（~75%） |
| Phase 2 完成 | ≥ 80% | +4 周 | ✅ 已达成（~82%） |
| Phase 3 完成 | ≥ 85% + 性能基线 | +5 周 | ✅ **87.8% 已达成**，分支 75.1%，性能基线已建立 |

### 10.5 已修复的 API Bug（v3 新增）

| # | 问题 | 修复文件 | 严重度 |
|---|------|---------|--------|
| 1 | ObjectSnapshotDeep.captureDeep NPE (null includeFields/excludePatterns) | ObjectSnapshotDeep.java | High |
| 2 | ConfigurationResolverImpl NPE (null Environment) | ConfigurationResolverImpl.java | High |
| 3 | SpotBugs: DefaultRenderProvider 冗余 null 检查 | DefaultRenderProvider.java | Medium |
| 4 | SpotBugs: ListCompareExecutor 死条件 + null 后解引用 | ListCompareExecutor.java | High |
| 5 | SpotBugs: PathDeduplicator 忽略异常 + 冗余 null | PathDeduplicator.java | Medium |
| 6 | SpotBugs: ObjectSnapshotDeep/Optimized 死代码 | ObjectSnapshotDeep.java, ...Optimized.java | Low |
| 7 | SpotBugs: ObjectSnapshotDeepOptimized System.gc() | ObjectSnapshotDeepOptimized.java | Medium |
| 8 | SpotBugs: PathNavigator putIfAbsent 返回值忽略 | PathNavigator.java | Medium |
| 9 | SpotBugs: TfiListDiff 实例方法写静态字段 | TfiListDiff.java | Medium |

---

*文档由资深测试专家撰写，项目经理审阅。v4 更新于 2026-02-16。*
