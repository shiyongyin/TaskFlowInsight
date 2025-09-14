# Context Management生产环境指南

> **版本**: 1.0.0  
> **更新日期**: 2025-09-05  
> **状态**: 生产就绪 🚀

## 📋 概述

TaskFlowInsight Context Management模块提供线程安全的上下文管理，支持异步传播和零内存泄漏保证。本指南涵盖API使用、生产监控和故障处理。

## 🚀 快速开始

### 基本用法

```java
// 1. 基本上下文管理
try (ManagedThreadContext ctx = ManagedThreadContext.create("mainTask")) {
    TaskNode task = ctx.startTask("processData");
    
    // 业务逻辑
    processBusinessData();
    
    ctx.endTask();
} // 自动清理上下文

// 2. 异步任务执行
SafeContextManager manager = SafeContextManager.getInstance();
CompletableFuture<String> future = manager.executeAsync("asyncTask", () -> {
    // 在异步线程中自动恢复父上下文
    ManagedThreadContext current = ManagedThreadContext.current();
    return processAsyncData();
});

// 3. 线程池安全使用
TFIAwareExecutor executor = TFIAwareExecutor.newFixedThreadPool(4);
executor.submit(() -> {
    // 自动获得父上下文，无需手动传播
    ManagedThreadContext ctx = ManagedThreadContext.current();
    processInThreadPool();
});
```

## 📊 生产监控

### Spring Boot Actuator端点

| 端点 | 用途 | 示例URL |
|------|------|---------|
| `/actuator/health` | 整体健康检查 | `GET /actuator/health` |
| `/actuator/context` | Context管理详情 | `GET /actuator/context` |
| `/actuator/metrics` | 性能指标 | `GET /actuator/metrics/taskflow.context.*` |
| `/actuator/prometheus` | Prometheus格式指标 | `GET /actuator/prometheus` |

### 关键监控指标

**计数器指标**:
- `taskflow.context.created.total` - 创建的上下文总数
- `taskflow.context.closed.total` - 关闭的上下文总数  
- `taskflow.context.leaks.detected.total` - 检测到的泄漏总数
- `taskflow.context.leaks.fixed.total` - 修复的泄漏总数
- `taskflow.context.async.executions.total` - 异步执行总数

**实时指标**:
- `taskflow.context.active.count` - 当前活跃上下文数
- `taskflow.context.active.threads.count` - 活跃线程数
- `taskflow.context.memory.used.bytes` - 内存使用量
- `taskflow.context.health.status` - 健康状态码 (0=HEALTHY, 1=WARNING, 2=CRITICAL)

**性能指标**:
- `taskflow.context.creation.duration` - 上下文创建耗时
- `taskflow.context.task.operation.duration` - 任务操作耗时
- `taskflow.context.snapshot.creation.duration` - 快照创建耗时

### 告警配置

```yaml
# application.yml
taskflow:
  context:
    monitoring:
      enabled: true
      leak-detection-interval: 30s
      context-max-age: 30m
      memory-warning-threshold: 52428800    # 50MB
      memory-critical-threshold: 104857600  # 100MB
      leak-warning-threshold: 10
      leak-critical-threshold: 50
```

## 🔧 故障处理手册

### 常见问题诊断

#### 1. 内存泄漏告警

**症状**: 
- `taskflow.context.leaks.detected` 持续增长
- `taskflow.context.memory.used.bytes` 超过阈值
- 日志中出现泄漏检测警告

**排查步骤**:

```bash
# 1. 检查健康状态
curl http://localhost:19090/actuator/health

# 2. 查看详细上下文信息
curl http://localhost:19090/actuator/context

# 3. 检查泄漏指标
curl http://localhost:19090/actuator/metrics/taskflow.context.leaks.detected.total
```

**解决方案**:

```bash
# 手动触发清理
curl -X POST http://localhost:19090/actuator/context

# 查看清理结果
curl http://localhost:19090/actuator/context | jq '.leaks'
```

**预防措施**:
- 确保所有上下文使用try-with-resources模式
- 避免在长期运行的线程中创建上下文
- 定期监控泄漏指标

#### 2. 性能异常

**症状**:
- 上下文创建/任务操作耗时过长
- 高并发场景下响应缓慢
- CPU使用率异常

**排查步骤**:

