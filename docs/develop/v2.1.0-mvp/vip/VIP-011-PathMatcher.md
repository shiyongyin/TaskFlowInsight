# VIP-011-PathMatcher（合并版）

## 1. 概述
- 主题：路径匹配器缓存优化
- 源卡：
  - OPUS: `../../opus/PROMPT-M2M1-003-path-matcher-cache.md`
  - GPT: 无直接对应
- 相关代码：
  - `src/main/java/com/syy/taskflowinsight/tracking/path/PathMatcherCache.java`（待创建）
  - `src/main/java/com/syy/taskflowinsight/core/ObjectSnapshot.java`（使用方）

## 2. 相同点（达成共识）
- 路径匹配性能优化
- 缓存匹配结果
- 支持通配符
- 线程安全

## 3. 差异与歧义

#### 现状 vs 规划 对照（速览）
- 现状（已实现）
  - 尚无 `PathMatcherCache` 组件与相关配置在代码中落地。
- 规划（按需引入）
  - 路径匹配与缓存：`tfi.change-tracking.path-matcher.*` 的匹配限制、缓存、预热等能力；先以 MVP 级别阈值控制为主。

## 处置策略（MVP阶段）
- 不自研匹配引擎，复用 Spring `PathPattern`/`AntPathMatcher`。
- 仅在确有需求时引入“轻量缓存”（如 Caffeine），默认关闭。
- 保持最小配置：`pattern-max-length`、`max-wildcards`、`cache-size`。

## 实施触发条件
- 需要对快照/字段进行路径级过滤（隐私/脱敏/降噪）。
- 大对象追踪导致输出体积或开销过高，需要按路径降采样或截断。
- 观测到路径匹配成为热点（命中率、CPU）且可通过缓存优化。

### 差异#1：实现方式
- **影响**：复杂度和性能
- **OPUS方案**：FSM状态机
- **简化方案**：正则表达式+缓存
- **建议取舍**：正则+缓存
- **理由**：实现简单，性能足够

### 差异#2：缓存策略
- **影响**：内存使用
- **OPUS方案**：无限缓存
- **建议改进**：LRU有限缓存
- **理由**：防止内存泄漏

## 4. 最终设计（融合后）

### 接口与契约

实现与使用示例：见 `snippets/VIP-011-PathMatcher-EXAMPLES.md#pathmatchercache-实现与使用`


### 配置键

配置示例：见 `snippets/VIP-011-PathMatcher-EXAMPLES.md#配置示例（yaml）`


## 5. 与代码的对齐与改造清单

### 变更点
- 新建：`PathMatcherCache.java` 路径匹配器
- 新建：`FieldFilter.java` 字段过滤器
- 集成：在Snapshot中使用路径匹配

### 不改动项
- 保持现有功能逻辑
- 路径匹配为可选功能

## 6. 测试计划

### 单元测试

测试示例：见 `snippets/VIP-011-PathMatcher-EXAMPLES.md#测试示例`


## 7. 验收与回滚

### 验收清单
- [ ] 通配符匹配正确
- [ ] 缓存命中率>90%
- [ ] 性能提升10倍
- [ ] 内存使用可控
- [ ] 线程安全

### 回滚方案
1. 禁用缓存功能
2. 降级到简单字符串匹配
3. 调整缓存大小

## 8. 开放问题
- [ ] 是否需要支持正则表达式？
- [ ] 是否需要分布式缓存？
- [ ] 是否支持动态模式更新？

---
*生成时间：2024-01-12*
*版本：v2.1.0-MVP*
