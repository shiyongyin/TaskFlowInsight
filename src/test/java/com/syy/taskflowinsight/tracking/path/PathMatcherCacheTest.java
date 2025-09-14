package com.syy.taskflowinsight.tracking.path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PathMatcherCache单元测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@DisplayName("PathMatcherCache单元测试")
public class PathMatcherCacheTest {
    
    private PathMatcherCache matcher;
    
    @BeforeEach
    void setUp() {
        matcher = new PathMatcherCache();
        matcher.setCacheSize(100);
        matcher.setPatternMaxLength(256);
        matcher.setMaxWildcards(10);
    }
    
    @Test
    @DisplayName("精确匹配测试")
    void testExactMatching() {
        // 精确匹配
        assertThat(matcher.matches("user.name", "user.name")).isTrue();
        assertThat(matcher.matches("user.age", "user.name")).isFalse();
        assertThat(matcher.matches("user", "user")).isTrue();
        assertThat(matcher.matches("", "")).isTrue();
    }
    
    @Test
    @DisplayName("单层通配符(*)匹配")
    void testSingleWildcard() {
        // * 匹配单层（不包括.）
        assertThat(matcher.matches("user.name", "user.*")).isTrue();
        assertThat(matcher.matches("user.age", "user.*")).isTrue();
        assertThat(matcher.matches("user.address.city", "user.*")).isFalse();
        
        // 前缀匹配
        assertThat(matcher.matches("username", "*name")).isTrue();
        assertThat(matcher.matches("firstname", "*name")).isTrue();
        assertThat(matcher.matches("name", "*name")).isTrue();
        
        // 后缀匹配
        assertThat(matcher.matches("username", "user*")).isTrue();
        assertThat(matcher.matches("userAge", "user*")).isTrue();
        assertThat(matcher.matches("admin", "user*")).isFalse();
        
        // 中间匹配
        assertThat(matcher.matches("user.name.field", "user.*.field")).isTrue();
        assertThat(matcher.matches("user.age.field", "user.*.field")).isTrue();
        assertThat(matcher.matches("user.address.city.field", "user.*.field")).isFalse();
    }
    
    @Test
    @DisplayName("多层通配符(**)匹配")
    void testDoubleWildcard() {
        // ** 匹配多层
        assertThat(matcher.matches("user.name", "user.**")).isTrue();
        assertThat(matcher.matches("user.address.city", "user.**")).isTrue();
        assertThat(matcher.matches("user.profile.contact.phone", "user.**")).isTrue();
        assertThat(matcher.matches("admin.name", "user.**")).isFalse();
        
        // 中间的**
        assertThat(matcher.matches("start.middle.end", "start.**.end")).isTrue();
        assertThat(matcher.matches("start.a.b.c.end", "start.**.end")).isTrue();
        assertThat(matcher.matches("start.end", "start.**.end")).isTrue();
        
        // 组合使用
        assertThat(matcher.matches("user.profile.name.first", "user.**.first")).isTrue();
        assertThat(matcher.matches("user.settings.privacy.level", "user.**.*")).isTrue();
    }
    
    @Test
    @DisplayName("单字符通配符(?)匹配")
    void testQuestionMarkWildcard() {
        // ? 匹配单个字符
        assertThat(matcher.matches("user1", "user?")).isTrue();
        assertThat(matcher.matches("userA", "user?")).isTrue();
        assertThat(matcher.matches("user", "user?")).isFalse();
        assertThat(matcher.matches("user10", "user?")).isFalse();
        
        // 多个?
        assertThat(matcher.matches("user12", "user??")).isTrue();
        assertThat(matcher.matches("user1", "user??")).isFalse();
        
        // 组合使用
        assertThat(matcher.matches("file1.txt", "file?.txt")).isTrue();
        assertThat(matcher.matches("file10.txt", "file??.txt")).isTrue();
    }
    
    @Test
    @DisplayName("复杂模式组合")
    void testComplexPatterns() {
        // 组合各种通配符
        assertThat(matcher.matches("user.john.address.city", "user.*.address.*")).isTrue();
        assertThat(matcher.matches("user.jane.profile.email", "user.*.profile.*")).isTrue();
        assertThat(matcher.matches("api.v1.users.123.profile", "api.v?.users.*.profile")).isTrue();
        assertThat(matcher.matches("api.v2.users.456.settings", "api.v?.users.**.settings")).isTrue();
        
        // 密码相关字段过滤
        assertThat(matcher.matches("user.password", "*.password")).isTrue();
        assertThat(matcher.matches("admin.password", "*.password")).isTrue();
        assertThat(matcher.matches("user.settings.password", "**.password")).isTrue();
        assertThat(matcher.matches("user.passwordHash", "*.password*")).isTrue();
    }
    
