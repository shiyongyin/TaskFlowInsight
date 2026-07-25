package com.syy.taskflowinsight.architecture;

import com.syy.taskflowinsight.api.TfiFlow;
import com.syy.taskflowinsight.internal.FlowConfigDefaults;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * tfi-flow-core 模块边界测试。
 *
 * <p>防止纯 Java flow 内核重新依赖 Spring、Micrometer、Caffeine 或 compare/tracking 包。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.1
 */
class FlowCoreArchitectureBoundaryTest {

    private static final String COPIED_CONFIG_DEFAULTS =
        "com.syy.taskflowinsight.internal.ConfigDefaults";
    private static final Set<String> FLOW_DEFAULT_FIELDS = Set.of(
        "MAX_EXPORT_DEPTH",
        "MAX_EXPORT_NODES",
        "MAX_EXPORT_PAYLOAD_ENTRIES",
        "MAX_EXPORT_TEXT_CHARS",
        "MAX_MESSAGES_PER_NODE");

    private static final List<String> FORBIDDEN_REFERENCES = List.of(
        "org.springframework.",
        "io.micrometer.",
        "com.github.benmanes.caffeine.",
        "com.syy.taskflowinsight.tracking.",
        "com.syy.taskflowinsight.config.resolver.",
        "com.syy.taskflowinsight.exporter.change."
    );

    @Test
    @DisplayName("tfi-flow-core 主源码不依赖 Spring/Micrometer/Caffeine/compare")
    void mainSourcesDoNotDependOnOuterModulesOrFrameworks() throws IOException {
        Path sourceRoot = resolveSourceRoot();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<String> violations = paths
                .filter(path -> path.toString().endsWith(".java"))
                .flatMap(FlowCoreArchitectureBoundaryTest::forbiddenReferencesIn)
                .toList();

            assertThat(violations).isEmpty();
        }
    }

    @Test
    @DisplayName("TfiFlow 不持有 Provider selected/fallback 状态")
    void tfiFlowDoesNotOwnProviderSelectionState() {
        Set<String> fieldNames = Stream.of(TfiFlow.class.getDeclaredFields())
            .map(Field::getName)
            .collect(java.util.stream.Collectors.toSet());
        Set<String> methodNames = Stream.of(TfiFlow.class.getDeclaredMethods())
            .map(Method::getName)
            .collect(java.util.stream.Collectors.toSet());

        assertThat(fieldNames).doesNotContain(
            "DEFAULT_FLOW_PROVIDER",
            "DEFAULT_EXPORT_PROVIDER",
            "cachedFlowProvider",
            "cachedExportProvider",
            "providerGeneration");
        assertThat(methodNames).doesNotContain("refreshProviderCacheIfStale", "lookupProvider");
    }

    @Test
    @DisplayName("Core 只发布 flow-owned defaults")
    void corePublishesOnlyFlowOwnedDefaults() {
        Path copiedSource = resolveSourceRoot().resolve(
            "com/syy/taskflowinsight/internal/ConfigDefaults.java");
        assertThat(copiedSource).doesNotExist();
        assertThatThrownBy(() -> Class.forName(COPIED_CONFIG_DEFAULTS))
            .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName(COPIED_CONFIG_DEFAULTS + "$Keys"))
            .isInstanceOf(ClassNotFoundException.class);

        Set<String> publicFields = Stream.of(FlowConfigDefaults.class.getDeclaredFields())
            .filter(field -> Modifier.isPublic(field.getModifiers()))
            .peek(field -> {
                assertThat(Modifier.isStatic(field.getModifiers())).as(field.getName()).isTrue();
                assertThat(Modifier.isFinal(field.getModifiers())).as(field.getName()).isTrue();
            })
            .map(Field::getName)
            .collect(java.util.stream.Collectors.toSet());
        assertThat(publicFields).containsExactlyInAnyOrderElementsOf(FLOW_DEFAULT_FIELDS);
    }

    private static Path resolveSourceRoot() {
        Path moduleRoot = Path.of("").toAbsolutePath();
        Path sourceRoot = moduleRoot.resolve("src/main/java");
        if (Files.isDirectory(sourceRoot)) {
            return sourceRoot;
        }
        return moduleRoot.resolve("tfi-flow-core/src/main/java");
    }

    private static Stream<String> forbiddenReferencesIn(Path sourceFile) {
        try {
            String content = Files.readString(sourceFile);
            return FORBIDDEN_REFERENCES.stream()
                .filter(content::contains)
                .map(reference -> sourceFile + " contains " + reference);
        } catch (IOException ex) {
            return Stream.of(sourceFile + " cannot be read: " + ex.getMessage());
        }
    }
}
