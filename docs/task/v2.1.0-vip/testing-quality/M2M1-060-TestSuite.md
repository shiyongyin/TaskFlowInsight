# testing-quality - M2M1-060-TestSuite（VIP 合并版）

1. 目标与范围
- 业务目标：建立覆盖 Snapshot/CollectionSummary/PathMatcher/Compare/Diff/Export/Starter/Endpoint 的测试套件；关键路径覆盖≥80%。

2. A/B 对比与取舍
- A 组优点：明确 compat/enhanced 断言分离与性能基线建议。
  - 参考：`../../v2.1.0-mvp/testing-quality/V210-060-Test-Suite.md`
- B 组优点：任务分解到位。
  - 参考：`../../v2.1.1-mvp/tasks/M2M1-060-TestSuite.md`
- 问题与风险：测试覆盖不足；断言口径不一致；测试执行时间过长。
- 冲突点清单：
  冲突#1：测试断言策略
    - 影响面：向后兼容性、测试维护成本
    - 方案A：compat/enhanced 双模式断言
    - 方案B：统一断言口径
    - 决策与理由：采用方案A，保证平滑过渡
    - 迁移与回滚：逐步迁移至 enhanced
  
  冲突#2：性能测试执行
    - 影响面：CI/CD 速度、资源消耗
    - 方案A：每次构建执行
    - 方案B：单独触发或定时执行
    - 决策与理由：采用方案B，平衡速度与质量
    - 迁移与回滚：通过 profile 控制

3. 最终设计（融合后）
- 单元：关键模块方法；
- 集成：AutoConfig + Endpoint；
- 并发：上下文隔离（必要处）；
- 回归：排序与字符串化稳定性；
- perf profile：单独触发，默认 CI 可禁长测。

4. 与代码差异与改造清单
- 新增 enhanced 场景断言；旧断言走 compat；
- 增加端点与指标一致性验证。

5. 开放问题与后续项
- 测试数据生成与基线更新机制。

---

