## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。请一次性完成 M2‑M0 全部阶段（Phase A→B→C→D）的实现、测试与证据汇总，并输出可提交补丁与卡片勾选列表。

## 2) CONTEXT & SOURCES（务必具体）
- 阶段提示词：
  - Phase A：../prompts/PROMPT-PHASE-A-core-close-loop.md
  - Phase B：../prompts/PROMPT-PHASE-B-integration-and-config.md
  - Phase C：../prompts/PROMPT-PHASE-C-tests-and-bench.md
  - Phase D：../prompts/PROMPT-PHASE-D-demo.md
- 合并卡索引：../cards-final/INDEX.md（依赖顺序）
- 合并卡总览：../cards-final/*.md（每张卡执行后需☐→☑️）
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md（开发守则与门槛）
- 工程规范：../开发工程师提示词.txt（四阶段执行法）
- 代码与关键符号（只读）：
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.stop(), handleInternalError(String,Throwable)
  - src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#close()
  - src/main/java/com/syy/taskflowinsight/context/TFIAwareExecutor.java#com.syy.taskflowinsight.context.TFIAwareExecutor
  - src/main/java/com/syy/taskflowinsight/model/TaskNode.java#addMessage(String, com.syy.taskflowinsight.enums.MessageType)
  - src/main/java/com/syy/taskflowinsight/enums/MessageType.java#CHANGE
  - src/main/java/com/syy/taskflowinsight/exporter/text/ConsoleExporter.java#ConsoleExporter
  - src/main/java/com/syy/taskflowinsight/exporter/json/JsonExporter.java#JsonExporter

## 3) GOALS（卡片→可执行目标）
- 业务目标：交付“显式 API + 标量 Diff + 任务树联动 + 导出可见 + 全套测试与 Demo”的 M2‑M0 MVP。
- 技术目标：新增 tracking 模块与必要集成，补齐清理/传播/导出/配置与测试、基准与 Demo，产出证据并勾选卡片。

## 4) SCOPE
- In Scope：
  - [ ] Phase A：201/202/203/210/204（闭环与 CHANGE 可见）
  - [ ] Phase B：220/221/230/240（清理/传播/导出/配置）
  - [ ] Phase C：260/261/262/263/264/250（测试套件与基准报告）
  - [ ] Phase D：270（Demo 展示）
- Out of Scope：
  - [ ] 自适应水位策略、SPI、集合/Map 递归、持久化（M1）

## 5) EXECUTION PLAN（端到端动作顺序）
1. Phase A：逐一执行 `PROMPT-PHASE-A-core-close-loop.md` 的 Coding Plan，新增 tracking/** 与修改 api/TFI.java；提交最小单测与 Console/JSON 片段。
2. Phase B：执行 `PROMPT-PHASE-B-integration-and-config.md`，实现 close() 清理、上下文传播与导出验证、新增 ChangeTrackingProperties。
3. Phase C：执行 `PROMPT-PHASE-C-tests-and-bench.md`，补齐单测/集成/基准与报告。
4. Phase D：执行 `PROMPT-PHASE-D-demo.md`，补充 Demo 与证据。
5. 逐卡片将☐→☑️，记录证据位置（文件/行/片段）。

## 6) DELIVERABLES（输出必须包含）
- 代码改动（统一 diff 与新增文件内容清单）：
  - 新增：`src/main/java/com/syy/taskflowinsight/tracking/**`
  - 变更：`src/main/java/com/syy/taskflowinsight/api/TFI.java`（新增 4 API + stop 刷新）
  - 变更：`src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#close()`（finally 清理）
  - 新增：`src/main/java/com/syy/taskflowinsight/config/ChangeTrackingProperties.java`（+ 启用装配）
- 测试：
  - 单测：DiffDetectorTests、ObjectSnapshotTests 等
  - 集成：ContextPropagationIT、LifecycleCleanupIT、MessageFormatIT、ExportVerificationIT、ReflectionMetaCacheTests
  - 基准：ChangeTrackingBenchmark.java + 报告（含环境参数）
- 运行与证据：
  - `./mvnw -q -DskipTests=false test` 输出（总数/通过/失败=0）
  - Console 与 JSON 的 `CHANGE` 片段（正则断言口径）
- 卡片勾选：
  - 将 `cards-final/*.md` 对应☐改为☑️，并返回“已勾选项清单 + 证据路径”

## 7) ACCEPTANCE（核对清单，默认空）
- [ ] Gate A：Console/JSON 出现 `CHANGE`；201/202/203/210/204 ☑️
- [ ] Gate B：220/221/230/240 ☑️（并发稳定；配置注入默认值）
- [ ] Gate C：260/261/262/263/264/250 ☑️（测试通过；基准报告提交）
- [ ] Gate D：270 ☑️（Demo 可运行，显式与 withTracked 输出等价且不重复）

## 8) PERFORMANCE & RELIABILITY
- 建议目标：2 字段 P95 ≤ 200μs；CPU 占比为报告项。
- 可靠性：三处清理一致且幂等；并发无交叉污染。

## 9) RISKS & MITIGATIONS
- 线程池复用串扰 → stop/close/endSession 三处清理 + 子线程 close
- 反射缓存膨胀 → 上限与拒绝新增；不展开复杂对象/集合
- 格式散落 → TFI 私有单点格式化；“先转义后截断（8192）”

## 10) REPORT（一次性回传）
- 变更清单与关键代码点（路径#符号）
- 测试执行摘要与失败=0 证据
- Console/JSON 的 `CHANGE` 片段
- 基准报告（P50/P95 + 环境参数）
- 卡片勾选项列表（☑️）与证据路径

## 11) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] valueType/valueKind 的填充策略（建议仅标量填）
- [ ] 反射缓存上限默认值（建议 1024）
- [ ] ChangeTracker 是否保存 WeakReference 还是要求 API 层靠近使用点刷新（建议前者并标注限制）

