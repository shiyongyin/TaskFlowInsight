## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-240-M0-Config-Keys-and-Defaults.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/config/ChangeTrackingProperties.java#@ConfigurationProperties(prefix="tfi.change-tracking")
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.setChangeTrackingEnabled(boolean)
  - src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshot.java#com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot.setMaxValueLength(int)
- 相关配置：
  - src/main/resources/application.yml:（若未存在请新增以下键并给出示例默认值）
    - tfi.change-tracking.enabled=false
    - tfi.change-tracking.value-repr-max-length=8192
    - tfi.change-tracking.cleanup-interval-minutes=5
    - tfi.change-tracking.max-cached-classes=1024
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）
- 历史提示词风格参考：../../v1.0.0-mvp/design/api-implementation/prompts/DEV-010-TFI-MainAPI-Prompt.md

## 3) GOALS（卡片→可执行目标）
- 业务目标：以 Spring ConfigurationProperties 注入 M0 配置键与默认值，统一入口 `tfi.change-tracking.*`。
- 技术目标：
  - 默认值：enabled=false、value-repr-max-length=8192、cleanup-interval-minutes=5（定时器默认关闭）、max-cached-classes=1024。
  - 启动时将属性注入到 TFI 与 ObjectSnapshot（setChangeTrackingEnabled/setMaxValueLength）。

## 4) SCOPE
- In Scope：
  - [x] `ChangeTrackingProperties` 字段与默认值核对；`isCleanupEnabled()` 等辅助方法。
  - [x] AutoConfiguration（若已有 `ContextMonitoringAutoConfiguration`，在其中装配属性并调用 TFI/ObjectSnapshot 注入）。
- Out of Scope：
  - [ ] 自适应水位策略（推迟到 M1）。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 校对 `ChangeTrackingProperties` 字段/默认值；如缺少 `maxCachedClasses` 等请补齐（当前已存在）。
2. 在 AutoConfiguration 中 `@EnableConfigurationProperties(ChangeTrackingProperties.class)` 并在 `@PostConstruct` 或 `InitializingBean` 阶段：
```java
TFI.setChangeTrackingEnabled(props.isEnabled());
ObjectSnapshot.setMaxValueLength(props.getValueReprMaxLength());
```
3. `application.yml` 添加默认示例块（不包含敏感信息）。
4. 测试：属性注入与行为路径（禁用下四 API 快速返回）。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：AutoConfiguration 注入；Properties 与 Javadoc；示例配置。
- 测试：
  - 属性绑定测试；
  - 启用/禁用路径与 repr 长度生效测试。
- 文档：README/指南中的配置清单与默认值。
- 回滚/灰度：通过配置切换；默认 fail-closed（禁用）。
- 观测：日志 DEBUG 显示注入后的开关状态与阈值。

## 7) API & MODELS（必须具体化）
- 配置键：
  - `tfi.change-tracking.enabled: false`
  - `tfi.change-tracking.value-repr-max-length: 8192`
  - `tfi.change-tracking.cleanup-interval-minutes: 5`
  - `tfi.change-tracking.max-cached-classes: 1024`

## 8) DATA & STORAGE
- N/A。

## 9) PERFORMANCE & RELIABILITY
- 默认禁用，避免对非使用方产生开销；阈值可调。

## 10) TEST PLAN（可运行、可断言）
- 单元/集成：
  - [x] 绑定属性值后，TFI.isChangeTrackingEnabled() 与 ObjectSnapshot.getMaxValueLength() 与配置一致；
  - [x] 禁用状态下 track/getChanges 快速返回；
  - [ ] 非法值回退安全（如负值）。

## 11) ACCEPTANCE（核对清单，默认空）
- [x] 功能：属性注入与默认值正确；
- [ ] 文档：配置清单与默认值说明；
- [ ] 观测：注入日志；
- [x] 性能：默认禁用零/近零开销；
- [ ] 风险：回退策略覆盖异常值。

## 12) RISKS & MITIGATIONS
- 兼容性：属性命名需稳定；使用统一前缀避免冲突。
- 行为：默认禁用可能影响预期 → 文档显式说明需启用。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 代码与卡片一致；如需新增键（如 future mask 策略），在后续版本引入，避免当前冗余。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否在 Actuator 暴露有效配置只读端点（2.1.0-spring-integration 方案描述中有）？本卡先不做。
- 责任人/截止日期/所需产物：待指派 / 本卡周期 / 代码+测试+文档。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-240-m0-config-keys-and-defaults.md
