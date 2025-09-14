## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-260-Unit-DiffDetector-Scalar.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/detector/DiffDetector.java#com.syy.taskflowinsight.tracking.detector.DiffDetector.diff(String,Map,Map)
  - src/main/java/com/syy/taskflowinsight/tracking/model/ChangeRecord.java#com.syy.taskflowinsight.tracking.model.ChangeRecord
  - src/main/java/com/syy/taskflowinsight/tracking/ChangeType.java#com.syy.taskflowinsight.tracking.ChangeType
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：为 DiffDetector 标量对比编写覆盖率≥80%的单元测试，覆盖等价类与边界。
- 技术目标：
  - 断言 changeType/valueRepr/valueType/数量与顺序；日期按毫秒时间戳比较。

## 4) SCOPE
- In Scope：
  - [x] null/类型变化/相等/不等 × STRING/NUMBER/BOOLEAN/DATE 用例矩阵。
- Out of Scope：
  - [ ] 集合/Map/复杂对象。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 新增测试类：`src/test/java/com/syy/taskflowinsight/tracking/DiffDetectorTests.java`。
2. 用例：
  - STRING：null→"x"(CREATE) / "x"→null(DELETE) / "a"→"b"(UPDATE) / "a"→"a"(无变化)。
  - NUMBER/BOOLEAN/DATE 同步覆盖；DATE 使用 `new Date(…ms)`。
3. 断言排序：字段名 TreeSet 字典序。

## 6) DELIVERABLES（输出必须包含）
- 测试代码：完整类与断言；必要时构造 before/after Map。
- 文档：卡片回填覆盖率与结论。

## 7) API & MODELS（必须具体化）
- `DiffDetector.diff(String, Map<String,Object>, Map<String,Object>)`。

## 8) DATA & STORAGE
- N/A。

## 9) PERFORMANCE & RELIABILITY
- N/A（单测目标）。

## 10) TEST PLAN（可运行、可断言）
- 单元：
  - [ ] 覆盖率 ≥ 80%；
  - [x] 四类型 × 四关系矩阵；
  - [x] 排序稳定性；DELETE 场景 valueRepr=null。

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：断言通过且覆盖率达标；
- [ ] 文档：结论回填；
- [ ] 观测：无异常日志；
- [ ] 性能：N/A；
- [ ] 风险：无。

## 12) RISKS & MITIGATIONS
- 时间戳浮动：使用固定 Date 值。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 与实现一致：DELETE → valueRepr=null。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否需要 oldRepr 断言？当前仅 newRepr；由导出层覆盖。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-260-unit-diffdetector-scalar.md
