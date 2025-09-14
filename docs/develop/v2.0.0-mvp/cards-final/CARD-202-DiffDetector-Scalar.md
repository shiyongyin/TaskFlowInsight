---
id: TASK-202
title: DiffDetector（标量字段对比，合并版）
owner: 待指派
priority: P1
status: Planned
estimate: 3人时
dependencies:
  - TASK-201
source:
  gpt: ../cards-gpt/CARD-202-DiffDetector-Scalar.md
  opus: ../cards-opus/TFI-MVP-202-diffdetector-scalar.md
spec_ref: ../../../specs/m2/final/TaskFlow-Insight-M2-Design.md#模块设计
---

一、合并策略与差异评估
- 采纳（GPT）：字段全集并集；CREATE/DELETE/UPDATE 判定；Date 用时间戳比较；valueRepr 转义→截断；复杂对象不参与。
- 采纳（OPUS）：卡片元数据、覆盖率≥80%、DOR/DOD 口径。
- 舍弃/调整：
  - “落盘失败率 ≤ 1%”删除（无持久化）。
  - “查询 ≤ 200μs”改为“2 字段对比 P95 ≤ 200μs（建议目标）”。
  - CPU 占比为报告项，非 M0 硬门槛。

二、开发目标（可勾选）
- ☑ 实现 DiffDetector.diff(before, after) → List<ChangeRecord>（仅标量）。
- ☑ 值类型支持：String/Number/Boolean/Date（Date→long 比较）。
- ☑ valueRepr：先转义后截断（8192）。
- ☐ 预留 Strategy 扩展点（集合/Map、Mask/Serialize）。
- ☐ DELETE 场景 `valueRepr=null`；CREATE/UPDATE 使用新值 `ObjectSnapshot.repr(newValue)`。

三、开发清单（可勾选）
- ☑ 新增 tracking/detector/DiffDetector.java。
- ☑ 变更类型枚举/常量：CREATE/UPDATE/DELETE。
- ☑ 排序稳定输出（字典序），便于测试。

四、测试要求（可勾选）
- ☑ null/类型变化/相等/不等路径覆盖；String/Number/Boolean/Date 全覆盖。
- ☐ 100 次循环、2 字段性能烟囱（报告 P95，记录环境）。

五、关键指标（可勾选）
- ☐ 2 字段 P95 ≤ 200μs（建议目标）。

六、验收标准（可勾选）
- ☑ 单测通过；Diff 结果稳定一致；异常不冒泡。
- ☑ 与 ObjectSnapshot/ChangeTracker 对接无缝。

七、风险评估（可勾选）
- ☑ equals 代价/异常；通过标量限定+Objects.equals/Date 归一化防护。
- ☐ 时间度量误差；采用 JMH 或稳定微基准。

八、核心技术设计（必读）与骨架
```java
public final class DiffDetector {
  public static List<ChangeRecord> diff(String obj, Map<String,Object> b, Map<String,Object> a, Ctx ctx){ /* 并集+判定 */ }
  static Object normalize(Object v){ return (v instanceof Date d)? d.getTime(): v; }
}
```

十—、澄清与范围界定（2.1 临时口径）
- 本卡不引入 CompareService 的“对比前规范化”（容差、大小写、空白、UTC 等）；暂保持 Diff 仅做 Date→long 与排序输出。
- 预规范化能力将由后续 CompareService（默认关闭、可选开启）在 Starter 中提供；与 DiffDetector 职责隔离。

九、DOR/DOD（整合）
- DOR：TASK-201 完成；需求输入齐备。
- DOD：覆盖≥80%；建议目标达成或给出计划；文档完成。

十、冲突与建议（需拍板）
- 指标措辞“查询 ≤ 200μs”调整为“2 字段对比 P95 ≤ 200μs（建议）”。
- 确认：指标措辞“查询 ≤ 200μs”调整为“2 字段对比 P95 ≤ 200μs”。

十一、引用
- GPT：cards-gpt/CARD-202-DiffDetector-Scalar.md
- OPUS：cards-opus/TFI-MVP-202-diffdetector-scalar.md
- 设计：specs/m2/final/TaskFlow-Insight-M2-Design.md
