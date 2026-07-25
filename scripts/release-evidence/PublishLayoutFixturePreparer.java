import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** 为 publish-layout 集成测试封存 fixed-final 构建输出，不拥有构建或发布能力。 */
public final class PublishLayoutFixturePreparer {
    private static final String USAGE =
            "Usage: PublishLayoutFixturePreparer prepare <repository-root> <evidence-dir> <final-version>";
    private static final Pattern FIXED_VERSION = Pattern.compile(
            "(?i)^(?!.*SNAPSHOT)(?!LATEST$)(?!RELEASE$)(?!.*\\$\\{)"
                    + "(?!.*[\\[\\](),])[A-Z0-9][A-Z0-9._-]*$");
    private static final List<String> POLICY_KEYS = List.of(
            "policyId", "reviewAssignmentId", "repository", "protectedRef", "candidateRevision",
            "finalVersion", "releaseTarget", "externalPublicationAuthority",
            "externalPublicationAuthoritySha256", "publishArtifactManifest",
            "publishArtifactManifestSha256", "runtimePerformancePolicy",
            "runtimePerformancePolicySha256", "compatibilityMatrix", "compatibilityMatrixSha256",
            "releaseExecutionPolicy", "releaseExecutionPolicySha256", "buildToolchainManifest",
            "buildToolchainManifestSha256", "productionAuthoritiesManifest",
            "productionAuthoritiesManifestSha256", "trustedBuilder", "provenanceWorkflow",
            "licensePolicy", "licensePolicySha256", "vulnerabilityScanner",
            "vulnerabilityFailCvssThreshold", "vulnerabilityDbMaxAgeHours", "secretScanner",
            "sbomGenerator", "sbomFormat", "requiredSignatures");
    private static final List<Module> MODULES = List.of(
            new Module("tfi-flow-core", "tfi-flow-core"),
            new Module("tfi-flow-spring-starter", "tfi-flow-spring-starter"),
            new Module("tfi-compare", "tfi-compare"),
            new Module("tfi-compare-spring-starter", "tfi-compare-spring-starter"),
            new Module("tfi-ops-spring", "tfi-ops-spring"),
            new Module("tfi-all", "TaskFlowInsight"));

