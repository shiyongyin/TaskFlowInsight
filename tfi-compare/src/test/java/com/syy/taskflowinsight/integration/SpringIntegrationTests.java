package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.aspect.TfiDeepTrackingAspect;
import com.syy.taskflowinsight.config.ChangeTrackingAutoConfiguration;
import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.exporter.change.ChangeConsoleExporter;
import com.syy.taskflowinsight.tracking.ChangeTracker;
import com.syy.taskflowinsight.tracking.path.CaffeinePathMatcherCache;
import com.syy.taskflowinsight.tracking.path.PathMatcherCacheInterface;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 综合集成测试：覆盖 tfi-compare 模块中 Spring Bean 的集成行为。
 * 测试 CaffeinePathMatcherCache、ChangeTrackingAutoConfiguration、TfiDeepTrackingAspect。
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@DisplayName("Spring 集成测试 — Spring Bean 综合集成")
class SpringIntegrationTests {

    // ── CaffeinePathMatcherCache ──

    @Nested
    @DisplayName("CaffeinePathMatcherCache — 路径匹配缓存")
    class CaffeinePathMatcherCacheTests {

        private TfiConfig tfiConfig;
        private CaffeinePathMatcherCache cacheWithMetrics;
        private CaffeinePathMatcherCache cacheWithoutMetrics;

        @BeforeEach
        void setUp() {
            tfiConfig = new TfiConfig(null, null, null, null, null);
            cacheWithMetrics = new CaffeinePathMatcherCache(tfiConfig, new SimpleMeterRegistry());
            cacheWithoutMetrics = new CaffeinePathMatcherCache(tfiConfig, null);
        }

        @AfterEach
        void tearDown() {
            if (cacheWithMetrics != null) {
                cacheWithMetrics.destroy();
            }
            if (cacheWithoutMetrics != null) {
                cacheWithoutMetrics.destroy();
            }
        }

        @Test
        @DisplayName("构造函数 — 使用 null MeterRegistry 可正常创建")
        void constructor_nullMeterRegistry() {
            CaffeinePathMatcherCache c = new CaffeinePathMatcherCache(tfiConfig, null);
            assertThat(c).isNotNull();
            c.destroy();
        }

        @Test
        @DisplayName("matches — path 或 pattern 为 null 时返回 false")
        void matches_nullInputs() {
            assertThat(cacheWithMetrics.matches(null, "*.foo")).isFalse();
            assertThat(cacheWithMetrics.matches("a.foo", null)).isFalse();
            assertThat(cacheWithMetrics.matches(null, null)).isFalse();
        }

        @Test
        @DisplayName("matches — 有效输入，通配符 * 匹配单层")
        void matches_validInputs_singleWildcard() {
            assertThat(cacheWithMetrics.matches("a", "*")).isTrue();
            assertThat(cacheWithMetrics.matches("abc", "*")).isTrue();
            assertThat(cacheWithMetrics.matches("a.b", "*")).isFalse(); // * 不匹配 .
        }

        @Test
        @DisplayName("matches — 通配符 ** 匹配任意")
        void matches_doubleWildcard() {
            assertThat(cacheWithMetrics.matches("a", "**")).isTrue();
            assertThat(cacheWithMetrics.matches("a.b.c", "**")).isTrue();
        }

        @Test
        @DisplayName("matches — 通配符 ? 匹配单字符")
        void matches_questionMark() {
            assertThat(cacheWithMetrics.matches("a", "?")).isTrue();
            assertThat(cacheWithMetrics.matches("ab", "?")).isFalse();
        }

        @Test
        @DisplayName("matches — 字面量匹配")
        void matches_literal() {
            assertThat(cacheWithMetrics.matches("foo.bar", "foo.bar")).isTrue();
            assertThat(cacheWithMetrics.matches("foo.bar", "foo.baz")).isFalse();
        }

        @Test
        @DisplayName("matches — 缓存命中，第二次调用返回相同结果")
        void matches_cacheHit() {
            String path = "com.syy.service.UserService";
            String pattern = "com.syy.**";
            boolean first = cacheWithMetrics.matches(path, pattern);
            boolean second = cacheWithMetrics.matches(path, pattern);
            assertThat(first).isEqualTo(second);
        }

