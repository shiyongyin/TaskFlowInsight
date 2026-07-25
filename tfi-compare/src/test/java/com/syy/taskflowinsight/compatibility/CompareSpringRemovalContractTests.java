package com.syy.taskflowinsight.compatibility;

import com.syy.taskflowinsight.api.TfiListDiff;
import com.syy.taskflowinsight.api.TfiListDiffFacade;
import com.syy.taskflowinsight.api.builder.DiffBuilder;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.render.ChangeReportRenderer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compare 纯内核与 Spring composition root 的删除边界合同。
 *
 * <p>这里检查源码与资源路径，而不是依赖类加载失败；删除资源后增量构建目录可能残留旧 class，
 * 只有源码闭集才能证明仓库没有继续维护第二条配置路径。</p>
 */
class CompareSpringRemovalContractTests {

    @Test
    void retiredCompareSpringTypesAndResourcesAreAbsent() throws IOException {
        Path root = CompareApiInventory.repositoryRoot();
        Path compareJava = root.resolve("tfi-compare/src/main/java/com/syy/taskflowinsight");
        Path compareResources = root.resolve("tfi-compare/src/main/resources/META-INF");
        Path starterResources = root.resolve("tfi-compare-spring-starter/src/main/resources/META-INF");

        List<Path> retiredSources = List.of(
                compareJava.resolve("config/ChangeTrackingAutoConfiguration.java"),
                compareJava.resolve("config/ConcurrencyAutoConfiguration.java"),
                compareJava.resolve("config/ConcurrencyConfig.java"),
                compareJava.resolve("config/DeepTrackingAutoConfiguration.java"),
                compareJava.resolve("config/TfiConfig.java"),
                compareJava.resolve("config/TfiConfigValidator.java"),
                compareJava.resolve("config/TfiFeatureFlags.java"),
                compareJava.resolve("config/resolver/ConfigMigrationMapper.java"),
                compareJava.resolve("config/resolver/ConfigurationResolver.java"),
                compareJava.resolve("config/resolver/ConfigurationResolverImpl.java"));

        assertThat(retiredSources).allMatch(path -> !Files.exists(path));
        assertThat(Files.readString(compareJava.resolve("tracking/detector/DiffFacade.java")))
                .doesNotContain("ApplicationContext", "AppContextInjector");
        assertThat(compareResources.resolve("spring.factories")).doesNotExist();
        assertThat(compareResources.resolve(
                "spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"))
                .doesNotExist();
        assertThat(compareResources.resolve("additional-spring-configuration-metadata.json"))
                .doesNotExist();
        assertThat(starterResources.resolve("spring.factories")).doesNotExist();

        List<String> autoConfigurations = Files.readAllLines(starterResources.resolve(
                "spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"));
        assertThat(autoConfigurations).containsExactly(
                "com.syy.taskflowinsight.compare.spring.TfiCompareAutoConfiguration",
                "com.syy.taskflowinsight.compare.spring.TfiCompareTrackingPrerequisiteAutoConfiguration",
                "com.syy.taskflowinsight.compare.spring.TfiCompareTrackingAutoConfiguration");
    }

    @Test
    void retainedCompareFacadesExposeNoSpringLookupOrLegacyGraphConstructor() {
        assertThat(TfiListDiff.class.getInterfaces()).isEmpty();
        assertThat(Arrays.stream(TfiListDiff.class.getDeclaredMethods()).map(method -> method.getName()))
                .doesNotContain("setApplicationContext");
        assertThat(Arrays.stream(DiffBuilder.class.getDeclaredMethods()).map(method -> method.getName()))
                .doesNotContain("fromSpring");
        assertThatThrownBy(() -> TfiListDiffFacade.class.getConstructor(
                ListCompareExecutor.class, ChangeReportRenderer.class))
                .isInstanceOf(NoSuchMethodException.class);
    }
}
