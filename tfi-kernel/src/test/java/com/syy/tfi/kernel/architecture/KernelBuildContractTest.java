package com.syy.tfi.kernel.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 内核身份预算的可执行合同，防止后续功能把轻量模块重新堆成第二个 flow-core。
 */
class KernelBuildContractTest {

    /** KCS-01 clean 主 JAR 基线，单位为 bytes。 */
    private static final long BASELINE_JAR_BYTES = 61_406L;
    /** 方案 D 的绝对主 JAR 上限，单位为 bytes。 */
    private static final long MAX_JAR_BYTES = 64L * 1024L;
    /** 相对 KCS-01 基线允许的最大增长，单位为 bytes。 */
    private static final long MAX_JAR_GROWTH_BYTES = 4L * 1024L;
    private static final long MAX_MAIN_FILES = 22L;
    private static final long MAX_MAIN_LINES = 2_800L;

    @Test
    void reactorAndModulePomKeepTheKernelDependencyBoundary() throws Exception {
        Document root = parse(repositoryRoot().resolve("pom.xml"));
        Document module = parse(repositoryRoot().resolve("tfi-kernel/pom.xml"));
        Element moduleProject = module.getDocumentElement();

        assertThat(directChildTexts(requiredChild(root.getDocumentElement(), "modules"), "module"))
                .containsOnlyOnce("tfi-kernel");

        Element managed = requiredChild(requiredChild(root.getDocumentElement(), "dependencyManagement"), "dependencies");
        assertThat(dependencies(managed))
                .anySatisfy(dependency -> {
                    assertThat(directText(dependency, "groupId")).isEqualTo("com.syy");
                    assertThat(directText(dependency, "artifactId")).isEqualTo("tfi-kernel");
                    assertThat(directText(dependency, "version")).isEqualTo("${project.version}");
                });

        List<Element> moduleDependencies = dependencies(requiredChild(moduleProject, "dependencies"));
        assertThat(moduleDependencies)
                .filteredOn(dependency -> directText(dependency, "scope") == null)
                .singleElement()
                .satisfies(dependency -> {
                    assertThat(directText(dependency, "groupId")).isEqualTo("org.slf4j");
                    assertThat(directText(dependency, "artifactId")).isEqualTo("slf4j-api");
                });
        assertThat(moduleDependencies)
                .filteredOn(dependency -> directText(dependency, "scope") != null)
                .allSatisfy(dependency -> assertThat(directText(dependency, "scope")).isEqualTo("test"));

        Element modulePlugins = requiredChild(requiredChild(moduleProject, "build"), "plugins");
        Element defaultFailsafe = directChildren(modulePlugins, "plugin").stream()
                .filter(plugin -> "maven-failsafe-plugin".equals(directText(plugin, "artifactId")))
                .findFirst()
                .orElseThrow();
        assertThat(directText(requiredChild(defaultFailsafe, "configuration"), "skipITs")).isEqualTo("true");

        assertThat(directText(moduleProject, "url"))
                .isEqualTo("https://github.com/shiyongyin/TaskFlowInsight");
        assertThat(directText(requiredChild(moduleProject, "licenses"), "license")).isNotBlank();
        assertThat(directText(requiredChild(moduleProject, "developers"), "developer")).isNotBlank();
        assertThat(directText(requiredChild(moduleProject, "scm"), "connection")).startsWith("scm:git:");

        Element rootProperties = requiredChild(root.getDocumentElement(), "properties");
        assertThat(directText(rootProperties, "tfi.perf.strict")).isEqualTo("false");
        Element managedPlugins = requiredChild(
                requiredChild(requiredChild(root.getDocumentElement(), "build"), "pluginManagement"), "plugins");
        Element failsafe = directChildren(managedPlugins, "plugin").stream()
                .filter(plugin -> "maven-failsafe-plugin".equals(directText(plugin, "artifactId")))
                .findFirst()
                .orElseThrow();
        Element strictProperty = requiredChild(
                requiredChild(requiredChild(failsafe, "configuration"), "systemPropertyVariables"),
                "tfi.perf.strict");
        assertThat(strictProperty.getTextContent().strip()).isEqualTo("${tfi.perf.strict}");

        Element releaseProfile = directChildren(requiredChild(moduleProject, "profiles"), "profile").stream()
                .filter(profile -> "release-artifacts".equals(directText(profile, "id")))
                .findFirst()
                .orElseThrow();
        Element releasePlugins = requiredChild(requiredChild(releaseProfile, "build"), "plugins");
        assertThat(directChildTexts(releasePlugins, "plugin"))
                .anySatisfy(plugin -> assertThat(plugin).contains("maven-source-plugin"))
                .anySatisfy(plugin -> assertThat(plugin).contains("maven-javadoc-plugin"));

        Element perfProfile = directChildren(requiredChild(moduleProject, "profiles"), "profile").stream()
                .filter(profile -> "perf".equals(directText(profile, "id")))
                .findFirst()
                .orElseThrow();
        Element perfFailsafe = directChildren(
                requiredChild(requiredChild(perfProfile, "build"), "plugins"), "plugin").stream()
                .filter(plugin -> "maven-failsafe-plugin".equals(directText(plugin, "artifactId")))
                .findFirst()
                .orElseThrow();
        assertThat(directText(requiredChild(perfFailsafe, "configuration"), "skipITs")).isEqualTo("false");
    }

