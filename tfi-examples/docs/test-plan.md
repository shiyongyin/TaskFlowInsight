# tfi-examples 模块 — 测试方案

> **作者**: 资深测试专家  
> **日期**: 2026-02-16  
> **版本**: v1.0  
> **范围**: 仅 tfi-examples 模块  
> **整体项目测试方案**: [project-overview/test-plan.md](project-overview/test-plan.md)

---

## 目录

1. [测试现状](#1-测试现状)
2. [逐文件测试评审](#2-逐文件测试评审)
3. [测试缺陷清单](#3-测试缺陷清单)
4. [测试覆盖盲区](#4-测试覆盖盲区)
5. [白盒测试方案](#5-白盒测试方案)
6. [黑盒测试方案](#6-黑盒测试方案)
7. [功能测试用例](#7-功能测试用例)
8. [性能测试方案](#8-性能测试方案)
9. [JMH 基准测试评审](#9-jmh-基准测试评审)
10. [测试改进计划](#10-测试改进计划)

---

## 1. 测试现状

### 1.1 测试文件统计

| 类型 | 文件数 | 有效测试数 | 有断言 | 被 JUnit 执行 |
|------|:------:|:----------:|:------:|:-------------:|
| 单元测试 (@Test) | 10 | ~40 | 8 个 | 10 个 |
| 手动测试 (main) | 3 | 0 | — | **0 个** |
| 调试测试 | 2 | ~5 | 0 | 2 个 |
| **合计** | **15** | **~45** | **8** | **12** |

### 1.2 源文件 vs 测试文件对照

| 源文件 | 行数 | 有对应测试 | 测试质量 |
|--------|:----:|:----------:|:--------:|
| TaskFlowInsightApplication | 14 | ❌ | — |
| TaskFlowInsightDemo | 166 | ❌ | — |
| **DemoController** | 143 | **❌** | — |
| QuickStartChapter | 89 | ❌ | — |
| BusinessScenarioChapter | 106 | ❌ | — |
| AdvancedFeaturesChapter | 241 | ❌ | — |
| BestPracticesChapter | 126 | ❌ | — |
| AdvancedApiChapter | 176 | ❌ | — |
| ChangeTrackingChapter | 257 | ❌ | — |
| AsyncPropagationChapter | 65 | ❌ | — |
| Demo01_BasicTypes | 225 | ❌ | — |
| Demo02_DateTypes | 274 | ❌ | — |
| Demo03_CustomObjects | 255 | ✅ Demo03Scenario7 | ⭐⭐ |
| Demo04_Collections | 164 | ✅ Demo04*Test (2) | ⭐⭐⭐ |
| Demo05_CollectionEntities | 249 | ❌ | — |
| Demo06_SetCollectionEntities | 813 | ✅ Demo06*Test (5) | ⭐⭐⭐ |
| Demo07_MapCollectionEntities | 779 | ✅ Demo07OutputTest | ⭐ |
| **AsyncPropagationDemo** | 245 | **❌** | — |
| **EcommerceDemoService** | 131 | **❌** | — |
| AnnotationDemo | 52 | ❌ | — |
| TfiSwitchDemo | 87 | ❌ | — |

**覆盖率**: 24 个源文件中仅 4 个有对应测试 → **测试覆盖率约 17%**。

---

## 2. 逐文件测试评审

### 2.1 有效单元测试

| # | 文件 | 行数 | 测试数 | 断言质量 | 评价 |
|---|------|:----:|:------:|:--------:|:----:|
| 1 | TypeSystemDemoTest | 169 | 6 | ✅ 完整 | ⭐⭐⭐ |
| 2 | TestSetStrategyDirectly | 49 | 1 | ✅ 完整 | ⭐⭐⭐⭐ |
| 3 | Demo06SetCollectionEntitiesTest | 354 | 5 | ✅ 完整 | ⭐⭐⭐⭐ |
| 4 | Demo06FormatTest | 133 | 3 | ✅ 完整 | ⭐⭐⭐ |
| 5 | Demo06DuplicateKeyManualTest | 121 | 4 | ✅ 完整 | ⭐⭐⭐⭐ |
| 6 | Demo04FixedCollectionsTest | 154 | 3 | ✅ 完整 | ⭐⭐⭐ |
| 7 | Demo04CollectionsMapFixTest | 126 | 2 | ✅ 完整 | ⭐⭐⭐ |

### 2.2 弱断言测试

| # | 文件 | 行数 | 问题 |
|---|------|:----:|------|
| 8 | Demo03Scenario7SortingTest | 223 | 排序验证依赖 `System.out` 输出，无 `assertThat(changes).isSortedBy(...)` |
| 9 | BasicSetMapTestAfterFix | 81 | 用 `logger.warn` 替代 `assertTrue`，检测失败不导致测试失败 |

### 2.3 冒烟测试（仅验证不抛异常）

| # | 文件 | 行数 | 问题 |
|---|------|:----:|------|
| 10 | Demo06And07ExecutionTest | 43 | 重定向 System.out 但不检查内容；类名拼写错误 (`jDemo06And07...`) |
| 11 | Demo07OutputTest | 17 | 仅调用 `main()`，零验证 |
| 12 | DebugDetailedChanges | 60 | 诊断用途，无断言 |

### 2.4 伪测试（不被 JUnit 执行）

| # | 文件 | 行数 | 问题 |
|---|------|:----:|------|
| 13 | **Demo06QuickTest** | 93 | 仅 `main()` 方法，无 `@Test`，`mvn test` 不运行 |
| 14 | **FullFeatureVerificationTest** | 387 | 387 行验证逻辑用 `main()` 驱动，不参与自动化 |
| 15 | **ChangeTrackingDemo** | 95 | Demo 代码放在 test 目录，仅 `main()` |

---

## 3. 测试缺陷清单

| # | 缺陷 | 严重度 | 影响 |
|---|------|:------:|------|
| **T-01** | 3 个伪测试不被 JUnit 执行 (Demo06Quick, FullFeature, CTDemo) | **高** | 回归检测失效 |
| **T-02** | BasicSetMapTestAfterFix 无硬断言，失败不报错 | **高** | 假阳性 |
| **T-03** | Demo03Scenario7SortingTest 排序验证无断言 | 中 | 排序回归不可检测 |
| T-04 | Demo06And07ExecutionTest 类名拼写错误 (`jDemo06...`) | 低 | 规范问题 |
| T-05 | Demo07OutputTest 仅 17 行零验证 | 中 | 测试价值极低 |
| T-06 | DebugDetailedChanges 为调试代码，不应在 test 中 | 低 | 干扰测试报告 |

---

## 4. 测试覆盖盲区

### 4.1 完全未测试的高优先源文件

| 源文件 | 行数 | 风险 | 建议测试类型 |
|--------|:----:|:----:|-------------|
| **DemoController** | 143 | **高** | MockMvc 集成测试 |
| **AsyncPropagationDemo** | 245 | **高** | @SpringBootTest 并发测试 |
| **EcommerceDemoService** | 131 | **高** | 单元测试 + 并发安全 |
| TaskFlowInsightDemo | 166 | 中 | CLI 集成测试 |
| AnnotationDemo | 52 | 中 | @SpringBootTest |

### 4.2 未测试的关键场景

| 场景 | 风险 | 建议 |
|------|:----:|------|
| DemoController 4 个 REST 端点 | 高 | MockMvc + 状态码 + 响应体验证 |
| 异步上下文传播到子线程 | 高 | CountDownLatch + 多线程断言 |
| EcommerceDemoService 并发安全 | 高 | 并发 track + HashMap 竞态 |
| 7 个章节 run() 方法正常执行 | 中 | 每章节一个冒烟测试 |
| CLI 菜单参数解析 | 低 | 参数化测试 |

---

## 5. 白盒测试方案

### 5.1 DemoController 分支覆盖

```
/api/hello
├── name 为 null → 默认 "World"
├── name 有值 → 使用传入值
└── TFI.stage() 内部异常 → 优雅处理

/api/process
├── body 为 null → ?
├── body 含 orderId → 正常流程
└── body 缺 orderId → ?

/api/async
├── 线程池任务正常完成
├── 线程池任务抛异常
└── 上下文传播验证

/api/async-comparison
├── 正确方式 vs 错误方式对比
└── 日志输出验证
```

### 5.2 EcommerceDemoService 数据流

```
processOrder(orderId, amount)
  → validateOrder(orderId)       ← HashMap.get，非线程安全
    → checkInventory(orderId)    ← TFI.message
      → processPayment(amount)   ← new Random() 在循环中
        → return result
```

**测试要点**:

| 方法 | 正常路径 | 异常路径 |
|------|----------|----------|
| validateOrder | orderId 存在 | orderId 不存在 |
| checkInventory | 库存充足 | 库存不足 |
| processPayment | 支付成功 | 金额异常 |
| 并发调用 | — | HashMap 并发修改 |

### 5.3 章节 run() 方法

每个章节需验证:
1. 不抛未捕获异常
2. TFI.exportToConsole() 返回 true
3. Session 正确关闭（TFI.clear() 后无残留）

---

## 6. 黑盒测试方案

### 6.1 DemoController 端点测试

| 端点 | 方法 | 输入 | 预期 |
|------|------|------|------|
| `/api/hello` | GET | — | 200, 含 "Hello World" |
| `/api/hello?name=TFI` | GET | name=TFI | 200, 含 "Hello TFI" |
| `/api/process` | POST | `{"orderId":"1","amount":100}` | 200, 含流程结果 |
| `/api/process` | POST | `{}` | 200 或 400 |
| `/api/async` | POST | `{"task":"test"}` | 200, 含异步结果 |
| `/api/async-comparison` | POST | `{}` | 200, 含比较结果 |

### 6.2 CLI 参数测试

| 输入 | 预期行为 |
|------|----------|
| `1` | 运行第 1 章 |
| `6` | 运行第 6 章 |
| `7` | **当前: 无响应（未注册）** |
| `all` | 运行所有章节 |
| `0` | 无效输入处理 |
| `abc` | 无效输入处理 |

### 6.3 Demo 执行测试

| Demo | 测试方式 | 验证 |
|------|----------|------|
| Demo01_BasicTypes | 调用 main() | 无异常 + 输出含 "CompareResult" |
| Demo02_DateTypes | 调用 main() | 无异常 |
| Demo03_CustomObjects | 调用 main() | 无异常 |
| Demo04_Collections | 调用 main() | 无异常 |
| Demo05-07 | 调用 main() | 无异常 + 输出含比对结果 |

---

## 7. 功能测试用例

### 7.1 DemoController 功能测试

| ID | 用例 | 步骤 | 预期 | 优先级 |
|----|------|------|------|:------:|
| FT-01 | hello 默认参数 | GET /api/hello | 200 + "Hello World" | P0 |
| FT-02 | hello 自定义 | GET /api/hello?name=X | 200 + "Hello X" | P0 |
| FT-03 | process 正常 | POST /api/process | 200 + 非空 body | P0 |
| FT-04 | async 正常 | POST /api/async | 200 + 异步结果 | P0 |

### 7.2 章节执行功能测试

| ID | 用例 | 步骤 | 预期 | 优先级 |
|----|------|------|------|:------:|
| FT-05 | Ch1 快速入门 | `chapter.run()` | 无异常，export 成功 | P0 |
| FT-06 | Ch2 电商场景 | `chapter.run()` | 无异常，export 成功 | P0 |
| FT-07 | Ch3 高级特性 | `chapter.run()` | 无异常，export 成功 | P0 |
| FT-08 | Ch4 最佳实践 | `chapter.run()` | 无异常，export 成功 | P0 |
| FT-09 | Ch5 高级 API | `chapter.run()` | 无异常，export 成功 | P0 |
| FT-10 | Ch6 变更追踪 | `chapter.run()` | 无异常，export 成功 | P0 |
| FT-11 | Ch7 异步传播 | `chapter.run()` | 无异常，export 成功 | P1 |

### 7.3 Compare Demo 功能测试

| ID | 用例 | 预期 | 优先级 |
|----|------|------|:------:|
| FT-12 | Demo01 基本类型 | compare 返回非 null，变更检测正确 | P0 |
| FT-13 | Demo02 日期 | @DateFormat 容差生效 | P0 |
| FT-14 | Demo03 对象 | @Entity/@ValueObject 正确识别 | P0 |
| FT-15 | Demo04 集合 | List/Set/Map 差异正确 | P0 |
| FT-16 | Demo05 实体集合 | @Key 匹配正确 | P0 |

### 7.4 EcommerceDemoService 功能测试

| ID | 用例 | 预期 | 优先级 |
|----|------|------|:------:|
| FT-17 | 正常订单处理 | 返回成功结果 | P0 |
| FT-18 | 无效订单 ID | 优雅处理 | P1 |
| FT-19 | 并发订单处理 | 无 ConcurrentModificationException | P0 |

---

## 8. 性能测试方案

### 8.1 Demo 执行性能

| 测试项 | 目标 | 方法 |
|--------|------|------|
| 章节执行时间 | 各章节 < 5s | JUnit + @Timeout |
| Demo01-07 执行时间 | 各 Demo < 10s | JUnit + @Timeout |
| Spring Boot 启动时间 | < 10s | @SpringBootTest + StopWatch |
| REST 端点响应时间 | p99 < 500ms | MockMvc + StopWatch |

### 8.2 JMH 基准性能门禁

| 基准 | SLA | 回归阈值 |
|------|-----|:--------:|
| TFIRoutingBenchmark | 路由开销 < 100ns | < 5% |
| ProviderRegistryBenchmark (热) | < 50ns | < 5% |
| P1PerformanceBenchmark | ≤ 5% 延迟退化 | < 5% |
| MapSetLargeBenchmarks (10K) | < 50ms | < 10% |

---

## 9. JMH 基准测试评审

| 文件 | 行数 | 质量 | 问题 |
|------|:----:|:----:|------|
| TFIRoutingBenchmark | 44 | ⭐⭐⭐ | `@Benchmark` 内改 System Property |
| ProviderRegistryBenchmark | 41 | ⭐⭐⭐⭐ | 热/冷路径设计好 |
| P1PerformanceBenchmark | 377 | ⭐⭐⭐⭐⭐ | 全面专业 |
| P1MemoryBenchmark | 291 | ⭐⭐⭐⭐⭐ | GCProfiler 正确使用 |
| ReferenceChangeBenchmarks | 53 | ⭐⭐⭐ | Order 缺 @Entity |
| QueryApiBenchmarks | 111 | ⭐⭐⭐⭐ | 参数化规模 |
| MapSetLargeBenchmarks | 77 | ⭐⭐⭐⭐ | 大规模测试 |
| FilterBenchmarks | 218 | ⭐⭐⭐⭐ | 缓存命中率测试 |
| LargeObjectGenerator | 93 | ⭐⭐⭐⭐ | 数据生成器 |
| Runners (3 个) | 125 | ⭐⭐⭐ | forks(0) 降低隔离性 |
| bench/TypedViewBenchmark | 67 | ⭐⭐⭐⭐ | 参数化 |
| bench/EntityListDiffResultBench | 71 | ⭐⭐⭐⭐ | 基线对比 |

**JMH 综合评价**: **8/10** — 模块内质量最高的部分。

---

## 10. 测试改进计划

### Phase 1: 紧急修复（第 1 天）

| 任务 | 工时 | 说明 |
|------|:----:|------|
| 修复 3 个伪测试 | 2h | 转为 @Test 或移到 src/main |
| BasicSetMapTestAfterFix 加硬断言 | 1h | `assertTrue(foundSetChange)` |
| Demo03 排序测试加断言 | 1h | `assertThat(changes).isSortedBy(...)` |

### Phase 2: 核心补齐（第 1 周）

| 任务 | 工时 | 说明 |
|------|:----:|------|
| DemoController MockMvc 测试 | 4h | 4 端点 × (正常+异常) |
| EcommerceDemoService 单元测试 | 3h | 含并发安全验证 |
| AsyncPropagationDemo 测试 | 4h | 多线程上下文传播 |

### Phase 3: 覆盖扩展（第 2 周）

| 任务 | 工时 | 说明 |
|------|:----:|------|
| 7 章节冒烟测试 | 4h | 每章节 run() + export 验证 |
| Demo01-07 输出验证 | 6h | ApprovalTests 金标准 |
| CLI 参数测试 | 2h | 参数化测试 |

### Phase 4: 性能保障（第 3 周）

| 任务 | 工时 | 说明 |
|------|:----:|------|
| 执行超时门禁 | 2h | @Timeout 注解 |
| JMH 基准问题修复 | 2h | System Property + @Entity |
| 性能基线文档 | 2h | SLA 定义 + 回归阈值 |

### 覆盖率目标

| 阶段 | 目标 |
|------|------|
| 当前 | ~17% (4/24 文件有测试) |
| Phase 2 后 | ~35% (核心文件覆盖) |
| Phase 3 后 | **~55%** (章节 + Demo 覆盖) |
