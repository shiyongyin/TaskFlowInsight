## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。完成 Phase D（Demo）：270，提供可运行示例展示显式 API 与 withTracked 的等价用法，并输出 CHANGE 片段。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-270-ChangeTracking-Demo.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/demo/TaskFlowInsightDemo.java#com.syy.taskflowinsight.demo.TaskFlowInsightDemo
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI
  - exporter/ConsoleExporter.java、exporter/JsonExporter.java

## 3) GOALS（卡片→可执行目标）
- 业务目标：展示 CHANGE 的可见价值与两种接入方式。
- 技术目标：新增 demo 类或章节；两种方式不重复输出（withTracked 已清理）。

## 4) SCOPE
- In Scope：
  - [ ] 新增或扩展 Demo；输出 Console/JSON 片段
- Out of Scope：
  - [ ] 修改导出器

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 编写两个场景：显式 API 与 withTracked。
2. 运行并采集输出片段。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：ChangeTrackingDemo.java 或 Demo 章节增量 diff。
- 文档：卡片☑️；输出片段。

## 7) API & MODELS（必须具体化）
- API：TFI.start/stop、TFI.withTracked、TFI.track/getChanges/clearAllTracking。

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 无特别要求。

## 10) TEST PLAN（可运行、可断言）
- 手工验证：
  - [ ] CHANGE 可见；两种方式不重复输出

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：Demo 可运行并展示 CHANGE
- [ ] 文档：卡片☑️ 与片段

## 12) RISKS & MITIGATIONS
- 示例与生产差距 → 选用简洁订单模型，注释清晰。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] Demo 放置路径（新文件 vs 现有章节）。

