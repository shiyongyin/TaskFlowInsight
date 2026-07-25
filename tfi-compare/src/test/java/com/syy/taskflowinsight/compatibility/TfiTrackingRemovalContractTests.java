package com.syy.taskflowinsight.compatibility;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 声明式tracking旧入口的删除合同。
 *
 * <p>这里检查生产源码而不是类加载结果，因为增量编译目录可能暂时保留已删除class；
 * 任务卡要求的是仓库只保留一个真实入口，不能让陈旧构建产物掩盖第二条源码路径。</p>
 */
class TfiTrackingRemovalContractTests {

    @Test
    void retiredTfiTrackTypeAndMembersHaveNoProductionReader() throws IOException {
        Path root = CompareApiInventory.repositoryRoot();

        assertThat(root.resolve(
                "tfi-compare/src/main/java/com/syy/taskflowinsight/annotation/TfiTrack.java"))
                .doesNotExist();
        assertThat(productionSources(root))
                .noneMatch(source -> source.content().contains("TfiTrack"));
    }

    @Test
    void deepTrackingUsesOnlyFlowOwnedAdvice() throws IOException {
        Path root = CompareApiInventory.repositoryRoot();
        List<ProductionSource> sources = productionSources(root);
        List<String> taskAdviceOwners = sources.stream()
                .filter(source -> source.content().contains("@Around(\"@annotation(tfiTask)\")"))
                .map(ProductionSource::relativePath)
                .toList();

        assertThat(root.resolve(
                "tfi-compare/src/main/java/com/syy/taskflowinsight/aspect/TfiDeepTrackingAspect.java"))
                .doesNotExist();
        assertThat(sources.stream()
                .filter(source -> source.relativePath().startsWith("tfi-compare/src/main/java/"))
                .map(ProductionSource::content))
                .noneMatch(content -> content.contains("@Aspect")
                        || content.contains("ProceedingJoinPoint"));
        assertThat(taskAdviceOwners).containsExactly(
                "tfi-flow-spring-starter/src/main/java/com/syy/taskflowinsight/aspect/TfiAnnotationAspect.java");
    }

    @Test
    void retiredGlobalStoresAndPseudoHealthOwnersAreAbsent() throws IOException {
        Path root = CompareApiInventory.repositoryRoot();
        List<Path> removedTypes = List.of(
                root.resolve("tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/ChangeTracker.java"),
                root.resolve("tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/SessionAwareChangeTracker.java"),
                root.resolve("tfi-compare/src/main/java/com/syy/taskflowinsight/actuator/TfiCompareHealthIndicator.java"),
                root.resolve("tfi-compare/src/main/java/com/syy/taskflowinsight/actuator/TfiCompareHealthAutoConfiguration.java"));

        assertThat(removedTypes).allMatch(path -> !Files.exists(path));
        assertThat(productionSources(root).stream().map(ProductionSource::content))
                .noneMatch(content -> content.contains("SessionAwareChangeTracker")
                        || content.contains("ChangeTracker")
                        || content.contains("TfiCompareHealthIndicator")
                        || content.contains("TfiCompareHealthAutoConfiguration")
                        || content.contains("class CleanupConfiguration")
                        || content.contains("class ActuatorConfiguration"));
    }

    @Test
    void implicitFacadeTrackingMethodsAreRemoved() throws IOException {
        Path root = CompareApiInventory.repositoryRoot();
        String facade = Files.readString(root.resolve(
                "tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java"));

        assertThat(facade)
                .contains("public static void withTracked(")
                .doesNotContain(
                        "public static void track(",
                        "public static void trackAll(",
                        "public static void trackDeep(",
                        "public static List<ChangeRecord> getChanges(",
                        "public static List<ChangeRecord> getAllChanges(",
                        "public static void clearTracking(",
                        "public static void clearAllTracking(",
                        "public static void startTracking(",
                        "public static void recordChange(");
    }

    private static List<ProductionSource> productionSources(Path root) throws IOException {
        List<ProductionSource> sources = new ArrayList<>();
        for (String module : List.of(
                "tfi-compare", "tfi-flow-spring-starter", "tfi-ops-spring", "tfi-all", "tfi-examples")) {
            Path sourceRoot = root.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                for (Path path : paths.filter(Files::isRegularFile)
                        .filter(candidate -> candidate.toString().endsWith(".java"))
                        .toList()) {
                    sources.add(new ProductionSource(
                            root.relativize(path).toString().replace('\\', '/'),
                            Files.readString(path)));
                }
            }
        }
        return List.copyOf(sources);
    }

    /**
     * @param relativePath 用于断言owner的仓库相对路径
     * @param content 只在测试进程内保留的源码文本
     */
    private record ProductionSource(String relativePath, String content) {
    }
}
