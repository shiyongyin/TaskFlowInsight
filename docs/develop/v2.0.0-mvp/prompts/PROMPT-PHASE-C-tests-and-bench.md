## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。一次性完成 Phase C（Tests & Bench）：260→261→262→263→264→250，覆盖 Diff 单测、并发/生命周期/格式/缓存验证与基准报告。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡集合：
  - ../cards-final/CARD-260-Unit-DiffDetector-Scalar.md
  - ../cards-final/CARD-261-Concurrency-Isolation.md
  - ../cards-final/CARD-262-Lifecycle-Cleanup.md
  - ../cards-final/CARD-263-Message-Format.md
  - ../cards-final/CARD-264-Reflection-Metadata-Cache.md
  - ../cards-final/CARD-250-Benchmarks-JMH-or-Micro.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - tracking/detector/DiffDetector.java
  - context/TFIAwareExecutor.java、context/SafeContextManager.java
  - api/TFI.java#stop()/endSession()
  - exporter/ConsoleExporter.java、exporter/JsonExporter.java
  - tracking/snapshot/ObjectSnapshot.java
- 工程操作规范：../开发工程师提示词.txt

## 3) GOALS（卡片→可执行目标）
- 业务目标：验收变更追踪的正确性、稳定性与可读性；提供基准报告。
- 技术目标：按卡片编写测试与基准；建议目标：2 字段 P95 ≤ 200μs；CPU 占比为报告项。

## 4) SCOPE
- In Scope：
  - [ ] 单测：DiffDetector（等价类矩阵）
  - [ ] 集成：并发隔离、生命周期清理、变更消息格式、反射缓存验证
  - [ ] 基准：ChangeTrackingBenchmark
- Out of Scope：
  - [ ] 修改核心实现（除非为修复缺陷）

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 补齐 260 单测矩阵。
2. 编写 261/262/263/264 集成测试。
3. 新增 250 基准代码并产出报告。
4. 自测与输出证据（Console/JSON 片段、命中率、延迟报告）。

## 6) DELIVERABLES（输出必须包含）
- 测试代码：DiffDetectorTests、ContextPropagationIT、LifecycleCleanupIT、MessageFormatIT、ReflectionMetaCacheTests。
- 基准代码与报告：ChangeTrackingBenchmark.java + 报告（含机器/参数）。
- 文档：六张卡均☑️；附证据与报告。

## 7) API & MODELS（必须具体化）
- 无新增 API；调用现有接口与导出器。

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 目标：2 字段 P95 ≤ 200μs（建议）；无随机失败；缓存命中 ≥ 80%。

## 10) TEST PLAN（可运行、可断言）
- 详见各卡提示词。

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：全部测试通过；无随机失败
- [ ] 文档：六卡均☑️；报告提交
- [ ] 性能：建议目标达成或提交改进计划

## 12) RISKS & MITIGATIONS
- 基准环境波动 → 记录环境，跑多次取中。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 报告输出路径（建议：docs/perf/ 或卡片附录）。

