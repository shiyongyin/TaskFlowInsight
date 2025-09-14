# VIP-010-CollectionSummary（合并版）

## 1. 概述
- 主题：集合摘要与降级处理
- 源卡：
  - OPUS: `../../opus/PROMPT-M2M1-002-collection-summary.md`
  - GPT: 无直接对应（部分功能在snapshot中）
- 相关代码：
  - `src/main/java/com/syy/taskflowinsight/tracking/summary/CollectionSummary.java`（待创建）
  - `src/main/java/com/syy/taskflowinsight/core/ObjectSnapshot.java`（集成点）

## 2. 相同点（达成共识）
- 大集合降级处理
- 保留关键信息
- 性能优先
- 可配置阈值

## 3. 差异与歧义

#### 现状 vs 规划 对照（速览）
- 现状（已实现）
  - 无独立的集合摘要组件；导出侧以 `repr` 与长度截断为主。
- 规划（按需引入）
  - `summary.enabled` 开关下的最小摘要：大集合降级为“类型+计数”摘要，少量示例；后续再考虑更丰富的统计。

## 最小实现（MVP）
- 大集合阈值触发时输出：`[CollectionType size=n]`，并附加不超过 `max-examples` 的示例（转义+截断后）。
- 小集合直接 `toString()`（统一 `repr` 转义与 8192 截断）。
- 开关与阈值：`summary.enabled`、`summary.max-size`、`summary.max-examples`。

## 实施触发条件（扩展统计）
- 日志/JSON 输出体积过大，需要进一步降噪或特征聚合。
- 运维/审计需要更细粒度的分布/敏感项统计。

### 差异#1：触发机制
- **影响**：降级时机
- **OPUS方案**：固定阈值触发
- **现有代码**：无此功能
- **建议取舍**：动态阈值+手动控制
- **理由**：更灵活，适应不同场景

### 差异#2：摘要内容
- **影响**：信息完整性
- **OPUS方案**：统计信息+示例
- **建议增强**：添加分布特征
- **理由**：更好的问题定位

### 差异#3：集成方式
- **影响**：代码侵入性
- **OPUS方案**：独立组件
- **建议取舍**：插件式集成到Snapshot
- **理由**：减少改动，易于开关

## 4. 最终设计（融合后）

### 接口与契约

实现示例：见 `snippets/VIP-010-CollectionSummary-EXAMPLES.md#summary-实现示例`


### 配置键

配置示例：见 `snippets/VIP-010-CollectionSummary-EXAMPLES.md#配置示例（yaml）`


## 5. 与代码的对齐与改造清单

### 变更点
- 新建：`CollectionSummary.java` 摘要生成器
- 新建：`SummaryInfo.java` 摘要信息模型
- 增强：`ObjectSnapshot.java` 集成摘要功能
- 配置：添加summary配置节

### 不改动项
- 保持现有Snapshot基础功能
- 摘要为可选功能，可完全关闭

## 6. 测试计划

### 单元测试

测试/用法示例：见 `snippets/VIP-010-CollectionSummary-EXAMPLES.md#测试/用法示例`


### 集成测试
- 与Snapshot集成测试
- 与ChangeTracker集成测试
- 端到端追踪测试

## 7. 验收与回滚

### 验收清单
- [ ] 大集合正确降级
- [ ] 摘要信息完整
- [ ] 性能符合要求
- [ ] 敏感信息过滤
- [ ] 配置生效

### 回滚方案
1. 禁用摘要：`tfi.change-tracking.summary.enabled=false`
2. 调整阈值避免触发
3. 使用原始Snapshot

## 8. 差异与建议
- 摘要功能独立组件化，便于维护
- 支持多种集合类型
- 提供丰富的统计信息

## 9. 开放问题
- [ ] 是否需要支持自定义摘要策略？
- [ ] 是否需要摘要结果缓存？
- [ ] 是否支持增量摘要更新？

---
*生成时间：2024-01-12*
*版本：v2.1.0-MVP*
