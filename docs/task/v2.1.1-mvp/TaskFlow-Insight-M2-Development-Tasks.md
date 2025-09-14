# TaskFlow Insight - v2.0.0-M2-m1 开发任务总览

## 0. 设计文档预评估报告

### ✅ 设计亮点
- **架构清晰**：分层架构设计合理，Facade模式提供统一API，模块职责明确
- **性能考量充分**：串行DFS设计避免并发复杂性，多级缓存策略（反射缓存、Pattern缓存）降低开销
- **护栏机制完善**：深度限制、循环检测、降级策略等多重保护机制
- **向后兼容**：保持M0基线API不变，新功能通过配置开关控制

### ⚠️ 风险与问题

| 风险ID | 类型 | 描述 | 影响范围 | 严重度 | 建议方案 |
|--------|------|------|---------|--------|---------|
| R001 | 性能 | DFS遍历大对象图可能导致性能瓶颈 | 3.1 Tracking Core | 高 | 通过白名单严格控制遍历范围，优先降级 |
| R002 | 技术 | Java 9+模块系统可能导致反射失败 | 3.1 Tracking Core | 中 | 采用trySetAccessible降级策略 |
| R003 | 复杂度 | 集合摘要的stableKey生成规则复杂 | 3.2 集合/Map摘要 | 中 | 提供默认实现，支持自定义扩展 |
| R004 | 并发 | 文件导出单锁可能成为瓶颈 | 3.6 Export | 低 | M2-m2考虑StripedLock |
| R005 | 配置 | 配置项过多可能导致使用困难 | 3.7 Spring Integration | 中 | 提供balanced预设配置 |

### 🔍 需要澄清的点
- **深度限制默认值**：建议默认3层是否合理？（引用第3.1节）
- **Pattern缓存大小**：1000条是否足够生产环境？（引用第3.1节）
- **集合降级阈值**：10000的硬编码阈值是否需要可配置？（引用第3.2节）
- **时区默认值**：UTC vs 系统默认时区的选择？（引用第3.3节）
- **导出路径权限**：/var/log/tfi是否需要特殊权限处理？（引用第3.6节）

### 💡 优化建议
- **建议1**：考虑引入对象池复用DFS过程中的临时数据结构，减少GC压力
- **建议2**：PathMatcherCache可以考虑二级缓存（本地+分布式），提升多实例场景性能
- **建议3**：集合摘要可以引入采样策略，避免大集合全量计算
- **建议4**：考虑支持异步导出，避免阻塞业务线程

## 1. 执行摘要 (Executive Summary)

- **项目范围**: M1(P0+P1) + M2.1最小产品化
  - M1 P0：嵌套扁平化、集合摘要、比较策略、格式引擎
  - M1 P1：内存存储、文件导出
  - M2.1：Spring Boot Starter、Actuator端点、预热机制
  
- **核心目标**:
  1. 实现嵌套对象的深度遍历与扁平化，支持循环检测和深度限制
  2. 提供集合/Map的智能摘要，支持降级和示例提取
  3. 实现灵活的比较策略和格式化模板
  4. 提供Spring Boot Starter封装，简化集成
  5. 建立完善的监控指标体系和护栏机制

- **技术约束**: 
  - Java 21, Spring Boot 3.5.5
  - 默认balanced配置
  - 核心模块不新增外部依赖
  - 保持与M0基线的向后兼容

- **交付计划**:
  - Phase 1 (Week 1-2): M1 P0核心功能 - Tracking Core + Compare Strategy
  - Phase 2 (Week 3-4): M1 P1存储导出 - Storage & Export
  - Phase 3 (Week 5-6): M2.1产品化 - Spring Integration + Monitoring
  - Phase 4 (Week 7-8): 测试与发布 - Quality Assurance + Documentation

## 2. 核心原则与技术约束

### YAGNI原则
- 满足当前需求的最小实现，杜绝过度设计
- 本阶段不实现：并行DFS、复杂导出并发、Locale比较等高阶特性

### 设计阐述
- **串行DFS选择**：避免并发复杂性，降低死锁风险，简化调试
- **IdentityHashMap循环检测**：O(1)性能，内存友好，避免equals开销
- **集合摘要化**：平衡信息完整性与性能开销

