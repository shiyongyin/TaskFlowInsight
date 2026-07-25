package com.syy.taskflowinsight.build;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仓库 Maven 构建所有权的源码契约门禁。
 *
 * <p>该测试只固定 Root 与模块 POM 的持久配置边界；Maven 继承后的 effective model 和插件执行结果
 * 由 BLD-01 的真实 Maven 命令验证，避免在测试中重写 Maven model resolution。
 */
class BuildConfigurationContractTests {

    private static final String SKIP_COVERAGE_PROFILE =
            "skip-coverage-when-tests-skipped";
    private static final Map<String, String> CANONICAL_PLUGIN_VERSIONS = Map.of(
            "maven-enforcer-plugin", "3.4.1",
            "maven-checkstyle-plugin", "3.6.0",
            "spotbugs-maven-plugin", "4.8.6.6",
            "jacoco-maven-plugin", "0.8.12",
            "maven-surefire-plugin", "3.5.3");

    @Test
    void repositoryParentOwnsCanonicalPluginVersionsAndJacocoExecutionIds()
            throws Exception {
        Document root = parsePom("pom.xml");
        Element managedPlugins = requiredDirectChild(
                requiredDirectChild(
                        requiredDirectChild(root.getDocumentElement(), "build"),
                        "pluginManagement"),
                "plugins");

        Map<String, String> actualVersions = new LinkedHashMap<>();
        for (Map.Entry<String, String> expected : CANONICAL_PLUGIN_VERSIONS.entrySet()) {
            Element plugin = onlyPlugin(managedPlugins, expected.getKey());
            actualVersions.put(expected.getKey(), directChildText(plugin, "version"));
        }
        assertThat(actualVersions)
                .containsExactlyInAnyOrderEntriesOf(CANONICAL_PLUGIN_VERSIONS);

        Element jacoco = onlyPlugin(managedPlugins, "jacoco-maven-plugin");
        assertThat(directChildren(requiredDirectChild(jacoco, "executions"), "execution"))
                .extracting(execution -> directChildText(execution, "id"))
                .containsExactly("prepare-agent", "report", "check");
    }

