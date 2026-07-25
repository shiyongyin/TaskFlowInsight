package com.syy.taskflowinsight.compare.spring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compare starter 默认 Maven 生命周期的可执行质量合同。
 */
class CompareStarterBuildConfigurationContractTests {

    @Test
    void artifactProvidesStandardBootStarterWithoutConsumerDependencies() throws Exception {
        Element project = parse(repositoryRoot().resolve("tfi-compare-spring-starter/pom.xml"));
        List<String> dependencies = directChildren(directChild(project, "dependencies"), "dependency")
                .stream()
                .map(dependency -> childText(dependency, "groupId") + ":"
                        + childText(dependency, "artifactId") + ":"
                        + childText(dependency, "scope"))
                .toList();

        assertThat(dependencies)
                .contains("org.springframework.boot:spring-boot-starter:")
                .doesNotContain("org.springframework.boot:spring-boot-autoconfigure:");
        assertThat(dependencies).noneMatch(coordinate -> coordinate.startsWith("com.syy:tfi-ops-spring:"));
        assertThat(dependencies).noneMatch(coordinate -> coordinate.startsWith("com.syy:TaskFlowInsight:"));
        assertThat(dependencies).noneMatch(coordinate -> coordinate.startsWith("com.syy:tfi-examples:"));
    }

    @Test
    void defaultVerifyOwnsCoverageAndBlockingSpotBugs() throws Exception {
        Element project = parse(repositoryRoot().resolve("tfi-compare-spring-starter/pom.xml"));
        Element properties = directChild(project, "properties");
        assertThat(childText(properties, "jacoco.skip")).isEqualTo("false");

        Element jacoco = plugin(project, "org.jacoco", "jacoco-maven-plugin");
        Element coverageCheck = execution(jacoco, "check");
        assertVerifyGoal(coverageCheck, "check");
        Element rules = directChild(directChild(coverageCheck, "configuration"), "rules");
        Element limits = directChild(directChild(rules, "rule"), "limits");
        List<Element> coverageLimits = directChildren(limits, "limit");
        assertThat(coverageLimits).singleElement().satisfies(limit -> {
            assertThat(childText(limit, "counter")).isEqualTo("INSTRUCTION");
            assertThat(childText(limit, "value")).isEqualTo("COVEREDRATIO");
            assertThat(new BigDecimal(childText(limit, "minimum"))).isPositive();
        });

        Element spotbugs = plugin(project, "com.github.spotbugs", "spotbugs-maven-plugin");
        assertThat(childText(directChild(spotbugs, "configuration"), "failOnError"))
                .isEqualTo("true");
        assertVerifyGoal(execution(spotbugs, "spotbugs-check"), "check");
    }

    @Test
    void starterOwnsFourSpaceCheckstyleAuthorityWithoutExclusions() throws Exception {
        Path root = repositoryRoot();
        Element project = parse(root.resolve("tfi-compare-spring-starter/pom.xml"));
        Element checkstyle = plugin(
                project, "org.apache.maven.plugins", "maven-checkstyle-plugin");
        assertThat(childText(directChild(checkstyle, "configuration"), "configLocation"))
                .isEqualTo("${maven.multiModuleProjectDirectory}/"
                        + "tfi-compare-spring-starter/config/checkstyle/checkstyle.xml");

        Path configPath = root.resolve(
                "tfi-compare-spring-starter/config/checkstyle/checkstyle.xml");
        Element checker = parse(configPath);
        assertThat(propertyValue(checker, "charset")).isEqualTo("UTF-8");
        assertThat(moduleNames(checker)).contains(
                "LineLength", "Indentation", "PackageName", "TypeName", "MethodName",
                "MemberName", "ParameterName", "LocalVariableName", "ConstantName",
                "RedundantImport", "UnusedImports", "NeedBraces", "GenericWhitespace",
                "NoWhitespaceBefore", "WhitespaceAfter", "MissingJavadocMethod");
        assertThat(propertyValue(module(checker, "LineLength"), "max")).isEqualTo("120");
        assertThat(propertyValue(module(checker, "Indentation"), "basicOffset")).isEqualTo("4");
        assertThat(Files.readString(configPath)).doesNotContain(
                "SuppressionFilter", "SuppressionXpathFilter",
                "BeforeExecutionExclusionFileFilter", "severity\" value=\"ignore",
                "<excludes>");
    }

    @Test
    void defaultVerifyUsesReadOnlyStarterStaticRatchet() throws Exception {
        Element project = parse(repositoryRoot().resolve("tfi-compare-spring-starter/pom.xml"));
        Element exec = plugin(project, "org.codehaus.mojo", "exec-maven-plugin");
        Element ratchet = execution(exec, "enforce-compare-starter-static-analysis-baseline");
        assertVerifyGoal(ratchet, "exec");
        Element configuration = directChild(ratchet, "configuration");
        assertThat(childText(configuration, "executable")).isEqualTo("python3");
        assertThat(directChildren(directChild(configuration, "arguments"), "argument"))
                .extracting(element -> element.getTextContent().strip())
                .containsExactly(
                        "scripts/enforce_static_analysis_baseline.py",
                        "--module",
                        "tfi-compare-spring-starter")
                .doesNotContain(
                        "--add-module", "--write-baseline", "--refresh-baseline");
    }

