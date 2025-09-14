---
id: TASK-202
title: DiffDetector 标量字段对比实现
owner: 待指派
priority: P1
status: Planned
estimate: 3人时
dependencies:
  - TASK-201（需要ChangeRecord数据模型）
---

## 1. 目标与范围
- 业务目标：实现字段级差异检测，识别对象状态变化并生成结构化变更记录
- 技术目标：实现 com.syy.taskflowinsight.tracking.detector.DiffDetector 类及diff方法
- 范围（In Scope）：
  - [ ] DiffDetector.diff(Map<String,Object> before, Map<String,Object> after) 方法实现
  - [ ] 标量类型对比逻辑（String/Number/Boolean/Date）
  - [ ] CREATE/DELETE/UPDATE 变更类型判定
  - [ ] valueRepr字符串生成（先转义后截断）
- 边界（Out of Scope）：
  - [ ] 集合/Map类型对比（推迟到M1）
  - [ ] 自定义对比策略接口（M1预留）

## 2. 输入 / 输出
- 输入：Map<String,Object> before, Map<String,Object> after - 前后快照映射
- 输出：List<ChangeRecord> - 检测到的变更记录列表

## 3. 设计与实现要点
- 关键接口/类（**引用真实符号**）：
  - [ ] com.syy.taskflowinsight.tracking.detector.DiffDetector#diff(Map<String,Object>, Map<String,Object>)
  - [ ] com.syy.taskflowinsight.tracking.model.ChangeRecord（依赖）
- 数据与存储（**表/索引/分区/归档策略具体化**）：
  - [ ] 内存计算，无持久化需求
- 安全与合规：
  - [ ] valueRepr截断（8192字符）防止内存溢出
  - [ ] 敏感数据脱敏（后续可扩展）
- 可观测性：
  - [ ] DEBUG日志记录对比详情
  - [ ] 性能metrics（对比耗时）

## 4. 开发清单（可勾选）
- [ ] 代码实现：src/main/java/com/syy/taskflowinsight/tracking/detector/DiffDetector.java
- [ ] 配置/脚本：无需额外配置
- [ ] 文档补全：方法级Javadoc注释
- [ ] 压测脚本与报告：2字段对比性能基准
- [ ] 回滚/灰度预案：通过tfi.change-tracking.enabled控制

## 5. 测试要求（可勾选）
- 单元测试
  - [ ] 覆盖率 ≥ 80%
  - [ ] 边界/异常用例（null值对比、类型变化、Date.equals）
- 集成测试
  - [ ] 关键路径通过（快照对比生成ChangeRecord）
  - [ ] 回归用例通过（暂无）
- 性能测试
  - [ ] P95 写开销 ≤ 3%
  - [ ] 查询 ≤ 200μs @ 2字段规模（100次循环平均）

## 6. 关键指标（可勾选）
- [ ] 采集成功率 ≥ 99%
- [ ] 落盘失败率 ≤ 1%
- [ ] 检索 P95 ≤ 200μs
- [ ] 可视化 2 步到达差异详情（暂无UI）

## 7. 验收标准（可勾选）
- [ ] 功能验收：diff方法正确识别CREATE/DELETE/UPDATE变化
- [ ] 文档齐备（方法注释完整）
- [ ] 监控告警就绪（暂无）
- [ ] 风险关闭或降级可接受（性能达标）

## 8. 风险评估（可勾选）
- [ ] 性能：字段数量增加时的对比开销（O(n)复杂度）
- [ ] 稳定性：equals方法异常处理
- [ ] 依赖与外部影响：依赖TASK-201的ChangeRecord
- [ ] 安全与合规：无特殊风险

## 9. 里程碑与排期
- 计划里程碑：M0阶段核心交付（DiffDetector类）
- DOR（就绪定义）
  - [ ] 需求输入齐备（docs/task/v2.0.0-mvp/change-tracking-core/TASK-202-DiffDetector-Scalar.md）
  - [ ] 依赖版本锁定（依赖TASK-201完成）
- DOD（完成定义）
  - [ ] 全测试通过（单测覆盖率>80%）
  - [ ] 指标达标（2字段P95<200μs）
  - [ ] 灰度/回滚演练完成（配置开关验证）

## 10. 证据与引用
- 源文档：docs/task/v2.0.0-mvp/change-tracking-core/TASK-202-DiffDetector-Scalar.md
- 相关代码：src/main/java/com/syy/taskflowinsight/tracking/detector/（待创建）
- 备注：对比规则参考文档第三节

## 11. 开放问题（必须列出）
- [ ] Strategy接口的具体设计（为M1预留扩展点）
- [ ] valueType/valueKind字段填充规则（M0可选填）
- [ ] Date类型比较是否统一使用getTime()长整型值