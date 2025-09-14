# tracking-core - M2M1-003-PathMatcherCache（VIP 终版）

## 目标与范围

### 业务目标
为路径 include/exclude 提供安全、可控、高命中率的匹配与缓存能力。

### 技术目标
- 避免 ReDoS 攻击风险
- 提供高效的模式匹配
- 支持常见通配符（*, **, ?）

### Out of Scope
- 完整正则表达式支持
- 复杂表达式语法
- FSM 编译器的完整实现（Phase 3）

## 核心设计决策

### 分阶段实现策略
**Phase 2: 受限正则（立即可用）**
```java
public class PathMatcherCache {
    private static final int MAX_PATTERN_LENGTH = 512;
    private static final int MAX_WILDCARDS = 32;
    private final LRUCache<String, Pattern> cache;
    
    public boolean matches(String pattern, String path) {
        // 防护检查
        if (!isPatternSafe(pattern)) {
            return literalMatch(pattern, path);
        }
        
        Pattern compiled = cache.computeIfAbsent(pattern, this::compileToRegex);
        return timedMatch(compiled, path, 100); // 100ms超时
    }
    
    private Pattern compileToRegex(String pattern) {
        // 简单通配符转正则（避免回溯）
        String regex = pattern
            .replace(".", "\\.")
            .replace("?", "[^/]")      // 单字符
            .replace("**", "§")         // 临时占位
            .replace("*", "[^/]*")      // 路径内通配
            .replace("§", ".*");        // 跨路径通配
        return Pattern.compile("^" + regex + "$");
    }
}
```

**Phase 3: FSM 优化（未来）**
```java
// 未来优化：编译成有限状态机
class FSMPattern {
    private final State[] states;
    private final int[][] transitions;
    
    public boolean matches(String path) {
        // O(n) 时间复杂度，无回溯
    }
}
```

## 实现要求

### Phase 1 - 低风险改动（本周）
1. **配置属性化**
   ```yaml
   tfi:
     pathmatcher:
       cache:
         enabled: true
         max-size: 1000            # LRU缓存大小
         pattern-max-length: 512   # 模式最大长度
         max-wildcards: 32         # 通配符数量限制
         match-timeout-ms: 100     # 匹配超时时间
         preload-patterns:         # 预热模式
           - "user.*"
           - "*.password"
           - "config.**"
   ```

2. **创建基础框架**
   - LRU 缓存实现
   - 安全检查逻辑
   - 降级机制

### Phase 2 - 核心功能（下周）
1. **安全的模式匹配**
   ```java
   private boolean isPatternSafe(String pattern) {
       if (pattern.length() > MAX_PATTERN_LENGTH) {
           metrics.increment("pathmatcher.pattern.toolong");
           return false;
       }
       
       int wildcards = countWildcards(pattern);
       if (wildcards > MAX_WILDCARDS) {
           metrics.increment("pathmatcher.pattern.complex");
           return false;
       }
       
       // 检查危险模式（易导致回溯）
       if (pattern.contains("***") || pattern.matches(".*\\*\\+.*")) {
           metrics.increment("pathmatcher.pattern.dangerous");
           return false;
       }
       
       return true;
   }
   ```

2. **超时保护**
   ```java
   private boolean timedMatch(Pattern pattern, String path, long timeoutMs) {
       Future<Boolean> future = executor.submit(() -> 
           pattern.matcher(path).matches()
       );
       
       try {
           return future.get(timeoutMs, TimeUnit.MILLISECONDS);
       } catch (TimeoutException e) {
           future.cancel(true);
           metrics.increment("pathmatcher.match.timeout");
           return false; // 超时默认不匹配
       }
   }
   ```

3. **缓存预热**
   ```java
   @PostConstruct
   public void preloadPatterns() {
       for (String pattern : config.getPreloadPatterns()) {
           try {
               Pattern compiled = compileToRegex(pattern);
               cache.put(pattern, compiled);
               logger.info("Preloaded pattern: {}", pattern);
           } catch (Exception e) {
               logger.warn("Failed to preload pattern: {}", pattern);
           }
       }
   }
   ```

