# VIP-007-ConfigStarter（合并版）

## 1. 概述
- 主题：Spring Boot配置管理和自动装配
- 源卡：
  - GPT: `../../gpt/PROMPT-CARD-240-m0-config-keys-and-defaults.md`
  - OPUS: `../../opus/PROMPT-M2M1-040-spring-boot-starter.md`
- 相关代码：
  - `src/main/java/com/syy/taskflowinsight/config/ContextMonitoringAutoConfiguration.java`
  - `src/main/resources/application.yml`

> 说明：MVP 阶段仅暴露“最小配置集”。详见同目录《OVERDESIGN-ASSESSMENT.md》中的“MVP 最小配置集（仅保留必要项）”。其余配置项与条件矩阵标注为“规划项/Phase 2+”。

## 2. 相同点（达成共识）
- 统一配置前缀：tfi.change-tracking.*
- 提供合理默认值
- 支持Spring Boot自动装配
- 配置属性类型安全

## 3. 差异与歧义

### 差异#1：配置结构
- **影响**：用户配置复杂度
- **GPT方案**：扁平化配置结构
- **OPUS方案**：分层嵌套配置
- **建议取舍**：采用分层结构，逻辑清晰
- **理由**：便于分模块管理和扩展

### 差异#2：自动装配条件
- **影响**：启动性能和灵活性
- **GPT方案**：简单条件装配
- **OPUS方案**：复杂条件矩阵
- **建议取舍**：分级条件，核心简单，扩展复杂
- **理由**：平衡易用性和灵活性

## 4. 最终设计（融合后）

### 配置属性类（示例）
- 详见 `snippets/VIP-007-CONFIG-STARTER-EXAMPLES.md#配置属性类`

### 自动装配类（示例）
- 详见 `snippets/VIP-007-CONFIG-STARTER-EXAMPLES.md#自动装配类`

### 默认配置（application.yml）
- 详见 `snippets/VIP-007-CONFIG-STARTER-EXAMPLES.md#默认配置applicationyml`

#### 现状 vs 规划 对照（速览）
- 现状（已实现）
  - 基础属性与自动装配：`ChangeTrackingProperties`、`ContextManagerProperties`、`MonitoringEndpointProperties`、`ContextMonitoringAutoConfiguration`；核心开关与最小阈值可用。
- 规划（按需扩展）
  - 更细的条件装配矩阵与扩展配置项；与“最小配置集”解耦的高级开关（标注为 Phase 2+）。

## 5. 与代码的对齐与改造清单

### 变更点
- 新建：`ChangeTrackingProperties.java` 配置类
- 改造：`ContextMonitoringAutoConfiguration` → `ChangeTrackingAutoConfiguration`
- 更新：`application.yml` 默认配置
- 新建：条件装配的各组件Bean定义

### 不改动项
- 保持现有组件的核心逻辑
- 保持现有的包结构

## 6. 测试计划

### 配置测试
- 详见 `snippets/VIP-007-CONFIG-STARTER-EXAMPLES.md#配置测试示例`

### 默认值测试
- 验证所有配置项的默认值
- 验证配置覆盖机制
- 验证条件装配逻辑

## 7. 验收与回滚

### 验收清单
- [x] 配置属性正确绑定
- [x] 默认值合理
- [x] 条件装配正确
- [x] 配置校验生效
- [ ] 文档完整

### 回滚方案
1. 恢复旧配置：使用原ContextMonitoringAutoConfiguration
2. 配置兼容：支持新旧配置键并存
3. 迁移指南：提供配置迁移文档

## 8. 开放问题
- [ ] 是否需要配置校验器？
- [ ] 是否支持配置热更新？
- [ ] 是否需要配置版本管理？

---
*生成时间：2024-01-12*
*版本：v2.1.0-MVP*
