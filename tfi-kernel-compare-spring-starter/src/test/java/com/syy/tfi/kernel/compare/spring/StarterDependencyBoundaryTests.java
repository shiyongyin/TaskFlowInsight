package com.syy.tfi.kernel.compare.spring;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

class StarterDependencyBoundaryTests {

    @Test
    void modulePomPublishesOnlyTheApprovedDirectDependencies() throws Exception {
        Document pom = parse(repositoryRoot().resolve("tfi-kernel-compare-spring-starter/pom.xml"));
        Element dependencies = directChild(pom.getDocumentElement(), "dependencies");
        List<String> actual = new ArrayList<>();
        for (Element dependency : directChildren(dependencies, "dependency")) {
            String coordinate = text(dependency, "groupId") + ":" + text(dependency, "artifactId");
            String scope = optionalText(dependency, "scope", "compile");
            String optional = optionalText(dependency, "optional", "false");
            actual.add(coordinate + "|" + scope + "|" + optional);
        }
        assertThat(actual).containsExactly(
                "com.syy:tfi-kernel-compare|compile|false",
                "org.springframework.boot:spring-boot-starter|compile|false",
                "org.springframework.boot:spring-boot-starter-aop|compile|true",
                "org.springframework.boot:spring-boot-configuration-processor|compile|true",
                "org.springframework.boot:spring-boot-starter-test|test|false",
                "org.springframework:spring-tx|test|false",
                "org.openjdk.jmh:jmh-core|test|false",
                "com.fasterxml.jackson.core:jackson-databind|test|false");

        Set<String> declaredArtifacts = new HashSet<>();
        NodeList artifactIds = pom.getElementsByTagName("artifactId");
        for (int index = 0; index < artifactIds.getLength(); index++) {
            declaredArtifacts.add(artifactIds.item(index).getTextContent().trim());
        }
        assertThat(declaredArtifacts).contains(
                "flatten-maven-plugin",
                "maven-resources-plugin",
                "maven-source-plugin",
                "maven-javadoc-plugin");
    }

    @Test
    void processedArtifactInputsIncludeLicenseAndConsumerPomHasNoReactorParent() throws Exception {
        try (InputStream license = TfiKernelProperties.class.getResourceAsStream("/META-INF/LICENSE")) {
            assertThat(license).isNotNull();
            assertThat(license.readAllBytes()).isNotEmpty();
        }

        Path flattenedPom = repositoryRoot()
                .resolve("tfi-kernel-compare-spring-starter/target/flattened-pom.xml");
        Document flattened = parse(flattenedPom);
        Element project = flattened.getDocumentElement();
        assertThat(directChildren(project, "parent")).isEmpty();
        assertThat(text(project, "groupId")).isEqualTo("com.syy");
        assertThat(directChildren(project, "licenses")).hasSize(1);
    }

    @Test
    void rootReactorAndDependencyManagementOwnTheStarterCoordinate() throws Exception {
        Document rootPom = parse(repositoryRoot().resolve("pom.xml"));
        Element project = rootPom.getDocumentElement();
        Set<String> modules = new HashSet<>();
        for (Element module : directChildren(directChild(project, "modules"), "module")) {
            modules.add(module.getTextContent().trim());
        }
        assertThat(modules).contains("tfi-kernel-compare-spring-starter");

        Element dependencyManagement = directChild(project, "dependencyManagement");
        Element dependencies = directChild(dependencyManagement, "dependencies");
        Set<String> managedCoordinates = new HashSet<>();
        for (Element dependency : directChildren(dependencies, "dependency")) {
            managedCoordinates.add(text(dependency, "groupId") + ":" + text(dependency, "artifactId"));
        }
        assertThat(managedCoordinates).contains("com.syy:tfi-kernel-compare-spring-starter");
    }

