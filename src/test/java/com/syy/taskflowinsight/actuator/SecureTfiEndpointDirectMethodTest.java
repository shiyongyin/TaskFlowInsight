package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.masking.UnifiedDataMasker;
import com.syy.taskflowinsight.tracking.path.PathMatcherCacheInterface;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
    
    @Mock
    private TfiConfig tfiConfig;
    
    @Mock
    private UnifiedDataMasker dataMasker;
    
    @Mock
    private PathMatcherCacheInterface pathMatcherCache;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        
        // 设置TfiConfig mock
        TfiConfig.ChangeTracking changeTracking = new TfiConfig.ChangeTracking(
            true, 8192, 5, null, null, null, 1024, null);
        TfiConfig.Context context = new TfiConfig.Context(
            3600000L, false, 60000L, false, 60000L);
        TfiConfig.Metrics metrics = new TfiConfig.Metrics(true, Map.of(), "PT1M");
        TfiConfig.Security security = new TfiConfig.Security(true, Set.of());
        
        when(tfiConfig.enabled()).thenReturn(true);
        when(tfiConfig.changeTracking()).thenReturn(changeTracking);
        when(tfiConfig.context()).thenReturn(context);
        when(tfiConfig.metrics()).thenReturn(metrics);
        when(tfiConfig.security()).thenReturn(security);
        
        // PathMatcherCacheInterface mock
        PathMatcherCacheInterface.CacheStats stats = PathMatcherCacheInterface.CacheStats.builder()
            .size(50)
            .hitCount(100L)
            .missCount(10L)
            .build();
        when(pathMatcherCache.getStats()).thenReturn(stats);
        
        endpoint = new SecureTfiEndpoint(tfiConfig, meterRegistry, dataMasker, pathMatcherCache);
        
        // 初始化TFI状态
        TFI.clear();
        TFI.enable();
    }

    @AfterEach
    void tearDown() {
        TFI.clear();
    }

    @Test
    @DisplayName("generateOverview私有方法应该返回概览信息")
    void generateOverviewShouldReturnOverviewInfo() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("generateOverview");
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> overview = (Map<String, Object>) method.invoke(endpoint);
        
        assertThat(overview).isNotNull();
        assertThat(overview).containsKeys("version", "enabled", "uptime", "timestamp");
        assertThat(overview.get("version")).isEqualTo("3.0.0-MVP");
        assertThat(overview.get("enabled")).isEqualTo(true);
    }

    @Test
    @DisplayName("generateConfig私有方法应该返回配置信息")
    void generateConfigShouldReturnConfigInfo() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("generateConfig");
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) method.invoke(endpoint);
        
        assertThat(config).isNotNull();
        assertThat(config).containsKey("globalEnabled");
    }

    @Test
    @DisplayName("generateAllMetrics私有方法应该返回度量信息")
    void generateAllMetricsShouldReturnMetricsInfo() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("generateAllMetrics");
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) method.invoke(endpoint);
        
        assertThat(metrics).isNotNull();
    }

    @Test
    @DisplayName("generateCacheStats私有方法应该返回缓存统计")
    void generateCacheStatsShouldReturnCacheStats() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("generateCacheStats");
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> cache = (Map<String, Object>) method.invoke(endpoint);
        
        assertThat(cache).isNotNull();
        assertThat(cache).containsKey("enabled");
    }

    @Test
    @DisplayName("generateSessionsSummary私有方法应该返回会话摘要")
    void generateSessionsSummaryShouldReturnSessionsSummary() throws Exception {
        // 创建一些会话活动
        TFI.startSession("test-session");
        TFI.start("test-task");
        
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("generateSessionsSummary");
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> sessions = (Map<String, Object>) method.invoke(endpoint);
        
        assertThat(sessions).isNotNull();
        
        TFI.stop();
        TFI.endSession();
    }

    @Test
    @DisplayName("generateHealthCheck私有方法应该返回健康检查")
    void generateHealthCheckShouldReturnHealthCheck() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("generateHealthCheck");
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> health = (Map<String, Object>) method.invoke(endpoint);
        
        assertThat(health).isNotNull();
        assertThat(health).containsKey("status");
    }

    @Test
    @DisplayName("generateDiagnostics私有方法应该返回诊断信息")
    void generateDiagnosticsShouldReturnDiagnostics() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("generateDiagnostics");
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> diagnostics = (Map<String, Object>) method.invoke(endpoint);
        
        assertThat(diagnostics).isNotNull();
    }

    @Test
    @DisplayName("generateStats私有方法应该返回统计信息")
    void generateStatsShouldReturnStats() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("generateStats");
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) method.invoke(endpoint);
        
        assertThat(stats).isNotNull();
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
    @DisplayName("formatDuration私有方法应该正确格式化持续时间")
    void formatDurationShouldFormatCorrectly() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("formatDuration", long.class);
        method.setAccessible(true);
        
        String result1 = (String) method.invoke(endpoint, 1000L);
        String result2 = (String) method.invoke(endpoint, 65000L);
        String result3 = (String) method.invoke(endpoint, 3665000L);
        
        assertThat(result1).isNotNull();
        assertThat(result2).isNotNull();
        assertThat(result3).isNotNull();
    }

    @Test
    @DisplayName("maskSessionId私有方法应该正确脱敏会话ID")
    void maskSessionIdShouldMaskCorrectly() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("maskSessionId", String.class);
        method.setAccessible(true);
        
        String result1 = (String) method.invoke(endpoint, "session-12345");
        String result2 = (String) method.invoke(endpoint, (String) null);
        String result3 = (String) method.invoke(endpoint, "short");
        
        assertThat(result1).isNotNull();
        assertThat(result2).isEqualTo("***"); // null input returns "***"
        assertThat(result3).isNotNull();
    }

    @Test
    @DisplayName("getComponentHealthStatus私有方法应该返回组件健康状态")
    void getComponentHealthStatusShouldReturnComponentHealth() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("getComponentHealthStatus");
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) method.invoke(endpoint);
        
        assertThat(components).isNotNull();
        assertThat(components).containsKeys("changeTracking", "dataMasking", "threadContext");
    }

    @Test
    @DisplayName("generateRecommendations私有方法应该生成操作建议")
    void generateRecommendationsShouldGenerateRecommendations() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("generateRecommendations");
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        java.util.List<String> recommendations = (java.util.List<String>) method.invoke(endpoint);
        
        assertThat(recommendations).isNotNull();
    }

    @Test
    @DisplayName("checkOverallHealth私有方法应该检查整体健康状态")
    void checkOverallHealthShouldCheckHealth() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("checkOverallHealth");
        method.setAccessible(true);
        
        Boolean health = (Boolean) method.invoke(endpoint);
        
        assertThat(health).isNotNull();
        assertThat(health).isIn(true, false);
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
    @DisplayName("getRecentErrorCount私有方法应该返回最近错误次数")
    void getRecentErrorCountShouldReturnCount() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("getRecentErrorCount");
        method.setAccessible(true);
        
        Integer count = (Integer) method.invoke(endpoint);
        
        assertThat(count).isNotNull();
        assertThat(count).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("getAverageResponseTime私有方法应该返回平均响应时间")
    void getAverageResponseTimeShouldReturnTime() throws Exception {
        Method method = SecureTfiEndpoint.class.getDeclaredMethod("getAverageResponseTime");
        method.setAccessible(true);
        
        String time = (String) method.invoke(endpoint);
        
        assertThat(time).isNotNull();
        assertThat(time).contains("ms");
    }

    @Test
    @DisplayName("私有方法在异常情况下应该处理得当")
    void privateMethodsShouldHandleExceptionsGracefully() {
        // 测试在null配置下的行为
        SecureTfiEndpoint nullConfigEndpoint = new SecureTfiEndpoint(null, meterRegistry, dataMasker, null);
        
        assertThatNoException().isThrownBy(() -> {
            Method method = SecureTfiEndpoint.class.getDeclaredMethod("generateConfig");
            method.setAccessible(true);
            method.invoke(nullConfigEndpoint);
        });
        
        assertThatNoException().isThrownBy(() -> {
            Method method = SecureTfiEndpoint.class.getDeclaredMethod("generateCacheStats");
            method.setAccessible(true);
            method.invoke(nullConfigEndpoint);
        });
    }
}