### 测试先行
- 每个任务包含明确的单元测试、集成测试要求
- 性能基线：2字段P95≤0.5ms，深度2 P95≤2ms，集合100项P95≤5ms

### 范围护栏
- 深度限制：默认3层，最大常量1000
- Pattern长度：最大512字符，通配符最多32个
- 集合大小：超过100降级，超过10000强制size-only

### 冲突优先级
- PRD/Design > 基线规范 > 现有实现
- 配置冲突时以Design文档为准

### 集合策略（硬约束）
- 一律摘要化（size-only + 示例STRING排序）
- 不展开元素级深度Diff
- 示例项默认Top-3

## 3. 任务分解与依赖关系

### 3.1 任务统计

| 模块 | P0任务 | P1任务 | P2任务 | 总计 |
|------|--------|--------|--------|------|
| Tracking Core | 4 | 0 | 0 | 4 |
| Format Engine | 1 | 0 | 0 | 1 |
| Compare Strategy | 1 | 0 | 0 | 1 |
| Storage & Export | 0 | 2 | 0 | 2 |
| Spring Integration | 0 | 0 | 3 | 3 |
| Guardrails & Monitoring | 1 | 0 | 0 | 1 |
| Testing & Quality | 0 | 2 | 0 | 2 |
| Docs & Examples | 0 | 0 | 1 | 1 |
| **合计** | 7 | 4 | 4 | 15 |

### 3.2 关键路径分析

```mermaid
graph LR
    subgraph Phase1[M1 P0 - Week 1-2]
        A[M2M1-001 ObjectSnapshotDeep] --> B[M2M1-002 CollectionSummary]
        C[M2M1-003 PathMatcherCache] --> A
        B --> D[M2M1-004 DiffDetector扩展]
        E[M2M1-020 CompareService] --> D
    end
    
    subgraph Phase2[M1 P1 - Week 3-4]  
        F[M2M1-030 CaffeineStore] --> G[M2M1-031 JsonExporter]
        D --> F
    end
    
    subgraph Phase3[M2.1 - Week 5-6]
        H[M2M1-040 SpringStarter] --> I[M2M1-041 Actuator端点]
        I --> J[M2M1-042 预热机制]
        G --> H
    end
    
    subgraph Phase4[Test - Week 7-8]
        K[M2M1-060 测试套件] --> L[M2M1-061 性能基线]
        J --> K
    end
```

### 3.3 依赖类型说明
- 🔴 **强依赖**: 必须完成才能开始（如ObjectSnapshotDeep依赖PathMatcherCache）
- 🟡 **弱依赖**: 可并行但有同步点（如CollectionSummary与CompareService）
- 🟢 **软依赖**: 建议顺序但灵活（如文档与测试）

## 4. 详细任务模块

### 模块A: Tracking Core（变更追踪核心）

#### 设计决策
- **串行DFS而非并行**：降低复杂度，避免并发问题，简化调试
- **IdentityHashMap循环检测**：O(1)性能，内存友好，准确识别对象引用
- **局部缓冲策略**：异常隔离，保证已收集数据不丢失

#### 任务列表

##### M2M1-001: SnapshotFacade与ObjectSnapshotDeep实现

**优先级**: P0 | **工期**: L (5-8天) | **前置依赖**: M2M1-003

**背景**:
参考设计文档第3.1节 - 需要支持嵌套对象扁平化，深度/循环护栏

**目标**:
- ✅ 实现DFS遍历，支持深度限制(默认3层)
- ✅ 循环检测与剪枝（IdentityHashMap）
- ✅ 局部缓冲确保异常安全
- ✅ 字段名字典序遍历保证确定性
- ❌ **非目标**: 并行DFS（延至M2-m2）

**核心实现要点**:
```java
// 包路径：com.syy.taskflowinsight.tracking.snapshot
public class ObjectSnapshotDeep {
    // 串行DFS实现，栈式遍历避免递归
    // 使用IdentityHashMap进行O(1)循环检测
    // 局部缓冲+try-finally确保异常不影响已收集数据
    // MAX_STACK_DEPTH=1000防止栈溢出
}
```

**[需要澄清]**:
- 深度限制是否可配置？建议默认3，可通过tfi.nested.max-depth调整
- 字段访问失败是否需要记录到专门的错误集合？

