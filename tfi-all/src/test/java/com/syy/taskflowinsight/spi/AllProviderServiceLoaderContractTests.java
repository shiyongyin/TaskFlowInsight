package com.syy.taskflowinsight.spi;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

/**
 * 校验聚合发布 JAR 对五类 Provider 的完整 ServiceLoader 声明。
 *
 * <p>聚合模块依赖多个实现 JAR，因此必须在聚合发布边界重复声明发现入口，避免调用方只引入
 * {@code TaskFlowInsight} 时因资源未打包而静默退化。</p>
 */
class AllProviderServiceLoaderContractTests {

    private static final String SERVICES_PREFIX = "META-INF/services/";

    private static final Map<String, String> EXPECTED_SERVICES = Map.of(
            FlowProvider.class.getName(), DefaultFlowProvider.class.getName(),
            ExportProvider.class.getName(), DefaultExportProvider.class.getName(),
            ComparisonProvider.class.getName(), DefaultComparisonProvider.class.getName(),
            TrackingProvider.class.getName(), DefaultTrackingProvider.class.getName(),
            RenderProvider.class.getName(), DefaultRenderProvider.class.getName());

    @Test
    void aggregateJarContainsExactConstructibleDefaults() throws Exception {
        Path jarPath = findBuiltJar();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            assertThat(serviceNames(jar)).containsExactlyInAnyOrderElementsOf(EXPECTED_SERVICES.keySet());
            for (Map.Entry<String, String> service : EXPECTED_SERVICES.entrySet()) {
                assertProvider(jar, service.getKey(), service.getValue());
            }
        }
    }

    private static Path findBuiltJar() throws Exception {
        Path target = Path.of(System.getProperty("basedir"), "target");
        Properties coordinates = new Properties();
        try (var input = Files.newInputStream(target.resolve("maven-archiver/pom.properties"))) {
            coordinates.load(input);
        }
        assertThat(coordinates.getProperty("artifactId")).isEqualTo("TaskFlowInsight");
        Path currentJar = target.resolve(
                coordinates.getProperty("artifactId") + "-" + coordinates.getProperty("version") + ".jar");
        assertThat(currentJar).as("current Maven build aggregate JAR").isRegularFile();
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

    private static void assertProvider(JarFile jar, String serviceName, String implementationName)
            throws Exception {
        JarEntry resource = jar.getJarEntry(SERVICES_PREFIX + serviceName);
        assertThat(resource).as("service resource %s", serviceName).isNotNull();
        List<String> declarations;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                jar.getInputStream(resource), StandardCharsets.UTF_8))) {
            declarations = reader.lines().toList();
        }
        assertThat(declarations).allSatisfy(line -> assertThat(line).isNotBlank().doesNotStartWith("#"));
        assertThat(declarations).doesNotHaveDuplicates().containsExactly(implementationName);

        Class<?> serviceType = Class.forName(serviceName);
        Class<?> implementationType = Class.forName(implementationName);
        assertThat(serviceType).isAssignableFrom(implementationType);
        Constructor<?> constructor = implementationType.getConstructor();
        assertThat(Modifier.isPublic(constructor.getModifiers())).isTrue();
        assertThat(constructor.newInstance()).isInstanceOf(serviceType);
    }
}
