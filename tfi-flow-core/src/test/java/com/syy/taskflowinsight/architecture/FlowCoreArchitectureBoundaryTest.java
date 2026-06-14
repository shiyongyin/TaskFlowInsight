package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * tfi-flow-core 模块边界测试。
 *
 * <p>防止纯 Java flow 内核重新依赖 Spring、Micrometer、Caffeine 或 compare/tracking 包。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.1
 */
class FlowCoreArchitectureBoundaryTest {

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
