# VIP-012-CompareService（合并版）

## 1. 概述
- 主题：比较服务与差异展示
- 源卡：
  - OPUS: `../../opus/PROMPT-M2M1-020-compare-service.md`
  - GPT: 部分功能在DiffDetector中
- 相关代码：
  - `src/main/java/com/syy/taskflowinsight/service/CompareService.java`（待创建）
  - `src/main/java/com/syy/taskflowinsight/tracking/detector/DiffDetector.java`（已有）

## 2. 相同点（达成共识）
- 对象比较功能
- 差异可视化
- 支持多种比较模式
- 结果格式化输出

## 3. 差异与歧义

### 差异#1：服务层级

#### 现状 vs 规划 对照（速览）
- 现状（已实现）
  - 已有 `DiffDetector` 与结构化输出作为基础比较能力；无独立 `CompareService`。
- 规划（Phase 3）
  - 服务化比较接口与多视图展示；在稳定用例出现后再推进，避免重复能力堆叠。

## 处置策略（MVP阶段）
- 不新增独立服务层；将“差异视图”归入导出层（Console/JSON）的可选视图模式。
- 优先保证结构化输出稳定，避免早期扩展点过多。

## 实施触发条件
- 外部消费（前端/系统集成）需要更友好的差异视图或三方比较。
- 审计/评审需要对变更进行分组、聚合或高亮展示。
- 基础导出难以满足可读性/可用性需求。
- **影响**：架构复杂度
- **OPUS方案**：独立服务层
- **现有代码**：核心层实现
- **建议取舍**：服务层封装核心功能
- **理由**：分层清晰，易于扩展

### 差异#2：比较策略
- **影响**：灵活性
- **OPUS方案**：策略模式
- **建议增强**：支持自定义比较器
- **理由**：满足特殊需求

## 4. 最终设计（融合后）

### 接口与契约
- 示例代码：见 `snippets/VIP-012-CompareService-EXAMPLES.md#服务与结果/选项`

### 配置键
配置示例：见 `snippets/VIP-012-CompareService-EXAMPLES.md#配置示例（yaml）`

## 5. 与代码的对齐与改造清单

### 变更点
- 新建：`CompareService.java` 比较服务
- 新建：`CompareResult.java` 比较结果
- 新建：`CompareOptions.java` 比较选项
- 新建：`MergeResult.java` 三方比较结果

### 不改动项
- 保持DiffDetector核心功能
- 服务层为可选组件

## 6. 测试计划

### 单元测试
测试示例：见 `snippets/VIP-012-CompareService-EXAMPLES.md#测试示例`

## 7. 验收与回滚

### 验收清单
- [ ] 比较结果准确
- [ ] 相似度计算合理
- [ ] 报告格式正确
- [ ] 性能满足要求

### 回滚方案
1. 降级到DiffDetector直接使用
2. 禁用服务层功能

## 8. 开放问题
- [ ] 是否需要支持结构化差异（JSON Patch）？
- [ ] 是否需要可视化差异展示？
- [ ] 是否支持异步比较？

---
*生成时间：2024-01-12*
*版本：v2.1.0-MVP*
