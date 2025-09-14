# VIP-009-ActuatorEndpoint（合并版）

## 1. 概述
- 主题：Spring Boot Actuator管理端点
- 源卡：
  - GPT: 无直接对应
  - OPUS: `../../opus/PROMPT-M2M1-041-actuator-endpoint.md`
- 相关代码：
  - `src/main/java/com/syy/taskflowinsight/actuator/TaskflowContextEndpoint.java` (现有)
  - `src/main/resources/application.yml#management.endpoints`

## 2. 相同点（达成共识）
- 提供只读管理端点
- 配置信息脱敏处理
- 集成Spring Boot Actuator框架
- 统一端点命名空间

## 3. 差异与歧义

#### 现状 vs 规划 对照（速览）
- 现状（已实现）
  - 已有基础端点：`TaskflowContextEndpoint`，用于导出当前会话/任务树等基础信息。
- 规划（Phase 2）
  - 端点统一与别名：以 `tfi` 为主，保留 `taskflow` 别名；新增 `effective-config` 等辅助端点。

### 差异#1：端点命名
- **影响**：URL路径和向后兼容性
- **现有代码**：使用 @Endpoint(id="taskflow")
- **OPUS方案**：建议 @Endpoint(id="tfi")
- **建议取舍**：新建tfi端点，保留taskflow作为别名
- **理由**：平滑迁移，避免破坏现有监控
- **回滚与开关**：`tfi.compatibility.legacy-endpoints=true`

### 差异#2：功能范围
- **影响**：端点复杂度和安全性
- **现有代码**：仅展示上下文信息
- **OPUS方案**：增加effective-config和metrics
- **建议取舍**：分阶段实现，先config后metrics
- **理由**：渐进式增强，降低风险

## 4. 最终设计（融合后）

### 接口与契约
- 示例代码：见 `snippets/VIP-009-ActuatorEndpoint-EXAMPLES.md#端点与兼容（Endpoint/Delegate）`

### 配置键
配置示例：见 `snippets/VIP-009-ActuatorEndpoint-EXAMPLES.md#管理端点配置（yaml）`

### 数据模型
数据模型示例：见 `snippets/VIP-009-ActuatorEndpoint-EXAMPLES.md#数据模型（EffectiveConfig / MetricsSummary）`

## 5. 与代码的对齐与改造清单

### 变更点
- 新建：`com.syy.taskflowinsight.actuator.TfiEndpoint`
- 新建：`com.syy.taskflowinsight.actuator.model.EffectiveConfig`
- 新建：`com.syy.taskflowinsight.actuator.model.MetricsSummary`
- 新建：`com.syy.taskflowinsight.actuator.ConfigSanitizer`
- 修改：`TaskflowContextEndpoint` → 标记@Deprecated，委托给TfiEndpoint
- 修改：`application.yml` → 添加端点配置

### 不改动项
- 保持现有TaskflowContextEndpoint的API不变
- 现有的上下文信息获取逻辑保持不变

## 6. 测试计划

### 单元测试
- **配置脱敏测试**：
  - 敏感键替换为***
  - 密码类字段不输出
  - URL中的凭据脱敏
- **端点访问测试**：
  - 权限控制验证
  - 响应格式验证
  - 错误处理测试

### 集成测试
- 场景：启动应用 -> 访问端点
- URI：
  - GET /actuator/tfi
  - GET /actuator/tfi/effective-config
  - GET /actuator/tfi/metrics
  - GET /actuator/taskflow (兼容性)
- 期望：
  - 200 OK，返回JSON
  - 配置已脱敏
  - 指标数据正确

### 安全测试
测试示例：见 `snippets/VIP-009-ActuatorEndpoint-EXAMPLES.md#测试示例（安全与脱敏）`

## 7. 验收与回滚

### 验收清单
- [ ] tfi端点可访问
- [ ] taskflow端点仍可用（兼容）
- [ ] 配置信息已脱敏
- [ ] 指标数据准确
- [ ] 权限控制生效
- [ ] 文档已更新

### 回滚方案
1. 禁用新端点：`management.endpoint.tfi.enabled=false`
2. 保留旧端点：`tfi.compatibility.legacy-endpoints=true`
3. 回滚步骤：
   - 修改配置文件
   - 重启应用
   - 验证taskflow端点正常

### 迁移计划
迁移计划：见 `snippets/VIP-009-ActuatorEndpoint-EXAMPLES.md#迁移计划（Rollout）`

## 8. 差异与建议（文档 vs 代码冲突）
- 现有TaskflowContextEndpoint功能较简单，建议保留并扩展
- 端点命名建议统一为tfi，但需要兼容期
- 配置脱敏逻辑可复用Spring Boot的ConfigurationPropertiesReportEndpoint实现

## 9. 开放问题 / 行动项
- [ ] **问题**：是否需要添加写操作端点（如清理缓存）？
  - 责任人：架构评审
  - 截止：v2.2规划
  - 所需：安全评估

- [ ] **问题**：监控系统迁移时间表？
  - 责任人：运维团队
  - 截止：本迭代末
  - 所需：影响分析

- [ ] **行动**：创建端点使用文档
  - 责任人：文档团队
  - 截止：发布前
  - 产出：操作手册

## 10. 示例输出

### GET /actuator/tfi/effective-config
示例输出（effective-config）：见 `snippets/VIP-009-ActuatorEndpoint-EXAMPLES.md#输出示例（JSON）`

### GET /actuator/tfi/metrics
示例输出（metrics）：见 `snippets/VIP-009-ActuatorEndpoint-EXAMPLES.md#输出示例（JSON）`

---
*生成时间：2024-01-12*
*版本：v2.1.0-MVP*
