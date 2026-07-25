package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 发布制品身份、闭集和内容完整性的可执行合同。 */
class ComparePublishabilityContractTests {

    private static final Map<String, String> RELEASE_JAR_MODULES = releaseJarModules();

    @Test
    void rootPomPublishesCompleteProjectIdentity() throws Exception {
        Element project = parse(repositoryRoot().resolve("pom.xml"));

        assertThat(childText(project, "url"))
                .isEqualTo("https://github.com/shiyongyin/TaskFlowInsight");
        assertThat(project.getAttribute("child.project.url.inherit.append.path"))
                .isEqualTo("false");
        Element license = directChild(directChild(project, "licenses"), "license");
        assertThat(childText(license, "name")).isEqualTo("Apache License, Version 2.0");
        assertThat(childText(license, "url"))
                .isEqualTo("https://www.apache.org/licenses/LICENSE-2.0.txt");
        assertThat(childText(license, "distribution")).isEqualTo("repo");

        Element developer = directChild(directChild(project, "developers"), "developer");
        assertThat(childText(developer, "id")).isEqualTo("shiyongyin");
        assertThat(childText(developer, "name")).isEqualTo("shiyongyin");
        assertThat(childText(developer, "url"))
                .isEqualTo("https://github.com/shiyongyin");

        Element scm = directChild(project, "scm");
        assertThat(scm.getAttribute("child.scm.connection.inherit.append.path"))
                .isEqualTo("false");
        assertThat(scm.getAttribute("child.scm.developerConnection.inherit.append.path"))
                .isEqualTo("false");
        assertThat(scm.getAttribute("child.scm.url.inherit.append.path"))
                .isEqualTo("false");
        assertThat(childText(scm, "connection"))
                .isEqualTo("scm:git:https://github.com/shiyongyin/TaskFlowInsight.git");
        assertThat(childText(scm, "developerConnection"))
                .isEqualTo("scm:git:ssh://git@github.com/shiyongyin/TaskFlowInsight.git");
        assertThat(childText(scm, "tag")).isEqualTo("HEAD");
        assertThat(childText(scm, "url"))
                .isEqualTo("https://github.com/shiyongyin/TaskFlowInsight");
    }

    @Test
    void rootPomPreservesStructuredStaticAnalysisDefaultsAndActivation() throws Exception {
        Element project = parse(repositoryRoot().resolve("pom.xml"));
        Element build = directChild(project, "build");
        Element pluginManagement = directChild(build, "pluginManagement");

        Element spotBugs = plugin(pluginManagement,
                "com.github.spotbugs", "spotbugs-maven-plugin");
        Element spotBugsConfiguration = directChild(spotBugs, "configuration");
        assertThat(childText(spotBugs, "version")).isEqualTo("4.8.6.6");
        assertThat(childText(spotBugsConfiguration, "effort")).isEqualTo("Max");
        assertThat(childText(spotBugsConfiguration, "threshold")).isEqualTo("High");
        assertThat(childText(spotBugsConfiguration, "failOnError")).isEqualTo("false");
        assertThat(childText(spotBugsConfiguration, "includeFilterFile"))
                .isEqualTo("${maven.multiModuleProjectDirectory}/spotbugs-include.xml");
        assertThat(childText(spotBugsConfiguration, "excludeFilterFile"))
                .isEqualTo("${maven.multiModuleProjectDirectory}/spotbugs-exclude.xml");
        assertVerifyExecution(spotBugs, "spotbugs-check");

        Element checkstyle = plugin(pluginManagement,
                "org.apache.maven.plugins", "maven-checkstyle-plugin");
        Element checkstyleConfiguration = directChild(checkstyle, "configuration");
        assertThat(childText(checkstyle, "version")).isEqualTo("3.6.0");
        assertThat(childText(checkstyleConfiguration, "configLocation"))
                .isEqualTo("google_checks.xml");
        assertThat(childText(checkstyleConfiguration, "failsOnError")).isEqualTo("false");
        assertThat(childText(checkstyleConfiguration, "violationSeverity")).isEqualTo("warning");
        assertThat(childText(checkstyleConfiguration, "maxAllowedViolations")).isEqualTo("30000");
        assertVerifyExecution(checkstyle, "checkstyle-check");

        Element pmd = plugin(pluginManagement,
                "org.apache.maven.plugins", "maven-pmd-plugin");
        Element pmdConfiguration = directChild(pmd, "configuration");
        assertThat(childText(pmd, "version")).isEqualTo("3.25.0");
        assertThat(childText(pmdConfiguration, "failOnViolation")).isEqualTo("false");
        assertThat(childText(directChild(pmdConfiguration, "rulesets"), "ruleset"))
                .isEqualTo("${maven.multiModuleProjectDirectory}/config/pmd/ruleset.xml");
        assertVerifyExecution(pmd, "pmd-check");

        plugin(build, "com.github.spotbugs", "spotbugs-maven-plugin");
        plugin(build, "org.apache.maven.plugins", "maven-checkstyle-plugin");
        plugin(build, "org.apache.maven.plugins", "maven-pmd-plugin");
    }

