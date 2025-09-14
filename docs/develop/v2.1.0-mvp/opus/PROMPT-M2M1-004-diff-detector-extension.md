# PROMPT-M2M1-004-DiffDetector-Extension 开发提示词

## 1) SYSTEM
你是**资深 Java 开发工程师**与**AI 结对编程引导者**。你需要基于下述"上下文与证据"，**按步骤**完成实现并给出**可提交的变更**（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../task/v2.1.0-vip/tracking-core/M2M1-004-DiffDetector-Extension.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/diff#DiffDetector（现有）
  - src/main/java/com/syy/taskflowinsight/tracking/compare#CompareService（前置规范化）
- 相关配置：
  - src/main/resources/application.yml: tfi.change-tracking.diff.output-mode
- 工程操作规范：../../develop/开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：输出稳定、可读、可比较的变更集，便于导出与回归
- 技术目标：
  - 扩展 DiffDetector 输出模型
  - 实现 valueKind/valueRepr 字段
  - 路径字典序稳定排序
  - 支持 compat/enhanced 双模式

## 4) SCOPE
- In Scope（当次实现必做）：
  - [ ] 扩展 com.syy.taskflowinsight.tracking.diff.Change 模型
  - [ ] 实现 valueKind 类型推断
  - [ ] 实现 valueRepr 字符串化规则
  - [ ] 实现路径稳定排序
  - [ ] 支持 compat/enhanced 输出模式
- Out of Scope（排除项）：
  - [ ] 树形复杂变更模型
  - [ ] Diff 内部规范化（由 CompareService 负责）

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 列出受影响模块与文件：
   - 修改：com.syy.taskflowinsight.tracking.diff.Change
   - 修改：com.syy.taskflowinsight.tracking.diff.DiffDetector
   - 新建：com.syy.taskflowinsight.tracking.diff.ValueKind 枚举

2. 给出重构/新建的**类与方法签名**：
```java
// Change.java (扩展)
@Data
public class Change {
    private String path;
    private String kind;      // 新增：valueKind
    private String reprOld;    // 新增：规范化后的旧值
    private String reprNew;    // 新增：规范化后的新值
    private Object rawOld;     // 增强模式：原始值
    private Object rawNew;     // 增强模式：原始值
}

// ValueKind.java
public enum ValueKind {
    NULL, BOOLEAN, NUMBER, STRING, DATE, COLLECTION, MAP, OBJECT
}

// DiffDetector.java (扩展)
public class DiffDetector {
    public List<Change> detect(Map<String, Object> before, Map<String, Object> after, DiffMode mode);
    private ValueKind inferKind(Object value);
    private String toRepr(Object value, ValueKind kind);
    private List<Change> sortByPath(List<Change> changes);
}
```

## 6) DELIVERABLES（输出必须包含）
- 代码改动：
  - 变更：Change.java（新增字段）
  - 变更：DiffDetector.java（排序、字符串化）
  - 新增：ValueKind.java
- 测试：
  - 单测：排序稳定性、字符串化一致性
  - 模式切换测试：compat vs enhanced
- 文档：输出字段规范说明
- 观测：调试计数（可选）

## 7) API & MODELS（必须具体化）
- 数据模型：
```java
Change {
    path: "user.profile.name",
    kind: "STRING",
    reprOld: "Alice",
    reprNew: "Bob",
    rawOld: "Alice",  // enhanced only
    rawNew: "Bob"     // enhanced only
}
```
- 输出模式：
  - compat：最小字段集
  - enhanced：包含 raw 字段

## 8) DATA & STORAGE
- 无持久化需求
- 排序使用 Comparator.comparing(Change::getPath)

## 9) PERFORMANCE & RELIABILITY
- 排序性能：O(n log n)
- 字符串化：缓存常用类型转换
- 多次运行输出必须稳定一致

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 80%
  - [ ] 排序测试：路径字典序
  - [ ] ValueKind 推断测试
  - [ ] Repr 字符串化测试
  - [ ] 模式切换断言
- 回归测试：
  - [ ] 多次运行结果一致性

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：输出稳定有序
- [ ] 兼容：compat 模式不破坏
- [ ] 文档：字段规范清晰

## 12) RISKS & MITIGATIONS
- 兼容性风险：破坏现有消费方 → compat 模式保持旧格式
- 性能风险：大量变更排序慢 → 考虑流式处理

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无冲突

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 问题1：raw 字段是否仅在 JSONL 增强模式输出？
  - 责任人：产品组
  - 期限：实现前确认
  - 所需：输出格式规范