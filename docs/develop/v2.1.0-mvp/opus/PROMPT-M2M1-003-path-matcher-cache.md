# PROMPT-M2M1-003-PathMatcherCache 开发提示词

## 1) SYSTEM
你是**资深 Java 开发工程师**与**AI 结对编程引导者**。你需要基于下述"上下文与证据"，**按步骤**完成实现并给出**可提交的变更**（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../task/v2.1.0-vip/tracking-core/M2M1-003-PathMatcherCache.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/snapshot#ObjectSnapshotDeep（调用方）
- 相关配置：
  - src/main/resources/application.yml: tfi.change-tracking.path-matcher.pattern-max-length
  - src/main/resources/application.yml: tfi.change-tracking.path-matcher.max-wildcards
  - src/main/resources/application.yml: tfi.change-tracking.path-matcher.cache-size
- 工程操作规范：../../develop/开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：为路径 include/exclude 提供安全、可控、高命中率的匹配与缓存能力
- 技术目标：
  - 实现 FSM 有限状态机匹配器（避免 ReDoS）
  - 支持 *, **, ? 通配符
  - LRU 缓存 + 预热机制
  - 上限控制（长度/通配符数）

## 4) SCOPE
- In Scope（当次实现必做）：
  - [ ] 创建 com.syy.taskflowinsight.tracking.path.PathMatcherCache
  - [ ] 实现 FSM 状态机匹配算法
  - [ ] 实现 LRU 缓存（默认 1000 条）
  - [ ] 实现预热机制
  - [ ] 实现上限检查和降级策略
  - [ ] 暴露 compile.fail、cache.hit/miss 指标
- Out of Scope（排除项）：
  - [ ] 正则表达式匹配（ReDoS 风险）
  - [ ] 复杂表达式语法

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 列出受影响模块与文件：
   - 新建：com.syy.taskflowinsight.tracking.path.PathMatcherCache
   - 新建：com.syy.taskflowinsight.tracking.path.FSMPattern
   - 新建：com.syy.taskflowinsight.tracking.path.PathMatcherConfig

2. 给出重构/新建的**类与方法签名**：
```java
// PathMatcherCache.java
public class PathMatcherCache {
    private final LRUCache<String, FSMPattern> cache;
    
    public boolean matches(String pattern, String path);
    private FSMPattern compile(String pattern);
    private boolean exceedsLimits(String pattern);
    private void fallbackToLiteral(String pattern, String path);
    public void preload(List<String> patterns);
}

// FSMPattern.java
public class FSMPattern {
    private final State[] states;
    
    public boolean matches(String input);
    private State transition(State current, char c);
}

// PathMatcherConfig.java
@Data
public class PathMatcherConfig {
    private int patternMaxLength = 512;
    private int maxWildcards = 32;
    private int cacheSize = 1000;
    private List<String> preloadPatterns = new ArrayList<>();
}
```

## 6) DELIVERABLES（输出必须包含）
- 代码改动：
  - 新文件：PathMatcherCache.java, FSMPattern.java, PathMatcherConfig.java
  - 集成：ObjectSnapshotDeep 调用路径匹配
- 测试：
  - 单测：PathMatcherCacheTest（命中/未命中、上限、降级、预热）
  - FSM 测试：通配符组合边界案例
- 文档：路径匹配语法说明
- 观测：tfi.pathmatcher.compile.fail、tfi.cache.hit/miss

## 7) API & MODELS（必须具体化）
- 接口签名：
```java
public boolean matches(String pattern, String path)
```
- 通配符语义：
  - `*` 匹配单层任意字符
  - `**` 匹配多层路径
  - `?` 匹配单个字符
- 降级：超限时退化为字面量匹配

## 8) DATA & STORAGE
- LRU 缓存：LinkedHashMap 实现
- 缓存键：原始 pattern 字符串
- 缓存值：编译后的 FSM 对象

## 9) PERFORMANCE & RELIABILITY
- 编译性能：< 1ms per pattern
- 匹配性能：< 0.1ms per match
- 缓存命中率目标：> 90%
- 降级策略：超限 → literal 匹配 → 记录指标

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 85%
  - [ ] 通配符测试：*, **, ?, 组合
  - [ ] 上限测试：长度 513、通配符 33
  - [ ] 缓存测试：命中率统计
  - [ ] 预热测试：启动时加载
- 性能测试：
  - [ ] 编译性能基线
  - [ ] 匹配性能基线

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：路径匹配正确
- [ ] 安全：ReDoS 防护有效
- [ ] 性能：匹配 < 0.1ms
- [ ] 观测：指标正确上报

## 12) RISKS & MITIGATIONS
- ReDoS 风险：拒绝正则，使用 FSM → 上限控制 → 降级 literal
- 性能风险：复杂模式编译慢 → LRU 缓存 + 预热
- 内存风险：缓存过大 → 有界 LRU（默认 1000）

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无冲突

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 问题1：预热模式列表从哪里获取？
  - 责任人：架构组
  - 期限：启动前配置
  - 所需：常用路径模式列表