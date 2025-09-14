---
id: TASK-240
title: M0 配置键与默认值（Spring Properties，合并版）
owner: 待指派
priority: P2
status: Planned
estimate: 2人时
dependencies: []
source:
  gpt: ../cards-gpt/CARD-240-M0-Config-Keys-and-Defaults.md
  opus: ../cards-opus/TFI-MVP-240-config-defaults.md
---

一、合并策略
- 采纳：直接使用 Spring `@ConfigurationProperties`（ChangeTrackingProperties），默认值落地；System.getProperty 仅极端 fallback。
- 调整：自适应水位阈值推迟到 M1；双层开关（全局 TFI + change-tracking.enabled）。

二、开发/测试/验收（可勾选）
- ☑ 新增配置类并装配；注入 ChangeTracker/策略（通过 AutoConfiguration 设置 TFI.setChangeTrackingEnabled）。
- ☑ 默认值：enabled=false、value-repr-max-length=8192、cleanup-interval-minutes=5（定时器默认关闭）。
- ☑ 异常值回退安全；禁用下快速返回（默认禁用，fail‑closed）。
- ☐ 将 `max-cached-classes=1024` 注入到 `ObjectSnapshot#setMaxCachedClasses(int)`，由 AutoConfiguration 在启动时应用。
- ☐ 在 `src/main/resources/application.yml` 添加注释示例块：
  ```yaml
  # TaskFlow Insight — Change Tracking
  tfi:
    change-tracking:
      enabled: false
      value-repr-max-length: 8192
      cleanup-interval-minutes: 5
      max-cached-classes: 1024
  # 运维端点（建议统一到 tfi* 命名空间，过渡期保留 taskflow 别名）
  management:
    endpoints:
      web:
        exposure:
          include: tfi*
  ```

三、冲突与建议
- 优先 Spring 配置体系，避免先 System.getProperty 后迁移的重构成本。
 - 端点统一建议：新增只读端点 `/actuator/tfi/effective-config` 暴露裁剪后的有效配置与最小指标；现有 `/actuator/taskflow` 作为别名保留一段时间，文档与配置示例切到 tfi*。
