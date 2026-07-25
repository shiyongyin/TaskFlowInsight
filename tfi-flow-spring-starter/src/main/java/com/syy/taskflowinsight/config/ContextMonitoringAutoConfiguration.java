package com.syy.taskflowinsight.config;

import com.syy.taskflowinsight.aspect.TfiAnnotationAspect;
import com.syy.taskflowinsight.aspect.TfiTaskDeepTrackingDelegate;
import com.syy.taskflowinsight.context.ContextManagerConfig;
import com.syy.taskflowinsight.context.SafeContextManager;
import com.syy.taskflowinsight.masking.UnifiedDataMasker;
import com.syy.taskflowinsight.spel.SafeSpELEvaluator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * TFI Flow 上下文监控自动配置.
 *
 * <p>在 Spring Boot 应用启动时，把 {@link TfiContextProperties} 作为一个完整配置应用到
 * {@link SafeContextManager}，避免 Spring 侧重新引入第二个调度或配置 owner。
 *
 * <h2>生效条件</h2>
 * <ul>
 *   <li>classpath 中存在 {@link SafeContextManager}（即 {@code tfi-flow-core} 已引入）</li>
 * </ul>
 *
 * <h2>配置前缀</h2>
 * <ul>
 *   <li>{@code tfi.context.*} — 上下文管理参数</li>
 *   <li>{@code tfi.security.*} — 安全策略参数</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 * @see TfiContextProperties
 * @see TfiSecurityProperties
 */
@AutoConfiguration
@ConditionalOnClass(SafeContextManager.class)
@EnableConfigurationProperties({TfiContextProperties.class, TfiSecurityProperties.class})
public class ContextMonitoringAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ContextMonitoringAutoConfiguration.class);

    private final TfiContextProperties properties;

    /**
     * 构造自动配置实例.
     *
     * @param properties 上下文配置属性
     */
    public ContextMonitoringAutoConfiguration(TfiContextProperties properties) {
        this.properties = properties;
    }

    /**
     * 提供starter-owned SpEL边界，使注解能力不依赖宿主component scan。
     *
     * @param beanFactory 当前上下文 bean 目录
     * @return 唯一安全表达式求值器
     */
    @Bean
    @ConditionalOnMissingBean(search = SearchStrategy.CURRENT)
    public SafeSpELEvaluator safeSpELEvaluator(final ListableBeanFactory beanFactory) {
        final TfiSecurityProperties securityProperties = requireLocalBean(
                beanFactory, TfiSecurityProperties.class);
        return new SafeSpELEvaluator(securityProperties);
    }

    /**
     * 提供与注解日志同域的脱敏器，同时允许宿主显式替换实现。
     *
     * @param beanFactory 当前上下文 bean 目录
     * @return 唯一统一脱敏器
     */
    @Bean
    @ConditionalOnMissingBean(search = SearchStrategy.CURRENT)
    public UnifiedDataMasker unifiedDataMasker(final ListableBeanFactory beanFactory) {
        final TfiSecurityProperties securityProperties = requireLocalBean(
                beanFactory, TfiSecurityProperties.class);
        return new UnifiedDataMasker(securityProperties);
    }

    /**
     * 显式装配Flow唯一advice，避免component scan决定hook是否生效。
     *
     * @param beanFactory 当前上下文 bean 目录
     * @return 当前上下文唯一的TfiTask advice
     */
    @Bean
    @ConditionalOnMissingBean(search = SearchStrategy.CURRENT)
    @ConditionalOnProperty(name = "tfi.annotation.enabled", havingValue = "true", matchIfMissing = false)
    public TfiAnnotationAspect tfiAnnotationAspect(
            final ListableBeanFactory beanFactory) {
        final SafeSpELEvaluator spelEvaluator = requireLocalBean(
                beanFactory, SafeSpELEvaluator.class);
        final UnifiedDataMasker dataMasker = requireLocalBean(
                beanFactory, UnifiedDataMasker.class);
        return new TfiAnnotationAspect(
                spelEvaluator,
                dataMasker,
                localBeanProvider(beanFactory, TfiTaskDeepTrackingDelegate.class));
    }

    private static <T> T requireLocalBean(
            final ListableBeanFactory beanFactory,
            final Class<T> type) {
        if (beanFactory.getBeanNamesForType(type, true, false).length == 0) {
            throw new IllegalStateException("Missing local bean: " + type.getName());
        }
        return beanFactory.getBean(type);
    }

    private static <T> ObjectProvider<T> localBeanProvider(
            final ListableBeanFactory beanFactory,
            final Class<T> type) {
        final String[] localNames = beanFactory.getBeanNamesForType(type, true, false);
        return new ObjectProvider<>() {
            /**
             * 只枚举创建 provider 时冻结的本地 bean 名称，明确排除 ancestor 候选。
             *
             * @return 当前 BeanFactory 本地候选的惰性流
             */
            @Override
            public Stream<T> stream() {
                return Arrays.stream(localNames)
                        .map(name -> beanFactory.getBean(name, type));
            }
        };
    }

    /**
     * 在 Bean 初始化后，将配置属性应用到 Flow Core 的上下文管理器.
     *
     * <p>配置采用 best-effort 语义，失败不阻断应用启动；manager 保留最后一次成功应用的状态，
     * 因此日志不能声称已回退到 defaults。
     */
    @PostConstruct
    public void applyMonitoringProperties() {
        try {
            applyToSafeContextManager();

            logger.info("Applied tfi.context properties: maxAgeMillis={} leakDetectionEnabled={}"
                            + " leakDetectionIntervalMillis={}",
                    properties.getMaxAgeMillis(),
                    properties.isLeakDetectionEnabled(),
                    properties.getLeakDetectionIntervalMillis());
        } catch (Exception failure) {
            logger.warn("Failed to apply tfi.context properties; manager retains its last successful state",
                    failure);
        }
    }

    /**
     * 将配置应用到 {@link SafeContextManager}.
     */
    private void applyToSafeContextManager() {
        SafeContextManager.getInstance().apply(contextManagerConfig());
    }

    /**
     * 构造一次完整配置，避免 Starter 通过多个 setter 向 Core 发布中间状态.
     *
     * @return 已按 Core 默认值清洗的不可变配置
     */
    ContextManagerConfig contextManagerConfig() {
        ContextManagerConfig defaults = ContextManagerConfig.defaults();
        return new ContextManagerConfig(
                sanitizeMillis(properties.getMaxAgeMillis(), defaults.timeoutMillis()),
                properties.isLeakDetectionEnabled(),
                sanitizeMillis(
                        properties.getLeakDetectionIntervalMillis(),
                        defaults.leakDetectionIntervalMillis()));
    }

    /**
     * 校验毫秒值，非法值（&le; 0）回退到默认值.
     *
     * @param configured 用户配置值
     * @param fallback   默认回退值
     * @return 校验后的有效值
     */
    static long sanitizeMillis(long configured, long fallback) {
        return configured > 0 ? configured : fallback;
    }
}
