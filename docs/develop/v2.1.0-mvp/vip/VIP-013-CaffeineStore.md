# VIP-013-CaffeineStore（合并版）

## 1. 概述
- 主题：Caffeine缓存存储实现
- 源卡：
  - OPUS: `../../opus/PROMPT-M2M1-030-caffeine-store.md`
  - GPT: 无直接对应
- 相关代码：
  - `src/main/java/com/syy/taskflowinsight/store/CaffeineStore.java`（待创建）
  - `src/main/java/com/syy/taskflowinsight/store/Store.java`（接口）

## 2. 相同点（达成共识）
- 高性能本地缓存
- 自动过期机制
- 统计监控
- 可配置策略

## 3. 差异与歧义

### 差异#1：缓存层级

#### 现状 vs 规划 对照（速览）
- 现状（已实现）
  - 无 `CaffeineStore` 实现；内存 Map 已满足 MVP 需要。
- 规划（Phase 4）
  - 可选的 Caffeine 集成（容量/TTL/统计），仅在明确性能瓶颈或并发竞争场景出现后引入。

## 处置策略（MVP阶段）
- 不建设“通用 Store 子系统”；如需缓存，按点使用 Caffeine（如反射元数据缓存、路径匹配缓存）。
- 避免引入额外运维与一致性负担，优先内存本地缓存。

## 实施触发条件
- QPS 提升且热点缓存命中不足，出现明显重复计算或反射开销。
- GC 压力上升、分配/复制成为瓶颈，通过缓存可显著降低分配。
- 指标观测到缓存带来的瓶颈（命中率、加载时延）。
- **影响**：架构复杂度
- **OPUS方案**：单层缓存
- **建议增强**：支持多级缓存
- **理由**：满足不同性能需求

### 差异#2：序列化
- **影响**：存储效率
- **OPUS方案**：直接存储对象
- **建议改进**：可选序列化
- **理由**：减少内存占用

## 4. 最终设计（融合后）

### 接口与契约

实现示例：见 `snippets/VIP-013-CaffeineStore-EXAMPLES.md#实现与配置`


### 配置键

配置示例：见 `snippets/VIP-013-CaffeineStore-EXAMPLES.md#实现与配置`


## 5. 与代码的对齐与改造清单

### 变更点
- 新建：`Store.java` 存储接口
- 新建：`CaffeineStore.java` Caffeine实现
- 新建：`TieredCaffeineStore.java` 分层缓存
- 新建：`StoreConfig.java` 配置类

### 不改动项
- 存储为可选功能
- 不影响核心追踪功能

## 6. 测试计划

### 单元测试

测试/用法：见 `snippets/VIP-013-CaffeineStore-EXAMPLES.md#测试/用法`


## 7. 验收与回滚

### 验收清单
- [ ] 基础操作正常
- [ ] 过期机制生效
- [ ] 统计信息准确
- [ ] 内存使用合理

### 回滚方案
1. 禁用存储：`tfi.store.enabled=false`
2. 切换到内存存储
3. 清空缓存

## 8. 开放问题
- [ ] 是否需要持久化支持？
- [ ] 是否集成Redis？
- [ ] 是否需要集群同步？

---
*生成时间：2024-01-12*
*版本：v2.1.0-MVP*
