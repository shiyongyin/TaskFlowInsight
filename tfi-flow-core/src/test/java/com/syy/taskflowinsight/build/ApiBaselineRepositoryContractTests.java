package com.syy.taskflowinsight.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** 仓库内 3.0 API 基线的载荷、校验和与 Maven 解析入口契约。 */
class ApiBaselineRepositoryContractTests {

    private static final Path BASELINE_ROOT = repositoryRoot()
            .resolve(".mvn/api-baseline");
    private static final Path PAYLOAD_ROOT = BASELINE_ROOT.resolve("repository");
    private static final List<String> PAYLOADS = List.of(
            "com/syy/taskflowinsight-parent/3.0.0/taskflowinsight-parent-3.0.0.pom",
            "com/syy/tfi-flow-core/3.0.0/tfi-flow-core-3.0.0.pom",
            "com/syy/tfi-flow-core/3.0.0/tfi-flow-core-3.0.0.jar",
            "com/syy/tfi-flow-spring-starter/3.0.0/tfi-flow-spring-starter-3.0.0.pom",
            "com/syy/tfi-flow-spring-starter/3.0.0/tfi-flow-spring-starter-3.0.0.jar",
            "com/syy/tfi-compare/3.0.0/tfi-compare-3.0.0.pom",
            "com/syy/tfi-compare/3.0.0/tfi-compare-3.0.0.jar",
            "com/syy/tfi-ops-spring/3.0.0/tfi-ops-spring-3.0.0.pom",
            "com/syy/tfi-ops-spring/3.0.0/tfi-ops-spring-3.0.0.jar",
            "com/syy/TaskFlowInsight/3.0.0/TaskFlowInsight-3.0.0.pom",
            "com/syy/TaskFlowInsight/3.0.0/TaskFlowInsight-3.0.0.jar");

    @Test
    void repositoryContainsExactlyTheApprovedPayloadsAndSidecars() throws Exception {
        Set<String> expected = new LinkedHashSet<>();
        for (String payload : PAYLOADS) {
            expected.add(payload);
            expected.add(payload + ".sha256");
            expected.add(payload + ".sha1");
        }

        assertThat(relativeRegularFiles(PAYLOAD_ROOT))
                .containsExactlyInAnyOrderElementsOf(expected);
        for (String payload : PAYLOADS) {
            Path file = PAYLOAD_ROOT.resolve(payload);
            assertThat(Files.isSymbolicLink(file)).as(payload).isFalse();
            assertThat(Files.size(file)).as(payload).isPositive();
        }
    }

    @Test
    void manifestAndSidecarsMatchEveryPayloadByteForByte() throws Exception {
        Map<String, String> manifest = readManifest();
        assertThat(manifest.keySet()).containsExactlyInAnyOrderElementsOf(
                PAYLOADS.stream().map(path -> "repository/" + path).toList());

        for (String payload : PAYLOADS) {
            String digest = sha256(PAYLOAD_ROOT.resolve(payload));
            assertThat(manifest.get("repository/" + payload)).as(payload).isEqualTo(digest);
            assertThat(Files.readString(PAYLOAD_ROOT.resolve(payload + ".sha256")).strip())
                    .as(payload + ".sha256")
                    .isEqualTo(digest + "  " + Path.of(payload).getFileName());
            assertThat(Files.readString(PAYLOAD_ROOT.resolve(payload + ".sha1")).strip())
                    .as(payload + ".sha1")
                    .isEqualTo(digest(PAYLOAD_ROOT.resolve(payload), "SHA-1"));
        }
    }

    @Test
    void rootApiCompatProfileUsesTheRepositoryOwnedBaselineOnly() throws Exception {
        Document document = parse(repositoryRoot().resolve("pom.xml"));
        Element profile = descendants(document.getDocumentElement(), "profile").stream()
                .filter(candidate -> "api-compat".equals(directText(candidate, "id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing root api-compat profile"));
        List<Element> repositories = descendants(profile, "repository");

        assertThat(repositories).hasSize(1);
        Element repository = repositories.getFirst();
        assertThat(directText(repository, "id")).isEqualTo("tfi-api-baseline");
        assertThat(directText(repository, "url")).isEqualTo(
                "file://${maven.multiModuleProjectDirectory}/.mvn/api-baseline/repository");
        assertThat(directText(requiredDirectChild(repository, "releases"), "enabled"))
                .isEqualTo("true");
        assertThat(directText(requiredDirectChild(repository, "snapshots"), "enabled"))
                .isEqualTo("false");
    }

    private static Set<String> relativeRegularFiles(Path root) throws IOException {
        assertThat(root).isDirectory();
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .map(Path::toString)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private static Map<String, String> readManifest() throws IOException {
        Path manifestPath = BASELINE_ROOT.resolve("SHA256SUMS");
        assertThat(manifestPath).isRegularFile();
        Map<String, String> entries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(manifestPath)) {
            String[] fields = line.split("  ", 2);
            assertThat(fields).as("invalid SHA256SUMS line: %s", line).hasSize(2);
            assertThat(fields[0]).matches("[0-9a-f]{64}");
            assertThat(entries.put(fields[1], fields[0])).as("duplicate path: %s", fields[1])
                    .isNull();
        }
        return entries;
    }

    private static String sha256(Path file) throws Exception {
        return digest(file, "SHA-256");
    }

    private static String digest(Path file, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static List<Element> descendants(Node parent, String name) {
        List<Element> result = new ArrayList<>();
        for (int index = 0; index < parent.getChildNodes().getLength(); index++) {
            Node child = parent.getChildNodes().item(index);
            if (child instanceof Element element) {
                if (name.equals(element.getLocalName())) {
                    result.add(element);
                }
                result.addAll(descendants(element, name));
            }
        }
        return result;
    }

    private static Element requiredDirectChild(Node parent, String name) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && name.equals(element.getLocalName())) {
                return element;
            }
        }
        throw new AssertionError("missing direct child " + name);
    }

    private static String directText(Node parent, String name) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && name.equals(element.getLocalName())) {
                return element.getTextContent().strip();
            }
        }
        return null;
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("maven.multiModuleProjectDirectory", "."))
                .toAbsolutePath().normalize();
        while (current != null && !Files.isDirectory(current.resolve("tfi-flow-core"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("cannot locate repository root");
        }
        return current;
    }
}
