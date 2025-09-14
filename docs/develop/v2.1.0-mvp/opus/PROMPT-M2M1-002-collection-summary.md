# PROMPT-M2M1-002-CollectionSummary 开发提示词

## 1) SYSTEM
你是**资深 Java 开发工程师**与**AI 结对编程引导者**。你需要基于下述"上下文与证据"，**按步骤**完成实现并给出**可提交的变更**（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../task/v2.1.0-vip/tracking-core/M2M1-002-CollectionSummary.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/snapshot#ObjectSnapshotDeep（调用方）
- 相关配置：
  - src/main/resources/application.yml: tfi.change-tracking.summary.enabled
  - src/main/resources/application.yml: tfi.change-tracking.summary.max-size
  - src/main/resources/application.yml: tfi.change-tracking.summary.max-examples
- 工程操作规范：../../develop/开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：以"摘要优先"替代元素级展开，保障性能、稳定性与可观测性
- 技术目标：
  - 实现 CollectionSummary 摘要生成器
  - 支持 Collection/Map/Array 统一处理
  - 提供 size、kind、examples、degraded 信息
  - 集成脱敏关键词检测

## 4) SCOPE
- In Scope（当次实现必做）：
  - [ ] 创建 com.syy.taskflowinsight.tracking.summary.CollectionSummary
  - [ ] 创建 com.syy.taskflowinsight.tracking.summary.Summary 数据模型
  - [ ] 实现 size 统计和 Top-N 示例收集
  - [ ] 实现敏感词脱敏（password/token/secret）
  - [ ] 集成降级标记（size > maxSize）
  - [ ] 暴露 tfi.collection.degrade.count 指标
- Out of Scope（排除项）：
  - [ ] 元素级深度 Diff
  - [ ] 复杂排序策略（仅 STRING 稳定排序）

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 列出受影响模块与文件：
   - 新建：com.syy.taskflowinsight.tracking.summary.CollectionSummary
   - 新建：com.syy.taskflowinsight.tracking.summary.Summary
   - 新建：com.syy.taskflowinsight.tracking.summary.SummaryConfig
   - 修改：ObjectSnapshotDeep（调用 CollectionSummary）

2. 给出重构/新建的**类与方法签名**：
```java
// CollectionSummary.java
public class CollectionSummary {
    public Summary summarize(String path, Object collectionLike, SummaryConfig config);
    private int calculateSize(Object collection);
    private List<String> collectExamples(Object collection, int maxExamples);
    private boolean shouldDegrade(int size, int maxSize);
    private String sanitize(String value);
}

// Summary.java
@Data
public class Summary {
    private int size;
    private String kind; // List/Set/Map/Array
    private List<String> examples;
    private boolean degraded;
}

// SummaryConfig.java
@Data
public class SummaryConfig {
    private boolean enabled = true;
    private int maxSize = 1000;
    private int maxExamples = 10;
    private Set<String> sensitiveKeywords = Set.of("password", "token", "secret");
}
```

## 6) DELIVERABLES（输出必须包含）
- 代码改动：
  - 新文件：CollectionSummary.java, Summary.java, SummaryConfig.java
  - 变更文件：ObjectSnapshotDeep.java（集成摘要处理）
- 测试：
  - 单测：CollectionSummaryTest（size边界、Top-N稳定性、脱敏覆盖）
  - 集成测试：与 ObjectSnapshotDeep 集成
- 文档：README 添加摘要配置说明
- 观测：tfi.collection.degrade.count 计数器

## 7) API & MODELS（必须具体化）
- 接口签名：
```java
public Summary summarize(String path, Object collectionLike, SummaryConfig config)
```
- 数据模型：Summary{size, kind, examples, degraded}
- 错误处理：异常时返回降级摘要

## 8) DATA & STORAGE
- 无持久化，纯内存计算
- 示例收集使用 TreeSet 保证稳定排序

## 9) PERFORMANCE & RELIABILITY
- 性能预算：< 5% CPU 增量
- 处理 10000 元素集合 < 10ms
- 降级策略：size > maxSize 时不收集示例
- 内存控制：最多保留 maxExamples 个示例

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 85%
  - [ ] Size 边界测试（0, 1, maxSize-1, maxSize, maxSize+1）
  - [ ] Top-N 稳定性测试
  - [ ] 脱敏测试（password/token/secret）
  - [ ] 降级路径测试
- 集成测试：
  - [ ] 嵌套集合处理
  - [ ] 性能基线验证

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：摘要信息准确完整
- [ ] 文档：配置项说明清晰
- [ ] 观测：降级指标正确
- [ ] 性能：CPU < 5% 增量
- [ ] 安全：敏感词已脱敏

## 12) RISKS & MITIGATIONS
- 性能风险：大集合 toString 开销 → 限制示例数量
- 安全风险：敏感数据泄漏 → 关键词脱敏
- 兼容性：旧断言依赖元素展开 → 配置开关控制

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无冲突

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 问题1：脱敏关键词默认列表是否需要外部配置文件？
  - 责任人：安全组
  - 期限：实现前确认
  - 所需：敏感词配置规范