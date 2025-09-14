# 📂 M2阶段任务文档生成清单

## 概述
本清单用于跟踪M2阶段（v2.0.0-M2-m1）所有任务卡文档的生成状态。共计15个详细任务卡需要生成。

## ✅ 已完成文档
- [x] `/docs/task/v2.1.1-mvp/README.md` - 任务总览说明
- [x] `/docs/task/v2.1.1-mvp/TaskFlow-Insight-M2-Development-Tasks.md` - 主任务文档
- [x] `/docs/task/v2.1.1-mvp/task-generation-checklist.md` - 任务生成清单

## 📝 任务卡生成状态

### A. 核心追踪模块 (Tracking Core) - 4个 [P0] ✅
- [x] `/docs/task/v2.1.1-mvp/tasks/M2M1-001-ObjectSnapshotDeep.md`
  - **任务名称**: SnapshotFacade与ObjectSnapshotDeep实现
  - **优先级**: P0
  - **工期**: L (5-8天)
  - **描述**: DFS遍历与嵌套对象扁平化，支持深度限制和循环检测

- [x] `/docs/task/v2.1.1-mvp/tasks/M2M1-002-CollectionSummary.md`
  - **任务名称**: CollectionSummary实现
  - **优先级**: P0
  - **工期**: M (3-4天)
  - **描述**: 集合/Map差异摘要，支持size-only降级和示例提取

- [x] `/docs/task/v2.1.1-mvp/tasks/M2M1-003-PathMatcherCache.md`
  - **任务名称**: PathMatcherCache实现
  - **优先级**: P0
  - **工期**: M (3-4天)
  - **描述**: Ant风格路径匹配器，LRU缓存，ReDoS防护

- [x] `/docs/task/v2.1.1-mvp/tasks/M2M1-004-DiffDetector-Extension.md`
  - **任务名称**: DiffDetector扩展
  - **优先级**: P0
  - **工期**: M (3-4天)
  - **描述**: 支持valueKind/valueRepr，字典序稳定输出

### B. 格式化引擎 (Format Engine) - 1个 [P0] ✅
- [x] `/docs/task/v2.1.1-mvp/tasks/M2M1-010-TemplateEngine.md`
  - **任务名称**: 轻量模板引擎实现
  - **优先级**: P0
  - **工期**: S (2天)
  - **描述**: #[...]占位符替换，模板选择优先级

### C. 比较策略 (Compare Strategy) - 1个 [P0] ✅
- [x] `/docs/task/v2.1.1-mvp/tasks/M2M1-020-CompareService.md`
  - **任务名称**: CompareService实现
  - **优先级**: P0
  - **工期**: M (3-4天)
  - **描述**: 绝对容差、时间归一化、字符串规范化

### D. 存储与导出 (Storage & Export) - 2个 [P1] ✅
- [x] `/docs/task/v2.1.1-mvp/tasks/M2M1-030-CaffeineStore.md`
  - **任务名称**: Caffeine Store实现
  - **优先级**: P1
  - **工期**: M (3-4天)
  - **描述**: 内存存储，TTL管理，查询接口

- [x] `/docs/task/v2.1.1-mvp/tasks/M2M1-031-JsonExporter.md`
  - **任务名称**: JSON/JSONL导出器
  - **优先级**: P1
  - **工期**: M (3-4天)
  - **描述**: 流式写入，元数据支持，原子文件操作

### E. Spring集成 (Spring Integration) - 3个 [P2] ✅
- [x] `/docs/task/v2.1.1-mvp/tasks/M2M1-040-SpringBootStarter.md`
  - **任务名称**: Spring Boot Starter封装
  - **优先级**: P2
  - **工期**: M (3-4天)
  - **描述**: AutoConfiguration，条件装配，配置元数据

- [x] `/docs/task/v2.1.1-mvp/tasks/M2M1-041-ActuatorEndpoint.md`
  - **任务名称**: Actuator只读端点
  - **优先级**: P2
  - **工期**: S (2天)
  - **描述**: /actuator/tfi/effective-config端点实现

- [x] `/docs/task/v2.1.1-mvp/tasks/M2M1-042-WarmupCache.md`
  - **任务名称**: 预热与有界缓存
  - **优先级**: P2
  - **工期**: S (2天)
  - **描述**: PathMatcher预热，LRU缓存管理

