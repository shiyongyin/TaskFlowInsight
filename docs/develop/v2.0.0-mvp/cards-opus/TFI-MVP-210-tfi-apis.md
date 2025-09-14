---
id: TASK-210
title: TFI 新增4个核心API
owner: 待指派
priority: P1
status: Planned
estimate: 2人时
dependencies:
  - TASK-203（需要ChangeTracker实现）
---

## 1. 目标与范围
- 业务目标：为开发者提供变更追踪的统一入口API
- 技术目标：在 com.syy.taskflowinsight.api.TFI 类中新增4个静态方法
- 范围（In Scope）：
  - [ ] track(String name, Object target, String... fields) 方法
  - [ ] trackAll(Map<String,Object> targets) 方法
  - [ ] getChanges() 返回List<ChangeRecord>
  - [ ] clearAllTracking() 清理方法
- 边界（Out of Scope）：
  - [ ] 复杂的追踪策略配置
  - [ ] 异步追踪支持

## 2. 输入 / 输出
- 输入：对象实例、字段名列表
- 输出：List<ChangeRecord> - 变更记录列表

## 3. 设计与实现要点
- 关键接口/类（**引用真实符号**）：
  - [ ] com.syy.taskflowinsight.api.TFI#track(String, Object, String...)
  - [ ] com.syy.taskflowinsight.api.TFI#trackAll(Map<String,Object>)
  - [ ] com.syy.taskflowinsight.api.TFI#getChanges()
  - [ ] com.syy.taskflowinsight.api.TFI#clearAllTracking()
  - [ ] com.syy.taskflowinsight.tracking.ChangeTracker（委托目标）
- 数据与存储（**表/索引/分区/归档策略具体化**）：
  - [ ] 无持久化需求
- 安全与合规：
  - [ ] 异常安全（catch Throwable）
  - [ ] 空值防御（target == null快速返回）
- 可观测性：
  - [ ] 使用handleInternalError统一记录异常
  - [ ] DEBUG级别日志

## 4. 开发清单（可勾选）
- [ ] 代码实现：src/main/java/com/syy/taskflowinsight/api/TFI.java（新增4个方法）
- [ ] 配置/脚本：tfi.change-tracking.enabled配置项
- [ ] 文档补全：API方法Javadoc
- [ ] 压测脚本与报告：无需单独压测
- [ ] 回滚/灰度预案：isEnabled()开关控制

## 5. 测试要求（可勾选）
- 单元测试
  - [ ] 覆盖率 ≥ 80%
  - [ ] 边界/异常用例（null参数、disabled状态）
- 集成测试
  - [ ] 关键路径通过（track -> getChanges流程）
  - [ ] 回归用例通过（现有TFI测试不受影响）
- 性能测试
  - [ ] P95 写开销 ≤ 3%
  - [ ] 查询 ≤ 200μs @ 禁用状态快速返回

## 6. 关键指标（可勾选）
- [ ] 采集成功率 ≥ 99%
- [ ] 落盘失败率 ≤ 1%
- [ ] 检索 P95 ≤ 200μs
- [ ] 可视化 2 步到达差异详情（暂无UI）

## 7. 验收标准（可勾选）
- [ ] 功能验收：4个API方法正常工作，异常不冒泡
- [ ] 文档齐备（API文档完整）
- [ ] 监控告警就绪（暂无）
- [ ] 风险关闭或降级可接受（异常处理完善）

## 8. 风险评估（可勾选）
- [ ] 性能：禁用状态需快速返回
- [ ] 稳定性：异常捕获保证不影响主流程
- [ ] 依赖与外部影响：依赖ChangeTracker实现
- [ ] 安全与合规：无特殊风险

## 9. 里程碑与排期
- 计划里程碑：M0阶段API层完成
- DOR（就绪定义）
  - [ ] 需求输入齐备（docs/task/v2.0.0-mvp/api-implementation/TASK-210-TFI-APIs-Track-GetClear.md）
  - [ ] 依赖版本锁定（TASK-203完成）
- DOD（完成定义）
  - [ ] 全测试通过（编译通过、单测覆盖）
  - [ ] 指标达标（disabled快速返回）
  - [ ] 灰度/回滚演练完成（开关验证）

## 10. 证据与引用
- 源文档：docs/task/v2.0.0-mvp/api-implementation/TASK-210-TFI-APIs-Track-GetClear.md
- 相关代码：src/main/java/com/syy/taskflowinsight/api/TFI.java#194-204行附近
- 备注：模板代码见文档第16-22行

## 11. 开放问题（必须列出）
- [ ] handleInternalError方法的具体实现位置（已存在于TFI类）
- [ ] tfi.change-tracking.enabled配置读取方式（Spring配置还是System.getProperty）
- [ ] ChangeTracker是否为单例模式