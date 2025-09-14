## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-240-M0-Config-Keys-and-Defaults.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/resources/application.yml#无 tfi.change-tracking.*（新增类与默认值由代码承载）
  - src/main/java/com/syy/taskflowinsight/config/ContextMonitoringAutoConfiguration.java#com.syy.taskflowinsight.config.ContextMonitoringAutoConfiguration（参考装配风格）
- 工程操作规范：../开发工程师提示词.txt

## 3) GOALS（卡片→可执行目标）
- 业务目标：以 Spring `@ConfigurationProperties` 承载变更追踪 M0 配置。
- 技术目标：新增 `ChangeTrackingProperties`，前缀 `tfi.change-tracking`，字段：enabled=false、valueReprMaxLength=8192、cleanupIntervalMinutes=5（定时器默认关闭）。

## 4) SCOPE
- In Scope：
  - [ ] 新建 `src/main/java/com/syy/taskflowinsight/config/ChangeTrackingProperties.java`
  - [ ] 在需要处注入（后续 210/203 使用）；本卡只提供配置类与默认值
- Out of Scope：
  - [ ] 修改 application.yml（可在后续演示/测试中添加）

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 新建配置类：
```java
@ConfigurationProperties("tfi.change-tracking")
public class ChangeTrackingProperties {
  private boolean enabled = false;
  private int valueReprMaxLength = 8192;
  private int cleanupIntervalMinutes = 5;
  // getters/setters
}
```
2. 在自动配置（如 ContextMonitoringAutoConfiguration 或新增 AutoConfiguration）中启用 `@EnableConfigurationProperties(ChangeTrackingProperties.class)`。
3. 补丁：提供新文件与启用装配的 diff。
4. 文档：卡片勾选；在 210/203 使用说明双开关语义。
5. 自测：编写最小配置注入单测或在集成测试中读取默认值断言。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：ChangeTrackingProperties.java；启用装配的修改 diff。
- 测试：最小注入测试。
- 文档：卡片勾选。

## 7) API & MODELS（必须具体化）
- 无外部 API；仅配置模型。

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 可配置开关，默认禁用；不影响性能。

## 10) TEST PLAN（可运行、可断言）
- 单元/集成测试：
  - [ ] enabled 默认 false；valueReprMaxLength=8192；cleanupIntervalMinutes=5

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：配置类可注入且默认值正确
- [ ] 文档：卡片勾选

## 12) RISKS & MITIGATIONS
- 装配遗漏 → 在 AutoConfiguration 中启用；加注入用例。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否需要将默认值同时写入 application.yml？（建议：示例化即可）