    @Test
    void publishedConsumerPomDoesNotDependOnTheReactorParent() throws Exception {
        Path root = repositoryRoot();
        Path flattenedPom = root.resolve("tfi-kernel/target/flattened-pom.xml");
        assertThat(flattenedPom).isRegularFile();

        Element project = parse(flattenedPom).getDocumentElement();
        Element rootProperties = requiredChild(parse(root.resolve("pom.xml")).getDocumentElement(), "properties");
        assertThat(directChildren(project, "parent")).isEmpty();
        assertThat(directText(project, "groupId")).isEqualTo("com.syy");
        assertThat(directText(project, "artifactId")).isEqualTo("tfi-kernel");
        assertThat(directText(project, "version")).isEqualTo(directText(rootProperties, "revision"));

        assertThat(dependencies(requiredChild(project, "dependencies")))
                .singleElement()
                .satisfies(dependency -> {
                    assertThat(directText(dependency, "groupId")).isEqualTo("org.slf4j");
                    assertThat(directText(dependency, "artifactId")).isEqualTo("slf4j-api");
                    assertThat(directText(dependency, "version"))
                            .isNotBlank()
                            .doesNotContain("${");
                });
    }

    @Test
    void packagedKernelStaysInsideItsSizeAndSourceBudgets() throws IOException {
        Path root = repositoryRoot();
        Path main = root.resolve("tfi-kernel/src/main/java");
        List<Path> sources;
        try (Stream<Path> paths = Files.exists(main) ? Files.walk(main) : Stream.empty()) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }

        long lines = 0L;
        for (Path source : sources) {
            try (Stream<String> sourceLines = Files.lines(source)) {
                lines += sourceLines.count();
            }
        }
        assertThat(sources).hasSizeLessThanOrEqualTo((int) MAX_MAIN_FILES);
        assertThat(lines).isLessThanOrEqualTo(MAX_MAIN_LINES);

        Path target = root.resolve("tfi-kernel/target");
        List<Path> mainJars;
        try (Stream<Path> paths = Files.list(target)) {
            mainJars = paths.filter(path -> path.getFileName().toString().matches("tfi-kernel-.*\\.jar"))
                    .filter(path -> !path.getFileName().toString().contains("sources"))
                    .filter(path -> !path.getFileName().toString().contains("javadoc"))
                    .toList();
        }
        assertThat(mainJars).singleElement();
        Path mainJar = mainJars.getFirst();
        long mainJarBytes = Files.size(mainJar);
        assertThat(mainJarBytes).isLessThanOrEqualTo(MAX_JAR_BYTES);
        assertThat(mainJarBytes - BASELINE_JAR_BYTES).isLessThanOrEqualTo(MAX_JAR_GROWTH_BYTES);