    @Test
    void releaseProfileRejectsMutableVersionsAndAttachesCompleteArtifacts() throws Exception {
        Element project = parse(repositoryRoot().resolve("pom.xml"));
        assertThat(childText(directChild(project, "properties"), "tfi.release.final-version.skip"))
                .isEqualTo("false");
        assertThat(childText(directChild(project, "properties"), "tfi.release.javadoc.fail-on-warnings"))
                .isEqualTo("true");
        Element rootEnforcer = plugin(directChild(project, "build"),
                "org.apache.maven.plugins", "maven-enforcer-plugin");
        assertThat(childText(execution(rootEnforcer, "enforce-reactor-module-convergence"), "inherited"))
                .isEqualTo("false");
        Element profile = directChildren(directChild(project, "profiles"), "profile").stream()
                .filter(candidate -> "release-artifacts".equals(childText(candidate, "id")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing release-artifacts profile"));

        Element build = directChild(profile, "build");
        Element enforcer = plugin(build, "org.apache.maven.plugins", "maven-enforcer-plugin");
        assertThat(childText(enforcer, "inherited")).isEqualTo("true");
        Element guard = execution(enforcer, "enforce-final-release-version");
        Element rules = directChild(directChild(guard, "configuration"), "rules");
        Element property = directChild(rules, "requireProperty");
        assertThat(childText(guard, "phase")).isEqualTo("validate");
        assertThat(childText(directChild(guard, "configuration"), "skip"))
                .isEqualTo("${tfi.release.final-version.skip}");
        assertThat(childText(property, "property")).isEqualTo("revision");
        assertThat(childText(property, "regex"))
                .contains("SNAPSHOT", "LATEST", "RELEASE", "\\$\\{");
        assertThat(directChildren(rules, "requireReleaseVersion")).hasSize(1);
        Element releaseDependencies = directChild(rules, "requireReleaseDeps");
        assertThat(childText(releaseDependencies, "failWhenParentIsSnapshot")).isEqualTo("true");
        assertThat(childText(releaseDependencies, "searchTransitive")).isEqualTo("true");

        Element resources = plugin(build, "org.apache.maven.plugins", "maven-resources-plugin");
        Element licenseCopy = execution(resources, "include-root-license");
        assertThat(childText(licenseCopy, "phase")).isEqualTo("process-resources");
        Element resource = directChild(directChild(
                directChild(licenseCopy, "configuration"), "resources"), "resource");
        assertThat(childText(resource, "directory")).isEqualTo("${maven.multiModuleProjectDirectory}");
        assertThat(directChildren(directChild(resource, "includes"), "include"))
                .extracting(Element::getTextContent)
                .containsExactly("LICENSE");

        Element source = plugin(build, "org.apache.maven.plugins", "maven-source-plugin");
        assertThat(directChildren(directChild(
                execution(source, "attach-sources"), "goals"), "goal"))
                .extracting(Element::getTextContent)
                .containsExactly("jar-no-fork");
        Element javadoc = plugin(build, "org.apache.maven.plugins", "maven-javadoc-plugin");
        assertThat(childText(directChild(javadoc, "configuration"), "doclint"))
                .isEqualTo("all,-missing");
        assertThat(childText(directChild(javadoc, "configuration"), "failOnWarnings"))
                .isEqualTo("${tfi.release.javadoc.fail-on-warnings}");
        assertThat(directChildren(directChild(
                execution(javadoc, "attach-javadocs"), "goals"), "goal"))
                .extracting(Element::getTextContent)
                .containsExactly("jar");

        Element flatten = plugin(build, "org.codehaus.mojo", "flatten-maven-plugin");
        Element flattenConfiguration = directChild(flatten, "configuration");
        assertThat(childText(flattenConfiguration, "flattenMode"))
                .isEqualTo("ossrh");
        assertThat(childText(directChild(flattenConfiguration, "pomElements"), "profiles"))
                .isEqualTo("remove");

        assertReleaseGuardExcluded("tfi-kernel");
        assertReleaseGuardExcluded("tfi-examples");
    }

    @Test
    void comparePublishClosureContainsExactlyOneParentAndSixJarModules() throws Exception {
        Element root = parse(repositoryRoot().resolve("pom.xml"));
        List<String> reactorModules = directChildren(directChild(root, "modules"), "module").stream()
                .map(Element::getTextContent)
                .map(String::strip)
                .toList();

        assertThat(RELEASE_JAR_MODULES.keySet())
                .containsExactly(
                        "tfi-flow-core",
                        "tfi-flow-spring-starter",
                        "tfi-compare",
                        "tfi-compare-spring-starter",
                        "tfi-ops-spring",
                        "tfi-all")
                .allMatch(reactorModules::contains)
                .doesNotContain("tfi-kernel", "tfi-examples");
        assertThat(childText(root, "artifactId")).isEqualTo("taskflowinsight-parent");
        assertThat(childText(root, "packaging")).isEqualTo("pom");
        assertThat(childText(root, "name")).isNotBlank();
        assertThat(childText(root, "description")).isNotBlank();

        for (Map.Entry<String, String> releaseModule : RELEASE_JAR_MODULES.entrySet()) {
            Element project = parse(repositoryRoot().resolve(releaseModule.getKey()).resolve("pom.xml"));
            Element parent = directChild(project, "parent");
            assertThat(childText(parent, "groupId")).isEqualTo("com.syy");
            assertThat(childText(parent, "artifactId")).isEqualTo("taskflowinsight-parent");
            assertThat(childText(parent, "version")).isEqualTo("${revision}");
            assertThat(childText(project, "artifactId")).isEqualTo(releaseModule.getValue());
            assertThat(childText(project, "name")).isNotBlank();
            assertThat(childText(project, "description")).isNotBlank();
            assertThat(directChildren(project, "packaging"))
                    .as("jar module must use Maven's default jar packaging")
                    .isEmpty();
        }
    }

    private static Map<String, String> releaseJarModules() {
        Map<String, String> modules = new LinkedHashMap<>();
        modules.put("tfi-flow-core", "tfi-flow-core");
        modules.put("tfi-flow-spring-starter", "tfi-flow-spring-starter");
        modules.put("tfi-compare", "tfi-compare");
        modules.put("tfi-compare-spring-starter", "tfi-compare-spring-starter");
        modules.put("tfi-ops-spring", "tfi-ops-spring");
        modules.put("tfi-all", "TaskFlowInsight");
        return Collections.unmodifiableMap(modules);
    }

    private static void assertReleaseGuardExcluded(String module) throws Exception {
        Element child = parse(repositoryRoot().resolve(module).resolve("pom.xml"));
        assertThat(childText(directChild(child, "properties"), "tfi.release.final-version.skip"))
                .isEqualTo("true");
        assertThat(childText(directChild(child, "properties"), "tfi.release.javadoc.fail-on-warnings"))
                .isEqualTo("false");
    }

    private static Path repositoryRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
    }

    private static Element parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return builder.parse(path.toFile()).getDocumentElement();
    }