    @Test
    @DisplayName("批量匹配测试")
    void testBatchMatching() {
        List<String> paths = Arrays.asList(
            "user.name",
            "user.age",
            "user.address.city",
            "admin.name",
            "guest.profile"
        );
        
        Map<String, Boolean> results = matcher.matchBatch(paths, "user.**");
        
        assertThat(results).hasSize(5);
        assertThat(results.get("user.name")).isTrue();
        assertThat(results.get("user.age")).isTrue();
        assertThat(results.get("user.address.city")).isTrue();
        assertThat(results.get("admin.name")).isFalse();
        assertThat(results.get("guest.profile")).isFalse();
    }
    
    @Test
    @DisplayName("查找匹配模式")
    void testFindMatchingPatterns() {
        List<String> patterns = Arrays.asList(
            "user.*",
            "*.name",
            "**.city",
            "admin.**"
        );
        
        List<String> matching = matcher.findMatchingPatterns("user.name", patterns);
        assertThat(matching).containsExactlyInAnyOrder("user.*", "*.name");
        
        matching = matcher.findMatchingPatterns("user.address.city", patterns);
        assertThat(matching).containsExactly("**.city");
        
        matching = matcher.findMatchingPatterns("admin.settings", patterns);
        assertThat(matching).containsExactly("admin.**");
    }
    
    @Test
    @DisplayName("null值处理")
    void testNullHandling() {
        assertThat(matcher.matches(null, "pattern")).isFalse();
        assertThat(matcher.matches("path", null)).isFalse();
        assertThat(matcher.matches(null, null)).isFalse();
        
        assertThat(matcher.matchBatch(null, "pattern")).isEmpty();
        assertThat(matcher.matchBatch(Collections.emptyList(), "pattern")).isEmpty();
        
        assertThat(matcher.findMatchingPatterns(null, Arrays.asList("*"))).isEmpty();
        assertThat(matcher.findMatchingPatterns("path", null)).isEmpty();
    }
    
    @Test
    @DisplayName("输入验证")
    void testInputValidation() {
        // 超长模式
        String longPattern = "a".repeat(300);
        assertThatThrownBy(() -> matcher.matches("path", longPattern))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Pattern too long");
        
        // 太多通配符
        String tooManyWildcards = "*".repeat(15);
        assertThatThrownBy(() -> matcher.matches("path", tooManyWildcards))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Too many wildcards");
    }
    
    @Test
    @DisplayName("缓存功能测试")
    void testCaching() {
        String path = "user.profile.settings";
        String pattern = "user.**";
        
        // 第一次匹配（缓存未命中）
        PathMatcherCache.CacheStats statsBefore = matcher.getStats();
        long missesBefore = statsBefore.getMissCount();
        
        boolean result1 = matcher.matches(path, pattern);
        
        PathMatcherCache.CacheStats statsAfter1 = matcher.getStats();
        assertThat(statsAfter1.getMissCount()).isEqualTo(missesBefore + 1);
        
        // 第二次匹配（缓存命中）
        boolean result2 = matcher.matches(path, pattern);
        
        PathMatcherCache.CacheStats statsAfter2 = matcher.getStats();
        assertThat(statsAfter2.getHitCount()).isEqualTo(statsAfter1.getHitCount() + 1);
        assertThat(statsAfter2.getMissCount()).isEqualTo(statsAfter1.getMissCount());
        
        assertThat(result1).isEqualTo(result2);
    }
    
    @Test
    @DisplayName("缓存清理测试")
    void testCacheClear() {
        // 添加一些缓存项（使用有通配符的模式确保进入缓存）
        for (int i = 0; i < 10; i++) {
            matcher.matches("path" + i, "pattern*" + i);
        }
        
        PathMatcherCache.CacheStats statsBeforeClear = matcher.getStats();
        assertThat(statsBeforeClear.getPatternCacheSize()).isGreaterThan(0);
        assertThat(statsBeforeClear.getResultCacheSize()).isGreaterThan(0);
        
        // 清理缓存
        matcher.clear();
        
        PathMatcherCache.CacheStats statsAfterClear = matcher.getStats();
        assertThat(statsAfterClear.getPatternCacheSize()).isEqualTo(0);
        assertThat(statsAfterClear.getResultCacheSize()).isEqualTo(0);
        assertThat(statsAfterClear.getHitCount()).isEqualTo(0);
        assertThat(statsAfterClear.getMissCount()).isEqualTo(0);
    }
    
