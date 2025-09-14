Title: AI 开发执行指南（基于 cards-final）

目标
- 指导 AI（或自动化开发助理）按 cards-final 的合并卡片进行实现、代码自检与测试编写，确保产物与设计一致、范围收敛、质量可验收。

读取优先级（每次执行前）
1) 索引与任务卡
   - 必读：`docs/develop/v2.0.0-mvp/cards-final/INDEX.md`（确定下一个待办与依赖是否满足）。
   - 必读：对应合并卡（主文件）：`cards-final/CARD-<ID>-<name>.md`。
   - 参考：对应 OPUS 别名卡（仅指引），无需重复维护正文。
2) 设计/规格
   - 必读：卡片 `spec_ref` 指向的章节（通常是 `docs/specs/m2/final/TaskFlow-Insight-M2-Design.md`）。
   - 参考：`docs/specs/m2/final/TaskFlow-Insight-M2-PRD.md` 中对应需求表项（M2-F001/2/3……）。
3) 源任务与对照
   - 参考：`docs/task/v2.0.0-mvp/<...>` 源任务文档（用于语义对照）；如与合并卡冲突，以 cards-final 为准。
4) 代码集成点（按卡片“开发清单”执行前快速定位）
   - `src/main/java/com/syy/taskflowinsight/api/TFI.java`
   - `src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java`
   - `src/main/java/com/syy/taskflowinsight/context/TFIAwareExecutor.java`
   - `src/main/java/com/syy/taskflowinsight/model/{TaskNode,Message,Session}.java`
   - `src/main/java/com/syy/taskflowinsight/exporter/{text/ConsoleExporter,json/JsonExporter}.java`
   - 新增：`src/main/java/com/syy/taskflowinsight/tracking/**`（model/snapshot/detector/ChangeTracker）

实施顺序（推荐遵循 INDEX）
- Phase A：201 → 202 → 203 → 210 → 204
- Phase B：220 → 221 → 230 → 240
- Phase C：260 → 261 → 262 → 263 → 264 → 250
- Phase D：270

代码实现守则（M0 收敛必遵）
- 功能边界：
  - 仅采集/对比 标量/字符串/日期；复杂对象/集合/Map 不进入快照；Date 归一化 long。
  - valueRepr：先转义后截断（默认 8192，尾部 `... (truncated)`）；空值 `null`。
  - stop 刷新：`TFI.stop()` 尾部统一 getChanges→写 CHANGE→clearAll；withTracked 在 finally 即时刷写并清理，stop 不重复输出。
  - 三处清理：`TFI.stop()`、`ManagedThreadContext.close()`、`TFI.endSession()` 一致且幂等。
- 配置：
  - 直接使用 Spring `@ConfigurationProperties`（`ChangeTrackingProperties`，前缀 `tfi.change-tracking`）。
  - 双层开关：`TFI.globalEnabled && properties.enabled` 才执行；否则快速返回。
- 性能与依赖：
  - 反射元数据缓存使用 `ConcurrentHashMap` + 上限（如 1024 类）；`computeIfAbsent` 单点构建；不引第三方缓存（M0）。
  - 自适应水位策略（动态截断）推迟到 M1。
- 日志与异常：
  - 门面与内部异常走 `TFI.handleInternalError`；日志级别以 WARN/DEBUG 为主；异常不冒泡业务层。
- 工程风格：Java 21、Spring Boot 3.5.x、4 空格、120 列软换行；少量明确使用 Lombok；Javadoc 摘要+非平凡参数/返回说明。

每个任务的标准执行流程（循环）
1) 选定任务
   - 从 `INDEX.md` 确认依赖满足；打开对应 `CARD-xxx`（主文件），阅读“开发目标/开发清单/核心技术设计/核心代码骨架/测试要求/关键指标/验收/风险”。
   - 快速对照 `spec_ref` 章节与 `docs/task/v2.0.0-mvp` 源任务。
2) 代码定位
   - 使用 ripgrep（rg）定位卡片列出的集成点；创建或打开对应包/类文件。
3) 实现最小骨架
   - 按卡片“核心代码骨架”先落主方法签名与结构（编译通过）。
   - 统一实现/复用 “转义→截断” 与 “CHANGE 格式化” 的帮助函数（单点）。
