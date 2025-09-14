# 依赖矩阵（Dependency Matrix）

列出关键依赖、版本、适用阶段、POM 片段与可选开关，便于发布前一致性核对。

## 基线依赖
- Java：21（LTS）
- Spring Boot：3.5.x（当前 `3.5.5`）
- Jackson：2.17+

## 可选/增强（按阶段启用）
- Micrometer：1.12+（最小指标集；Phase 1 可选）
- Caffeine：3.x（Phase 2+，用于缓存/驱逐策略）
- JMH：Phase 3（系统化基准）

## POM 片段示例
```xml
<!-- Micrometer（建议最小） -->
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-core</artifactId>
  <version>1.12.5</version>
  <optional>true</optional>
  
</dependency>

<!-- Caffeine（Phase 2+） -->
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
  <version>3.1.8</version>
  <optional>true</optional>
</dependency>
```

## 开关与装配
- 指标：默认不开启导出，仅在 `dev`/性能场景下启用（避免生产高基数）
- 缓存：按需引入；未启用不影响核心功能

> 参考：《RELEASE-CHECKLIST》《TECH-SPEC》

