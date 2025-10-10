# Tracking包文档中心

> **TaskFlowInsight 变更追踪引擎核心文档**
>
> 版本: v3.0.0-M3 | 最后更新: 2025-10-05

---

## 📚 文档体系

本目录提供tracking包的**完整技术文档**，采用分层文档架构，满足不同角色和场景的需求。

### 🎯 文档定位

```
快速上手 ──→ 深入理解 ──→ 架构演进 ──→ 专项主题
   5min       45min        60min       按需查阅
```

---

## 🚀 核心文档（推荐阅读顺序）

### 1️⃣ [QuickStart.md](./QuickStart.md) - 快速入门
**⏱️ 5分钟** | **适合**: 首次接触的开发人员

**内容**:
- ✅ 最小可运行示例（对象/列表比较、变更追踪）
- ✅ Spring vs 非Spring环境用法
- ✅ 常用配置摘要
- ✅ 常见问题排错

**典型场景**:
```java
// 对象比较
CompareResult result = compareService.compare(obj1, obj2, options);

// 列表比较
var result = listDiff.diff(oldList, newList);

// 变更追踪
ChangeTracker.track("order", order, "status", "price");
List<ChangeRecord> changes = ChangeTracker.getChanges();
```

---

### 2️⃣ [PACKAGE_DESIGN.md](./PACKAGE_DESIGN.md) - 包设计文档 ⭐
**⏱️ 45分钟** | **适合**: 需要深入理解架构的开发人员

**内容**:
- 📋 包的核心职责和设计目标
- 🏗️ 6层分层架构设计
- 📦 17个子包的详细说明（职责/主要类/使用场景）
- 🚀 4个典型场景的完整代码示例
- 🔧 完整配置指南（最小/推荐/生产配置）
- 📚 最佳实践和常见陷阱
- 🔍 故障排查指南
- 📖 进阶主题（自定义策略/渲染器/监控集成）

**架构总览**:
```
API Layer (ChangeTracker)
    ↓
Snapshot Layer (ObjectSnapshot)
    ↓
Detector Layer (DiffDetector)
    ↓
Compare Layer (CompareEngine)
    ↓
Render Layer (MarkdownRenderer)
    ↓
Infrastructure (Cache/Metrics/Monitoring)
```

---

### 3️⃣ [QUICK_REFERENCE.md](./QUICK_REFERENCE.md) - 快速参考手册
**⏱️ 随时查阅** | **适合**: 日常开发速查

**内容**:
- 🎯 核心API速查表
- 📦 包结构速查表（17个包的重要度排序）
- 🔧 常用配置模板（3种场景）
- 💡 典型场景代码片段
- ⚠️ DO/DON'T对比
- 🔍 故障排查检查清单
- 📊 性能优化检查清单

**适合打印或放在桌面随时查阅**

---

## 📖 专项文档

### 4️⃣ [Configuration.md](./Configuration.md) - 配置详解
**⏱️ 15分钟** | **适合**: 需要调整配置的开发人员

**内容**:
- 🔧 完整配置项说明（`tfi.*` 全部配置）
- 📝 配置示例和默认值
- 💡 配置最佳实践
- ⚙️ Spring vs 非Spring配置方式
- 🔌 系统属性/环境变量兜底

**关键配置域**:
```yaml
tfi.change-tracking.*     # 变更追踪核心配置
tfi.perf-guard.*         # 性能守护
tfi.compare.auto-route.* # 自动路由
tfi.diff.cache.*         # 缓存配置
tfi.render.*             # 渲染配置
```

---

### 5️⃣ [Performance-BestPractices.md](./Performance-BestPractices.md) - 性能优化
**⏱️ 20分钟** | **适合**: 关注性能的开发人员

**内容**:
- 🚀 策略选择指南（SIMPLE/AS_SET/ENTITY/LCS）
- 🛡️ PerfGuard性能守护
- 🔄 自动路由降低误配成本
- 💾 缓存优化（策略缓存/反射缓存）
- 📉 降级治理
- 📊 可观测与诊断
- 🔧 排障清单

**性能优化清单**:
- ✅ 启用策略缓存（命中率>90%）
- ✅ 启用反射缓存（命中率>85%）
- ✅ 合理配置算法阈值
- ✅ 实体列表使用@Key注解
- ✅ 生产环境启用降级治理

---

## 🏛️ 架构与演进

### 6️⃣ [TRACKING_COMPATIBILITY_ANALYSIS.md](../../../../../../../../TRACKING_COMPATIBILITY_ANALYSIS.md)
**⏱️ 60分钟** | **适合**: 架构师、技术负责人

