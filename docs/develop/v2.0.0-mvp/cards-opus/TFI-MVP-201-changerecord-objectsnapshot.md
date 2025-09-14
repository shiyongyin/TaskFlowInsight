---
id: TASK-201
title: ChangeRecord 与 ObjectSnapshot 实现
owner: 待指派
priority: P1
status: Planned
estimate: 4人时
dependencies:
  - 无（核心基础模块）
---

## 1. 目标与范围
- 业务目标：为变更追踪功能提供基础数据模型和快照采集能力，支持对象字段变化的结构化记录
- 技术目标：实现 com.syy.taskflowinsight.tracking.model.ChangeRecord 和 com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot
- 范围（In Scope）：
  - [ ] ChangeRecord 数据模型类（包含objectName、fieldName、oldValue、newValue等15个字段）
  - [ ] ObjectSnapshot 快照采集器（capture方法支持字段白名单）
  - [ ] 反射元数据缓存机制（ConcurrentHashMap<Class<?>, Map<String,Field>>）
  - [ ] valueRepr 字符串表示生成（8192字符截断策略）
- 边界（Out of Scope）：
  - [ ] 复杂对象递归展开（仅支持标量/String/Date）
  - [ ] 集合/Map类型摘要（推迟到M1）

## 2. 输入 / 输出
- 输入：capture(String name, Object target, String... fields) - 对象实例和字段白名单
- 输出：Map<String,Object> - 字段名到值的快照映射

## 3. 设计与实现要点
- 关键接口/类（**引用真实符号**）：
  - [ ] com.syy.taskflowinsight.tracking.model.ChangeRecord
  - [ ] com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot#capture(String, Object, String...)
- 数据与存储（**表/索引/分区/归档策略具体化**）：
  - [ ] 内存数据结构，无持久化需求
- 安全与合规：
  - [ ] 反射字段访问权限控制（setAccessible(true)）
  - [ ] valueRepr默认脱敏（8192字符截断）
- 可观测性：
  - [ ] DEBUG级别日志记录字段不可达异常
  - [ ] 缓存命中率监控（后续可添加metrics）

## 4. 开发清单（可勾选）
- [ ] 代码实现：src/main/java/com/syy/taskflowinsight/tracking/model/ChangeRecord.java
- [ ] 代码实现：src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshot.java
- [ ] 配置/脚本：value-repr-max-length=8192（默认配置值）
- [ ] 文档补全：类级别Javadoc注释
- [ ] 压测脚本与报告：反射性能基准测试
- [ ] 回滚/灰度预案：功能开关tfi.change-tracking.enabled

## 5. 测试要求（可勾选）
- 单元测试
  - [ ] 覆盖率 ≥ 80%
  - [ ] 边界/异常用例（null值、空字符串、Date深拷贝）
- 集成测试
  - [ ] 关键路径通过（capture -> 生成快照）
  - [ ] 回归用例通过（暂无）
- 性能测试
  - [ ] P95 写开销 ≤ 3%
  - [ ] 查询 ≤ 200μs @ 2字段规模

## 6. 关键指标（可勾选）
- [ ] 采集成功率 ≥ 99%
- [ ] 落盘失败率 ≤ 1%
- [ ] 检索 P95 ≤ 200μs
- [ ] 可视化 2 步到达差异详情（暂无UI）

## 7. 验收标准（可勾选）
- [ ] 功能验收：capture方法能正确捕获标量字段快照
- [ ] 文档齐备（Javadoc完整）
- [ ] 监控告警就绪（暂无）
- [ ] 风险关闭或降级可接受（反射性能风险通过缓存缓解）

## 8. 风险评估（可勾选）
- [ ] 性能：反射调用开销，通过元数据缓存缓解
- [ ] 稳定性：字段访问异常，通过try-catch忽略并记录DEBUG
- [ ] 依赖与外部影响：无外部依赖
- [ ] 安全与合规：反射访问权限，仅内部使用

## 9. 里程碑与排期
- 计划里程碑：M0阶段核心交付（ChangeRecord类、ObjectSnapshot类）
- DOR（就绪定义）
  - [ ] 需求输入齐备（docs/task/v2.0.0-mvp/change-tracking-core/TASK-201-ChangeRecord-and-ObjectSnapshot.md）
  - [ ] 依赖版本锁定（无外部依赖）
- DOD（完成定义）
  - [ ] 全测试通过（单测覆盖率>80%）
  - [ ] 指标达标（P95<200μs）
  - [ ] 灰度/回滚演练完成（通过配置开关控制）

## 10. 证据与引用
- 源文档：docs/task/v2.0.0-mvp/change-tracking-core/TASK-201-ChangeRecord-and-ObjectSnapshot.md
- 相关代码：src/main/java/com/syy/taskflowinsight/tracking/（待创建）
- 备注：参考docs/specs/m2/final/TaskFlow-Insight-M2-Design.md第4节

## 11. 开放问题（必须列出）
- [ ] 具体的ChangeType枚举值定义（CREATE/UPDATE/DELETE）需确认
- [ ] valueType和valueKind字段的具体填充规则需明确
- [ ] 是否需要支持自定义字段过滤策略接口