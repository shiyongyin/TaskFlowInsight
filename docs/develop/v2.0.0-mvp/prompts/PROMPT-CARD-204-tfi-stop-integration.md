## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-204-TFI-Stop-Integration.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.stop()
  - src/main/java/com/syy/taskflowinsight/model/TaskNode.java#com.syy.taskflowinsight.model.TaskNode.addMessage(String, com.syy.taskflowinsight.enums.MessageType)
  - src/main/java/com/syy/taskflowinsight/enums/MessageType.java#com.syy.taskflowinsight.enums.MessageType.CHANGE
- 工程操作规范：../开发工程师提示词.txt
- 历史提示词风格参考：../v1.0.0-mvp/design/api-implementation/prompts/DEV-010-TFI-MainAPI-Prompt.md

## 3) GOALS（卡片→可执行目标）
- 业务目标：在任务结束时自动输出变更记录，使 Console/JSON 导出可见 CHANGE。
- 技术目标：在 `TFI.stop()` 尾部：getChanges → 写 CHANGE → clearAllTracking（try/finally）。与 withTracked 行为互斥（withTracked 已清理）。

## 4) SCOPE
- In Scope：
  - [ ] 修改 `src/main/java/com/syy/taskflowinsight/api/TFI.java#stop()` 尾部集成刷新逻辑
  - [ ] 新增内部格式化函数：`<obj>.<field>: <old> → <new>`（先转义后截断）
- Out of Scope：
  - [ ] 修改导出器（不需要）

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 受影响模块与文件：TFI.java。
2. 修改方法与新增私有函数：
```java
// TFI.stop() 尾部（try/finally）
List<ChangeRecord> changes = ChangeTracker.getChanges();
for (ChangeRecord cr : changes) {
  String msg = formatChangeMessage(cr);
  task.addMessage(msg, MessageType.CHANGE);
}
// finally: ChangeTracker.clearAllTracking();

private static String formatChangeMessage(ChangeRecord cr) { /* 转义→截断 */ }
```
3. 补丁：提供 TFI.java 的 diff；仅在 stop 尾部新增，无行号依赖。
4. 文档：卡片勾选；在 230/263 验证格式可见性。
5. 自测：最小流程 start→track→修改→stop；Console/JSON 输出片段。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：TFI.java（stop 集成的 diff + 私有格式化函数）。
- 测试：StopIntegrationTests（单元或集成，断言 CHANGE 出现）。
- 文档：卡片勾选；附 Console/JSON 片段。
- 回滚/灰度：通过不开启变更追踪开关回滚；或移除新增片段。
- 观测：无新增指标。

## 7) API & MODELS（必须具体化）
- 接口：TFI.stop（现有）；不改签名。
- 模型：ChangeRecord（201），DiffDetector（202），ChangeTracker（203）。

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 性能：stop 追加开销小；异常不影响主流程。
- 可靠性：finally 清理，避免泄漏与重复输出。

## 10) TEST PLAN（可运行、可断言）
- 单元/集成测试：
  - [ ] 覆盖率 ≥ 80%
  - [ ] start→track→修改→stop：Console/JSON 含 CHANGE
  - [ ] 与 withTracked 并用不重复输出

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：CHANGE 可见且仅输出一次
- [ ] 文档：卡片勾选与输出片段
- [ ] 观测：无异常日志
- [ ] 性能：stop 开销可接受
- [ ] 风险：空上下文防护

## 12) RISKS & MITIGATIONS
- 当前 TaskNode 为空 → 早返回；不 NPE。
- 变更过多导致消息膨胀 → 依赖上游截断。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无（枚举 CHANGE 已存在）。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] withTracked 与 stop 的去重是否需额外标记？（建议：不需要，依赖 withTracked finally 清理）