### F. 监控与护栏 (Guardrails & Monitoring) - 1个 [P0] ✅
- [x] `/docs/task/v2.1.1-mvp/tasks/M2M1-050-MetricsLogging.md`
  - **任务名称**: 指标与日志体系
  - **优先级**: P0
  - **工期**: S (2天)
  - **描述**: Micrometer指标，SLF4J日志，错误码体系

### G. 测试与质量 (Testing & Quality) - 2个 [P1] ✅
- [x] `/docs/task/v2.1.1-mvp/tasks/M2M1-060-TestSuite.md`
  - **任务名称**: 测试套件
  - **优先级**: P1
  - **工期**: L (5-8天)
  - **描述**: 单元测试、集成测试、并发测试框架

- [x] `/docs/task/v2.1.1-mvp/tasks/M2M1-061-PerformanceBaseline.md`
  - **任务名称**: 性能基线验证
  - **优先级**: P1
  - **工期**: M (3-4天)
  - **描述**: JMH基准测试，2小时稳定性测试

### H. 文档与示例 (Docs & Examples) - 1个 [P2] ✅
- [x] `/docs/task/v2.1.1-mvp/tasks/M2M1-070-Documentation.md`
  - **任务名称**: 文档与示例
  - **优先级**: P2
  - **工期**: M (3-4天)
  - **描述**: 配置说明、最佳实践、迁移指南

## 📊 统计汇总

### 按优先级分布
| 优先级 | 数量 | 任务ID范围 | 总工期 |
|--------|------|------------|--------|
| P0 | 7个 | 001-004, 010, 020, 050 | 约20-26天 |
| P1 | 4个 | 030-031, 060-061 | 约14-19天 |
| P2 | 4个 | 040-042, 070 | 约10-12天 |
| **总计** | **15个** | - | **约44-57天** |

### 按模块分布
| 模块 | 任务数 | 优先级分布 |
|------|--------|-----------|
| Tracking Core | 4 | P0×4 |
| Format Engine | 1 | P0×1 |
| Compare Strategy | 1 | P0×1 |
| Storage & Export | 2 | P1×2 |
| Spring Integration | 3 | P2×3 |
| Guardrails & Monitoring | 1 | P0×1 |
| Testing & Quality | 2 | P1×2 |
| Docs & Examples | 1 | P2×1 |

### 按工期分布
| 工期级别 | 天数 | 任务数量 | 任务ID |
|---------|------|---------|--------|
| S (Small) | <2天 | 4个 | 010, 041, 042, 050 |
| M (Medium) | 3-4天 | 8个 | 002, 003, 004, 020, 030, 031, 040, 070 |
| L (Large) | 5-8天 | 3个 | 001, 060, 061 |
| XL (Extra Large) | >8天 | 0个 | - |

## 🎯 生成策略

### 建议生成顺序
1. **第一批 (P0关键路径)**：001, 003 → 002, 004 → 010, 020, 050
2. **第二批 (P1重要功能)**：030, 031 → 060, 061
3. **第三批 (P2产品化)**：040 → 041, 042 → 070

### 依赖关系
```
001(ObjectSnapshotDeep) ← 003(PathMatcherCache)
     ↓
002(CollectionSummary) → 004(DiffDetector)
                            ↓
                        030(CaffeineStore)
                            ↓
                        031(JsonExporter)
                            ↓
                        040(SpringStarter)
                            ↓
                        041(Actuator) → 042(Warmup)
```

## ⏰ 时间线
- **Week 1-2**: 完成所有P0任务（7个）
- **Week 3-4**: 完成所有P1任务（4个）
- **Week 5-6**: 完成所有P2任务（4个）
- **Week 7-8**: 集成测试与发布准备

## 📝 备注
- 所有任务卡需包含：背景、目标、非目标、实现要点、测试要求、验收标准、风险评估
- 每个任务卡建议包含代码示例和关键API设计
- 需要澄清的问题应在任务卡中明确标注

---

*生成日期*: 2025-01-12  
*文档版本*: v1.0.0  
*状态*: ✅ 全部生成完成