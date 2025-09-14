## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-210-TFI-APIs-Track-GetClear.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI
  - src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#com.syy.taskflowinsight.context.ManagedThreadContext
- 工程操作规范：../开发工程师提示词.txt
- 历史提示词风格参考：../v1.0.0-mvp/design/api-implementation/prompts/DEV-010-TFI-MainAPI-Prompt.md

## 3) GOALS（卡片→可执行目标）
- 业务目标：提供显式 API track/trackAll/getChanges/clearAllTracking，供业务与 withTracked/stop 集成使用。
- 技术目标：在 TFI.java 中新增 4 个静态方法，委托 ChangeTracker；实现双层开关（TFI.globalEnabled 与 tfi.change-tracking.enabled）。

## 4) SCOPE
- In Scope：
  - [ ] 修改 `src/main/java/com/syy/taskflowinsight/api/TFI.java`：新增四个静态方法
  - [ ] 参数检查（null/空字符串）；异常通过 handleInternalError 处理
  - [ ] 双层开关：若任一关闭则快速返回
- Out of Scope：
  - [ ] SPI 策略扩展（M1）

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 受影响模块与文件：TFI.java。
2. 方法签名（新增）：
```java
public static void track(String name, Object target, String... fields)
public static void trackAll(java.util.Map<String,Object> targets)
public static java.util.List<com.syy.taskflowinsight.tracking.model.ChangeRecord> getChanges()
public static void clearAllTracking()
```
3. 实现：
   - isEnabled() && ChangeTracker 开关（卡240引入配置属性）
   - try/catch → 调用 com.syy.taskflowinsight.tracking.ChangeTracker 对应方法
4. 补丁：提供 TFI.java 的 diff（只新增方法，不改现有语义）。
5. 文档：卡片勾选；在 204/211 提示使用点。
6. 自测：本地编译；最小调用链与异常路径单测。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：TFI.java（新增四个方法的 diff）。
- 测试：TFI_ApiTests（禁用/启用、异常路径）。
- 文档：卡片勾选。
- 回滚/灰度：由全局与变更追踪开关控制（默认禁用）。
- 观测：错误通过 logger 记录（TFI.handleInternalError）。

## 7) API & MODELS（必须具体化）
- 接口签名：如上。
- 错误码/异常：内部异常被捕获并记录，不冒泡。
- DTO：使用 ChangeRecord 列表作为返回。

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 性能：禁用状态快速返回；启用状态最小开销（委托调用）。
- 可靠性：异常隔离；不影响业务线程。

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 80%
  - [ ] 禁用/启用切换；异常路径（ChangeTracker 抛错）
- 集成测试：
  - [ ] 与 203/204 联调在对应卡片完成
- 性能测试：
  - [ ] 见 250

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：四个 API 正常工作
- [ ] 文档：卡片勾选
- [ ] 观测：异常不冒泡
- [ ] 性能：禁用状态近零开销
- [ ] 风险：双开关逻辑覆盖

## 12) RISKS & MITIGATIONS
- ChangeTracker 未就绪 → 按依赖顺序提交；或在方法体内以空实现兜底（不建议）。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 变更追踪开关读取点（由卡240提供 ChangeTrackingProperties）。
- 责任人/截止日期/所需产物：配置类实现与注入方案确认。

