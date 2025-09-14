# VIP-003-ChangeTracker（合并版）

## 1. 概述
- 主题：变更追踪器核心功能
- 源卡：
  - GPT: `../../gpt/PROMPT-CARD-203-changetracker-threadlocal.md`
  - GPT: `../../gpt/PROMPT-CARD-210-tfi-apis-track-getclear.md`
  - OPUS: 无直接对应
- 相关代码：
  - `src/main/java/com/syy/taskflowinsight/tracking/ChangeTracker.java`
  - `src/main/java/com/syy/taskflowinsight/api/TFI.java#track()/getChanges()/clear()`

## 2. 相同点（达成共识）
- 使用ThreadLocal管理变更上下文
- 提供track/getChanges/clear核心API
- 自动快照和差异检测
- 线程隔离保证安全性

## 3. 差异与歧义

### 差异#1：存储策略
- **影响**：内存使用和性能
- **GPT方案**：纯ThreadLocal存储
- **现有代码**：已实现ThreadLocal<List<ChangeRecord>>
- **建议取舍**：保持现有实现，可选增加缓存层
- **理由**：简单可靠，满足当前需求
- **回滚与开关**：`tfi.change-tracking.storage-strategy=threadlocal`

### 差异#2：清理时机
- **影响**：内存泄漏风险
- **GPT方案**：显式clear()和自动清理
- **现有代码**：仅显式清理
- **建议取舍**：增加自动清理机制
- **理由**：防止忘记清理导致内存泄漏

## 4. 最终设计（融合后）

### 接口与契约

示例已迁移：见 `snippets/VIP-003-ChangeTracker-EXAMPLES.md`


### 配置键

示例已迁移：见 `snippets/VIP-003-ChangeTracker-EXAMPLES.md`


## 5. 与代码的对齐与改造清单

### 变更点
- `ChangeTracker.java` → 增加自动清理机制
- 新增：`TrackingContext` 内部类（封装上下文）
- 新增：`AutoCleanupInterceptor` AOP拦截器
- 修改：添加防御性编程（null检查、异常处理）

### 不改动项
- 保持现有ThreadLocal存储机制
- 保持现有API签名（track/getChanges/clear）
- 保持与TFI的集成方式

## 6. 测试计划

### 单元测试
- **基础功能测试**：
  - track -> modify -> getChanges流程
  - 多对象追踪
  - 空对象处理
- **线程隔离测试**：
  - 多线程并发追踪
  - ThreadLocal隔离验证
- **清理测试**：
  - 显式clear()
  - 自动清理
  - 异常清理

### 集成测试

示例已迁移：见 `snippets/VIP-003-ChangeTracker-EXAMPLES.md`


### 性能测试
- 场景：1000次追踪操作
- 目标：无内存泄漏
- 验证：ThreadLocal正确清理

## 7. 验收与回滚

### 验收清单
- [x] 基本追踪功能正常
- [x] 线程隔离正确
- [x] 无内存泄漏
- [ ] 自动清理可选
- [x] 异常处理完善

### 回滚方案
1. 禁用自动清理：`tfi.change-tracking.auto-cleanup=false`
2. 恢复手动清理模式
3. 监控内存使用情况

## 8. 差异与建议（文档 vs 代码冲突）
- 现有代码已实现基础ThreadLocal机制，建议增强而非重写
- 自动清理通过AOP实现，避免侵入核心逻辑
- 考虑添加metrics统计追踪次数和清理次数

## 9. 开放问题 / 行动项
- [ ] **问题**：是否需要支持分布式追踪？
  - 责任人：架构组
  - 截止：v2.2规划
  - 所需：分布式场景分析

- [ ] **问题**：如何处理长时间运行的追踪？
  - 责任人：性能团队
  - 截止：本迭代
  - 所需：超时策略

- [ ] **行动**：添加ThreadLocal泄漏检测
  - 责任人：测试团队
  - 截止：发布前
  - 工具：内存分析工具

## 10. 最佳实践建议

### 使用模式

示例已迁移：见 `snippets/VIP-003-ChangeTracker-EXAMPLES.md`


### 注意事项
1. **线程池场景**：使用前后必须清理
2. **异步任务**：需要传播上下文
3. **长时间任务**：定期清理或分批处理
4. **异常处理**：finally块中清理

---
*生成时间：2024-01-12*
*版本：v2.1.0-MVP*
