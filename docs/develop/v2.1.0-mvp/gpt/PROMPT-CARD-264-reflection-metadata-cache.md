## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-264-Reflection-Metadata-Cache.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshot.java#FIELD_CACHE(MAX_CLASSES), buildFieldMap, getFieldMap
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：验证反射元数据缓存的命中/上限/并发正确性；不引入第三方缓存（M0）。
- 技术目标：
  - ConcurrentHashMap + 上限（MAX_CLASSES=1024）；computeIfAbsent 单点构建；超限临时构建 + WARN。

## 4) SCOPE
- In Scope：
  - [ ] 单测/微基准：首轮构建 vs 后续命中差异；填满上限后的行为；并发访问正确性。
- Out of Scope：
  - [ ] 引入 Caffeine 等第三方依赖。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 若必要，补齐 WARN 日志与 MAX_CLASSES 常量注释。
2. 测试：构造 1000+ 不同类（可用动态类或简单 POJO），观察超限后不继续缓存。
3. 并发：多线程并发 capture 同一类字段，断言不会重复 setAccessible/构建。

## 6) DELIVERABLES（输出必须包含）
- 测试：`src/test/java/com/syy/taskflowinsight/tracking/ObjectSnapshotCacheTests.java`（建议路径）。
- 文档：卡片回填；README 摘要说明缓存策略。

## 7) API & MODELS（必须具体化）
- 常量/字段：`ObjectSnapshot.MAX_CLASSES`、`ObjectSnapshot.FIELD_CACHE`。

## 8) DATA & STORAGE
- N/A。

## 9) PERFORMANCE & RELIABILITY
- 可靠性：超限后临时构建避免 OOM；并发下 computeIfAbsent 单点构建。

## 10) TEST PLAN（可运行、可断言）
- 单元：
  - [ ] 首轮 vs 后续 capture 时间对比（数量级差异即可）；
  - [ ] 超过 1024 类后缓存大小不再增长；
  - [ ] 并发下无异常、无重复 setAccessible 过多。

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：缓存策略与上限验证通过；
- [ ] 文档：卡片/README 更新；
- [ ] 观测：WARN 日志出现于超限后首个超限类；
- [ ] 性能：命中率带来可观收益（相对基线）。

## 12) RISKS & MITIGATIONS
- 伪基准：JIT 影响测试 → 预热后测量；避免微不足道的断言。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 与实现一致：不引第三方缓存；仅验证。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否需要指标计数（compile.fail/hit/miss）？M0 可先不加，仅日志。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-264-reflection-metadata-cache.md

