## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-270-ChangeTracking-Demo.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/demo/TaskFlowInsightDemo.java#com.syy.taskflowinsight.demo.TaskFlowInsightDemo
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI
  - src/main/java/com/syy/taskflowinsight/exporter/text/ConsoleExporter.java#com.syy.taskflowinsight.exporter.text.ConsoleExporter
- 工程操作规范：../开发工程师提示词.txt

## 3) GOALS（卡片→可执行目标）
- 业务目标：演示显式 API 与 withTracked 便捷 API 的等价用法，并在输出中展示 CHANGE。
- 技术目标：新增 demo 类或在现有 Demo 增加章节；展示 start→track→修改→stop 与 withTracked 差异（刷新时机）。

## 4) SCOPE
- In Scope：
  - [ ] 新增 `src/main/java/com/syy/taskflowinsight/demo/ChangeTrackingDemo.java` 或在 TaskFlowInsightDemo 中增一节
- Out of Scope：
  - [ ] 修改导出器

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 新增 demo 代码：两个场景并排展示；导出 Console/JSON。
2. 自测：运行 demo 样例；截图或复制输出片段。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：ChangeTrackingDemo.java 或 Demo 章节增量 diff。
- 文档：卡片勾选；输出片段。

## 7) API & MODELS（必须具体化）
- API：TFI.start/stop、TFI.withTracked、TFI.track/getChanges/clearAllTracking、ConsoleExporter/JsonExporter。

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 无特定指标；以可读性为主。

## 10) TEST PLAN（可运行、可断言）
- 手工运行：
  - [ ] 输出含 CHANGE；两种方式不重复输出

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：Demo 可运行并展示 CHANGE
- [ ] 文档：卡片勾选与片段

## 12) RISKS & MITIGATIONS
- 示例偏离真实场景 → 选择简单订单模型（status/amount）。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] Demo 放置路径（新文件 vs 现有 Demo 章节）。

