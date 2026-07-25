package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.TaskFlowInsightApplication;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.aspect.TfiTaskDeepTrackingDelegate;
import com.syy.taskflowinsight.compare.spring.TfiCompareProperties;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 官方 default/dev/prod 配置的真实启动与结构化 YAML 合同。
 */
class CompareProfilesStartupContractTests {

    /** 未覆盖时由 Compare Policy 提供的单值字符预算。 */
    private static final int DEFAULT_MAX_VALUE_CHARS = 4096;

    /** dev/prod 官方 profile 显式采用的单值字符预算。 */
    private static final int OVERRIDDEN_MAX_VALUE_CHARS = 8192;

    @Test
    void defaultProfileStartsWithCanonicalCompareConfiguration() {
        assertProfile("default", true, true, true, DEFAULT_MAX_VALUE_CHARS);
    }

    @Test
    void devProfileStartsWithCanonicalCompareConfiguration() {
        assertProfile("dev", true, true, true, OVERRIDDEN_MAX_VALUE_CHARS);
    }

    @Test
    void prodProfileStartsWithCanonicalCompareConfiguration() {
        assertProfile("prod", false, false, false, OVERRIDDEN_MAX_VALUE_CHARS);
    }

    @Test
    void officialYamlResourcesContainNoRetiredOrOwnerlessKeys() throws Exception {
        Set<String> names = new LinkedHashSet<>();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String resource : Set.of(
                "application.yml", "application-dev.yml", "application-prod.yml")) {
            loader.load(resource, new ClassPathResource(resource)).stream()
                    .filter(EnumerablePropertySource.class::isInstance)
                    .map(EnumerablePropertySource.class::cast)
                    .flatMap(source -> Arrays.stream(source.getPropertyNames()))
                    .forEach(names::add);
        }

        assertThat(names).noneMatch(name -> name.equals("tfi.enabled"));
        assertThat(names).noneMatch(name -> name.startsWith("tfi.change-tracking."));
        assertThat(names).noneMatch(name -> name.endsWith("cleanup-interval-minutes"));
        assertThat(names).noneMatch(name -> name.endsWith("max-cached-classes"));
        assertThat(names).noneMatch(name -> name.endsWith("debug-logging"));
    }

    private static void assertProfile(
            String profile,
            boolean annotationEnabled,
            boolean compareEnabled,
            boolean trackingEnabled,
            int maxResultValueChars) {
        boolean staticEnabled = TFI.isEnabled();
        boolean staticTrackingEnabled = TFI.isChangeTrackingEnabled();
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                TaskFlowInsightApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.profiles.active=must-not-win")
                .logStartupInfo(false)
                .run("--spring.profiles.active=" + profile)) {
            ConfigurableEnvironment environment = context.getEnvironment();
            TfiCompareProperties properties = context.getBean(TfiCompareProperties.class);
            ComparePolicy policy = context.getBean(ComparePolicy.class);

            assertThat(environment.getActiveProfiles()).containsExactly(profile);
            assertThat(environment.getProperty("tfi.annotation.enabled", Boolean.class))
                    .isEqualTo(annotationEnabled);
            assertThat(properties.enabled()).isEqualTo(compareEnabled);
            assertThat(properties.tracking().enabled()).isEqualTo(trackingEnabled);
            assertThat(policy.enabled()).isEqualTo(compareEnabled);
            assertThat(policy.maxResultValueChars()).isEqualTo(maxResultValueChars);
            assertThat(context.getBeanNamesForType(TfiTaskDeepTrackingDelegate.class))
                    .hasSize(trackingEnabled ? 1 : 0);
        }
        assertThat(TFI.isEnabled()).isEqualTo(staticEnabled);
        assertThat(TFI.isChangeTrackingEnabled()).isEqualTo(staticTrackingEnabled);
    }
}