        try (JarFile jar = new JarFile(mainJar.toFile())) {
            List<String> entries = jar.stream().map(entry -> entry.getName()).toList();
            assertThat(entries)
                    .filteredOn(entry -> entry.endsWith(".class"))
                    .allMatch(entry -> entry.startsWith("com/syy/tfi/kernel/"))
                    .noneMatch(entry -> entry.contains("/benchmark/")
                            || entry.endsWith("Test.class") || entry.endsWith("Tests.class"));
            assertThat(entries).contains(
                    "META-INF/maven/com.syy/tfi-kernel/pom.xml",
                    "META-INF/maven/com.syy/tfi-kernel/pom.properties");
        }
    }

    @Test
    void pilotReadinessDocsExampleAndCiRemainDiscoverable() throws IOException {
        Path root = repositoryRoot();
        Path design = root.resolve("tfi-kernel/docs/design-doc.md");
        Path apiInventory = root.resolve("tfi-kernel/docs/api-inventory.md");
        Path example = root.resolve(
                "tfi-kernel/src/test/java/com/syy/tfi/kernel/example/PlainJavaKernelExample.java");
        Path workflow = root.resolve(".github/workflows/tfi-kernel-ci.yml");
        Path strictPerfWorkflow = root.resolve(".github/workflows/tfi-kernel-perf-gate.yml");

        assertThat(design).isRegularFile();
        assertThat(Files.readString(design))
                .contains("Tfi.toJson(FlowSession)")
                .contains("schema.md")
                .contains("api-inventory.md")
                .contains("KNL-03")
                .contains("KNL-04");
        assertThat(apiInventory).isRegularFile();
        assertThat(Files.readString(apiInventory))
                .contains("当前没有真实服务试用证据")
                .contains("PENDING")
                .contains("Tfi.call(String, Supplier<T>)")
                .contains("FlowSink.accept(FlowSession)");
        assertThat(example).isRegularFile();
        assertThat(Files.readString(root.resolve("README.md")))
                .contains("11 reactor modules")
                .contains("com.syy.taskflowinsight.api.TFI")
                .contains("com.syy.tfi.kernel.Tfi")
                .contains("`TFI` and `Tfi` are different Java types")
                .contains("do not depend on or delegate to each other")
                .contains("TaskFlowInsight 4.0 RC train")
                .contains("targets 1.0 as its first stable API baseline")
                .contains("tfi-kernel Strict Perf Gate");
        assertThat(Files.readString(root.resolve("README.zh-CN.md")))
                .contains("11 个 reactor 模块")
                .contains("com.syy.taskflowinsight.api.TFI")
                .contains("com.syy.tfi.kernel.Tfi")
                .contains("`TFI` 与 `Tfi` 是位于不同包中的不同 Java 类型")
                .contains("互不依赖、互不委托")
                .contains("TaskFlowInsight 4.0 版本列车进入 RC")
                .contains("首个稳定 API 基线目标为 1.0")
                .contains("tfi-kernel Strict Perf Gate");
        assertThat(workflow).isRegularFile();
        assertThat(Files.readString(workflow))
                .contains("./mvnw -q -pl tfi-kernel -am verify")
                .contains("KernelBenchmarkRunnerTest")
                .contains("-Djacoco.skip=true")
                .contains("PlainJavaKernelExample")
                .contains("tfi-kernel/target/flattened-pom.xml")
                .contains(".github/workflows/tfi-kernel-perf-gate.yml");
        assertThat(strictPerfWorkflow).isRegularFile();
        assertThat(Files.readString(strictPerfWorkflow))
                .contains("runs-on: [self-hosted, tfi-kernel-perf]")
                .contains("-Dtfi.perf.strict=true")
                .contains("KernelPerfGateIT")
                .contains("-Dfailsafe.failIfNoSpecifiedTests=true")
                .contains("Verify strict gate execution")
                .contains("TEST-com.syy.tfi.kernel.benchmark.KernelPerfGateIT.xml")
                .doesNotContain("-Dfailsafe.failIfNoSpecifiedTests=false")
                .doesNotContain("ubuntu-latest");
    }

    private static Document parse(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(pom.toFile());
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("tfi-flow-core"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Cannot locate repository root");
        }
        return current;
    }

    private static List<Element> dependencies(Element parent) {
        return directChildren(parent, "dependency");
    }

    private static Element requiredChild(Element parent, String name) {
        return directChildren(parent, name).stream().findFirst().orElseThrow();
    }

    private static List<String> directChildTexts(Element parent, String name) {
        return directChildren(parent, name).stream().map(Node::getTextContent).map(String::strip).toList();
    }

    private static String directText(Element parent, String name) {
        return directChildren(parent, name).stream()
                .findFirst()
                .map(Node::getTextContent)
                .map(String::strip)
                .orElse(null);
    }

    private static List<Element> directChildren(Element parent, String name) {
        NodeList nodes = parent.getChildNodes();
        return java.util.stream.IntStream.range(0, nodes.getLength())
                .mapToObj(nodes::item)
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .filter(element -> name.equals(element.getLocalName()))
                .toList();
    }
}
