import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 只在本地证据目录内组装发布制品，不拥有网络、签名或发布能力。 */
public final class PublishArtifactAssembler {
    private static final String USAGE =
            "Usage: PublishArtifactAssembler assemble <evidence-dir> <production-policy.tsv>";
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
    private static final Pattern FIXED_VERSION = Pattern.compile(
            "(?i)^(?!.*SNAPSHOT)(?!LATEST$)(?!RELEASE$)(?!.*\\$\\{)"
                    + "(?!.*[\\[\\](),])[A-Z0-9][A-Z0-9._-]*$");

    private PublishArtifactAssembler() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (AssemblerFailure failure) {
            System.err.println(failure.getMessage());
            System.exit(2);
        }
    }

    private static void run(String[] args) {
        if (args.length == 0 || !"assemble".equals(args[0])) {
            String mode = args.length == 0 ? "<missing>" : args[0];
            throw new AssemblerFailure("unknown mode: " + mode + System.lineSeparator() + USAGE);
        }
        if (args.length != 3) {
            throw new AssemblerFailure(USAGE);
        }

        Path evidenceDirectory = Path.of(args[1]).toAbsolutePath().normalize();
        if (!Files.isDirectory(evidenceDirectory, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(evidenceDirectory)) {
            throw new AssemblerFailure("evidence directory is not a readable directory");
        }
        Path policy = Path.of(args[2]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(policy, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(policy)) {
            throw new AssemblerFailure("production policy is not a readable regular file");
        }
        Policy authority = Policy.load(policy);
        PublishManifest manifest = PublishManifest.load(
                authority.publishManifest(), authority.finalVersion());
        BuildInputs inputs = BuildInputs.load(evidenceDirectory, manifest);
        assemble(evidenceDirectory, manifest, inputs);
    }

    private static List<String> decodeLines(Path file, String description) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException failure) {
            throw new AssemblerFailure(description + " cannot be read");
        }
        if (bytes.length >= 3
                && Byte.toUnsignedInt(bytes[0]) == 0xEF
                && Byte.toUnsignedInt(bytes[1]) == 0xBB
                && Byte.toUnsignedInt(bytes[2]) == 0xBF) {
            throw new AssemblerFailure(description + " must not contain a UTF-8 BOM");
        }
        String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new AssemblerFailure(description + " must be valid UTF-8");
        }
        if (content.indexOf('\r') >= 0) {
            throw new AssemblerFailure(description + " must use LF line endings");
        }
        String[] split = content.split("\n", -1);
        int length = split.length;
        if (length > 0 && split[length - 1].isEmpty()) {
            length--;
        }
        List<String> lines = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            lines.add(split[index]);
        }
        return List.copyOf(lines);
    }

    private static Path resolveSealedFile(Path base, String relative, String description) {
        if (relative.isEmpty() || relative.startsWith("/") || relative.contains("\\")) {
            throw new AssemblerFailure(description + " must be a relative POSIX path");
        }
        Path current = base.toAbsolutePath().normalize();
        String[] parts = relative.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new AssemblerFailure(description + " must be a relative POSIX path");
            }
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new AssemblerFailure(description + " must not traverse a symbolic link");
            }
        }
        if (!Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(current)) {
            throw new AssemblerFailure(description + " is not a readable regular file");
        }
        return current;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record Policy(String finalVersion, Path publishManifest) {
        private static Policy load(Path path) {
            List<String> lines = decodeLines(path, "production policy");
            if (lines.size() != POLICY_KEYS.size()) {
                throw new AssemblerFailure("production policy must contain exactly 32 lines");
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int index = 0; index < POLICY_KEYS.size(); index++) {
                String line = lines.get(index);
                int separator = line.indexOf('\t');
                if (separator <= 0 || separator != line.lastIndexOf('\t')) {
                    throw new AssemblerFailure("policy line " + (index + 1) + " must have two TSV columns");
                }
                String key = line.substring(0, separator);
                String expectedKey = POLICY_KEYS.get(index);
                if (!expectedKey.equals(key)) {
                    throw new AssemblerFailure(
                            "policy key at line " + (index + 1) + " must be " + expectedKey);
                }
                String value = line.substring(separator + 1);
                if (value.isEmpty()) {
                    throw new AssemblerFailure("policy value at line " + (index + 1) + " must not be empty");
                }
                values.put(key, value);
            }

            String version = values.get("finalVersion");
            if (!FIXED_VERSION.matcher(version).matches() || "3.0.0".equals(version)) {
                throw new AssemblerFailure("finalVersion must be a fixed non-SNAPSHOT version other than 3.0.0");
            }
            Path manifest = resolveSealedFile(
                    path.toAbsolutePath().normalize().getParent(),
                    values.get("publishArtifactManifest"),
                    "publishArtifactManifest");
            String actualDigest;
            try {
                actualDigest = sha256(Files.readAllBytes(manifest));
            } catch (IOException failure) {
                throw new AssemblerFailure("publishArtifactManifest cannot be read");
            }
            if (!actualDigest.equals(values.get("publishArtifactManifestSha256"))) {
                throw new AssemblerFailure("publishArtifactManifestSha256 does not match retained bytes");
            }
            return new Policy(version, manifest);
        }
    }

    private record PublishManifest(List<ManifestRow> rows) {
        private static final String HEADER =
                "ordinal\tsubjectOrdinal\tsubjectCoordinate\trepositoryPath\trole\tsidecarKind";

        private static PublishManifest load(Path path, String finalVersion) {
            List<String> lines = decodeLines(path, "publishArtifactManifest");
            if (lines.size() < 2 || !HEADER.equals(lines.getFirst())) {
                throw new AssemblerFailure("publishArtifactManifest has an invalid header or no data rows");
            }
            Map<String, String> missingPrimaries = expectedPrimaries(finalVersion);
            Map<Integer, ManifestRow> primaries = new LinkedHashMap<>();
            Map<Integer, Set<String>> checksums = new LinkedHashMap<>();
            Set<String> paths = new HashSet<>();
            List<ManifestRow> rows = new ArrayList<>();

            for (int index = 1; index < lines.size(); index++) {
                String[] columns = lines.get(index).split("\t", -1);
                if (columns.length != 6) {
                    throw new AssemblerFailure("publish manifest row must have exactly six columns");
                }
                int expectedOrdinal = index;
                int ordinal = positiveInteger(columns[0], "ordinal");
                if (ordinal != expectedOrdinal) {
                    throw new AssemblerFailure("publish manifest ordinal must be continuous from 1");
                }
                requireRelativeMavenPath(columns[3]);
                if (!paths.add(columns[3])) {
                    throw new AssemblerFailure("publish manifest contains a duplicate repositoryPath");
                }
                ManifestRow row = new ManifestRow(
                        ordinal, columns[1], columns[2], columns[3], columns[4], columns[5]);
                if (isPrimaryRole(row.role())) {
                    validatePrimary(row, finalVersion, missingPrimaries);
                    primaries.put(ordinal, row);
                    checksums.put(ordinal, new LinkedHashSet<>());
                } else {
                    validateSidecar(row, primaries, checksums);
                }
                rows.add(row);
            }
            if (!missingPrimaries.isEmpty()) {
                throw new AssemblerFailure("publish primary closure is missing required entry");
            }
            for (Set<String> kinds : checksums.values()) {
                if (!kinds.equals(Set.of("SHA256", "SHA512"))) {
                    throw new AssemblerFailure("every primary must have exactly SHA256 and SHA512 sidecars");
                }
            }
            return new PublishManifest(List.copyOf(rows));
        }

        private static void validatePrimary(
                ManifestRow row, String finalVersion, Map<String, String> missingPrimaries) {
            if (!"-".equals(row.subjectOrdinal()) || !"-".equals(row.sidecarKind())) {
                throw new AssemblerFailure("primary rows must use dash subject and sidecar fields");
            }
            Coordinate coordinate = Coordinate.parse(row.coordinate());
            if (!finalVersion.equals(coordinate.version())) {
                throw new AssemblerFailure("primary coordinate version must equal finalVersion");
            }
            validateRoleCoordinate(row.role(), coordinate);
            if (!coordinate.repositoryPath().equals(row.repositoryPath())) {
                throw new AssemblerFailure("primary repositoryPath does not match its Maven coordinate");
            }
            String expectedRole = missingPrimaries.remove(row.coordinate());
            if (!row.role().equals(expectedRole)) {
                throw new AssemblerFailure("publish primary closure contains unexpected entry");
            }
        }

        private static void validateSidecar(
                ManifestRow row,
                Map<Integer, ManifestRow> primaries,
                Map<Integer, Set<String>> checksums) {
            int subject = positiveInteger(row.subjectOrdinal(), "subjectOrdinal");
            ManifestRow primary = primaries.get(subject);
            if (primary == null || subject >= row.ordinal()) {
                throw new AssemblerFailure("sidecar must reference an earlier primary ordinal");
            }
            if (!primary.coordinate().equals(row.coordinate())) {
                throw new AssemblerFailure("sidecar coordinate must equal its primary coordinate");
            }
            String suffix;
            if ("CHECKSUM".equals(row.role())) {
                suffix = switch (row.sidecarKind()) {
                    case "SHA256" -> ".sha256";
                    case "SHA512" -> ".sha512";
                    default -> throw new AssemblerFailure("unknown checksum sidecar kind");
                };
                if (!checksums.get(subject).add(row.sidecarKind())) {
                    throw new AssemblerFailure("primary contains a duplicate checksum sidecar");
                }
            } else if ("SIGNATURE".equals(row.role())) {
                suffix = switch (row.sidecarKind()) {
                    case "PGP" -> ".asc";
                    case "SIGSTORE" -> ".sigstore.json";
                    default -> throw new AssemblerFailure("unknown signature sidecar kind");
                };
            } else {
                throw new AssemblerFailure("unknown publish manifest role");
            }
            if (!row.repositoryPath().equals(primary.repositoryPath() + suffix)) {
                throw new AssemblerFailure("sidecar repositoryPath does not match its primary");
            }
        }

        private static Map<String, String> expectedPrimaries(String version) {
            Map<String, String> expected = new LinkedHashMap<>();
            addPrimary(expected, "taskflowinsight-parent", "POM", "pom", null, version);
            for (String artifact : List.of(
                    "tfi-flow-core", "tfi-flow-spring-starter", "tfi-compare",
                    "tfi-compare-spring-starter", "tfi-ops-spring", "TaskFlowInsight")) {
                addPrimary(expected, artifact, "POM", "pom", null, version);
                addPrimary(expected, artifact, "BINARY", "jar", null, version);
                addPrimary(expected, artifact, "SOURCES", "jar", "sources", version);
                addPrimary(expected, artifact, "JAVADOC", "jar", "javadoc", version);
            }
            return expected;
        }

        private static void addPrimary(
                Map<String, String> target,
                String artifact,
                String role,
                String extension,
                String classifier,
                String version) {
            String classifierPart = classifier == null ? "" : ":" + classifier;
            target.put("com.syy:" + artifact + ":" + extension + classifierPart + ":" + version, role);
        }
    }

    private static boolean isPrimaryRole(String role) {
        return "POM".equals(role)
                || "BINARY".equals(role)
                || "SOURCES".equals(role)
                || "JAVADOC".equals(role);
    }

    private static void validateRoleCoordinate(String role, Coordinate coordinate) {
        boolean valid = switch (role) {
            case "POM" -> "pom".equals(coordinate.extension()) && coordinate.classifier() == null;
            case "BINARY" -> "jar".equals(coordinate.extension()) && coordinate.classifier() == null;
            case "SOURCES" -> "jar".equals(coordinate.extension())
                    && "sources".equals(coordinate.classifier());
            case "JAVADOC" -> "jar".equals(coordinate.extension())
                    && "javadoc".equals(coordinate.classifier());
            default -> false;
        };
        if (!valid) {
            throw new AssemblerFailure("primary role does not match coordinate extension/classifier");
        }
    }

    private static int positiveInteger(String value, String field) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0 || !Integer.toString(parsed).equals(value)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException failure) {
            throw new AssemblerFailure(field + " must be a canonical positive integer");
        }
    }

    private static void requireRelativeMavenPath(String value) {
        if (value.isEmpty() || value.startsWith("/") || value.contains("\\")) {
            throw new AssemblerFailure("repositoryPath must be a relative Maven2 path");
        }
        for (String part : value.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new AssemblerFailure("repositoryPath must be a relative Maven2 path");
            }
        }
    }

    private record ManifestRow(
            int ordinal,
            String subjectOrdinal,
            String coordinate,
            String repositoryPath,
            String role,
            String sidecarKind) {

        private String tsv() {
            return ordinal + "\t" + subjectOrdinal + "\t" + coordinate + "\t"
                    + repositoryPath + "\t" + role + "\t" + sidecarKind;
        }
    }

    private record Coordinate(
            String group,
            String artifact,
            String extension,
            String classifier,
            String version) {

        private static Coordinate parse(String raw) {
            String[] parts = raw.split(":", -1);
            if ((parts.length != 4 && parts.length != 5)
                    || java.util.Arrays.stream(parts).anyMatch(String::isEmpty)) {
                throw new AssemblerFailure("subjectCoordinate is not canonical");
            }
            return parts.length == 4
                    ? new Coordinate(parts[0], parts[1], parts[2], null, parts[3])
                    : new Coordinate(parts[0], parts[1], parts[2], parts[3], parts[4]);
        }

        private String repositoryPath() {
            String classifierPart = classifier == null ? "" : "-" + classifier;
            return group.replace('.', '/') + "/" + artifact + "/" + version + "/"
                    + artifact + "-" + version + classifierPart + "." + extension;
        }

        private String canonical() {
            String classifierPart = classifier == null ? "" : ":" + classifier;
            return group + ":" + artifact + ":" + extension + classifierPart + ":" + version;
        }

        private Coordinate withClassifier(String value) {
            return new Coordinate(group, artifact, extension, value, version);
        }

        private Coordinate withExtension(String value) {
            return new Coordinate(group, artifact, value, null, version);
        }
    }

    private record BuildInputs(
            Map<String, BuildInput> primaries,
            Map<String, List<BuildInput>> sourcesByBinaryCoordinate,
            BuildInput license) {
        private static final String HEADER =
                "kind\tsubjectCoordinate\trole\tmodulePath\tsourceRevisionPath\tretainedPath\tsha256";

        private static BuildInputs load(Path evidence, PublishManifest manifest) {
            Path path = resolveSealedFile(evidence, "metadata/publish-build-inputs.tsv", "build input manifest");
            List<String> lines = decodeLines(path, "build input manifest");
            if (lines.size() < 2 || !HEADER.equals(lines.getFirst())) {
                throw new AssemblerFailure("build input manifest has an invalid header or no data rows");
            }
            Map<String, ManifestRow> required = new LinkedHashMap<>();
            Set<String> binaryCoordinates = new LinkedHashSet<>();
            String parentCoordinate = null;
            for (ManifestRow row : manifest.rows()) {
                if (isPrimaryRole(row.role())) {
                    required.put(primaryKey(row.coordinate(), row.role()), row);
                }
                if ("BINARY".equals(row.role())) {
                    binaryCoordinates.add(row.coordinate());
                }
                if ("POM".equals(row.role())
                        && "taskflowinsight-parent".equals(Coordinate.parse(row.coordinate()).artifact())) {
                    parentCoordinate = row.coordinate();
                }
            }
            Map<String, BuildInput> primaries = new LinkedHashMap<>();
            Map<String, List<BuildInput>> sources = new LinkedHashMap<>();
            Set<String> sourceRevisionPaths = new HashSet<>();
            Set<String> retainedPaths = new HashSet<>();
            BuildInput license = null;
            for (int index = 1; index < lines.size(); index++) {
                String[] columns = lines.get(index).split("\t", -1);
                if (columns.length != 7) {
                    throw new AssemblerFailure("build input row must have exactly seven columns");
                }
                String retainedPath = columns[5];
                if (!retainedPaths.add(retainedPath)) {
                    throw new AssemblerFailure("build input manifest contains a duplicate retainedPath");
                }
                Path retained = resolveSealedFile(evidence, retainedPath, "retained build input");
                byte[] bytes = readBytes(retained, "retained build input");
                if (!sha256(bytes).equals(columns[6])) {
                    throw new AssemblerFailure("build input sha256 does not match retained bytes");
                }
                BuildInput input = new BuildInput(
                        columns[0], columns[1], columns[2], columns[3], columns[4], retainedPath, retained, columns[6]);
                if ("PRIMARY".equals(input.kind())) {
                    if (!"-".equals(input.sourceRevisionPath())) {
                        throw new AssemblerFailure("primary build input sourceRevisionPath must be dash");
                    }
                    String key = primaryKey(input.coordinate(), input.role());
                    ManifestRow expected = required.remove(key);
                    if (expected == null || !modulePath(input.coordinate()).equals(input.modulePath())) {
                        throw new AssemblerFailure("build input primary closure contains unexpected entry");
                    }
                    if (primaries.putIfAbsent(key, input) != null) {
                        throw new AssemblerFailure("build input primary closure contains a duplicate entry");
                    }
                } else if ("SOURCE".equals(input.kind())) {
                    validateSourceInput(input, binaryCoordinates);
                    if (!sourceRevisionPaths.add(input.sourceRevisionPath())) {
                        throw new AssemblerFailure("build input sourceRevisionPath must be unique");
                    }
                    sources.computeIfAbsent(input.coordinate(), ignored -> new ArrayList<>()).add(input);
                } else if ("LICENSE".equals(input.kind())) {
                    if (license != null
                            || !parentCoordinate.equals(input.coordinate())
                            || !"LICENSE".equals(input.role())
                            || !".".equals(input.modulePath())
                            || !"LICENSE".equals(input.sourceRevisionPath())
                            || bytes.length == 0) {
                        throw new AssemblerFailure("build input must contain one canonical root LICENSE row");
                    }
                    license = input;
                } else {
                    throw new AssemblerFailure("unknown build input kind");
                }
            }
            if (!required.isEmpty()) {
                throw new AssemblerFailure("build input primary closure is missing required entry");
            }
            if (license == null) {
                throw new AssemblerFailure("build input root LICENSE row is missing");
            }
            for (String coordinate : binaryCoordinates) {
                if (!sources.containsKey(coordinate) || sources.get(coordinate).isEmpty()) {
                    throw new AssemblerFailure("build input source closure is missing a published module");
                }
            }
            Map<String, List<BuildInput>> immutableSources = new LinkedHashMap<>();
            sources.forEach((coordinate, rows) -> immutableSources.put(coordinate, List.copyOf(rows)));
            return new BuildInputs(Map.copyOf(primaries), Map.copyOf(immutableSources), license);
        }

        private static void validateSourceInput(BuildInput input, Set<String> binaryCoordinates) {
            if (!"SOURCE".equals(input.role()) || !binaryCoordinates.contains(input.coordinate())) {
                throw new AssemblerFailure("source build input must bind a published binary coordinate");
            }
            if (!modulePath(input.coordinate()).equals(input.modulePath())) {
                throw new AssemblerFailure("source build input modulePath does not match its coordinate");
            }
            String prefix = input.modulePath() + "/src/main/java/";
            if (!input.sourceRevisionPath().startsWith(prefix)
                    || !input.sourceRevisionPath().endsWith(".java")) {
                throw new AssemblerFailure("sourceRevisionPath must identify module production Java source");
            }
            requireRelativeSourcePath(input.sourceRevisionPath());
        }

        private BuildInput primary(ManifestRow row) {
            return primary(row.coordinate(), row.role());
        }

        private BuildInput primary(String coordinate, String role) {
            BuildInput input = primaries.get(primaryKey(coordinate, role));
            if (input == null) {
                throw new AssemblerFailure("build input primary is missing during assembly");
            }
            return input;
        }

        private List<BuildInput> sources(String binaryCoordinate) {
            List<BuildInput> result = sourcesByBinaryCoordinate.get(binaryCoordinate);
            if (result == null) {
                throw new AssemblerFailure("build input source closure is missing during assembly");
            }
            return result;
        }

        private static String modulePath(String coordinate) {
            String artifact = Coordinate.parse(coordinate).artifact();
            return switch (artifact) {
                case "taskflowinsight-parent" -> ".";
                case "TaskFlowInsight" -> "tfi-all";
                default -> artifact;
            };
        }
    }

    private record BuildInput(
            String kind,
            String coordinate,
            String role,
            String modulePath,
            String sourceRevisionPath,
            String retainedPath,
            Path retainedFile,
            String sha256) {

        private String sourcesEntryPath() {
            String prefix = modulePath + "/src/main/java/";
            return sourceRevisionPath.substring(prefix.length());
        }
    }

    private static void requireRelativeSourcePath(String value) {
        if (value.isEmpty() || value.startsWith("/") || value.contains("\\")) {
            throw new AssemblerFailure("sourceRevisionPath must be a relative POSIX path");
        }
        for (String part : value.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new AssemblerFailure("sourceRevisionPath must be a relative POSIX path");
            }
        }
    }

    private record Archive(Map<String, byte[]> entries) {
        private static Archive load(Path path, String description) {
            Map<String, byte[]> entries = new LinkedHashMap<>();
            Set<String> names = new HashSet<>();
            try (ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
                Enumeration<? extends ZipEntry> enumeration = zip.entries();
                while (enumeration.hasMoreElements()) {
                    ZipEntry entry = enumeration.nextElement();
                    String name = entry.getName();
                    requireSafeArchiveEntry(name, entry.isDirectory(), description);
                    if (!names.add(name)) {
                        throw new AssemblerFailure(description + " contains a duplicate entry");
                    }
                    if (!entry.isDirectory()) {
                        try (var input = zip.getInputStream(entry)) {
                            entries.put(name, input.readAllBytes());
                        }
                    }
                }
            } catch (AssemblerFailure failure) {
                throw failure;
            } catch (IOException | IllegalArgumentException failure) {
                throw new AssemblerFailure(description + " is not a readable ZIP archive");
            }
            if (entries.isEmpty()) {
                throw new AssemblerFailure(description + " must contain files");
            }
            return new Archive(Map.copyOf(entries));
        }

        private byte[] require(String entry, String description) {
            byte[] bytes = entries.get(entry);
            if (bytes == null) {
                throw new AssemblerFailure(description + " is missing entry " + entry);
            }
            return bytes;
        }

        private Map<String, byte[]> endingWith(String suffix) {
            Map<String, byte[]> matches = new LinkedHashMap<>();
            entries.forEach((name, bytes) -> {
                if (name.endsWith(suffix)) {
                    matches.put(name, bytes);
                }
            });
            return Map.copyOf(matches);
        }
    }

    private static void requireSafeArchiveEntry(String name, boolean directory, String description) {
        String candidate = directory && name.endsWith("/")
                ? name.substring(0, name.length() - 1)
                : name;
        if (candidate.isEmpty() || candidate.startsWith("/")
                || candidate.contains("\\") || candidate.indexOf('\0') >= 0) {
            throw new AssemblerFailure(description + " contains an unsafe entry name");
        }
        for (String part : candidate.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new AssemblerFailure(description + " contains an unsafe entry name");
            }
        }
    }

    private record ClassInfo(String entryName, String internalName, int accessFlags) {
        private static final int CLASS_MAGIC = 0xCAFEBABE;
        private static final int ACC_PUBLIC = 0x0001;
        private static final int ACC_PROTECTED = 0x0004;

        private static ClassInfo parse(String entryName, byte[] bytes) {
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (input.readInt() != CLASS_MAGIC) {
                    throw new AssemblerFailure("binary archive contains an invalid class file");
                }
                input.readUnsignedShort();
                input.readUnsignedShort();
                int constantPoolCount = input.readUnsignedShort();
                if (constantPoolCount <= 1) {
                    throw new AssemblerFailure("binary archive contains an invalid constant pool");
                }
                Object[] constants = new Object[constantPoolCount];
                for (int index = 1; index < constantPoolCount; index++) {
                    int tag = input.readUnsignedByte();
                    switch (tag) {
                        case 1 -> constants[index] = input.readUTF();
                        case 3, 4 -> skipExact(input, 4);
                        case 5, 6 -> {
                            skipExact(input, 8);
                            index++;
                        }
                        case 7 -> constants[index] = input.readUnsignedShort();
                        case 8, 16, 19, 20 -> skipExact(input, 2);
                        case 9, 10, 11, 12, 17, 18 -> skipExact(input, 4);
                        case 15 -> skipExact(input, 3);
                        default -> throw new AssemblerFailure(
                                "binary archive contains an unknown class constant tag");
                    }
                }
                int access = input.readUnsignedShort();
                int classIndex = input.readUnsignedShort();
                if (!(constant(constants, classIndex) instanceof Integer nameIndex)
                        || !(constant(constants, nameIndex) instanceof String internalName)
                        || !entryName.equals(internalName + ".class")) {
                    throw new AssemblerFailure("binary class entry does not match its declared name");
                }
                return new ClassInfo(entryName, internalName, access);
            } catch (AssemblerFailure failure) {
                throw failure;
            } catch (EOFException failure) {
                throw new AssemblerFailure("binary archive contains a truncated class file");
            } catch (IOException failure) {
                throw new AssemblerFailure("binary archive class file cannot be read");
            }
        }

        private boolean publicApi() {
            return (accessFlags & (ACC_PUBLIC | ACC_PROTECTED)) != 0;
        }

        private static Object constant(Object[] constants, int index) {
            if (index <= 0 || index >= constants.length) {
                throw new AssemblerFailure("binary archive contains an invalid constant reference");
            }
            return constants[index];
        }

        private static void skipExact(DataInputStream input, int length) throws IOException {
            input.skipNBytes(length);
        }
    }

    private record SourceUnit(
            BuildInput input,
            String entryPath,
            Map<String, String> logicalToInternalName) {

        private static SourceUnit parse(BuildInput input, byte[] bytes) {
            String source = decodeUtf8(bytes, "retained production source");
            var compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new AssemblerFailure("JDK compiler is required to verify production sources");
            }
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            JavaFileObject sourceFile = new StringSource(source);
            JavacTask task = (JavacTask) compiler.getTask(
                    null, null, diagnostics, List.of("-proc:none"), null, List.of(sourceFile));
            List<CompilationUnitTree> units = new ArrayList<>();
            try {
                task.parse().forEach(units::add);
            } catch (IOException | RuntimeException failure) {
                throw new AssemblerFailure("retained production source cannot be parsed");
            }
            if (units.size() != 1 || diagnostics.getDiagnostics().stream()
                    .anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)) {
                throw new AssemblerFailure("retained production source contains a parse error");
            }

            CompilationUnitTree unit = units.getFirst();
            String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            if (packageName.isBlank()) {
                throw new AssemblerFailure("published production source must declare a package");
            }
            String entryPath = input.sourcesEntryPath();
            String packagePath = packageName.replace('.', '/');
            if (!entryPath.startsWith(packagePath + "/")) {
                throw new AssemblerFailure("source entry path does not match its declared package");
            }

            Map<String, String> types = new LinkedHashMap<>();
            for (Tree declaration : unit.getTypeDecls()) {
                if (declaration instanceof ClassTree type && isTypeDeclaration(type.getKind())) {
                    String simpleName = type.getSimpleName().toString();
                    if (simpleName.isEmpty()) {
                        throw new AssemblerFailure("production source contains an unnamed top-level type");
                    }
                    String logicalName = packageName + "." + simpleName;
                    if (types.putIfAbsent(logicalName, logicalName.replace('.', '/')) != null) {
                        throw new AssemblerFailure("production source contains a duplicate top-level type");
                    }
                }
            }
            boolean packageInfo = entryPath.equals(packagePath + "/package-info.java");
            if (types.isEmpty() && !packageInfo) {
                throw new AssemblerFailure("production source does not declare a top-level type");
            }
            return new SourceUnit(input, entryPath, Map.copyOf(types));
        }

        private static boolean isTypeDeclaration(Tree.Kind kind) {
            return kind == Tree.Kind.CLASS
                    || kind == Tree.Kind.INTERFACE
                    || kind == Tree.Kind.ENUM
                    || kind == Tree.Kind.RECORD
                    || kind == Tree.Kind.ANNOTATION_TYPE;
        }
    }

    private static final class StringSource extends SimpleJavaFileObject {
        private final String content;

        private StringSource(String content) {
            super(URI.create("memo:///RetainedSource.java"), JavaFileObject.Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }

    private static String decodeUtf8(byte[] bytes, String description) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new AssemblerFailure(description + " must be valid UTF-8");
        }
    }

    private static void verifyPom(BuildInput input, String finalVersion) {
        Coordinate expected = Coordinate.parse(input.coordinate());
        byte[] bytes = readBytes(input.retainedFile(), "retained POM");
        Element project = parseXml(bytes, "retained POM");
        boolean parentPom = "taskflowinsight-parent".equals(expected.artifact());

        if (!"project".equals(project.getLocalName())
                || !"com.syy".equals(requiredText(project, "groupId"))
                || !expected.artifact().equals(requiredText(project, "artifactId"))
                || !finalVersion.equals(requiredText(project, "version"))) {
            throw new AssemblerFailure("retained POM coordinate does not match its manifest coordinate");
        }
        String packaging = optionalText(project, "packaging");
        if (parentPom ? !"pom".equals(packaging) : !(packaging.isEmpty() || "jar".equals(packaging))) {
            throw new AssemblerFailure("retained POM packaging does not match its published role");
        }
        if (!directChildren(project, "parent").isEmpty()
                || !directChildren(project, "profiles").isEmpty()
                || project.getElementsByTagNameNS("*", "repositories").getLength() != 0
                || project.getElementsByTagNameNS("*", "pluginRepositories").getLength() != 0
                || project.getElementsByTagNameNS("*", "systemPath").getLength() != 0) {
            throw new AssemblerFailure("retained POM leaks build-only parent, profile, or repository state");
        }
        requireMetadata(project);
        verifyPomDependencies(project, finalVersion, parentPom);
        String xml = decodeUtf8(bytes, "retained POM");
        if (xml.contains("${") || Pattern.compile("(?i)SNAPSHOT|>LATEST<|>RELEASE<").matcher(xml).find()) {
            throw new AssemblerFailure("retained POM contains a mutable or unresolved value");
        }
    }

    private static void requireMetadata(Element project) {
        if (requiredText(project, "name").isBlank()
                || requiredText(project, "description").isBlank()
                || !"https://github.com/shiyongyin/TaskFlowInsight".equals(requiredText(project, "url"))) {
            throw new AssemblerFailure("retained POM project metadata is incomplete");
        }
        Element license = onlyChild(onlyChild(project, "licenses"), "license");
        if (!"Apache License, Version 2.0".equals(requiredText(license, "name"))
                || !"https://www.apache.org/licenses/LICENSE-2.0.txt".equals(requiredText(license, "url"))
                || !"repo".equals(requiredText(license, "distribution"))) {
            throw new AssemblerFailure("retained POM license metadata is invalid");
        }
        Element developer = onlyChild(onlyChild(project, "developers"), "developer");
        if (!"shiyongyin".equals(requiredText(developer, "id"))
                || !"shiyongyin".equals(requiredText(developer, "name"))
                || !"https://github.com/shiyongyin".equals(requiredText(developer, "url"))) {
            throw new AssemblerFailure("retained POM developer metadata is invalid");
        }
        Element scm = onlyChild(project, "scm");
        if (!"scm:git:https://github.com/shiyongyin/TaskFlowInsight.git"
                .equals(requiredText(scm, "connection"))
                || !"scm:git:ssh://git@github.com/shiyongyin/TaskFlowInsight.git"
                        .equals(requiredText(scm, "developerConnection"))
                || !"https://github.com/shiyongyin/TaskFlowInsight".equals(requiredText(scm, "url"))
                || !(optionalText(scm, "tag").isEmpty() || "HEAD".equals(optionalText(scm, "tag")))) {
            throw new AssemblerFailure("retained POM SCM metadata is invalid");
        }
    }

    private static void verifyPomDependencies(Element project, String finalVersion, boolean parentPom) {
        Set<String> published = Set.of(
                "tfi-flow-core", "tfi-flow-spring-starter", "tfi-compare",
                "tfi-compare-spring-starter", "tfi-ops-spring", "TaskFlowInsight");
        List<Element> containers = directChildren(project, "dependencies");
        if (parentPom && !containers.isEmpty()) {
            throw new AssemblerFailure("published parent POM must not expose runtime dependencies");
        }
        if (containers.size() > 1) {
            throw new AssemblerFailure("retained POM contains duplicate dependency containers");
        }
        if (containers.isEmpty()) {
            return;
        }
        Set<String> keys = new HashSet<>();
        for (Element dependency : directChildren(containers.getFirst(), "dependency")) {
            String group = requiredText(dependency, "groupId");
            String artifact = requiredText(dependency, "artifactId");
            String version = requiredText(dependency, "version");
            String scope = optionalText(dependency, "scope");
            String key = group + ":" + artifact;
            if (!keys.add(key) || !FIXED_VERSION.matcher(version).matches()
                    || !(scope.isEmpty() || Set.of("compile", "runtime", "provided").contains(scope))) {
                throw new AssemblerFailure("retained POM dependency is duplicate, mutable, or build-only");
            }
            if ("com.syy".equals(group)
                    && (!published.contains(artifact) || !finalVersion.equals(version))) {
                throw new AssemblerFailure("retained POM contains an invalid internal dependency edge");
            }
        }
    }

    private static Element parseXml(byte[] bytes, String description) {
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
            return builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
        } catch (Exception failure) {
            throw new AssemblerFailure(description + " is not secure well-formed XML");
        }
    }

    private static Element onlyChild(Element parent, String name) {
        List<Element> matches = directChildren(parent, name);
        if (matches.size() != 1) {
            throw new AssemblerFailure("retained POM must contain exactly one " + name + " element");
        }
        return matches.getFirst();
    }

    private static String requiredText(Element parent, String name) {
        String value = onlyChild(parent, name).getTextContent().strip();
        if (value.isEmpty()) {
            throw new AssemblerFailure("retained POM " + name + " must not be empty");
        }
        return value;
    }

    private static String optionalText(Element parent, String name) {
        List<Element> matches = directChildren(parent, name);
        if (matches.size() > 1) {
            throw new AssemblerFailure("retained POM contains duplicate " + name + " elements");
        }
        return matches.isEmpty() ? "" : matches.getFirst().getTextContent().strip();
    }

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> matches = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element && name.equals(element.getLocalName())) {
                matches.add(element);
            }
        }
        return List.copyOf(matches);
    }

    private static List<String> verifyContent(PublishManifest manifest, BuildInputs inputs) {
        String finalVersion = manifest.rows().stream()
                .filter(row -> isPrimaryRole(row.role()))
                .map(row -> Coordinate.parse(row.coordinate()).version())
                .findFirst()
                .orElseThrow(() -> new AssemblerFailure("publish manifest has no primary version"));
        for (ManifestRow row : manifest.rows()) {
            if ("POM".equals(row.role())) {
                verifyPom(inputs.primary(row), finalVersion);
            }
        }

        List<ContentRow> rows = new ArrayList<>();
        for (ManifestRow row : manifest.rows()) {
            if ("BINARY".equals(row.role())) {
                rows.addAll(verifyModuleContent(Coordinate.parse(row.coordinate()), inputs));
            }
        }
        rows.sort(Comparator.comparing(ContentRow::binaryCoordinate, PublishArtifactAssembler::compareUtf8)
                .thenComparing(ContentRow::logicalTypeName, PublishArtifactAssembler::compareUtf8));
        List<String> lines = new ArrayList<>();
        lines.add(ContentManifest.HEADER);
        rows.forEach(row -> lines.add(row.tsv()));
        return List.copyOf(lines);
    }

    private static List<ContentRow> verifyModuleContent(Coordinate binary, BuildInputs inputs) {
        BuildInput binaryInput = inputs.primary(binary.canonical(), "BINARY");
        BuildInput pomInput = inputs.primary(binary.withExtension("pom").canonical(), "POM");
        BuildInput sourcesInput = inputs.primary(binary.withClassifier("sources").canonical(), "SOURCES");
        BuildInput javadocInput = inputs.primary(binary.withClassifier("javadoc").canonical(), "JAVADOC");
        Archive binaryArchive = Archive.load(binaryInput.retainedFile(), "binary archive");
        Archive sourcesArchive = Archive.load(sourcesInput.retainedFile(), "sources archive");
        Archive javadocArchive = Archive.load(javadocInput.retainedFile(), "Javadoc archive");

        byte[] license = readBytes(inputs.license().retainedFile(), "retained root LICENSE");
        if (!Arrays.equals(license, binaryArchive.require("META-INF/LICENSE", "binary archive"))) {
            throw new AssemblerFailure("binary archive LICENSE does not match retained root LICENSE");
        }
        String embeddedPom = "META-INF/maven/" + binary.group() + "/" + binary.artifact() + "/pom.xml";
        if (!Arrays.equals(readBytes(pomInput.retainedFile(), "retained POM"),
                binaryArchive.require(embeddedPom, "binary archive"))) {
            throw new AssemblerFailure("binary archive embedded POM does not match published POM bytes");
        }

        Map<String, byte[]> classEntries = binaryArchive.endingWith(".class");
        Map<String, byte[]> sourceEntries = sourcesArchive.endingWith(".java");
        if (classEntries.isEmpty() || !sourcesArchive.endingWith(".class").isEmpty()
                || !javadocArchive.endingWith(".class").isEmpty()) {
            throw new AssemblerFailure("published archives do not have the required binary/source/Javadoc shape");
        }
        javadocArchive.require("index.html", "Javadoc archive");

        Map<String, SourceUnit> sourcesByTopLevel = verifySources(binary, inputs, sourceEntries);
        Map<String, ClassInfo> classes = new LinkedHashMap<>();
        classEntries.forEach((name, bytes) -> classes.put(name, ClassInfo.parse(name, bytes)));
        Map<String, List<ClassInfo>> classesByTopLevel = groupClasses(sourcesByTopLevel.keySet(), classes);

        Set<String> expectedJavadocs = new LinkedHashSet<>();
        List<ContentRow> rows = new ArrayList<>();
        for (Map.Entry<String, SourceUnit> sourceType : sourcesByTopLevel.entrySet()) {
            String logicalName = sourceType.getKey();
            SourceUnit source = sourceType.getValue();
            String internalName = source.logicalToInternalName().get(logicalName);
            String topEntry = internalName + ".class";
            ClassInfo topLevel = classes.get(topEntry);
            if (topLevel == null) {
                throw new AssemblerFailure("production source top-level type is missing from binary archive");
            }
            List<ClassInfo> group = classesByTopLevel.get(internalName);
            String javadocPath = "-";
            String javadocSha = "-";
            if (topLevel.publicApi()) {
                for (ClassInfo member : group) {
                    if (member.publicApi()) {
                        String memberDoc = member.internalName().replace('$', '.') + ".html";
                        expectedJavadocs.add(memberDoc);
                    }
                }
                javadocPath = internalName + ".html";
                byte[] page = javadocArchive.require(javadocPath, "Javadoc archive");
                verifyJavadocPage(page);
                javadocSha = sha256(page);
            }
            byte[] sourceBytes = sourceEntries.get(source.entryPath());
            rows.add(new ContentRow(
                    binary.canonical(), logicalName, topLevel.publicApi() ? "PUBLIC_API" : "NON_PUBLIC",
                    source.input().sourceRevisionPath(), source.input().sha256(), source.entryPath(),
                    sha256(sourceBytes), binaryEntriesSha(group, classEntries), javadocPath, javadocSha, "PASS"));
        }
        verifyJavadocClosure(javadocArchive, expectedJavadocs);
        return List.copyOf(rows);
    }

    private static Map<String, SourceUnit> verifySources(
            Coordinate binary, BuildInputs inputs, Map<String, byte[]> sourceEntries) {
        Map<String, BuildInput> expectedEntries = new LinkedHashMap<>();
        for (BuildInput source : inputs.sources(binary.canonical())) {
            if (expectedEntries.putIfAbsent(source.sourcesEntryPath(), source) != null) {
                throw new AssemblerFailure("build input source entry path must be unique per module");
            }
        }
        if (!expectedEntries.keySet().equals(sourceEntries.keySet())) {
            throw new AssemblerFailure("sources archive and retained production source closure differ");
        }
        Map<String, SourceUnit> topLevels = new LinkedHashMap<>();
        expectedEntries.forEach((entryPath, sourceInput) -> {
            byte[] archiveBytes = sourceEntries.get(entryPath);
            byte[] revisionBytes = readBytes(sourceInput.retainedFile(), "retained production source");
            if (!Arrays.equals(archiveBytes, revisionBytes)) {
                throw new AssemblerFailure("sources archive entry differs from retained production source");
            }
            SourceUnit unit = SourceUnit.parse(sourceInput, revisionBytes);
            unit.logicalToInternalName().keySet().forEach(type -> {
                if (topLevels.putIfAbsent(type, unit) != null) {
                    throw new AssemblerFailure("production source closure contains a duplicate top-level type");
                }
            });
        });
        if (topLevels.isEmpty()) {
            throw new AssemblerFailure("published module source closure contains no top-level type");
        }
        return Map.copyOf(topLevels);
    }

    private static Map<String, List<ClassInfo>> groupClasses(
            Set<String> logicalNames, Map<String, ClassInfo> classes) {
        Set<String> topLevelInternalNames = new LinkedHashSet<>();
        logicalNames.forEach(name -> topLevelInternalNames.add(name.replace('.', '/')));
        Map<String, List<ClassInfo>> grouped = new LinkedHashMap<>();
        topLevelInternalNames.forEach(name -> grouped.put(name, new ArrayList<>()));
        for (ClassInfo classInfo : classes.values()) {
            if (classInfo.entryName().endsWith("/package-info.class")
                    || "module-info.class".equals(classInfo.entryName())) {
                continue;
            }
            String owner = null;
            if (topLevelInternalNames.contains(classInfo.internalName())) {
                owner = classInfo.internalName();
            } else {
                for (String candidate : topLevelInternalNames) {
                    if (classInfo.internalName().startsWith(candidate + "$")) {
                        if (owner == null || candidate.length() > owner.length()) {
                            owner = candidate;
                        }
                    }
                }
            }
            if (owner == null) {
                throw new AssemblerFailure("binary archive contains a top-level type absent from retained sources");
            }
            grouped.get(owner).add(classInfo);
        }
        for (Map.Entry<String, List<ClassInfo>> group : grouped.entrySet()) {
            if (group.getValue().stream().noneMatch(type -> type.internalName().equals(group.getKey()))) {
                throw new AssemblerFailure("binary archive is missing a retained source top-level type");
            }
            group.getValue().sort(Comparator.comparing(ClassInfo::entryName, PublishArtifactAssembler::compareUtf8));
        }
        Map<String, List<ClassInfo>> immutable = new LinkedHashMap<>();
        grouped.forEach((name, group) -> immutable.put(name, List.copyOf(group)));
        return Map.copyOf(immutable);
    }

    private static String binaryEntriesSha(List<ClassInfo> group, Map<String, byte[]> entries) {
        List<String> rows = new ArrayList<>();
        for (ClassInfo classInfo : group) {
            rows.add(classInfo.entryName() + "\t" + sha256(entries.get(classInfo.entryName())));
        }
        return sha256(linesBytes(rows));
    }

    private static void verifyJavadocClosure(Archive javadoc, Set<String> expected) {
        if (expected.isEmpty()) {
            throw new AssemblerFailure("Javadoc archive contains no public API type pages");
        }
        Set<String> actual = new LinkedHashSet<>();
        javadoc.entries().forEach((name, bytes) -> {
            if (isTypeJavadocEntry(name)) {
                verifyJavadocPage(bytes);
                actual.add(name);
            }
        });
        if (!actual.equals(expected)) {
            Set<String> missing = new LinkedHashSet<>(expected);
            missing.removeAll(actual);
            Set<String> extra = new LinkedHashSet<>(actual);
            extra.removeAll(expected);
            throw new AssemblerFailure("Javadoc type page closure differs from public/protected binary types: "
                    + "missing=" + firstUtf8(missing) + ", extra=" + firstUtf8(extra));
        }
    }

    private static String firstUtf8(Set<String> values) {
        return values.stream().min(PublishArtifactAssembler::compareUtf8).orElse("-");
    }

    private static boolean isTypeJavadocEntry(String name) {
        if (!name.endsWith(".html") || !name.contains("/")
                || name.contains("/class-use/") || name.contains("/doc-files/")) {
            return false;
        }
        String fileName = name.substring(name.lastIndexOf('/') + 1);
        return !Set.of(
                "package-summary.html", "package-tree.html", "package-use.html",
                "module-summary.html", "module-tree.html").contains(fileName);
    }

    private static void verifyJavadocPage(byte[] bytes) {
        String html = decodeUtf8(bytes, "Javadoc type page");
        if (!html.contains("<html") || !html.contains("</html>")
                || !html.contains("name=\"generator\" content=\"javadoc/")
                || !html.contains("class=\"type-signature\"")) {
            throw new AssemblerFailure("Javadoc type page is not generated type documentation");
        }
    }

    private static int compareUtf8(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        int limit = Math.min(leftBytes.length, rightBytes.length);
        for (int index = 0; index < limit; index++) {
            int compared = Integer.compare(Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
    }

    private record ContentRow(
            String binaryCoordinate,
            String logicalTypeName,
            String visibility,
            String sourceRevisionPath,
            String sourceRevisionSha256,
            String sourcesEntryPath,
            String sourcesEntrySha256,
            String binaryEntriesSha256,
            String javadocEntryPath,
            String javadocEntrySha256,
            String status) {

        private String tsv() {
            return String.join("\t", binaryCoordinate, logicalTypeName, visibility,
                    sourceRevisionPath, sourceRevisionSha256, sourcesEntryPath, sourcesEntrySha256,
                    binaryEntriesSha256, javadocEntryPath, javadocEntrySha256, status);
        }
    }

    private static String primaryKey(String coordinate, String role) {
        return coordinate + '\t' + role;
    }

    private static void assemble(Path evidence, PublishManifest manifest, BuildInputs inputs) {
        Path artifactsDirectory = requirePublishOutputParent(evidence, "artifacts", false);
        Path metadataDirectory = requirePublishOutputParent(evidence, "metadata", true);
        Path finalRepository = artifactsDirectory.resolve("publishable-repository");
        Path finalArtifacts = metadataDirectory.resolve("publishable-artifacts.tsv");
        Path finalContent = metadataDirectory.resolve("publishable-content.tsv");
        for (Path output : List.of(finalRepository, finalArtifacts, finalContent)) {
            if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
                throw new AssemblerFailure("publish assembly output already exists");
            }
        }
        List<String> contentRows = verifyContent(manifest, inputs);

        Path staging;
        try {
            staging = Files.createTempDirectory(evidence, ".publish-assembly-");
        } catch (IOException failure) {
            throw new AssemblerFailure("publish assembly staging cannot be created");
        }
        try {
            Path stagedRepository = Files.createDirectories(staging.resolve("repository"));
            Map<Integer, byte[]> primaryBytes = new LinkedHashMap<>();
            List<String> artifactRows = new ArrayList<>();
            artifactRows.add(PublishManifest.HEADER + "\tsha256");

            for (ManifestRow row : manifest.rows()) {
                byte[] outputBytes;
                if (isPrimaryRole(row.role())) {
                    outputBytes = readBytes(inputs.primary(row).retainedFile(), "retained build input");
                    primaryBytes.put(row.ordinal(), outputBytes);
                } else if ("CHECKSUM".equals(row.role())) {
                    int subject = positiveInteger(row.subjectOrdinal(), "subjectOrdinal");
                    byte[] subjectBytes = primaryBytes.get(subject);
                    if (subjectBytes == null) {
                        throw new AssemblerFailure("checksum subject bytes are unavailable");
                    }
                    String digest = switch (row.sidecarKind()) {
                        case "SHA256" -> sha256(subjectBytes);
                        case "SHA512" -> sha512(subjectBytes);
                        default -> throw new AssemblerFailure("unknown checksum sidecar kind");
                    };
                    outputBytes = (digest + "\n").getBytes(StandardCharsets.UTF_8);
                } else {
                    throw new AssemblerFailure("signature sidecars must be supplied by HRD-08");
                }
                Path target = stagedRepository.resolve(row.repositoryPath()).normalize();
                if (!target.startsWith(stagedRepository)) {
                    throw new AssemblerFailure("repositoryPath escapes publish staging");
                }
                writeNew(target, outputBytes);
                artifactRows.add(row.tsv() + '\t' + sha256(outputBytes));
            }

            Path stagedArtifacts = staging.resolve("publishable-artifacts.tsv");
            writeNew(stagedArtifacts, linesBytes(artifactRows));
            Path stagedContent = staging.resolve("publishable-content.tsv");
            writeNew(stagedContent, linesBytes(contentRows));

            requirePublishOutputParent(evidence, "artifacts", true);
            requirePublishOutputParent(evidence, "metadata", true);
            Files.move(stagedArtifacts, finalArtifacts, StandardCopyOption.ATOMIC_MOVE);
            Files.move(stagedContent, finalContent, StandardCopyOption.ATOMIC_MOVE);
            // Repository 最后出现；调用方不能把只有部分 metadata 的目录误认成完整发布闭集。
            Files.move(stagedRepository, finalRepository, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            throw new AssemblerFailure("publish assembly output cannot be written");
        } finally {
            deleteOwnedStaging(staging);
        }
    }

    private static Path requirePublishOutputParent(
            Path evidence, String directoryName, boolean createIfMissing) {
        Path directory = evidence.resolve(directoryName);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS) && createIfMissing) {
            try {
                Files.createDirectory(directory);
            } catch (IOException failure) {
                throw new AssemblerFailure("publish output parent cannot be created: " + directoryName);
            }
        }
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))) {
            throw new AssemblerFailure(
                    "publish output parent must be an owned directory: " + directoryName);
        }
        if (createIfMissing && !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new AssemblerFailure(
                    "publish output parent must be an owned directory: " + directoryName);
        }
        return directory;
    }

    private static byte[] readBytes(Path path, String description) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException failure) {
            throw new AssemblerFailure(description + " cannot be read");
        }
    }

    private static void writeNew(Path path, byte[] bytes) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static byte[] linesBytes(List<String> lines) {
        return (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String sha512(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-512 is unavailable", impossible);
        }
    }

    private static void deleteOwnedStaging(Path staging) {
        try (Stream<Path> paths = Files.walk(staging)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Fresh evidence remains fail-closed if cleanup itself is interrupted.
        }
    }

    private static final class ContentManifest {
        private static final String HEADER = "binaryCoordinate\tlogicalTypeName\tvisibility\t"
                + "sourceRevisionPath\tsourceRevisionSha256\tsourcesEntryPath\tsourcesEntrySha256\t"
                + "binaryEntriesSha256\tjavadocEntryPath\tjavadocEntrySha256\tstatus";

        private ContentManifest() {
        }
    }

    /** 预期输入拒绝不采集堆栈，避免把宿主路径或数据带入证据日志。 */
    private static final class AssemblerFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private AssemblerFailure(String message) {
            super(message, null, false, false);
        }
    }
}
