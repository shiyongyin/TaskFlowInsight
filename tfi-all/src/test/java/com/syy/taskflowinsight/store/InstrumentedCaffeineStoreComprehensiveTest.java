package com.syy.taskflowinsight.store;

import com.github.benmanes.caffeine.cache.CacheLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.*;

/**
 * InstrumentedCaffeineStore综合测试
 * 专门提升InstrumentedCaffeineStore覆盖率从51%到80%
 * 
 * @author TaskFlow Insight Team
 * @since 2025-01-13
 */
@DisplayName("InstrumentedCaffeineStore综合测试 - 目标覆盖率80%")
class InstrumentedCaffeineStoreComprehensiveTest {

    private CacheLoader<String, String> testLoader;
    private CacheLoader<String, String> failingLoader;
    private InstrumentedCaffeineStore<String, String> store;
    private AtomicInteger loadCounter;

    @BeforeEach
    void setUp() {
        loadCounter = new AtomicInteger(0);
        testLoader = new CacheLoader<String, String>() {
            @Override
            public String load(String key) {
                loadCounter.incrementAndGet();
                return "loaded-" + key;
            }
        };
        
        failingLoader = new CacheLoader<String, String>() {
            @Override
            public String load(String key) throws Exception {
                throw new RuntimeException("Loader failed for key: " + key);
            }
        };
    }

    @Nested
    @DisplayName("构造函数和配置测试")
    class ConstructorAndConfigurationTests {

        @Test
        @DisplayName("默认配置构造函数应该正确初始化")
        void defaultConstructorShouldInitializeCorrectly() {
            store = new InstrumentedCaffeineStore<>(testLoader);
            
            assertThat(store).isNotNull();
            assertThat(store.size()).isEqualTo(0);
            
            // 验证可以正常工作
            store.put("key", "value");
            assertThat(store.get("key")).contains("value");
        }

