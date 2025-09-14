# Context Management架构重构方案

> **目标**: 将监控功能从MVP核心中分离，实现松耦合、可插拔的设计

## 🎯 重构目标

### 问题识别
1. **MVP边界模糊**: 监控功能与核心业务逻辑混合
2. **过度耦合**: 核心类直接依赖监控功能  
3. **默认策略不当**: 监控默认开启，增加不必要开销
4. **生命周期管理缺失**: 未考虑不同阶段的监控需求

### 解决方案
```
原架构 (问题):
Context Core ←→ Monitoring (紧耦合)

新架构 (目标):
Context Core (MVP)
    ↓ (松耦合)
Optional Monitoring Layer (可插拔)
```

## 📦 新包结构设计

```
src/main/java/com/syy/taskflowinsight/
├── context/                           # MVP核心包
│   ├── ManagedThreadContext.java      # 核心上下文管理
│   ├── SafeContextManager.java        # 核心管理器 (单例)
│   ├── TFIAwareExecutor.java          # 上下文感知线程池
│   ├── ZeroLeakThreadLocalManager.java # 泄漏清理 (必需)
│   └── ContextSnapshot.java           # 快照功能
│
└── optional/                          # 可选功能包
    └── monitoring/                    # 监控模块 (独立)
        ├── ContextMonitoringManager.java   # 监控管理器
        ├── PerformanceCollector.java       # 性能收集器  
        ├── LeakDetectionService.java       # 泄漏检测服务
        └── config/                    
            └── MonitoringConfiguration.java # 监控配置
```

## ⚙️ 分阶段配置策略

### 开发阶段 (Development)
```yaml
taskflow:
  context:
    monitoring:
      enabled: true
      debug-mode: true
      leak-detection-interval: 30s
      performance-tracking: true
```

### 测试阶段 (Testing)  
```yaml
taskflow:
  context:
    monitoring:
      enabled: true
      debug-mode: false
      leak-detection-interval: 120s
      performance-tracking: false
```

### 生产阶段 (Production)
```yaml
taskflow:
  context:
    monitoring:
      enabled: false               # 关闭监控
      # 或者低频监控
      # enabled: true
      # leak-detection-interval: 600s
```

### 稳定阶段 (Stable)
```yaml
taskflow:
  context:
    monitoring:
      enabled: false               # 完全关闭
```

## 🔧 松耦合设计原则

### 1. 核心功能独立性
```java
// ✅ 核心类不依赖监控
public final class SafeContextManager {
    // 纯粹的业务逻辑，无监控代码
    public CompletableFuture<Void> executeAsync(String taskName, Runnable task) {
        // 核心逻辑
    }
}
```

### 2. 可选监控注入
```java
// ✅ 监控功能可选注入
@ConditionalOnProperty(name = "taskflow.context.monitoring.enabled", havingValue = "true")
@Component
public class ContextMonitoringManager {
    // 监控逻辑与核心功能解耦
}
```

### 3. 事件驱动集成
```java
// ✅ 通过事件松耦合
// 核心功能发布事件
applicationEventPublisher.publishEvent(new ContextCreatedEvent(contextId));

// 监控功能监听事件 (可选)
@EventListener
@ConditionalOnProperty("taskflow.context.monitoring.enabled")
public void onContextCreated(ContextCreatedEvent event) {
    // 监控逻辑
}
```

## 📊 实施计划

### Phase 1: 核心清理 (立即执行)
- [x] 从核心类中移除监控代码
- [x] 恢复纯粹的业务逻辑  
- [x] 确保MVP功能完整

### Phase 2: 独立监控模块 (可选)
- [ ] 创建独立的监控包
- [ ] 实现可插拔的监控功能
- [ ] 配置驱动的启用机制

### Phase 3: 分阶段策略 (长期)
- [ ] 提供不同阶段的配置模板
- [ ] 文档化最佳实践
- [ ] 监控功能的渐进式移除指南

## 🎯 预期收益

### 即时收益
- **MVP纯净**: 核心功能专注业务逻辑
- **性能提升**: 默认关闭监控，减少开销
- **维护简化**: 降低核心功能复杂度

### 长期收益  
- **架构灵活**: 监控功能可插拔
- **环境适配**: 不同阶段不同策略
- **成本控制**: 稳定后可完全移除监控开销

## 🚨 注意事项

### 保留必要功能
```java
// ✅ 保留: 核心泄漏清理 (属于稳定性，非监控)
ZeroLeakThreadLocalManager // 仍在核心包

// ❌ 移除: 性能监控、指标收集、告警等
ContextMetrics, AlertManager // 移至可选包
```

### 兼容性考虑
- 保持现有API不变
- 新增配置向后兼容  
- 渐进式迁移，避免破坏性更改

---

**结论**: 通过架构重构，实现"核心稳定 + 监控可选"的设计，更好地服务于不同阶段的需求。