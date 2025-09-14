# 实施顺序（Implementation Order）

建议顺序基于依赖稳定性与返工最小化：先冻结对外“合同”，再堆叠实现与集成。

## 顺序（Phase 1）
1) VIP-002：DiffDetector（冻结合同）
2) VIP-003：ChangeTracker（依赖冻结的差异模型）
3) VIP-004：TFI-API（门面编排，flush/stop/clear 一致性）
4) VIP-006：OutputFormat（Console/JSON/Map 结构口径）
5) VIP-007：ConfigStarter（Starter/AutoConfig 与配置键落盘）

## 冻结点与合同内容
- DiffDetector 合同（冻结后再动需评审）：
  - 字段/差异模型：`ChangeRecord(objectName, fieldName, oldValue, newValue, changeType, timestamp)`
  - 归一化与排序口径：字段顺序、数值/字符串/集合的比较策略；空值与缺省一致性
  - 扩展点：大字段截断（统一委托 `ObjectSnapshot.repr`，先转义后截断 8192）
- OutputFormat 合同：
  - Console/JSON/Map 三种导出的结构一致性；禁止高基数字段
  - Change → Message 格式：`<obj>.<field>: <old> → <new>`（可配置是否启用）

## 每阶段验收与出场条件（DoD）
- VIP-002 DiffDetector
  - [x] 标量/字符串最小集通过单测；P50 < 50 μs（具备用例）
  - [x] 归一化/排序规则固定；接口 Javadoc 与样例完成
- VIP-003 ChangeTracker
  - [x] 线程隔离与 WeakReference 防泄漏；最大追踪对象数策略
  - [x] 三处清理口径统一（`stop/endSession/close`）与幂等
- VIP-004 TFI-API
  - [x] 门面禁用态快速返回；内部异常不出栈
  - [x] `flushChangesToCurrentTask` 结构化消息一致；可配置导出
- VIP-006 OutputFormat
  - [x] Console/JSON/Map 结构一致性测试与示例
  - [x] 导出对接门面 API，保持无副作用
- VIP-007 ConfigStarter
  - [x] `application-*.yml` 键落地；默认值保守；示例齐全
  - [x] Spring Boot AutoConfig 条件装配无副作用

## 并行化建议
- DiffDetector 合同确定后，ChangeTracker + OutputFormat 可并行推进
- 测试与样例文档可提前铺排（导出结构断言模板）

## 风险与回退
- 若 DiffDetector 合同迟迟不稳 → 暂停下游改动，仅补测试与示例
- 若 OutputFormat 争议大 → 先固定 JSON/Map，Console 推迟到 Phase 2

> 相关文档：
> - 《TECH-SPEC》：`docs/standards/TECH-SPEC.md`
> - 《Integration Test Plan》：`docs/standards/INTEGRATION-TEST-PLAN.md`
