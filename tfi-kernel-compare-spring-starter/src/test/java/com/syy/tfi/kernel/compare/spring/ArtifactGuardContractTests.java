package com.syy.tfi.kernel.compare.spring;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactGuardContractTests {

    /** Guard 识别的 Core class resource。 */
    private static final String CORE_RESOURCE =
            TfiKernelCompareArtifactGuardAutoConfiguration.COMPARE_RUNTIME_RESOURCE;
    /** Guard 识别的旧 shell marker resource。 */
    private static final String LEGACY_MARKER =
            TfiKernelCompareArtifactGuardAutoConfiguration.LEGACY_TRACKING_PROVIDER_RESOURCE;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    TfiKernelCompareArtifactGuardAutoConfiguration.class,
                    TfiKernelRuntimeAutoConfiguration.class,
                    TfiCompareCoreAutoConfiguration.class,
                    TfiKernelCompareAutoConfiguration.class));

    @Test
    void oneCoreResourcePassesAndRepeatedExternalFormIsDeduplicated() {
        URL core = url("file:/fixture/classes/" + CORE_RESOURCE);
        ClassLoader loader = new ResourceClassLoader(Map.of(CORE_RESOURCE, List.of(core, core)));

        assertThatCode(() -> TfiKernelCompareArtifactGuardAutoConfiguration.verifyClassPath(loader))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("duplicateCoreLocations")
    void anyTwoDistinctCoreResourceUrlsFailWithoutLeakingLocations(
            String fixture,
            URL first,
            URL second) {
        ClassLoader loader = new ResourceClassLoader(Map.of(CORE_RESOURCE, List.of(first, second)));

        assertThatThrownBy(() ->
                TfiKernelCompareArtifactGuardAutoConfiguration.verifyClassPath(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KCS_E_1001")
                .hasMessageContaining(CORE_RESOURCE)
                .hasMessageNotContaining("fixture-secret");
    }

    @Test
    void legacyShellMarkerFailsWithoutLoadingTheMarkerClass() {
        ClassLoader loader = new ResourceClassLoader(Map.of(
                CORE_RESOURCE, List.of(url("file:/fixture/core/" + CORE_RESOURCE)),
                LEGACY_MARKER, List.of(url("file:/fixture/legacy/" + LEGACY_MARKER))));

        assertThatThrownBy(() ->
                TfiKernelCompareArtifactGuardAutoConfiguration.verifyClassPath(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KCS_E_1001")
                .hasMessageContaining(LEGACY_MARKER);
    }

    @Test
    void bootConsumerWithSecondCoreArtifactFailsAtGuard() throws Exception {
        assertBootFixtureFails("/artifact-guard/duplicate-core/");
    }

    @Test
    void bootConsumerWithLegacyShellArtifactFailsAtGuard() throws Exception {
        assertBootFixtureFails("/artifact-guard/legacy-shell/");
    }

    @Test
    void starterPomEnforcesTheTransitiveLegacyArtifactBan() throws Exception {
        String pom = Files.readString(repositoryRoot().resolve(
                "tfi-kernel-compare-spring-starter/pom.xml"));
        assertThat(pom).contains("<searchTransitive>true</searchTransitive>");
        assertThat(pom).contains(
                "<exclude>com.syy:tfi-flow-core</exclude>",
                "<exclude>com.syy:tfi-compare</exclude>",
                "<exclude>com.syy:tfi-flow-spring-starter</exclude>",
                "<exclude>com.syy:tfi-compare-spring-starter</exclude>",
                "<exclude>com.syy:tfi-ops-spring</exclude>",
                "<exclude>com.syy:tfi-all</exclude>",
                "<exclude>com.syy:tfi-examples</exclude>");
    }

    private void assertBootFixtureFails(String rootResource) throws Exception {
        URL fixtureRoot = ArtifactGuardContractTests.class.getResource(rootResource);
        assertThat(fixtureRoot).isNotNull();
        try (URLClassLoader loader = new URLClassLoader(
                new URL[] {fixtureRoot}, getClass().getClassLoader())) {
            contextRunner.withClassLoader(loader).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("KCS_E_1001");
            });
        }
    }

    private static Stream<Arguments> duplicateCoreLocations() {
        return Stream.of(
                Arguments.of(
                        "IDE classes and ordinary JAR",
                        url("file:/fixture-secret/classes/" + CORE_RESOURCE),
                        url("jar:file:/fixture-secret/core.jar!/" + CORE_RESOURCE)),
                Arguments.of(
                        "two ordinary JAR URLs",
                        url("jar:file:/fixture-secret/core-a.jar!/" + CORE_RESOURCE),
                        url("jar:file:/fixture-secret/core-b.jar!/" + CORE_RESOURCE)),
                Arguments.of(
                        "ordinary and nested JAR URLs",
                        url("jar:file:/fixture-secret/core.jar!/" + CORE_RESOURCE),
                        url("jar:file:/fixture-secret/app.jar!/BOOT-INF/lib/core.jar!/" + CORE_RESOURCE)));
    }

    private static URL url(String externalForm) {
        try {
            return URI.create(externalForm).toURL();
        } catch (IOException exception) {
            throw new IllegalArgumentException("invalid fixture URL", exception);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("tfi-kernel-compare-spring-starter"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root");
    }

    private static final class ResourceClassLoader extends ClassLoader {

        /** 每个 resource name 对应的确定性 URL 枚举。 */
        private final Map<String, List<URL>> resources;

        private ResourceClassLoader(Map<String, List<URL>> resources) {
            super(null);
            this.resources = Map.copyOf(resources);
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            return Collections.enumeration(resources.getOrDefault(name, List.of()));
        }
    }
}
