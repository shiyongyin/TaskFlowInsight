## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-263-Message-Format.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.formatChangeMessage(ChangeRecord)
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.formatValue(Object)
  - src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshot.java#com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot.repr(Object)
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：验证变更消息格式稳定：`<Object>.<field>: <old> → <new>`；先转义后截断；空值输出 null；Console/JSON 一致。

## 4) SCOPE
- In Scope：
  - [ ] 用正则断言 CHANGE 消息内容；包含换行/引号等特殊字符场景；与导出一致。
- Out of Scope：
  - [ ] 更改消息格式（仅验证）。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 新增测试：构造包含特殊字符的变更，验证 `formatChangeMessage` 输出。
2. Console/JSON 导出各验证一次（可复用 230 的导出结果）。

## 6) DELIVERABLES（输出必须包含）
- 测试：`src/test/java/com/syy/taskflowinsight/format/ChangeMessageFormatTests.java`（建议路径）。
- 文档：卡片回填。

## 7) API & MODELS（必须具体化）
- `TFI.formatChangeMessage(ChangeRecord)`；`ObjectSnapshot.repr(Object)`。

## 8) DATA & STORAGE
- N/A。

## 9) PERFORMANCE & RELIABILITY
- 表示逻辑统一在 ObjectSnapshot；TFI 仅拼接，不引入额外成本。

## 10) TEST PLAN（可运行、可断言）
- 单元/集成：
  - [x] 正则 `^.+\..+: .+ → .+$`；
  - [x] 包含引号/换行/反斜杠等字符被正确转义后再截断；
  - [x] Console/JSON 中的 content 与上述一致。

## 11) ACCEPTANCE（核对清单，默认空）
- [x] 功能：格式/转义/截断一致；
- [ ] 文档：结论回填；
- [ ] 观测：无异常日志；
- [ ] 性能：N/A；
- [ ] 风险：无。

## 12) RISKS & MITIGATIONS
- 双重转义：确保只在 ObjectSnapshot.repr 处转义；TFI 不再重复。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 当前实现 `TFI.formatValue` 已委托 `ObjectSnapshot.repr`，对齐要求。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否需要 oldRepr？当前不需要；格式固定。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-263-message-format.md
