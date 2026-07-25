import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 从 retained 3.0/final POM 生成可执行兼容矩阵，不把 baseline 引入候选发布闭集。 */
public final class CompatibilityMatrixFixturePreparer {

    private static final String GROUP = "com.syy";
    private static final String BASELINE = "3.0.0";
    private static final List<String> ARTIFACTS = List.of(
            "taskflowinsight-parent", "tfi-flow-core", "tfi-flow-spring-starter",
            "tfi-compare", "tfi-compare-spring-starter", "tfi-ops-spring", "TaskFlowInsight");
    private static final String MATRIX_HEADER =
            "edgeKind\tconsumerGa\tconsumerVersion\tdependencyGa\tdependencyVersion"
                    + "\texpected\tenforcement\tevidenceCommandId";
    private static final String SPEC_HEADER = MATRIX_HEADER
            + "\tconsumerPom\tconsumerPomSha256\tconsumerBinary\tconsumerBinarySha256"
            + "\tdependencyPom\tdependencyPomSha256\tdependencyBinary\tdependencyBinarySha256"
            + "\tconsumerClass\tdependencyClass";

    private CompatibilityMatrixFixturePreparer() {
    }

    public static void main(String[] args) {
        try {
            if (args.length != 5 || !"prepare".equals(args[0])) {
                throw new MatrixFailure("Usage: CompatibilityMatrixFixturePreparer prepare "
                        + "<baseline-repository> <candidate-repository> <final-version> <output-directory>");
            }
            prepare(directory(args[1]), directory(args[2]), fixedVersion(args[3]), emptyDirectory(args[4]));
        } catch (MatrixFailure failure) {
            System.err.println("compatibility matrix fixture rejected: " + failure.getMessage());
            System.exit(1);
        }
    }

    private static void prepare(Path baselineRepo, Path candidateRepo, String finalVersion, Path output) {
        Map<Key, PublishedPom> poms = new LinkedHashMap<>();
        for (String artifact : ARTIFACTS) {
            load(poms, baselineRepo, artifact, BASELINE,
                    !"tfi-compare-spring-starter".equals(artifact), "BASELINE");
            load(poms, candidateRepo, artifact, finalVersion, true, "CANDIDATE");
        }
        List<Row> rows = matrixRows(poms, finalVersion);
        write(output.resolve("compatibility-matrix.tsv"), lines(MATRIX_HEADER, rows, false));
        write(output.resolve("compatibility-row-specs.tsv"), lines(SPEC_HEADER, rows, true));
        write(output.resolve("compatibility-pom-inventory.tsv"), inventoryLines(poms, finalVersion));
        write(output.resolve("compatibility-edge-inventory.tsv"), edgeLines(poms));
        System.out.println("COMPATIBILITY_MATRIX_PREPARED\t" + rows.size());
    }

    private static void load(
            Map<Key, PublishedPom> target, Path repo, String artifact, String version,
            boolean required, String source) {
        Path pomPath = repo.resolve("com/syy").resolve(artifact).resolve(version)
                .resolve(artifact + "-" + version + ".pom");
        if (!Files.isRegularFile(pomPath, LinkOption.NOFOLLOW_LINKS)) {
            if (required) {
                throw new MatrixFailure("required retained POM is missing: " + pomPath);
            }
            return;
        }
        PublishedPom pom = PublishedPom.read(repo, pomPath, artifact, version, source);
        if (target.putIfAbsent(new Key(artifact, version), pom) != null) {
            throw new MatrixFailure("duplicate retained POM coordinate");
        }
    }

