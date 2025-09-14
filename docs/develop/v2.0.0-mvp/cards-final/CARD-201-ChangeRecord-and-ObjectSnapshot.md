---
id: TASK-201
title: ChangeRecord 与 ObjectSnapshot（合并版）
owner: 待指派
priority: P1
status: Planned
estimate: 4人时
dependencies: []
source:
  gpt: ../cards-gpt/CARD-201-ChangeRecord-and-ObjectSnapshot.md
  opus: ../cards-opus/TFI-MVP-201-changerecord-objectsnapshot.md
spec_ref: ../../../specs/m2/final/TaskFlow-Insight-M2-Design.md#数据与存储
---

一、合并策略与差异评估
- 采纳（GPT）：
  - 仅标量/字符串/日期入快照；复杂对象/集合/Map 不采集（M0）。
  - valueRepr“先转义后截断（8192，… (truncated)）”，空值输出null；Date 深拷贝。
  - 反射元数据缓存 + 容量上限；并发容器；异常仅 DEBUG。
- 采纳（OPUS）：
  - 卡片元数据（owner/priority/status/estimate）、DOR/DOD 口径与依赖占位。
  - 单测覆盖率≥80% 作为质量门槛。
- 舍弃/调整（与设计或M0收敛冲突）：
  - “落盘失败率 ≤ 1%”：M0 无持久化，移除。
  - “检索 ≤ 200μs @ 2字段规模”文案调整为“2 字段对比 P95 ≤ 200μs（建议目标）”。
  - CPU 占比作为观察项，不作为 M0 硬门槛（见 README/ROADMAP 调整）。

二、开发目标（可勾选）
- ☑ 建立 ChangeRecord 数据模型（objectName/fieldName/oldValue/newValue/timestamp/sessionId/taskPath/changeType/valueType/valueKind/valueRepr）。
- ☑ 实现 ObjectSnapshot.capture(name, target, fields) → Map<String,Object>（仅标量/字符串/日期）。
- ☑ valueRepr 生成规范：转义→截断（8192，… (truncated)），空值 null。
- ☑ Date 深拷贝；反射元数据缓存（并发+上限）。
- ☐ Spring Properties 注入（tfi.change-tracking.*），System.getProperty 仅极端 fallback。
- ☐ 增加 `ObjectSnapshot#setMaxCachedClasses(int)` 并由 AutoConfiguration 注入 `ChangeTrackingProperties.maxCachedClasses`（上限仍默认1024）。

三、开发清单（可勾选）
- ☑ 新增 tracking/model/ChangeRecord.java（Javadoc 完整）。
- ☑ 新增 tracking/snapshot/ObjectSnapshot.java（白名单字段/元数据缓存）。
- ☑ Repr 工具：escape+truncate；标量判定/归一化（Date→long）。
- ☑ 日志：字段不可达/异常 DEBUG；不冒泡业务。

四、测试要求（可勾选）
- ☑ 单测覆盖率 ≥ 80%；空值/类型变化/边界值完整。
- ☑ 长字符串先转义后截断；包含引号/换行等字符稳定。
- ☑ 日期深拷贝验证；复杂对象/集合不入快照验证。
- ☑ 反射缓存命中率提升与容量上限行为验证。

五、关键指标（可勾选）
- ☐ 2 字段 P95 ≤ 200μs（建议目标，报告机器/参数）。
- ☐ 热路径反射缓存命中率 ≥ 80%。
- ☐ 禁用（enabled=false）时入口快速返回。

六、验收标准（可勾选）
- ☐ 单/集成测试通过；接口与设计一致；Javadoc 完整。
- ☐ 与 TASK-202/203 对接无缝（Map 快照可被 DiffDetector 消费）。
- ☐ 日志仅 DEBUG/WARN；无异常冒泡。

七、风险评估（可勾选）
- ☐ 反射性能风险：元数据缓存 + 白名单；容量上限与拒绝新增策略。
- ☐ 线程安全：并发容器；只读路径无写入竞争。
- ☐ 范围歧义：明确 M0 不采集复杂/集合/Map，避免误报与重型成本。

八、核心技术设计（必读）
- 数据模型：changeType=CREATE/UPDATE/DELETE；valueType（FQCN）、valueKind（STRING/NUMBER/BOOLEAN/DATE 可选）。valueRepr 仅用于展示。
- 快照规则：白名单字段（setAccessible）；最大深度=2 防御性；M0 不展开复杂类型。
- 反射缓存：ConcurrentHashMap<Class<?>,Map<String,Field>> + computeIfAbsent；上限如 1024 类（超限临时构建+WARN）。
- 值归一化：Date→副本/long；标量集合：String/Number/Boolean/Character/Enum/Date。
- 错误处理：字段异常跳过并 DEBUG。

九、核心代码骨架（伪码）
```java
@Getter @Builder
final class ChangeRecord { /* 见数据模型定义 */ }

final class ObjectSnapshot {
  static final int MAX_CLASSES = 1024;
  static final ConcurrentHashMap<Class<?>,Map<String,Field>> CACHE = new ConcurrentHashMap<>();
  static Map<String,Object> capture(String name, Object target, String... fields) { /* 仅标量采集 */ }
  static void setMaxCachedClasses(int n) { /* n>0 生效；用于属性注入 */ }
}

final class Repr {
  static String repr(Object v, int max) { /* escape + truncate + "... (truncated)" */ }
}
```

十三、澄清与范围界定（2.1 临时口径）
- 本卡不引入 `ObjectSnapshotDeep` 深度遍历；保持“仅标量/字符串/日期”的浅快照行为。
- 深度快照/集合摘要在后续里程碑由 `SnapshotFacade` 路由与开关控制，默认关闭；本卡仅为后续留好注入与上限控制点（`setMaxCachedClasses`）。

十、DOR / DOD（整合）
- DOR：
  - ☐ 需求输入齐备（TASK-201 文档/Design 第4节）。
  - ☐ 依赖确定（无外部依赖）。
- DOD：
  - ☐ 测试通过（覆盖≥80%）。
  - ☐ 性能建议目标达成或给出改进计划。
  - ☐ 文档完整；配置/开关可用。

十一、冲突与建议（需拍板）
- 冲突：M0 是否设 CPU/“检索≤200μs”为硬门槛？建议：CPU 仅报告；“检索”措辞改为“2 字段对比”。
- 确认：CPU 仅报告；“检索”措辞改为“2 字段对比
- 冲突：是否默认脱敏？建议：M0 不做通用脱敏，采用白名单；脱敏留给 M1（Mask 策略）。
- 确认：M0 不做通用脱敏，采用白名单；脱敏留给 M1

十二、引用
- GPT 卡：cards-gpt/CARD-201-ChangeRecord-and-ObjectSnapshot.md
- OPUS 卡：cards-opus/TFI-MVP-201-changerecord-objectsnapshot.md
- 设计：specs/m2/final/TaskFlow-Insight-M2-Design.md 第4节