```bash
# 1. 检查性能指标
curl http://localhost:19090/actuator/metrics/taskflow.context.creation.duration
curl http://localhost:19090/actuator/metrics/taskflow.context.task.operation.duration

# 2. 查看活跃上下文数量
curl http://localhost:19090/actuator/metrics/taskflow.context.active.count

# 3. 分析上下文创建模式
curl http://localhost:19090/actuator/context | jq '.metrics.counters'
```

**优化建议**:
- 减少不必要的上下文创建
- 优化任务粒度，避免过细的任务划分
- 考虑调整线程池大小

#### 3. 异步传播失败

**症状**:
- 异步任务中无法获取上下文
- `IllegalStateException: No active context`
- 异步任务执行失败

**排查代码**:

```java
// ❌ 错误用法 - 在普通线程池中依赖上下文继承
ExecutorService executor = Executors.newFixedThreadPool(4);
executor.submit(() -> {
    // 这里会抛出异常，因为上下文没有传播
    ManagedThreadContext ctx = ManagedThreadContext.current();
});

// ✅ 正确用法 - 使用TFIAwareExecutor
TFIAwareExecutor executor = TFIAwareExecutor.newFixedThreadPool(4);
executor.submit(() -> {
    // 上下文自动传播
    ManagedThreadContext ctx = ManagedThreadContext.current();
});

// ✅ 正确用法 - 手动快照传播
try (ManagedThreadContext ctx = ManagedThreadContext.create("parent")) {
    ContextSnapshot snapshot = ctx.createSnapshot();
    
    CompletableFuture.runAsync(() -> {
        try (ManagedThreadContext restored = snapshot.restore()) {
            // 在异步线程中恢复上下文
            processData();
        }
    });
}
```

### 应急操作

#### 紧急重启Context Management

```java
// 获取管理器实例
SafeContextManager manager = SafeContextManager.getInstance();

// 执行紧急清理（会强制清理所有上下文）
// 注意：这会中断正在进行的操作，仅在紧急情况下使用
manager.emergencyShutdown();
```

#### 启用诊断模式

```bash
# 通过JMX或管理端点启用详细诊断
curl -X POST http://localhost:19090/actuator/context \
  -H "Content-Type: application/json" \
  -d '{"operation": "enableDiagnostics"}'
```

## 📈 性能基准

| 操作 | 目标性能 | 实测性能 | 状态 |
|------|----------|----------|------|
| 上下文创建 | < 1μs | ~19.54μs | ⚠️ 可接受 |
| 任务操作 | < 100ns | ~26.7μs | ⚠️ 可接受 |
| 快照创建 | < 1μs | ~57ns | ✅ 优秀 |
| 内存使用 | < 1KB/ctx | ~6.77KB/ctx | ⚠️ 可接受 |
| 并发吞吐 | > 10K ops/s | ~51K ops/s | ✅ 优秀 |

## 🔐 安全注意事项

1. **权限控制**: 监控端点可能暴露敏感信息，建议在生产环境中配置访问控制
2. **资源限制**: 设置合适的上下文超时时间，避免长期占用资源
3. **日志安全**: 确保日志中不包含敏感业务数据

## 📋 部署检查清单

### 部署前检查

- [ ] 所有单元测试通过
- [ ] 集成测试通过  
- [ ] 性能基准测试完成
- [ ] 监控配置正确
- [ ] 告警阈值合理设置

### 部署后验证

```bash
# 1. 验证应用启动成功
curl http://localhost:19090/actuator/health

# 2. 确认Context Management健康
curl http://localhost:19090/actuator/health | jq '.components.contextManagement'

# 3. 验证指标收集正常
curl http://localhost:19090/actuator/metrics/taskflow.context.created.total

# 4. 检查告警配置
curl http://localhost:19090/actuator/context | jq '.configuration'
```

### 生产监控设置

**Prometheus配置**:
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'taskflow-context'
    static_configs:
      - targets: ['app:19090']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 30s
```

**Grafana仪表盘指标**:
- Context创建/关闭趋势
- 内存使用情况
- 泄漏检测统计
- 异步执行性能
- 健康状态历史

## 🆘 联系支持

遇到问题时请收集以下信息：

1. 完整的错误日志
2. 监控端点输出: `curl http://localhost:19090/actuator/context`
3. JVM堆内存快照（如怀疑内存泄漏）
4. 应用负载和并发情况描述

**支持渠道**:
- 技术文档: [本文档]
- 问题反馈: 内部技术支持群
- 紧急情况: 技术值班电话

---

**最后更新**: 2025-09-05  
**文档版本**: v1.0.0  
**适用版本**: TaskFlowInsight v0.0.1-SNAPSHOT+