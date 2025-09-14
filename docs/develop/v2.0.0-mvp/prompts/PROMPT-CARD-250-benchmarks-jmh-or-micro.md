## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-250-Benchmarks-JMH-or-Micro.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI
- 工程操作规范：../开发工程师提示词.txt

## 3) GOALS（卡片→可执行目标）
- 业务目标：提供可复现的变更追踪热路径基准测试与报告。
- 技术目标：JMH 基准（优先），覆盖 2/20 字段、8/16 线程；输出 P95 延迟（建议目标 2 字段 ≤ 200μs），记录机器/参数；CPU 占比作为报告项。

## 4) SCOPE
- In Scope：
  - [ ] 新增基准类 ChangeTrackingBenchmark（包：bench 或 test/java 下的基准包）
- Out of Scope：
  - [ ] 引入外部数据源

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 新建基准类，基准步骤：track → mutate → getChanges → clearAllTracking。
2. JMH 注解：@BenchmarkMode(SampleTime) @OutputTimeUnit(MICROSECONDS)；warmup/measurement 固定轮次；@Threads(8/16)。
3. 自测：本地跑通；输出报告（md/截图）。

## 6) DELIVERABLES（输出必须包含）
- 基准代码：ChangeTrackingBenchmark.java。
- 报告：docs 或卡片附录；注明机器/参数。

## 7) API & MODELS（必须具体化）
- API：TFI.track/getChanges/clearAllTracking。

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 目标：2 字段 P95 ≤ 200μs（建议）。

## 10) TEST PLAN（可运行、可断言）
- 性能测试：
  - [ ] 2 字段与 20 字段两组；8/16 线程；报告 P50/P95

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：基准可运行并生成稳定结果
- [ ] 文档：卡片勾选与报告提交

## 12) RISKS & MITIGATIONS
- 环境波动 → 固定参数、记录环境、跑多次取中。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 报告存放路径（建议：docs/perf/ 或卡片附录）。