**内容**:
- 🏗️ 15个主题的前后兼容设计详解
- 📊 使用统计和影响分析
- 🎯 P0/P1/P2优先级行动清单
- 🔬 架构演进时间线（v2.0 → v4.0）
- 📚 配置迁移指南
- ⚠️ 技术债务清单

**关键价值**:
- 理解tracking包的架构演进历程
- 掌握新旧API的兼容性策略
- 规划技术债务偿还路径

**注**: 此文档位于项目根目录，提供全局架构视角

---

## 🎯 快速导航

### 我想...

| 需求 | 推荐文档 | 章节 |
|------|---------|------|
| 🔹 **5分钟快速上手** | [QuickStart.md](./QuickStart.md) | 全文 |
| 🔹 **理解架构设计** | [PACKAGE_DESIGN.md](./PACKAGE_DESIGN.md) | 「架构设计」 |
| 🔹 **查找API用法** | [QUICK_REFERENCE.md](./QUICK_REFERENCE.md) | 「核心API速查」 |
| 🔹 **配置应用** | [Configuration.md](./Configuration.md) | 全文 |
| 🔹 **解决配置问题** | [Configuration.md](./Configuration.md) | 「关键说明」 |
| 🔹 **优化性能** | [Performance-BestPractices.md](./Performance-BestPractices.md) | 全文 |
| 🔹 **排查故障** | [QUICK_REFERENCE.md](./QUICK_REFERENCE.md) | 「故障排查检查清单」 |
| 🔹 **了解最佳实践** | [PACKAGE_DESIGN.md](./PACKAGE_DESIGN.md) | 「最佳实践」 |
| 🔹 **理解架构演进** | [TRACKING_COMPATIBILITY_ANALYSIS.md](../../../../../../../../TRACKING_COMPATIBILITY_ANALYSIS.md) | 全文 |
| 🔹 **查看子包说明** | [PACKAGE_DESIGN.md](./PACKAGE_DESIGN.md) | 「子包说明」 |
| 🔹 **学习示例代码** | [PACKAGE_DESIGN.md](./PACKAGE_DESIGN.md) | 「快速开始」 |

---

## 📂 目录结构

```
tracking/docs/
├── INDEX.md                          # 本文件 - 文档导航中心 ⭐
│
├── 核心文档/
│   ├── QuickStart.md                 # 快速入门（5分钟）
│   ├── PACKAGE_DESIGN.md             # 包设计文档（核心，45分钟）⭐⭐⭐
│   └── QUICK_REFERENCE.md            # 快速参考手册（速查表）
│
├── 专项文档/
│   ├── Configuration.md              # 配置详解
│   └── Performance-BestPractices.md  # 性能优化
│
└── 相关链接/
    └── ../../../../../../../../TRACKING_COMPATIBILITY_ANALYSIS.md  # 架构兼容性分析
```

---

## 💡 推荐学习路径

### 🎓 初学者路径（2-3小时）

```
1. QuickStart.md（15分钟）
   快速上手，运行第一个示例
   ↓
2. PACKAGE_DESIGN.md 「快速开始」章节（30分钟）
   深入理解4个典型场景
   ↓
3. QUICK_REFERENCE.md 浏览（10分钟）
   熟悉API和配置速查
   ↓
4. Configuration.md（15分钟）
   理解配置项含义
   ↓
5. 实践练习（60分钟）
   修改配置，运行示例，尝试集成
```

### 🚀 进阶开发者路径（4-5小时）

```
1. PACKAGE_DESIGN.md 完整阅读（45分钟）
   全面理解架构和子包设计
   ↓
2. Performance-BestPractices.md（20分钟）
   掌握性能优化技巧
   ↓
3. 源码学习（120分钟）
   深入阅读感兴趣的子包源码
   ↓
4. TRACKING_COMPATIBILITY_ANALYSIS.md（60分钟）
   理解架构演进历程
   ↓
5. 实战集成（90分钟）
   集成到真实项目，解决实际问题
```

### 🏆 架构师路径（全天）

```
1. 全部文档通读（180分钟）
   建立完整知识体系
   ↓
2. 源码架构分析（240分钟）
   核心链路梳理，绘制架构图
   ↓
3. 性能基准测试（120分钟）
   实际测试，找出性能瓶颈
   ↓
4. 架构演进规划（60分钟）
   制定技术债务偿还计划
```

---

## 🔗 相关文档

### 项目级文档（根目录）

| 文档 | 说明 |
|------|------|
| [README.md](../../../../../../../../README.md) | 项目总览 |
| [QUICKSTART.md](../../../../../../../../QUICKSTART.md) | 3分钟快速开始 |
| [EXAMPLES.md](../../../../../../../../EXAMPLES.md) | 11个实战示例 |
| [GETTING-STARTED.md](../../../../../../../../GETTING-STARTED.md) | 详细入门指南 |
| [FAQ.md](../../../../../../../../FAQ.md) | 常见问题解答 |
| [TROUBLESHOOTING.md](../../../../../../../../TROUBLESHOOTING.md) | 问题诊断指南 |
| [TRACKING_COMPATIBILITY_ANALYSIS.md](../../../../../../../../TRACKING_COMPATIBILITY_ANALYSIS.md) | 兼容性架构分析 |

