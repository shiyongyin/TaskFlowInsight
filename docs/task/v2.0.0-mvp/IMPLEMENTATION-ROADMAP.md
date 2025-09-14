Title: TaskFlow Insight M2-M0 MVP 实施路线图
Version: v2.0.0-M0
Date: 2025-09-10
Status: Ready-for-Execution

一、执行概览
- 目标：一周内交付 M2-M0 MVP 版本
- 关键路径：Core → API → Integration → Testing → Demo
- 并行策略：Phase A/B 部分并行，Phase C/D 串行验证

二、关键代码路径映射

1. 核心包结构（新增）
```
src/main/java/com/syy/taskflowinsight/
├── tracking/                      # 变更追踪核心包
│   ├── model/
│   │   └── ChangeRecord.java     # TASK-201: 变更记录模型
│   ├── snapshot/
│   │   └── ObjectSnapshot.java   # TASK-201: 对象快照采集器
│   ├── detector/
│   │   └── DiffDetector.java     # TASK-202: 差异检测器
│   └── ChangeTracker.java        # TASK-203: 线程隔离管理器
└── api/
    └── TFI.java                   # TASK-210: 新增4个API方法
```

2. 集成点（修改）
- `TFI.java#stop()`：在方法尾部集成自动刷新（getChanges → 写入当前 TaskNode 的 CHANGE → clearAllTracking，使用 try/finally 保护）
- `ManagedThreadContext.java#close()`：finally 中调用 `ChangeTracker.clearAllTracking()` 兜底清理
- `TFI.java#endSession()`：在结束会话时调用 `ChangeTracker.clearAllTracking()`，确保三处清理一致且幂等

三、Phase A：核心功能（D+0 ~ D+2）

TASK-201: ChangeRecord & ObjectSnapshot [预计4h]
- 实现位置：`tracking/model/ChangeRecord.java`
- 关键字段：objectName, fieldName, oldValue, newValue, timestamp, sessionId, taskPath
- 特殊处理：
  - M0 仅纳入标量/字符串/日期字段进入快照 Map，复杂对象不进入快照（避免高代价 toString 与误报）；
  - valueRepr：对参与 diff 的值统一生成字符串表现并按 8192 截断（先转义后截断，尾部 `... (truncated)`）；
  - Date 类型深拷贝防篡改。
- 验证点：单测覆盖空值、边界值、截断策略

TASK-202: DiffDetector [预计3h]
- 实现位置：`tracking/detector/DiffDetector.java`
- 核心方法：`diff(Map<String,Object> before, Map<String,Object> after)`
- 对比规则：
  - CREATE: old=null, new!=null
  - DELETE: old!=null, new=null
  - UPDATE: 类型不同或 equals 不等
- 性能要求：2字段对比 P95 < 200μs

TASK-203: ChangeTracker [预计4h]
- 实现位置：`tracking/ChangeTracker.java`
- ThreadLocal 结构：`Map<String, ObjectSnapshot>`
- 核心方法：
  - track(name, target, fields)
  - trackAll(targets)
  - getChanges() - 返回增量变更
  - clearAllTracking()
- 清理策略：5 分钟定时器（可选，默认关闭，通过配置开启）；核心依赖生命周期清理（stop/close/endSession）。

TASK-210: TFI API 扩展 [预计2h]
- 修改位置：`api/TFI.java`
- 新增静态方法，委托给 ChangeTracker
- 错误处理模板：
```java
try { 
    ChangeTracker.track(name, target, fields); 
} catch (Throwable t) { 
    handleInternalError("Failed to track: " + name, t); 
}
```

四、Phase B：集成与配置（D+2 ~ D+3）

TASK-204: TFI.stop() 集成 [预计2h]
- 修改位置：`TFI.java#stop()`
- 逻辑：在尾部调用 `getChanges()`，将结果写入当前 TaskNode 的 CHANGE 消息，随后 `clearAllTracking()`；使用 try/finally 保证清理；无变更不输出；避免重复输出。
- 消息格式：`Order.status: PENDING → PAID`

TASK-211: withTracked 便捷API [预计2h]
- 位置：`TFI.java` 新增方法
- 签名：`public static void withTracked(String name, Object target, Runnable body, String... fields)`、`public static <T> T withTracked(String name, Object target, Callable<T> body, String... fields)`
- 实现：执行前 `track(...)`；finally 中获取增量变更并写入当前 TaskNode 的 CHANGE，随后 `clearAllTracking()`；不吞业务异常。由于已清理，`TFI.stop()` 不会再次输出这些变更。

TASK-220: ManagedThreadContext 清理 [预计1h]
- 修改：`ManagedThreadContext.java#close()`
- 添加：`ChangeTracker.clearAllTracking()`

