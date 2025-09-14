# compare-strategy - M2M1-020-CompareService（VIP 合并版）

1. 目标与范围
- 业务目标：在 Diff 前统一值的“可比较表示”，降低无谓差异，提高结果稳定性。
- Out of Scope：复杂规则引擎、路径级大量定制（仅保留 identity-paths）。

2. A/B 对比与取舍
- A 组优点：将 Compare 定位为 pre‑normalize，明确与 Diff 的边界；默认容差=0、UTC、trim+lowercase。
  - 参考：`../../v2.1.0-mvp/compare-strategy/V210-020-CompareService-Normalization.md`
- B 组优点：任务结构清晰。
  - 参考：`../../v2.1.1-mvp/tasks/M2M1-020-CompareService.md`
- 问题与风险：规范化规则过度导致误判；性能开销；规则冲突。
- 冲突点清单：
  冲突#1：规范化的职责归属
    - 影响面：模块边界、处理流程、性能
    - 方案A：CompareService 负责规范化（pre-normalize）
    - 方案B：DiffDetector 内部处理规范化
    - 决策与理由：采用方案A，职责分离清晰，便于测试和维护
    - 迁移与回滚：通过开关控制是否启用规范化
  
  冲突#2：默认规范化策略
    - 影响面：比较结果准确性、向后兼容性
    - 方案A：默认启用所有规范化（数值容差、时间UTC、字符串trim）
    - 方案B：默认关闭，按需启用
    - 决策与理由：采用方案B，保持向后兼容，避免破坏现有断言
    - 迁移与回滚：配置项控制各类规范化策略

3. 最终设计（融合后）
3.1 接口与契约
- `int compare(String path, Object a, Object b, CompareContext ctx)`；
- 规范化：数值（绝对容差）、时间（UTC/ISO‑8601）、字符串（trim/lowercase）、identity‑paths 短路。
3.2 非功能
- 开关：默认启用规范化（容差=0），不改变旧断言大逻辑；
- 可观测：必要时统计规范化命中率（开发期）。

4. 与代码差异与改造清单
- 新增 CompareService 与上下文；DiffDetector 接收已规范化值；
- 测试：规范化边界用例、与 Diff 的集成冒烟。

5. 开放问题与后续项
- identity‑paths 的默认集合维护（**/id, **/uuid）。

---