**测试要点**:
- 单测：正常遍历、循环检测、深度剪枝、异常恢复、字段排序
- 性能：1000节点<10ms，内存增量<5MB
- 边界：空对象、单字段、最大深度

**验收标准**:
- [ ] 功能：DFS遍历正确，循环/深度护栏生效
- [ ] 性能：P95<2ms（深度2场景）
- [ ] 质量：单测覆盖>80%，无内存泄漏
- [ ] 可观测：depth.limit和cycle.skip指标正确

**风险与缓解**:
- 风险：反射性能开销 → 缓解：字段元数据缓存(上限1024类)
- 风险：栈溢出 → 缓解：栈深度硬限制(MAX_STACK_DEPTH=1000)
- 风险：模块系统限制 → 缓解：trySetAccessible降级策略

**代码映射**:
- 新增类：ObjectSnapshotDeep, SnapshotFacade
- 修改类：ChangeTracker（调用SnapshotFacade）
- 测试类：ObjectSnapshotDeepTest

---

##### M2M1-002: CollectionSummary实现

**优先级**: P0 | **工期**: M (3-4天) | **前置依赖**: 无

[继续其他任务卡...]

##### M2M1-003: PathMatcherCache实现

**优先级**: P0 | **工期**: M (3-4天) | **前置依赖**: 无

##### M2M1-004: DiffDetector扩展

**优先级**: P0 | **工期**: M (3-4天) | **前置依赖**: M2M1-001, M2M1-002

### 模块B: Format Engine

##### M2M1-010: 轻量模板引擎实现

**优先级**: P0 | **工期**: S (2天) | **前置依赖**: 无

### 模块C: Compare Strategy

##### M2M1-020: CompareService实现

**优先级**: P0 | **工期**: M (3-4天) | **前置依赖**: 无

### 模块D: Storage & Export (P1)

##### M2M1-030: Caffeine Store实现

**优先级**: P1 | **工期**: M (3-4天) | **前置依赖**: M2M1-004

##### M2M1-031: JSON/JSONL导出器

**优先级**: P1 | **工期**: M (3-4天) | **前置依赖**: M2M1-004

### 模块E: Spring Integration (M2.1)

##### M2M1-040: Spring Boot Starter封装

**优先级**: P2 | **工期**: M (3-4天) | **前置依赖**: M2M1-030, M2M1-031

##### M2M1-041: Actuator只读端点

**优先级**: P2 | **工期**: S (2天) | **前置依赖**: M2M1-040

##### M2M1-042: 预热与有界缓存

**优先级**: P2 | **工期**: S (2天) | **前置依赖**: M2M1-003

### 模块F: Guardrails & Monitoring

##### M2M1-050: 指标与日志体系

**优先级**: P0 | **工期**: S (2天) | **前置依赖**: 无

### 模块G: Testing & Quality

##### M2M1-060: 测试套件

**优先级**: P1 | **工期**: L (5-8天) | **前置依赖**: 所有P0任务

##### M2M1-061: 性能基线验证

**优先级**: P1 | **工期**: M (3-4天) | **前置依赖**: M2M1-060

### 模块H: Docs & Examples

##### M2M1-070: 文档与示例

**优先级**: P2 | **工期**: M (3-4天) | **前置依赖**: M2M1-040

## 5. 全局质量门槛 (Definition of Done)

### 硬性门槛
- ✅ 关键路径测试覆盖率 ≥ 80%
- ✅ 性能不劣化：相比M0基线，P95延迟增量<20%
- ✅ 指标可观测：最小集指标全部暴露
- ✅ 降级验证：kill-switch和降级路径测试通过
- ✅ 依赖控制：核心模块无新增外部依赖（Caffeine除外）

### 指标最小集

| 指标名 | 说明 | 告警阈值 |
|--------|------|---------|
| tfi.diff.nested.depth.limit | 深度限制触发次数 | >100/min |
| tfi.diff.nested.cycle.skip | 循环剪枝次数 | >50/min |
| tfi.pathmatcher.compile.failure | Pattern编译失败 | >10/min |
| tfi.cache.hit.rate | 缓存命中率 | <60% |
| tfi.collection.degrade.count | 集合降级次数 | >100/min |

## 6. 测试矩阵

