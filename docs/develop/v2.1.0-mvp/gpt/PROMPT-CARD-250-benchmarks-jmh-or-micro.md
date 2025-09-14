## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-250-Benchmarks-JMH-or-Micro.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshot.java
  - src/main/java/com/syy/taskflowinsight/tracking/detector/DiffDetector.java
  - src/main/java/com/syy/taskflowinsight/tracking/ChangeTracker.java
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：建立可复现的微基准（或JMH）覆盖 track→修改→getChanges→clearAll 链路，报告 P95 延迟，并记录环境参数。

## 4) SCOPE
- In Scope：
  - [ ] 2/20 字段、8/16 线程的场景；Warmup/Measurement 固定；输出 P95。
- Out of Scope：
  - [ ] CPU 占比硬门槛（仅报告）。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 在 `docs/perf/` 增加脚本与结果模板；可选：`benchmarks/` 下 JMH 工程或基于 JUnit 的微基准（循环+计时）。
2. 记录：Git SHA、JDK、OS/CPU、JVM 参数、重复次数、P95。

## 6) DELIVERABLES（输出必须包含）
- 基准代码：`benchmarks/` 或 `src/test/java/.../Perf*Tests.java`；
- 报告：`docs/perf/V200-benchmark-report.md`（示例命名）。

## 7) API & MODELS（必须具体化）
- 目标链路：`ChangeTracker.track → 修改对象 → ChangeTracker.getChanges → ChangeTracker.clearAllTracking`。

## 8) DATA & STORAGE
- N/A。

## 9) PERFORMANCE & RELIABILITY
- 重复 ≥ 5 次，剔除首轮；Warmup ≥5s，Measurement ≥30s（如使用 JMH）。

## 10) TEST PLAN（可运行、可断言）
- 性能测试：
  - [ ] 输出 P95；
  - [ ] 记录环境与参数；
  - [ ] 未达建议目标附改进计划。

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：基准链路覆盖；
- [ ] 文档：报告完成；
- [ ] 观测：无异常；
- [ ] 性能：P95 报告齐备；
- [ ] 风险：N/A。

## 12) RISKS & MITIGATIONS
- 伪基准：严格按固定 Warmup/Measurement；多次重复取 P95。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- N/A。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否引入 JMH？若受限则使用微基准并清晰标注方法。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-250-benchmarks-jmh-or-micro.md

