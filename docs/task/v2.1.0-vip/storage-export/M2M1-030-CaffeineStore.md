# storage-export - M2M1-030-CaffeineStore（VIP 合并版）

1. 目标与范围
- 业务目标：提供可选的内存存储与查询能力（默认关闭），不影响核心路径。
- Out of Scope：分布式持久化、复杂查询语言。

2. A/B 对比与取舍
- A 组优点：明确"可选组件 + 默认 disabled + Starter 引入依赖"。
  - 参考：`../../v2.1.0-mvp/storage-export/V210-030-Caffeine-Store-Query.md`
- B 组优点：任务命名与目标清晰。
  - 参考：`../../v2.1.1-mvp/tasks/M2M1-030-CaffeineStore.md`
- 问题与风险：内存占用不可控；查询能力期望过高；与核心功能耦合。
- 冲突点清单：
  冲突#1：存储组件是否默认启用
    - 影响面：内存占用、性能影响、依赖管理
    - 方案A：默认启用，提供开箱即用体验
    - 方案B：默认关闭，需显式配置启用
    - 决策与理由：采用方案B，零入侵原则，避免影响核心路径
    - 迁移与回滚：通过 tfi.change-tracking.store.enabled 控制
  
  冲突#2：查询能力范围
    - 影响面：实现复杂度、API 设计
    - 方案A：仅支持按 key 查询
    - 方案B：支持复杂查询（路径前缀、时间范围等）
    - 决策与理由：MVP 采用方案A，保持简单，后续可扩展
    - 迁移与回滚：预留查询接口扩展点

3. 最终设计（融合后）
3.1 接口与契约
- `ChangeStore` 接口 + `CaffeineChangeStore` 实现；TTL、max-size、回压策略。
3.2 非功能
- 默认关闭；仅 Starter 引入依赖；零入侵。

4. 与代码差异与改造清单
- 新增 Store 接口与实现；AutoConfig 条件化装配；
- 测试：开启/关闭、TTL 淘汰、回压触发。

5. 开放问题与后续项
- 是否需要简单的查询语义（path 前缀、sessionId 过滤）。

---

