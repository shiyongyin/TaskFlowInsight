# VIP-008-Performance（合并版）

## 1. 概述  
- 主题：性能基准测试与优化
- 源卡：
  - GPT: `../../gpt/PROMPT-CARD-250-benchmarks-jmh-or-micro.md`
  - OPUS: `../../opus/PROMPT-M2M1-050-metrics-logging.md`（部分）
- 相关代码：
  - `src/test/java/com/syy/taskflowinsight/performance/`（待创建）
  - `src/main/java/com/syy/taskflowinsight/monitor/PerformanceMonitor.java`（待创建）

## 2. 相同点（达成共识）
- 建立性能基准
- 分场景测试
- 持续监控
- 性能护栏

## 3. 差异与歧义

#### 现状 vs 规划 对照（速览）
- 现状（MVP）
  - 仅对热点模块（ObjectSnapshot、DiffDetector）建立极简基线（计时+断言），不引入复杂微基准框架。
- 规划（Phase 3）
  - 系统化 JMH 基准与 SLA 分级；引入指标面板与自动对比。

## 处置策略（MVP阶段）
- 先有“能测得出差异”的简单基线，再考虑系统化微基准。
- 避免过早优化，测试时间可控（秒级），不引入外部依赖。

## 实施触发条件

## 示例与基线（迁移至 snippets）
- JMH 基准类、运行与SLA示例、监控配置、典型优化示例均已迁移至：
  - `snippets/VIP-008-PERFORMANCE-BASELINE-EXAMPLES.md`
- 出现可复现的性能退化（快照/对比/导出路径）。
- 性能目标明确（SLA/吞吐/时延），基线能有效反馈优化收益。

### 差异#1：测试工具
- **影响**：测试准确性和集成复杂度
- **GPT方案**：JMH微基准测试
- **OPUS方案**：Spring Boot Test + 自定义
- **建议取舍**：JMH为主，Spring Boot Test为辅
- **理由**：JMH更专业，结果更可靠

### 差异#2：SLA定义
- **影响**：性能预期管理
- **GPT方案**：分场景细粒度SLA
- **OPUS方案**：统一SLA标准
- **建议取舍**：分场景SLA
- **理由**：更贴近实际使用场景

### 差异#3：监控粒度
- **影响**：性能问题定位
- **GPT方案**：方法级监控
- **OPUS方案**：组件级监控
- **建议取舍**：分级监控（组件级+关键方法）
- **理由**：平衡开销和可观测性

## 4. 最终设计（融合后）

### 性能基准测试
- 示例代码已迁移至 `snippets/VIP-008-PERFORMANCE-BASELINE-EXAMPLES.md`

### 配置键
- 监控与基准相关 YAML 样例：见 `snippets/VIP-008-PERFORMANCE-BASELINE-EXAMPLES.md`

## 5. 与代码的对齐与改造清单

### 变更点
- 新建：`PerformanceBenchmark.java` JMH基准测试
- 新建：`PerformanceMonitor.java` 性能监控组件
- 新建：`PerformanceSLA.java` SLA定义和验证
- 新建：`PerformanceStats.java` 性能统计数据

### 不改动项
- 保持现有功能逻辑不变
- 监控为可选组件，不影响核心功能

## 6. 测试计划

### 基准测试场景
- 运行基准与SLA验证：见 `snippets/VIP-008-PERFORMANCE-BASELINE-EXAMPLES.md#运行与SLA验证示例`

### 负载测试
- 并发100线程
- 持续运行10分钟
- 监控内存和CPU

## 7. 验收与回滚

### 验收清单
- [ ] 所有场景SLA达标
- [ ] 无内存泄漏
- [ ] CPU使用率合理
- [ ] 并发性能稳定
- [ ] 监控指标准确

### 回滚方案
1. 禁用性能监控：`tfi.performance.enabled=false`
2. 降级到基础功能
3. 关闭性能护栏检查

## 8. 差异与建议
- JMH提供更准确的微基准测试
- 分场景SLA更贴近实际使用
- 性能护栏防止生产环境性能退化

## 9. 开放问题
- [ ] 是否需要支持火焰图生成？
- [ ] 是否集成APM工具？
- [ ] 是否需要压测模式？

## 10. 性能优化建议

### 优化点
1. **反射缓存**：缓存Field和Method引用
2. **对象池**：复用Snapshot对象
3. **延迟初始化**：按需加载组件
4. **批量处理**：减少单次调用开销
5. **异步处理**：非关键路径异步化

### 示例优化
- 反射缓存/对象池示例：见 `snippets/VIP-008-PERFORMANCE-BASELINE-EXAMPLES.md#典型优化反射缓存对象池`

---
*生成时间：2024-01-12*
*版本：v2.1.0-MVP*

