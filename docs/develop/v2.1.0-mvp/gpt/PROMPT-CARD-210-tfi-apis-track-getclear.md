## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-210-TFI-APIs-Track-GetClear.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.{track,trackAll,getChanges,clearAllTracking,setChangeTrackingEnabled,isChangeTrackingEnabled}
  - src/main/java/com/syy/taskflowinsight/tracking/ChangeTracker.java#com.syy.taskflowinsight.tracking.ChangeTracker.{track,trackAll,getChanges,clearAllTracking}
  - src/main/java/com/syy/taskflowinsight/tracking/model/ChangeRecord.java#com.syy.taskflowinsight.tracking.model.ChangeRecord
- 相关配置：
  - src/main/java/com/syy/taskflowinsight/config/ChangeTrackingProperties.java#tfi.change-tracking.enabled
  - src/main/resources/application.yml: tfi.change-tracking.enabled（默认false，需显式开启；若未存在请新增）
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）
- 历史提示词风格参考：../../v1.0.0-mvp/design/api-implementation/prompts/DEV-010-TFI-MainAPI-Prompt.md

## 3) GOALS（卡片→可执行目标）
- 业务目标：提供四个静态门面 API 并委托 ChangeTracker，实现异常安全、双层开关与低开销。
- 技术目标：
  - 方法：track/trackAll/getChanges/clearAllTracking；禁用时快速返回；所有方法 try/catch 并委托 handleInternalError。

## 4) SCOPE
- In Scope：
  - [x] 校核并完善四 API 行为、Javadoc 与异常路径覆盖。
  - [x] 双开关：`TFI.isEnabled()` 与 `TFI.isChangeTrackingEnabled()` 共同为真时才执行。
- Out of Scope：
  - [ ] 新增业务依赖或修改 ChangeTracker 行为（门面不改变底层语义）。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 校对 TFI 四方法现有实现，确保禁用路径 return 空/Null，启用路径委托 ChangeTracker。
2. 方法签名（已存在）：
```java
public static void track(String name, Object target, String... fields)
public static void trackAll(Map<String,Object> targets)
public static java.util.List<ChangeRecord> getChanges()
public static void clearAllTracking()
```
3. 自测：构造简单对象，track→修改→getChanges→clearAll；禁用状态快速返回。
4. Javadoc：补充禁用时行为与异常处理策略。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：必要的 Javadoc 与异常路径补强；无语义变化。
- 测试：
  - 成功/失败分支覆盖：禁用与启用；
  - 异常路径：ChangeTracker 抛出异常时不冒泡；
  - 功能路径：与 ChangeTracker 协同正确。
- 文档：任务卡回填；README 补充“启用方法与开关”。
- 回滚/灰度：通过配置关闭 `tfi.change-tracking.enabled=false`。
- 观测：错误统一 `TFI.handleInternalError`，日志 DEBUG/WARN 合理。

## 7) API & MODELS（必须具体化）
```java
public final class TFI {
  public static void track(String name, Object target, String... fields);
  public static void trackAll(java.util.Map<String,Object> targets);
  public static java.util.List<com.syy.taskflowinsight.tracking.model.ChangeRecord> getChanges();
  public static void clearAllTracking();
}
```
- 异常：所有捕获并记录；不冒泡业务。

## 8) DATA & STORAGE
- N/A。

## 9) PERFORMANCE & RELIABILITY
- 性能：禁用状态零/近零开销；启用状态避免多余对象创建。
- 可靠性：门面不持状态，避免内存泄漏。

## 10) TEST PLAN（可运行、可断言）
- 单元：
  - [x] 禁用：四方法快速返回（getChanges 返回空；其他无抛异常）。
  - [x] 启用：四方法委托 ChangeTracker，覆盖成功路径。
  - [ ] 异常：模拟底层异常，断言 handleInternalError 被调用（可通过 Spy/日志匹配）。
- 集成：
  - [ ] 简单对象追踪链路贯通。

## 11) ACCEPTANCE（核对清单，默认空）
- [x] 功能：四 API 与卡片一致；禁用/启用路径正确。
- [ ] 文档：Javadoc 与 README 更新。
- [x] 观测：日志与异常策略正确。
- [x] 性能：禁用路径近零开销。
- [x] 风险：与 ChangeTracker 协同无副作用。

## 12) RISKS & MITIGATIONS
- 行为漂移：门面不修改底层语义；仅委托。
- 误用风险：Javadoc 明确禁用时的返回行为。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 现实现已对齐卡片；保持双开关与异常策略一致。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否在 export 前也自动 flush 变化？当前仅 stop/withTracked 触发。
- 责任人/截止日期/所需产物：待指派 / 本卡周期 / 代码+测试+文档。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-210-tfi-apis-track-getclear.md
