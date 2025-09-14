# VIP-004-TFI-API（合并版）

## 1. 概述
- 主题：TFI门面API统一设计
- 源卡：
  - GPT: `../../gpt/PROMPT-CARD-204-tfi-stop-integration.md`
  - GPT: `../../gpt/PROMPT-CARD-210-tfi-apis-track-getclear.md`
  - OPUS: 无直接对应
- 相关代码：
  - `src/main/java/com/syy/taskflowinsight/api/TFI.java`
  - `src/main/java/com/syy/taskflowinsight/tracking/ChangeTracker.java`

## 2. 相同点（达成共识）
- 提供统一的门面API
- 支持链式调用
- 静态方法便于使用
- 线程安全设计

## 3. 差异与歧义

### 差异#1：API完整性
- **影响**：用户体验和功能覆盖
- **GPT方案**：track/stop/getChanges/clear基础API
- **现有代码**：已有track/withTracked/stop等
- **建议取舍**：保持现有，增强功能
- **理由**：向后兼容，渐进增强

### 差异#2：异常处理
- **影响**：健壮性和调试
- **GPT方案**：静默失败，返回空
- **现有代码**：部分异常抛出
- **建议取舍**：分级处理，关键异常抛出
- **理由**：平衡易用性和可调试性

### 差异#3：批量操作
- **影响**：性能和便利性
- **现有代码**：单对象操作
- **建议新增**：批量追踪API
- **理由**：提升效率，减少调用次数

## 4. 最终设计（融合后）

> **注意**：以下设计包含了当前已实现的API和规划中的扩展API。
> - 🟢 **已实现**：标记为已在当前代码中实现
> - 🟡 **规划中**：标记为计划实现但尚未完成的功能

### 接口与契约
- 示例代码：见 `snippets/VIP-004-TFI-API-EXAMPLES.md#门面API示例`

示例已迁移：见 `snippets/VIP-004-TFI-API-EXAMPLES.md`


#### 现状 vs 规划 对照（速览）
- 现状（🟢 已实现）
  - `TFI.track(String name, Object target, String... fields)`（示例中为简化的 `track(name, target)`）
  - `TFI.trackAll(Map<String, Object> targets)`
  - `TFI.getChanges()` → 委托 `ChangeTracker.getChanges()`
  - `TFI.clearAllTracking()` → 委托 `ChangeTracker.clearAllTracking()`
  - `TFI.stop()`（先刷新变更到当前任务，再清理追踪）
- 规划（🟡 规划中）
  - `TrackingOptions` 及 `track(..., TrackingOptions options)`
  - `TFI.stop(String name, Object target)`、`TFI.stopAll()`
  - `TFI.getChanges(String name)`


### 配置键
配置键示例：见 `snippets/VIP-004-TFI-API-EXAMPLES.md#配置键（原文代码块）`

## 5. 与代码的对齐与改造清单

### 变更点
- `TFI.java` → 增强API，添加批量操作、查询、导出功能
- 新增：`TrackingOptions.java` 追踪选项类
- 新增：`TrackingStats.java` 统计信息类
- 新增：`BatchTracker.java` 批量操作构建器

### 不改动项
- 保持现有track/stop/getChanges/clear基础API
- 保持静态方法设计
- 保持线程安全机制

## 6. 测试计划

### 单元测试
测试与用法示例：见 `snippets/VIP-004-TFI-API-EXAMPLES.md#测试与用法（原文代码块）`

### 性能测试
- 批量追踪1000个对象
- 并发追踪测试
- 内存使用监控

## 7. 验收与回滚

### 验收清单
- [x] 所有API正常工作
- [ ] 批量操作高效
- [x] 异常处理合理
- [ ] 统计信息准确
- [ ] 文档完整

### 回滚方案
1. 保留基础API不变
2. 新功能通过版本控制
3. 提供兼容层适配

## 8. 差异与建议
- 现有TFI.java已有基础实现，建议增强而非重写
- 批量操作使用构建器模式，更灵活
- 统计功能可选启用，避免性能影响

## 9. 开放问题
- [ ] 是否需要异步追踪API？
- [ ] 是否支持分布式追踪？
- [ ] 是否需要追踪结果缓存？

## 10. 使用示例

### 基础用法
### 高级用法
- 见 `snippets/VIP-004-TFI-API-EXAMPLES.md#测试与用法（原文代码块）`

---
*生成时间：2024-01-12*
*版本：v2.1.0-MVP*