    private PublishLayoutFixturePreparer() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (FixtureFailure failure) {
            System.err.println(failure.getMessage());
            System.exit(2);
        }
    }

    private static void run(String[] args) {
        if (args.length != 4 || !"prepare".equals(args[0])) {
            throw new FixtureFailure(USAGE);
        }
        Path root = directory(args[1], "repository root");
        Path evidence = emptyDirectory(args[2]);
        String version = args[3];
        if (!FIXED_VERSION.matcher(version).matches() || "3.0.0".equals(version)) {
            throw new FixtureFailure("final version must be fixed and must not equal 3.0.0");
        }
        prepare(root, evidence, version);
        System.out.println(evidence.resolve("policy/production-policy.tsv"));
    }

    private static void prepare(Path root, Path evidence, String version) {
        List<Primary> primaries = primaries(version);
        List<String> buildInputs = new ArrayList<>();
        buildInputs.add(
                "kind\tsubjectCoordinate\trole\tmodulePath\tsourceRevisionPath\tretainedPath\tsha256");
        for (int index = 0; index < primaries.size(); index++) {
            Primary primary = primaries.get(index);
            Path source = primary.source(root, version);
            String retainedPath = "build/primaries/" + (index + 1) + "-" + primary.fileName(version);
            byte[] bytes = copy(source, evidence.resolve(retainedPath), "primary build output");
            buildInputs.add("PRIMARY\t" + primary.coordinate() + "\t" + primary.role() + "\t"
                    + primary.modulePath() + "\t-\t" + retainedPath + "\t" + sha256(bytes));
        }

        byte[] license = copy(root.resolve("LICENSE"), evidence.resolve("source-revision/LICENSE"),
                "root LICENSE");
        buildInputs.add("LICENSE\tcom.syy:taskflowinsight-parent:pom:" + version
                + "\tLICENSE\t.\tLICENSE\tsource-revision/LICENSE\t" + sha256(license));
        for (Module module : MODULES) {
            Path sourceRoot = root.resolve(module.modulePath()).resolve("src/main/java");
            for (Path source : productionSources(sourceRoot)) {
                String entry = posix(sourceRoot.relativize(source));
                String revisionPath = module.modulePath() + "/src/main/java/" + entry;
                String retainedPath = "source-revision/" + revisionPath;
                byte[] bytes = copy(source, evidence.resolve(retainedPath), "production source");
                buildInputs.add("SOURCE\tcom.syy:" + module.artifact() + ":jar:" + version
                        + "\tSOURCE\t" + module.modulePath() + "\t" + revisionPath + "\t"
                        + retainedPath + "\t" + sha256(bytes));
            }
        }
        writeLines(evidence.resolve("metadata/publish-build-inputs.tsv"), buildInputs);

        Path policyDirectory = evidence.resolve("policy");
        Path publishManifest = policyDirectory.resolve("publish-artifact-manifest.tsv");
        writeLines(publishManifest, publishManifest(primaries));
        writePolicy(policyDirectory.resolve("production-policy.tsv"), version,
                publishManifest.getFileName().toString(), sha256(read(publishManifest, "publish manifest")));
    }

    private static List<Primary> primaries(String version) {
        List<Primary> result = new ArrayList<>();
        result.add(new Primary(".", "taskflowinsight-parent", "POM", "pom", null, version));
        for (Module module : MODULES) {
            result.add(new Primary(module.modulePath(), module.artifact(), "POM", "pom", null, version));
            result.add(new Primary(module.modulePath(), module.artifact(), "BINARY", "jar", null, version));
            result.add(new Primary(module.modulePath(), module.artifact(), "SOURCES", "jar", "sources", version));
            result.add(new Primary(module.modulePath(), module.artifact(), "JAVADOC", "jar", "javadoc", version));
        }
        return List.copyOf(result);
    }

    private static List<String> publishManifest(List<Primary> primaries) {
        List<String> lines = new ArrayList<>();
        lines.add("ordinal\tsubjectOrdinal\tsubjectCoordinate\trepositoryPath\trole\tsidecarKind");
        for (int index = 0; index < primaries.size(); index++) {
            Primary primary = primaries.get(index);
            lines.add((index + 1) + "\t-\t" + primary.coordinate() + "\t"
                    + primary.repositoryPath() + "\t" + primary.role() + "\t-");
        }
        int ordinal = primaries.size() + 1;
        for (int index = 0; index < primaries.size(); index++) {
            Primary primary = primaries.get(index);
            int subject = index + 1;
            lines.add(ordinal++ + "\t" + subject + "\t" + primary.coordinate() + "\t"
                    + primary.repositoryPath() + ".sha256\tCHECKSUM\tSHA256");
            lines.add(ordinal++ + "\t" + subject + "\t" + primary.coordinate() + "\t"
                    + primary.repositoryPath() + ".sha512\tCHECKSUM\tSHA512");
        }
        return List.copyOf(lines);
    }

    private static void writePolicy(Path path, String version, String manifestPath, String manifestSha) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : POLICY_KEYS) {
            values.put(key, "fixture-" + key);
        }
        values.put("policyId", "authority:publish-layout-fixture");
        values.put("candidateRevision", "a".repeat(40));
        values.put("finalVersion", version);
        values.put("publishArtifactManifest", manifestPath);
        values.put("publishArtifactManifestSha256", manifestSha);
        writeLines(path, POLICY_KEYS.stream().map(key -> key + "\t" + values.get(key)).toList());
    }

    private static List<Path> productionSources(Path root) {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new FixtureFailure("module production source directory is missing");
        }
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> sources = paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted(Comparator.comparing(path -> posix(root.relativize(path))))
                    .toList();
            if (sources.isEmpty()) {
                throw new FixtureFailure("module production source closure is empty");
            }
            return sources;
        } catch (IOException failure) {
            throw new FixtureFailure("module production source closure cannot be enumerated");
        }
    }

    private static byte[] copy(Path source, Path target, String description) {
        byte[] bytes = read(source, description);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return bytes;
        } catch (IOException failure) {
            throw new FixtureFailure(description + " cannot be retained");
        }
    }

    private static byte[] read(Path path, String description) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(path)) {
            throw new FixtureFailure(description + " is not a readable regular file");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException failure) {
            throw new FixtureFailure(description + " cannot be read");
        }
    }

    private static void writeLines(Path path, List<String> lines) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new FixtureFailure("fixture manifest cannot be written");
        }
    }

    private static Path directory(String value, String description) {
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new FixtureFailure(description + " is not a directory");
        }
        return path;
    }

    private static Path emptyDirectory(String value) {
        Path path = directory(value, "evidence directory");
        try (Stream<Path> entries = Files.list(path)) {
            if (entries.findAny().isPresent()) {
                throw new FixtureFailure("evidence directory must be empty");
            }
        } catch (IOException failure) {
            throw new FixtureFailure("evidence directory cannot be inspected");
        }
        return path;
    }

    private static String posix(Path path) {
        String result = path.toString().replace(path.getFileSystem().getSeparator(), "/");
        if (result.isEmpty() || result.startsWith("/") || result.contains("\\") || result.contains("../")) {
            throw new FixtureFailure("fixture path is not a relative POSIX path");
        }
        return result;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record Module(String modulePath, String artifact) {
    }

    private record Primary(
            String modulePath,
            String artifact,
            String role,
            String extension,
            String classifier,
            String version) {

        private String coordinate() {
            String classifierPart = classifier == null ? "" : ":" + classifier;
            return "com.syy:" + artifact + ":" + extension + classifierPart + ":" + version;
        }

        private String repositoryPath() {
            return "com/syy/" + artifact + "/" + version + "/" + fileName(version);
        }

        private String fileName(String fixedVersion) {
            String classifierPart = classifier == null ? "" : "-" + classifier;
            return artifact + "-" + fixedVersion + classifierPart + "." + extension;
        }

        private Path source(Path root, String fixedVersion) {
            if (".".equals(modulePath)) {
                return root.resolve("target/flattened-pom.xml");
            }
            Path target = root.resolve(modulePath).resolve("target");
            return "POM".equals(role)
                    ? target.resolve("flattened-pom.xml")
                    : target.resolve(fileName(fixedVersion));
        }
    }

    private static final class FixtureFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private FixtureFailure(String message) {
            super(message, null, false, false);
        }
    }
}
