# TaskFlow Insight 架构文档

## 概述

TaskFlow Insight (TFI) 是一个 Java 应用性能分析工具，通过构建多层嵌套的任务树形模型，提供代码执行流程的深度洞察能力。本文档描述了 TFI 的整体架构设计、核心组件和设计决策。

## 核心设计目标

- **零侵入性**: 通过注解或 AOP 方式集成，最小化对业务代码的侵入
- **高性能**: 运行开销 < 5%，内存占用可控
- **线程安全**: 多线程环境下安全可靠
- **易扩展**: 支持插件化架构，可自定义采集器和导出器

## 文档目录

- [整体架构](./01-overview.md) - 系统整体架构和分层设计
- [核心组件](./02-core-components.md) - 核心引擎和组件设计
- [数据模型](./03-data-model.md) - 数据结构和存储模型
- [多线程设计](./04-threading-model.md) - 线程安全和并发处理
- [性能优化](./05-performance.md) - 性能优化策略和资源管理
- [扩展机制](./06-extensibility.md) - 插件化架构和 SPI 设计

## 快速导航

### 对于架构师
- [整体架构](./01-overview.md) - 了解系统全貌
- [核心组件](./02-core-components.md) - 关键组件设计
- [扩展机制](./06-extensibility.md) - 可扩展性设计

### 对于开发者
- [数据模型](./03-data-model.md) - 核心数据结构
- [多线程设计](./04-threading-model.md) - 并发编程模型
- [性能优化](./05-performance.md) - 性能最佳实践

### 对于集成开发者
- [扩展机制](./06-extensibility.md) - 如何开发插件
- [API 文档](../api/) - 接口定义和使用说明
- [集成指南](../integration/) - 与其他系统集成

## 执行参考（MVP）
- 开发索引：`../develop/v2.1.0-mvp/vip/INDEX.md`
- 合并总结：`../develop/v2.1.0-mvp/vip/MERGE-SUMMARY.md`
- 过度设计评估与MVP标准：`../develop/v2.1.0-mvp/vip/OVERDESIGN-ASSESSMENT.md`
- 最终评审：`../develop/v2.1.0-mvp/vip/FINAL-REVIEW.md`

### Snippets（长示例已外移，正文仅保留链接）
- 索引：`../develop/v2.1.0-mvp/vip/snippets/README.md`
- Config/AutoConfig 示例：`../develop/v2.1.0-mvp/vip/snippets/CONFIG-STARTER-EXAMPLES.md`
- Metrics/Micrometer 示例：`../develop/v2.1.0-mvp/vip/snippets/METRICS-MICROMETER-EXAMPLE.md`
- 性能基线/JMH 示例：`../develop/v2.1.0-mvp/vip/snippets/PERFORMANCE-BASELINE-EXAMPLES.md`

说明：为优化大模型阅读与检索性能，VIP 正文尽量保持精简，长代码与完整样例统一沉淀到 snippets，并通过上述链接访问。

## 架构原则

1. **分层解耦**: 采用分层架构，各层职责清晰，便于维护和扩展
2. **组件化**: 模块化设计，支持按需加载和配置
3. **异步优先**: 非关键路径异步处理，避免阻塞业务逻辑
4. **资源可控**: 内存和 CPU 使用可配置和监控
5. **向后兼容**: API 设计考虑向后兼容性，平滑升级
