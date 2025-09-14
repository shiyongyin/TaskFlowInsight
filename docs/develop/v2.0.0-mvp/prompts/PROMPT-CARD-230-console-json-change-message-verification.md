## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-230-Console-Json-ChangeMessage-Verification.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/exporter/text/ConsoleExporter.java#com.syy.taskflowinsight.exporter.text.ConsoleExporter
  - src/main/java/com/syy/taskflowinsight/exporter/json/JsonExporter.java#com.syy.taskflowinsight.exporter.json.JsonExporter
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.exportToJson(), exportToConsole(boolean)
- 工程操作规范：../开发工程师提示词.txt

## 3) GOALS（卡片→可执行目标）
- 业务目标：验证 M0 不改导出器即可展示 CHANGE；提供 Console/JSON 验证用例。
- 技术目标：编写测试，断言 Console 包含 `<obj>.<field>: <old> → <new>`，JSON 中 messages 有 `{type:CHANGE, content:...}`。

## 4) SCOPE
- In Scope：
  - [ ] 新增测试：导出到 Console 与 JSON 的断言
- Out of Scope：
  - [ ] 修改导出器实现

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 新增测试类：ExportVerificationIT。
2. 用例：start→track→修改→stop→exportToConsole/exportToJson；断言包含 CHANGE。
3. 自测：运行 `./mvnw -q -DskipTests=false test`。

## 6) DELIVERABLES（输出必须包含）
- 测试代码：ExportVerificationIT.java。
- 文档：卡片勾选；附输出片段。

## 7) API & MODELS（必须具体化）
- 使用：ConsoleExporter.export(Session)、JsonExporter.export(Session)、TFI.exportToJson/Console。

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 展示稳定；容忍时间戳差异；不硬编码 nodeId。

## 10) TEST PLAN（可运行、可断言）
- 集成测试：
  - [ ] Console：包含 `<obj>.<field>: <old> → <new>`
  - [ ] JSON：存在 `{"type":"CHANGE","content":"..."}`

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：两种导出方式均可见 CHANGE
- [ ] 文档：卡片勾选与片段附带

## 12) RISKS & MITIGATIONS
- 输出格式轻微变动 → 使用包含关系与正则断言。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] Console 使用简化/完整模式选择（建议：无时间戳 simple 模式）。

