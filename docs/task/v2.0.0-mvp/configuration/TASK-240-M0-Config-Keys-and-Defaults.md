Title: TASK-240 — M0 可选配置键与默认值

一、任务背景
- 生产可控性需要基本配置开关，但 M0 默认“零配置可跑”。

二、目标
- 定义 `tfi.change-tracking.*` 关键配置项与默认值，直接通过 Spring Boot `@ConfigurationProperties` 承载与注入（M0 即落地）。

 三、做法
 - 新增 `ChangeTrackingProperties`（`tfi.change-tracking` 前缀），示例：
   - `enabled=false`（独立于全局 TFI 开关）
   - `value-repr-max-length=8192`
   - `cleanup-interval-minutes=5`（定时清理器默认关闭，开启时使用该周期）
   - 说明：`default-max-fields/default-max-depth/collection-limit` 可保留为内部常量或后续扩展项；自适应截断/水位阈值推迟到 M1。
 - 使用 Spring 自动配置注入到 ChangeTracker/策略；`System.getProperty` 仅作为极端 fallback（如无 Spring 环境运行的基准测试）。

四、测试标准
- 配置未提供时使用默认值；提供时生效；值异常时使用安全降级值。

 五、验收标准
 - 文档与实现一致；不影响 M0 开箱即用体验；禁用状态快速返回。