| 测试类型 | 覆盖重点 | 工具/框架 | 通过标准 |
|---------|----------|-----------|---------|
| 单元测试 | 核心逻辑 | JUnit 5 | 覆盖率>80% |
| 集成测试 | 模块交互 | Spring Test | 全部通过 |
| 并发测试 | 线程安全 | JCStress | 无竞态条件 |
| 性能测试 | 基线对比 | JMH | P95达标 |
| 长稳测试 | 内存泄漏 | 2h soak | 堆稳定 |

## 7. 可追踪性矩阵 (Traceability)

| PRD需求 | Design章节 | 任务ID | 代码路径 | 测试类 |
|---------|-----------|--------|----------|--------|
| 嵌套扁平化 | 3.1 | M2M1-001 | .tracking.snapshot.ObjectSnapshotDeep | ObjectSnapshotDeepTest |
| 集合摘要 | 3.2 | M2M1-002 | .tracking.summary.CollectionSummary | CollectionSummaryTest |
| 比较策略 | 3.3 | M2M1-020 | .tracking.compare.CompareService | CompareServiceTest |
| 内存存储 | 3.5 | M2M1-030 | .store.InMemoryChangeStore | InMemoryChangeStoreTest |
| 文件导出 | 3.6 | M2M1-031 | .exporter.FileExporter | FileExporterTest |

## 8. 完整示例任务卡

### 示例1: M2M1-002 CollectionSummary实现

**优先级**: P0 | **工期**: M (3-4天) | **Owner**: [待定]

**背景**:
参考设计文档第3.2节 - 集合/Map摘要需求

**目标**:
- ✅ 实现集合差异摘要（新增/删除计数）
- ✅ size-only降级机制（超过max-size自动降级）
- ✅ 示例项提取（Top-N，STRING排序）
- ✅ stableKey生成（支持identity-paths）
- ❌ **非目标**: 元素级深度Diff、NUMERIC/NATURAL排序

**核心实现要点**:
```java
// 包路径：com.syy.taskflowinsight.tracking.summary
public class CollectionSummary {
    public Summary summarize(Object before, Object after, Config cfg) {
        // 1. 计算stableKey集合（使用ValueReprUtil）
        // 2. 差集计算（新增/删除）
        // 3. 示例提取（STRING排序，限制Top-N）
        // 4. size>maxSize时降级为size-only
        // 5. 处理null元素（stableKey="NULL"）
    }
}
```

**[需要澄清]**:
- stableKey生成规则是否需要支持自定义？建议提供SPI扩展点
- 示例项默认Top-3是否合适？是否需要可配置？
- 大集合（>10000）的硬编码阈值是否需要暴露配置？

**安全与合规**:
- 示例项需脱敏处理（password/token等敏感字段）
- 大集合防OOM：超10000直接降级，不进行差集计算
- 使用弱引用避免内存泄漏

**测试要点**:
- 正常差异计算（List/Set/Map各种类型）
- 降级触发（size>100的场景）
- 示例排序正确性（STRING排序）
- null元素处理（确保不抛NPE）
- 性能测试（100项集合<5ms）

**验收标准**:
- [ ] 功能：摘要计算正确，降级机制生效
- [ ] 性能：100项集合处理<5ms
- [ ] 质量：无内存泄漏，单测覆盖>85%
- [ ] 监控：degrade.count指标准确
- [ ] 文档：API文档完整，包含使用示例

**依赖关系**:
- 前置：无
- 后置：M2M1-004 (DiffDetector需要调用)
- 跨模块：ValueReprUtil（🟡弱依赖）

**风险与缓解**:
- 风险：大集合OOM → 缓解：size>10000强制降级
- 风险：排序性能 → 缓解：限制示例数量(maxN=10)
- 风险：stableKey冲突 → 缓解：使用FNV-1a哈希补充

**代码映射**:
- 新增：CollectionSummary, Summary, ExampleSorter
- 调用方：ObjectSnapshotDeep, DiffDetector
- 测试：CollectionSummaryTest, CollectionSummaryPerfTest

### 示例2: M2M1-040 Spring Boot Starter实现

**优先级**: P2 | **工期**: M (3-4天) | **Owner**: [待定]

**背景**:
参考设计文档第3.7节和10.A节 - Spring Boot集成需求

