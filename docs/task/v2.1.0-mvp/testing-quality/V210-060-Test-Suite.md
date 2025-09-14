# V210-060: 测试套件（单元/集成/并发/回归）

- 优先级：P0  
- 预估工期：M（3–4天）  
- Phase：测试与发布  
- Owner：待定  
- 前置依赖：核心模块最小能力  
- 关联设计：`Testing & Quality – 测试矩阵`

## 目标
- 建立覆盖快照/集合摘要/路径匹配/规范化/护栏/导出/Starter/端点的测试套件；
- 关键路径覆盖率 ≥ 80%。

## 实现要点
- 单元：ObjectSnapshotDeep、CollectionSummary、PathMatcherCache、CompareService、DiffDetector、JsonLinesExporter；
- 集成：AutoConfiguration、有效配置端点；
- 并发：必要的上下文隔离用例；
- 回归：关键序列化/排序/repr 稳定性用例。

## 验收标准
- [ ] 通过率 100%；覆盖率达标；
- [ ] 基线不回退。

## 对现有代码的影响（Impact）
- 影响级别：中（测试层面）。
- 行为：需为新模块新增测试；导出/排序/repr/摘要化等旧断言可能需要调整或区分 compat/enhanced 场景。
- 运行：引入 perf profile（默认 CI 可禁长测）。
