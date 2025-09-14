## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-260-Unit-DiffDetector-Scalar.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/detector/DiffDetector.java#（本卡依赖 202 新增）
- 工程操作规范：../开发工程师提示词.txt

## 3) GOALS（卡片→可执行目标）
- 业务目标：为 DiffDetector 提供覆盖全面的标量对比单元测试（等价类+边界）。
- 技术目标：构建用例矩阵：{null→val, val→null, val→same, val→diff, typeChange} × {STRING,NUMBER,BOOLEAN,DATE}。

## 4) SCOPE
- In Scope：
  - [ ] 新增测试类 DiffDetectorTests，实现上述矩阵与顺序断言
- Out of Scope：
  - [ ] 集合/Map 对比

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 新建测试类：`src/test/java/com/syy/taskflowinsight/tracking/detector/DiffDetectorTests.java`
2. 用例覆盖：字符串、整数/小数、布尔、日期（long 比较）；字典序稳定断言。
3. 自测：`./mvnw -q -DskipTests=false test`

## 6) DELIVERABLES（输出必须包含）
- 测试代码：DiffDetectorTests.java
- 文档：卡片勾选

## 7) API & MODELS（必须具体化）
- 接口：DiffDetector.diff(String, Map, Map)

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 无性能指标；仅功能正确性测试。

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 80%
  - [ ] 完整矩阵覆盖与断言

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：所有等价类断言通过
- [ ] 文档：卡片勾选

## 12) RISKS & MITIGATIONS
- 日期比较误差 → 统一用 getTime() 比较。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否需要性能烟囱用例在本卡提交？（建议：放在 250）

