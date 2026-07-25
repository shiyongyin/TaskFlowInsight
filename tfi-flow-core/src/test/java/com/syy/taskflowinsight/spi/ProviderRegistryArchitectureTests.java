package com.syy.taskflowinsight.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Provider Registry 状态所有权的结构契约。
 *
 * <p>后续 trust 与 selected-cache 卡只能扩展同一个 engine，不能重新在 facade 中落静态状态。
 */
class ProviderRegistryArchitectureTests {

    @Test
    void engineIsPackagePrivateFinalAndNotAnApi() throws Exception {
        Class<?> engineType = Class.forName("com.syy.taskflowinsight.spi.ProviderRegistryEngine");
        int modifiers = engineType.getModifiers();

        assertThat(Modifier.isFinal(modifiers)).isTrue();
        assertThat(Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)).isFalse();
        assertThat(Arrays.stream(engineType.getDeclaredConstructors()))
            .noneMatch(constructor -> isPublicOrProtected(constructor.getModifiers()));
        assertThat(Arrays.stream(engineType.getDeclaredMethods()))
            .noneMatch(method -> isPublicOrProtected(method.getModifiers()));
        assertThat(Arrays.stream(engineType.getDeclaredFields()))
            .filteredOn(field -> Modifier.isStatic(field.getModifiers()))
            .allMatch(field -> Modifier.isFinal(field.getModifiers()));
    }

    @Test
    void registryOwnsExactlyOneEngineInstance() throws Exception {
        Class<?> engineType = Class.forName("com.syy.taskflowinsight.spi.ProviderRegistryEngine");
        List<Field> engineFields = Arrays.stream(ProviderRegistry.class.getDeclaredFields())
            .filter(field -> field.getType() == engineType)
            .toList();

        assertThat(engineFields).singleElement().satisfies(field -> {
            assertThat(field.getName()).isEqualTo("engine");
            assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
            assertThat(Modifier.isStatic(field.getModifiers())).isTrue();
            assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
        });
        assertThat(ProviderRegistry.class.getDeclaredFields()).hasSize(1);
        assertThat(engineConstructionSites()).containsExactly("ProviderRegistry.java");
    }

    @Test
    void engineOwnsAllTrustCandidateAndEpochState() throws Exception {
        Class<?> engineType = Class.forName("com.syy.taskflowinsight.spi.ProviderRegistryEngine");
        Set<String> instanceFields = Arrays.stream(engineType.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .map(Field::getName)
            .collect(java.util.stream.Collectors.toSet());

        assertThat(instanceFields).containsExactlyInAnyOrder(
            "lifecycleLock",
            "registrations",
            "serviceLoaderCache",
            "effectiveLoaders",
            "resolvedProviders",
            "knownProviderTypes",
            "allowedProviders",
            "whitelistState",
            "runtimeAllowedProviders",
            "registryEpoch",
            "generation",
            "runtimeStarted");
    }

    @Test
    void capacityAndLoaderIdentityContractsAreExact() throws Exception {
        Class<?> engineType = Class.forName("com.syy.taskflowinsight.spi.ProviderRegistryEngine");
        assertThat(intConstant(engineType, "MAX_PROVIDER_TYPES")).isEqualTo(64);
        assertThat(intConstant(engineType, "MAX_REGISTERED_PROVIDERS_PER_TYPE")).isEqualTo(64);
        assertThat(intConstant(engineType, "MAX_CACHED_LOADERS_PER_TYPE")).isEqualTo(8);
        assertThat(intConstant(engineType, "MAX_DISCOVERED_PROVIDERS_PER_SCAN")).isEqualTo(64);
        assertThat(intConstant(engineType, "MAX_PUBLICATION_ATTEMPTS")).isEqualTo(3);

        Class<?> loaderKey = Class.forName(
            "com.syy.taskflowinsight.spi.ProviderRegistryEngine$LoaderKey");
        assertThat(Arrays.stream(loaderKey.getRecordComponents())
            .map(component -> component.getType().getName()))
            .containsExactly(
                "long",
                "java.lang.Class",
                "com.syy.taskflowinsight.spi.ProviderRegistryEngine$LoaderIdentity");

        Class<?> resolutionKey = Class.forName(
            "com.syy.taskflowinsight.spi.ProviderRegistryEngine$ResolutionKey");
        assertThat(Arrays.stream(resolutionKey.getRecordComponents())
            .map(component -> component.getType().getName()))
            .containsExactly(
                "long",
                "java.lang.Class",
                "com.syy.taskflowinsight.spi.ProviderRegistryEngine$LoaderIdentity");
    }

    @Test
    void selectedCacheIsOwnedOnlyByTheEngine() throws Exception {
        Class<?> engineType = Class.forName("com.syy.taskflowinsight.spi.ProviderRegistryEngine");
        Field selected = engineType.getDeclaredField("resolvedProviders");

        assertThat(Modifier.isPrivate(selected.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(selected.getModifiers())).isTrue();
        assertThat(Modifier.isStatic(selected.getModifiers())).isFalse();
        assertThat(Arrays.stream(ProviderRegistry.class.getDeclaredFields())
            .map(Field::getName))
            .doesNotContain("resolvedProviders", "selectedCache", "NO_PROVIDER");
    }

    @Test
    void publicationLoopsAreBoundedAndP4StateIsNotIntroduced() {
        String source = read(moduleRoot().resolve(
            "src/main/java/com/syy/taskflowinsight/spi/ProviderRegistryEngine.java"));

        assertThat(occurrences(source, "<= MAX_PUBLICATION_ATTEMPTS")).isEqualTo(2);
        assertThat(source)
            .contains("ProviderRegistry.class.getClassLoader()")
            .contains("value == identity.value")
            .contains("System.identityHashCode(value)")
            .contains("resolvedProviders")
            .contains("ResolutionKey")
            .contains("NO_PROVIDER")
            .doesNotContain("computeIfAbsent")
            .doesNotContain("selectedCache")
            .doesNotContain("ComparisonProvider")
            .doesNotContain("TrackingProvider")
            .doesNotContain("RenderProvider");
    }

    private static List<String> engineConstructionSites() throws IOException {
        try (var files = Files.walk(repositoryRoot())) {
            return files.filter(path -> path.toString().endsWith(".java"))
                .filter(ProviderRegistryArchitectureTests::isProductionSource)
                .filter(path -> read(path).contains("new ProviderRegistryEngine("))
                .map(path -> path.getFileName().toString())
                .toList();
        }
    }

    private static Path repositoryRoot() {
        Path moduleRoot = moduleRoot();
        Path parent = moduleRoot.getParent();
        return parent != null && Files.isDirectory(parent.resolve("tfi-flow-core/src/main/java"))
            ? parent
            : moduleRoot;
    }

    private static boolean isProductionSource(Path path) {
        Path relative = repositoryRoot().relativize(path.toAbsolutePath().normalize());
        return relative.getNameCount() > 4
            && "src".equals(relative.getName(1).toString())
            && "main".equals(relative.getName(2).toString())
            && "java".equals(relative.getName(3).toString());
    }

    private static Path moduleRoot() {
        Path basedir = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
        if (Files.isDirectory(basedir.resolve("src/main/java"))) {
            return basedir;
        }
        return basedir.resolve("tfi-flow-core");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read source contract: " + path, failure);
        }
    }

    private static int intConstant(Class<?> type, String fieldName) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
        assertThat(Modifier.isStatic(field.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
        return field.getInt(null);
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static boolean isPublicOrProtected(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }
}
