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
 * 校验 core 发布 JAR 的 ServiceLoader 契约，而不是源码目录中的候选资源。
 *
 * <p>运行时发现能力属于发布物 ABI；直接读取 JAR 可以防止资源未打包时测试仍然假绿。</p>
 */
class CoreServiceLoaderContractTests {

    private static final String SERVICES_PREFIX = "META-INF/services/";

    private static final Map<String, String> EXPECTED_SERVICES = Map.of(
            FlowProvider.class.getName(), DefaultFlowProvider.class.getName(),
            ExportProvider.class.getName(), DefaultExportProvider.class.getName());

    @Test
    void coreJarContainsExactConstructibleDefaults() throws Exception {
        assertPackagedServices("tfi-flow-core-", EXPECTED_SERVICES);
    }

    private static void assertPackagedServices(String jarPrefix, Map<String, String> expected)
            throws Exception {
        Path jarPath = findBuiltJar(jarPrefix);
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            assertThat(serviceNames(jar)).containsExactlyInAnyOrderElementsOf(expected.keySet());
            for (Map.Entry<String, String> service : expected.entrySet()) {
                assertProvider(jar, service.getKey(), service.getValue());
            }
        }
    }

    private static Path findBuiltJar(String prefix) throws Exception {
        Path target = Path.of(System.getProperty("basedir"), "target");
        Properties coordinates = new Properties();
        try (var input = Files.newInputStream(target.resolve("maven-archiver/pom.properties"))) {
            coordinates.load(input);
        }
        assertThat(coordinates.getProperty("artifactId") + "-").isEqualTo(prefix);
        Path currentJar = target.resolve(prefix + coordinates.getProperty("version") + ".jar");
        assertThat(currentJar).as("current Maven build core JAR").isRegularFile();
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
