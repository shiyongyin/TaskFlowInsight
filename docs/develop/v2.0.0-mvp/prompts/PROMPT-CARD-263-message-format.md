## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-263-Message-Format.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.stop()（内部格式化私有函数将新增）
  - src/main/java/com/syy/taskflowinsight/exporter/text/ConsoleExporter.java#com.syy.taskflowinsight.exporter.text.ConsoleExporter
  - src/main/java/com/syy/taskflowinsight/exporter/json/JsonExporter.java#com.syy.taskflowinsight.exporter.json.JsonExporter
- 工程操作规范：../开发工程师提示词.txt

## 3) GOALS（卡片→可执行目标）
- 业务目标：确保变更消息格式稳定可测：`<Object>.<field>: <old> → <new>`。
- 技术目标：编写测试使用正则断言；验证“先转义后截断（8192）”“空值 null”。

## 4) SCOPE
- In Scope：
  - [ ] 新增测试类 MessageFormatIT
- Out of Scope：
  - [ ] 修改导出器

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 新建测试类 MessageFormatIT。
2. 用例：含引号/换行的值；超长值截断；null 值表示；正则 `^([\w.$]+)\.([\w.$]+): (.+) \u2192 (.+)$` 匹配。
3. 自测：运行测试。

## 6) DELIVERABLES（输出必须包含）
- 测试代码：MessageFormatIT.java。
- 文档：卡片勾选。

## 7) API & MODELS（必须具体化）
- API：TFI.stop（内部格式化）。

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 无特别指标；仅格式稳定性。

## 10) TEST PLAN（可运行、可断言）
- 集成测试：
  - [ ] 正则匹配通过；截断后 `... (truncated)` 结尾；null 正确显示

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：格式稳定
- [ ] 文档：卡片勾选

## 12) RISKS & MITIGATIONS
- 格式化逻辑分散 → 建议在 TFI 单点函数实现。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否将格式化工具函数外提为公共 util？（建议保持私有，避免扩散）

