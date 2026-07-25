package com.syy.taskflowinsight.config;

import com.syy.taskflowinsight.context.ContextManagerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TfiContextProperties} 单元测试.
 *
 * <p>覆盖默认值、getter/setter 和属性绑定。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
@DisplayName("TfiContextProperties 配置属性测试")
class TfiContextPropertiesTest {

    @Test
    @DisplayName("默认值正确")
    void defaultValues() {
        TfiContextProperties props = new TfiContextProperties();
        ContextManagerConfig defaults = ContextManagerConfig.defaults();

        assertThat(props.getMaxAgeMillis()).isEqualTo(defaults.timeoutMillis());
        assertThat(props.isLeakDetectionEnabled()).isEqualTo(defaults.leakDetectionEnabled());
        assertThat(props.getLeakDetectionIntervalMillis())
                .isEqualTo(defaults.leakDetectionIntervalMillis());
    }

    @Test
    @DisplayName("setter/getter 正确工作")
    void setterGetter() {
        TfiContextProperties props = new TfiContextProperties();

        props.setMaxAgeMillis(1800000L);
        assertThat(props.getMaxAgeMillis()).isEqualTo(1800000L);

        props.setLeakDetectionEnabled(true);
        assertThat(props.isLeakDetectionEnabled()).isTrue();

        props.setLeakDetectionIntervalMillis(30000L);
        assertThat(props.getLeakDetectionIntervalMillis()).isEqualTo(30000L);

    }
}
