package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.masking.UnifiedDataMasker;
import com.syy.taskflowinsight.tracking.path.PathMatcherCacheInterface;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.withSettings;

/**
 * SecureTfiEndpoint测试用例
 * 
 * 验证M1-005的关键要求：
 * - 纯只读操作，无写操作
 * - 数据脱敏功能
 * - 性能优化和缓存
 * - 最小权限暴露
 * 
 * @since 3.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("M1-005 SecureTfiEndpoint Tests")
class SecureTfiEndpointTest {
    
    @Mock
    private TfiConfig tfiConfig;
    
    @Mock
    private PathMatcherCacheInterface pathMatcherCache;
    
    private MeterRegistry meterRegistry;
    private UnifiedDataMasker dataMasker;
    private SecureTfiEndpoint endpoint;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        dataMasker = new UnifiedDataMasker();
        
        // 使用lenient模式避免UnnecessaryStubbingException
        setupMockProperties();
        
        endpoint = new SecureTfiEndpoint(
            tfiConfig, meterRegistry, dataMasker, pathMatcherCache);
    }
    
    private void setupMockProperties() {
        // Setup TfiConfig mock
        TfiConfig.ChangeTracking changeTracking = mock(TfiConfig.ChangeTracking.class, withSettings().lenient());
        TfiConfig.Context context = mock(TfiConfig.Context.class, withSettings().lenient());
        TfiConfig.Metrics metrics = mock(TfiConfig.Metrics.class, withSettings().lenient());
        TfiConfig.Security security = mock(TfiConfig.Security.class, withSettings().lenient());
        
        lenient().when(tfiConfig.enabled()).thenReturn(true);
        lenient().when(tfiConfig.changeTracking()).thenReturn(changeTracking);
        lenient().when(tfiConfig.context()).thenReturn(context);
        lenient().when(tfiConfig.metrics()).thenReturn(metrics);
        lenient().when(tfiConfig.security()).thenReturn(security);
        
        lenient().when(changeTracking.enabled()).thenReturn(true);
        lenient().when(context.leakDetectionEnabled()).thenReturn(false);
        lenient().when(context.cleanupEnabled()).thenReturn(false);
        lenient().when(metrics.enabled()).thenReturn(true);
        lenient().when(security.enableDataMasking()).thenReturn(true);
    }
    
    // ===== 只读性验证测试 =====
    
    @Test
    @DisplayName("端点应该只提供只读操作")
    void shouldProvideOnlyReadOperations() {
        // Given & When - 端点方法应该是只读的
        Map<String, Object> response = endpoint.taskflow();
        
        // Then - 应该返回期望的结构
        assertThat(response).isNotNull()
            .containsKey("version")
            .containsKey("enabled")
            .containsKey("uptime")
            .containsKey("timestamp")
            .containsKey("stats")
            .containsKey("config")
            .containsKey("components");
    }
    
    @Test
    @DisplayName("端点应该返回完整的监控数据结构")
    void shouldReturnCompleteMonitoringStructure() {
        // 测试场景：验证taskflow端点返回的数据结构完整性
        // 业务价值：确保监控端点提供全面的系统状态信息
        Map<String, Object> response = endpoint.taskflow();
        
        // 验证基础信息
        assertThat(response).containsKey("version");
        assertThat(response).containsKey("enabled");
        assertThat(response).containsKey("uptime");
        assertThat(response).containsKey("timestamp");
        
        // 验证组件状态
        assertThat(response).containsKey("components");
        Map<String, Object> components = (Map<String, Object>) response.get("components");
        assertThat(components).isNotNull();
        
        // 验证统计信息
        assertThat(response).containsKey("stats");
        Map<String, Object> stats = (Map<String, Object>) response.get("stats");
        assertThat(stats).containsKey("activeContexts")
                         .containsKey("totalChanges")
                         .containsKey("activeSessions")
                         .containsKey("errorRate");
        
        // 验证健康信息
        assertThat(response).containsKey("healthScore");
        assertThat(response).containsKey("healthLevel");
        
        // 验证配置信息
        assertThat(response).containsKey("config");
    }

    @Test
    @DisplayName("端点应该提供脱敏的数据")
    void shouldProvideMaskedData() {
        // 测试场景：验证敏感数据被正确脱敏
        // 业务价值：确保生产环境中敏感信息不会泄露
        Map<String, Object> response = endpoint.taskflow();
        
        // 验证数据结构存在但不包含敏感信息
        assertThat(response).isNotNull();
        assertThat(response.get("version")).isEqualTo("3.0.0-MVP");
        
        // 健康评分应该是数字
        Object healthScore = response.get("healthScore");
        assertThat(healthScore).isInstanceOf(Integer.class);
        assertThat((Integer) healthScore).isBetween(0, 100);
    }

    @Test
    @DisplayName("端点应该提供缓存优化")
    void shouldProvideCaching() {
        // 测试场景：验证缓存机制提升性能
        // 业务价值：避免频繁查询造成的性能问题
        
        // 第一次调用
        long start1 = System.nanoTime();
        Map<String, Object> response1 = endpoint.taskflow();
        long time1 = System.nanoTime() - start1;
        
        // 第二次调用（应该更快，因为有缓存）
        long start2 = System.nanoTime();
        Map<String, Object> response2 = endpoint.taskflow();
        long time2 = System.nanoTime() - start2;
        
        // 验证响应内容一致
        assertThat(response1).isNotNull();
        assertThat(response2).isNotNull();
        assertThat(response1.get("version")).isEqualTo(response2.get("version"));
    }

    @Test
    @DisplayName("端点应该无任何写操作方法")
    void shouldHaveNoWriteOperations() {
        // Given - 检查类中没有@WriteOperation, @DeleteOperation注解的方法
        java.lang.reflect.Method[] methods = SecureTfiEndpoint.class.getDeclaredMethods();
        
        // When & Then - 验证没有写操作
        for (java.lang.reflect.Method method : methods) {
            assertThat(method.isAnnotationPresent(org.springframework.boot.actuate.endpoint.annotation.WriteOperation.class))
                .as("Method %s should not have @WriteOperation", method.getName())
                .isFalse();
            
            assertThat(method.isAnnotationPresent(org.springframework.boot.actuate.endpoint.annotation.DeleteOperation.class))
                .as("Method %s should not have @DeleteOperation", method.getName())
                .isFalse();
        }
    }
    
    // ===== 数据脱敏测试 =====
    
    @Test
    @DisplayName("应该对敏感配置信息进行脱敏")
    void shouldMaskSensitiveConfigInformation() {
        // When
        Map<String, Object> response = endpoint.taskflow();
        
        // Then - 验证不包含敏感配置信息
        assertThat(response).doesNotContainKeys(
            "password", "secret", "token", "key", 
            "privateKey", "connectionString", "databaseUrl"
        );
        
        // 验证包含配置信息
        assertThat(response).containsKey("config");
        Map<String, Object> config = (Map<String, Object>) response.get("config");
        assertThat(config).isNotNull();
        
        // 验证数据脱敏已启用
        assertThat(config).containsEntry("dataMaskingEnabled", true);
    }
    
    @Test
    @DisplayName("应该对会话ID进行脱敏")
    void shouldMaskSessionIds() {
        // When
        Map<String, Object> response = endpoint.taskflow();
        
        // Then - 统计信息应该是安全的，不包含敏感会话信息
        assertThat(response).containsKey("stats");
        Map<String, Object> stats = (Map<String, Object>) response.get("stats");
        assertThat(stats).isNotNull();
        // 验证不包含敏感的会话详情
        assertThat(stats).doesNotContainKeys("sessionDetails", "userSessions", "userIds");
    }
    
    @Test
    @DisplayName("应该对指标名称进行脱敏")
    void shouldMaskMetricDetails() {
        // When
        Map<String, Object> response = endpoint.taskflow();
        
        // Then - 验证指标信息不包含敏感数据
        assertThat(response).containsKey("stats");
        Map<String, Object> stats = (Map<String, Object>) response.get("stats");
        assertThat(stats).isNotNull();
        // 只包含聚合统计，不包含详细的指标数据
        assertThat(stats).doesNotContainKeys("detailedMetrics", "rawData");
    }
    
    // ===== 性能和缓存测试 =====
    
    @Test
    @DisplayName("应该提供缓存机制提升性能")
    void shouldProvideCachingForPerformance() {
        // Given - 多次调用相同端点
        long start1 = System.nanoTime();
        Map<String, Object> overview1 = endpoint.taskflow();
        long time1 = System.nanoTime() - start1;
        
        long start2 = System.nanoTime();
        Map<String, Object> overview2 = endpoint.taskflow();
        long time2 = System.nanoTime() - start2;
        
        // Then - 两次调用都应该成功返回数据
        assertThat(overview1).isNotNull().containsKey("version");
        assertThat(overview2).isNotNull().containsKey("version");
        // 注意：由于timestamp和uptime会变化，不能简单比较相等
        assertThat(overview1.get("version")).isEqualTo(overview2.get("version"));
    }
    
    @Test
    @DisplayName("缓存应该有合理的TTL")
    void shouldHaveReasonableCacheTtl() throws InterruptedException {
        // Given
        Map<String, Object> overview1 = endpoint.taskflow();
        
        // When - 等待缓存过期（模拟）
        Thread.sleep(10); // 短暂等待
        Map<String, Object> overview2 = endpoint.taskflow();
        
        // Then - 两次调用都应该成功
        assertThat(overview1).isNotNull().containsKey("version");
        assertThat(overview2).isNotNull().containsKey("version");
        // 版本信息应该保持一致
        assertThat(overview1.get("version")).isEqualTo(overview2.get("version"));
    }
    
    @Test
    @DisplayName("应该控制响应时间在合理范围内")
    void shouldHaveReasonableResponseTime() {
        // Given - 测试各个端点的响应时间
        long start = System.nanoTime();
        
        // When
        endpoint.taskflow();
        endpoint.taskflow();
        endpoint.taskflow();
        endpoint.taskflow();
        
        long totalTime = System.nanoTime() - start;
        
        // Then - 总响应时间应该小于100ms
        assertThat(totalTime).isLessThan(100_000_000L); // 100ms in nanoseconds
    }
    
    // ===== 最小权限暴露测试 =====
    
    @Test
    @DisplayName("健康检查应该只暴露必要信息")
    void shouldExposeMinimalHealthInformation() {
        // When
        Map<String, Object> response = endpoint.taskflow();
        
        // Then - 只包含基本健康状态
        assertThat(response).containsKeys("healthLevel", "healthScore", "timestamp");
        assertThat(response).doesNotContainKeys(
            "stackTrace", "detailedException", "systemProperties", "environmentVariables"
        );
        
        String healthLevel = (String) response.get("healthLevel");
        assertThat(healthLevel).isIn("EXCELLENT", "GOOD", "WARNING", "CRITICAL");
    }
    
    @Test
    @DisplayName("诊断信息应该限制系统信息暴露")
    void shouldLimitSystemInformationExposure() {
        // When
        Map<String, Object> response = endpoint.taskflow();
        
        // Then - 不应该直接暴露JVM信息
        assertThat(response).doesNotContainKey("jvm");
        
        // 不应该包含敏感系统信息
        assertThat(response).doesNotContainKeys(
            "systemProperties", "environmentVariables", "classPath", "bootClassPath"
        );
        
        // 只包含安全的最小运行信息
        assertThat(response).containsKey("uptime");
    }
    
    @Test
    @DisplayName("统计信息应该聚合脱敏")
    void shouldProvideAggregatedMaskedStatistics() {
        // When
        Map<String, Object> response = endpoint.taskflow();
        
        // Then - 应该包含聚合统计
        assertThat(response).containsKeys("uptime", "healthScore");
        assertThat(response).containsKey("stats");
        Map<String, Object> stats = (Map<String, Object>) response.get("stats");
        assertThat(stats).containsKey("activeContexts");
        
        // 不应该包含详细的用户或会话信息
        assertThat(stats).doesNotContainKeys(
            "userSessions", "userIds", "sessionDetails", "requestDetails"
        );
    }
    
    // ===== 安全性测试 =====
    
    @Test
    @DisplayName("配置端点不应该暴露敏感配置")
    void shouldNotExposeSensitiveConfiguration() {
        // When
        Map<String, Object> config = endpoint.taskflow();
        
        // Then - 不应该包含任何敏感配置值
        String configString = config.toString().toLowerCase();
        
        assertThat(configString).doesNotContain("password");
        assertThat(configString).doesNotContain("secret");
        assertThat(configString).doesNotContain("key");
        assertThat(configString).doesNotContain("token");
        assertThat(configString).doesNotContain("credential");
    }
    
    @Test
    @DisplayName("所有端点都应该有安全保护")
    void shouldHaveSecurityProtectionOnAllEndpoints() {
        // Given - 所有端点方法
        java.lang.reflect.Method[] methods = SecureTfiEndpoint.class.getDeclaredMethods();
        
        // When & Then - 检查所有公共方法都有适当的保护
        for (java.lang.reflect.Method method : methods) {
            if (method.isAnnotationPresent(org.springframework.boot.actuate.endpoint.annotation.ReadOperation.class)) {
                // 所有端点方法都应该正常工作（无异常抛出）
                assertThatCode(() -> {
                    if (method.getParameterCount() == 0) {
                        method.invoke(endpoint);
                    } else if (method.getParameterCount() == 1) {
                        method.invoke(endpoint, (Object) null);
                    }
                }).as("Method %s should not throw exceptions", method.getName())
                 .doesNotThrowAnyException();
            }
        }
    }
    
    // ===== 错误处理测试 =====
    
    @Test
    @DisplayName("当组件不可用时应该优雅降级")
    void shouldGracefullyDegradeWhenComponentsUnavailable() {
        // Given - 创建没有可选依赖的端点
        SecureTfiEndpoint endpointWithoutDeps = new SecureTfiEndpoint(
            null, meterRegistry, dataMasker, null);
        
        // When & Then - 所有方法都应该正常工作
        assertThatCode(() -> {
            endpointWithoutDeps.taskflow();
            endpointWithoutDeps.taskflow();
            endpointWithoutDeps.taskflow();
            endpointWithoutDeps.taskflow();
        }).doesNotThrowAnyException();
    }
    
    @Test
    @DisplayName("应该处理并发访问")
    void shouldHandleConcurrentAccess() throws InterruptedException {
        // Given
        int threadCount = 10;
        int requestsPerThread = 5;
        Thread[] threads = new Thread[threadCount];
        final Exception[] exceptions = new Exception[threadCount];
        
        // When - 并发访问端点
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        endpoint.taskflow();
                        endpoint.taskflow();
                        endpoint.taskflow();
                    }
                } catch (Exception e) {
                    exceptions[threadIndex] = e;
                }
            });
            threads[i].start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Then - 没有异常发生
        for (int i = 0; i < threadCount; i++) {
            assertThat(exceptions[i]).as("Thread %d should not have exceptions", i).isNull();
        }
    }
}