    private static List<Row> matrixRows(Map<Key, PublishedPom> poms, String finalVersion) {
        List<RowDraft> drafts = new ArrayList<>();
        for (PublishedPom consumer : poms.values()) {
            if (consumer.parentArtifact() != null) {
                PublishedPom parent = required(poms, consumer.parentArtifact(), consumer.parentVersion());
                drafts.add(RowDraft.parent(consumer, parent));
            }
            for (Edge edge : consumer.edges()) {
                for (String dependencyVersion : List.of(BASELINE, finalVersion)) {
                    PublishedPom dependency = poms.get(new Key(edge.artifact(), dependencyVersion));
                    if (dependency == null) {
                        continue;
                    }
                    drafts.add(RowDraft.dependency(consumer, dependency, edge.optional()));
                }
            }
        }
        drafts.sort(Comparator.comparing(RowDraft::edgeKind)
                .thenComparing(RowDraft::consumerGa)
                .thenComparing(RowDraft::consumerVersion)
                .thenComparing(RowDraft::dependencyGa)
                .thenComparing(RowDraft::dependencyVersion));
        Set<String> keys = new LinkedHashSet<>();
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            RowDraft draft = drafts.get(index);
            if (!keys.add(draft.key())) {
                throw new MatrixFailure("duplicate compatibility matrix row: " + draft.key());
            }
            rows.add(draft.numbered(index + 1));
        }
        return List.copyOf(rows);
    }

    private static PublishedPom required(Map<Key, PublishedPom> poms, String artifact, String version) {
        PublishedPom result = poms.get(new Key(artifact, version));
        if (result == null) {
            throw new MatrixFailure("retained parent POM is missing: " + artifact + ":" + version);
        }
        return result;
    }

    private static List<String> lines(String header, List<Row> rows, boolean specs) {
        List<String> lines = new ArrayList<>(List.of(header));
        rows.forEach(row -> lines.add(specs ? row.specTsv() : row.matrixTsv()));
        return List.copyOf(lines);
    }

    private static List<String> inventoryLines(Map<Key, PublishedPom> poms, String finalVersion) {
        List<String> lines = new ArrayList<>(List.of(
                "source\tga\tversion\tpomPath\tpomSha256\tbinaryPath\tbinarySha256\tstatus"));
        for (String version : List.of(BASELINE, finalVersion)) {
            for (String artifact : ARTIFACTS) {
                PublishedPom pom = poms.get(new Key(artifact, version));
                String source = BASELINE.equals(version) ? "BASELINE" : "CANDIDATE";
                if (pom == null) {
                    lines.add(source + "\t" + GROUP + ":" + artifact + "\t" + version
                            + "\t-\t-\t-\t-\tABSENT");
                } else {
                    lines.add(pom.inventoryTsv());
                }
            }
        }
        return List.copyOf(lines);
    }

    private static List<String> edgeLines(Map<Key, PublishedPom> poms) {
        List<String> lines = new ArrayList<>(List.of(
                "source\tedgeKind\tconsumerGa\tconsumerVersion\tdependencyGa"
                        + "\tdeclaredVersion\toptional"));
        poms.values().stream().sorted(Comparator.comparing(PublishedPom::source)
                .thenComparing(PublishedPom::artifact)).forEach(pom -> {
            if (pom.parentArtifact() != null) {
                lines.add(pom.source() + "\tPARENT\t" + pom.ga() + "\t" + pom.version()
                        + "\t" + GROUP + ":" + pom.parentArtifact() + "\t"
                        + pom.parentVersion() + "\tfalse");
            }
            pom.edges().forEach(edge -> lines.add(pom.source() + "\tDEPENDENCY\t" + pom.ga()
                    + "\t" + pom.version() + "\t" + GROUP + ":" + edge.artifact()
                    + "\t" + edge.version() + "\t" + edge.optional()));
        });
        return List.copyOf(lines);
    }

    private record PublishedPom(
            Path repository,
            String artifact,
            String version,
            String source,
            String pomPath,
            String pomSha,
            String binaryPath,
            String binarySha,
            String parentArtifact,
            String parentVersion,
            List<Edge> edges) {

        private static PublishedPom read(
                Path repository, Path pomPath, String artifact, String version, String source) {
            Element project = parse(pomPath);
            if (!artifact.equals(text(project, "artifactId"))) {
                throw new MatrixFailure("retained POM artifactId differs from repository path");
            }
            String parentArtifact = null;
            String parentVersion = null;
            List<Element> parents = children(project, "parent");
            if (parents.size() > 1) {
                throw new MatrixFailure("retained POM has duplicate parent elements");
            }
            if (!parents.isEmpty() && GROUP.equals(text(parents.getFirst(), "groupId"))) {
                String candidate = text(parents.getFirst(), "artifactId");
                if (ARTIFACTS.contains(candidate)) {
                    parentArtifact = candidate;
                    parentVersion = resolveVersion(text(parents.getFirst(), "version"), version);
                }
            }
            List<Edge> edges = new ArrayList<>();
            for (Element dependencies : children(project, "dependencies")) {
                for (Element dependency : children(dependencies, "dependency")) {
                    if (!GROUP.equals(text(dependency, "groupId"))) {
                        continue;
                    }
                    String dependencyArtifact = text(dependency, "artifactId");
                    if (!ARTIFACTS.contains(dependencyArtifact)) {
                        throw new MatrixFailure("unknown retained TFI dependency: " + dependencyArtifact);
                    }
                    String scope = optionalText(dependency, "scope");
                    if ("test".equals(scope) || "provided".equals(scope)) {
                        continue;
                    }
                    edges.add(new Edge(dependencyArtifact,
                            resolveVersion(text(dependency, "version"), version),
                            "true".equals(optionalText(dependency, "optional"))));
                }
            }
            edges.sort(Comparator.comparing(Edge::artifact));
            String relativePom = posix(repository.relativize(pomPath));
            Path binary = repository.resolve("com/syy").resolve(artifact).resolve(version)
                    .resolve(artifact + "-" + version + ".jar");
            boolean parentPom = "taskflowinsight-parent".equals(artifact);
            if (!parentPom && !Files.isRegularFile(binary, LinkOption.NOFOLLOW_LINKS)) {
                throw new MatrixFailure("retained binary is missing for " + artifact + ":" + version);
            }
            return new PublishedPom(
                    repository, artifact, version, source, relativePom, sha256(pomPath),
                    parentPom ? "-" : posix(repository.relativize(binary)),
                    parentPom ? "-" : sha256(binary), parentArtifact, parentVersion, List.copyOf(edges));
        }

        private String ga() {
            return GROUP + ":" + artifact;
        }

        private String typeName() {
            return switch (artifact) {
                case "tfi-flow-core" -> "com.syy.taskflowinsight.api.TfiFlow";
                case "tfi-flow-spring-starter" -> BASELINE.equals(version)
                        ? "com.syy.taskflowinsight.config.ContextMonitoringAutoConfiguration"
                        : "com.syy.taskflowinsight.aspect.TfiTaskDeepTrackingDelegate";
                case "tfi-compare" -> "com.syy.taskflowinsight.tracking.compare.CompareResult";
                case "tfi-compare-spring-starter" ->
                        "com.syy.taskflowinsight.compare.spring.TfiCompareProperties";
                case "tfi-ops-spring" -> BASELINE.equals(version)
                        ? "com.syy.taskflowinsight.health.TfiHealthIndicator"
                        : "com.syy.taskflowinsight.ops.compare.CompareObservationAutoConfiguration";
                case "TaskFlowInsight" -> "com.syy.taskflowinsight.api.TFI";
                default -> "-";
            };
        }

        private String inventoryTsv() {
            return source + "\t" + ga() + "\t" + version + "\t" + pomPath + "\t" + pomSha
                    + "\t" + binaryPath + "\t" + binarySha + "\tPRESENT";
        }
    }

    private record Edge(String artifact, String version, boolean optional) {
    }

    private record RowDraft(
            String edgeKind,
            PublishedPom consumer,
            PublishedPom dependency,
            String expected,
            String enforcement) {

        private static RowDraft parent(PublishedPom consumer, PublishedPom dependency) {
            return new RowDraft("PARENT", consumer, dependency,
                    "SUPPORTED", "MAVEN_MODEL_VALIDATION");
        }

        private static RowDraft dependency(
                PublishedPom consumer, PublishedPom dependency, boolean optional) {
            if (consumer.version().equals(dependency.version())) {
                return new RowDraft("DEPENDENCY", consumer, dependency,
                        "SUPPORTED", "ARTIFACT_TEST");
            }
            if (!optional) {
                return new RowDraft("DEPENDENCY", consumer, dependency,
                        "REJECTED", "DEPENDENCY_CONVERGENCE");
            }
            if ("tfi-ops-spring".equals(consumer.artifact()) && BASELINE.equals(consumer.version())) {
                return new RowDraft("DEPENDENCY", consumer, dependency,
                        "SUPPORTED", "ARTIFACT_TEST");
            }
            return new RowDraft("DEPENDENCY", consumer, dependency,
                    "REJECTED", "STARTUP_FAIL_FAST");
        }

        private String consumerGa() {
            return consumer.ga();
        }

        private String consumerVersion() {
            return consumer.version();
        }

        private String dependencyGa() {
            return dependency.ga();
        }

        private String dependencyVersion() {
            return dependency.version();
        }

        private String key() {
            return edgeKind + "\t" + consumerGa() + "\t" + consumerVersion()
                    + "\t" + dependencyGa() + "\t" + dependencyVersion();
        }

        private Row numbered(int ordinal) {
            return new Row(this, "COMPAT-" + String.format("%03d", ordinal));
        }
    }

    private record Row(RowDraft draft, String commandId) {
        private String matrixTsv() {
            return draft.key() + "\t" + draft.expected() + "\t" + draft.enforcement() + "\t" + commandId;
        }

        private String specTsv() {
            PublishedPom consumer = draft.consumer();
            PublishedPom dependency = draft.dependency();
            return matrixTsv() + "\t" + consumer.pomPath() + "\t" + consumer.pomSha()
                    + "\t" + consumer.binaryPath() + "\t" + consumer.binarySha()
                    + "\t" + dependency.pomPath() + "\t" + dependency.pomSha()
                    + "\t" + dependency.binaryPath() + "\t" + dependency.binarySha()
                    + "\t" + consumer.typeName() + "\t" + dependency.typeName();
        }
    }

    private static Element parse(Path path) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            return builder.parse(path.toFile()).getDocumentElement();
        } catch (Exception failure) {
            throw new MatrixFailure("retained POM is not secure well-formed XML: " + path);
        }
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element element && name.equals(element.getLocalName())) {
                result.add(element);
            }
        }
        return List.copyOf(result);
    }

    private static String text(Element parent, String name) {
        List<Element> matches = children(parent, name);
        if (matches.size() != 1 || matches.getFirst().getTextContent().strip().isEmpty()) {
            throw new MatrixFailure("retained POM must contain one non-empty " + name);
        }
        return matches.getFirst().getTextContent().strip();
    }

    private static String optionalText(Element parent, String name) {
        List<Element> matches = children(parent, name);
        if (matches.size() > 1) {
            throw new MatrixFailure("retained POM contains duplicate " + name);
        }
        return matches.isEmpty() ? "" : matches.getFirst().getTextContent().strip();
    }

    private static String resolveVersion(String value, String projectVersion) {
        if (value.equals(projectVersion)
                || value.equals("${project.version}")
                || value.equals("${tfi-flow-core.version}")) {
            return projectVersion;
        }
        throw new MatrixFailure("retained TFI edge has an unresolved or foreign version: " + value);
    }

    private static Path directory(String value) {
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new MatrixFailure("repository is not a directory: " + path);
        }
        return path;
    }

    private static Path emptyDirectory(String value) {
        Path path = Path.of(value).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
            try (var entries = Files.list(path)) {
                if (entries.findAny().isPresent()) {
                    throw new MatrixFailure("output directory must be empty");
                }
            }
            return path;
        } catch (MatrixFailure failure) {
            throw failure;
        } catch (Exception failure) {
            throw new MatrixFailure("output directory cannot be prepared");
        }
    }

    private static String fixedVersion(String value) {
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]*")
                || value.equals(BASELINE)
                || value.toUpperCase().contains("SNAPSHOT")
                || Set.of("LATEST", "RELEASE").contains(value.toUpperCase())) {
            throw new MatrixFailure("final version must be fixed and differ from 3.0.0");
        }
        return value;
    }

    private static void write(Path path, List<String> lines) {
        try {
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (Exception failure) {
            throw new MatrixFailure("matrix evidence cannot be written: " + path.getFileName());
        }
    }

    private static String sha256(Path path) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (Exception failure) {
            throw new MatrixFailure("retained artifact cannot be hashed: " + path);
        }
    }

    private static String posix(Path path) {
        String value = path.toString().replace(path.getFileSystem().getSeparator(), "/");
        if (value.isEmpty() || value.startsWith("/") || value.contains("../") || value.contains("\\")) {
            throw new MatrixFailure("retained repository path is not relative POSIX");
        }
        return value;
    }

    private record Key(String artifact, String version) {
    }

    private static final class MatrixFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private MatrixFailure(String message) {
            super(message, null, false, false);
        }
    }
}
