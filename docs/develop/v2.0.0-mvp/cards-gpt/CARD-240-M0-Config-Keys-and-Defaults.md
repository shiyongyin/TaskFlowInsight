Title: CARD-240 — M0 配置键与默认值（Spring Properties）

一、开发目标
- ☐ 通过 Spring Boot `@ConfigurationProperties` 定义 `ChangeTrackingProperties`（前缀 `tfi.change-tracking`）。
- ☐ 关键项（M0）：`enabled=false`、`value-repr-max-length=8192`、`cleanup-interval-minutes=5`（定时清理默认关闭）。
- ☐ 在 `ChangeTracker`/策略中注入配置；`System.getProperty` 仅作为极端 fallback。

二、开发清单
- ☐ 新增 `src/main/java/com/syy/taskflowinsight/config/ChangeTrackingProperties.java`。
- ☐ 自动配置：在现有 AutoConfiguration 中装配并可被注入使用。
- ☐ 文档化：双层开关（全局 TFI 开关 + 变更追踪开关）的关系与默认值。

三、测试要求
- ☐ 未提供配置时使用默认值；提供后即时生效。
- ☐ 异常值（负数/过大）回退到安全值。

四、关键指标
- ☐ 禁用时快速返回；开启时行为一致稳定。

五、验收标准
- ☐ 文档与实现一致；不影响 M0 开箱即用体验。

六、风险评估
- ☐ 双开关语义混淆：文档明确，测试覆盖关闭/开启组合场景。

七、核心技术设计（必读）
- ☐ Properties 设计：
  - ☐ `@ConfigurationProperties(prefix = "tfi.change-tracking")`，字段：enabled、valueReprMaxLength、cleanupIntervalMinutes。
  - ☐ 默认值：false/8192/5；JSR-303 约束（如最小/最大）。
- ☐ 注入点：
  - ☐ `ChangeTracker`/策略的静态配置装载（建议构造注入或 setter），同时保留 `System.getProperty` 作为无 Spring 环境 fallback。
- ☐ 双开关：
  - ☐ `TFI.globalEnabled && properties.enabled` → 变更追踪有效。

八、核心代码说明（骨架/伪码）
```java
@ConfigurationProperties("tfi.change-tracking")
public class ChangeTrackingProperties {
  private boolean enabled = false;
  private int valueReprMaxLength = 8192;
  private int cleanupIntervalMinutes = 5;
}
```
