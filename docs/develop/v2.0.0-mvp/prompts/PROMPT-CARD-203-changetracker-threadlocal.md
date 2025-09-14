## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-203-ChangeTracker-ThreadLocal.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#com.syy.taskflowinsight.context.ManagedThreadContext
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.handleInternalError(java.lang.String, java.lang.Throwable)
- 相关配置/SQL/脚本：无（定时清理器开关由卡240落地）
- 工程操作规范：../开发工程师提示词.txt（必须遵循）
- 历史提示词风格参考：../v1.0.0-mvp/design/api-implementation/prompts/DEV-010-TFI-MainAPI-Prompt.md

## 3) GOALS（卡片→可执行目标）
- 业务目标：在线程边界内追踪对象变化，提供 track/trackAll/getChanges/clearAllTracking，支持 stop/close/endSession 三处清理。
- 技术目标：实现 ThreadLocal 基线快照；getChanges 捕获 after 调用 DiffDetector，返回增量并更新基线；同名 track 覆盖并 WARN；定时清理器默认关闭。

## 4) SCOPE
- In Scope：
  - [ ] 新建 `src/main/java/com/syy/taskflowinsight/tracking/ChangeTracker.java`
  - [ ] API：`track(String,Object,String...)`、`trackAll(Map<String,Object>)`、`getChanges()`、`clearAllTracking()`
  - [ ] 线程本地结构：`ThreadLocal<Map<String, SnapshotEntry>>`
  - [ ] 元数据透传：从 ManagedThreadContext 读取 sessionId/taskPath
- Out of Scope：
  - [ ] 保持 pendingChanges 副本（改为按需计算），避免冗余状态
  - [ ] 自适应水位策略（M1）

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 受影响模块与文件：新增 tracking/ChangeTracker.java；读取 context/ManagedThreadContext；将由卡210接入 TFI 门面。
2. 新建类与方法签名：
```java
package com.syy.taskflowinsight.tracking;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import java.util.*;
public final class ChangeTracker {
  public static void track(String name, Object target, String... fields) { /* capture before */ }
  public static void trackAll(Map<String,Object> targets) { /* 可选：多对象快捷 */ }
  public static List<ChangeRecord> getChanges() { /* capture after + diff + update baseline */ }
  public static void clearAllTracking() { /* 清空 ThreadLocal */ }
}
```
3. 实现要点：
   - SnapshotEntry{name, before(Map<String,Object>), fields(String[])}；getChanges 需要可获得 target 与 fields（可要求由 API 层再次提供目标对象；M0 简化为在 track 时保存 WeakReference<Object> target）。
   - 同名覆盖并 WARN；异常通过调用方处理（TFI.handleInternalError）。
4. 补丁：提供完整文件内容；若使用 WeakReference 请注明注释与风险。
5. 文档：卡片勾选；在 210/204 进行 API 集成与 stop 刷新。
6. 自测：增量变化/清理/多线程隔离（部分在 261/262/221 覆盖）。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：ChangeTracker.java（完整内容）。
- 测试：最小增量与清理单测；并发用例移至 261。
- 文档：卡片勾选。
- 回滚/灰度：`clearAllTracking()` 可作为回滚手段；定时清理默认关闭。
- 观测：WARN：同名覆盖；DEBUG：采集与对比路径。

## 7) API & MODELS（必须具体化）
- 接口签名：如上。
- 异常：内部异常不冒泡，由 TFI 门面处理（卡210）。
- 模型：ChangeRecord，ObjectSnapshot（201），DiffDetector（202）。

## 8) DATA & STORAGE
- 内存：ThreadLocal<Map<String, SnapshotEntry>>；无持久化。
- 幂等：clearAll 幂等；多次 getChanges 返回增量。

## 9) PERFORMANCE & RELIABILITY
- 性能：getChanges 在 2 字段场景下配合 250 达到建议目标。
- 可靠性：三处清理路径（stop/close/endSession）都会调用 clearAll（210/220/204/262 接力）。

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 80%
  - [ ] track→修改→getChanges（仅增量）；clearAll 后空
- 集成测试：
  - [ ] 与 210/204 合作路径在 204/230 验证
- 性能测试：
  - [ ] 见 250

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：四个 API 正常工作；同名覆盖 WARN
- [ ] 文档：卡片勾选与注释完善
- [ ] 观测：日志级别合理
- [ ] 性能：建议目标满足或报告
- [ ] 风险：ThreadLocal 泄漏风险可控（清理路径验证）

## 12) RISKS & MITIGATIONS
- WeakReference 目标对象回收导致无法计算 after → 要求 API 层靠近使用点，尽快 getChanges 刷新。
- 线程池复用串扰 → 三处清理 + 221 子线程 close。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否在 track 持有 WeakReference<Object>？还是要求 API 层提供 after 时目标对象？（建议 WeakReference + 限制生命周期）
- 责任人/截止日期/所需产物：架构评审结论；卡片备注。

