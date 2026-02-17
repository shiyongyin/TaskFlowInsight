package com.syy.taskflowinsight.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TfiSecurityProperties} 单元测试.
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
@DisplayName("TfiSecurityProperties 安全配置属性测试")
class TfiSecurityPropertiesTest {

    @Test
    @DisplayName("默认值正确")
    void defaultValues() {
        TfiSecurityProperties props = new TfiSecurityProperties();
        assertThat(props.getSpelMaxLength()).isEqualTo(1000);
        assertThat(props.getSpelMaxNesting()).isEqualTo(10);
        assertThat(props.getSpelCacheMaxSize()).isEqualTo(500);
        assertThat(props.getSpelBlockedPatterns()).contains("class", "runtime", "exec");
        assertThat(props.getSensitiveKeywords()).contains("password", "token", "secret");
    }

    @Test
    @DisplayName("setter/getter 正确工作")
    void setterGetter() {
        TfiSecurityProperties props = new TfiSecurityProperties();

        props.setSpelMaxLength(500);
        assertThat(props.getSpelMaxLength()).isEqualTo(500);

        props.setSpelMaxNesting(5);
        assertThat(props.getSpelMaxNesting()).isEqualTo(5);

        props.setSpelCacheMaxSize(100);
        assertThat(props.getSpelCacheMaxSize()).isEqualTo(100);

        Set<String> customPatterns = Set.of("custom1", "custom2");
        props.setSpelBlockedPatterns(customPatterns);
        assertThat(props.getSpelBlockedPatterns()).containsExactlyInAnyOrder("custom1", "custom2");

        Set<String> customKeywords = Set.of("mysecret");
        props.setSensitiveKeywords(customKeywords);
        assertThat(props.getSensitiveKeywords()).containsExactly("mysecret");
    }

    @Test
    @DisplayName("默认敏感关键词包含 19 个")
    void defaultSensitiveKeywords_contains19() {
        TfiSecurityProperties props = new TfiSecurityProperties();
        assertThat(props.getSensitiveKeywords()).hasSize(19);
    }

    @Test
    @DisplayName("默认黑名单包含 15 个模式")
    void defaultBlockedPatterns_contains15() {
        TfiSecurityProperties props = new TfiSecurityProperties();
        assertThat(props.getSpelBlockedPatterns()).hasSize(15);
    }
}
