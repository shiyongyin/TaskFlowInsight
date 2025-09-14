# VIP-016-TestSuite（合并版）

## 1. 概述
- 主题：测试套件与验证框架
- 源卡：
  - GPT: `../../gpt/PROMPT-CARD-260-unit-diffdetector-scalar.md`
  - GPT: `../../gpt/PROMPT-CARD-261-concurrency-isolation.md`（并发测试）
  - GPT: `../../gpt/PROMPT-CARD-262-lifecycle-cleanup.md`（生命周期测试）
  - OPUS: 各模块测试部分
- 相关代码：
  - `src/test/java/com/syy/taskflowinsight/suite/`（待创建）

## 2. 相同点（达成共识）
- 完整测试覆盖
- 场景化测试
- 性能基准测试
- 自动化验证

## 3. 差异与歧义

#### 现状 vs 规划 对照（速览）
- 现状（已实现）
  - 已具备基础单元/集成测试（DiffDetector、Snapshot、API 导出等）。
- 规划（MVP/Phase 2）
  - 并发与生命周期 IT（10–16 线程，TFIAwareExecutor 传播与归属、去重、无交叉污染）；覆盖清理幂等与异常路径。

### 差异#1：测试组织
- **影响**：维护成本
- **GPT方案**：场景化套件
- **OPUS方案**：模块化测试
- **建议取舍**：两者结合
- **理由**：既有单元测试又有集成测试

## 4. 最终设计（融合后）

### 测试套件结构

核心测试类/结构：见 `snippets/VIP-016-TestSuite-EXAMPLES.md#核心测试类/结构`


### 测试配置

配置示例：见 `snippets/VIP-016-TestSuite-EXAMPLES.md#配置示例（yaml）`


## 5. 验收与回滚

### 验收清单
- [ ] 核心功能测试通过
- [ ] 场景测试覆盖完整
- [ ] 性能测试达标
- [ ] 测试覆盖率>80%

---
*生成时间：2024-01-12*
*版本：v2.1.0-MVP*
