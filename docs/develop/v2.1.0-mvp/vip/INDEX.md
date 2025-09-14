# TaskFlowInsight v2.1.0-MVP VIP合并文档索引

## 0. 执行参考（MVP）
- Phase 1 验收标准：见同目录《[OVERDESIGN-ASSESSMENT.md](OVERDESIGN-ASSESSMENT.md)》的“Phase 1 验收标准（MVP）”。
- MVP 最小配置集：见同目录《[OVERDESIGN-ASSESSMENT.md](OVERDESIGN-ASSESSMENT.md)》的“MVP 最小配置集（仅保留必要项）”。
- 目录清理条件：见同目录《[OVERDESIGN-ASSESSMENT.md](OVERDESIGN-ASSESSMENT.md)》的“目录清理条件（暂缓删除 gpt/opus）”。

## 1. 索引表

| Slug | GPT源 | OPUS源 | VIP合并稿 | 主要模块/包 | 备注 |
|------|-------|--------|-----------|------------|------|
| object-snapshot-deep | CARD-201 | M2M1-001 | VIP-001-ObjectSnapshotDeep.md | tracking.snapshot | 深度快照 |
| diff-detector | CARD-202, CARD-260 | M2M1-004 | VIP-002-DiffDetector.md | tracking.detector | 差异检测扩展 |
| change-tracker | CARD-203, CARD-210 | - | VIP-003-ChangeTracker.md | tracking | 变更追踪核心 |
| tfi-api | CARD-204, CARD-210 | - | VIP-004-TFI-API.md | api | TFI门面API |
| thread-context | CARD-220, CARD-221 | - | VIP-005-ThreadContext.md | context | 线程上下文管理 |
| output-format | CARD-230 | M2M1-031, M2M1-010 | VIP-006-OutputFormat.md | exporter | 输出格式化 |
| config-starter | CARD-240 | M2M1-040 | VIP-007-ConfigStarter.md | config | 配置与自动装配 |
| performance | CARD-250 | M2M1-061 | VIP-008-Performance.md | test.performance | 性能基准测试 |
| actuator-endpoint | - | M2M1-041 | VIP-009-ActuatorEndpoint.md | actuator | 管理端点 |
| collection-summary | - | M2M1-002 | VIP-010-CollectionSummary.md | tracking.summary | 集合摘要 |
| path-matcher | - | M2M1-003 | VIP-011-PathMatcher.md | tracking.path | 路径匹配缓存 |
| compare-service | - | M2M1-020 | VIP-012-CompareService.md | tracking.compare | 比较服务 |
| caffeine-store | - | M2M1-030 | VIP-013-CaffeineStore.md | storage.caffeine | 缓存存储 |
| warmup-cache | - | M2M1-042 | VIP-014-WarmupCache.md | spring.warmup | 缓存预热 |
| metrics-logging | - | M2M1-050 | VIP-015-MetricsLogging.md | metrics | 指标与日志 |
| test-suite | - | M2M1-060 | VIP-016-TestSuite.md | test | 测试套件 |
| documentation | - | M2M1-070 | VIP-017-Documentation.md | docs | 文档生成 |

## 2. 映射关系表

