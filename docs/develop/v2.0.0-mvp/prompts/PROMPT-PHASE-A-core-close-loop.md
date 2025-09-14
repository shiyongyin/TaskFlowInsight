## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。基于下述证据，按步骤一次性完成 Phase A（Core Close Loop）：201→202→203→210→204，实现可在 Console/JSON 看到 CHANGE 的最小闭环，并提交可运行代码/测试/文档与证据。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡集合：
  - ../cards-final/CARD-201-ChangeRecord-and-ObjectSnapshot.md
  - ../cards-final/CARD-202-DiffDetector-Scalar.md
  - ../cards-final/CARD-203-ChangeTracker-ThreadLocal.md
  - ../cards-final/CARD-210-TFI-APIs-Track-GetClear.md
  - ../cards-final/CARD-204-TFI-Stop-Integration.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码与符号：
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.stop(), handleInternalError(String,Throwable)
  - src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#com.syy.taskflowinsight.context.ManagedThreadContext
  - src/main/java/com/syy/taskflowinsight/model/TaskNode.java#com.syy.taskflowinsight.model.TaskNode.addMessage(String, com.syy.taskflowinsight.enums.MessageType)
  - src/main/java/com/syy/taskflowinsight/enums/MessageType.java#com.syy.taskflowinsight.enums.MessageType.CHANGE
  - src/main/java/com/syy/taskflowinsight/exporter/text/ConsoleExporter.java#com.syy.taskflowinsight.exporter.text.ConsoleExporter
  - src/main/java/com/syy/taskflowinsight/exporter/json/JsonExporter.java#com.syy.taskflowinsight.exporter.json.JsonExporter
- 工程操作规范：../开发工程师提示词.txt（必须遵循）
- 历史提示词风格参考：../v1.0.0-mvp/design/api-implementation/prompts/DEV-010-TFI-MainAPI-Prompt.md

## 3) GOALS（卡片→可执行目标）
- 业务目标：实现“显式 API + 标量 Diff + 任务树联动”，在 Console/JSON 中看到 CHANGE。
- 技术目标：新增 tracking 模块（ChangeRecord/ObjectSnapshot/DiffDetector/ChangeTracker），在 TFI 中新增 4 个 API，并在 stop 尾部刷新并清理；遵守 M0 边界（仅标量/字符串/日期；先转义后截断 8192）。

## 4) SCOPE
- In Scope（当次实现必做）：
  - [ ] 新建 tracking/model/ChangeRecord.java
  - [ ] 新建 tracking/snapshot/ObjectSnapshot.java（仅标量/字符串/日期；反射缓存+上限）
  - [ ] 新建 tracking/detector/DiffDetector.java（CREATE/UPDATE/DELETE；Date→long）
  - [ ] 新建 tracking/ChangeTracker.java（ThreadLocal 基线；getChanges → diff → 更新基线；clearAll）
  - [ ] 修改 api/TFI.java（新增 track/trackAll/getChanges/clearAllTracking；stop 尾部 flush）
  - [ ] 单测最小集：ObjectSnapshot、DiffDetector 基本用例；stop 集成烟囱；Console/JSON 片段
- Out of Scope（排除项）：
  - [ ] 集合/Map 摘要、SPI、自适应水位、持久化

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 类与方法签名（汇总）：
```java
// tracking/model/ChangeRecord.java（见 CARD-201 提示词）
// tracking/snapshot/ObjectSnapshot.java：static Map<String,Object> capture(String name, Object target, String... fields)
// tracking/detector/DiffDetector.java：static List<ChangeRecord> diff(String objectName, Map<String,Object> before, Map<String,Object> after)
// tracking/ChangeTracker.java：track/trackAll/getChanges/clearAllTracking
// api/TFI.java：新增4个API + stop 尾部 flush（formatChangeMessage 私有函数）
```
2. 实施顺序：201 → 202 → 203 → 210 → 204。
3. 最小改动原则：新增 tracking 包，不改既有导出器；TFI 仅增方法与在 stop 尾部追加 flush。
4. 统一工具：字符串转义+截断与消息格式化在 TFI 私有函数集中实现，避免散落实现。
5. 自测：最小样例对象与两个字段；Console/JSON 片段包含 `Object.field: OLD → NEW`。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：
  - 新文件：ChangeRecord.java、ObjectSnapshot.java、DiffDetector.java、ChangeTracker.java（完整内容）
  - 变更：TFI.java（新增4方法与 stop 集成的 diff；含 formatChangeMessage）
- 测试：
  - 基础单测：ObjectSnapshotTests、DiffDetectorTests
  - 集成烟囱：StopIntegrationIT（Console/JSON 片段）
- 文档：
  - 将上述五张卡的☐逐项改为☑️；附 Console/JSON 片段
- 回滚/灰度：
  - 双层开关（全局 + 变更追踪，变更追踪开关由 240 引入，当前默认视为禁用时快速返回）；停止刷写逻辑仅追加在 stop 尾部，移除该段可回滚
- 观测：
  - 日志：WARN（同名覆盖）、DEBUG（采集失败与跳过）

## 7) API & MODELS（必须具体化）
- 接口签名：见 5) 中的汇总；返回值为 List<ChangeRecord> 或 void。
- 异常：统一由 TFI.handleInternalError 记录；不向业务冒泡。
- 模型：ChangeRecord 字段齐全；valueType/valueKind 在标量时可选填。

## 8) DATA & STORAGE
- 内存结构：反射元数据缓存（ConcurrentHashMap + 上限），ChangeTracker 的 ThreadLocal<Map<String, SnapshotEntry>>。

## 9) PERFORMANCE & RELIABILITY
- 延迟预算：2 字段 P95 ≤ 200μs（建议目标，阶段C由 250 基准报告验证）。
- 可靠性：三处清理将在 B/C 阶段补齐，当前 stop 已 finally 清理；异常不冒泡。

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] ObjectSnapshot：空值/长字符串截断/Date 深拷贝/缓存上限
  - [ ] DiffDetector：null/类型变化/相等/不等；Date→long
- 集成测试：
  - [ ] start→track→修改→stop：Console/JSON 含 CHANGE（正则断言）

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：闭环可见 CHANGE；无重复输出
- [ ] 文档：五张卡均☑️；附 Console/JSON 片段
- [ ] 观测：异常仅记录
- [ ] 性能：基本可用（详细报告在阶段C）
- [ ] 风险：ThreadLocal 清理在 stop 路径生效

## 12) RISKS & MITIGATIONS
- WeakReference vs API 提供 after 目标 → 选择 WeakReference 并注明限制，或在 210 明确调用靠近使用点。
- 格式化散落 → TFI 单点函数；统一“先转义后截断（8192）”。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无，MessageType.CHANGE 已存在；行号定位被移除，改为方法尾部锚点。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] valueType/valueKind 的填充策略确认（建议仅标量填）
- [ ] 反射缓存上限默认值（建议 1024）

