## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-270-ChangeTracking-Demo.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/demo/TaskFlowInsightDemo.java#com.syy.taskflowinsight.demo.TaskFlowInsightDemo
  - src/main/java/com/syy/taskflowinsight/demo/chapters/ChangeTrackingChapter.java#com.syy.taskflowinsight.demo.chapters.ChangeTrackingChapter
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.{start,stop,withTracked,track,getChanges,exportToConsole,exportToJson}
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：在 demo 包新增/完善示例，显式 API 与 withTracked 并排演示；Console/JSON 展示 CHANGE。

## 4) SCOPE
- In Scope：
  - [x] 示例：`TFI.start/stop` 与 `TFI.withTracked` 两条路径；
  - [x] 导出：Console + JSON 样例输出；
  - [x] 注释清晰，便于读者复现。
- Out of Scope：
  - [ ] 复杂业务逻辑与 UI。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 在 `demo/chapters` 新增/完善 `ChangeTrackingChapter` 示例方法：explicitDemo() / withTrackedDemo()。
2. 在 `TaskFlowInsightDemo` 注册并调用章节；在 README 添加运行说明。
3. 输出片段作为 docs 示例（截取 CHANGE 行）。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：Demo 章节与入口；
- 文档：示例说明与输出片段；

## 7) API & MODELS（必须具体化）
- `TFI.start/stop/withTracked/track/getChanges/exportToConsole/exportToJson`。

## 8) DATA & STORAGE
- N/A。

## 9) PERFORMANCE & RELIABILITY
- 示例运行快速稳定；无异常日志。

## 10) TEST PLAN（可运行、可断言）
- 演示运行：
  - [ ] `./mvnw -q -DskipTests -Dexec.mainClass=com.syy.taskflowinsight.demo.TaskFlowInsightDemo spring-boot:run` 或直接运行 main；
  - [ ] 观察 Console 与 JSON 片段；

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：两条路径输出 CHANGE；
- [ ] 文档：README 更新；
- [ ] 观测：无异常；
- [ ] 性能：N/A；
- [ ] 风险：无。

## 12) RISKS & MITIGATIONS
- 与 stop 互斥：withTracked 已清理，stop 不重复输出；示例注释强调。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 现有 demo 结构可复用；按章节扩展示例。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否需要截图/录屏？可选。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-270-changetracking-demo.md