    @Test
    @DisplayName("预加载模式测试")
    void testPreloadPatterns() {
        List<String> patterns = Arrays.asList(
            "*.password",
            "*.secret",
            "**.token",
            "**.apiKey"
        );
        
        matcher.preload(patterns);
        
        PathMatcherCache.CacheStats stats = matcher.getStats();
        assertThat(stats.getPatternCacheSize()).isEqualTo(4);
    }
    
    @Test
    @DisplayName("统计信息测试")
    void testStatistics() {
        // 执行一些匹配（使用有通配符的模式）
        for (int i = 0; i < 100; i++) {
            matcher.matches("user.name", "user.*"); // 缓存命中
        }
        
        for (int i = 0; i < 10; i++) {
            matcher.matches("path" + i, "pattern*" + i); // 缓存未命中
        }
        
        PathMatcherCache.CacheStats stats = matcher.getStats();
        
        assertThat(stats.getHitCount()).isGreaterThanOrEqualTo(99); // 至少99次命中
        assertThat(stats.getMissCount()).isGreaterThanOrEqualTo(11); // 至少11次未命中（第一次user.*也是未命中）
        assertThat(stats.getHitRate()).isGreaterThan(0.85); // 命中率>85%
        assertThat(stats.getAvgMatchTimeNanos()).isGreaterThan(0);
        
        // 测试toString
        String statsStr = stats.toString();
        assertThat(statsStr).contains("CacheStats");
        assertThat(statsStr).contains("hitRate=");
        assertThat(statsStr).contains("avgTime=");
    }
    
    @Test
    @DisplayName("并发安全测试")
    void testConcurrentSafety() throws InterruptedException {
        int threadCount = 10;
        int iterationsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        String path = "user.thread" + threadId + ".item" + i;
                        String pattern = "user.**.item*";
                        
                        if (matcher.matches(path, pattern)) {
                            successCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        // 验证所有匹配都成功
        assertThat(successCount.get()).isEqualTo(threadCount * iterationsPerThread);
        
        // 验证缓存统计
        PathMatcherCache.CacheStats stats = matcher.getStats();
        assertThat(stats.getHitCount() + stats.getMissCount())
            .isEqualTo(threadCount * iterationsPerThread);
    }
    
    @Test
    @DisplayName("特殊字符转义测试")
    void testSpecialCharacterEscaping() {
        // 点号应该被正确转义
        assertThat(matcher.matches("user.name", "user.name")).isTrue();
        assertThat(matcher.matches("username", "user.name")).isFalse();
        
        // 其他特殊字符
        assertThat(matcher.matches("user[admin]", "user[admin]")).isTrue();
        assertThat(matcher.matches("user{id}", "user{id}")).isTrue();
        assertThat(matcher.matches("user(test)", "user(test)")).isTrue();
        assertThat(matcher.matches("user$var", "user$var")).isTrue();
        assertThat(matcher.matches("user^root", "user^root")).isTrue();
        assertThat(matcher.matches("user+admin", "user+admin")).isTrue();
        assertThat(matcher.matches("user|admin", "user|admin")).isTrue();
    }
    
    @Test
    @DisplayName("边界情况测试")
    void testEdgeCases() {
        // 空字符串
        assertThat(matcher.matches("", "")).isTrue();
        assertThat(matcher.matches("", "*")).isTrue();
        assertThat(matcher.matches("", "**")).isTrue();
        assertThat(matcher.matches("", "?")).isFalse();
        
        // 只有通配符
        assertThat(matcher.matches("anything", "*")).isTrue();
        assertThat(matcher.matches("any.thing", "**")).isTrue();
        assertThat(matcher.matches("a", "?")).isTrue();
        
        // 连续通配符
        assertThat(matcher.matches("user.name", "**.**")).isTrue();
        assertThat(matcher.matches("test", "***")).isTrue();
        
        // 点号边界
        assertThat(matcher.matches(".user", ".*")).isTrue();
        assertThat(matcher.matches("user.", "user.*")).isTrue();
        assertThat(matcher.matches("..user", "**")).isTrue();
    }
}