    private static Element directChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element && name.equals(element.getTagName())) {
                return element;
            }
        }
        throw new IllegalStateException("Missing element " + name + " under " + parent.getTagName());
    }

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> matches = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element && name.equals(element.getTagName())) {
                matches.add(element);
            }
        }
        return List.copyOf(matches);
    }

    private static Element plugin(Element build, String groupId, String artifactId) {
        return directChildren(directChild(build, "plugins"), "plugin").stream()
                .filter(candidate -> groupId.equals(childText(candidate, "groupId")))
                .filter(candidate -> artifactId.equals(childText(candidate, "artifactId")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing plugin " + artifactId));
    }

    private static Element execution(Element plugin, String id) {
        return directChildren(directChild(plugin, "executions"), "execution").stream()
                .filter(candidate -> id.equals(childText(candidate, "id")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing execution " + id));
    }

    private static void assertVerifyExecution(Element plugin, String id) {
        Element configuredExecution = execution(plugin, id);
        assertThat(childText(configuredExecution, "phase")).isEqualTo("verify");
        assertThat(directChildren(directChild(configuredExecution, "goals"), "goal"))
                .extracting(Element::getTextContent)
                .containsExactly("check");
    }

    private static String childText(Element parent, String name) {
        return directChild(parent, name).getTextContent().strip();
    }
}
