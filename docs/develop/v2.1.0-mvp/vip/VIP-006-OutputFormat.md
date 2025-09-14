# VIP-006-OutputFormat（合并版）

## 1. 概述
- 主题：输出格式化（Console/JSON/Template）
- 源卡：
  - GPT: `../../gpt/PROMPT-CARD-230-console-json-changemessage-verification.md`
  - OPUS: `../../opus/PROMPT-M2M1-031-json-exporter.md`
  - OPUS: `../../opus/PROMPT-M2M1-010-template-engine.md`
- 相关代码：
  - `src/main/java/com/syy/taskflowinsight/exporter/console/ConsoleExporter.java`
  - `src/main/java/com/syy/taskflowinsight/exporter/json/JsonExporter.java`

## 2. 相同点（达成共识）
- 支持多种输出格式（Console/JSON）
- 消息格式标准化
- 支持格式化选项
- 保持向后兼容

## 3. 差异与歧义

#### 现状 vs 规划 对照（速览）
- 现状（已实现）
  - Console/JSON 导出：`ConsoleExporter`、`JsonExporter`；结构化输出，基于结构断言，不依赖文案；`MapExporter` 用于内部结构验证。
- 规划（按需引入）
  - 模板引擎与可插拔格式（如自定义模板/DSL、多格式导出）。

### 差异#1：模板引擎
- **影响**：灵活性和复杂度
- **GPT方案**：硬编码格式
- **OPUS方案**：可配置模板引擎
- **建议取舍**：Phase 1硬编码，Phase 2引入模板
- **理由**：渐进式增强，避免过度设计

### 差异#2：JSON序列化
- **影响**：依赖管理
- **GPT方案**：手工序列化
- **OPUS方案**：考虑Jackson/Gson
- **建议取舍**：保持手工序列化（已实现）
- **理由**：零依赖，性能可控

### 差异#3：验证策略
- **影响**：测试稳定性
- **GPT方案**：基于结构验证，不依赖文案
- **OPUS方案**：精确匹配输出
- **建议取舍**：采用GPT的结构验证
- **理由**：更稳定，便于国际化

## 4. 最终设计（融合后）

### 接口与契约
核心代码示例：见 `snippets/VIP-006-OutputFormat-EXAMPLES.md#exporter/console/json-示例（原文代码块）`

### 配置键
配置示例（YAML）：见 `snippets/VIP-006-OutputFormat-EXAMPLES.md#配置示例（yaml）`

### 消息验证器
测试与验证（MessageVerifier）：见 `snippets/VIP-006-OutputFormat-EXAMPLES.md#测试与验证（messageverifier）`

## 5. 与代码的对齐与改造清单

### 变更点
- `ConsoleExporter.java` → 增加模板支持预留接口
- `JsonExporter.java` → 增加enhanced模式
- 新增：`MessageVerifier.java` 测试工具类
- 新增：`ExportConfig.java` 配置类
- 新增：`TemplateEngine.java` 接口（Phase 2）

### 不改动项
- 保持现有手工序列化实现
- 保持现有的基本输出格式
- 保持零依赖原则

## 6. 测试计划

### 单元测试
格式化单元测试：见 `snippets/VIP-006-OutputFormat-EXAMPLES.md#格式化单元测试`

### 格式验证测试
- 空列表处理
- 特殊字符转义
- Unicode处理
- 大数据量性能

## 7. 验收与回滚

### 验收清单
- [x] Console输出可读
- [x] JSON格式正确
- [x] 测试验证稳定
- [ ] 性能达标
- [ ] 文档完整

### 回滚方案
1. 保持compat模式为默认
2. 新功能通过配置启用
3. 保留原有API

## 8. 实施计划

### Phase 1：基础增强（本周）
- Console格式优化
- JSON enhanced模式
- 消息验证器

### Phase 2：模板支持（下周）
- 简单模板引擎
- 自定义模板
- 模板缓存

### Phase 3：高级功能（后续）
- 集成主流模板引擎
- 国际化支持
- 自定义导出器

## 9. 开放问题
- [ ] 是否需要XML/YAML格式？
- [ ] 是否需要二进制格式（protobuf）？
- [ ] 模板语法选择？

---
*生成时间：2024-01-12*
*版本：v2.1.0-MVP*
