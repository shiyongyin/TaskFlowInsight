## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-220-ManagedThreadContext-Cleanup.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#com.syy.taskflowinsight.context.ManagedThreadContext.close()
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.clearAllTracking()
- 工程操作规范：../开发工程师提示词.txt

## 3) GOALS（卡片→可执行目标）
- 业务目标：上下文关闭时清理变更追踪，避免线程池复用造成串扰。
- 技术目标：在 ManagedThreadContext.close() 的 finally 中调用 TFI.clearAllTracking()；幂等。

## 4) SCOPE
- In Scope：
  - [ ] 修改 ManagedThreadContext.close() 的 finally 段落，追加清理调用
- Out of Scope：
  - [ ] 其他生命周期钩子

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 受影响模块与文件：ManagedThreadContext.java。
2. 修改点：
```java
finally {
  try { com.syy.taskflowinsight.api.TFI.clearAllTracking(); } catch (Throwable ignore) {}
  closed = true; SafeContextManager.getInstance().unregisterContext(this);
}
```
3. 补丁：提供 ManagedThreadContext.java 的精确 diff。
4. 文档：卡片勾选；与 262 测试闭环。
5. 自测：创建上下文→close→getChanges 返回空。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：ManagedThreadContext.java（diff）。
- 测试：ContextCleanupTests。
- 文档：卡片勾选。
- 回滚/灰度：删除追加行。
- 观测：无。

## 7) API & MODELS（必须具体化）
- 无新增接口；直接调用现有静态方法。

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 性能：基本无影响；可靠性提升（避免串扰）。

## 10) TEST PLAN（可运行、可断言）
- 单元/集成测试：
  - [ ] 覆盖率 ≥ 80%
  - [ ] close 后 getChanges 为空；重复 close 幂等

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：清理触发成功
- [ ] 文档：卡片勾选
- [ ] 观测：无异常日志
- [ ] 性能：无退化
- [ ] 风险：线程复用无串扰

## 12) RISKS & MITIGATIONS
- TFI.clearAllTracking 未实现 → 先完成卡210。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否在 endSession 也补充清理？（建议：是，在卡262覆盖）

