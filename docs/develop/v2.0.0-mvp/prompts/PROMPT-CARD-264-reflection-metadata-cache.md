## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-264-Reflection-Metadata-Cache.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshot.java#（本卡验证 201 的缓存实现）
- 工程操作规范：../开发工程师提示词.txt

## 3) GOALS（卡片→可执行目标）
- 业务目标：验证反射元数据缓存的命中与容量上限行为。
- 技术目标：编写测试测量首轮 vs 后续访问耗时差、命中计数（可用简单计数器）、上限后不扩容或淘汰策略生效。

## 4) SCOPE
- In Scope：
  - [ ] 新增测试类 ReflectionMetaCacheTests
- Out of Scope：
  - [ ] 引入第三方缓存（如 Caffeine）

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 新建测试类 ReflectionMetaCacheTests。
2. 用例：
   - 同类字段访问多次，统计时间差与命中数；
   - 构造 >MAX_CLASSES 的不同类模拟达到上限；验证后续行为；
3. 自测：`./mvnw test`。

## 6) DELIVERABLES（输出必须包含）
- 测试代码：ReflectionMetaCacheTests.java。
- 文档：卡片勾选。

## 7) API & MODELS（必须具体化）
- API：ObjectSnapshot.capture（访问缓存）。

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 命中率在热路径 ≥ 80%（测试报告中体现）。

## 10) TEST PLAN（可运行、可断言）
- 单元/集成测试：
  - [ ] 命中率显著提升；
  - [ ] 上限后行为符合预期（不扩容/不抛错）。

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：缓存有效且受控
- [ ] 文档：卡片勾选

## 12) RISKS & MITIGATIONS
- 测量噪声 → 重复次数与阈值放宽，关注量级变化。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 上限默认值确认（建议 1024）。