    @Test
    void staticBaselineContainsOneOwnedStarterBootstrap() throws Exception {
        JSONObject baseline = new JSONObject(Files.readString(
                repositoryRoot().resolve(".mvn/static-analysis-baseline.json")));
        JSONArray modules = baseline.getJSONArray("modules");
        int starterOccurrences = 0;
        for (int index = 0; index < modules.length(); index++) {
            if (modules.getString(index).equals("tfi-compare-spring-starter")) {
                starterOccurrences++;
            }
        }
        assertThat(starterOccurrences).isEqualTo(1);

        JSONArray bootstraps = baseline.getJSONArray("moduleBootstraps");
        assertThat(bootstraps.length()).isEqualTo(1);
        JSONObject bootstrap = bootstraps.getJSONObject(0);
        assertThat(bootstrap.getString("module")).isEqualTo("tfi-compare-spring-starter");
        assertThat(bootstrap.getString("ownerTask")).isEqualTo("CMP-HRD-03");
        assertThat(bootstrap.getString("reason")).isNotBlank();
        JSONArray configs = bootstrap.getJSONArray("configFiles");
        assertThat(List.of(
                configs.getJSONObject(0).getString("path"),
                configs.getJSONObject(1).getString("path")))
                .containsExactly(
                        "config/pmd/ruleset.xml",
                        "tfi-compare-spring-starter/config/checkstyle/checkstyle.xml");
        JSONObject baselineTools = baseline.getJSONObject("tools");
        JSONObject bootstrapTools = bootstrap.getJSONObject("tools");
        for (String tool : List.of("checkstyle", "pmd")) {
            int frozenCount = starterFindingCount(baselineTools.getJSONArray(tool));
            JSONObject evidence = bootstrapTools.getJSONObject(tool);
            assertThat(evidence.getInt("findingCount")).isEqualTo(frozenCount);
            assertThat(evidence.getString("fingerprintSha256")).matches("[0-9a-f]{64}");
        }
    }

    private static int starterFindingCount(JSONArray entries) throws Exception {
        int count = 0;
        for (int index = 0; index < entries.length(); index++) {
            JSONObject entry = entries.getJSONObject(index);
            if (entry.getString("module").equals("tfi-compare-spring-starter")) {
                count += entry.getInt("count");
            }
        }
        return count;
    }

    private static void assertVerifyGoal(Element execution, String goal) {
        assertThat(childText(execution, "phase")).isEqualTo("verify");
        assertThat(directChildren(directChild(execution, "goals"), "goal"))
                .extracting(element -> element.getTextContent().strip())
                .containsExactly(goal);
    }

    private static Set<String> moduleNames(Element root) {
        Set<String> names = new HashSet<>();
        collectModuleNames(root, names);
        return names;
    }

    private static void collectModuleNames(Element element, Set<String> names) {
        if (element.getTagName().equals("module")) {
            names.add(element.getAttribute("name"));
        }
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element child) {
                collectModuleNames(child, names);
            }
        }
    }

    private static Element module(Element root, String name) {
        if (root.getTagName().equals("module") && root.getAttribute("name").equals(name)) {
            return root;
        }
        NodeList children = root.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element child) {
                Element match = findModule(child, name);
                if (match != null) {
                    return match;
                }
            }
        }
        throw new IllegalStateException("Missing Checkstyle module: " + name);
    }

    private static Element findModule(Element root, String name) {
        if (root.getTagName().equals("module") && root.getAttribute("name").equals(name)) {
            return root;
        }
        NodeList children = root.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element child) {
                Element match = findModule(child, name);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static String propertyValue(Element parent, String name) {
        return directChildren(parent, "property").stream()
                .filter(property -> property.getAttribute("name").equals(name))
                .map(property -> property.getAttribute("value"))
                .findFirst()
                .orElse("");
    }

    private static Element plugin(Element project, String groupId, String artifactId) {
        return directChildren(directChild(directChild(project, "build"), "plugins"), "plugin")
                .stream()
                .filter(candidate -> childText(candidate, "groupId").equals(groupId)
                        && childText(candidate, "artifactId").equals(artifactId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing plugin: " + artifactId));
    }

    private static Element execution(Element plugin, String id) {
        return directChildren(directChild(plugin, "executions"), "execution").stream()
                .filter(candidate -> childText(candidate, "id").equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing execution: " + id));
    }

    private static Element directChild(Element parent, String name) {
        return directChildren(parent, name).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing element: " + name));
    }

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> matches = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && element.getTagName().equals(name)) {
                matches.add(element);
            }
        }
        return matches;
    }

    private static String childText(Element parent, String name) {
        return directChildren(parent, name).stream()
                .findFirst()
                .map(Element::getTextContent)
                .map(String::strip)
                .orElse("");
    }

    private static Element parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return builder.parse(path.toFile()).getDocumentElement();
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("tfi-compare-spring-starter"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root");
    }
}