| GPT任务卡 | OPUS任务卡 | 关系类型 | 功能描述 |
|-----------|------------|----------|----------|
| CARD-201 | M2M1-001 | 完全对应 | 对象快照（浅->深） |
| CARD-202, CARD-260 | M2M1-004 | 部分重叠 | 差异检测（标量->扩展） |
| CARD-203, CARD-210 | - | GPT独有 | ChangeTracker核心 |
| CARD-204 | - | GPT独有 | TFI.stop()集成 |
| CARD-220, CARD-221 | - | GPT独有 | 线程上下文管理 |
| CARD-230 | M2M1-031, M2M1-010 | 部分重叠 | 输出格式化 |
| CARD-240 | M2M1-040 | 部分重叠 | 配置管理 |
| CARD-250 | M2M1-061 | 部分重叠 | 性能测试 |
| CARD-251 | - | GPT独有 | 自适应截断 |
| CARD-261 | - | GPT独有 | 并发隔离 |
| CARD-262 | - | GPT独有 | 生命周期清理 |
| CARD-263 | - | GPT独有 | 消息格式 |
| CARD-264 | - | GPT独有 | 反射元数据缓存 |
| CARD-270 | - | GPT独有 | 演示程序 |
| - | M2M1-002 | OPUS独有 | 集合摘要 |
| - | M2M1-003 | OPUS独有 | 路径匹配缓存 |
| - | M2M1-020 | OPUS独有 | 比较服务 |
| - | M2M1-030 | OPUS独有 | Caffeine存储 |
| - | M2M1-041 | OPUS独有 | Actuator端点 |
| - | M2M1-042 | OPUS独有 | 缓存预热 |
| - | M2M1-050 | OPUS独有 | 指标收集 |
| - | M2M1-060 | OPUS独有 | 测试套件 |
| - | M2M1-070 | OPUS独有 | 文档生成 |

## 3. 未匹配清单

### GPT独有（需评估是否保留）
- CARD-251：自适应截断水印 - 性能优化相关
- CARD-261：并发隔离 - 线程安全相关
- CARD-262：生命周期清理 - 资源管理相关
- CARD-263：消息格式 - 输出格式相关
- CARD-264：反射元数据缓存 - 性能优化相关
- CARD-270：变更追踪演示 - 示例程序

### OPUS独有（已纳入VIP）
- M2M1-002：CollectionSummary - 集合摘要功能
- M2M1-003：PathMatcherCache - 路径匹配缓存
- M2M1-020：CompareService - 值比较服务
- M2M1-030：CaffeineStore - 高性能缓存存储
- M2M1-042：WarmupCache - Spring缓存预热
- M2M1-050：MetricsLogging - 指标与日志收集
- M2M1-060：TestSuite - 完整测试套件
- M2M1-070：Documentation - 文档自动生成

## 4. 决策记录

### 4.1 命名规范决策
- 采用编号+名称风格：VIP-xxx-FunctionName.md
- 配置前缀统一：tfi.change-tracking.*
- 端点统一：/actuator/tfi/effective-config（保留taskflow别名）

### 4.2 技术决策
- 深度快照：默认关闭，通过开关启用
- 集合摘要：默认启用，可配置阈值
- 性能测试：JMH为主，微基准为辅
- 缓存策略：优先内存，Caffeine可选

### 4.3 兼容性决策
- 保持现有API不变，新功能通过扩展点
- 配置向后兼容，新配置项默认关闭
- 测试断言基于结构，不依赖文案

## 5. 实施优先级

### Phase 1 - 核心功能（必须）
1. VIP-001：ObjectSnapshotDeep - 深度快照
2. VIP-002：DiffDetector - 差异检测
3. VIP-003：ChangeTracker - 追踪核心
4. VIP-004：TFI-API - 门面API

### Phase 2 - 基础增强（推荐）
5. VIP-005：ThreadContext - 上下文管理
6. VIP-006：OutputFormat - 输出格式
7. VIP-007：ConfigStarter - Spring集成
8. VIP-009：ActuatorEndpoint - 管理端点

### Phase 3 - 高级功能（可选）
9. VIP-010：CollectionSummary - 集合摘要
10. VIP-011：PathMatcher - 路径匹配
11. VIP-008：Performance - 性能测试
12. VIP-015：MetricsLogging - 监控指标

### Phase 4 - 扩展功能（延后）
13. VIP-012：CompareService - 比较服务
14. VIP-013：CaffeineStore - 缓存存储
15. VIP-014：WarmupCache - 缓存预热
16. VIP-016：TestSuite - 测试套件
17. VIP-017：Documentation - 文档生成

---
*最后更新：2024-01-12*