    @Test
    void coreInheritsRepositoryParentAndRetainsStrictPluginOverrides()
            throws Exception {
        Document core = parsePom("tfi-flow-core/pom.xml");
        Element project = core.getDocumentElement();
        Element parent = requiredDirectChild(project, "parent");

        assertThat(directChildTextMap(parent)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "groupId", "com.syy",
                "artifactId", "taskflowinsight-parent",
                "version", "${revision}",
                "relativePath", "../pom.xml"));
        assertThat(directChildText(project, "groupId")).isNull();
        assertThat(directChildText(project, "version")).isNull();
        assertThat(directChildTextMap(requiredDirectChild(project, "properties")))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "jacoco.skip", "false",
                        "jmh.version", "1.37"));

        Element plugins = buildPlugins(core);
        Element enforcer = onlyPlugin(plugins, "maven-enforcer-plugin");
        assertThat(directChildText(enforcer, "version")).isNull();
        requiredExecution(
                enforcer, "enforce-banned-dependencies", null, "enforce");
        Element bannedDependencies = onlyDescendant(enforcer, "bannedDependencies");
        assertThat(directChildTexts(
                requiredDirectChild(bannedDependencies, "excludes"), "exclude"))
                .containsExactlyInAnyOrder(
                        "org.springframework:*",
                        "org.springframework.boot:*",
                        "io.micrometer:*",
                        "com.github.ben-manes.caffeine:*");
        assertThat(onlyDescendant(enforcer, "fail").getTextContent().strip())
                .isEqualTo("true");

        Element checkstyle = onlyPlugin(plugins, "maven-checkstyle-plugin");
        assertThat(directChildText(checkstyle, "version")).isNull();
        Element checkstyleConfig = requiredDirectChild(checkstyle, "configuration");
        assertThat(checkstyleConfig.getAttribute("combine.self")).isEqualTo("override");
        assertThat(directChildTextMap(checkstyleConfig))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "configLocation",
                        "${maven.multiModuleProjectDirectory}/tfi-flow-core/"
                                + "config/checkstyle/checkstyle.xml",
                        "consoleOutput", "true",
                        "failsOnError", "true"));
        requiredExecution(checkstyle, "checkstyle-check", "verify", "check");

        Element spotbugs = onlyPlugin(plugins, "spotbugs-maven-plugin");
        assertThat(directChildText(spotbugs, "version")).isNull();
        Element spotbugsConfig = requiredDirectChild(spotbugs, "configuration");
        assertThat(spotbugsConfig.getAttribute("combine.self")).isEqualTo("override");
        assertThat(directChildTextMap(spotbugsConfig))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "effort", "Max",
                        "threshold", "High",
                        "failOnError", "true"));
        requiredExecution(spotbugs, "spotbugs-check", "verify", "check");

        Element jacoco = onlyPlugin(plugins, "jacoco-maven-plugin");
        assertThat(directChildText(jacoco, "version")).isNull();
        Element jacocoConfig = requiredDirectChild(jacoco, "configuration");
        assertThat(jacocoConfig.getAttribute("combine.self")).isEqualTo("override");
        assertThat(directChildTextMap(jacocoConfig))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "skip", "${jacoco.skip}"));

        assertThat(directChildren(requiredDirectChild(jacoco, "executions"), "execution"))
                .extracting(execution -> directChildText(execution, "id"))
                .containsExactly("prepare-agent", "report", "check");

        Element prepare = requiredExecution(jacoco, "prepare-agent", null, "prepare-agent");
        assertEmptyOverride(requiredDirectChild(
                requiredDirectChild(prepare, "configuration"), "excludes"));
        Element report = requiredExecution(jacoco, "report", "test", "report");
        assertEmptyOverride(requiredDirectChild(
                requiredDirectChild(report, "configuration"), "excludes"));
        Element check = requiredExecution(jacoco, "check", "verify", "check");
        Element rules = requiredDirectChild(
                requiredDirectChild(check, "configuration"), "rules");
        assertThat(rules.getAttribute("combine.self")).isEqualTo("override");
        Element rule = onlyDirectChild(rules, "rule");
        assertThat(directChildText(rule, "element")).isEqualTo("BUNDLE");
        assertThat(directChildren(rule, "excludes")).isEmpty();
        Map<String, String> limits = new LinkedHashMap<>();
        for (Element limit : directChildren(
                requiredDirectChild(rule, "limits"), "limit")) {
            assertThat(directChildText(limit, "value")).isEqualTo("COVEREDRATIO");
            limits.put(
                    directChildText(limit, "counter"),
                    directChildText(limit, "minimum"));
        }
        assertThat(limits).containsExactlyInAnyOrderEntriesOf(Map.of(
                "INSTRUCTION", "0.80",
                "BRANCH", "0.70"));
        assertThat(descendants(jacoco, "exclude")).isEmpty();
    }

    @Test
    void coreHasNoLocalFlattenOrUnsupportedCheckstyleParameter() throws Exception {
        Document core = parsePom("tfi-flow-core/pom.xml");
        assertThat(descendants(core, "plugin"))
                .extracting(plugin -> directChildText(plugin, "artifactId"))
                .doesNotContain("flatten-maven-plugin");

        for (String pom : List.of("pom.xml", "tfi-flow-core/pom.xml", "tfi-all/pom.xml")) {
            assertThat(descendants(parsePom(pom), "includeTestSourceRoots"))
                    .as("unsupported Checkstyle parameter in %s", pom)
                    .isEmpty();
        }
    }

    @Test
    void coverageOwnersSkipJacocoOnlyWhenTestsAreSkipped() throws Exception {
        for (String pom : List.of("tfi-flow-core/pom.xml", "tfi-all/pom.xml")) {
            Document document = parsePom(pom);
            Element project = document.getDocumentElement();
            assertThat(directChildText(
                    requiredDirectChild(project, "properties"), "jacoco.skip"))
                    .as("normal coverage default in %s", pom)
                    .isEqualTo("false");

            Element profile = onlyProfile(document, SKIP_COVERAGE_PROFILE);
            Element activationProperty = requiredDirectChild(
                    requiredDirectChild(profile, "activation"), "property");
            assertThat(directChildTextMap(activationProperty))
                    .containsExactlyInAnyOrderEntriesOf(Map.of(
                            "name", "skipTests",
                            "value", "true"));
            assertThat(directChildTextMap(requiredDirectChild(profile, "properties")))
                    .containsExactlyInAnyOrderEntriesOf(Map.of(
                            "jacoco.skip", "true"));
        }
    }

    private static Document parsePom(String relativePath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder()
                .parse(findRepositoryRoot().resolve(relativePath).toFile());
    }

    private static Element buildPlugins(Document document) {
        return requiredDirectChild(
                requiredDirectChild(document.getDocumentElement(), "build"), "plugins");
    }

    private static Element onlyPlugin(Element plugins, String artifactId) {
        List<Element> matches = directChildren(plugins, "plugin").stream()
                .filter(plugin -> artifactId.equals(directChildText(plugin, "artifactId")))
                .toList();
        assertThat(matches)
                .as("exactly one plugin %s", artifactId)
                .hasSize(1);
        return matches.getFirst();
    }

    private static Element requiredExecution(
            Element plugin, String id, String phase, String goal) {
        List<Element> matches = directChildren(
                requiredDirectChild(plugin, "executions"), "execution").stream()
                .filter(execution -> id.equals(directChildText(execution, "id")))
                .toList();
        assertThat(matches).as("exactly one execution %s", id).hasSize(1);
        Element execution = matches.getFirst();
        if (phase == null) {
            assertThat(directChildText(execution, "phase")).isNull();
        } else {
            assertThat(directChildText(execution, "phase")).isEqualTo(phase);
        }
        assertThat(directChildTexts(requiredDirectChild(execution, "goals"), "goal"))
                .containsExactly(goal);
        return execution;
    }

    private static Element onlyProfile(Document document, String id) {
        List<Element> matches = directChildren(
                requiredDirectChild(document.getDocumentElement(), "profiles"),
                "profile").stream()
                .filter(profile -> id.equals(directChildText(profile, "id")))
                .toList();
        assertThat(matches).as("exactly one profile %s", id).hasSize(1);
        return matches.getFirst();
    }

    private static Element onlyDescendant(Node parent, String name) {
        List<Element> matches = descendants(parent, name);
        assertThat(matches).as("exactly one descendant %s", name).hasSize(1);
        return matches.getFirst();
    }

    private static Element onlyDirectChild(Node parent, String name) {
        List<Element> matches = directChildren(parent, name);
        assertThat(matches).as("exactly one direct child %s", name).hasSize(1);
        return matches.getFirst();
    }

    private static Element requiredDirectChild(Node parent, String name) {
        Element child = null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(localName(element))) {
                if (child != null) {
                    throw new IllegalStateException("duplicate direct child " + name);
                }
                child = element;
            }
        }
        if (child == null) {
            throw new IllegalStateException("missing direct child " + name);
        }
        return child;
    }

    private static List<Element> directChildren(Node parent, String name) {
        return directChildren(parent).stream()
                .filter(element -> name.equals(localName(element)))
                .toList();
    }

    private static List<Element> directChildren(Node parent) {
        List<Element> elements = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return List.copyOf(elements);
    }

    private static List<Element> descendants(Node root, String name) {
        NodeList nodes = root instanceof Document document
                ? document.getElementsByTagNameNS("*", name)
                : ((Element) root).getElementsByTagNameNS("*", name);
        List<Element> elements = new ArrayList<>(nodes.getLength());
        for (int index = 0; index < nodes.getLength(); index++) {
            elements.add((Element) nodes.item(index));
        }
        return List.copyOf(elements);
    }

    private static List<String> directChildTexts(Element parent, String name) {
        return directChildren(parent, name).stream()
                .map(element -> element.getTextContent().strip())
                .toList();
    }

    private static Map<String, String> directChildTextMap(Element parent) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Element child : directChildren(parent)) {
            String previous = values.put(localName(child), child.getTextContent().strip());
            if (previous != null) {
                throw new IllegalStateException(
                        "duplicate direct child " + localName(child));
            }
        }
        return Map.copyOf(values);
    }

    private static String directChildText(Element parent, String name) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && name.equals(localName(element))) {
                return element.getTextContent().strip();
            }
        }
        return null;
    }

    private static void assertEmptyOverride(Element element) {
        assertThat(element.getAttribute("combine.self")).isEqualTo("override");
        assertThat(directChildren(element)).isEmpty();
    }

    private static String localName(Node node) {
        return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
    }

    private static Path findRepositoryRoot() {
        for (Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
             path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("tfi-flow-core/pom.xml"))) {
                return path;
            }
        }
        throw new IllegalStateException("cannot locate repository root from user.dir");
    }
}
