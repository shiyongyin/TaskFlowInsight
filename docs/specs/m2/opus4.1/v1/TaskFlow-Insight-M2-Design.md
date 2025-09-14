# TaskFlow Insight — M2 设计说明书

**版本**: v2.0.0  
**负责人**: 架构团队  
**状态**: 正式版  
**日期**: 2025-01-10  

---

## 1. 现状基线（已有能力/缺口清单）

### 已有能力（源码证据）
- ✅ **核心API门面**：TFI.java【src/main/java/com/syy/taskflowinsight/api/TFI.java】
- ✅ **会话管理**：Session.java【src/main/java/com/syy/taskflowinsight/model/Session.java】
- ✅ **任务节点**：TaskNode.java【src/main/java/com/syy/taskflowinsight/model/TaskNode.java】
- ✅ **消息模型**：Message.java + MessageType枚举【src/main/java/com/syy/taskflowinsight/model/】
- ✅ **上下文管理**：ManagedThreadContext【src/main/java/com/syy/taskflowinsight/context/】
- ✅ **导出能力**：JsonExporter/ConsoleExporter/MapExporter【src/main/java/com/syy/taskflowinsight/exporter/】

### 缺口清单（需M2实现）
- ❌ 变更追踪API（track/trackAll/getChanges/clearAllTracking）
- ❌ 变更检测引擎（DiffDetector）
- ❌ 对象快照机制（ObjectSnapshot）
- ❌ 查询构建器（ChangeQuery）
- ❌ Spring注解支持（@TrackChanges）
- ❌ 持久化层（SessionStorage抽象）
- ❌ HTML报告变更模块
- ❌ 监控规则引擎

## 2. 总体架构

```
┌──────────────────────────────────────────────────────┐
│                    应用层                              │
│         Spring Boot Application / 业务代码             │
└────────────────┬─────────────────────────────────────┘
                 │ 使用
┌────────────────▼─────────────────────────────────────┐
│                  TFI API 层                           │
│   track() / trackAll() / getChanges() / query()      │
│            【扩展 TFI.java 现有API】                    │
└────────────────┬─────────────────────────────────────┘
                 │ 委托
┌────────────────▼─────────────────────────────────────┐
│              变更追踪核心层                             │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │ChangeTracker│  │ DiffDetector │  │ChangeQuery│ │
│  └──────────────┘  └──────────────┘  └────────────┘ │
└────────────────┬─────────────────────────────────────┘
                 │ 依赖
┌────────────────▼─────────────────────────────────────┐
│                 数据模型层                             │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │ChangeRecord │  │ObjectSnapshot│  │ObjectState │ │
│  └──────────────┘  └──────────────┘  └────────────┘ │
└────────────────┬─────────────────────────────────────┘
                 │ 存储
┌────────────────▼─────────────────────────────────────┐
│                  存储抽象层                            │
│   ThreadLocal（M2-M0） → Memory → JDBC → Redis        │
└──────────────────────────────────────────────────────┘
```

## 3. 模块设计

### 3.1 core 模块（变更追踪核心）

**包路径**: com.syy.taskflowinsight.tracking

**核心类职责**：

| 类名 | 职责 | 依赖 | 扩展点 |
|------|------|------|--------|
| ChangeTracker | 追踪生命周期管理 | ThreadLocal | 追踪策略接口 |
| DiffDetector | 变更检测算法 | PropertyReader | 比较策略接口 |
| PropertyReader | 属性访问抽象 | 反射API | 访问策略接口 |
| SnapshotStore | 快照存储管理 | Cache | 存储策略接口 |
| TrackingContext | 线程级上下文 | ConcurrentHashMap | - |

### 3.2 model 模块（数据模型）

**包路径**: com.syy.taskflowinsight.tracking.model

**数据实体**：

```java
// 变更记录【新增】
class ChangeRecord {
    String objectName;
    String fieldName;
    Object oldValue;
    Object newValue;
    ChangeType type;
    long timestamp;
    String nodeId;      // 关联到 TaskNode.id
    String sessionId;   // 关联到 Session.sessionId
}

// 对象快照【新增】
class ObjectSnapshot {
    String objectId;
    Map<String, Object> fieldValues;
    long captureTime;
}

// 扩展现有 TaskNode【修改 TaskNode.java】
class TaskNode {
    // 现有字段...
    List<ChangeRecord> changes;  // 新增：关联的变更记录
}
```

### 3.3 spring 模块（Spring集成）

**包路径**: com.syy.taskflowinsight.spring.tracking

**集成组件**：

