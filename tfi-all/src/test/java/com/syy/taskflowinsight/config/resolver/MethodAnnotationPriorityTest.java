package com.syy.taskflowinsight.config.resolver;

import com.syy.taskflowinsight.annotation.TfiTask;
import com.syy.taskflowinsight.aspect.TfiAnnotationAspect;
import com.syy.taskflowinsight.masking.UnifiedDataMasker;
import com.syy.taskflowinsight.spel.SafeSpELEvaluator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static com.syy.taskflowinsight.config.resolver.ConfigDefaults.Keys;

/**
 * 方法注解层优先级测试
 * 
 * 验证@TfiTask注解配置覆盖Spring配置层
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 */
@SpringBootTest
@TestPropertySource(properties = {
    "tfi.config.resolver.enabled=true",
    "tfi.change-tracking.snapshot.max-depth=5",     // Spring层: 5
    "tfi.change-tracking.snapshot.time-budget-ms=500" // Spring层: 500
})
class MethodAnnotationPriorityTest {
    
    @Autowired
    private Environment environment;
    
    private ConfigurationResolverImpl resolver;
    private TfiAnnotationAspect aspect;
    
    @Mock private ProceedingJoinPoint pjp;
    @Mock private MethodSignature signature;
    @Mock private SafeSpELEvaluator spelEvaluator;
    @Mock private UnifiedDataMasker dataMasker;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        resolver = new ConfigurationResolverImpl(environment, new ConfigMigrationMapper());
        aspect = new TfiAnnotationAspect(spelEvaluator, dataMasker, new SimpleMeterRegistry(), resolver);
    }
    
    @Test
    @DisplayName("测试Method Annotation层覆盖Spring配置层")
    void testMethodAnnotationOverridesSpringConfig() throws Throwable {
        // 模拟@TfiTask注解
        TfiTask tfiTask = createMockTfiTask(15, 2000L); // 注解层: maxDepth=15, timeBudget=2000
        
        // 模拟方法签名和连接点
        setupMockProceedingJoinPoint();
        
        // 在切面执行前验证Spring配置值
        assertThat(resolver.resolve(Keys.MAX_DEPTH, Integer.class, null)).isEqualTo(5);
        assertThat(resolver.resolve(Keys.TIME_BUDGET_MS, Long.class, null)).isEqualTo(500L);
        
        // 执行切面（会设置方法层配置）
        aspect.around(pjp, tfiTask);
        
        // 验证方法层配置已生效（覆盖Spring配置）
        assertThat(resolver.resolve(Keys.MAX_DEPTH, Integer.class, null)).isEqualTo(15);
        assertThat(resolver.resolve(Keys.TIME_BUDGET_MS, Long.class, null)).isEqualTo(2000L);
        
        // 验证优先级是METHOD_ANNOTATION
        assertThat(resolver.getEffectivePriority(Keys.MAX_DEPTH))
            .isEqualTo(ConfigurationResolver.ConfigPriority.METHOD_ANNOTATION);
        assertThat(resolver.getEffectivePriority(Keys.TIME_BUDGET_MS))
            .isEqualTo(ConfigurationResolver.ConfigPriority.METHOD_ANNOTATION);
    }
    
    @Test
    @DisplayName("测试注解默认值不覆盖Spring配置")
    void testAnnotationDefaultsDoNotOverride() throws Throwable {
        // 模拟@TfiTask注解使用默认值
        TfiTask tfiTask = createMockTfiTask(10, 1000L); // 使用默认值
        
        setupMockProceedingJoinPoint();
        
        // 执行切面（默认值不会设置方法层配置）
        aspect.around(pjp, tfiTask);
        
        // 验证Spring配置仍然生效（没有被默认值覆盖）
        assertThat(resolver.resolve(Keys.MAX_DEPTH, Integer.class, null)).isEqualTo(5);
        assertThat(resolver.resolve(Keys.TIME_BUDGET_MS, Long.class, null)).isEqualTo(500L);
        
        // 优先级应该仍是SPRING_CONFIG
        assertThat(resolver.getEffectivePriority(Keys.MAX_DEPTH))
            .isEqualTo(ConfigurationResolver.ConfigPriority.SPRING_CONFIG);
    }
    
    @Test
    @DisplayName("测试配置源追踪包含方法注解层")
    void testConfigSourceIncludesMethodAnnotation() throws Throwable {
        TfiTask tfiTask = createMockTfiTask(20, 3000L);
        setupMockProceedingJoinPoint();
        
        // 执行切面设置方法层配置
        aspect.around(pjp, tfiTask);
        
        // 获取配置源
        var sources = resolver.getConfigSources(Keys.MAX_DEPTH);
        
        // 验证包含多个层级
        assertThat(sources).containsKeys(
            ConfigurationResolver.ConfigPriority.METHOD_ANNOTATION,
            ConfigurationResolver.ConfigPriority.SPRING_CONFIG,
            ConfigurationResolver.ConfigPriority.DEFAULT_VALUE
        );
        
        // 验证方法注解层的值
        assertThat(sources.get(ConfigurationResolver.ConfigPriority.METHOD_ANNOTATION).value())
            .isEqualTo(20);
    }
    
    private TfiTask createMockTfiTask(int maxDepth, long timeBudgetMs) {
        TfiTask tfiTask = mock(TfiTask.class);
        when(tfiTask.maxDepth()).thenReturn(maxDepth);
        when(tfiTask.timeBudgetMs()).thenReturn(timeBudgetMs);
        when(tfiTask.samplingRate()).thenReturn(1.0); // 100%采样
        when(tfiTask.condition()).thenReturn(""); // 无条件
        when(tfiTask.logArgs()).thenReturn(false);
        when(tfiTask.logResult()).thenReturn(false);
        when(tfiTask.logException()).thenReturn(false);
        when(tfiTask.deepTracking()).thenReturn(false);
        return tfiTask;
    }
    
    private void setupMockProceedingJoinPoint() throws Throwable {
        when(signature.getName()).thenReturn("testMethod");
        when(signature.getDeclaringTypeName()).thenReturn("TestClass");
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[]{});
        when(pjp.proceed()).thenReturn("result");
        
        // 模拟方法
        Method mockMethod = this.getClass().getDeclaredMethod("mockTestMethod");
        when(signature.getMethod()).thenReturn(mockMethod);
    }
    
    // 用于测试的虚拟方法
    private void mockTestMethod() {}
}