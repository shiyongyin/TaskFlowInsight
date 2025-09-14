# TaskFlow Insight v2.1.1 MVP 任务文档

## 📋 概述

本目录包含TaskFlow Insight M2阶段（v2.0.0-M2-m1）的开发任务拆分文档，用于指导高级工程师进行模块开发和任务卡创建。

## 📁 文档结构

```
v2.1.1-mvp/
├── README.md                                   # 本文档
├── TaskFlow-Insight-M2-Development-Tasks.md   # 主任务总览文档
└── tasks/                                      # 详细任务卡目录（待创建）
    ├── M2M1-001-ObjectSnapshotDeep.md
    ├── M2M1-002-CollectionSummary.md
    └── ...
```

## 🎯 核心内容

### 任务范围
- **M1 P0功能**：嵌套扁平化、集合摘要、比较策略、格式引擎（7个任务）
- **M1 P1功能**：内存存储、文件导出（4个任务）
- **M2.1产品化**：Spring Boot Starter、Actuator端点（4个任务）

### 任务分布
- 总任务数：15个
- P0优先级：7个（必须完成）
- P1优先级：4个（应该完成）
- P2优先级：4个（可以延后）

## 📅 时间规划

| 阶段 | 时间 | 内容 | 关键交付物 |
|------|------|------|-----------|
| Phase 1 | Week 1-2 | M1 P0核心功能 | ObjectSnapshotDeep, CollectionSummary, CompareService |
| Phase 2 | Week 3-4 | M1 P1存储导出 | CaffeineStore, JsonExporter |
| Phase 3 | Week 5-6 | M2.1产品化 | Spring Boot Starter, Actuator端点 |
| Phase 4 | Week 7-8 | 测试与发布 | 测试套件, 性能验证, 文档 |

## 🔧 使用指南

### 1. 任务认领
1. 查看主文档中的任务列表
2. 选择合适的任务（注意依赖关系）
3. 在任务跟踪系统（Jira/Azure DevOps）中创建对应任务卡

### 2. 开发流程
1. 仔细阅读任务卡的背景和目标
2. 注意"需要澄清"部分，及时沟通
3. 遵循"非目标"约束，避免过度设计
4. 按照验收标准进行自测

### 3. 质量保证
- 单元测试覆盖率 > 80%
- 性能测试达到指定基线
- 代码评审通过
- 文档更新完成

## ⚠️ 重要约束

### YAGNI原则
- 只实现当前需求，不做过度设计
- 本阶段不实现：并行DFS、复杂导出并发、Locale比较等

### 集合策略（硬约束）
- 一律摘要化（size-only + 示例STRING排序）
- 不展开元素级深度Diff

### 性能基线
- 2字段场景：P95 ≤ 0.5ms
- 深度2嵌套：P95 ≤ 2ms  
- 集合100项：P95 ≤ 5ms

## 📊 风险管理

### 已识别的主要风险
1. **R001**: DFS遍历性能瓶颈 → 通过白名单控制范围
2. **R002**: Java模块系统反射限制 → trySetAccessible降级
3. **R003**: 配置复杂度 → 提供balanced预设

### 待决策事项
- 深度限制默认值（建议3层）
- Pattern缓存大小（建议1000）
- 时区默认值（UTC vs 系统默认）

## 🔗 相关文档

- 设计文档：`docs/specs/m2/opus4.1/v2/TaskFlow-Insight-M2-m1阶段-vip-Design.md`
- 配置说明：`docs/configuration/README.md`
- API文档：`docs/api/README.md`

## 👥 联系方式

- 架构组：[架构师邮箱]
- 研发组：[Tech Lead邮箱]
- 产品组：[产品经理邮箱]

---

*最后更新*: 2025-01-12  
*版本*: v2.1.1-mvp  
*状态*: 进行中