| 组件 | 类型 | 配置键 | 说明 |
|------|------|--------|------|
| @TrackChanges | 注解 | - | 方法级变更追踪 |
| TrackChangesAspect | 切面 | tfi.tracking.enabled | AOP拦截器 |
| TFITrackingAutoConfiguration | 自动配置 | tfi.tracking.* | Spring Boot Starter |
| TFITrackingProperties | 配置类 | tfi.tracking | 配置属性映射 |

### 3.4 export 模块（报告生成）

**扩展路径**: com.syy.taskflowinsight.exporter

**扩展现有导出器**：

```java
// 扩展 HtmlExporter【TODO：当前缺失，需新建】
class HtmlExporter {
    String export(Session session) {
        // 包含变更时间线
        // 包含变更热力图
        // 包含审计记录表
    }
}

// 新增变更导出器
class ChangeExporter {
    String toJson(List<ChangeRecord> changes);
    String toCsv(List<ChangeRecord> changes);
    String toMarkdown(List<ChangeRecord> changes);
}
```

## 4. 数据与存储

### 4.1 存储模式演进

| 阶段 | 存储方案 | 容量限制 | 持久化 | 查询能力 |
|------|---------|---------|--------|----------|
| M2-M0 | ThreadLocal | 1000对象/线程 | 否 | 仅当前线程 |
| M2-M1 | 内存Map | 10000记录 | 否 | 全局查询 |
| M2-M2 | JDBC | 无限制 | 是 | SQL查询 |
| M3 | Redis/ES | PB级 | 是 | 全文检索 |

### 4.2 数据库设计（M2-M2）

```sql
-- 变更记录表
CREATE TABLE tfi_change_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(36),
    node_id VARCHAR(36),
    object_name VARCHAR(100),
    field_name VARCHAR(100),
    old_value TEXT,
    new_value TEXT,
    change_type VARCHAR(20),
    timestamp BIGINT,
    user_id VARCHAR(50),
    operation VARCHAR(100),
    INDEX idx_session (session_id),
    INDEX idx_timestamp (timestamp),
    INDEX idx_object (object_name, field_name)
);

-- 对象快照表
CREATE TABLE tfi_object_snapshots (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    object_id VARCHAR(100),
    snapshot_data JSON,
    capture_time BIGINT,
    session_id VARCHAR(36),
    INDEX idx_object_time (object_id, capture_time)
);
```

### 4.3 缓存策略

- **L1缓存**：ThreadLocal，无延迟
- **L2缓存**：Caffeine，LRU淘汰
- **L3存储**：数据库，持久化

## 5. 关键 API

### 5.1 公共API扩展【修改 TFI.java】

```java
public final class TFI {
    // ===== 新增变更追踪API =====
    public static void track(String name, Object target, String... fields) {
        ChangeTracker.getInstance().track(name, target, fields);
    }
    
    public static void trackAll(Map<String, Object> targets) {
        ChangeTracker.getInstance().trackAll(targets);
    }
    
    public static List<ChangeRecord> getChanges() {
        return ChangeTracker.getInstance().detectChanges();
    }
    
    public static void clearAllTracking() {
        ChangeTracker.getInstance().clearAll();
    }
    
    public static List<ChangeRecord> queryChanges(ChangeQuery query) {
        return ChangeTracker.getInstance().query(query);
    }
}
```

### 5.2 Spring注解【新增】

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackChanges {
    String operation() default "";
    boolean autoDetect() default true;
    String[] exclude() default {};
}
```

### 5.3 REST API【新增，可选】

```yaml
# 查询变更记录
GET /api/tfi/changes?sessionId={id}&limit=100

# 获取对象历史
GET /api/tfi/history/{objectName}

# 导出审计日志
GET /api/tfi/export?format=json&from={timestamp}&to={timestamp}
```

## 6. 时序与业务流

### 6.1 写路径（变更追踪流程）

```
用户代码 → TFI.track() → ChangeTracker.track()
    ↓
创建ObjectSnapshot（T0时刻快照）
    ↓
执行业务逻辑（对象状态变化）
    ↓
TFI.getChanges() → DiffDetector.detect()
    ↓
创建ObjectSnapshot（T1时刻快照）
    ↓
对比差异 → 生成ChangeRecord
    ↓
关联到当前TaskNode
    ↓
存储（ThreadLocal → Memory → DB）
```

### 6.2 读路径（查询检索流程）

```
用户查询 → TFI.queryChanges(query)
    ↓
构建查询条件
    ↓
多级存储查询（L1 → L2 → L3）
    ↓
结果聚合与排序
    ↓
返回List<ChangeRecord>
```

### 6.3 回放路径（历史重建流程）

```
TFI.getStateAt(objectName, timestamp)
    ↓
查询该时间点前所有变更
    ↓
