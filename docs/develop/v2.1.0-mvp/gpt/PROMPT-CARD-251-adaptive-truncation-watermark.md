## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-251-Adaptive-Truncation-Watermark.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshot.java#repr(Object,int), setMaxValueLength(int)
  - src/main/java/com/syy/taskflowinsight/config/ChangeTrackingProperties.java#valueReprMaxLength
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：M1 规划项，定义自适应截断与水位策略口径；M0 不实现，仅明确接口与配置占位。

## 4) SCOPE
- In Scope：
  - [ ] 设计占位：水位口径会话/全局；阈值 80%/90% 收紧至 2048/512，回落恢复。
- Out of Scope：
-  [ ] M0 实现与代码改动（本周期不落地）。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 在 docs/ 添加策略设计草案：`docs/task/v2.1.0-mvp/performance/V210-251-Adaptive-Policy.md`（建议路径）。
2. 接口占位（如需）：预留 `ObjectSnapshot`/配置键但默认禁用，不影响现有行为。

## 6) DELIVERABLES（输出必须包含）
- 文档：策略口径、阈值、回退策略与观测项；不提交代码实现。

## 7) API & MODELS（必须具体化）
- 预留（文档层面）：`tfi.change-tracking.value-repr-adaptive.enabled=false`（暂不实现）。

## 8) DATA & STORAGE
- N/A。

## 9) PERFORMANCE & RELIABILITY
- 仅文档规划；实现阶段需避免抖动与误触发。

## 10) TEST PLAN（可运行、可断言）
- N/A（M1 实现时补充）。

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 文档：策略草案提交；
- [ ] 风险：口径明确；
- [ ] 影响：不影响 M0 行为。

## 12) RISKS & MITIGATIONS
- 阈值抖动：需引入滞后与回落策略；后续讨论。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 与卡片一致：推迟到 M1。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 统计口径与触发窗口大小；
- [ ] 监控指标与告警阈值；
- 责任人/截止日期/所需产物：待指派 / M1 / 设计文档。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-251-adaptive-truncation-watermark.md

