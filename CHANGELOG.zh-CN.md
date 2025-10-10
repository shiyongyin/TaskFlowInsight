# 更新日志

本文件记录项目的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
项目遵循 [语义化版本控制](https://semver.org/lang/zh-CN/)。

## [3.1.x] - 待发布

### 新增
- 引用变更语义：`FieldChange` 现在包含 `referenceChange` 和 `ReferenceDetail`，用于表示浅引用切换（包括 null 转换）。引擎和列表策略在构建时标记此标志。
- 性能门控（验证）：故障安全集成测试 `ReferenceSemanticPerfGateIT`，设置软/硬阈值（默认 1000ns/操作）。参见 `docs/performance/reference-change-baseline.md`。
- `ArrayCompareStrategy`：为 Java 数组添加容器事件支持（ContainerType.ARRAY），支持数组差异的 `getContainerChanges()` 和 `groupByContainerOperation()`。
- JMH 基准测试（bench profile）用于查询辅助 API。参见 docs/performance/JMH-BENCHMARKS.md。

### 变更
- `CompareResult`：弃用 `groupByContainerOperationAsString()`；请改用类型安全的 `groupByContainerOperation()`。迁移指南位于 docs/api/QUERY-HELPER-MIGRATION-3.2.0.md。
- 渲染：`ChangeAdapters` 不再使用启发式方法派生 reference_change 事件；它严格读取 `FieldChange.isReferenceChange()` 和 `ReferenceDetail`。

### 注意事项/破坏性变更
- 渲染适配器应读取 `FieldChange.referenceChange` 和 `ReferenceDetail` 以获取引用变更。已移除启发式检测以避免误报。

### 修复
- 移除导致编译失败的重复 `groupByContainerOperationAsString()` 定义。

### 计划 (3.2.0)
- 完全移除 `groupByContainerOperationAsString()`。请在升级前迁移到类型安全 API。

---

## [3.0.0] - 2025-10-10

### 🎉 重大版本发布 - 架构重构

这是一次重大架构升级，引入统一门面模式、完整注解系统和高级比对策略。**13 个模块化提交**，涉及 **542 个文件变更**。

#### 新增功能

##### 🏗️ **统一门面模式** (P0-T0)
- **`DiffFacade`**: 统一差异检测入口，支持自动降级
  - 优先级：编程式服务 → Spring Bean → 静态 DiffDetector
  - Spring/非Spring 环境无缝切换
- **`SnapshotProviders`**: 快照捕获抽象层
  - `DirectSnapshotProvider`: 直接实现（默认）
  - `FacadeSnapshotProvider`: 门面实现（可选）
  - 选择优先级：Spring Bean → 系统属性 → 默认实现
- **`ChangeTracker`**: 集成 DiffFacade 和 SnapshotProviders

##### 📝 **完整注解系统** (P0-T1)
- **类型系统注解**:
  - `@Entity`: 标记具有唯一标识的对象（用于列表匹配）
  - `@ValueObject`: 标记值对象（按内容比对）
  - `@Key`: 标记实体的唯一标识字段
- **比对注解**:
  - `@NumericPrecision(scale, tolerance)`: 控制数值比对精度
  - `@DateFormat(pattern)`: 日期格式化输出
  - `@CustomComparator(class)`: 字段级自定义比对器
- **过滤注解**:
  - `@DiffIgnore` / `@DiffInclude`: 字段级包含/排除控制
  - `@ShallowReference`: 浅引用标记（仅比对 ID）
  - `@IgnoreDeclaredProperties` / `@IgnoreInheritedProperties`: 类级过滤

##### 🔍 **高级比对策略** (P1-T3, P1-T4)
- **`EntityListStrategy`**: 基于 `@Key` 注解的实体匹配 + 移动检测
  - 输出格式：`items[0→2]: MOVED`, `items[+3]: ADDED`, `items[-1]: REMOVED`
- **`NumericCompareStrategy`**: 数值精度比对（BigDecimal/Float/Double）
  - 支持字段级 `@NumericPrecision` 注解
  - 可配置容差：`tfi.compare.numeric.float-tolerance: 1e-12`
- **`EnhancedDateCompareStrategy`**: 时区感知日期比对
  - 格式控制：`tfi.compare.datetime.default-format: "yyyy-MM-dd HH:mm:ss"`
  - 容差：`tfi.compare.datetime.tolerance-ms: 0`
- **`MapCompareStrategy`**: 深度 Map 比对，支持嵌套对象
- **`SetCompareStrategy`**: Set 元素变更检测
- **`ArrayCompareStrategy`**: 数组元素比对，支持容器事件

##### 🎨 **TFI API 扩展** (P0-T2)
- **门面方法**:
  - `TFI.compare(oldObj, newObj)`: 零配置比对
  - `TFI.render(result, style)`: Markdown 渲染（simple/standard/detailed）
  - `TFI.comparator()`: 流式构建器，支持高级配置
- **`ComparatorBuilder`**: 链式配置构建器
  - `withMaxDepth(int)`, `ignoring(String...)`, `withTemplate(Template)`
- **`ComparisonTemplate`**: 预定义比对模板
  - `AUDIT`: 审计模式（所有字段，最大深度）
  - `DEBUG`: 调试模式（详细输出）
  - `PERFORMANCE`: 性能模式（浅层比对）
- **`TfiListDiff`**: 列表专用差异门面，提供便捷方法

##### 🛤️ **路径系统** (P1-T6)
- **`PathDeduplicator`**: 冗余路径消除
  - 问题：`order.items` (UPDATED) + `order.items[0].status` (UPDATED)
  - 解决：仅保留 `order.items[0].status`（细粒度路径）
  - 快速路径 (<800 变更): ~1ms, 完整去重 (>800): ~10ms
- **`PathArbiter`**: 确定性路径选择与优先级计算
- **`PathBuilder`**: 路径构建工具
- **`PathCollector`**: 路径收集与过滤

##### 🎭 **渲染与导出** (P1-T7)
- **`MarkdownRenderer`**: Markdown 差异报告生成器
  - 样式：`simple`（仅摘要），`standard`（推荐），`detailed`（完整信息）
- **`MaskRuleMatcher`**: 敏感信息脱敏
  - 默认模式：`password`, `secret`, `token`, `internal*`
- **`ChangeReportRenderer`**: 结构化变更报告

##### 📊 **监控与降级** (P2-T7)
- **`DegradationManager`**: 自适应降级系统（可选，默认禁用）
  - 级别：NORMAL → SKIP_DEEP → SIMPLE → SUMMARY → DISABLED
  - 触发条件：内存 >80%, CPU >70%, 慢操作 >30%
  - 迟滞机制：`min-level-change-duration: 30s` 防止抖动
- **`PerfGuard`**: 超时保护（默认：5000ms）
- **`ResourceMonitor`**: 系统资源监控

##### ⚙️ **配置系统** (VIP-003)
- **`ConfigurationResolver`**: 五层优先级解析
  - 优先级：方法注解 → 系统属性 → 环境变量 → YAML → 默认值
- **`ConfigMigrationMapper`**: 旧配置键自动迁移
  - `taskflow.*` → `tfi.*` 自动映射
- **`TfiFeatureFlags`**: 特性开关管理
  - `tfi.api.facade.enabled: true`（控制门面 API）
- **新增配置类别**:
  - 缓存：`tfi.change-tracking.diff.cache.strategy.*`（10000 条目，5分钟 TTL）
  - 性能：`tfi.change-tracking.diff.perf.timeout-ms: 5000`
  - 数值：`tfi.compare.numeric.*`（容差，比对模式）
  - 日期时间：`tfi.compare.datetime.*`（格式，时区，容差）
  - 监控：`tfi.compare.monitoring.slow-operation-ms: 200`

##### 🧪 **全面测试覆盖** (350+ 测试类)
- **单元测试**:
  - `AnnotationTests`, `CustomComparatorAnnotationTests`
  - `NumericCompareStrategyTest`, `EnhancedDateCompareStrategyTest`
  - `PathDeduplicatorTest`, `PathArbiterTest`
- **集成测试**:
  - `DiffFacadeIntegrationTest`（9 个场景）
  - `SnapshotProviderSwitchTest`（12 个场景）
  - `EntityValueObjectEndToEndTests`
  - `PathSyntaxEndToEndTest`
  - `ContainerEventsGoldenIntegrationTests`
- **性能测试**:
  - `ConcurrencyBenchmarkTest`（100 线程）
  - `PathSyntaxPerformanceTest`
  - `TypeSystemPerformanceTests`
  - `EntityListStrategyPerformanceTest`
- **JMH 基准测试**:
  - `FilterBenchmarks`, `QueryApiBenchmarks`, `ReferenceChangeBenchmarks`

##### 📚 **文档体系**
- **顶层文档**:
  - `README.md`: 项目概览（反映 v3.0.0 特性）
  - `QUICKSTART.md`: 3 分钟快速入门
  - `EXAMPLES.md`: 11 个真实场景示例
  - `FAQ.md`: 常见问题解答
  - `TROUBLESHOOTING.md`: 问题诊断指南
  - `CHANGELOG.md`: 完整更新日志
- **内部文档**:
  - `tracking/docs/INDEX.md`: 文档索引
  - `tracking/docs/QuickStart.md`: 快速入门指南
  - `tracking/docs/Configuration.md`: 配置指南
  - `tracking/docs/Performance-BestPractices.md`: 性能优化最佳实践
- **示例代码**:
  - `Demo01_BasicTypes` 到 `Demo07_MapCollectionEntities`
  - `ChangeTrackingComprehensiveDemo`, `ChangeTrackingBestPracticeDemo`

#### 变更内容

##### 🔧 **核心组件**
- **`ChangeTracker`**: 集成 SnapshotProviders 和 DiffFacade
  - 第 120-121 行：`SnapshotProviders.get().captureBaseline()`
  - 第 244-256 行：`DiffFacade.diff(name, baseline, current)`
- **`DiffDetector`**: 降低圈复杂度
  - 提取方法：`buildStandardChangeRecord()`, `applySortingIfNeeded()`, `applyDedupIfNeeded()`
  - 统一键合并：`unionSortedKeys()`
  - 详细变更检测：`containsDetailedChanges()`
- **`CompareService`**: 可扩展策略系统
  - 通过 `CompareStrategy` 接口支持自定义策略
  - 基于对象类型的自动路由

##### ⚙️ **配置**
- **配置层次结构**: 5 级优先级解析
- **旧配置键迁移**: `taskflow.*` → `tfi.*` 自动映射
- **默认值**: 所有配置方法保持一致
- **新增配置类别**: 新增 178 个配置项

#### 问题修复

##### 🐛 **Bug 修复**
- **`DiffDetectorService`**: 缺失 `valueKind` 和 `valueType` 字段
  - 问题：`EnumChangeTrackingTest` 失败
  - 根本原因：第 149-158 行仅设置 oldValue/newValue
  - 修复：添加 `getValueKind()` 方法和字段填充逻辑（第 158-163, 390-417 行）
  - 验证：`valueKind` 正确设置为 "ENUM"

#### 向后兼容

##### ✅ **零破坏性变更**
- **API 兼容性**:
  - `TFI.track()` 方法签名不变
  - `TFI.getChanges()` 行为一致
  - 所有现有测试通过
- **配置兼容性**:
  - 旧配置键自动迁移（`taskflow.*` → `tfi.*`）
  - 默认值保持一致
  - 新配置项可选
- **行为兼容性**:
  - 默认：`DirectSnapshotProvider` + 静态 `DiffDetector`
  - 门面模式自动降级（对用户透明）
  - 降级系统默认禁用

#### 性能指标

##### 📈 **基准测试**
| 操作 | 耗时 | 内存 | 说明 |
|-----|-----|------|-----|
| 浅层快照 | ~10ms | +2MB | 仅标量字段 |
| 深层快照（深度=10） | ~50ms | +10MB | 嵌套对象+集合 |
| 简单对象比对 | ~5ms | +1MB | <10 个字段 |
| 列表比对（1000 元素） | ~100ms | +20MB | 实体匹配 |
| 路径去重（<800） | ~1ms | +0.5MB | 快速路径 |
| 路径去重（>800） | ~10ms | +2MB | 完整去重 |

**总体影响**: 内存 +5%, CPU +3%（缓存开销）

#### 部署建议

##### 生产环境
```yaml
tfi:
  change-tracking:
    snapshot:
      max-depth: 5  # 推荐值（默认 10 可能过深）
    diff:
      cache:
        strategy.max-size: 10000
    degradation:
      enabled: false  # 除非高并发场景
```

##### 高并发场景 (>100 QPS)
```yaml
tfi:
  change-tracking:
    degradation:
      enabled: true
      memory-threshold: 0.75  # 75% 触发降级
      cpu-threshold: 0.65
```

#### 迁移指南

##### 对于现有用户
**无需任何操作** - v3.0.0 完全向后兼容：
- 所有现有 API 正常工作
- 旧配置键自动迁移
- 默认行为保持不变

##### 对于高级用户
选择性启用新特性：
```java
// 方式 1：通过系统属性切换快照提供器
System.setProperty("tfi.change-tracking.snapshot.provider", "facade");

// 方式 2：使用新门面 API
CompareResult result = TFI.compare(oldObj, newObj);
String markdown = TFI.render(result, "standard");

// 方式 3：使用流式构建器
CompareResult result = TFI.comparator()
    .withMaxDepth(5)
    .ignoring("id", "createTime")
    .compare(obj1, obj2);
```

#### 相关问题与 PR
- PR #4: Feature/major refactoring（已于 2025-10-10 合并）
- 提交：`0f67180` 到 `a8f8ec2`（13 个模块化提交）
- 文件变更：542 个
- 测试覆盖率：>85%

#### 贡献者
- @shiyongyin（架构设计、实现、测试、文档）

---

**注**: 此版本代表 2 个月的密集重构工作，为 v3.1.0 及后续版本的增强奠定了坚实基础。
