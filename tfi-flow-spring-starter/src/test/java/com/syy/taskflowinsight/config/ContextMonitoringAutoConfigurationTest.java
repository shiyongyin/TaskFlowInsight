package com.syy.taskflowinsight.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ContextMonitoringAutoConfiguration} 单元测试.
 *
 * <p>覆盖 sanitizeMillis 边界值和配置应用逻辑。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
@DisplayName("ContextMonitoringAutoConfiguration 自动配置测试")
class ContextMonitoringAutoConfigurationTest {

    @Nested
    @DisplayName("sanitizeMillis - 毫秒值校验")
    class SanitizeMillisTests {

        @Test
        @DisplayName("正常正数值直接返回")
        void positiveValue_returnsAsIs() {
            assertThat(ContextMonitoringAutoConfiguration.sanitizeMillis(5000L, 60000L))
                    .isEqualTo(5000L);
        }

        @Test
        @DisplayName("零值回退到默认值")
        void zeroValue_returnsFallback() {
            assertThat(ContextMonitoringAutoConfiguration.sanitizeMillis(0L, 60000L))
                    .isEqualTo(60000L);
        }

        @Test
        @DisplayName("负值回退到默认值")
        void negativeValue_returnsFallback() {
            assertThat(ContextMonitoringAutoConfiguration.sanitizeMillis(-1L, 60000L))
                    .isEqualTo(60000L);
        }

        @Test
        @DisplayName("Long.MIN_VALUE 回退到默认值")
        void minValue_returnsFallback() {
            assertThat(ContextMonitoringAutoConfiguration.sanitizeMillis(Long.MIN_VALUE, 3600000L))
                    .isEqualTo(3600000L);
        }

        @Test
        @DisplayName("1 毫秒（最小正值）直接返回")
        void oneMillis_returnsAsIs() {
            assertThat(ContextMonitoringAutoConfiguration.sanitizeMillis(1L, 60000L))
                    .isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("applyMonitoringProperties - 配置应用")
    class ApplyPropertiesTests {

        @Test
        @DisplayName("默认配置不抛异常")
        void defaultProperties_noException() {
            TfiContextProperties props = new TfiContextProperties();
            ContextMonitoringAutoConfiguration config =
                    new ContextMonitoringAutoConfiguration(props);

            // 应不抛异常（即使 SafeContextManager 在测试环境的行为可能不同）
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(config::applyMonitoringProperties);
        }

        @Test
        @DisplayName("自定义配置正常应用")
        void customProperties_appliedSuccessfully() {
            TfiContextProperties props = new TfiContextProperties();
            props.setMaxAgeMillis(1800000L);
            props.setLeakDetectionEnabled(true);
            props.setCleanupEnabled(true);
            props.setCleanupIntervalMillis(30000L);

            ContextMonitoringAutoConfiguration config =
                    new ContextMonitoringAutoConfiguration(props);
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(config::applyMonitoringProperties);
        }
    }
}