**目标**:
- ✅ AutoConfiguration自动装配
- ✅ @ConfigurationProperties配置映射
- ✅ 条件装配（@ConditionalOnClass/@ConditionalOnProperty）
- ✅ spring-configuration-metadata.json生成
- ❌ **非目标**: 自定义Deep-Merge、复杂Profile管理

**核心实现要点**:
```java
// 包路径：com.syy.taskflowinsight.spring.boot.autoconfigure
@Configuration
@ConditionalOnClass(TFI.class)
@EnableConfigurationProperties(ChangeTrackingProperties.class)
public class TaskFlowInsightAutoConfiguration {
    // Bean定义和装配逻辑
    // 护栏校验（@PostConstruct）
    // 条件装配各个组件
}
```

**[需要澄清]**:
- 是否需要提供spring.factories支持Spring Boot 2.x？
- 默认是否启用（tfi.enabled默认值）？

**安全与合规**:
- 配置敏感信息脱敏（password等不打印到日志）
- Actuator端点默认需要认证

**测试要点**:
- ApplicationContextRunner测试各种配置组合
- 条件装配测试（有/无依赖情况）
- 配置属性绑定测试
- Profile切换测试

**验收标准**:
- [ ] 功能：自动装配正确，配置生效
- [ ] 兼容：支持Spring Boot 3.5.x
- [ ] 质量：测试覆盖>80%
- [ ] 文档：配置说明完整，包含示例

**依赖关系**:
- 前置：M2M1-030, M2M1-031（需要Store和Export功能）
- 后置：M2M1-041（Actuator端点）
- 跨模块：所有核心模块（🔴强依赖）

**风险与缓解**:
- 风险：配置复杂度高 → 缓解：提供balanced预设
- 风险：版本兼容性 → 缓解：明确支持版本范围

**代码映射**:
- 新增：TaskFlowInsightAutoConfiguration
- 新增：ChangeTrackingProperties扩展
- 新增：spring.factories/AutoConfiguration.imports
- 测试：AutoConfigurationTest

## 9. Backlog (M2-m2及更远期)

| 特性 | 优先级 | 预计版本 | 说明 |
|------|--------|---------|------|
| 并行DFS | P2 | M2-m2 | Fork/Join并行遍历，提升大对象图处理性能 |
| 复杂导出并发 | P2 | M2-m2 | StripedLock细粒度锁，支持多线程导出 |
| NUMERIC排序 | P3 | M3 | 数值型示例排序，更智能的示例选择 |
| 自定义Comparator | P3 | M3 | 扩展比较策略，支持业务自定义 |
| Locale比较 | P3 | M3 | 支持国际化字符串比较 |
| 导入校验 | P2 | M2-m2 | 支持导出文件的导入和校验 |

## 10. 假设与开放问题

### 需确认假设
1. 默认balanced配置满足80%场景？需要产品确认
2. 深度限制3层是否合理？需要基于实际业务对象确认
3. Pattern缓存1000条是否足够？需要压测验证

### 待决策项

| 问题 | 默认建议 | 决策Owner |
|------|---------|-----------|
| 循环策略 | 固定cut（剪枝） | 架构师 |
| 时区默认值 | UTC | 产品 |
| 导出路径 | /var/log/tfi | 运维 |
| 缓存策略 | LRU | 架构师 |
| 降级阈值 | size>100 | 产品 |

## 附录A: 工期估算标准

| 级别 | 天数 | 说明 | 示例 |
|------|------|------|------|
| S | <2天 | 简单实现，无复杂逻辑 | 配置项新增、简单工具类 |
| M | 3-4天 | 常规复杂度，需要设计 | 单个组件实现 |
| L | 5-8天 | 需设计评审，复杂逻辑 | 核心模块、复杂算法 |
| XL | >8天 | 建议拆分为多个任务 | 完整特性、端到端功能 |

## 附录B: 风险等级定义

| 等级 | 分值 | 处理策略 | 决策层级 |
|------|------|---------|---------|
| 高 | 7-9 | 必须缓解，需要备选方案 | 架构师 |
| 中 | 4-6 | 持续监控，准备应对措施 | Tech Lead |
| 低 | 1-3 | 接受风险，正常处理 | 开发者 |

风险值 = 概率(1-3) × 影响(1-3)

---

*文档版本*: v1.0.0  
*生成日期*: 2025-01-12  
*状态*: 待评审  
*负责人*: 架构组/研发组