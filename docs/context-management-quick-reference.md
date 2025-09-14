# Context Management 快速参考

> 一页纸的核心API和最佳实践速查表

## 🚀 基本用法

```java
// 标准模式
try (ManagedThreadContext ctx = ManagedThreadContext.create("taskName")) {
    TaskNode task = ctx.startTask("subtask");
    // 业务逻辑
    ctx.endTask();
}

// 异步执行
SafeContextManager.getInstance().executeAsync("async", () -> {
    // 上下文自动传播
});

// 线程池安全
TFIAwareExecutor executor = TFIAwareExecutor.newFixedThreadPool(4);
executor.submit(() -> {
    ManagedThreadContext ctx = ManagedThreadContext.current();
});
```

## 📊 监控端点

| URL | 用途 | 状态 |
|-----|------|------|
| `/actuator/health` | Spring Boot健康检查 | ✅ 可用 |
| `/actuator/info` | 应用基础信息 | ✅ 可用 |
| *高级监控功能* | *详细指标和管理* | 🔄 未来版本 |

## ⚠️ 常见错误

| 错误 | 原因 | 解决 |
|------|------|------|
| `No active context` | 线程没有上下文 | 检查上下文创建/传播 |
| `Context already closed` | 使用已关闭上下文 | 检查try-with-resources |
| `Session already active` | 重复创建会话 | 先结束当前会话 |

## 🔧 故障排查

```bash
# 检查应用健康状态
curl http://localhost:19090/actuator/health

# 查看应用信息
curl http://localhost:19090/actuator/info

# 检查日志中的上下文警告
tail -f logs/application.log | grep "ManagedThreadContext"

# 注意: 高级监控功能(指标收集、手动清理等)已规划为可选增强功能
```

## ⚡ 性能基准

- 上下文创建: ~19.54μs
- 任务操作: ~26.7μs  
- 快照创建: ~57ns
- 内存使用: ~6.77KB/上下文
- 并发吞吐: ~51K ops/s

## 🎯 最佳实践

✅ **DO**:
- 始终使用try-with-resources
- 使用TFIAwareExecutor进行线程池操作
- 监控泄漏指标
- 合理设置上下文超时时间

❌ **DON'T**:
- 手动调用close()方法
- 在线程池中依赖上下文继承
- 忽略泄漏告警
- 在长期运行线程中创建上下文

## 📋 配置模板

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: [health, metrics, prometheus, contextManagement]

taskflow:
  context:
    monitoring:
      enabled: true
      memory-warning-threshold: 52428800   # 50MB
      memory-critical-threshold: 104857600 # 100MB
      leak-warning-threshold: 10
      leak-critical-threshold: 50

logging:
  level:
    com.syy.taskflowinsight.context: INFO
```

---
📖 **详细文档**: [生产环境指南](context-management-production-guide.md) | [API参考](context-management-api-reference.md)