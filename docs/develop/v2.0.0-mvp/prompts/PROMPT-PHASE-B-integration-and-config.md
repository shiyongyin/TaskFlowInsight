## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。一次性完成 Phase B（Integration & Config）：220→221→230→240，确保上下文清理与传播稳定、导出可见、配置可注入。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡集合：
  - ../cards-final/CARD-220-ManagedThreadContext-Cleanup.md
  - ../cards-final/CARD-221-Context-Propagation-Executor.md
  - ../cards-final/CARD-230-Console-Json-ChangeMessage-Verification.md
  - ../cards-final/CARD-240-M0-Config-Keys-and-Defaults.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#close()
  - src/main/java/com/syy/taskflowinsight/context/TFIAwareExecutor.java#com.syy.taskflowinsight.context.TFIAwareExecutor
  - src/main/java/com/syy/taskflowinsight/context/SafeContextManager.java#wrapRunnable, wrapCallable
  - src/main/java/com/syy/taskflowinsight/exporter/text/ConsoleExporter.java#ConsoleExporter
  - src/main/java/com/syy/taskflowinsight/exporter/json/JsonExporter.java#JsonExporter
- 工程操作规范：../开发工程师提示词.txt

## 3) GOALS（卡片→可执行目标）
- 业务目标：稳定的线程清理与传播；导出中可见 CHANGE；提供变更追踪的配置承载。
- 技术目标：在 close() finally 调用 TFI.clearAllTracking；TFIAwareExecutor 并发验证；Console/JSON 验证；新增 ChangeTrackingProperties 并启用装配。

## 4) SCOPE
- In Scope：
  - [ ] 修改 ManagedThreadContext.close() finally：TFI.clearAllTracking()
  - [ ] 新增集成测试：上下文传播（10~16线程）与导出验证
  - [ ] 新增 ChangeTrackingProperties（enabled=false、valueReprMaxLength=8192、cleanupIntervalMinutes=5）并启用装配
- Out of Scope：
  - [ ] 修改 TFIAwareExecutor 实现

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 完成 240 配置类与装配启用。
2. 修改 close() finally 追加清理调用。
3. 编写 221/230 集成测试。
4. 自测运行并收集 Console/JSON 片段。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：ManagedThreadContext.java diff；ChangeTrackingProperties.java 新文件与启用装配。
- 测试：ContextPropagationIT、ExportVerificationIT。
- 文档：四张卡勾选；附 Console/JSON 片段。

## 7) API & MODELS（必须具体化）
- API：TFI.clearAllTracking（由 210 提供）；TFIAwareExecutor 提交任务；exportToJson/Console。

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 稳定性优先；无随机失败；幂等清理。

## 10) TEST PLAN（可运行、可断言）
- 220：close 后 getChanges 空；重复 close 幂等
- 221：N 线程并发，子节点 CHANGE 归属正确
- 230：Console/JSON 含 CHANGE（正则/包含断言）
- 240：配置默认值注入成功

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：清理/传播/导出/配置达标
- [ ] 文档：四卡均☑️；片段与注入证据

## 12) RISKS & MITIGATIONS
- 清理遗漏 → 262 再次兜底；本阶段先覆盖 close。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] ChangeTrackingProperties 是否需要更多键（暂按 M0 最小集）。