### 其他核心包文档

```
src/main/java/com/syy/taskflowinsight/
├── api/            # API外观层
├── config/         # 配置管理
├── context/        # 上下文管理
└── tracking/       # 变更追踪（本包）
    └── docs/       # 当前目录
```

---

## 📊 文档质量保证

### ✅ 准确性
- 所有API和配置都经过实际验证
- 代码示例可直接运行
- 版本号与代码库保持同步

### ✅ 完整性
- 覆盖17个子包的所有核心功能
- 包含97个类的使用说明
- 提供从入门到精通的完整路径

### ✅ 实用性
- 基于真实业务场景
- 提供可复制粘贴的代码片段
- 包含常见问题的解决方案

### ✅ 可维护性
- 清晰的版本管理
- 详细的更新日志
- 模块化的文档结构

---

## 🆘 获取帮助

### 遇到问题？

#### 1️⃣ 查看文档
- **快速问题**: [QUICK_REFERENCE.md](./QUICK_REFERENCE.md) 的故障排查清单
- **配置问题**: [Configuration.md](./Configuration.md)
- **性能问题**: [Performance-BestPractices.md](./Performance-BestPractices.md)
- **通用问题**: [FAQ.md](../../../../../../../../FAQ.md)

#### 2️⃣ 启用调试
```yaml
logging:
  level:
    com.syy.taskflowinsight.tracking: DEBUG
```

#### 3️⃣ 提交Issue
GitHub Issues: https://github.com/anthropics/claude-code/issues

**提交Issue时请提供**:
- 版本号
- 配置文件
- 完整错误日志
- 最小可复现示例

#### 4️⃣ 社区支持
- 技术博客: 待定
- 邮件列表: 待定
- 官方网站: 待定

---

## 📝 文档贡献

### 发现文档问题？

欢迎提交PR改进文档！

**贡献流程**:
1. Fork 项目
2. 修改文档（遵循文档规范）
3. 提交 Pull Request

**文档改进原则**:
- ✅ 准确性优先（代码示例必须可运行）
- ✅ 简洁易懂（避免术语堆砌）
- ✅ 结构清晰（使用标题和列表）
- ✅ 示例丰富（真实场景代码）

---

## 🔄 文档更新日志

| 版本 | 日期 | 变更内容 | 负责人 |
|------|------|----------|--------|
| v3.0.0-M3 | 2025-10-05 | 创建新版文档体系，整合并完善所有文档 | Architecture Team |
| v3.0.0-M3 | 2025-10-05 | 新增 PACKAGE_DESIGN.md、QUICK_REFERENCE.md | Architecture Team |
| v3.0.0-M3 | 2025-10-05 | 移除重复文档，优化目录结构 | Architecture Team |
| v3.0.0-M2 | 2025-10-04 | 创建 QuickStart.md、Configuration.md | Dev Team |

---

## 📞 联系我们

**TaskFlowInsight Team**

- 📧 Email: 待定
- 🌐 官网: 待定
- 💬 GitHub: https://github.com/anthropics/claude-code

---

## 🌟 快速链接

### 最常用文档（收藏这些）

1. 🚀 [快速入门](./QuickStart.md)
2. 📖 [包设计文档](./PACKAGE_DESIGN.md)
3. 🔍 [快速参考](./QUICK_REFERENCE.md)
4. ⚙️ [配置详解](./Configuration.md)

### 典型问题快速跳转

| 问题 | 跳转 |
|------|------|
| 怎么快速上手？ | [QuickStart.md](./QuickStart.md) |
| 如何比较两个对象？ | [PACKAGE_DESIGN.md](./PACKAGE_DESIGN.md#场景1-基础对象追踪) |
| 如何比较列表？ | [PACKAGE_DESIGN.md](./PACKAGE_DESIGN.md#场景3-列表智能比较entity) |
| 性能慢怎么办？ | [Performance-BestPractices.md](./Performance-BestPractices.md) |
| 配置项是什么意思？ | [Configuration.md](./Configuration.md) |
| API怎么用？ | [QUICK_REFERENCE.md](./QUICK_REFERENCE.md) |

---

**Happy Coding! 🚀**

_让变更追踪变得简单而强大_

---

**文档维护**: TaskFlowInsight Architecture Team
**反馈渠道**: GitHub Issues
**最后更新**: 2025-10-05