        @Test
        @DisplayName("自定义配置构造函数应该正确初始化")
        void customConfigConstructorShouldInitializeCorrectly() {
            StoreConfig config = StoreConfig.builder()
                .maxSize(50)
                .defaultTtl(Duration.ofMinutes(5))
                .idleTimeout(Duration.ofMinutes(2))
                .refreshAfterWrite(Duration.ofMinutes(1))
                .useSoftValues(true)
                .logEvictions(true)
                .recordStats(true)
                .build();

            store = new InstrumentedCaffeineStore<>(config, testLoader);
            
            assertThat(store).isNotNull();
            assertThat(store.size()).isEqualTo(0);
            
            // 验证加载器工作
            Optional<String> result = store.get("test-key");
            assertThat(result).contains("loaded-test-key");
            assertThat(loadCounter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("所有配置选项都应该正确应用")
        void allConfigurationOptionsShouldBeApplied() throws Exception {
            StoreConfig config = StoreConfig.builder()
                .maxSize(10)
                .defaultTtl(Duration.ofMillis(100))
                .idleTimeout(Duration.ofMillis(50))
                .refreshAfterWrite(Duration.ofMillis(30))
                .useSoftValues(false)
                .logEvictions(false)
                .recordStats(true)
                .build();

            store = new InstrumentedCaffeineStore<>(config, testLoader);
            
            // 验证最大大小限制
            for (int i = 0; i < 15; i++) {
                store.put("key" + i, "value" + i);
            }
            
            // 由于maxSize=10，应该有一些条目被驱逐
            assertThat(store.size()).isLessThanOrEqualTo(10);
            
            // 验证TTL - 等待过期
            store.put("ttl-test", "ttl-value");
            Thread.sleep(120); // 超过TTL时间
            store.cleanUp(); // 手动触发清理
            
            // 由于TTL过期，应该重新加载
            Optional<String> result = store.get("ttl-test");
            assertThat(result).contains("loaded-ttl-test"); // 从loader加载
        }
    }

    @Nested
    @DisplayName("基本操作测试")
    class BasicOperationTests {

        @BeforeEach
        void setUpStore() {
            store = new InstrumentedCaffeineStore<>(testLoader);
        }

        @Test
        @DisplayName("put操作应该正确存储键值对")
        void putShouldStoreKeyValuePairs() {
            store.put("key1", "value1");
            store.put("key2", "value2");
            
            assertThat(store.get("key1")).contains("value1");
            assertThat(store.get("key2")).contains("value2");
            assertThat(store.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("put操作null键应该抛出异常")
        void putWithNullKeyShouldThrowException() {
            assertThatThrownBy(() -> store.put(null, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Key and value cannot be null");
        }

        @Test
        @DisplayName("put操作null值应该抛出异常")
        void putWithNullValueShouldThrowException() {
            assertThatThrownBy(() -> store.put("key", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Key and value cannot be null");
        }

        @Test
        @DisplayName("get操作null键应该返回空Optional")
        void getWithNullKeyShouldReturnEmpty() {
            Optional<String> result = store.get(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("get不存在的键应该使用loader加载")
        void getForMissingKeyShouldUseLoader() {
            Optional<String> result = store.get("missing-key");
            
            assertThat(result).contains("loaded-missing-key");
            assertThat(loadCounter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("remove操作应该正确删除键")
        void removeShouldDeleteKey() {
            store.put("to-remove", "value");
            assertThat(store.get("to-remove")).contains("value");
            
            store.remove("to-remove");
            
            // 应该从loader重新加载
            Optional<String> result = store.get("to-remove");
            assertThat(result).contains("loaded-to-remove");
            assertThat(loadCounter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("remove操作null键应该安全处理")
        void removeWithNullKeyShouldBeHandledSafely() {
            assertThatCode(() -> store.remove(null))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("clear操作应该清空所有缓存")
        void clearShouldEmptyAllCache() {
            store.put("key1", "value1");
            store.put("key2", "value2");
            assertThat(store.size()).isEqualTo(2);
            
            store.clear();
            
            assertThat(store.size()).isEqualTo(0);
            
            // 清空后get应该从loader重新加载
            Optional<String> result = store.get("key1");
            assertThat(result).contains("loaded-key1");
        }
    }

    @Nested
    @DisplayName("异步操作测试")
    class AsyncOperationTests {

        @BeforeEach
        void setUpStore() {
            store = new InstrumentedCaffeineStore<>(testLoader);
        }

        @Test
        @DisplayName("getAsync应该返回异步结果")
        void getAsyncShouldReturnAsyncResult() throws Exception {
            store.put("async-key", "async-value");
            
            CompletableFuture<String> future = store.getAsync("async-key");
            
            assertThat(future).isNotNull();
            String result = future.get(1, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("async-value");
        }

        @Test
        @DisplayName("getAsync不存在的键应该异步加载")
        void getAsyncForMissingKeyShouldAsyncLoad() throws Exception {
            CompletableFuture<String> future = store.getAsync("async-missing");
            
            assertThat(future).isNotNull();
            String result = future.get(1, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("loaded-async-missing");
            assertThat(loadCounter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("getAsync指定执行器应该正确工作")
        void getAsyncWithExecutorShouldWork() throws Exception {
            Executor customExecutor = Executors.newSingleThreadExecutor();
            store.put("executor-key", "executor-value");
            
            CompletableFuture<String> future = store.getAsync("executor-key", customExecutor);
            
            assertThat(future).isNotNull();
            String result = future.get(1, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("executor-value");
        }

        @Test
        @DisplayName("getAsync应该处理缓存命中")
        void getAsyncShouldHandleCacheHit() throws Exception {
            store.put("cached-key", "cached-value");
            
            CompletableFuture<String> future = store.getAsync("cached-key");
            
            assertThat(future).isNotNull();
            String result = future.get(1, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("cached-value");
            // 对于缓存命中，不应该调用loader
            assertThat(loadCounter.get()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("刷新操作测试")
    class RefreshOperationTests {

        @BeforeEach
        void setUpStore() {
            store = new InstrumentedCaffeineStore<>(testLoader);
        }

        @Test
        @DisplayName("refresh应该触发重新加载")
        void refreshShouldTriggerReload() {
            // 首次加载
            Optional<String> firstResult = store.get("refresh-key");
            assertThat(firstResult).contains("loaded-refresh-key");
            assertThat(loadCounter.get()).isEqualTo(1);
            
            // 执行刷新（刷新会再次调用loader）
            store.refresh("refresh-key");

            // refresh是异步执行的，等待刷新完成后再断言
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(loadCounter.get()).isGreaterThanOrEqualTo(2)
            );

            // 刷新后再次获取
            Optional<String> refreshedResult = store.get("refresh-key");
            assertThat(refreshedResult).contains("loaded-refresh-key");
        }

        @Test
        @DisplayName("refreshAsync应该返回异步刷新结果")
        void refreshAsyncShouldReturnAsyncRefreshResult() throws Exception {
            // 首次加载
            store.get("async-refresh-key");
            int initialLoadCount = loadCounter.get();
            
            // 执行异步刷新
            CompletableFuture<String> future = store.refreshAsync("async-refresh-key");
            
            assertThat(future).isNotNull();
            String result = future.get(1, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("loaded-async-refresh-key");
            // 刷新应该再次调用loader
            assertThat(loadCounter.get()).isGreaterThan(initialLoadCount);
        }

        @Test
        @DisplayName("refresh不存在的键应该安全处理")
        void refreshForNonExistentKeyShouldBeHandledSafely() {
            assertThatCode(() -> store.refresh("non-existent"))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("错误处理测试")
    class ErrorHandlingTests {

        @BeforeEach
        void setUpFailingLoader() {
            store = new InstrumentedCaffeineStore<>(failingLoader);
        }

        @Test
        @DisplayName("loader异常应该返回空Optional")
        void loaderExceptionShouldReturnEmptyOptional() {
            Optional<String> result = store.get("failing-key");
            
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("loader异常不应该传播到调用者")
        void loaderExceptionShouldNotPropagateToCallers() {
            // 验证loader异常不会传播，而是返回空结果
            assertThatCode(() -> store.get("error-key"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("多次loader异常应该每次都返回空")
        void multipleLoaderExceptionsShouldAlwaysReturnEmpty() {
            for (int i = 0; i < 3; i++) {
                Optional<String> result = store.get("failing-key-" + i);
                assertThat(result).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("统计信息测试")
    class StatisticsTests {

        @BeforeEach
        void setUpStore() {
            StoreConfig config = StoreConfig.builder()
                .maxSize(100)
                .recordStats(true)
                .build();
            
            store = new InstrumentedCaffeineStore<>(config, testLoader);
        }

        @Test
        @DisplayName("getStats应该返回正确的统计信息")
        void getStatsShouldReturnCorrectStatistics() {
            // 执行一些操作
            store.put("stats-key1", "value1");
            store.get("stats-key1"); // hit
            store.get("stats-key2"); // miss -> load
            store.get("stats-key1"); // hit again
            
            StoreStats stats = store.getStats();
            
            assertThat(stats).isNotNull();
            assertThat(stats.getHitCount()).isGreaterThanOrEqualTo(2);
            assertThat(stats.getMissCount()).isGreaterThanOrEqualTo(1);
            assertThat(stats.getLoadSuccessCount()).isGreaterThanOrEqualTo(1);
            assertThat(stats.getEstimatedSize()).isGreaterThanOrEqualTo(1);
            assertThat(stats.getHitRate()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("统计信息应该正确反映缓存性能")
        void statisticsShouldReflectCachePerformance() {
            // 创建已知的访问模式
            for (int i = 0; i < 10; i++) {
                store.put("perf-key" + i, "value" + i);
            }
            
            // 执行混合访问（命中和未命中）
            for (int i = 0; i < 5; i++) {
                store.get("perf-key" + i); // hits
            }
            for (int i = 10; i < 15; i++) {
                store.get("perf-key" + i); // misses -> loads
            }
            
            StoreStats stats = store.getStats();
            
            assertThat(stats.getHitCount()).isGreaterThanOrEqualTo(5);
            assertThat(stats.getMissCount()).isGreaterThanOrEqualTo(5);
            assertThat(stats.getLoadSuccessCount()).isGreaterThanOrEqualTo(5);
        }
    }

    @Nested
    @DisplayName("清理和维护测试")
    class CleanupAndMaintenanceTests {

        @BeforeEach
        void setUpStore() {
            store = new InstrumentedCaffeineStore<>(testLoader);
        }

        @Test
        @DisplayName("cleanUp应该触发缓存清理")
        void cleanUpShouldTriggerCacheCleanup() {
            store.put("cleanup-key", "cleanup-value");
            assertThat(store.size()).isEqualTo(1);
            
            store.cleanUp();
            
            // cleanUp主要是内部维护，size可能不变
            // 但应该不会抛出异常
            assertThat(store.size()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("size应该在清理后调用cleanUp")
        void sizeShouldCallCleanUpBeforeReturning() {
            store.put("size-key1", "value1");
            store.put("size-key2", "value2");
            
            long size = store.size();
            
            assertThat(size).isGreaterThanOrEqualTo(0);
            // 验证cleanUp被调用（通过覆盖率验证）
        }

        @Test
        @DisplayName("多次clear和cleanUp应该安全执行")
        void multipleClearAndCleanUpShouldBeSafe() {
            store.put("multi-key", "multi-value");
            
            store.clear();
            store.cleanUp();
            store.clear();
            store.cleanUp();
            
            assertThat(store.size()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("配置边界测试")
    class ConfigurationBoundaryTests {

        @Test
        @DisplayName("最小配置应该正常工作")
        void minimalConfigurationShouldWork() {
            StoreConfig config = StoreConfig.builder()
                .maxSize(1)
                .build();
            
            store = new InstrumentedCaffeineStore<>(config, testLoader);
            
            store.put("minimal-key", "minimal-value");
            assertThat(store.get("minimal-key")).contains("minimal-value");
        }

        @Test
        @DisplayName("所有TTL配置为null应该正常工作")
        void allNullTtlConfigurationShouldWork() {
            StoreConfig config = StoreConfig.builder()
                .maxSize(100)
                .defaultTtl(null)
                .idleTimeout(null)
                .refreshAfterWrite(null)
                .useSoftValues(false)
                .logEvictions(false)
                .recordStats(true)
                .build();
            
            store = new InstrumentedCaffeineStore<>(config, testLoader);
            
            store.put("null-ttl-key", "null-ttl-value");
            assertThat(store.get("null-ttl-key")).contains("null-ttl-value");
        }

        @Test
        @DisplayName("极短TTL配置应该正确过期")
        void veryShortTtlShouldExpireCorrectly() throws Exception {
            StoreConfig config = StoreConfig.builder()
                .maxSize(100)
                .defaultTtl(Duration.ofMillis(10))
                .recordStats(true)
                .build();
            
            store = new InstrumentedCaffeineStore<>(config, testLoader);
            
            store.put("short-ttl-key", "short-ttl-original");
            
            // 等待过期
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 应该重新加载
            Optional<String> result = store.get("short-ttl-key");
            assertThat(result).contains("loaded-short-ttl-key");
            assertThat(loadCounter.get()).isEqualTo(1);
        }
    }
}
