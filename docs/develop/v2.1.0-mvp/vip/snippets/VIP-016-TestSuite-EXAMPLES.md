# VIP-016 TestSuite 示例汇总（由正文迁移）

## 核心测试类/结构（精简）
```java
@DisplayName("TFI 核心功能测试")
class TFICoreTests {
  @Nested @DisplayName("快照功能") class SnapshotTests { /* ... */ }
  @Nested @DisplayName("差异检测") class DiffTests { /* ... */ }
  @Nested @DisplayName("并发场景") class ConcurrencyTests { /* ... */ }
}
```

## 配置示例（YAML）
```yaml
spring:
  task:
    execution:
      pool:
        core-size: 16
        max-size: 32
tfi:
  change-tracking:
    enabled: true
```
