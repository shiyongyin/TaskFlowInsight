## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-230-Console-Json-ChangeMessage-Verification.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/exporter/text/ConsoleExporter.java#com.syy.taskflowinsight.exporter.text.ConsoleExporter.export(Session)
  - src/main/java/com/syy/taskflowinsight/exporter/json/JsonExporter.java#com.syy.taskflowinsight.exporter.json.JsonExporter.export(Session)
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.{exportToConsole,exportToJson,formatChangeMessage}
  - src/main/java/com/syy/taskflowinsight/enums/MessageType.java#com.syy.taskflowinsight.enums.MessageType.CHANGE
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）
- 历史提示词风格参考：../../v1.0.0-mvp/design/api-implementation/prompts/DEV-010-TFI-MainAPI-Prompt.md

## 3) GOALS（卡片→可执行目标）
- 业务目标：无需改导出器，验证 Console/JSON 输出包含规范 CHANGE 信息，避免硬编码 nodeId，容忍时间戳差异。
- 技术目标：
  - Console：包含任务树与消息 `[CHANGE]` 行；
  - JSON：messages 数组包含 type=CHANGE 的项，content 符合 `<Object>.<field>: <old> → <new>`。

## 4) SCOPE
- In Scope：
  - [ ] 集成测试：start→track→修改→stop→导出；断言 Console/JSON 含 CHANGE。
- Out of Scope：
  - [ ] 修改导出器行为。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 编写 IT：调用 `TFI.exportToConsole(false)` 与 `TFI.exportToJson()`；
2. Console 断言：正则匹配 `\[CHANGE] .*: .* → .*`；忽略时间戳。
3. JSON 断言：解析 JSON，遍历 root.children/messages，找到 `{"type":"CHANGE","content":"..."}`。

## 6) DELIVERABLES（输出必须包含）
- 测试：`src/test/java/com/syy/taskflowinsight/export/ExportChangeMessageIntegrationTests.java`（建议路径）。
- 文档：卡片回填。

## 7) API & MODELS（必须具体化）
- 门面：`TFI.exportToConsole(boolean showTimestamp)`、`TFI.exportToJson()`。

## 8) DATA & STORAGE
- N/A。

## 9) PERFORMANCE & RELIABILITY
- 输出稳定：内容格式受 `formatChangeMessage` 控制；时间戳非关键断言项。

## 10) TEST PLAN（可运行、可断言）
- 集成：
  - [x] Console 正则匹配 `[CHANGE]` 行；
  - [x] JSON 中存在 type=CHANGE 的消息；
  - [x] 内容格式 `<Object>.<field>: <old> → <new>` 与 escape+truncate 一致。

## 11) ACCEPTANCE（核对清单，默认空）
- [x] 功能：两种导出均含 CHANGE；
- [ ] 文档：卡片更新；
- [ ] 观测：无异常日志；
- [ ] 性能：导出耗时可接受；
- [ ] 风险：断言避免对 nodeId 硬编码。

## 12) RISKS & MITIGATIONS
- 弱耦合断言：使用正则与字段存在性，避免对顺序/时间戳敏感。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 与实现一致：不改导出器；仅验证。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] JSON 导出模式（COMPAT/ENHANCED）是否影响断言？本卡使用默认 COMPAT。
- 责任人/截止日期/所需产物：待指派 / 本卡周期 / 集成测试。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-230-console-json-changemessage-verification.md
