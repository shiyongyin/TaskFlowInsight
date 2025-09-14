# TaskFlowInsight v2.1.0-MVP

## 项目概述

TaskFlowInsight 是一个轻量级的 Java 对象变更追踪框架，基于 Spring Boot 3.5.5 和 Java 21 构建。它提供了高性能的对象状态追踪、变更检测和统计分析功能，适用于审计日志、状态管理和数据同步等场景。

## 核心特性

### 🎯 主要功能
- **对象变更追踪**：自动追踪对象状态变化，支持深度追踪和批量操作
- **高性能差异检测**：P50 < 50μs, P95 < 200μs 的差异检测性能
- **灵活的输出格式**：支持控制台、JSON、自定义格式输出
- **Spring Boot 集成**：自动装配、配置管理、条件装配
- **线程安全设计**：基于 ThreadLocal 的隔离机制
- **内存优化**：WeakReference 管理，自动清理，最大 1000 对象限制

### 📊 统计分析
- 变更类型分布统计（CREATE/UPDATE/DELETE）
- 性能指标监控（P50/P95/P99）
- 热点对象识别
- 实时追踪报告

## 快速开始

### 环境要求
- Java 21+
- Maven 3.6+
- Spring Boot 3.5.5

### 构建运行
```bash
# 编译项目
./mvnw clean compile

# 运行测试
./mvnw test

# 运行应用
./mvnw spring-boot:run

# 打包
./mvnw clean package
```

### 基础用法

#### 1. 简单追踪
```java
// 开始追踪对象
TFI.track("user", userObject);

// 修改对象...
userObject.setName("New Name");

// 获取变更
List<ChangeRecord> changes = TFI.getChanges();

// 清理追踪
TFI.clearAllTracking();
```

#### 2. 批量追踪
```java
Map<String, Object> targets = new HashMap<>();
targets.put("user1", user1);
targets.put("user2", user2);
targets.put("order", order);

// 批量追踪（自动优化性能）
TFI.trackAll(targets);
```

#### 3. 统计信息
```java
// 获取统计信息
TrackingStatistics stats = TFI.getStatistics();
StatisticsSummary summary = stats.getSummary();

// 查看性能指标
PerformanceStatistics perf = stats.getPerformanceStatistics();
System.out.println("P95: " + perf.p95Micros + "μs");
```

## 配置说明

### application.yml 配置示例
```yaml
tfi:
  change-tracking:
    enabled: true
    value-repr-max-length: 8192
    cleanup-interval-minutes: 5
    
    snapshot:
      enable-deep: false
      max-depth: 3
      max-elements: 100
      excludes:
        - "*.password"
        - "*.secret"
        - "*.token"
        - "*.key"
    
    diff:
      output-mode: compat  # compat | enhanced
      include-null-changes: false
      max-changes-per-object: 1000
    
    export:
      format: json  # json | console
      pretty-print: true
      include-sensitive-info: false
```

## 架构设计

### 核心组件
1. **DiffDetector**：差异检测引擎
2. **ChangeTracker**：变更追踪管理器
3. **ObjectSnapshot**：对象快照生成器
4. **ChangeExporter**：变更导出器（JSON/Console）
5. **TFI**：统一门面 API
6. **TrackingStatistics**：统计分析引擎

### 设计模式
- **门面模式**：TFI 提供统一入口
- **策略模式**：可插拔的导出器和检测器
- **建造者模式**：配置和选项构建
- **单例模式**：全局追踪管理

## 性能指标

| 操作 | P50 | P95 | P99 |
|------|-----|-----|-----|
| 差异检测 | < 50μs | < 200μs | < 500μs |
| 快照生成 | < 100μs | < 500μs | < 1ms |
| 批量追踪(100对象) | < 10ms | < 50ms | < 100ms |

## 测试覆盖

- **单元测试**：90+ 测试用例
- **集成测试**：Spring Boot 自动配置测试
- **并发测试**：多线程场景验证
- **性能测试**：基准测试和压力测试
- **边界测试**：极限场景和异常处理

## 开发团队

TaskFlow Insight Team

## 许可证

Apache License 2.0

## 版本历史

- **v2.1.0-MVP** (2025-01-13)
  - 初始 MVP 版本发布
  - 核心变更追踪功能
  - Spring Boot 自动配置
  - 批量操作优化
  - 统计分析功能