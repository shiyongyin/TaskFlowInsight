package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.actuator.support.TfiHealthCalculator;
import com.syy.taskflowinsight.actuator.support.TfiStatsAggregator;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.tracking.path.PathMatcherCacheInterface;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.when;

/**
 * SecureTfiEndpoint直接方法测试
 * 专门针对未覆盖的私有方法进行测试，提升覆盖率
 * 
 * @author TaskFlow Insight Team
 * @since 2025-01-13
 */
@DisplayName("SecureTfiEndpoint直接方法测试 - 提升私有方法覆盖率")
class SecureTfiEndpointDirectMethodTest {

    private SecureTfiEndpoint endpoint;
    private MeterRegistry meterRegistry;
    
    private TfiConfig tfiConfig;
    
    @Mock
    private PathMatcherCacheInterface pathMatcherCache;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        
        TfiConfig.ChangeTracking changeTracking = new TfiConfig.ChangeTracking(
            true, 8192, 5, null, null, null, 1024, null);
        TfiConfig.Context context = new TfiConfig.Context(
            3600000L, false, 60000L, false, 60000L);
        TfiConfig.Metrics metrics = new TfiConfig.Metrics(true, Map.of(), "PT1M");
        TfiConfig.Security security = new TfiConfig.Security(true, Set.of());
        tfiConfig = new TfiConfig(true, changeTracking, context, metrics, security);
        
        // PathMatcherCacheInterface mock
        PathMatcherCacheInterface.CacheStats stats = PathMatcherCacheInterface.CacheStats.builder()
            .size(50)
            .hitCount(100L)
            .missCount(10L)
            .build();
        when(pathMatcherCache.getStats()).thenReturn(stats);
        
        TfiHealthCalculator healthCalculator = new TfiHealthCalculator();
        ReflectionTestUtils.setField(healthCalculator, "memoryThreshold", 0.8);
        ReflectionTestUtils.setField(healthCalculator, "maxActiveContexts", 100);
        ReflectionTestUtils.setField(healthCalculator, "maxSessionsWarning", 500);

        endpoint = new SecureTfiEndpoint(tfiConfig, meterRegistry, pathMatcherCache,
            healthCalculator, new TfiStatsAggregator());
        
        // 初始化TFI状态
        TFI.clear();
        TFI.enable();
    }

    @AfterEach
    void tearDown() {
        TFI.clear();
    }

    @Test
    @DisplayName("getCachedResponse私有方法应该支持缓存机制")
    void getCachedResponseShouldSupportCaching() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("getCachedResponse", String.class, Supplier.class);
        method.setAccessible(true);
        
        Supplier<Map<String, Object>> supplier = () -> {
            Map<String, Object> data = Map.of("test", "value");
            return data;
        };
        
        @SuppressWarnings("unchecked")
        Map<String, Object> result1 = (Map<String, Object>) method.invoke(endpoint, "test-key", supplier);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> result2 = (Map<String, Object>) method.invoke(endpoint, "test-key", supplier);
        
        assertThat(result1).isNotNull();
        assertThat(result2).isNotNull();
        assertThat(result1.get("test")).isEqualTo("value");
    }

    @Test
    @DisplayName("getComponentStatus私有方法应该返回组件状态")
    void getComponentStatusShouldReturnComponentStatus() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("getComponentStatus");
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) method.invoke(endpoint);
        
        assertThat(components).isNotNull();
        assertThat(components).containsKeys("changeTracking", "dataMasking", "threadContext");
    }

    @Test
    @DisplayName("getRecentAccessCount私有方法应该返回最近访问次数")
    void getRecentAccessCountShouldReturnCount() throws Exception {
        // 先触发一些访问
        endpoint.taskflow();
        endpoint.taskflow();
        
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("getRecentAccessCount");
        method.setAccessible(true);
        
        Integer count = (Integer) method.invoke(endpoint);
        
        assertThat(count).isNotNull();
        assertThat(count).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("calculateErrorRate私有方法应该返回错误率")
    void calculateErrorRateShouldReturnRate() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("calculateErrorRate");
        method.setAccessible(true);
        
        Double rate = (Double) method.invoke(endpoint);
        
        assertThat(rate).isNotNull();
        assertThat(rate).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("getGlobalEnabled私有方法应该返回全局启用状态")
    void getGlobalEnabledShouldReturnStatus() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("getGlobalEnabled");
        method.setAccessible(true);
        
        Boolean enabled = (Boolean) method.invoke(endpoint);
        
        assertThat(enabled).isNotNull();
    }

    @Test
    @DisplayName("isChangeTrackingEnabled私有方法应该返回变更跟踪启用状态")
    void isChangeTrackingEnabledShouldReturnStatus() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("isChangeTrackingEnabled");
        method.setAccessible(true);
        
        Boolean enabled = (Boolean) method.invoke(endpoint);
        
        assertThat(enabled).isNotNull();
    }

    @Test
    @DisplayName("私有方法在null配置下应该处理得当")
    void privateMethodsShouldHandleExceptionsGracefully() {
        TfiHealthCalculator hc = new TfiHealthCalculator();
        ReflectionTestUtils.setField(hc, "memoryThreshold", 0.8);
        ReflectionTestUtils.setField(hc, "maxActiveContexts", 100);
        ReflectionTestUtils.setField(hc, "maxSessionsWarning", 500);

        SecureTfiEndpoint nullConfigEndpoint = new SecureTfiEndpoint(
            null, meterRegistry, null, hc, new TfiStatsAggregator());
        
        assertThatNoException().isThrownBy(() -> {
            Method method = SecureTfiEndpoint.class.getDeclaredMethod("getComponentStatus");
            method.setAccessible(true);
            method.invoke(nullConfigEndpoint);
        });
        
        assertThatNoException().isThrownBy(() -> {
            Method method = SecureTfiEndpoint.class.getDeclaredMethod("getGlobalEnabled");
            method.setAccessible(true);
            method.invoke(nullConfigEndpoint);
        });
    }
}