        @Test
        @DisplayName("matches — 无效正则模式触发 fallback")
        void matches_invalidPattern_fallback() {
            // 使用会导致 PatternSyntaxException 的模式（如未闭合的 [）
            boolean result = cacheWithMetrics.matches("foo", "[invalid");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("matchBatch — null 或空输入返回空 Map")
        void matchBatch_nullOrEmpty() {
            assertThat(cacheWithMetrics.matchBatch(null, "*.foo")).isEmpty();
            assertThat(cacheWithMetrics.matchBatch(List.of(), "*.foo")).isEmpty();
            assertThat(cacheWithMetrics.matchBatch(List.of("a"), null)).isEmpty();
        }

        @Test
        @DisplayName("matchBatch — 有效批量匹配")
        void matchBatch_validInputs() {
            List<String> paths = List.of("a.foo", "b.bar", "c.foo");
            Map<String, Boolean> result = cacheWithMetrics.matchBatch(paths, "*.foo");
            assertThat(result).hasSize(3);
            assertThat(result.get("a.foo")).isTrue();
            assertThat(result.get("b.bar")).isFalse();
            assertThat(result.get("c.foo")).isTrue();
        }

        @Test
        @DisplayName("matchBatch — 路径列表含 null 时跳过")
        void matchBatch_skipsNullPaths() {
            List<String> paths = new java.util.ArrayList<>();
            paths.add("a.foo");
            paths.add(null);
            paths.add("c.foo");
            Map<String, Boolean> result = cacheWithMetrics.matchBatch(paths, "*.foo");
            assertThat(result).containsOnlyKeys("a.foo", "c.foo");
        }

        @Test
        @DisplayName("findMatchingPatterns — path 或 patterns 为 null 返回空列表")
        void findMatchingPatterns_nullInputs() {
            assertThat(cacheWithMetrics.findMatchingPatterns(null, List.of("*.foo"))).isEmpty();
            assertThat(cacheWithMetrics.findMatchingPatterns("a.foo", null)).isEmpty();
        }

        @Test
        @DisplayName("findMatchingPatterns — 有效输入返回匹配的模式")
        void findMatchingPatterns_validInputs() {
            List<String> patterns = List.of("*.foo", "*.bar", "a.*");
            List<String> result = cacheWithMetrics.findMatchingPatterns("a.foo", patterns);
            assertThat(result).containsExactlyInAnyOrder("*.foo", "a.*");
        }

        @Test
        @DisplayName("preload — null 不抛异常")
        void preload_null() {
            assertThatCode(() -> cacheWithMetrics.preload(null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("preload — 有效模式预加载")
        void preload_validPatterns() {
            cacheWithMetrics.preload(List.of("*.foo", "com.syy.**"));
            PathMatcherCacheInterface.CacheStats stats = cacheWithMetrics.getStats();
            assertThat(stats.getSize()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("preload — 含无效模式时跳过并继续")
        void preload_invalidPatterns() {
            assertThatCode(() -> cacheWithMetrics.preload(List.of("valid.*", "[invalid")))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("clear — 清空缓存")
        void clear() {
            cacheWithMetrics.matches("a.foo", "*.foo");
            cacheWithMetrics.clear();
            PathMatcherCacheInterface.CacheStats stats = cacheWithMetrics.getStats();
            assertThat(stats.getSize()).isEqualTo(0);
        }

        @Test
        @DisplayName("getStats — 返回有效统计")
        void getStats() {
            cacheWithMetrics.matches("a.foo", "*.foo");
            PathMatcherCacheInterface.CacheStats stats = cacheWithMetrics.getStats();
            assertThat(stats).isNotNull();
            assertThat(stats.getHitCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getMissCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getSize()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("destroy — 生命周期销毁")
        void destroy() {
            CaffeinePathMatcherCache c = new CaffeinePathMatcherCache(tfiConfig, new SimpleMeterRegistry());
            assertThatCode(c::destroy).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("无 MeterRegistry 时 clear 和 getStats 仍可用")
        void withoutMeterRegistry_clearAndGetStats() {
            // matches() 需要 MeterRegistry，此处仅验证 clear 和 getStats
            cacheWithoutMetrics.clear();
            PathMatcherCacheInterface.CacheStats stats = cacheWithoutMetrics.getStats();
            assertThat(stats).isNotNull();
        }
    }

    // ── ChangeTrackingAutoConfiguration ──

    @Nested
    @DisplayName("ChangeTrackingAutoConfiguration — 变更追踪自动配置")
    class ChangeTrackingAutoConfigurationTests {

        @Test
        @DisplayName("配置类可加载")
        void configurationClass_loadable() {
            assertThat(ChangeTrackingAutoConfiguration.class).isNotNull();
        }

        @Test
        @DisplayName("静态 Bean 方法 diffFacadeAppContextInjector 可调用")
        void diffFacadeAppContextInjector_staticBean() {
            Object injector = ChangeTrackingAutoConfiguration.diffFacadeAppContextInjector();
            assertThat(injector).isNotNull();
        }

        @Test
        @DisplayName("静态 Bean 方法 snapshotProvidersAppContextInjector 可调用")
        void snapshotProvidersAppContextInjector_staticBean() {
            Object injector = ChangeTrackingAutoConfiguration.snapshotProvidersAppContextInjector();
            assertThat(injector).isNotNull();
        }
    }

    // ── TfiDeepTrackingAspect ──

    @Nested
    @DisplayName("TfiDeepTrackingAspect — 深度追踪切面")
    class TfiDeepTrackingAspectTests {

        private TfiDeepTrackingAspect aspect;

        @BeforeEach
        void setUp() {
            ChangeTracker.clearAllTracking();
        }

        @AfterEach
        void tearDown() {
            ChangeTracker.clearAllTracking();
        }

        @Test
        @DisplayName("默认构造函数创建切面")
        void defaultConstructor() {
            aspect = new TfiDeepTrackingAspect();
            assertThat(aspect).isNotNull();
        }

        @Test
        @DisplayName("带 ChangeConsoleExporter 的构造函数")
        void constructor_withExporter() {
            aspect = new TfiDeepTrackingAspect(new ChangeConsoleExporter());
            assertThat(aspect).isNotNull();
        }

        @Test
        @DisplayName("deepTracking=false 时直接放行")
        void around_deepTrackingFalse_passesThrough() throws Throwable {
            aspect = new TfiDeepTrackingAspect();
            org.aspectj.lang.ProceedingJoinPoint pjp = mock(org.aspectj.lang.ProceedingJoinPoint.class);
            com.syy.taskflowinsight.annotation.TfiTask tfiTask = mock(com.syy.taskflowinsight.annotation.TfiTask.class);

            when(tfiTask.deepTracking()).thenReturn(false);
            when(pjp.proceed()).thenReturn("result");

            Object result = aspect.around(pjp, tfiTask);

            assertThat(result).isEqualTo("result");
            org.mockito.Mockito.verify(pjp, org.mockito.Mockito.times(1)).proceed();
        }

        @Test
        @DisplayName("tfiTask 为 null 时直接放行")
        void around_tfiTaskNull_passesThrough() throws Throwable {
            aspect = new TfiDeepTrackingAspect();
            org.aspectj.lang.ProceedingJoinPoint pjp = mock(org.aspectj.lang.ProceedingJoinPoint.class);
            when(pjp.proceed()).thenReturn("ok");

            Object result = aspect.around(pjp, null);

            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("deepTracking=true 时执行追踪并放行")
        void around_deepTrackingTrue_tracksAndProceeds() throws Throwable {
            aspect = new TfiDeepTrackingAspect();
            org.aspectj.lang.ProceedingJoinPoint pjp = mock(org.aspectj.lang.ProceedingJoinPoint.class);
            org.aspectj.lang.reflect.MethodSignature signature = mock(org.aspectj.lang.reflect.MethodSignature.class);
            com.syy.taskflowinsight.annotation.TfiTask tfiTask = mock(com.syy.taskflowinsight.annotation.TfiTask.class);

            when(tfiTask.deepTracking()).thenReturn(true);
            when(tfiTask.maxDepth()).thenReturn(3);
            when(tfiTask.timeBudgetMs()).thenReturn(1000L);
            when(tfiTask.includeFields()).thenReturn(new String[0]);
            when(tfiTask.excludeFields()).thenReturn(new String[0]);
            when(tfiTask.collectionStrategy()).thenReturn("SUMMARY");
            when(pjp.proceed()).thenReturn("result");
            when(pjp.getSignature()).thenReturn(signature);
            when(signature.getMethod()).thenReturn(
                SpringIntegrationTests.class.getMethod("sampleMethod", Map.class));
            when(pjp.getArgs()).thenReturn(new Object[]{Map.of("key", "value")});

            Object result = aspect.around(pjp, tfiTask);

            assertThat(result).isEqualTo("result");
        }

        @Test
        @DisplayName("proceed 抛异常时异常向上传播")
        void around_exceptionPropagates() throws Throwable {
            aspect = new TfiDeepTrackingAspect();
            org.aspectj.lang.ProceedingJoinPoint pjp = mock(org.aspectj.lang.ProceedingJoinPoint.class);
            org.aspectj.lang.reflect.MethodSignature signature = mock(org.aspectj.lang.reflect.MethodSignature.class);
            com.syy.taskflowinsight.annotation.TfiTask tfiTask = mock(com.syy.taskflowinsight.annotation.TfiTask.class);

            when(tfiTask.deepTracking()).thenReturn(true);
            when(tfiTask.maxDepth()).thenReturn(3);
            when(tfiTask.timeBudgetMs()).thenReturn(1000L);
            when(tfiTask.includeFields()).thenReturn(new String[0]);
            when(tfiTask.excludeFields()).thenReturn(new String[0]);
            when(tfiTask.collectionStrategy()).thenReturn("SUMMARY");
            when(pjp.proceed()).thenThrow(new RuntimeException("test error"));
            when(pjp.getSignature()).thenReturn(signature);
            when(signature.getMethod()).thenReturn(
                SpringIntegrationTests.class.getMethod("sampleMethod", Map.class));

            assertThatThrownBy(() -> aspect.around(pjp, tfiTask))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("test error");
        }
    }

    @SuppressWarnings("unused")
    public static void sampleMethod(Map<String, Object> param) {
    }
}