TASK-240: 配置实现 [预计2h]
- 方式：直接采用 Spring Boot `@ConfigurationProperties`（新增 `ChangeTrackingProperties`），集中承载默认值；`System.getProperty` 仅作为极端 fallback。
- 配置键前缀：`tfi.change-tracking`
- 关键配置（M0）：
  - enabled: false（独立于全局 TFI 开关）
  - value-repr-max-length: 8192
  - cleanup-interval-minutes: 5（定时清理器默认关闭，开启时使用该周期）
  - 说明：自适应水位与高级策略推迟到 M1，不在 M0 强制实现。

五、Phase C：性能与测试（D+3 ~ D+5）

TASK-250: 基准测试 [预计3h]
- JMH 基准（优先）或 JUnit 微基准
- 场景：2/20 字段，8/16 线程并发
- 验收：以延迟门槛为主（建议：2 字段 P95 ≤ 200μs）；CPU 占比作为报告项，不作为 M0 硬门槛。

TASK-251: 自适应截断 [预计2h，推迟到 M1]
- 说明：水位（capacity watermark）定义与实现依赖后续存储/计数机制。M0 不纳入验收，仅作为 M1 优化项；可选以“每会话变更条数占上限”做实验性验证。

TASK-260~264: 测试套件 [预计4h]
- 单元测试：DiffDetector 标量对比
- 并发测试：线程隔离验证
- 生命周期测试：三个清理点验证
- 消息格式测试：Console/JSON 输出验证
- 缓存测试：反射元数据命中率

六、Phase D：Demo与文档（D+5 ~ D+7）

TASK-270: ChangeTrackingDemo [预计2h]
- 位置：`src/test/java/.../demo/ChangeTrackingDemo.java`
- 场景1：显式API演示
- 场景2：withTracked便捷API
- 输出：Console展示变更消息

七、关键验收点

D+2 里程碑：
- [ ] Core/API 可运行
- [ ] Console 能看到 CHANGE 消息
- [ ] 单测通过率 > 80%

D+3 里程碑：
- [ ] 性能基准报告生成（记录机器/参数）
- [ ] 2 字段 P95 ≤ 200μs（建议目标）
- [ ] CPU 占比为观测项（不做硬门槛）

D+5 里程碑：
- [ ] 并发测试通过
- [ ] 无内存泄漏
- [ ] 生命周期清理验证

D+7 里程碑：
- [ ] Demo 可运行
- [ ] 文档更新完整
- [ ] PR 准备就绪

八、风险与缓解

1. 反射性能风险
   - 缓解：元数据缓存 + setAccessible 批量处理
   - 监控：基准测试持续跟踪

2. 内存泄漏风险
   - 缓解：三层清理机制 + 定时器兜底
   - 验证：长时运行 + HeapDump 分析

3. 线程安全风险
   - 缓解：ThreadLocal 隔离 + 不可变快照
   - 测试：并发压测验证

九、并行执行建议

可并行任务组：
- Group 1: TASK-201/202（模型与检测器）
- Group 2: TASK-203/210（追踪器与API）
- Group 3: TASK-260~264（测试套件）

依赖链：
- 201 → 202 → 203 → 210 → 204
- 203 → 220/221
- 204 → 230
- All → 270

十、每日 Standup 清单

Day 1:
- [ ] ChangeRecord 模型完成
- [ ] ObjectSnapshot 实现
- [ ] DiffDetector 框架搭建

Day 2:
- [ ] ChangeTracker 完成
- [ ] TFI API 集成
- [ ] 基本单测通过

Day 3:
- [ ] TFI.stop() 集成
- [ ] 性能基准初版
- [ ] 配置框架完成

Day 4:
- [ ] 并发测试通过
- [ ] 生命周期验证
- [ ] 性能优化

Day 5:
- [ ] Demo 编写
- [ ] 文档更新
- [ ] 集成测试

Day 6-7:
- [ ] Bug 修复
- [ ] 代码审查
- [ ] PR 提交

十一、代码审查清单

架构层：
- [ ] 包结构清晰，职责单一
- [ ] 接口设计符合 Design 文档
- [ ] 错误处理统一且透明

实现层：
- [ ] ThreadLocal 正确使用和清理
- [ ] 反射操作安全且有缓存
- [ ] 截断策略按预期工作

测试层：
- [ ] 单测覆盖率 ≥ 80%
- [ ] 性能指标达标
- [ ] 并发场景验证充分

文档层：
- [ ] Javadoc 完整
- [ ] README 更新
- [ ] Demo 可运行

十二、交付物清单

代码：
- [ ] tracking 包实现（4个核心类）
- [ ] TFI.java 扩展（4+1个方法）
- [ ] 生命周期集成（3处修改）
- [ ] 配置实现

测试：
- [ ] 单元测试套件
- [ ] 性能基准代码
- [ ] 并发测试用例
- [ ] Demo 程序

文档：
- [ ] API 使用指南
- [ ] 性能测试报告
- [ ] 发布说明
- [ ] 快速入门更新
