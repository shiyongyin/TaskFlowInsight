package com.syy.taskflowinsight.config;

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
        assertThat(props.getMaxAgeMillis()).isEqualTo(3_600_000L);
        assertThat(props.isLeakDetectionEnabled()).isFalse();
        assertThat(props.getLeakDetectionIntervalMillis()).isEqualTo(60_000L);
        assertThat(props.isCleanupEnabled()).isFalse();
        assertThat(props.getCleanupIntervalMillis()).isEqualTo(60_000L);
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

        props.setCleanupEnabled(true);
        assertThat(props.isCleanupEnabled()).isTrue();

        props.setCleanupIntervalMillis(15000L);
        assertThat(props.getCleanupIntervalMillis()).isEqualTo(15000L);
    }
}
