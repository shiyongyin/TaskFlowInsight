# V210-050: 指标与日志（最小集接入）

- 优先级：P0  
- 预估工期：S（<2天）  
- Phase：M1 P0  
- Owner：待定  
- 前置依赖：A/C/E 基础能力  
- 关联设计：`Guardrails & Monitoring – 指标最小集`

## 目标
- 接入并对外聚合导出最小指标集：
  - `tfi.diff.nested.depth.limit`
  - `tfi.diff.nested.cycle.skip`
  - `tfi.pathmatcher.compile.fail`
  - `tfi.cache.hit` / `tfi.cache.miss`
  - `tfi.collection.degrade.count`

## 实现要点
- Micrometer 接入（若已存在则补指标）；
- 与 Actuator 端点的只读展示对齐；
- 阈值建议由运维监控系统配置。

## 验收标准
- [ ] 指标随各场景触发而增长；
- [ ] 与只读端点输出一致。

## 对现有代码的影响（Impact）
- 影响级别：低。
- 行为：核心通过轻量接口计数；Starter 绑定 Micrometer；默认不开启不会引入运行时成本。
- 测试：新增指标递增与端点一致性的断言；原有用例不受影响。
