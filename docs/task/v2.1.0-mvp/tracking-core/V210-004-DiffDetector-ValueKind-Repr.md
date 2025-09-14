# V210-004: DiffDetector 扩展（valueKind/valueRepr 与稳定输出）

- 优先级：P0  
- 预估工期：L（5–8天）  
- 前置依赖：V210-001、V210-002（输入来源）  
- 关联设计：`3.x Diff 扩展与稳定表示`（参考设计文档章节标题）

## 目标
- 将两个快照视图进行字典序稳定比对，输出 `List<Change>`；
- 为每个变更项补充 `valueKind`（SCALAR/ENUM/NULL/MISSING/COLLECTION/MAP/ARRAY/NESTED/CYCLE/BINARY）与 `valueRepr`（稳定可读字符串）；
- 处理缺失/空/循环/二进制标识；
- 输出顺序稳定（按路径与类型）；
- 与 CompareService 规范化结果兼容。

### 流水线边界
- Pre‑normalize：由 CompareService 根据配置对原值进行规范化（容差/时间/字符串/identity‑paths）；
- Detect：DiffDetector 基于“规范化后的快照视图”进行稳定排序的差异输出；
- Export：导出器遵循 README“导出字段规范（JSON/JSONL）”。

## 实现要点
- 包与类：`tracking.detector.DiffDetector`（扩展）、`tracking.detector.ValueReprUtil`（新增）；
- 接口：`List<Change> detect(Map<String,Object> before, Map<String,Object> after, CompareContext ctx)`；
- 稳定排序：主键路径、次键类型；
- Repr 规则：时间用 ISO‑8601；字符串 trim/lowercase（在 Compare 前置规范化）；
- 指标：可选统计项，用于调试（非必须）。

## 测试要点
- 标量/枚举/空/缺失；集合摘要；循环/深度剪枝后的路径；
- 稳定性：多次运行输出一致；
- 兼容性：与现有导出器初步联动。

## 验收标准
- 功能正确、排序稳定、repr 一致；
- 质量与性能满足全局 DoD；
- 兼容默认 balanced 配置。

## 对现有代码的影响（Impact）
- 影响级别：低到中（主要在测试预期）。
- 行为：引入稳定排序与 valueRepr 统一口径；默认 compat 模式可保持旧输出（关闭增强字段）。
- 对外 API：Diff 消费端接口不变；增强仅在新模式增加字段，不删除旧字段。
- 测试：涉及顺序/字符串化的断言需按规范更新；建议优先在 enhanced 用例覆盖新行为。
