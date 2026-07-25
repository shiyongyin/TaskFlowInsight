package com.syy.taskflowinsight.spi;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compare制品的ServiceLoader发布合同。
 *
 * <p>同时校验资源和JDK发现结果，防止源码类存在但descriptor遗漏、重复或指向不可构造实现。</p>
 */
class CompareServiceLoaderContractTests {

    private static final String SERVICES_PREFIX = "META-INF/services/";

    private static final Map<String, String> EXPECTED_SERVICES = Map.of(
            ComparisonProvider.class.getName(), DefaultComparisonProvider.class.getName(),
            TrackingProvider.class.getName(), DefaultTrackingProvider.class.getName(),
            RenderProvider.class.getName(), DefaultRenderProvider.class.getName());

    @Test
    void compareArtifactPublishesExactConstructibleDefaults() throws Exception {
        try (JarFile jar = new JarFile(findBuiltJar().toFile())) {
            assertThat(serviceNames(jar)).containsExactlyInAnyOrderElementsOf(EXPECTED_SERVICES.keySet());
            for (Map.Entry<String, String> service : EXPECTED_SERVICES.entrySet()) {
                assertDescriptor(jar, service.getKey(), service.getValue());
                assertProvider(service.getKey(), service.getValue());
            }
        }
    }

    private static Path findBuiltJar() throws Exception {
        Path target = Path.of(System.getProperty("basedir"), "target");
        Properties coordinates = new Properties();
        try (var input = Files.newInputStream(target.resolve("maven-archiver/pom.properties"))) {
            coordinates.load(input);
        }
        assertThat(coordinates.getProperty("artifactId")).isEqualTo("tfi-compare");
        Path currentJar = target.resolve(
                coordinates.getProperty("artifactId") + "-" + coordinates.getProperty("version") + ".jar");
        assertThat(currentJar).as("current Maven build Compare JAR").isRegularFile();
        return currentJar;
    }

    private static Set<String> serviceNames(JarFile jar) {
        Set<String> names = new LinkedHashSet<>();
        Enumeration<JarEntry> entries = jar.entries();
        for (JarEntry entry : Collections.list(entries)) {
            if (!entry.isDirectory() && entry.getName().startsWith(SERVICES_PREFIX)) {
                names.add(entry.getName().substring(SERVICES_PREFIX.length()));
            }
        }
        return names;
    }

    private static void assertDescriptor(
            JarFile jar, String serviceName, String implementationName) throws Exception {
        JarEntry resource = jar.getJarEntry(SERVICES_PREFIX + serviceName);
        assertThat(resource).as("service resource %s", serviceName).isNotNull();
        List<String> declarations;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                jar.getInputStream(resource), StandardCharsets.UTF_8))) {
            declarations = reader.lines()
                    .map(String::strip)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .toList();
        }
        assertThat(declarations).doesNotHaveDuplicates().containsExactly(implementationName);
    }

    private static void assertProvider(String serviceName, String implementationName) throws Exception {
        Class<?> serviceType = Class.forName(serviceName);
        Class<?> implementationType = Class.forName(implementationName);
        assertThat(serviceType).isAssignableFrom(implementationType);
        Constructor<?> constructor = implementationType.getConstructor();
        assertThat(Modifier.isPublic(constructor.getModifiers())).isTrue();
        assertThat(constructor.newInstance()).isInstanceOf(serviceType);
        assertThat(loadProviderTypes(serviceType)).containsExactly(implementationName);
    }

    private static <S> List<String> loadProviderTypes(Class<S> serviceType) {
        return ServiceLoader.load(serviceType).stream()
                .map(provider -> provider.type().getName())
                .toList();
    }
}
