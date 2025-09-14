# VIP-015-MetricsLogging（合并版）

## 1. 概述
- 主题：指标收集与日志记录
- 源卡：
  - OPUS: `../../opus/PROMPT-M2M1-050-metrics-logging.md`
  - GPT: `../../gpt/PROMPT-CARD-250-benchmarks-jmh-or-micro.md`（部分）
- 相关代码：
  - `src/main/java/com/syy/taskflowinsight/metrics/MetricsCollector.java`（待创建）
  - `src/main/java/com/syy/taskflowinsight/logging/StructuredLogger.java`（待创建）

## 2. 相同点（达成共识）
- Micrometer集成
- 结构化日志
- 性能指标收集
- 可配置级别

## 3. 差异与歧义

### 差异#1：指标粒度

#### 最小指标集与标签约束（MVP）
- 指标集合（从少到稳）：
  - 计数：`tfi.tracking.total`、`tfi.changes.total`、`tfi.errors.total`
  - 时延：`tfi.snapshot.duration`、`tfi.diff.duration`、`tfi.stop.duration`
- 约束：
  - 标签维度极简（避免使用高基数字段）；关键路径低频打点或采样；默认关闭 Micrometer，按需启用。

## 实施触发条件
- 需要用指标进行回归/排障（如变更数突增、快照/对比时延异常）。
- 仅在“最小指标集”足以支撑观测的前提下开启；标签维度需经评审确认。

#### 现状 vs 规划 对照（速览）
- 现状（已实现）
  - 结构化日志可按需记录；无 Micrometer 集成。
- 规划（Phase 2+）
  - 按“最小指标集”落地 Micrometer，保守控制标签与频率；后续再扩展更细粒度指标。
- **影响**：性能开销
- **OPUS方案**：全量收集
- **GPT方案**：采样收集
- **建议取舍**：分级收集
- **理由**：平衡信息完整性和性能

## 4. 最终设计（融合后）

### 接口与契约（示例）
- 详见 `snippets/VIP-015-METRICS-MICROMETER-EXAMPLE.md#采集器与结构化日志`

### 配置键
- 基础配置示例：见 `snippets/VIP-015-METRICS-MICROMETER-EXAMPLE.md#基础配置示例yaml`

## 5. 验收与回滚

### 验收清单
- [ ] 指标正确收集
- [ ] 日志格式规范
- [ ] 性能影响<5%
- [ ] 敏感信息脱敏

### 回滚方案
1. 禁用指标：`tfi.metrics.enabled=false`
2. 禁用结构化日志：`tfi.logging.structured=false`

---
*生成时间：2024-01-12*
*版本：v2.1.0-MVP*