    @Test
    void autoConfigurationImportsContainTheFiveStageSequence() throws Exception {
        String resource = "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        try (InputStream input = TfiKernelRuntimeAutoConfiguration.class.getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList())
                    .containsExactly(
                            TfiKernelCompareArtifactGuardAutoConfiguration.class.getName(),
                            TfiKernelRuntimeAutoConfiguration.class.getName(),
                            TfiCompareCoreAutoConfiguration.class.getName(),
                            TfiKernelCompareAutoConfiguration.class.getName(),
                            TfiKernelCompareAopAutoConfiguration.class.getName());
        }
    }

    @Test
    void generatedMetadataContainsOnlyDocumentedCanonicalProperties() throws Exception {
        String resource = "/META-INF/spring-configuration-metadata.json";
        try (InputStream input = TfiKernelProperties.class.getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            JSONArray properties = new JSONObject(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .getJSONArray("properties");
            Set<String> names = new HashSet<>();
            for (int index = 0; index < properties.length(); index++) {
                JSONObject property = properties.getJSONObject(index);
                names.add(property.getString("name"));
                assertThat(property.optString("description", ""))
                        .as(property.getString("name"))
                        .isNotBlank();
            }
            assertThat(names).containsExactlyInAnyOrderElementsOf(expectedMetadataNames());
            assertThat(names).noneMatch(name -> name.contains("include-sensitive"));
            assertThat(names).noneMatch(name -> name.startsWith("tfi.change-tracking."));
        }
    }

    @Test
    void springPropertiesDoNotCopyCoreHardCeilings() throws Exception {
        Path properties = repositoryRoot().resolve(
                "tfi-kernel-compare-spring-starter/src/main/java/com/syy/tfi/kernel/compare/spring");
        String sources = Files.readString(properties.resolve("TfiKernelProperties.java"))
                + Files.readString(properties.resolve("TfiCompareCoreProperties.java"))
                + Files.readString(properties.resolve("TfiKernelCompareProperties.java"));

        assertThat(sources).doesNotContain(
                "1_024", "1_048_576", "65_536", "100_000", "10_000", "Duration.ofSeconds(30)",
                "1_000", "8_192", "16_384", "10_000_000", "2_048", "Duration.ofHours(24)");
        assertThat(sources).doesNotContain("requireRange(", "requirePositiveDuration(", "requireRuleBudget(");
    }

    private static Set<String> expectedMetadataNames() {
        return Set.of(
                "tfi.kernel.enabled", "tfi.kernel.max-stages",
                "tfi.kernel.max-session-encoded-bytes", "tfi.kernel.max-record-encoded-bytes",
                "tfi.kernel.max-attrs", "tfi.compare.enabled", "tfi.compare.compute-similarity",
                "tfi.compare.include-collection-contents", "tfi.compare.max-depth",
                "tfi.compare.max-compared-nodes", "tfi.compare.max-elements", "tfi.compare.deadline",
                "tfi.compare.max-change-details", "tfi.compare.max-issues",
                "tfi.compare.max-result-value-chars", "tfi.compare.max-path-encoded-chars",
                "tfi.compare.max-result-total-chars", "tfi.compare.max-entity-key-components",
                "tfi.compare.max-entity-key-encoded-bytes", "tfi.compare.max-registered-extensions",
                "tfi.compare.max-path-rules", "tfi.compare.max-pattern-segments",
                "tfi.compare.max-pattern-token-chars", "tfi.compare.max-pattern-total-chars",
                "tfi.compare.include-path-rules", "tfi.compare.exclude-path-rules",
                "tfi.compare.max-tracking-targets", "tfi.compare.max-tracking-name-chars",
                "tfi.compare.numeric-absolute-tolerance", "tfi.compare.numeric-relative-tolerance",
                "tfi.compare.temporal-tolerance", "tfi.compare.masking.additional-rules",
                "tfi.kernel-compare.enabled", "tfi.kernel-compare.max-recorded-changes",
                "tfi.kernel-compare.aop.enabled");
    }

    private static Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static Element directChild(Element parent, String name) {
        return directChildren(parent, name).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing XML element: " + name));
    }

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> matches = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && name.equals(element.getTagName())) {
                matches.add(element);
            }
        }
        return List.copyOf(matches);
    }

    private static String text(Element parent, String name) {
        return directChild(parent, name).getTextContent().trim();
    }

    private static String optionalText(Element parent, String name, String defaultValue) {
        return directChildren(parent, name).stream()
                .findFirst()
                .map(element -> element.getTextContent().trim())
                .orElse(defaultValue);
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
}
