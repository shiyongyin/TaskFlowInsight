## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-211-TFI-Convenience-WithTracked.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI
- 工程操作规范：../开发工程师提示词.txt

## 3) GOALS（卡片→可执行目标）
- 业务目标：提供便捷 API，降低显式 API 的成对调用心智负担。
- 技术目标：在 TFI.java 中新增 withTracked 两个重载（Runnable/Callable）；finally 中 getChanges→写 CHANGE→clearAllTracking；不吞业务异常；与 stop 去重（因已清理）。

## 4) SCOPE
- In Scope：
  - [ ] 修改 `src/main/java/com/syy/taskflowinsight/api/TFI.java`：新增 `withTracked` 两个方法
- Out of Scope：
  - [ ] 在 withTracked 内调用 stop（不需要）

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 方法签名：
```java
public static void withTracked(String name, Object target, java.lang.Runnable body, String... fields)
public static <T> T withTracked(String name, Object target, java.util.concurrent.Callable<T> body, String... fields) throws Exception
```
2. 实现：
   - 执行前：track(name, target, fields)
   - finally：List<ChangeRecord> list = getChanges(); 将每条写入当前 TaskNode 的 CHANGE；clearAllTracking();
   - 捕获内部异常走 handleInternalError；业务异常透明抛出。
3. 补丁：提供 TFI.java 的 diff。
4. 文档：卡片勾选；在 204/230/263 验证可见性与格式稳定。
5. 自测：正常/异常路径。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：TFI.java（新增 withTracked 两方法）。
- 测试：WithTrackedTests（异常透明/不重复输出）。
- 文档：卡片勾选。

## 7) API & MODELS（必须具体化）
- 接口签名：如上。
- 模型：ChangeRecord。

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 便捷 API 开销低；异常透明；与 stop 去重。

## 10) TEST PLAN（可运行、可断言）
- 单元/集成测试：
  - [ ] Runnable/Callable 两路径
  - [ ] 业务异常透明；内部异常仅记录
  - [ ] 不重复输出（withTracked 已清理）

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：两重载正常、异常路径正确
- [ ] 文档：卡片勾选

## 12) RISKS & MITIGATIONS
- 重复输出风险 → finally 清理确保 stop 无待刷变更。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] withTracked 是否返回 Callable 的结果并透明抛异常（建议：是）。

