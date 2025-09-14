# tracking-core - M2M1-004-DiffDetector-Extension（VIP 合并版）

1. 目标与范围
- 业务目标：输出稳定、可读、可比较的变更集，便于导出与回归。
- Out of Scope：树形复杂变更模型。

2. A/B 对比与取舍
- A 组优点：定义 valueKind/valueRepr、路径字典序排序；Compare pre‑normalize → Diff detect → Export。
  - 参考：`../../v2.1.0-mvp/tracking-core/V210-004-DiffDetector-ValueKind-Repr.md`
- B 组优点：扩展点定义与任务落地明确。
  - 参考：`../../v2.1.1-mvp/tasks/M2M1-004-DiffDetector-Extension.md`
- 冲突点清单：
  冲突#1：是否在 Diff 内处理规范化
    - 决策：否；规范化在 CompareService（pre‑normalize）完成，Diff 仅负责排序与输出

3. 最终设计（融合后）
3.1 数据模型
- `Change{ path, kind(valueKind), reprOld, reprNew }`；增强模式可附 raw 字段。
3.2 流程与状态
- 接收规范化后的快照视图，进行稳定排序比对。
3.3 非功能
- 多次运行输出稳定；compat/enhanced 双模式；可观测（调试计数可选）。
3.4 兼容与迁移
- compat 保持旧输出；enhanced 新增字段，不删除旧字段。

4. 与代码差异与改造清单
- 扩展 DiffDetector 输出模型与排序；统一 valueRepr 规则；
- 导出器同步字段口径；
- 测试：排序/字符串化/模式切换断言。

5. 开放问题与后续项
- raw 字段是否仅在 JSONL 增强模式输出。

---