4) 单元测试（先写/同步写）
   - 基于卡片“测试要求”的用例矩阵先写核心路径与边界；包路径与产物一致；覆盖≥80%。
   - 对于并发与集成（如 221/262/230/204），编写 slice/integration 测试，但避免不必要的 @SpringBootTest。
5) 迭代完善
   - 扩充实现逻辑满足“开发清单”全部子项；每完成一子项，补充/修正测试。
6) 本地验证
   - 运行：`./mvnw -q -DskipTests=false test` 或 `./mvnw clean verify`。
   - 如卡片涉及 Demo/导出，手动用 `TFI.start/stop` 演练，调用 `exportToConsole()/exportToJson()` 检查 CHANGE 呈现。
7) 自检与审核
   - 严格逐项勾选卡片中的☐ 清单项：开发清单/测试/指标/验收/风险是否落实。
   - 代码审核清单：
     - 线程安全：ThreadLocal 清理与隔离（尤其线程池复用）。
     - 性能：反射缓存上限；不对复杂对象调用重型 toString。
     - 错误处理：不冒泡；日志级别合理；格式化/截断单点复用。
     - 配置：双开关路径覆盖；默认值与文档一致。
     - 文档：Javadoc/README 片段与卡片一致。
8) 产物与移交
   - 代码变更最小化；不改无关文件；必要时在卡片“冲突与建议”处补充结论。
   - 输出运行证据（Console/JSON 片段用于 204/230/263 等）。

典型任务的开发要点速查
- 201（ChangeRecord & ObjectSnapshot）：仅标量/字符串/日期；元数据缓存+上限；日期深拷贝；“转义→截断”。
- 202（DiffDetector）：并集字段；CREATE/DELETE/UPDATE 判定；Date 用 long；字典序稳定输出。
- 203（ChangeTracker）：ThreadLocal 基线；getChanges 时 capture-after+diff+更新基线；三处清理；定时器默认关闭。
- 210（TFI APIs）：track/trackAll/getChanges/clearAllTracking 委托 ChangeTracker；双开关；异常处理统一。
- 204（stop 集成）：尾部 flush；格式 `<obj>.<field>: <old> → <new>`；withTracked 已清理避免重复。
- 220/221：close 清理、上下文传播校验（子线程归属）；不修改 TFIAwareExecutor。
- 230/263：导出与格式正则；Console/JSON 一致；不改导出器；先转义后截断。
- 240：Spring Properties 直落；默认值与启用/禁用覆盖路径。
- 264：缓存命中/上限/并发正确性；不引三方缓存（M0）。
- 250：JMH 优先；报告 P95 延迟，记录机器/参数；CPU 仅报告项。
- 270：显式与便捷 API 并排演示；CHANGE 可见。

命令速查
- 构建与测试：
  - `./mvnw clean verify`（完整构建+测试）
  - `./mvnw test`（仅测试）
  - `./mvnw spring-boot:run`（本地运行，可配 `SPRING_PROFILES_ACTIVE`）
- 读取/定位：
  - `rg --files docs/develop/v2.0.0-mvp/cards-final | sort`
  - `rg -n "class ChangeTracker|TFI\.stop|ManagedThreadContext" -S src/main/java`

Do / Don’t（易错项）
- Do：
  - 用 cards-final 的合并卡作为唯一执行依据；其它方案仅参考。
  - 在同一处维护“格式化/转义/截断”的帮助函数，避免散落实现。
  - 在 withTracked finally 清理，依赖该语义避免 stop 重复输出。
- Don’t：
  - 不在 M0 实现自适应水位策略；不引入新第三方缓存依赖。
  - 不对复杂对象/集合进行 toString 展开或递归采集。
  - 不使用行号定位做修改；不更改 MessageType 枚举定义。

附：首次落地建议节奏
1) 落 201/202 骨架与测试；
2) 落 203 + 210，完成最小可运行；
3) 落 204，Console/JSON 看到 CHANGE；
4) 补 220/221/230/263/262/264 测试；
5) 270 Demo 与 250 报告；
6) 240 配置注入与开关验证。