按时间顺序重放变更
    ↓
构建ObjectState
    ↓
返回历史状态
```

## 7. 非功能与工程治理

### 7.1 性能预算

| 操作 | 目标延迟 | 实际测量 | 优化措施 |
|------|---------|---------|---------|
| track() | <1ms | TODO | 缓存反射元数据 |
| detectChanges() | <10ms/对象 | TODO | 并行检测 |
| query() | <200ms@10k | TODO | 索引优化 |
| export() | <5s@100k | TODO | 流式处理 |

### 7.2 幂等性保证

- **追踪幂等**：重复track同一对象覆盖而非累加
- **检测幂等**：连续detectChanges()返回相同结果
- **查询幂等**：查询操作不改变数据状态

### 7.3 重试与补偿

```java
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 100))
public void persistChanges(List<ChangeRecord> changes) {
    // 持久化重试
}

@Recover
public void recoverPersist(Exception e, List<ChangeRecord> changes) {
    // 降级到本地文件
    writeToLocalFile(changes);
}
```

### 7.4 可观测性

**指标埋点**：
- tfi.tracking.objects.count - 追踪对象数
- tfi.tracking.changes.count - 变更记录数
- tfi.tracking.detect.duration - 检测耗时
- tfi.tracking.memory.usage - 内存占用

**日志规范**：
```java
logger.debug("[TRACKING] Started tracking object: {}", objectName);
logger.info("[TRACKING] Detected {} changes in {}ms", count, duration);
logger.error("[TRACKING] Failed to persist changes", exception);
```

### 7.5 安全与权限

- **字段脱敏**：敏感字段（password/token）自动排除
- **审计控制**：通过注解控制哪些操作需要审计
- **数据加密**：存储层支持透明加密

## 8. 上线与灰度

### 8.1 功能开关

```yaml
tfi:
  tracking:
    enabled: true                  # 总开关
    features:
      annotation: true             # 注解支持
      auto-detect: true           # 自动检测
      persistence: false          # 持久化（灰度）
      export: true                # 导出功能
```

### 8.2 灰度策略

| 阶段 | 范围 | 监控指标 | 回滚条件 |
|------|------|---------|---------|
| Alpha | 开发环境 | 功能完整性 | 单测失败 |
| Beta | 测试环境10% | 性能影响 | 延迟>20ms |
| RC | 生产环境1% | 错误率 | 错误>0.1% |
| GA | 全量上线 | 用户反馈 | P0问题 |

### 8.3 回滚方案

```java
// 紧急开关
if (emergencyKillSwitch.isEnabled()) {
    // 跳过所有追踪逻辑
    return Collections.emptyList();
}

// 降级模式
if (degradationMode.isActive()) {
    // 仅追踪关键对象
    return trackCriticalOnly();
}
```

## 9. 可行性评分

| 维度 | 分数 | 设计侧论证 |
|------|------|-----------|
| **功能契合** | 9/10 | 完全满足M2需求，API设计合理 |
| **稳定性** | 8/10 | ThreadLocal隔离+异常处理完善 |
| **性能风险** | 7/10 | 反射有开销但可接受，已设计缓存优化 |
| **可扩展性** | 9/10 | 策略模式+插件化设计，易于扩展 |
| **过度设计** | 2/10 | 聚焦MVP，避免过早优化 |

## 10. 待确认与开放问题

| ID | 问题 | 所需证据 | 负责人 | 期限 |
|----|------|---------|--------|------|
| Q1 | HtmlExporter类是否已存在？ | 源码确认 | 开发 | D+1 |
| Q2 | 是否需要支持跨线程追踪？ | 业务场景 | 产品 | D+2 |
| Q3 | 敏感字段列表如何配置？ | 安全规范 | 安全 | D+3 |
| Q4 | 持久化存储选型？ | 运维要求 | 运维 | D+5 |
| Q5 | 是否集成已有监控系统？ | 技术栈 | 架构 | D+7 |

---

**附录A：关键代码位置映射**

| 功能 | 新增文件 | 修改文件 |
|------|---------|---------|
| 核心API | - | TFI.java#L500+ |
| 变更追踪 | ChangeTracker.java | - |
| 数据模型 | ChangeRecord.java | TaskNode.java#L100+ |
| Spring集成 | TrackChangesAspect.java | - |
| 配置 | TFITrackingProperties.java | application.yml |

**附录B：风险缓解详情**

1. **内存泄漏风险**：使用WeakReference + 定时清理
2. **循环引用风险**：深度限制 + visited标记
3. **并发风险**：ThreadLocal隔离 + CopyOnWrite
4. **性能退化风险**：熔断器 + 自动降级