### Phase 3 - 优化增强（后续）
1. FSM 编译器实现
2. 更复杂的模式语法
3. 性能优化

## 性能指标

### 缓存命中率
- 目标：> 90%
- 预热后：> 95%

### 匹配性能
| 场景 | P50 | P99 | 超时率 |
|------|-----|-----|--------|
| 缓存命中 | <10μs | <100μs | 0% |
| 缓存未命中 | <1ms | <10ms | <0.1% |
| 复杂模式 | <10ms | <100ms | <1% |

## 冲突解决方案

### 核心冲突点及决策

1. **FSM vs 正则表达式**
   - 冲突：FSM 实现复杂，正则有 ReDoS 风险
   - 决策：Phase 2 用受限正则，Phase 3 考虑 FSM
   - 实施：严格限制模式复杂度 + 超时保护

2. **缓存容量管理**
   - 冲突：缓存过大占内存，过小命中率低
   - 决策：LRU + 可配置大小 + 预热
   - 实施：默认 1000，监控命中率调整

3. **降级策略**
   - 冲突：降级到字面匹配可能影响功能
   - 决策：分级降级（复杂度降级 > 超时降级 > 异常降级）
   - 实施：记录降级原因和次数

## 测试计划

### 功能测试
- 基本通配符匹配（*, **, ?）
- 边界案例（空模式、超长路径）
- 降级路径验证

### 安全测试
- ReDoS 攻击防护
- 超时机制验证
- 资源限制测试

### 性能测试
- 缓存命中率
- 匹配延迟分布
- 并发压力测试

## 监控与可观测性

### 核心指标
```java
tfi.pathmatcher.cache.hit         // 缓存命中次数
tfi.pathmatcher.cache.miss        // 缓存未命中次数
tfi.pathmatcher.compile.fail      // 编译失败次数
tfi.pathmatcher.match.timeout     // 匹配超时次数
tfi.pathmatcher.pattern.toolong   // 模式过长次数
tfi.pathmatcher.pattern.complex   // 模式过复杂次数
tfi.pathmatcher.fallback.literal  // 降级到字面匹配次数
```

### 日志级别
- INFO：缓存预热、配置变更
- WARN：编译失败、超时、降级
- DEBUG：匹配详情、缓存操作

## 风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| ReDoS 攻击 | 中 | 高 | 模式限制+超时保护 |
| 缓存击穿 | 低 | 中 | 预热+限流 |
| 内存泄漏 | 低 | 高 | LRU自动淘汰 |
| 匹配错误 | 低 | 高 | 充分测试+回归集 |

## 实施检查清单

### Phase 1（立即执行）
- [ ] 增加配置属性 `tfi.pathmatcher.*`
- [ ] 实现 LRU 缓存
- [ ] 添加安全检查框架
- [ ] 集成降级机制

### Phase 2（下周）
- [ ] 实现受限正则编译
- [ ] 添加超时保护
- [ ] 完成缓存预热
- [ ] 单元测试覆盖

### Phase 3（后续）
- [ ] FSM 编译器研究
- [ ] 性能优化
- [ ] 扩展模式语法

## 与现有代码的集成

```java
// 在 SnapshotConfig 中使用
public class SnapshotConfig {
    private final PathMatcherCache pathMatcher;
    
    public boolean shouldInclude(String path) {
        // 先检查 exclude
        for (String pattern : excludePatterns) {
            if (pathMatcher.matches(pattern, path)) {
                return false;
            }
        }
        
        // 再检查 include
        if (includePatterns.isEmpty()) {
            return true; // 默认包含
        }
        
        for (String pattern : includePatterns) {
            if (pathMatcher.matches(pattern, path)) {
                return true;
            }
        }
        
        return false;
    }
}
```

## 开放问题

1. **危险模式黑名单维护？**
   - 建议：建立模式库，定期更新

2. **是否支持正则表达式？**
   - 建议：仅在明确需求时，以独立 API 提供

3. **缓存共享策略？**
   - 建议：全局单例，避免重复编译

---
*更新：基于工程评审反馈，分阶段实现，优先保证安全性*