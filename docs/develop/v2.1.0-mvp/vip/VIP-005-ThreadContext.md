# VIP-005-ThreadContext（合并版）

## 1. 概述
- 主题：线程上下文管理
- 源卡：
  - GPT: `../../gpt/PROMPT-CARD-220-managedthreadcontext-cleanup.md`
  - GPT: `../../gpt/PROMPT-CARD-221-context-propagation-executor.md`
  - OPUS: 无直接对应
- 相关代码：
  - `src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java`
  - `src/main/java/com/syy/taskflowinsight/model/Session.java`
  - `src/main/java/com/syy/taskflowinsight/model/TaskNode.java`

## 2. 相同点（达成共识）
- ThreadLocal管理上下文
- 支持上下文传播
- 自动清理机制
- 线程池集成

## 3. 差异与歧义

### 差异#1：上下文范围
- **影响**：功能完整性
- **GPT方案**：Session + Task两级上下文
- **现有代码**：已有ManagedThreadContext实现
- **建议取舍**：保持现有，增强传播机制
- **理由**：已验证可用，风险低

### 差异#2：清理策略
- **影响**：内存泄漏风险
- **GPT方案**：自动清理 + 手动清理
- **现有代码**：主要依赖手动清理
- **建议取舍**：增强自动清理
- **理由**：防止泄漏，提升健壮性

### 差异#3：线程池支持
- **影响**：异步场景可用性
- **GPT方案**：装饰器模式包装Executor
- **现有代码**：未涉及
- **建议取舍**：新增装饰器支持
- **理由**：异步场景必需

## 处置策略（MVP阶段）
- 采用装饰器 `TFIAwareExecutor` 进行最小化上下文传播，覆盖常见线程池用法。
- 不以 RequestContextHolder 作为主上下文模型（非 Web 线程不适配），如有 Web 集成需求可后续提供适配层。
- 清理一致性：`ManagedThreadContext.close()`/`TFI.stop()`/`TFI.endSession()` 三处清理幂等一致。

## 实施触发条件
- 并发任务需要继承会话/任务上下文（审计归属、变更归属）。
- 线程池复用场景出现上下文残留或泄漏问题，需要传播与清理策略验证。

## 4. 最终设计（融合后）

### 接口与契约

示例已迁移：见 `snippets/VIP-005-ThreadContext-EXAMPLES.md`


### 配置键
配置示例：见 `snippets/VIP-005-ThreadContext-EXAMPLES.md#配置示例（yaml）`

## 5. 与代码的对齐与改造清单

### 变更点
- `ManagedThreadContext.java` → 增强清理机制和传播支持
- 新增：`ContextPropagatingExecutor.java` 执行器包装
- 新增：`ContextPropagatingExecutorService.java` 服务包装
- 新增：`@ContextManaged` 注解
- 新增：`AutoCleanupAspect` 自动清理切面

### 不改动项
- 保持现有Session/TaskNode模型
- 保持现有的基础上下文功能

## 6. 测试计划

### 单元测试
测试示例：见 `snippets/VIP-005-ThreadContext-EXAMPLES.md#测试示例`

### 并发测试
- 多线程上下文隔离
- 线程池上下文传播
- 内存泄漏检测

## 7. 验收与回滚

### 验收清单
- [ ] 上下文正确管理
- [ ] 自动清理有效
- [ ] 传播机制正常
- [ ] 无内存泄漏
- [ ] 性能影响可控

### 回滚方案
1. 禁用自动清理：`tfi.context.auto-cleanup=false`
2. 禁用传播：`tfi.context.propagation.enabled=false`
3. 恢复手动管理模式

## 8. 差异与建议
- 现有ManagedThreadContext基础良好，增强即可
- 自动清理通过AOP实现，非侵入式
- 线程池包装保证异步场景可用

## 9. 开放问题
- [ ] 是否需要支持跨JVM传播？
- [ ] 是否需要持久化上下文？
- [ ] 是否集成MDC（日志上下文）？

## 10. 最佳实践

### 使用模式
使用模式：见 `snippets/VIP-005-ThreadContext-EXAMPLES.md#使用模式示例`

---
*生成时间：2024-01-12*
*版本：v2.1.0-MVP*
