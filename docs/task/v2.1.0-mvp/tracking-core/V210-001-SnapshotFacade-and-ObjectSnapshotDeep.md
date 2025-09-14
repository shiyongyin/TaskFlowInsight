# V210-001: SnapshotFacade 与 ObjectSnapshotDeep 实现

- 优先级：P0  
- 预估工期：L（5–8天）  
- Phase：M1 P0  
- Owner：待定  
- 前置依赖：无  
- 关联设计：`3.x Tracking Core – 嵌套对象扁平化与护栏`（参考设计文档章节标题）

## 背景（Background）
为变更追踪提供可控、可观测的深度对象快照能力：串行 DFS、深度/栈深度护栏、循环检测、异常路径不合并（局部提交）。输出用于后续 Diff 与导出。

## 目标（Goals）
- 串行 DFS 遍历对象图，生成扁平路径键的结构化 Map/视图；
- 护栏：`maxDepth`（默认 3，可配置）、`MAX_STACK_DEPTH≈1000` 常量；
- 循环检测：`IdentityHashMap` 路径栈；
- 局部提交：异常路径不合并、finally 出栈；
- Path 匹配白/黑名单：进入节点前判断，未命中分支不展开；
- 集合/Map 不展开，统一交由 CollectionSummary；
- 指标：`depth.limit`、`cycle.skip` 计数。

## 非目标（Non‑Goals）
- 不实现并行 DFS；
- 不展开集合/Map 子元素；
- 不引入平台特化优化与第三方反射库。

## 核心实现要点（How）
- 包与类（建议）：
  - `com.syy.taskflowinsight.tracking.snapshot.SnapshotFacade`
  - `com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep`
- API（示例）：
  - `Map<String, Object> SnapshotFacade.capture(Object root, SnapshotConfig cfg)`
  - `SnapshotConfig { int maxDepth; PathMatcher include; PathMatcher exclude; ... }`
- 算法：
  - DFS(rootPath, obj, depth):
    - 若 null/标量/枚举/日期 → normalize 后 put；
    - 若在 `currentPath`（IdentityHashMap）中 → `cycle.skip++` 并 return；
    - 若 `depth>=maxDepth` 或 栈深度超限 → `depth.limit++` 并 return；
    - 入栈 try {
      - 若集合/Map → 调用 CollectionSummary.summarize(path, obj)
      - 否则反射字段 `trySetAccessible()` 读取，按 include/exclude 与顺序产出子路径；
      } finally { 出栈 }
- 反射与可访问性：`trySetAccessible()` 失败跳过，debug 记录。

### 与现有 ObjectSnapshot 的集成（包装+扩展）
- 现有 `tracking.snapshot.ObjectSnapshot` 保留用于“标量/浅层快照”；
- 新增 `ObjectSnapshotDeep` 负责嵌套结构与护栏；
- `SnapshotFacade` 作为统一入口：按节点类型路由到现有 ObjectSnapshot（标量/浅层）或 Deep（嵌套），集合/Map 统一路由到 `CollectionSummary`；
- 外部调用保持向后兼容，不直接依赖具体实现类。

### 并发与上下文边界
- 禁止并行 DFS，禁止使用 `parallelStream`；
- 统一依托已有 `ManagedThreadContext/SafeContextManager`，严禁新增 ThreadLocal；
- 由 Starter 注入 `ThreadLocalManager`（见 V210-051），提供清理与快照能力。

### 配置（建议，默认 balanced）
- `tfi.change-tracking.max-depth`（默认 3）
- `tfi.change-tracking.include-paths`（可选，列表）
- `tfi.change-tracking.exclude-paths`（可选，列表）
- `tfi.change-tracking.max-stack-depth`（常量 1000，不建议暴露为配置）

示例 YAML：
```yaml
tfi:
  change-tracking:
    enabled: true
    max-depth: 3
    include-paths: ["order/**", "user/*/name"]
    exclude-paths: ["**/password", "**/token"]
```

### 匹配与优先级
- 进入节点前进行 include/exclude 匹配；未命中分支不展开；
- 优先级：`exclude > include > default`；

### 与 Compare/Diff 的边界
- Snapshot 仅负责遍历与采样，不做值的规范化；
- 值规范化（绝对容差、时间/字符串、identity‑paths）在 CompareService 的 pre‑normalize 阶段统一处理；
- DiffDetector 仅消费规范化后的视图并输出稳定变更项。

## 安全与合规
- 仅收集“可访问字段”；
- 对潜在敏感名称（password/token/secret）字段屏蔽或交由路径过滤处理。

## 测试要点（Testing）
- 单测：
  - 正常对象/嵌套对象；循环引用剪枝；深度限制；异常路径不合并；
  - 反射访问失败分支；include/exclude 生效；
  - 指标累加与重置；
- 性能：
  - 5 层、50 字段样例运行时间、CPU 开销记录；
  - 基线与不回退对比；

## 验收标准（Acceptance）
- [ ] 功能：上述场景均覆盖，快照输出与护栏行为符合预期；
- [ ] 质量：关键路径覆盖 ≥ 80%；
- [ ] 性能：不劣化既有基线，样例规模 CPU 开销 < 5%；
- [ ] 可观测：`depth.limit / cycle.skip` 指标可读；
- [ ] 兼容：默认 balanced 配置，无破坏性变更；
- [ ] 回退：可通过配置完全禁用该功能路径。

## 依赖（Dependencies）
- 强依赖：无；
- 弱依赖：PathMatcherCache（V210-003）；
- 软依赖：CollectionSummary（V210-002）。

## 风险与缓解
- 深层对象遍历引发性能劣化 → 护栏 + 路径过滤 + 指标告警 + 样例基线；
- 反射访问抖动 → 字段元数据缓存（上限 1024 类）；
- 栈溢出 → `MAX_STACK_DEPTH≈1000` 硬限制。

## 代码映射（建议）
- 新增：`tracking.snapshot.SnapshotFacade`、`tracking.snapshot.ObjectSnapshotDeep`；
- 修改：`tracking.ChangeTracker`（接入 SnapshotFacade）；
- 测试：`ObjectSnapshotDeepTests`、`SnapshotFacadeTests`。

## 对现有代码的影响（Impact）
- 影响级别：低（新增与可选接入为主）。
- 对外 API：TFI/TaskContext/Session/TaskNode 无变更；调用方无感知。
- 内部路由：`ChangeTracker` 可通过开关路由到 `SnapshotFacade`；关闭开关时沿用旧路径，行为不变。
- 测试：新增 Deep/Facade 覆盖；原有断言不需变更。
- 缓解策略：feature flag + 默认 balanced；出现异常可回退到旧路径。
