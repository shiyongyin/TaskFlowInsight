# PROMPT-M2M1-001-ObjectSnapshotDeep 开发提示词

## 1) SYSTEM
你是**资深 Java 开发工程师**与**AI 结对编程引导者**。你需要基于下述"上下文与证据"，**按步骤**完成实现并给出**可提交的变更**（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../task/v2.1.0-vip/tracking-core/M2M1-001-ObjectSnapshotDeep.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/core/context#ChangeTracker
  - src/main/java/com/syy/taskflowinsight/tracking/snapshot#ObjectSnapshot（现有）
- 相关配置：
  - src/main/resources/application.yml: tfi.change-tracking.snapshot.max-depth
  - src/main/resources/application.yml: tfi.change-tracking.snapshot.max-stack-depth
- 工程操作规范：../../develop/开发工程师提示词.txt（必须遵循）
- 历史提示词风格参考：../../develop/v1.0.0-mvp/design/api-implementation/prompts/DEV-010-TFI-MainAPI-Prompt.md

## 3) GOALS（卡片→可执行目标）
- 业务目标：提供可控、可观测、可回退的深度对象快照能力，服务变更追踪（前后快照）
- 技术目标：
  - 实现 SnapshotFacade 统一路由类
  - 实现 ObjectSnapshotDeep 深度遍历类
  - 配置 maxDepth=3, maxStackDepth=1000 护栏
  - 集成循环引用检测机制
  - 暴露 depth.limit、cycle.skip 指标

## 4) SCOPE
- In Scope（当次实现必做）：
  - [ ] 创建 com.syy.taskflowinsight.tracking.snapshot.SnapshotFacade
  - [ ] 创建 com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep
  - [ ] 实现串行 DFS 深度遍历（maxDepth=3）
  - [ ] 实现循环引用检测（IdentityHashMap）
  - [ ] 集成 CollectionSummary 摘要处理
  - [ ] 配置 include/exclude 路径匹配
  - [ ] 暴露轻量级指标接口
- Out of Scope（排除项）：
  - [ ] 并行 DFS（性能风险）
  - [ ] 元素级集合 Diff（复杂度过高）
  - [ ] 平台特化优化

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 列出受影响模块与文件：
   - 新建：com.syy.taskflowinsight.tracking.snapshot.SnapshotFacade
   - 新建：com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep
   - 新建：com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig
   - 修改：com.syy.taskflowinsight.core.context.ChangeTracker（路由到 Facade）
   - 配置：application.yml 新增 tfi.change-tracking.snapshot.*

2. 给出重构/新建的**类与方法签名**：
```java
// SnapshotFacade.java
public class SnapshotFacade {
    public Map<String, Object> capture(Object root, SnapshotConfig config);
    private boolean shouldUseDeep(SnapshotConfig config);
}

// ObjectSnapshotDeep.java
public class ObjectSnapshotDeep {
    public Map<String, Object> captureDeep(Object root, int maxDepth, Set<String> includes, Set<String> excludes);
    private void traverseDFS(Object obj, String path, int depth, Map<String, Object> result, Set<Object> visited);
    private boolean checkCycle(Object obj, Set<Object> visited);
}

// SnapshotConfig.java
@Data
public class SnapshotConfig {
    private int maxDepth = 3;
    private int maxStackDepth = 1000;
    private Set<String> includes = new HashSet<>();
    private Set<String> excludes = new HashSet<>();
    private boolean enableDeep = true;
}
```

3. 逐步实现：接口契约 → 领域模型 → 服务实现 → 配置 → 观测埋点
4. 给出**完整补丁**或**文件全量内容**
5. 更新配置和文档
6. 提交前自测步骤

## 6) DELIVERABLES（输出必须包含）
- 代码改动：
  - 新文件：SnapshotFacade.java, ObjectSnapshotDeep.java, SnapshotConfig.java
  - 变更文件：ChangeTracker.java（添加 Facade 路由）
- 测试：
  - 单测：ObjectSnapshotDeepTest（深度控制、循环检测、路径匹配）
  - 集成测试：SnapshotFacadeIntegrationTest
- 文档：README 更新深度快照配置说明
- 回滚/灰度：tfi.change-tracking.snapshot.enable-deep 开关
- 观测：depth.limit.reached、cycle.detected 计数器

## 7) API & MODELS（必须具体化）
- 接口签名：
```java
public interface SnapshotCapture {
    Map<String, Object> capture(Object root, SnapshotConfig config);
}
```
- 错误处理：反射失败跳过，不抛异常
- 数据模型：Map<String, Object> 扁平路径键值对
- 权限：无需特殊权限，内部组件

## 8) DATA & STORAGE
- 无持久化需求，纯内存操作
- 循环检测使用 IdentityHashMap 保证对象唯一性
- 字段元数据缓存：LRU 上限 1024 个类

## 9) PERFORMANCE & RELIABILITY
- 性能预算：P95 ≤ 50ms（1000 字段对象）
- CPU 开销：< 5% 增量
- 内存：单次快照 < 10MB
- 失败策略：降级到浅层快照，不影响业务主流程
- 护栏配置：
  - tfi.change-tracking.snapshot.max-depth=3
  - tfi.change-tracking.snapshot.max-stack-depth=1000

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 80%
  - [ ] 深度控制边界测试（depth=0,1,2,3,4）
  - [ ] 循环引用检测（自引用、互引用、链式引用）
  - [ ] Include/Exclude 路径匹配测试
  - [ ] 异常处理（null、反射失败、栈溢出）
- 集成测试：
  - [ ] ChangeTracker 集成场景
  - [ ] 配置开关切换测试
  - [ ] 性能基线测试（1000/10000 字段）
- 性能测试：
  - [ ] JMH 基准测试：深度 vs 性能曲线

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：深度遍历正确，循环检测有效
- [ ] 文档：配置项说明完整
- [ ] 观测：指标正确上报
- [ ] 性能：P95 < 50ms
- [ ] 兼容：旧路径可回退

## 12) RISKS & MITIGATIONS
- 性能风险：深度过大导致 CPU 飙升 → maxDepth 护栏 + 监控告警
- 内存风险：大对象图 OOM → 对象数量限制 + 降级
- 兼容性：破坏现有快照格式 → Facade 路由，开关控制
- 反射安全：SecurityManager 限制 → trySetAccessible + 跳过

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 现有 ObjectSnapshot 类位置在 core 包，建议迁移到 tracking.snapshot 包统一管理

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 问题1：include/exclude 默认配置清单从哪里获取？
  - 责任人：架构组
  - 期限：实现前确认
  - 所需：默认排除路径列表（如 *.password, *.secret）
- [ ] 问题2：Micrometer 指标桥接实现细节？
  - 责任人：监控组
  - 期限：集成测试前
  - 所需：MetricRegistry 注入方式