import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * 生产发布证据的离线、只读验证入口。
 *
 * <p>该源文件保持 JDK-only，可由 {@code java ReleaseEvidenceVerifier.java} 在隔离 runner 中直接执行。</p>
 */
public final class ReleaseEvidenceVerifier {

    /** Policy 行数和顺序本身属于 release-owner authority，不能按 Map 宽松解析。 */
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

    /** 所有必需引用均成对绑定 relative path 与 release-owner SHA。 */
    private static final List<String> AUTHORITY_PATH_KEYS = List.of(
            "publishArtifactManifest", "runtimePerformancePolicy", "compatibilityMatrix",
            "releaseExecutionPolicy", "buildToolchainManifest",
            "productionAuthoritiesManifest", "licensePolicy");

    /** 32 行 policy 的硬上界；正常 authority 远小于该值。 */
    private static final int MAX_POLICY_BYTES = 64 * 1024;

    /** 单个 policy 引用文件的硬上界，防止 parser 在信任前无界分配。 */
    private static final int MAX_AUTHORITY_BYTES = 8 * 1024 * 1024;

    /** 单次 CLI 调用的首次读取摘要；阻断不同验证阶段对同一路径观察不同 preimage。 */
    private static final ThreadLocal<Map<Path, String>> SEALED_READ_HASHES =
            ThreadLocal.withInitial(java.util.HashMap::new);

    /** Maven fixed release version，不接受 baseline、范围、占位符或 mutable token。 */
    private static final Pattern FIXED_VERSION = Pattern.compile(
            "(?i)^(?!.*SNAPSHOT)(?!LATEST$)(?!RELEASE$)(?!.*\\$\\{)"
                    + "(?!.*[\\[\\](),])[A-Z0-9][A-Z0-9._-]*$");

    /** 证据中所有摘要统一使用 lowercase SHA-256。 */
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    /** 外部工具 identity 同时固定名称、版本和完整 bundle closure。 */
    private static final Pattern TOOL_IDENTITY = Pattern.compile(
            "[^@#\\s]+@[^#\\s]+#bundle-sha256:[0-9a-f]{64}");

    /** 固定 CLI 语法，避免不同阶段自行解释参数。 */
    private static final String USAGE = "Usage: ReleaseEvidenceVerifier "
            + "verify-policy <production-policy.tsv> | "
            + "verify-supply-chain <evidence-dir> <production-policy.tsv> | "
            + "verify-integrity <evidence-dir> <expected-reports.tsv> | "
            + "verify-all <evidence-dir> <expected-reports.tsv>";

    /** 唯一允许的验证阶段；未知 mode 必须在读取任何证据前失败。 */
    private static final Set<String> MODES = Set.of(
            "verify-policy", "verify-supply-chain", "verify-integrity", "verify-all");

    private ReleaseEvidenceVerifier() {
    }

    /** 实际进程测量、loaded bytes 与 release-owner authority 的闭集验证。 */
    private static final class ExecutionEvidence {
        /** Actual build toolchain 使用与 authority 相同的列，路径相对 evidence root。 */
        private static final String BUILD_TOOLCHAIN_HEADER =
                "ordinal\trole\tcoordinate\tevidencePath\tsha256";
        /** Measurement index 只定位 retained raw machine output，不承载 loaded 事实。 */
        private static final String BUILD_MEASUREMENT_HEADER =
                "role\tcoordinate\tmeasurementKind\trawEvidencePath\trawEvidenceSha256";
        /** Raw machine output 中的 loaded fact schema。 */
        private static final String RAW_BUILD_FACT_HEADER =
                "observationId\tmeasurementKind\tsourceLocator\trole\tcoordinate\t"
                        + "loadedEvidencePath\tloadedSha256\trawSourcePath\trawSourceSha256";
        /** 允许的 raw measurement 来源必须能证明进程或 OCI 实际加载关系。 */
        private static final Set<String> BUILD_MEASUREMENT_KINDS = Set.of(
                "OCI_RUNTIME_INSPECTION", "PROCESS_EXECUTABLE_MAP", "JVM_CODESOURCE",
                "MAVEN_CLASSREALM");
        /** Scanner/generator bundle manifest 的唯一 schema。 */
        private static final String TOOL_CLOSURE_HEADER =
                "ordinal\tkind\tcoordinate\tevidencePath\tsha256";
        /** Policy identity 到 actual closure 的固定映射，禁止执行方改用等价工具。 */
        private static final Map<String, String[]> TOOL_CLOSURES = Map.of(
                "VULNERABILITY_SCANNER", new String[]{
                        "vulnerabilityScanner", "security/tool-closures/vulnerability-scanner.tsv",
                        "supply-chain/tool-bytes/vulnerability-scanner/"},
                "SECRET_SCANNER", new String[]{
                        "secretScanner", "security/tool-closures/secret-scanner.tsv",
                        "supply-chain/tool-bytes/secret-scanner/"},
                "SBOM_GENERATOR", new String[]{
                        "sbomGenerator", "supply-chain/tool-closures/sbom-generator.tsv",
                        "supply-chain/tool-bytes/sbom-generator/"});
        /** 每次 scanner/generator 执行的 loaded-byte ledger schema。 */
        private static final String TOOL_EXECUTION_HEADER =
                "executionId\ttoolRole\tcommandId\tbundleSha256\tloadedKind\tloadedCoordinate\t"
                        + "loadedEvidencePath\tloadedSha256\tmeasurementKind\tmeasurementPath\t"
                        + "measurementSha256";
        /** Per-execution raw process measurement 的固定事实 schema。 */
        private static final String RAW_TOOL_FACT_HEADER =
                "observationId\texecutionId\ttoolRole\tmeasurementKind\tsourceLocator\t"
                        + "loadedKind\tloadedCoordinate\tloadedEvidencePath\tloadedSha256\t"
                        + "rawSourcePath\trawSourceSha256";
        /** Scanner/generator 只接受可复算的三类加载来源。 */
        private static final Set<String> TOOL_MEASUREMENT_KINDS = Set.of(
                "JVM_CODESOURCE", "PROCESS_EXECUTABLE_MAP", "OCI_RUNTIME_INSPECTION");
        /** Release execution authority 的九列 schema。 */
        private static final String RELEASE_POLICY_HEADER =
                "executionId\trole\tcommandId\tcommandSpecSha256\tconfigPath\tconfigSha256\t"
                        + "rulesPath\trulesSha256\tscopeRule";
        /** Actual execution ledger 在 authority 后只追加可复算的运行事实。 */
        private static final String RELEASE_EXECUTION_HEADER = RELEASE_POLICY_HEADER
                + "\tscopePath\tscopeSha256\trawReportPath\trawReportSha256\tactualExit\t"
                + "startedAtUtc\tendedAtUtc\tstatus";

        private ExecutionEvidence() {
        }

        private static void verify(Path evidence, Policy policy) {
            validateBuildToolchain(evidence, policy);
            Map<String, ToolClosure> toolClosures = loadToolClosures(evidence, policy);
            validateToolExecutions(evidence, policy, toolClosures);
            validateReleaseExecutions(evidence, policy);
        }

        private static void validateBuildToolchain(Path evidence, Policy policy) {
            Path policyBase = policy.authorities().get("buildToolchainManifest").path().getParent();
            Map<String, String[]> expected = loadToolchainRows(
                    policyBase,
                    policy.authorities().get("buildToolchainManifest"),
                    "build toolchain authority",
                    null);
            SealedBytes actualFile = readRelative(
                    evidence,
                    "supply-chain/tool-closures/build-toolchain.tsv",
                    "actual build toolchain",
                    MAX_AUTHORITY_BYTES);
            Map<String, String[]> actual = loadToolchainRows(
                    evidence,
                    actualFile,
                    "actual build toolchain",
                    "supply-chain/tool-bytes/build-toolchain/");
            if (!expected.keySet().equals(actual.keySet())) {
                throw new VerificationFailure("expected and actual build toolchain closure differs");
            }
            for (String key : expected.keySet()) {
                if (!expected.get(key)[4].equals(actual.get(key)[4])) {
                    throw new VerificationFailure("actual build toolchain bytes differ from authority");
                }
            }
            validateBuildMeasurements(evidence, actual);
        }

        private static Map<String, String[]> loadToolchainRows(
                Path base,
                SealedBytes file,
                String description,
                String requiredPathPrefix) {
            List<String[]> rows = parseTable(
                    file, description, BUILD_TOOLCHAIN_HEADER, 5, 8192);
            Map<String, String[]> result = new LinkedHashMap<>();
            String previous = null;
            for (int index = 0; index < rows.size(); index++) {
                String[] row = rows.get(index);
                String key = row[1] + "\t" + row[2];
                if (!Integer.toString(index + 1).equals(row[0])
                        || !AuthoritySchemas.TOOLCHAIN_ROLES.contains(row[1])
                        || (previous != null && previous.compareTo(key) >= 0)
                        || result.putIfAbsent(key, row) != null) {
                    throw new VerificationFailure(description + " rows must be ordered and unique");
                }
                if (requiredPathPrefix != null && !row[3].startsWith(requiredPathPrefix)) {
                    throw new VerificationFailure(description + " bytes are outside the retained tool directory");
                }
                sealRelative(base, row[3], row[4], description + " component");
                previous = key;
            }
            return Map.copyOf(result);
        }

        private static void validateBuildMeasurements(
                Path evidence, Map<String, String[]> actual) {
            SealedBytes index = readRelative(
                    evidence,
                    "supply-chain/tool-closures/build-toolchain-measurements.tsv",
                    "build toolchain measurements",
                    MAX_AUTHORITY_BYTES);
            List<String[]> rows = parseTable(
                    index, "build toolchain measurements", BUILD_MEASUREMENT_HEADER, 5, 8192);
            Map<String, String[]> indexed = new LinkedHashMap<>();
            Map<String, String[]> derived = new LinkedHashMap<>();
            Map<String, String> rawFiles = new LinkedHashMap<>();
            for (String[] row : rows) {
                String key = row[0] + "\t" + row[1];
                if (!BUILD_MEASUREMENT_KINDS.contains(row[2])
                        || indexed.putIfAbsent(key, row) != null) {
                    throw new VerificationFailure("build toolchain measurement index is invalid or duplicate");
                }
                SealedBytes raw = sealRelative(
                        evidence, row[3], row[4], "build toolchain raw measurement");
                String previousSha = rawFiles.putIfAbsent(row[3], row[4]);
                if (previousSha == null) {
                    deriveBuildFacts(evidence, raw, derived);
                } else if (!previousSha.equals(row[4])) {
                    throw new VerificationFailure("build toolchain raw measurement has conflicting hashes");
                }
            }
            if (!actual.keySet().equals(indexed.keySet())
                    || !actual.keySet().equals(derived.keySet())) {
                throw new VerificationFailure("build toolchain measurements do not close actual loaded bytes");
            }
            for (String key : actual.keySet()) {
                String[] measurement = indexed.get(key);
                String[] fact = derived.get(key);
                String[] component = actual.get(key);
                if (!measurement[2].equals(fact[1])
                        || !measurement[3].equals(fact[9])
                        || !component[3].equals(fact[5])
                        || !component[4].equals(fact[6])) {
                    throw new VerificationFailure("build toolchain measurement differs from loaded bytes");
                }
            }
        }

        private static Map<String, ToolClosure> loadToolClosures(
                Path evidence, Policy policy) {
            Map<String, ToolClosure> result = new LinkedHashMap<>();
            for (Map.Entry<String, String[]> definition : TOOL_CLOSURES.entrySet()) {
                String toolRole = definition.getKey();
                String[] mapping = definition.getValue();
                String identity = policy.values().get(mapping[0]);
                String expectedBundleSha = identity.substring(
                        identity.indexOf("#bundle-sha256:") + "#bundle-sha256:".length());
                SealedBytes manifest = readRelative(
                        evidence, mapping[1], toolRoleDescription(toolRole) + " tool closure",
                        MAX_AUTHORITY_BYTES);
                if (!manifest.sha256().equals(expectedBundleSha)) {
                    throw new VerificationFailure(
                            toolRoleDescription(toolRole) + " bundle SHA differs from policy identity");
                }
                Map<String, String[]> components = parseToolClosure(
                        evidence, manifest, toolRoleDescription(toolRole), mapping[2]);
                result.put(toolRole, new ToolClosure(expectedBundleSha, components));
            }
            return Map.copyOf(result);
        }

        private static Map<String, String[]> parseToolClosure(
                Path evidence,
                SealedBytes manifest,
                String description,
                String requiredPathPrefix) {
            List<String[]> rows = parseTable(
                    manifest, description + " tool closure", TOOL_CLOSURE_HEADER, 5, 8192);
            Map<String, String[]> result = new LinkedHashMap<>();
            String previous = null;
            for (int index = 0; index < rows.size(); index++) {
                String[] row = rows.get(index);
                if (!Integer.toString(index + 1).equals(row[0])
                        || !Set.of("MAVEN", "BINARY", "OCI").contains(row[1])
                        || !toolCoordinateValid(row[1], row[2])
                        || (previous != null && previous.compareTo(row[2]) >= 0)
                        || result.putIfAbsent(row[2], row) != null) {
                    throw new VerificationFailure(description + " tool closure is unordered or invalid");
                }
                if (!row[3].startsWith(requiredPathPrefix)) {
                    throw new VerificationFailure(description + " bytes are outside the retained tool directory");
                }
                sealRelative(evidence, row[3], row[4], description + " retained tool bytes");
                previous = row[2];
            }
            return Map.copyOf(result);
        }

        private static boolean toolCoordinateValid(String kind, String coordinate) {
            return switch (kind) {
                case "MAVEN" -> mavenToolCoordinateValid(coordinate);
                case "BINARY" -> binaryToolCoordinateValid(coordinate);
                case "OCI" -> coordinate.matches("oci:[^\\s@]+@sha256:[0-9a-f]{64}");
                default -> false;
            };
        }

        private static boolean mavenToolCoordinateValid(String coordinate) {
            String[] parts = coordinate.split(":", -1);
            int versionIndex = parts.length - 1;
            return (parts.length == 5 || parts.length == 6)
                    && "mvn".equals(parts[0])
                    && parts[1].matches("[A-Za-z0-9_.-]+")
                    && parts[2].matches("[A-Za-z0-9_.-]+")
                    && parts[3].matches("[A-Za-z0-9_.-]+")
                    && (parts.length == 5 || parts[4].matches("[A-Za-z0-9_.-]+"))
                    && FIXED_VERSION.matcher(parts[versionIndex]).matches();
        }

        private static boolean binaryToolCoordinateValid(String coordinate) {
            String[] parts = coordinate.split(":", -1);
            return parts.length == 5
                    && "bin".equals(parts[0])
                    && parts[1].matches("[A-Za-z0-9_.-]+")
                    && FIXED_VERSION.matcher(parts[2]).matches()
                    && parts[3].matches("[a-z0-9_.-]+")
                    && parts[4].matches("[a-z0-9_.-]+");
        }

        private static String toolRoleDescription(String toolRole) {
            return toolRole.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        }

        private static void validateToolExecutions(
                Path evidence,
                Policy policy,
                Map<String, ToolClosure> closures) {
            Map<String, ExecutionTool> expectedExecutions = expectedToolExecutions(policy);
            Map<String, String[]> expectedRows = new LinkedHashMap<>();
            for (Map.Entry<String, ExecutionTool> execution : expectedExecutions.entrySet()) {
                ToolClosure closure = closures.get(execution.getValue().toolRole());
                for (String coordinate : closure.components().keySet()) {
                    expectedRows.put(execution.getKey() + "\t" + coordinate,
                            closure.components().get(coordinate));
                }
            }

            SealedBytes ledger = readRelative(
                    evidence, "metadata/tool-executions.tsv", "tool execution ledger",
                    MAX_AUTHORITY_BYTES);
            List<String[]> rows = parseTable(
                    ledger, "tool execution ledger", TOOL_EXECUTION_HEADER, 11, 100_000);
            Map<String, String[]> actualRows = new LinkedHashMap<>();
            Map<String, String[]> derivedRows = new LinkedHashMap<>();
            Map<String, String> rawFiles = new LinkedHashMap<>();
            for (String[] row : rows) {
                String key = row[0] + "\t" + row[5];
                ExecutionTool expectedExecution = expectedExecutions.get(row[0]);
                ToolClosure closure = expectedExecution == null
                        ? null : closures.get(expectedExecution.toolRole());
                String[] component = closure == null ? null : closure.components().get(row[5]);
                if (expectedExecution == null
                        || !expectedExecution.toolRole().equals(row[1])
                        || !expectedExecution.commandId().equals(row[2])
                        || !closure.bundleSha256().equals(row[3])
                        || component == null
                        || !component[1].equals(row[4])
                        || !component[3].equals(row[6])
                        || !component[4].equals(row[7])
                        || !TOOL_MEASUREMENT_KINDS.contains(row[8])
                        || actualRows.putIfAbsent(key, row) != null) {
                    throw new VerificationFailure("tool execution loaded row differs from policy closure");
                }
                SealedBytes raw = sealRelative(
                        evidence, row[9], row[10], "tool execution raw measurement");
                String previousSha = rawFiles.putIfAbsent(row[9], row[10]);
                if (previousSha == null) {
                    deriveToolFacts(evidence, raw, expectedExecutions, derivedRows);
                } else if (!previousSha.equals(row[10])) {
                    throw new VerificationFailure("tool execution raw measurement has conflicting hashes");
                }
            }
            if (!expectedRows.keySet().equals(actualRows.keySet())
                    || !expectedRows.keySet().equals(derivedRows.keySet())) {
                throw new VerificationFailure("tool execution loaded-byte closure differs from policy tools");
            }
            for (String key : expectedRows.keySet()) {
                String[] actual = actualRows.get(key);
                String[] fact = derivedRows.get(key);
                if (!actual[8].equals(fact[3])
                        || !actual[9].equals(fact[11])
                        || !actual[4].equals(fact[5])
                        || !actual[5].equals(fact[6])
                        || !actual[6].equals(fact[7])
                        || !actual[7].equals(fact[8])) {
                    throw new VerificationFailure("tool execution ledger differs from raw loaded facts");
                }
            }
        }

        private static Map<String, ExecutionTool> expectedToolExecutions(Policy policy) {
            List<String[]> rows = parseTable(
                    policy.authorities().get("releaseExecutionPolicy"),
                    "release execution policy",
                    RELEASE_POLICY_HEADER,
                    9,
                    4096);
            Map<String, ExecutionTool> result = new LinkedHashMap<>();
            for (String[] row : rows) {
                String toolRole = switch (row[1]) {
                    case "VULNERABILITY_SCAN" -> "VULNERABILITY_SCANNER";
                    case "SECRET_SCAN_FIRST", "SECRET_SCAN_SELF", "SENSITIVE_LOG_SCAN" ->
                            "SECRET_SCANNER";
                    case "SBOM_GENERATE" -> "SBOM_GENERATOR";
                    default -> null;
                };
                if (toolRole != null) {
                    result.put(row[0], new ExecutionTool(toolRole, row[2]));
                }
            }
            return Map.copyOf(result);
        }

        private static void deriveToolFacts(
                Path evidence,
                SealedBytes raw,
                Map<String, ExecutionTool> expectedExecutions,
                Map<String, String[]> derived) {
            List<String[]> facts = parseTable(
                    raw, "tool execution raw measurement", RAW_TOOL_FACT_HEADER, 11, 8192);
            Set<String> observationIds = new java.util.HashSet<>();
            String rawRelative = evidence.toAbsolutePath().normalize()
                    .relativize(raw.path()).toString().replace(java.io.File.separatorChar, '/');
            for (String[] fact : facts) {
                ExecutionTool execution = expectedExecutions.get(fact[1]);
                String key = fact[1] + "\t" + fact[6];
                if (!fact[0].matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                        || !observationIds.add(fact[0])
                        || execution == null
                        || !execution.toolRole().equals(fact[2])
                        || !TOOL_MEASUREMENT_KINDS.contains(fact[3])
                        || !toolMeasurementMatchesKind(fact[5], fact[3])) {
                    throw new VerificationFailure("tool execution raw loaded fact is invalid");
                }
                validateSourceLocator(fact[3], fact[4]);
                sealRelative(evidence, fact[7], fact[8], "tool execution measured bytes");
                validateRawObservation(evidence, fact[3], fact[4], fact[9], fact[10]);
                String[] envelope = java.util.Arrays.copyOf(fact, 12);
                envelope[11] = rawRelative;
                if (derived.putIfAbsent(key, envelope) != null) {
                    throw new VerificationFailure("tool execution raw loaded fact is duplicate");
                }
            }
        }

        private static boolean toolMeasurementMatchesKind(String loadedKind, String measurementKind) {
            return switch (loadedKind) {
                case "MAVEN" -> "JVM_CODESOURCE".equals(measurementKind);
                case "BINARY" -> "PROCESS_EXECUTABLE_MAP".equals(measurementKind);
                case "OCI" -> "OCI_RUNTIME_INSPECTION".equals(measurementKind);
                default -> false;
            };
        }

        private static void validateReleaseExecutions(Path evidence, Policy policy) {
            List<String[]> policyRows = parseTable(
                    policy.authorities().get("releaseExecutionPolicy"),
                    "release execution policy", RELEASE_POLICY_HEADER, 9, 4096);
            Map<String, String[]> expected = new LinkedHashMap<>();
            for (String[] row : policyRows) {
                expected.put(row[0], row);
            }
            SealedBytes ledger = readRelative(
                    evidence, "metadata/release-executions.tsv", "release execution ledger",
                    MAX_AUTHORITY_BYTES);
            List<String[]> actualRows = parseTable(
                    ledger, "release execution ledger", RELEASE_EXECUTION_HEADER, 17, 4096);
            Map<String, String[]> actual = new LinkedHashMap<>();
            for (String[] row : actualRows) {
                if (actual.putIfAbsent(row[0], row) != null) {
                    throw new VerificationFailure("release executionId is duplicate");
                }
            }
            if (!expected.keySet().equals(actual.keySet())) {
                throw new VerificationFailure("release execution policy and actual ledger closure differs");
            }
            for (Map.Entry<String, String[]> entry : expected.entrySet()) {
                String[] authority = entry.getValue();
                String[] row = actual.get(entry.getKey());
                if (!java.util.Arrays.equals(
                        authority, java.util.Arrays.copyOfRange(row, 0, 9))) {
                    throw new VerificationFailure("release execution authority columns differ from policy");
                }
                ScopedEvidence scope = executionScope(evidence, policy, authority[1]);
                if (!scope.relativePath().equals(row[9]) || !scope.bytes().sha256().equals(row[10])) {
                    throw new VerificationFailure("release execution scope differs from retained closure");
                }
                String reportPath = executionReportPath(policy, authority[1]);
                SealedBytes report = readRelative(
                        evidence, reportPath, "release execution raw report", MAX_AUTHORITY_BYTES);
                if (!reportPath.equals(row[11]) || !report.sha256().equals(row[12])) {
                    throw new VerificationFailure("release execution report differs from retained raw bytes");
                }
                validateExecutionResult(authority[1], report, policy);
                validateExecutionOutcome(row);
            }
        }

        private static ScopedEvidence executionScope(
                Path evidence, Policy policy, String role) {
            return switch (role) {
                case "PERFORMANCE" -> scopedAuthority(
                        evidence, policy, "runtimePerformancePolicy",
                        "metadata/execution-scopes/performance-policy.tsv");
                case "COMPATIBILITY" -> scopedAuthority(
                        evidence, policy, "compatibilityMatrix",
                        "metadata/execution-scopes/compatibility-matrix.tsv");
                case "VULNERABILITY_SCAN", "SBOM_GENERATE" -> scopedFile(
                        evidence, "metadata/runtime-artifacts.tsv", "runtime execution scope");
                case "SECRET_SCAN_FIRST" -> scopedFile(
                        evidence, "security/secret-scan/scope.tsv", "secret first scope");
                case "SECRET_SCAN_SELF" -> scopedFile(
                        evidence, "security/secret-scan/report-self-scan-scope.tsv", "secret self scope");
                case "SENSITIVE_LOG_SCAN" -> scopedFile(
                        evidence, "security/sensitive-log/scope.tsv", "sensitive-log scope");
                default -> throw new VerificationFailure("release execution role is unsupported");
            };
        }

        private static ScopedEvidence scopedAuthority(
                Path evidence, Policy policy, String authorityKey, String relative) {
            SealedBytes authority = policy.authorities().get(authorityKey);
            SealedBytes retained = sealRelative(
                    evidence, relative, authority.sha256(), "retained execution authority scope");
            return new ScopedEvidence(relative, retained);
        }

        private static ScopedEvidence scopedFile(
                Path evidence, String relative, String description) {
            return new ScopedEvidence(
                    relative, readRelative(evidence, relative, description, MAX_AUTHORITY_BYTES));
        }

        private static String executionReportPath(Policy policy, String role) {
            return switch (role) {
                case "PERFORMANCE" -> "runtime/performance/results.tsv";
                case "COMPATIBILITY" -> "runtime/compatibility/results.tsv";
                case "VULNERABILITY_SCAN" -> "security/vulnerability/report.json";
                case "SECRET_SCAN_FIRST" -> "security/secret-scan/report.json";
                case "SECRET_SCAN_SELF" -> "security/secret-scan/report-self-scan.tsv";
                case "SBOM_GENERATE" -> "CycloneDX-1.6".equals(policy.values().get("sbomFormat"))
                        ? "supply-chain/sbom/bom.cdx.json" : "supply-chain/sbom/bom.spdx.json";
                case "SENSITIVE_LOG_SCAN" -> "security/sensitive-log/raw-result.tsv";
                default -> throw new VerificationFailure("release execution role is unsupported");
            };
        }

        private static void validateExecutionOutcome(String[] row) {
            try {
                int exit = Integer.parseInt(row[13]);
                Instant started = Instant.parse(row[14]);
                Instant ended = Instant.parse(row[15]);
                if (exit < 0 || exit > 255 || !Integer.toString(exit).equals(row[13])
                        || !started.toString().equals(row[14]) || !ended.toString().equals(row[15])
                        || started.isAfter(ended)) {
                    throw new VerificationFailure("release execution runtime facts are invalid");
                }
            } catch (NumberFormatException | DateTimeParseException failure) {
                throw new VerificationFailure("release execution runtime facts are invalid");
            }
            if (!"0".equals(row[13]) || !"PASS".equals(row[16])) {
                throw new VerificationFailure("release execution is blocking");
            }
        }

        private static void validateExecutionResult(
                String role, SealedBytes report, Policy policy) {
            if ("PERFORMANCE".equals(role)) {
                validatePerformanceExecutionResult(report, policy);
            } else if ("COMPATIBILITY".equals(role)) {
                validateCompatibilityExecutionResult(report, policy);
            }
        }

        private static void validatePerformanceExecutionResult(
                SealedBytes report, Policy policy) {
            List<String[]> policyRows = parseTable(
                    policy.authorities().get("runtimePerformancePolicy"),
                    "runtime performance policy",
                    "workloadId\tscenario\tthreads\tfixtureManifestPath\tfixtureManifestSha256\t"
                            + "semanticOraclePath\tsemanticOracleSha256\tbenchmarkEnvironmentPath\t"
                            + "benchmarkEnvironmentSha256\trunnerProfile\tevidenceCommandId\t"
                            + "commandSpecSha256\tabsoluteP99NanosMax\tbaselineArtifactSetSha256\t"
                            + "maxRegressionPercent\tallocationBytesPerOpMax",
                    16,
                    64);
            List<String[]> rows = parseTable(
                    report,
                    "performance execution result",
                    "workloadId\tevidenceCommandId\tcandidateReportPath\tbaselineReportPath\t"
                            + "candidateP99Nanos\tbaselineP99Nanos\tregressionPercent\t"
                            + "candidateAllocationBytesPerOp\tsemanticLogicalFactsSha256\t"
                            + "semanticFactsFileSha256\tenvironmentSha256\tsemanticStatus\t"
                            + "codeSourceStatus\tstatus",
                    14,
                    64);
            Map<String, String> expected = new LinkedHashMap<>();
            for (String[] row : policyRows) {
                expected.put(row[0], row[10]);
            }
            Map<String, String[]> actual = new LinkedHashMap<>();
            for (String[] row : rows) {
                if (!expected.getOrDefault(row[0], "<missing>").equals(row[1])
                        || !java.util.Arrays.equals(
                        java.util.Arrays.copyOfRange(row, 11, 14),
                        new String[]{"PASS", "PASS", "PASS"})
                        || actual.putIfAbsent(row[0], row) != null) {
                    throw new VerificationFailure("performance execution result is invalid or duplicate");
                }
            }
            if (!expected.keySet().equals(actual.keySet())) {
                throw new VerificationFailure("performance execution result closure differs from policy");
            }
        }

        private static void validateCompatibilityExecutionResult(
                SealedBytes report, Policy policy) {
            List<String[]> policyRows = parseTable(
                    policy.authorities().get("compatibilityMatrix"),
                    "compatibility matrix",
                    "edgeKind\tconsumerGa\tconsumerVersion\tdependencyGa\tdependencyVersion\t"
                            + "expected\tenforcement\tevidenceCommandId",
                    8,
                    4096);
            List<String[]> rows = parseTable(
                    report,
                    "compatibility execution result",
                    "edgeKind\tconsumerGa\tconsumerVersion\tdependencyGa\tdependencyVersion\t"
                            + "expected\tenforcement\tevidenceCommandId\tactualExit\t"
                            + "failureClassifier\tresolvedArtifactsPath\tdependencyTreePath\t"
                            + "codeSourcePath\trawEvidencePath\tstatus",
                    15,
                    4096);
            Map<String, String[]> expected = new LinkedHashMap<>();
            for (String[] row : policyRows) {
                expected.put(String.join("\t", java.util.Arrays.copyOfRange(row, 0, 5)), row);
            }
            Map<String, String[]> actual = new LinkedHashMap<>();
            for (String[] row : rows) {
                String key = String.join("\t", java.util.Arrays.copyOfRange(row, 0, 5));
                String[] authority = expected.get(key);
                if (authority == null
                        || !java.util.Arrays.equals(
                        authority, java.util.Arrays.copyOfRange(row, 0, 8))
                        || !"PASS".equals(row[14])
                        || actual.putIfAbsent(key, row) != null) {
                    throw new VerificationFailure("compatibility execution result is invalid or duplicate");
                }
            }
            if (!expected.keySet().equals(actual.keySet())) {
                throw new VerificationFailure("compatibility execution result closure differs from policy");
            }
        }

        /**
         * @param bundleSha256 policy identity 中的 canonical manifest SHA
         * @param components coordinate 到实际 retained loaded bytes 行
         */
        private record ToolClosure(
                String bundleSha256, Map<String, String[]> components) {
        }

        /**
         * @param toolRole policy 绑定的 scanner/generator role
         * @param commandId 本次执行必须使用的 expected command
         */
        private record ExecutionTool(String toolRole, String commandId) {
        }

        /**
         * @param relativePath evidence-root relative canonical scope path
         * @param bytes 单次封存并已验证 SHA 的 scope bytes
         */
        private record ScopedEvidence(String relativePath, SealedBytes bytes) {
        }

        private static void deriveBuildFacts(
                Path evidence,
                SealedBytes raw,
                Map<String, String[]> derived) {
            List<String[]> facts = parseTable(
                    raw, "build toolchain raw measurement", RAW_BUILD_FACT_HEADER, 9, 8192);
            Set<String> observationIds = new java.util.HashSet<>();
            String rawRelative = evidence.toAbsolutePath().normalize()
                    .relativize(raw.path()).toString().replace(java.io.File.separatorChar, '/');
            for (String[] fact : facts) {
                String key = fact[3] + "\t" + fact[4];
                if (!fact[0].matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                        || !observationIds.add(fact[0])
                        || !BUILD_MEASUREMENT_KINDS.contains(fact[1])
                        || !AuthoritySchemas.TOOLCHAIN_ROLES.contains(fact[3])
                        || !measurementAllowed(fact[3], fact[1])) {
                    throw new VerificationFailure("build toolchain raw fact is invalid or duplicate");
                }
                validateSourceLocator(fact[1], fact[2]);
                SealedBytes loaded = sealRelative(
                        evidence, fact[5], fact[6], "build toolchain measured bytes");
                if (!loaded.sha256().equals(fact[6])) {
                    throw new VerificationFailure("build toolchain raw fact SHA differs from loaded bytes");
                }
                validateRawObservation(evidence, fact[1], fact[2], fact[7], fact[8]);
                String[] envelope = java.util.Arrays.copyOf(fact, 10);
                envelope[9] = rawRelative;
                if (derived.putIfAbsent(key, envelope) != null) {
                    throw new VerificationFailure("build toolchain raw loaded fact is duplicate");
                }
            }
        }

        private static boolean measurementAllowed(String role, String kind) {
            return switch (role) {
                case "RUNNER_IMAGE" -> "OCI_RUNTIME_INSPECTION".equals(kind);
                case "JDK_RUNTIME" -> Set.of("PROCESS_EXECUTABLE_MAP", "JVM_CODESOURCE").contains(kind);
                case "MAVEN_DISTRIBUTION" ->
                        Set.of("PROCESS_EXECUTABLE_MAP", "MAVEN_CLASSREALM").contains(kind);
                case "MAVEN_WRAPPER" ->
                        Set.of("JVM_CODESOURCE", "MAVEN_CLASSREALM").contains(kind);
                case "MAVEN_PLUGIN", "BUILD_EXTENSION", "BUILD_DEPENDENCY" ->
                        "MAVEN_CLASSREALM".equals(kind);
                default -> false;
            };
        }

        /**
         * Raw fact 只负责索引；locator 必须能从外部工具保留的机器输出重新派生。
         * 这样 expected manifest 或 normalized TSV 不能自行充当运行时观测。
         */
        private static void validateRawObservation(
                Path evidence,
                String kind,
                String locator,
                String rawSourcePath,
                String rawSourceSha256) {
            SealedBytes source = sealRelative(
                    evidence, rawSourcePath, rawSourceSha256, "retained raw observation");
            switch (kind) {
                case "PROCESS_EXECUTABLE_MAP" -> validateProcessObservation(source, locator);
                case "JVM_CODESOURCE", "MAVEN_CLASSREALM" ->
                        validateJvmObservation(source, locator, kind);
                case "OCI_RUNTIME_INSPECTION" -> validateOciObservation(source, locator);
                default -> throw new VerificationFailure("retained raw observation kind is invalid");
            }
        }

        private static void validateProcessObservation(SealedBytes source, String locator) {
            int separator = locator.indexOf(':');
            if (separator < 0) {
                throw new VerificationFailure("retained raw observation does not derive process locator");
            }
            String target = locator.substring(separator + 1);
            List<String> lines = decodeLines(source.bytes(), "retained process observation");
            boolean matched;
            if (locator.substring(0, separator).endsWith("/exe")) {
                matched = lines.size() == 1 && target.equals(lines.getFirst());
            } else {
                matched = lines.stream().anyMatch(line -> processMapLineMatches(line, target));
            }
            if (!matched) {
                throw new VerificationFailure("retained raw observation does not derive process locator");
            }
        }

        private static boolean processMapLineMatches(String line, String target) {
            String[] columns = line.split("\\s+", 6);
            return columns.length == 6
                    && columns[0].matches("[0-9a-f]+-[0-9a-f]+")
                    && columns[1].matches("[r-][w-][x-][ps]")
                    && columns[2].matches("[0-9a-f]+")
                    && columns[3].matches("[0-9a-f]+:[0-9a-f]+")
                    && columns[4].matches("[0-9]+")
                    && target.equals(columns[5]);
        }

        private static void validateJvmObservation(
                SealedBytes source, String locator, String kind) {
            List<String> locations = decodeLines(source.bytes(), "retained JVM observation");
            for (String location : locations) {
                validateSourceLocator(kind, location);
            }
            if (locations.isEmpty() || !locations.contains(locator)) {
                throw new VerificationFailure("retained raw observation does not derive JVM locator");
            }
        }

        private static void validateOciObservation(SealedBytes source, String locator) {
            Object parsed = StrictJson.parse(source, "retained OCI observation");
            Object candidate = parsed;
            if (parsed instanceof List<?> list && list.size() == 1) {
                candidate = list.getFirst();
            }
            if (!(candidate instanceof Map<?, ?> object)
                    || !(object.get("RepoDigests") instanceof List<?> digests)
                    || digests.isEmpty()) {
                throw new VerificationFailure("retained raw observation has no OCI RepoDigests");
            }
            String expected = locator.substring("oci-runtime://".length());
            boolean matched = false;
            for (Object digest : digests) {
                if (!(digest instanceof String value)
                        || !value.matches("[^\\s@]+@sha256:[0-9a-f]{64}")) {
                    throw new VerificationFailure("retained raw observation has invalid OCI digest");
                }
                matched |= expected.equals(value);
            }
            if (!matched) {
                throw new VerificationFailure("retained raw observation does not derive OCI locator");
            }
        }

        private static void validateSourceLocator(String kind, String locator) {
            boolean valid = switch (kind) {
                case "OCI_RUNTIME_INSPECTION" -> locator.matches(
                        "oci-runtime://[^\\s@]+@sha256:[0-9a-f]{64}");
                case "PROCESS_EXECUTABLE_MAP" -> locator.matches(
                        "/proc/[1-9][0-9]*/(exe|maps):/[^\\s]+");
                case "JVM_CODESOURCE", "MAVEN_CLASSREALM" ->
                        locator.startsWith("file:/")
                                && !locator.contains("..")
                                && locator.chars().noneMatch(Character::isWhitespace);
                default -> false;
            };
            if (!valid) {
                throw new VerificationFailure("raw measurement source locator is invalid");
            }
        }
    }

    /** CLI 只输出稳定失败分类，不把宿主路径堆栈写入发布证据。 */
    public static void main(String[] args) {
        try {
            SEALED_READ_HASHES.set(new java.util.HashMap<>());
            run(args);
        } catch (UsageFailure failure) {
            System.err.println(failure.getMessage());
            System.exit(64);
        } catch (VerificationFailure failure) {
            System.err.println(failure.getMessage());
            System.exit(2);
        } finally {
            SEALED_READ_HASHES.remove();
        }
    }

    private static void run(String[] args) {
        if (args.length == 0 || !MODES.contains(args[0])) {
            String mode = args.length == 0 ? "<missing>" : args[0];
            throw new UsageFailure("unknown mode: " + mode + System.lineSeparator() + USAGE);
        }
        switch (args[0]) {
            case "verify-policy" -> {
                requireArity(args, 2);
                Policy policy = Policy.load(Path.of(args[1]));
                System.out.println("POLICY_OK\t" + policy.sha256());
            }
            case "verify-supply-chain" -> {
                requireArity(args, 3);
                Path evidence = requireReadableDirectory(Path.of(args[1]), "evidence directory");
                Policy policy = Policy.load(Path.of(args[2]));
                SupplyChain.verify(evidence, policy, false);
                System.out.println("SUPPLY_CHAIN_OK");
            }
            case "verify-all" -> {
                requireArity(args, 3);
                Path evidence = requireReadableDirectory(Path.of(args[1]), "evidence directory");
                Path expectedReports = requireReadableFile(Path.of(args[2]), "expected reports");
                Path retainedPolicy = requireReadableFile(
                        evidence.resolve("policy/production-policy.tsv"),
                        "retained production policy");
                Policy policy = Policy.load(retainedPolicy);
                if (Files.exists(
                        evidence.resolve("metadata/review-assignment.tsv"),
                        LinkOption.NOFOLLOW_LINKS)) {
                    ProductionMode.validate(evidence, policy);
                }
                IntegrityEvidence.verifyPreparedContent(evidence, expectedReports);
                SupplyChain.verify(evidence, policy, false);
                System.out.println("ALL_OK");
            }
            case "verify-integrity" -> {
                requireArity(args, 3);
                Path evidence = requireReadableDirectory(Path.of(args[1]), "evidence directory");
                Path expectedReports = requireReadableFile(Path.of(args[2]), "expected reports");
                IntegrityEvidence.verify(evidence, expectedReports);
                System.out.println("INTEGRITY_OK");
            }
            default -> throw new UsageFailure("unknown mode: " + args[0]);
        }
    }

    private static void requireArity(String[] args, int expected) {
        if (args.length != expected) {
            throw new UsageFailure(USAGE);
        }
    }

    private static SealedBytes readSealed(Path input, String description, int maximumBytes) {
        Path path = input.toAbsolutePath().normalize();
        BasicFileAttributes before;
        try {
            before = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException failure) {
            throw new VerificationFailure(description + " is not a readable regular file");
        }
        if (!before.isRegularFile() || !Files.isReadable(path)) {
            throw new VerificationFailure(description + " is not a readable regular file");
        }
        if (before.size() > maximumBytes) {
            throw new VerificationFailure(description + " exceeds its byte limit");
        }

        byte[] bytes = new byte[(int) before.size()];
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (var channel = Files.newByteChannel(path, options)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                // Bounded buffer is sized from the sealed pre-read attributes.
            }
            ByteBuffer extra = ByteBuffer.allocate(1);
            if (buffer.hasRemaining() || channel.read(extra) >= 0) {
                throw new VerificationFailure(description + " changed while being read");
            }
        } catch (VerificationFailure failure) {
            throw failure;
        } catch (IOException failure) {
            throw new VerificationFailure(description + " cannot be read");
        }

        BasicFileAttributes after;
        try {
            after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException failure) {
            throw new VerificationFailure(description + " changed while being read");
        }
        if (!after.isRegularFile()
                || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey())) {
            throw new VerificationFailure(description + " changed while being read");
        }
        String digest = sha256(bytes);
        String previous = SEALED_READ_HASHES.get().putIfAbsent(path, digest);
        if (previous != null && !previous.equals(digest)) {
            throw new VerificationFailure(description + " changed between sealed reads");
        }
        return new SealedBytes(path, bytes, digest);
    }

    private static void rejectRelativeSymbolicComponents(
            Path base, String relative, String description) {
        Path current = base;
        for (String part : relative.split("/", -1)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new VerificationFailure(description + " must not traverse a symbolic link");
            }
        }
    }

    private static List<String> decodeLines(byte[] bytes, String description) {
        if (bytes.length >= 3
                && Byte.toUnsignedInt(bytes[0]) == 0xEF
                && Byte.toUnsignedInt(bytes[1]) == 0xBB
                && Byte.toUnsignedInt(bytes[2]) == 0xBF) {
            throw new VerificationFailure(description + " must not contain a UTF-8 BOM");
        }
        String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new VerificationFailure(description + " must be valid UTF-8");
        }
        if (content.indexOf('\r') >= 0 || content.indexOf('\0') >= 0) {
            throw new VerificationFailure(description + " must use LF and contain no NUL");
        }
        String[] split = content.split("\n", -1);
        int length = split.length;
        if (length > 0 && split[length - 1].isEmpty()) {
            length--;
        }
        List<String> lines = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            if (split[index].length() > 4096) {
                throw new VerificationFailure(description + " contains an overlong line");
            }
            lines.add(split[index]);
        }
        return List.copyOf(lines);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void requireRelativePosix(String value, String field) {
        if (value.isEmpty() || value.startsWith("/") || value.contains("\\")) {
            throw new VerificationFailure(field + " must be a relative POSIX path");
        }
        for (String part : value.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new VerificationFailure(field + " must be a relative POSIX path");
            }
        }
    }

    private static void requireHttpsUri(String value, String field) {
        try {
            URI uri = new URI(value);
            if (!"https".equals(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || uri.getPort() == 443
                    || !uri.normalize().toASCIIString().equals(value)) {
                throw new VerificationFailure(field + " must be a canonical credential-free HTTPS URI");
            }
        } catch (URISyntaxException failure) {
            throw new VerificationFailure(field + " must be a canonical credential-free HTTPS URI");
        }
    }

    private static SealedBytes sealRelative(
            Path base, String relative, String expectedSha, String description) {
        if (!SHA256.matcher(expectedSha).matches()) {
            throw new VerificationFailure(description + " SHA must be lowercase 64-hex");
        }
        SealedBytes sealed = readRelative(base, relative, description, MAX_AUTHORITY_BYTES);
        if (!sealed.sha256().equals(expectedSha)) {
            throw new VerificationFailure(description + " SHA does not match retained bytes");
        }
        return sealed;
    }

    private static SealedBytes readRelative(
            Path base, String relative, String description, int maximumBytes) {
        requireRelativePosix(relative, description);
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path path = normalizedBase.resolve(relative).normalize();
        if (!path.startsWith(normalizedBase)) {
            throw new VerificationFailure(description + " must be a relative POSIX path");
        }
        rejectRelativeSymbolicComponents(normalizedBase, relative, description);
        return readSealed(path, description, maximumBytes);
    }

    private static String decodeUtf8(byte[] bytes, String description) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new VerificationFailure(description + " must be valid UTF-8");
        }
    }

    private static List<String[]> parseTable(
            SealedBytes file,
            String description,
            String header,
            int columns,
            int maximumRows) {
        List<String> lines = decodeLines(file.bytes(), description);
        if (lines.size() < 2 || !header.equals(lines.getFirst())) {
            throw new VerificationFailure(description + " has an invalid header or no data rows");
        }
        if (lines.size() - 1 > maximumRows) {
            throw new VerificationFailure(description + " exceeds its row limit");
        }
        List<String[]> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String[] row = lines.get(index).split("\t", -1);
            if (row.length != columns) {
                throw new VerificationFailure(description + " row must have exactly " + columns + " columns");
            }
            for (String value : row) {
                if (value.isEmpty() || value.length() > 2048
                        || value.chars().anyMatch(Character::isISOControl)) {
                    throw new VerificationFailure(description + " row contains an invalid value");
                }
            }
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private static List<String[]> parseTableAllowEmpty(
            SealedBytes file,
            String description,
            String header,
            int columns,
            int maximumRows) {
        List<String> lines = decodeLines(file.bytes(), description);
        if (lines.isEmpty() || !header.equals(lines.getFirst())) {
            throw new VerificationFailure(description + " has an invalid header");
        }
        if (lines.size() - 1 > maximumRows) {
            throw new VerificationFailure(description + " exceeds its row limit");
        }
        List<String[]> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String[] row = lines.get(index).split("\t", -1);
            if (row.length != columns) {
                throw new VerificationFailure(description + " row must have exactly " + columns + " columns");
            }
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    /**
     * 单次封存并通过基础身份校验的 production policy。
     *
     * @param values 32 个按 authority 顺序解析的值
     * @param authorities 已按 relative path 与 SHA 封存的直接引用
     * @param sha256 production policy 原始 bytes 的摘要
     */
    private record Policy(
            Map<String, String> values,
            Map<String, SealedBytes> authorities,
            String sha256) {

        private static Policy load(Path input) {
            SealedBytes policy = readSealed(input, "production policy", MAX_POLICY_BYTES);
            List<String> lines = decodeLines(policy.bytes(), "production policy");
            if (lines.size() != POLICY_KEYS.size()) {
                throw new VerificationFailure("production policy must contain exactly 32 lines");
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int index = 0; index < POLICY_KEYS.size(); index++) {
                String line = lines.get(index);
                int separator = line.indexOf('\t');
                if (separator <= 0 || separator != line.lastIndexOf('\t')) {
                    throw new VerificationFailure(
                            "policy line " + (index + 1) + " must have exactly two TSV columns");
                }
                String expectedKey = POLICY_KEYS.get(index);
                String key = line.substring(0, separator);
                if (!expectedKey.equals(key)) {
                    throw new VerificationFailure(
                            "policy key at line " + (index + 1) + " must be " + expectedKey);
                }
                String value = line.substring(separator + 1);
                if (value.isEmpty() || !value.equals(value.strip())
                        || value.chars().anyMatch(Character::isISOControl)) {
                    throw new VerificationFailure(
                            "policy value at line " + (index + 1) + " is invalid");
                }
                values.put(key, value);
            }
            validateIdentity(values);
            Map<String, SealedBytes> authorities = sealAuthorities(policy.path(), values);
            ProductionAuthorities.validate(
                    policy.path().getParent(),
                    values,
                    authorities.get("productionAuthoritiesManifest"));
            AuthoritySchemas.validate(policy.path().getParent(), values, authorities);
            return new Policy(Map.copyOf(values), Map.copyOf(authorities), policy.sha256());
        }

        private static void validateIdentity(Map<String, String> values) {
            if (!values.get("policyId").matches("[A-Za-z0-9][A-Za-z0-9_.:-]{1,127}")
                    || !values.get("policyId").contains(":")) {
                throw new VerificationFailure("policyId must be an authority token");
            }
            if (!values.get("reviewAssignmentId").matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) {
                throw new VerificationFailure("reviewAssignmentId must be a closed token");
            }
            requireHttpsUri(values.get("repository"), "repository");
            String protectedRef = values.get("protectedRef");
            if (!protectedRef.matches("refs/(heads|tags)/[A-Za-z0-9][A-Za-z0-9._/-]*")
                    || protectedRef.contains("..") || protectedRef.contains("//")
                    || protectedRef.endsWith("/")) {
                throw new VerificationFailure("protectedRef must be a canonical heads/tags ref");
            }
            if (!values.get("candidateRevision").matches("[0-9a-f]{40}")) {
                throw new VerificationFailure("candidateRevision must be lowercase 40-hex");
            }
            String version = values.get("finalVersion");
            if (!FIXED_VERSION.matcher(version).matches() || "3.0.0".equals(version)) {
                throw new VerificationFailure("finalVersion must be a fixed release version other than 3.0.0");
            }
            String targetKind = validateReleaseTarget(values.get("releaseTarget"));
            validateExternalAuthority(values, targetKind);
            validateBuilder(values.get("trustedBuilder"));
            validateWorkflow(values.get("provenanceWorkflow"));
            for (String key : List.of("vulnerabilityScanner", "secretScanner", "sbomGenerator")) {
                if (!TOOL_IDENTITY.matcher(values.get(key)).matches()) {
                    throw new VerificationFailure(key + " must bind a canonical tool bundle");
                }
            }
            try {
                BigDecimal threshold = new BigDecimal(values.get("vulnerabilityFailCvssThreshold"));
                if (threshold.compareTo(BigDecimal.ZERO) <= 0
                        || threshold.compareTo(new BigDecimal("7.0")) > 0) {
                    throw new NumberFormatException();
                }
                int maximumAge = Integer.parseInt(values.get("vulnerabilityDbMaxAgeHours"));
                if (maximumAge <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException failure) {
                throw new VerificationFailure("vulnerability thresholds are invalid");
            }
            if (!Set.of("CycloneDX-1.6", "SPDX-2.3").contains(values.get("sbomFormat"))) {
                throw new VerificationFailure("sbomFormat is unsupported");
            }
            if (!Set.of("NONE", "PGP", "SIGSTORE", "PGP,SIGSTORE")
                    .contains(values.get("requiredSignatures"))) {
                throw new VerificationFailure("requiredSignatures is unsupported");
            }
        }

        private static String validateReleaseTarget(String value) {
            int separator = value.indexOf(':');
            if (separator <= 0 || separator == value.length() - 1) {
                throw new VerificationFailure("releaseTarget is invalid");
            }
            String kind = value.substring(0, separator);
            String payload = value.substring(separator + 1);
            if (Set.of("INTERNAL_REPOSITORY", "EXTERNAL_REPOSITORY").contains(kind)) {
                requireHttpsUri(payload, "releaseTarget");
            } else if ("MAVEN_CENTRAL".equals(kind)) {
                if (!payload.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*")
                        || payload.contains("..")) {
                    throw new VerificationFailure("MAVEN_CENTRAL target must contain a canonical groupId");
                }
            } else {
                throw new VerificationFailure("releaseTarget is invalid");
            }
            return kind;
        }

        private static void validateExternalAuthority(Map<String, String> values, String targetKind) {
            boolean internal = "INTERNAL_REPOSITORY".equals(targetKind);
            String path = values.get("externalPublicationAuthority");
            String digest = values.get("externalPublicationAuthoritySha256");
            if (internal != ("NONE".equals(path) && "-".equals(digest))) {
                throw new VerificationFailure("external publication authority does not match releaseTarget");
            }
            if (!internal && (!SHA256.matcher(digest).matches() || "NONE".equals(path))) {
                throw new VerificationFailure("external publication authority does not match releaseTarget");
            }
        }

        private static void validateBuilder(String value) {
            String[] parts = value.split("\\|", -1);
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                throw new VerificationFailure("trustedBuilder must contain issuer and builder id");
            }
        }

        private static void validateWorkflow(String value) {
            String[] parts = value.split("\\|", -1);
            if (parts.length != 3 || parts[0].isEmpty() || !parts[2].matches("[0-9a-f]{40}")) {
                throw new VerificationFailure("provenanceWorkflow is invalid");
            }
            requireRelativePosix(parts[1], "provenanceWorkflow path");
        }

        private static Map<String, SealedBytes> sealAuthorities(
                Path policyPath, Map<String, String> values) {
            Path base = policyPath.getParent().toAbsolutePath().normalize();
            Map<String, SealedBytes> result = new LinkedHashMap<>();
            for (String key : AUTHORITY_PATH_KEYS) {
                sealAuthority(base, key, values.get(key), values.get(key + "Sha256"), result);
            }
            if (!"NONE".equals(values.get("externalPublicationAuthority"))) {
                sealAuthority(
                        base,
                        "externalPublicationAuthority",
                        values.get("externalPublicationAuthority"),
                        values.get("externalPublicationAuthoritySha256"),
                        result);
            }
            return result;
        }

        private static void sealAuthority(
                Path base,
                String key,
                String relative,
                String expectedSha,
                Map<String, SealedBytes> result) {
            try {
                result.put(key, sealRelative(base, relative, expectedSha, key));
            } catch (VerificationFailure failure) {
                String digestMessage = key + " SHA does not match retained bytes";
                if (digestMessage.equals(failure.getMessage())) {
                    throw new VerificationFailure(key + "Sha256 does not match retained bytes");
                }
                throw failure;
            }
        }
    }

    /** verify-all 的生产信任边界；测试 fixture 只能进入局部合同入口。 */
    private static final class ProductionMode {
        /** 显式测试材料标记，生产模式不得以任意摘要包装后继续使用。 */
        private static final byte[] TEST_ONLY = "TEST_ONLY".getBytes(StandardCharsets.US_ASCII);

        private ProductionMode() {
        }

        private static void validate(Path evidence, Policy policy) {
            String policyId = policy.values().get("policyId").toLowerCase(java.util.Locale.ROOT);
            if (policyId.matches(".*(^|[:._-])test([:._-]|$).*") || policyId.contains("test-policy")) {
                reject();
            }
            for (SealedBytes authority : policy.authorities().values()) {
                rejectMarker(authority);
            }
            validatePolicyToolBytes(policy);
            validateTrustBytes(policy);
            validateExecutionConfigBytes(policy);
            validateEvidenceToolBytes(evidence);
            validateVulnerabilityAuthority(evidence);
        }

        private static void validatePolicyToolBytes(Policy policy) {
            Path base = policy.authorities().get("buildToolchainManifest").path().getParent();
            List<String[]> rows = parseTable(
                    policy.authorities().get("buildToolchainManifest"),
                    "build toolchain manifest",
                    "ordinal\trole\tcoordinate\tevidencePath\tsha256",
                    5,
                    8192);
            for (String[] row : rows) {
                rejectMarker(sealRelative(base, row[3], row[4], "build toolchain component"));
            }
        }

        private static void validateTrustBytes(Policy policy) {
            SealedBytes authorities = policy.authorities().get("productionAuthoritiesManifest");
            Path base = authorities.path().getParent();
            List<String> lines = decodeLines(authorities.bytes(), "production authorities manifest");
            for (String line : lines) {
                String[] row = line.split("\t", -1);
                if (!"NONE".equals(row[1])) {
                    SealedBytes root = sealRelative(base, row[1], row[2], row[0]);
                    rejectMarker(root);
                    if (Set.of(
                            "PROVENANCE_TRUST_ROOT",
                            "EXTERNAL_PUBLICATION_TRUST_ROOT",
                            "ARTIFACT_SIGNATURE_TRUST_ROOT").contains(row[0])) {
                        inspectReferencedColumn(base, root, 4, 2, 3, "signature trust material");
                    } else if ("VULNERABILITY_DATABASE_TRUST_ROOT".equals(row[0])) {
                        inspectReferencedColumn(base, root, 6, 3, 4, "database trust material");
                    }
                }
            }
        }

        private static void inspectReferencedColumn(
                Path base,
                SealedBytes manifest,
                int columns,
                int pathColumn,
                int shaColumn,
                String description) {
            List<String> lines = decodeLines(manifest.bytes(), description + " manifest");
            for (int index = 1; index < lines.size(); index++) {
                String[] row = lines.get(index).split("\t", -1);
                if (row.length != columns) {
                    throw new VerificationFailure(description + " manifest row is malformed");
                }
                rejectMarker(sealRelative(base, row[pathColumn], row[shaColumn], description));
            }
        }

        private static void validateExecutionConfigBytes(Policy policy) {
            Path base = policy.authorities().get("releaseExecutionPolicy").path().getParent();
            List<String[]> rows = parseTable(
                    policy.authorities().get("releaseExecutionPolicy"),
                    "release execution policy",
                    ExecutionEvidence.RELEASE_POLICY_HEADER,
                    9,
                    4096);
            Map<String, String> seen = new LinkedHashMap<>();
            for (String[] row : rows) {
                for (int column : List.of(4, 6)) {
                    if (!"NONE".equals(row[column])) {
                        String sha = row[column + 1];
                        String previous = seen.putIfAbsent(row[column], sha);
                        if (previous == null) {
                            rejectMarker(sealRelative(base, row[column], sha, "release tool configuration"));
                        } else if (!previous.equals(sha)) {
                            throw new VerificationFailure("release tool configuration hashes conflict");
                        }
                    }
                }
            }
        }

        private static void validateEvidenceToolBytes(Path evidence) {
            for (String path : List.of(
                    "supply-chain/tool-closures/build-toolchain.tsv",
                    "security/tool-closures/vulnerability-scanner.tsv",
                    "security/tool-closures/secret-scanner.tsv",
                    "supply-chain/tool-closures/sbom-generator.tsv")) {
                SealedBytes manifest = readRelative(
                        evidence, path, "actual tool closure", MAX_AUTHORITY_BYTES);
                List<String[]> rows = parseTable(
                        manifest, "actual tool closure",
                        "ordinal\trole\tcoordinate\tevidencePath\tsha256".equals(
                                decodeLines(manifest.bytes(), "actual tool closure").getFirst())
                                ? "ordinal\trole\tcoordinate\tevidencePath\tsha256"
                                : "ordinal\tkind\tcoordinate\tevidencePath\tsha256",
                        5,
                        8192);
                for (String[] row : rows) {
                    rejectMarker(sealRelative(evidence, row[3], row[4], "actual loaded tool bytes"));
                }
            }
        }

        private static void validateVulnerabilityAuthority(Path evidence) {
            rejectMarker(readRelative(
                    evidence, "security/vulnerability/database/manifest.sig",
                    "vulnerability database signature", 1024 * 1024));
            rejectMarker(readRelative(
                    evidence, "security/vulnerability/scanner.log",
                    "vulnerability scanner log", MAX_AUTHORITY_BYTES));
        }

        private static void rejectMarker(SealedBytes bytes) {
            byte[] value = bytes.bytes();
            outer:
            for (int start = 0; start <= value.length - TEST_ONLY.length; start++) {
                for (int offset = 0; offset < TEST_ONLY.length; offset++) {
                    if (value[start + offset] != TEST_ONLY[offset]) {
                        continue outer;
                    }
                }
                reject();
            }
        }

        private static void reject() {
            throw new VerificationFailure("TEST_ONLY authority is forbidden in production mode");
        }
    }

    /** 五行外部信任 authority 及其 material closure 的结构化验证。 */
    private static final class ProductionAuthorities {
        /** 顺序是 policy 语义的一部分，避免缺失 root 被其他行替代。 */
        private static final List<String> KEYS = List.of(
                "PROVENANCE_TRUST_ROOT",
                "EXTERNAL_PUBLICATION_TRUST_ROOT",
                "ARTIFACT_SIGNATURE_TRUST_ROOT",
                "VULNERABILITY_DATABASE_TRUST_ROOT",
                "VULNERABILITY_SUPPRESSIONS");

        private ProductionAuthorities() {
        }

        private static void validate(
                Path base, Map<String, String> policy, SealedBytes manifest) {
            List<String> lines = decodeLines(manifest.bytes(), "production authorities manifest");
            if (lines.size() != KEYS.size()) {
                throw new VerificationFailure("production authorities manifest must contain exactly five lines");
            }
            Map<String, String[]> values = new LinkedHashMap<>();
            for (int index = 0; index < KEYS.size(); index++) {
                String[] columns = lines.get(index).split("\t", -1);
                if (columns.length != 3) {
                    throw new VerificationFailure(
                            "production authorities line " + (index + 1) + " must have three columns");
                }
                String expected = KEYS.get(index);
                if (!expected.equals(columns[0])) {
                    throw new VerificationFailure(
                            "production authorities key at line " + (index + 1) + " must be " + expected);
                }
                values.put(expected, columns);
            }

            SealedBytes provenance = requiredRoot(base, values.get("PROVENANCE_TRUST_ROOT"));
            SealedBytes database = requiredRoot(base, values.get("VULNERABILITY_DATABASE_TRUST_ROOT"));
            boolean internal = policy.get("releaseTarget").startsWith("INTERNAL_REPOSITORY:");
            requireNoneState(values.get("EXTERNAL_PUBLICATION_TRUST_ROOT"), internal);
            if (!internal) {
                validateExternalTrust(
                        base, requiredRoot(base, values.get("EXTERNAL_PUBLICATION_TRUST_ROOT")));
            }
            boolean signaturesNone = "NONE".equals(policy.get("requiredSignatures"));
            requireNoneState(values.get("ARTIFACT_SIGNATURE_TRUST_ROOT"), signaturesNone);
            if (!signaturesNone) {
                SealedBytes artifactTrust = requiredRoot(
                        base, values.get("ARTIFACT_SIGNATURE_TRUST_ROOT"));
                validateArtifactTrust(
                        base, artifactTrust,
                        Set.copyOf(List.of(policy.get("requiredSignatures").split(",", -1))));
            }
            validateOptionalSuppression(base, values.get("VULNERABILITY_SUPPRESSIONS"));
            validateProvenanceTrust(base, provenance, policy.get("trustedBuilder"));
            validateDatabaseTrust(base, database);
        }

        private static SealedBytes requiredRoot(Path base, String[] row) {
            if ("NONE".equals(row[1]) || "-".equals(row[2])) {
                throw new VerificationFailure(row[0] + " must reference retained trust material");
            }
            return sealRelative(base, row[1], row[2], row[0]);
        }

        private static void requireNoneState(String[] row, boolean mustBeNone) {
            boolean none = "NONE".equals(row[1]) && "-".equals(row[2]);
            if (mustBeNone != none) {
                throw new VerificationFailure(row[0] + " does not match release policy");
            }
        }

        private static void validateOptionalSuppression(Path base, String[] row) {
            boolean pathNone = "NONE".equals(row[1]);
            boolean digestNone = "-".equals(row[2]);
            if (pathNone != digestNone) {
                throw new VerificationFailure("VULNERABILITY_SUPPRESSIONS has an incomplete NONE state");
            }
            if (!pathNone) {
                sealRelative(base, row[1], row[2], row[0]);
            }
        }

        private static void validateProvenanceTrust(
                Path base, SealedBytes manifest, String trustedBuilder) {
            List<String[]> rows = parseTable(
                    manifest,
                    "provenance trust manifest",
                    "scheme\tkeyId\tmaterialPath\tmaterialSha256",
                    4,
                    32);
            String previous = null;
            for (String[] row : rows) {
                String key = row[0] + "\t" + row[1];
                if (!"SIGSTORE".equals(row[0]) || !trustedBuilder.equals(row[1])) {
                    throw new VerificationFailure(
                            "provenance trust manifest must contain only the trusted SIGSTORE builder");
                }
                if (previous != null && previous.compareTo(key) >= 0) {
                    throw new VerificationFailure("provenance trust manifest must be sorted and unique");
                }
                sealRelative(base, row[2], row[3], "provenance trust material");
                previous = key;
            }
        }

        private static void validateArtifactTrust(
                Path base, SealedBytes manifest, Set<String> requiredSchemes) {
            List<String[]> rows = parseTable(
                    manifest,
                    "artifact signature trust manifest",
                    "scheme\tkeyId\tmaterialPath\tmaterialSha256",
                    4,
                    4);
            Set<String> schemes = new java.util.HashSet<>();
            String previous = null;
            for (String[] row : rows) {
                String identity = row[0] + "\t" + row[1];
                boolean keyValid = "PGP".equals(row[0])
                        ? row[1].matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
                        : "SIGSTORE".equals(row[0])
                        && row[1].split("\\|", -1).length == 2
                        && !row[1].startsWith("|") && !row[1].endsWith("|");
                if (!requiredSchemes.contains(row[0]) || !schemes.add(row[0]) || !keyValid
                        || previous != null && previous.compareTo(identity) >= 0) {
                    throw new VerificationFailure(
                            "artifact signature trust must exactly match required schemes");
                }
                sealRelative(base, row[2], row[3], "artifact signature trust material");
                previous = identity;
            }
            if (!schemes.equals(requiredSchemes)) {
                throw new VerificationFailure(
                        "artifact signature trust must exactly match required schemes");
            }
        }

        private static void validateExternalTrust(Path base, SealedBytes manifest) {
            List<String[]> rows = parseTable(
                    manifest,
                    "external publication trust manifest",
                    "scheme\tkeyId\tmaterialPath\tmaterialSha256",
                    4,
                    32);
            String previous = null;
            for (String[] row : rows) {
                String identity = row[0] + "\t" + row[1];
                if (!"SIGSTORE".equals(row[0])
                        || row[1].split("\\|", -1).length != 2
                        || row[1].startsWith("|") || row[1].endsWith("|")
                        || previous != null && previous.compareTo(identity) >= 0) {
                    throw new VerificationFailure(
                            "external publication trust must contain sorted SIGSTORE identities");
                }
                sealRelative(base, row[2], row[3], "external publication trust material");
                previous = identity;
            }
        }

        private static void validateDatabaseTrust(Path base, SealedBytes manifest) {
            List<String[]> rows = parseTable(
                    manifest,
                    "vulnerability database trust manifest",
                    "sourceId\tscheme\tkeyId\tmaterialPath\tmaterialSha256\tminimumSnapshotSequence",
                    6,
                    256);
            Set<String> sources = new java.util.HashSet<>();
            Set<String> keys = new java.util.HashSet<>();
            for (String[] row : rows) {
                if (!row[0].matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                        || !Set.of("PGP", "SIGSTORE").contains(row[1])
                        || !sources.add(row[0]) || !keys.add(row[2])) {
                    throw new VerificationFailure("vulnerability database trust identity is invalid");
                }
                try {
                    if (Long.parseLong(row[5]) < 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException failure) {
                    throw new VerificationFailure("minimumSnapshotSequence must be non-negative");
                }
                sealRelative(base, row[3], row[4], "vulnerability database trust material");
            }
        }
    }

    /** release-owner 引用文件的结构化 schema 入口。 */
    private static final class AuthoritySchemas {
        private static final Set<String> TOOLCHAIN_ROLES = Set.of(
                "RUNNER_IMAGE", "JDK_RUNTIME", "MAVEN_DISTRIBUTION", "MAVEN_WRAPPER",
                "MAVEN_PLUGIN", "BUILD_EXTENSION", "BUILD_DEPENDENCY");

        private AuthoritySchemas() {
        }

        private static void validate(
                Path base,
                Map<String, String> policy,
                Map<String, SealedBytes> authorities) {
            validatePublishManifest(
                    authorities.get("publishArtifactManifest"), policy.get("finalVersion"));
            validatePerformancePolicy(base, authorities.get("runtimePerformancePolicy"));
            validateCompatibilityMatrix(
                    authorities.get("compatibilityMatrix"), policy.get("finalVersion"));
            validateReleaseExecutions(base, authorities.get("releaseExecutionPolicy"));
            validateLicensePolicy(authorities.get("licensePolicy"));
            validateBuildToolchain(base, authorities.get("buildToolchainManifest"));
        }

        private static void validatePublishManifest(SealedBytes manifest, String finalVersion) {
            List<String[]> rows = parseTable(
                    manifest,
                    "publish artifact manifest",
                    "ordinal\tsubjectOrdinal\tsubjectCoordinate\trepositoryPath\trole\tsidecarKind",
                    6,
                    4096);
            Map<String, String> missingPrimaries = expectedPrimaries(finalVersion);
            Map<Integer, String[]> primaries = new LinkedHashMap<>();
            Map<Integer, Set<String>> checksums = new LinkedHashMap<>();
            Set<String> paths = new java.util.HashSet<>();
            for (int index = 0; index < rows.size(); index++) {
                String[] row = rows.get(index);
                if (!Integer.toString(index + 1).equals(row[0])) {
                    throw new VerificationFailure("publish artifact ordinal must be continuous from 1");
                }
                requireRelativePosix(row[3], "publish artifact repositoryPath");
                if (!paths.add(row[3])) {
                    throw new VerificationFailure("publish artifact repositoryPath must be unique");
                }
                if (!Set.of(
                        "POM", "BINARY", "SOURCES", "JAVADOC", "CHECKSUM", "SIGNATURE")
                        .contains(row[4])) {
                    throw new VerificationFailure("publish artifact role is unsupported");
                }
                if (Set.of("POM", "BINARY", "SOURCES", "JAVADOC").contains(row[4])) {
                    if (!"-".equals(row[1]) || !"-".equals(row[5])) {
                        throw new VerificationFailure("publish primary must use dash sidecar fields");
                    }
                    String expectedRole = missingPrimaries.remove(row[2]);
                    if (!row[4].equals(expectedRole)) {
                        throw new VerificationFailure("publish primary closure contains an unexpected entry");
                    }
                    String expectedPath = repositoryPath(row[2]);
                    if (!expectedPath.equals(row[3])) {
                        throw new VerificationFailure("publish primary repositoryPath does not match coordinate");
                    }
                    primaries.put(index + 1, row);
                    checksums.put(index + 1, new java.util.HashSet<>());
                } else if ("CHECKSUM".equals(row[4])) {
                    int subject = canonicalPositive(row[1], "publish checksum subjectOrdinal");
                    String[] primary = primaries.get(subject);
                    if (primary == null || subject >= index + 1 || !primary[2].equals(row[2])) {
                        throw new VerificationFailure("publish checksum must reference an earlier primary");
                    }
                    String suffix = switch (row[5]) {
                        case "SHA256" -> ".sha256";
                        case "SHA512" -> ".sha512";
                        default -> throw new VerificationFailure("publish checksum kind is unsupported");
                    };
                    if (!row[3].equals(primary[3] + suffix) || !checksums.get(subject).add(row[5])) {
                        throw new VerificationFailure("publish checksum sidecar is duplicate or misaddressed");
                    }
                } else {
                    throw new VerificationFailure("signature sidecars belong to the HRD-08 signed closure");
                }
            }
            if (!missingPrimaries.isEmpty()) {
                throw new VerificationFailure("publish primary closure is incomplete");
            }
            if (checksums.values().stream().anyMatch(kinds -> !kinds.equals(Set.of("SHA256", "SHA512")))) {
                throw new VerificationFailure("every publish primary requires SHA256 and SHA512");
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

        private static String repositoryPath(String coordinate) {
            String[] parts = coordinate.split(":", -1);
            if ((parts.length != 4 && parts.length != 5)
                    || java.util.Arrays.stream(parts).anyMatch(String::isEmpty)) {
                throw new VerificationFailure("publish coordinate is not canonical");
            }
            String classifier = parts.length == 5 ? "-" + parts[3] : "";
            String version = parts[parts.length - 1];
            return parts[0].replace('.', '/') + "/" + parts[1] + "/" + version + "/"
                    + parts[1] + "-" + version + classifier + "." + parts[2];
        }

        private static int canonicalPositive(String value, String description) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed <= 0 || !Integer.toString(parsed).equals(value)) {
                    throw new NumberFormatException();
                }
                return parsed;
            } catch (NumberFormatException failure) {
                throw new VerificationFailure(description + " must be a canonical positive integer");
            }
        }

        private static void validatePerformancePolicy(Path base, SealedBytes manifest) {
            List<String[]> rows = parseTable(
                    manifest,
                    "runtime performance policy",
                    "workloadId\tscenario\tthreads\tfixtureManifestPath\tfixtureManifestSha256\t"
                            + "semanticOraclePath\tsemanticOracleSha256\tbenchmarkEnvironmentPath\t"
                            + "benchmarkEnvironmentSha256\trunnerProfile\tevidenceCommandId\t"
                            + "commandSpecSha256\tabsoluteP99NanosMax\tbaselineArtifactSetSha256\t"
                            + "maxRegressionPercent\tallocationBytesPerOpMax",
                    16,
                    64);
            if (rows.size() != 21) {
                throw new VerificationFailure("performance policy must contain exactly 21 workloads");
            }
            Set<String> scenarios = Set.of(
                    "NESTED_POJO", "LIST", "MAP", "SET_SCALAR", "SET_ENTITY",
                    "SET_AMBIGUOUS", "OBSERVED_COMPARE");
            Set<String> combinations = new java.util.HashSet<>();
            Set<String> workloadIds = new java.util.HashSet<>();
            Map<String, String> sealedPaths = new java.util.HashMap<>();
            for (String[] row : rows) {
                String combination = row[1] + "\t" + row[2];
                if (!workloadIds.add(row[0])
                        || !scenarios.contains(row[1])
                        || !Set.of("1", "8", "32").contains(row[2])
                        || !combinations.add(combination)) {
                    throw new VerificationFailure("performance workload identity is duplicate or invalid");
                }
                sealOnce(base, row[3], row[4], "performance fixture manifest", sealedPaths);
                sealOnce(base, row[5], row[6], "performance semantic oracle", sealedPaths);
                sealOnce(base, row[7], row[8], "performance environment", sealedPaths);
                if (!Set.of("JMH", "CAPACITY_HARNESS").contains(row[9])
                        || !row[10].matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
                        || !SHA256.matcher(row[11]).matches()
                        || !SHA256.matcher(row[13]).matches()) {
                    throw new VerificationFailure("performance command authority is invalid");
                }
                try {
                    if (new BigDecimal(row[12]).compareTo(BigDecimal.ZERO) <= 0
                            || new BigDecimal(row[14]).compareTo(BigDecimal.ZERO) < 0
                            || new BigDecimal(row[15]).compareTo(BigDecimal.ZERO) <= 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException failure) {
                    throw new VerificationFailure("performance numeric limit is invalid");
                }
            }
            for (String scenario : scenarios) {
                for (String threads : List.of("1", "8", "32")) {
                    if (!combinations.contains(scenario + "\t" + threads)) {
                        throw new VerificationFailure("performance policy 7 x 3 closure is incomplete");
                    }
                }
            }
        }

        private static void sealOnce(
                Path base,
                String relative,
                String digest,
                String description,
                Map<String, String> sealedPaths) {
            String previous = sealedPaths.putIfAbsent(relative, digest);
            if (previous == null) {
                sealRelative(base, relative, digest, description);
            } else if (!previous.equals(digest)) {
                throw new VerificationFailure(description + " has conflicting hashes");
            }
        }

        private static void validateCompatibilityMatrix(SealedBytes manifest, String finalVersion) {
            List<String[]> rows = parseTable(
                    manifest,
                    "compatibility matrix",
                    "edgeKind\tconsumerGa\tconsumerVersion\tdependencyGa\tdependencyVersion\t"
                            + "expected\tenforcement\tevidenceCommandId",
                    8,
                    4096);
            Set<String> keys = new java.util.HashSet<>();
            for (String[] row : rows) {
                String key = String.join("\t", row[0], row[1], row[2], row[3], row[4]);
                if (!keys.add(key)) {
                    throw new VerificationFailure("compatibility matrix keys must be unique");
                }
                if (!Set.of("PARENT", "DEPENDENCY").contains(row[0])
                        || !row[1].matches("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+")
                        || !row[3].matches("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+")
                        || !Set.of("3.0.0", finalVersion).contains(row[2])
                        || !Set.of("3.0.0", finalVersion).contains(row[4])
                        || !Set.of("SUPPORTED", "REJECTED").contains(row[5])
                        || !Set.of(
                                "ARTIFACT_TEST", "DEPENDENCY_CONVERGENCE",
                                "STARTUP_FAIL_FAST", "MAVEN_MODEL_VALIDATION").contains(row[6])) {
                    throw new VerificationFailure("compatibility matrix row is invalid");
                }
            }
        }

        private static void validateReleaseExecutions(Path base, SealedBytes manifest) {
            List<String[]> rows = parseTable(
                    manifest,
                    "release execution policy",
                    "executionId\trole\tcommandId\tcommandSpecSha256\tconfigPath\tconfigSha256\t"
                            + "rulesPath\trulesSha256\tscopeRule",
                    9,
                    4096);
            Map<String, String> scopes = Map.of(
                    "PERFORMANCE", "PERFORMANCE_POLICY",
                    "COMPATIBILITY", "COMPATIBILITY_MATRIX",
                    "VULNERABILITY_SCAN", "RUNTIME_DEPENDENCY_CLOSURE",
                    "SECRET_SCAN_FIRST", "SECRET_BEARING_EVIDENCE",
                    "SECRET_SCAN_SELF", "SECRET_REPORT_ONLY",
                    "SBOM_GENERATE", "PUBLISHABLE_RUNTIME_CLOSURE",
                    "SENSITIVE_LOG_SCAN", "SENSITIVE_LOG_EVIDENCE");
            Set<String> executionIds = new java.util.HashSet<>();
            Set<String> presentRoles = new java.util.HashSet<>();
            Map<String, String> sealedPaths = new java.util.HashMap<>();
            for (String[] row : rows) {
                if (!executionIds.add(row[0])
                        || !row[0].matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
                        || !row[2].matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
                        || !SHA256.matcher(row[3]).matches()) {
                    throw new VerificationFailure("release execution identity is duplicate or invalid");
                }
                String expectedScope = scopes.get(row[1]);
                if (expectedScope == null || !expectedScope.equals(row[8])) {
                    throw new VerificationFailure("release execution role and scopeRule do not match");
                }
                presentRoles.add(row[1]);
                boolean noFiles = Set.of("PERFORMANCE", "COMPATIBILITY").contains(row[1]);
                if (noFiles) {
                    if (!"NONE".equals(row[4]) || !"-".equals(row[5])
                            || !"NONE".equals(row[6]) || !"-".equals(row[7])) {
                        throw new VerificationFailure(
                                "performance/compatibility execution must not invent config or rules");
                    }
                } else {
                    sealOnce(base, row[4], row[5], "release execution config", sealedPaths);
                    sealOnce(base, row[6], row[7], "release execution rules", sealedPaths);
                }
            }
            if (!presentRoles.equals(scopes.keySet())) {
                throw new VerificationFailure("release execution role closure is incomplete");
            }
        }

        private static void validateLicensePolicy(SealedBytes manifest) {
            List<String[]> rows = parseTable(
                    manifest,
                    "license policy",
                    "spdxExpression\tdecision\tnoticeRequired\tlicenseTextSha256",
                    4,
                    4096);
            String previous = null;
            for (String[] row : rows) {
                String key = row[0] + "\t" + row[3];
                if (previous != null && previous.compareTo(key) >= 0) {
                    throw new VerificationFailure("license policy keys must be sorted and unique");
                }
                if (!Set.of("ALLOW", "DENY").contains(row[1])
                        || !Set.of("true", "false").contains(row[2])) {
                    throw new VerificationFailure("license policy decision is invalid");
                }
                if ("ALLOW".equals(row[1]) != SHA256.matcher(row[3]).matches()) {
                    throw new VerificationFailure("license policy hash does not match its decision");
                }
                if ("DENY".equals(row[1]) && !"-".equals(row[3])) {
                    throw new VerificationFailure("license policy DENY hash must be dash");
                }
                if (!row[0].matches("[A-Za-z0-9][A-Za-z0-9.+()-]{0,127}")) {
                    throw new VerificationFailure("license policy SPDX expression is not canonical");
                }
                previous = key;
            }
        }

        private static void validateBuildToolchain(Path base, SealedBytes manifest) {
            List<String[]> rows = parseTable(
                    manifest,
                    "build toolchain manifest",
                    "ordinal\trole\tcoordinate\tevidencePath\tsha256",
                    5,
                    8192);
            String previous = null;
            Set<String> identities = new java.util.HashSet<>();
            for (int index = 0; index < rows.size(); index++) {
                String[] row = rows.get(index);
                String key = row[1] + "\t" + row[2];
                if (!Integer.toString(index + 1).equals(row[0])
                        || !TOOLCHAIN_ROLES.contains(row[1])) {
                    throw new VerificationFailure("build toolchain ordinal or role is invalid");
                }
                if ((previous != null && previous.compareTo(key) >= 0) || !identities.add(key)) {
                    throw new VerificationFailure("build toolchain rows must be sorted and unique");
                }
                sealRelative(base, row[3], row[4], "build toolchain component");
                previous = key;
            }
            for (String role : List.of(
                    "RUNNER_IMAGE", "JDK_RUNTIME", "MAVEN_DISTRIBUTION", "MAVEN_WRAPPER")) {
                if (rows.stream().noneMatch(row -> role.equals(row[1]))) {
                    throw new VerificationFailure("build toolchain is missing required role " + role);
                }
            }
        }
    }

    /** Publishable artifact signature 的 policy-presence 闭集入口。 */
    private static final class ArtifactSignatures {
        /** Signature verification result 的固定 evidence 路径。 */
        private static final String RESULTS_PATH =
                "supply-chain/signatures/artifact-signature-results.tsv";
        /** Artifact signature result 的唯一十列 schema。 */
        private static final String RESULTS_HEADER =
                "subjectOrdinal\tscheme\tsubjectSha256\tsidecarPath\tsidecarSha256\t"
                        + "signerKeyId\tdigestAlgorithm\tsignatureAlgorithm\tintegratedTime\tstatus";

        private ArtifactSignatures() {
        }

        private static void verify(Path evidence, Policy policy) {
            Set<String> requiredSchemes = requiredSchemes(policy.values().get("requiredSignatures"));
            boolean required = !requiredSchemes.isEmpty();
            boolean present = Files.exists(evidence.resolve(RESULTS_PATH), LinkOption.NOFOLLOW_LINKS);
            if (required && !present) {
                throw new VerificationFailure("artifact signature results are required");
            }
            if (!required && present) {
                throw new VerificationFailure("artifact signature results are forbidden by policy");
            }
            boolean publishablePresent = Files.exists(
                    evidence.resolve("metadata/publishable-artifacts.tsv"),
                    LinkOption.NOFOLLOW_LINKS);
            if (!required && !publishablePresent) {
                return;
            }
            Map<String, ArtifactTrust> trusts = required
                    ? loadArtifactTrust(policy, requiredSchemes) : Map.of();
            SignatureClosure closure = loadPublishableClosure(evidence, requiredSchemes);
            if (required) {
                SealedBytes results = readRelative(
                        evidence, RESULTS_PATH, "artifact signature results", MAX_AUTHORITY_BYTES);
                List<String[]> rows = parseTable(
                        results,
                        "artifact signature results",
                        RESULTS_HEADER,
                        10,
                        8192);
                if (rows.isEmpty()) {
                    throw new VerificationFailure("artifact signature result closure is empty");
                }
                validateResults(evidence, rows, closure, requiredSchemes, trusts);
            }
        }

        private static Set<String> requiredSchemes(String value) {
            if ("NONE".equals(value)) {
                return Set.of();
            }
            return Set.copyOf(List.of(value.split(",", -1)));
        }

        private static Set<String> postScopeSidecarPaths(Path evidence, Policy policy) {
            if (!Files.exists(
                    evidence.resolve("metadata/publishable-artifacts.tsv"),
                    LinkOption.NOFOLLOW_LINKS)) {
                return Set.of();
            }
            SignatureClosure closure = loadPublishableClosure(
                    evidence, requiredSchemes(policy.values().get("requiredSignatures")));
            Set<String> result = new java.util.HashSet<>();
            for (String[] row : closure.signatures().values()) {
                result.add("artifacts/publishable-repository/" + row[3]);
            }
            return Set.copyOf(result);
        }

        private static SignatureClosure loadPublishableClosure(
                Path evidence, Set<String> requiredSchemes) {
            SealedBytes manifest = readRelative(
                    evidence, "metadata/publishable-artifacts.tsv",
                    "publishable artifact set", MAX_AUTHORITY_BYTES);
            List<String[]> rows = parseTable(
                    manifest,
                    "publishable artifact set",
                    "ordinal\tsubjectOrdinal\tsubjectCoordinate\trepositoryPath\trole\t"
                            + "sidecarKind\tsha256",
                    7,
                    8192);
            Map<Integer, String[]> primaries = new LinkedHashMap<>();
            Map<String, String[]> signatures = new LinkedHashMap<>();
            for (int index = 0; index < rows.size(); index++) {
                String[] row = rows.get(index);
                if (!Integer.toString(index + 1).equals(row[0])
                        || !SHA256.matcher(row[6]).matches()) {
                    throw new VerificationFailure("publishable artifact row identity is invalid");
                }
                if (Set.of("POM", "BINARY", "SOURCES", "JAVADOC").contains(row[4])) {
                    primaries.put(index + 1, row);
                } else if ("SIGNATURE".equals(row[4])) {
                    int subjectOrdinal = positiveInteger(row[1], "signature subjectOrdinal");
                    String[] primary = primaries.get(subjectOrdinal);
                    String key = subjectOrdinal + "\t" + row[5];
                    String suffix = switch (row[5]) {
                        case "PGP" -> ".asc";
                        case "SIGSTORE" -> ".sigstore.json";
                        default -> throw new VerificationFailure(
                                "publishable artifact signature scheme is invalid");
                    };
                    if (primary == null || !requiredSchemes.contains(row[5])
                            || !primary[2].equals(row[2])
                            || !row[3].equals(primary[3] + suffix)
                            || signatures.putIfAbsent(key, row) != null) {
                        throw new VerificationFailure(
                                "publishable artifact signature sidecar binding is invalid");
                    }
                } else if (!"CHECKSUM".equals(row[4])) {
                    throw new VerificationFailure("publishable artifact role is invalid");
                }
            }
            Set<String> expected = new java.util.HashSet<>();
            for (Integer ordinal : primaries.keySet()) {
                for (String scheme : requiredSchemes) {
                    expected.add(ordinal + "\t" + scheme);
                }
            }
            if (!signatures.keySet().equals(expected)) {
                if (requiredSchemes.isEmpty() && !signatures.isEmpty()) {
                    throw new VerificationFailure("artifact signatures are forbidden by policy");
                }
                if (!signatures.isEmpty()) {
                    throw new VerificationFailure("publishable artifact signature closure is incomplete");
                }
            }
            return new SignatureClosure(Map.copyOf(primaries), Map.copyOf(signatures), Set.copyOf(expected));
        }

        private static Map<String, ArtifactTrust> loadArtifactTrust(
                Policy policy, Set<String> requiredSchemes) {
            SealedBytes authorities = policy.authorities().get("productionAuthoritiesManifest");
            List<String> authorityRows = decodeLines(
                    authorities.bytes(), "production authorities manifest");
            String[] authority = authorityRows.get(2).split("\t", -1);
            Path base = authorities.path().getParent();
            SealedBytes manifest = sealRelative(
                    base, authority[1], authority[2], "artifact signature trust manifest");
            List<String[]> rows = parseTable(
                    manifest,
                    "artifact signature trust manifest",
                    "scheme\tkeyId\tmaterialPath\tmaterialSha256",
                    4,
                    4);
            Map<String, ArtifactTrust> result = new LinkedHashMap<>();
            String previous = null;
            for (String[] row : rows) {
                String identity = row[0] + "\t" + row[1];
                if (!requiredSchemes.contains(row[0]) || row[1].isEmpty()
                        || previous != null && previous.compareTo(identity) >= 0
                        || result.containsKey(row[0])) {
                    throw new VerificationFailure(
                            "artifact signature trust must exactly match required schemes");
                }
                SealedBytes material = sealRelative(
                        base, row[2], row[3], "artifact signature trust material");
                SigstoreAttestations.Trust sigstore = "SIGSTORE".equals(row[0])
                        ? SigstoreAttestations.loadTrustMaterial(
                                base, row, "artifact Sigstore trust material")
                        : null;
                result.put(row[0], new ArtifactTrust(row[1], material, sigstore));
                previous = identity;
            }
            if (!result.keySet().equals(requiredSchemes)) {
                throw new VerificationFailure(
                        "artifact signature trust must exactly match required schemes");
            }
            return Map.copyOf(result);
        }

        private static void validateResults(
                Path evidence,
                List<String[]> rows,
                SignatureClosure closure,
                Set<String> requiredSchemes,
                Map<String, ArtifactTrust> trusts) {
            Set<String> seen = new java.util.HashSet<>();
            for (String[] row : rows) {
                int subjectOrdinal = positiveInteger(row[0], "artifact signature subjectOrdinal");
                String key = subjectOrdinal + "\t" + row[1];
                String[] primary = closure.primaries().get(subjectOrdinal);
                String[] sidecar = closure.signatures().get(key);
                if (primary == null || sidecar == null || !requiredSchemes.contains(row[1])
                        || !seen.add(key)) {
                    throw new VerificationFailure(
                            "artifact signature result does not match a publishable sidecar");
                }
                String expectedPath = "artifacts/publishable-repository/" + sidecar[3];
                if (!primary[6].equals(row[2]) || !expectedPath.equals(row[3])
                        || !sidecar[6].equals(row[4]) || !"PASS".equals(row[9])) {
                    throw new VerificationFailure("artifact signature result binding is invalid");
                }
                ArtifactTrust trust = trusts.get(row[1]);
                if (trust == null || !trust.keyId().equals(row[5])) {
                    throw new VerificationFailure("artifact signature signer differs from policy trust");
                }
                SealedBytes bytes = readRelative(
                        evidence, row[3], "artifact signature sidecar", MAX_AUTHORITY_BYTES);
                if (!bytes.sha256().equals(row[4])) {
                    throw new VerificationFailure("artifact signature sidecar SHA differs from bytes");
                }
                if ("SIGSTORE".equals(row[1])) {
                    if (!"SHA2_256".equals(row[6])
                            || !Set.of("ECDSA_P256_SHA256", "RSA_SHA256", "ED25519").contains(row[7])) {
                        throw new VerificationFailure("Sigstore artifact signature algorithms are invalid");
                    }
                    SigstoreAttestations.canonicalLong(row[8], "Sigstore artifact integratedTime");
                    String subjectPath = "artifacts/publishable-repository/" + primary[3];
                    SealedBytes subject = readRelative(
                            evidence, subjectPath, "artifact signature primary", 256 * 1024 * 1024);
                    if (!subject.sha256().equals(primary[6])) {
                        throw new VerificationFailure("artifact signature primary SHA differs from bytes");
                    }
                    SigstoreAttestations.verifyMessageSignature(
                            bytes, subject, row[5], row[8], row[7], trust.sigstore());
                } else if (!"-".equals(row[8])) {
                    throw new VerificationFailure("PGP artifact integratedTime must be dash");
                } else {
                    throw new VerificationFailure(
                            "PGP artifact verification requires a sealed external verifier authority");
                }
            }
            if (!seen.equals(closure.expected())) {
                throw new VerificationFailure("artifact signature result closure is incomplete");
            }
        }

        private static int positiveInteger(String value, String description) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed <= 0 || !Integer.toString(parsed).equals(value)) {
                    throw new NumberFormatException();
                }
                return parsed;
            } catch (NumberFormatException failure) {
                throw new VerificationFailure(description + " must be a canonical positive integer");
            }
        }

        /** @param primaries primary rows; @param signatures signature rows; @param expected join keys. */
        private record SignatureClosure(
                Map<Integer, String[]> primaries,
                Map<String, String[]> signatures,
                Set<String> expected) {
        }

        /** @param keyId signer identity; @param material raw trust bytes; @param sigstore Sigstore trust. */
        private record ArtifactTrust(
                String keyId,
                SealedBytes material,
                SigstoreAttestations.Trust sigstore) {
        }
    }

    /** 三层 Sigstore v0.3 DSSE、X.509 与 transparency proof 的离线验证。 */
    private static final class SigstoreAttestations {
        /** Fulcio legacy/current OIDC issuer extensions; HTTPS issuer 不得回退到 X.500。 */
        private static final List<String> FULCIO_ISSUER_OIDS = List.of(
                "1.3.6.1.4.1.57264.1.1", "1.3.6.1.4.1.57264.1.8");
        /** 三份 attestation 的路径和 predicate type 均不可替换。 */
        private static final Map<String, String> ATTESTATIONS = Map.of(
                "supply-chain/provenance/artifact-provenance.sigstore.json",
                "https://taskflowinsight.dev/attestation/artifact-provenance/v1",
                "security/secret-scan/process-attestation.sigstore.json",
                "https://taskflowinsight.dev/attestation/secret-scan/v1",
                "supply-chain/provenance/evidence-attestation.sigstore.json",
                "https://taskflowinsight.dev/attestation/release-evidence/v1");
        /** Artifact predicate 只绑定构建前 authority 与候选构建事实。 */
        private static final Set<String> ARTIFACT_KEYS = Set.of(
                "productionPolicySha256", "repository", "protectedRef", "candidateRevision",
                "finalVersion", "releaseTarget", "publishableArtifactSetSha256", "builderIssuer",
                "builderId", "workflowRepository", "workflowPath", "workflowRevision",
                "expectedCommandsSha256", "releaseExecutionPolicySha256",
                "buildToolchainManifestSha256", "actualBuildToolchainSha256",
                "vulnerabilityScannerBundleSha256", "secretScannerBundleSha256",
                "sbomGeneratorBundleSha256", "buildStartedAtUtc", "buildFinishedAtUtc");
        /** Secret predicate 只绑定两遍扫描和已冻结 execution ledgers。 */
        private static final Set<String> SECRET_KEYS = Set.of(
                "productionPolicySha256", "secretScannerBundleSha256", "firstScopeSha256",
                "firstReportSha256", "firstActualExit", "firstFindings", "secondScopeSha256",
                "secondReportSha256", "secondActualExit", "secondFindings",
                "releaseExecutionsSha256", "toolExecutionsSha256", "executionStartedAtUtc",
                "executionFinishedAtUtc");
        /** Final predicate 引用前两层 SHA，绝不反向进入自身 subject hash。 */
        private static final Set<String> EVIDENCE_KEYS = Set.of(
                "productionPolicySha256", "evidenceSubjectManifestSha256",
                "publishableArtifactSetSha256", "actualCommandLedgersSha256",
                "releaseExecutionsSha256", "toolExecutionsSha256", "actualBuildToolchainSha256",
                "artifactProvenanceSha256", "secretProcessAttestationSha256", "builderIssuer",
                "builderId", "workflowRepository", "workflowPath", "workflowRevision", "attestedAtUtc");

        private SigstoreAttestations() {
        }

        private static void verify(Path evidence, Policy policy, boolean required) {
            long present = ATTESTATIONS.keySet().stream()
                    .filter(path -> Files.exists(evidence.resolve(path), LinkOption.NOFOLLOW_LINKS))
                    .count();
            if (present == 0 && !required) {
                return;
            }
            if (present != ATTESTATIONS.size()) {
                throw new VerificationFailure("three-layer Sigstore attestation closure is incomplete");
            }
            Trust trust = loadTrust(policy);
            Map<String, Attestation> verified = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : ATTESTATIONS.entrySet()) {
                SealedBytes bundle = readRelative(
                        evidence, entry.getKey(), "Sigstore attestation bundle", MAX_AUTHORITY_BYTES);
                verified.put(entry.getValue(), verifyBundle(bundle, entry.getValue(), policy, trust));
            }
            validateCrossLayerBindings(evidence, policy, verified);
        }

        private static Trust loadTrust(Policy policy) {
            SealedBytes authorities = policy.authorities().get("productionAuthoritiesManifest");
            String[] provenanceRow = decodeLines(
                    authorities.bytes(), "production authorities manifest").getFirst().split("\t", -1);
            Path base = authorities.path().getParent();
            SealedBytes provenance = sealRelative(
                    base, provenanceRow[1], provenanceRow[2], "provenance trust manifest");
            List<String[]> rows = parseTable(
                    provenance, "provenance trust manifest",
                    "scheme\tkeyId\tmaterialPath\tmaterialSha256", 4, 32);
            String trustedBuilder = policy.values().get("trustedBuilder");
            String[] row = rows.stream()
                    .filter(candidate -> "SIGSTORE".equals(candidate[0]))
                    .filter(candidate -> trustedBuilder.equals(candidate[1]))
                    .findFirst()
                    .orElseThrow(() -> new VerificationFailure(
                            "trusted builder has no Sigstore verification material"));
            return loadTrustMaterial(base, row, "Sigstore verification material");
        }

        private static Trust loadTrustMaterial(
                Path base, String[] row, String description) {
            SealedBytes material = sealRelative(
                    base, row[2], row[3], description);
            String pem = decodeUtf8(material.bytes(), description);
            List<X509Certificate> roots = new ArrayList<>();
            for (byte[] der : pemBlocks(pem, "CERTIFICATE")) {
                try {
                    roots.add((X509Certificate) CertificateFactory.getInstance("X.509")
                            .generateCertificate(new ByteArrayInputStream(der)));
                } catch (GeneralSecurityException failure) {
                    throw new VerificationFailure("Sigstore trust certificate is invalid");
                }
            }
            List<byte[]> publicKeys = pemBlocks(pem, "PUBLIC KEY");
            if (roots.isEmpty() || publicKeys.size() != 1) {
                throw new VerificationFailure("Sigstore trust requires certificate roots and one Rekor key");
            }
            return new Trust(List.copyOf(roots), decodePublicKey(publicKeys.getFirst()));
        }

        private static List<byte[]> pemBlocks(String pem, String label) {
            String begin = "-----BEGIN " + label + "-----";
            String end = "-----END " + label + "-----";
            List<byte[]> result = new ArrayList<>();
            int offset = 0;
            while (true) {
                int start = pem.indexOf(begin, offset);
                if (start < 0) {
                    break;
                }
                int finish = pem.indexOf(end, start + begin.length());
                if (finish < 0) {
                    throw new VerificationFailure("Sigstore PEM block is incomplete");
                }
                String encoded = pem.substring(start + begin.length(), finish).replaceAll("\\s", "");
                try {
                    result.add(Base64.getDecoder().decode(encoded));
                } catch (IllegalArgumentException failure) {
                    throw new VerificationFailure("Sigstore PEM block is invalid base64");
                }
                offset = finish + end.length();
            }
            return List.copyOf(result);
        }

        private static PublicKey decodePublicKey(byte[] der) {
            for (String algorithm : List.of("EC", "RSA", "Ed25519")) {
                try {
                    return KeyFactory.getInstance(algorithm).generatePublic(new X509EncodedKeySpec(der));
                } catch (GeneralSecurityException ignored) {
                    // Try the next production-approved public-key algorithm.
                }
            }
            throw new VerificationFailure("Sigstore Rekor public key algorithm is unsupported");
        }

        private static Attestation verifyBundle(
                SealedBytes bundle,
                String expectedPredicateType,
                Policy policy,
                Trust trust) {
            Map<String, Object> root = StrictJson.object(
                    StrictJson.parse(bundle, "Sigstore bundle"), "Sigstore bundle");
            requireKeys(root, Set.of("mediaType", "verificationMaterial", "dsseEnvelope"),
                    "Sigstore bundle");
            if (!"application/vnd.dev.sigstore.bundle.v0.3+json".equals(root.get("mediaType"))) {
                throw new VerificationFailure("Sigstore bundle mediaType is not v0.3");
            }
            Map<String, Object> material = StrictJson.object(
                    root.get("verificationMaterial"), "Sigstore verification material");
            requireKeys(material, Set.of("x509CertificateChain", "tlogEntries"),
                    "Sigstore verification material");
            List<X509Certificate> chain = parseCertificateChain(material.get("x509CertificateChain"));
            List<Object> entries = StrictJson.array(material.get("tlogEntries"), "Sigstore tlog entries");
            if (entries.size() != 1) {
                throw new VerificationFailure("Sigstore bundle requires exactly one tlog entry");
            }
            Map<String, Object> tlog = StrictJson.object(entries.getFirst(), "Sigstore tlog entry");
            long integratedTime = canonicalLong(
                    StrictJson.string(tlog.get("integratedTime"), "Sigstore integratedTime"),
                    "Sigstore integratedTime");
            X509Certificate leaf = verifyCertificateChain(
                    chain, trust.roots(), integratedTime, policy.values().get("trustedBuilder"));

            Map<String, Object> envelope = StrictJson.object(
                    root.get("dsseEnvelope"), "Sigstore DSSE envelope");
            requireKeys(envelope, Set.of("payload", "payloadType", "signatures"),
                    "Sigstore DSSE envelope");
            String payloadType = StrictJson.string(
                    envelope.get("payloadType"), "Sigstore DSSE payloadType");
            if (!"application/vnd.in-toto+json".equals(payloadType)) {
                throw new VerificationFailure("Sigstore DSSE payloadType is invalid");
            }
            byte[] payload = base64(
                    StrictJson.string(envelope.get("payload"), "Sigstore DSSE payload"),
                    "Sigstore DSSE payload");
            List<Object> signatures = StrictJson.array(
                    envelope.get("signatures"), "Sigstore DSSE signatures");
            if (signatures.size() != 1) {
                throw new VerificationFailure("Sigstore DSSE requires exactly one signature");
            }
            Map<String, Object> signature = StrictJson.object(
                    signatures.getFirst(), "Sigstore DSSE signature");
            requireKeys(signature, Set.of("keyid", "sig"), "Sigstore DSSE signature");
            if (!"".equals(signature.get("keyid"))) {
                throw new VerificationFailure("Sigstore DSSE keyid must be empty for X.509 profile");
            }
            byte[] signatureBytes = base64(
                    StrictJson.string(signature.get("sig"), "Sigstore DSSE signature"),
                    "Sigstore DSSE signature");
            if (!verifySignature(leaf.getPublicKey(), dssePae(payloadType, payload), signatureBytes)) {
                throw new VerificationFailure("Sigstore DSSE signature is invalid");
            }
            verifyTransparency(tlog, envelope, leaf, trust.rekorKey(), integratedTime);
            Statement statement = validateStatement(payload, expectedPredicateType, policy);
            return new Attestation(
                    bundle.sha256(), statement.subjects(), statement.predicate(), integratedTime);
        }

        private static void verifyMessageSignature(
                SealedBytes bundle,
                SealedBytes subject,
                String signerKeyId,
                String expectedIntegratedTime,
                String expectedSignatureAlgorithm,
                Trust trust) {
            Map<String, Object> root = StrictJson.object(
                    StrictJson.parse(bundle, "Sigstore artifact bundle"), "Sigstore artifact bundle");
            requireKeys(root, Set.of("mediaType", "verificationMaterial", "messageSignature"),
                    "Sigstore artifact bundle");
            if (!"application/vnd.dev.sigstore.bundle.v0.3+json".equals(root.get("mediaType"))) {
                throw new VerificationFailure("Sigstore artifact bundle mediaType is not v0.3");
            }
            Map<String, Object> material = StrictJson.object(
                    root.get("verificationMaterial"), "Sigstore artifact verification material");
            requireKeys(material, Set.of("x509CertificateChain", "tlogEntries"),
                    "Sigstore artifact verification material");
            List<Object> entries = StrictJson.array(
                    material.get("tlogEntries"), "Sigstore artifact tlog entries");
            if (entries.size() != 1) {
                throw new VerificationFailure("Sigstore artifact bundle requires one tlog entry");
            }
            Map<String, Object> tlog = StrictJson.object(
                    entries.getFirst(), "Sigstore artifact tlog entry");
            String integratedText = StrictJson.string(
                    tlog.get("integratedTime"), "Sigstore artifact integratedTime");
            long integratedTime = canonicalLong(integratedText, "Sigstore artifact integratedTime");
            if (!expectedIntegratedTime.equals(integratedText)) {
                throw new VerificationFailure("Sigstore artifact integratedTime differs from result");
            }
            X509Certificate leaf = verifyCertificateChain(
                    parseCertificateChain(material.get("x509CertificateChain")),
                    trust.roots(), integratedTime, signerKeyId);
            if (!expectedSignatureAlgorithm.equals(signatureAlgorithm(leaf.getPublicKey()))) {
                throw new VerificationFailure("Sigstore artifact signature algorithm differs from result");
            }

            Map<String, Object> message = StrictJson.object(
                    root.get("messageSignature"), "Sigstore messageSignature");
            requireKeys(message, Set.of("messageDigest", "signature"), "Sigstore messageSignature");
            Map<String, Object> digest = StrictJson.object(
                    message.get("messageDigest"), "Sigstore message digest");
            requireKeys(digest, Set.of("algorithm", "digest"), "Sigstore message digest");
            byte[] expectedDigest = sha256Bytes(subject.bytes());
            byte[] actualDigest = base64(
                    StrictJson.string(digest.get("digest"), "Sigstore message digest bytes"),
                    "Sigstore message digest bytes");
            if (!"SHA2_256".equals(digest.get("algorithm"))
                    || !MessageDigest.isEqual(expectedDigest, actualDigest)) {
                throw new VerificationFailure("Sigstore artifact message digest differs from primary bytes");
            }
            byte[] signature = base64(
                    StrictJson.string(message.get("signature"), "Sigstore artifact signature"),
                    "Sigstore artifact signature");
            if (!verifySignature(leaf.getPublicKey(), subject.bytes(), signature)) {
                throw new VerificationFailure("Sigstore artifact message signature is invalid");
            }
            verifyMessageTransparency(
                    tlog, leaf, trust.rekorKey(), integratedTime, subject.sha256(), signature);
        }

        /** 数据库签名不依赖自报 result row；时间和算法必须直接从已验证 bundle 派生。 */
        private static long verifyDatabaseMessageSignature(
                SealedBytes bundle,
                SealedBytes subject,
                String signerKeyId,
                Trust trust) {
            Map<String, Object> root = StrictJson.object(
                    StrictJson.parse(bundle, "Sigstore database bundle"),
                    "Sigstore database bundle");
            Map<String, Object> material = StrictJson.object(
                    root.get("verificationMaterial"),
                    "Sigstore database verification material");
            List<Object> entries = StrictJson.array(
                    material.get("tlogEntries"), "Sigstore database tlog entries");
            if (entries.size() != 1) {
                throw new VerificationFailure("Sigstore database bundle requires one tlog entry");
            }
            Map<String, Object> entry = StrictJson.object(
                    entries.getFirst(), "Sigstore database tlog entry");
            String integratedTime = StrictJson.string(
                    entry.get("integratedTime"), "Sigstore database integratedTime");
            List<X509Certificate> chain = parseCertificateChain(
                    material.get("x509CertificateChain"));
            X509Certificate leaf = verifyCertificateChain(
                    chain, trust.roots(), canonicalLong(
                            integratedTime, "Sigstore database integratedTime"), signerKeyId);
            verifyMessageSignature(
                    bundle, subject, signerKeyId, integratedTime,
                    signatureAlgorithm(leaf.getPublicKey()), trust);
            return canonicalLong(integratedTime, "Sigstore database integratedTime");
        }

        private static String signatureAlgorithm(PublicKey key) {
            if (key instanceof java.security.interfaces.ECPublicKey ec
                    && ec.getParams().getCurve().getField().getFieldSize() == 256) {
                return "ECDSA_P256_SHA256";
            }
            if (key instanceof java.security.interfaces.RSAPublicKey rsa
                    && rsa.getModulus().bitLength() >= 2048) {
                return "RSA_SHA256";
            }
            if (Set.of("Ed25519", "EdDSA").contains(key.getAlgorithm())) {
                return "ED25519";
            }
            throw new VerificationFailure("Sigstore artifact public-key profile is unsupported");
        }

        private static void verifyMessageTransparency(
                Map<String, Object> entry,
                X509Certificate leaf,
                PublicKey rekorKey,
                long integratedTime,
                String subjectSha,
                byte[] messageSignature) {
            requireKeys(entry, Set.of(
                    "canonicalizedBody", "inclusionPromise", "inclusionProof", "integratedTime",
                    "kindVersion", "logId", "logIndex"), "Sigstore artifact tlog entry");
            if (canonicalLong(StrictJson.string(
                    entry.get("integratedTime"), "Sigstore artifact integratedTime"),
                    "Sigstore artifact integratedTime") != integratedTime) {
                throw new VerificationFailure("Sigstore artifact integratedTime changed");
            }
            Map<String, Object> kindVersion = StrictJson.object(
                    entry.get("kindVersion"), "Sigstore artifact kindVersion");
            requireKeys(kindVersion, Set.of("kind", "version"), "Sigstore artifact kindVersion");
            if (!"hashedrekord".equals(kindVersion.get("kind"))
                    || !"0.0.1".equals(kindVersion.get("version"))) {
                throw new VerificationFailure("Sigstore artifact tlog profile is invalid");
            }
            Map<String, Object> logId = StrictJson.object(entry.get("logId"), "Sigstore artifact logId");
            requireKeys(logId, Set.of("keyId"), "Sigstore artifact logId");
            if (!MessageDigest.isEqual(
                    sha256Bytes(rekorKey.getEncoded()),
                    base64(StrictJson.string(logId.get("keyId"), "Sigstore artifact logId keyId"),
                            "Sigstore artifact logId keyId"))) {
                throw new VerificationFailure("Sigstore artifact logId differs from policy Rekor key");
            }
            byte[] bodyBytes = base64(
                    StrictJson.string(entry.get("canonicalizedBody"), "Sigstore artifact tlog body"),
                    "Sigstore artifact tlog body");
            Object bodyValue = new JsonParser(
                    decodeUtf8(bodyBytes, "Sigstore artifact tlog body"),
                    "Sigstore artifact tlog body").parse();
            if (!java.util.Arrays.equals(bodyBytes, StrictJson.canonicalBytes(bodyValue))) {
                throw new VerificationFailure("Sigstore artifact tlog body is not canonical JSON");
            }
            Map<String, Object> body = StrictJson.object(bodyValue, "Sigstore artifact tlog body");
            requireKeys(body, Set.of("apiVersion", "kind", "spec"), "Sigstore artifact tlog body");
            if (!"0.0.1".equals(body.get("apiVersion"))
                    || !"hashedrekord".equals(body.get("kind"))) {
                throw new VerificationFailure("Sigstore artifact tlog body profile is invalid");
            }
            validateHashedRekord(body.get("spec"), leaf, subjectSha, messageSignature);
            Map<String, Object> promise = StrictJson.object(
                    entry.get("inclusionPromise"), "Sigstore artifact inclusion promise");
            requireKeys(promise, Set.of("signedEntryTimestamp"),
                    "Sigstore artifact inclusion promise");
            if (!verifySignature(rekorKey, setVerificationPayload(entry, bodyBytes), base64(
                    StrictJson.string(promise.get("signedEntryTimestamp"), "Sigstore artifact SET"),
                    "Sigstore artifact SET"))) {
                throw new VerificationFailure("Sigstore artifact signed entry timestamp is invalid");
            }
            verifyInclusionProof(entry, bodyBytes, rekorKey);
        }

        private static void validateHashedRekord(
                Object value,
                X509Certificate leaf,
                String subjectSha,
                byte[] messageSignature) {
            Map<String, Object> spec = StrictJson.object(value, "Sigstore hashedrekord spec");
            requireKeys(spec, Set.of("data", "signature"), "Sigstore hashedrekord spec");
            Map<String, Object> data = StrictJson.object(spec.get("data"), "Sigstore hashedrekord data");
            requireKeys(data, Set.of("hash"), "Sigstore hashedrekord data");
            Map<String, Object> hash = StrictJson.object(data.get("hash"), "Sigstore hashedrekord hash");
            requireKeys(hash, Set.of("algorithm", "value"), "Sigstore hashedrekord hash");
            if (!"sha256".equals(hash.get("algorithm")) || !subjectSha.equals(hash.get("value"))) {
                throw new VerificationFailure("Sigstore hashedrekord does not bind primary digest");
            }
            Map<String, Object> signature = StrictJson.object(
                    spec.get("signature"), "Sigstore hashedrekord signature");
            requireKeys(signature, Set.of("content", "publicKey"),
                    "Sigstore hashedrekord signature");
            if (!MessageDigest.isEqual(
                    messageSignature,
                    base64(StrictJson.string(signature.get("content"),
                            "Sigstore hashedrekord signature content"),
                            "Sigstore hashedrekord signature content"))) {
                throw new VerificationFailure("Sigstore hashedrekord signature content differs");
            }
            Map<String, Object> publicKey = StrictJson.object(
                    signature.get("publicKey"), "Sigstore hashedrekord public key");
            requireKeys(publicKey, Set.of("content"), "Sigstore hashedrekord public key");
            byte[] certificate;
            try {
                certificate = leaf.getEncoded();
            } catch (GeneralSecurityException failure) {
                throw new VerificationFailure("Sigstore artifact certificate cannot be encoded");
            }
            byte[] pemBytes = base64(
                    StrictJson.string(publicKey.get("content"),
                            "Sigstore hashedrekord public key content"),
                    "Sigstore hashedrekord public key content");
            List<byte[]> certificates = pemBlocks(
                    decodeUtf8(pemBytes, "Sigstore hashedrekord public key"), "CERTIFICATE");
            if (certificates.size() != 1 || !MessageDigest.isEqual(certificate, certificates.getFirst())) {
                throw new VerificationFailure("Sigstore hashedrekord public key differs from certificate");
            }
        }

        private static List<X509Certificate> parseCertificateChain(Object value) {
            Map<String, Object> chain = StrictJson.object(value, "Sigstore certificate chain");
            requireKeys(chain, Set.of("certificates"), "Sigstore certificate chain");
            List<Object> certificates = StrictJson.array(
                    chain.get("certificates"), "Sigstore certificates");
            if (certificates.size() < 2 || certificates.size() > 8) {
                throw new VerificationFailure("Sigstore certificate chain length is invalid");
            }
            List<X509Certificate> result = new ArrayList<>();
            for (Object item : certificates) {
                Map<String, Object> certificate = StrictJson.object(item, "Sigstore certificate");
                requireKeys(certificate, Set.of("rawBytes"), "Sigstore certificate");
                byte[] der = base64(
                        StrictJson.string(certificate.get("rawBytes"), "Sigstore certificate"),
                        "Sigstore certificate");
                try {
                    result.add((X509Certificate) CertificateFactory.getInstance("X.509")
                            .generateCertificate(new ByteArrayInputStream(der)));
                } catch (GeneralSecurityException failure) {
                    throw new VerificationFailure("Sigstore certificate is invalid");
                }
            }
            return List.copyOf(result);
        }

        private static X509Certificate verifyCertificateChain(
                List<X509Certificate> chain,
                List<X509Certificate> roots,
                long integratedTime,
                String trustedBuilder) {
            try {
                X509Certificate presentedRoot = chain.getLast();
                X509Certificate trustedRoot = roots.stream().filter(root -> {
                    try {
                        return java.util.Arrays.equals(root.getEncoded(), presentedRoot.getEncoded());
                    } catch (GeneralSecurityException failure) {
                        return false;
                    }
                }).findFirst().orElse(null);
                if (trustedRoot == null) {
                    throw new VerificationFailure("Sigstore certificate chain is not rooted in policy trust");
                }
                Date signingTime = Date.from(Instant.ofEpochSecond(integratedTime));
                presentedRoot.checkValidity(signingTime);
                if (presentedRoot.getBasicConstraints() < 0) {
                    throw new VerificationFailure("Sigstore trust anchor is not a CA certificate");
                }
                java.security.cert.CertPath path = CertificateFactory.getInstance("X.509")
                        .generateCertPath(chain.subList(0, chain.size() - 1));
                java.security.cert.PKIXParameters parameters = new java.security.cert.PKIXParameters(
                        Set.of(new java.security.cert.TrustAnchor(trustedRoot, null)));
                parameters.setDate(signingTime);
                parameters.setRevocationEnabled(false);
                java.security.cert.CertPathValidator.getInstance("PKIX").validate(path, parameters);
                X509Certificate leaf = chain.getFirst();
                boolean[] usage = leaf.getKeyUsage();
                if (leaf.getBasicConstraints() >= 0
                        || usage != null && (usage.length == 0 || !usage[0])) {
                    throw new VerificationFailure("Sigstore leaf certificate lacks digitalSignature usage");
                }
                List<String> extendedUsage = leaf.getExtendedKeyUsage();
                if (extendedUsage == null || !extendedUsage.contains("1.3.6.1.5.5.7.3.3")) {
                    throw new VerificationFailure("Sigstore leaf certificate lacks codeSigning usage");
                }
                validateBuilderIdentity(leaf, trustedBuilder);
                return leaf;
            } catch (VerificationFailure failure) {
                throw failure;
            } catch (GeneralSecurityException failure) {
                throw new VerificationFailure("Sigstore X.509 chain verification failed");
            }
        }

        private static void validateBuilderIdentity(
                X509Certificate leaf, String trustedBuilder) throws GeneralSecurityException {
            String[] identity = trustedBuilder.split("\\|", -1);
            if (identity.length != 2) {
                throw new VerificationFailure("Sigstore trusted builder identity is invalid");
            }
            String certificateIssuer = identity[0].startsWith("https://")
                    ? fulcioIssuer(leaf)
                    : leaf.getIssuerX500Principal().getName();
            if (!identity[0].equals(certificateIssuer)) {
                throw new VerificationFailure("Sigstore certificate issuer differs from trusted builder");
            }
            boolean sanMatch = leaf.getSubjectAlternativeNames() != null
                    && leaf.getSubjectAlternativeNames().stream().anyMatch(name ->
                    Integer.valueOf(6).equals(name.get(0)) && identity[1].equals(name.get(1)));
            if (!sanMatch) {
                throw new VerificationFailure("Sigstore certificate SAN differs from trusted builder");
            }
        }

        private static String fulcioIssuer(X509Certificate leaf) {
            String result = null;
            for (String oid : FULCIO_ISSUER_OIDS) {
                byte[] extension = leaf.getExtensionValue(oid);
                if (extension == null) {
                    continue;
                }
                byte[] value = unwrapDer(extension, 0x04, "Fulcio issuer extension");
                if (value.length > 0 && (Byte.toUnsignedInt(value[0]) == 0x0c
                        || Byte.toUnsignedInt(value[0]) == 0x16)) {
                    value = unwrapDer(value, Byte.toUnsignedInt(value[0]), "Fulcio issuer value");
                }
                String issuer = decodeUtf8(value, "Fulcio issuer extension");
                requireHttpsUri(issuer, "Fulcio issuer extension");
                if (result != null && !result.equals(issuer)) {
                    throw new VerificationFailure("Fulcio issuer extensions disagree");
                }
                result = issuer;
            }
            if (result == null) {
                throw new VerificationFailure("Sigstore certificate lacks Fulcio issuer extension");
            }
            return result;
        }

        private static byte[] unwrapDer(byte[] encoded, int expectedTag, String description) {
            if (encoded.length < 2 || Byte.toUnsignedInt(encoded[0]) != expectedTag) {
                throw new VerificationFailure(description + " has an invalid DER tag");
            }
            int offset = 2;
            int length = Byte.toUnsignedInt(encoded[1]);
            if ((length & 0x80) != 0) {
                int count = length & 0x7f;
                if (count == 0 || count > 4 || encoded.length < 2 + count || encoded[2] == 0) {
                    throw new VerificationFailure(description + " has an invalid DER length");
                }
                length = 0;
                for (int index = 0; index < count; index++) {
                    length = (length << 8) | Byte.toUnsignedInt(encoded[2 + index]);
                }
                if (length < 128) {
                    throw new VerificationFailure(description + " has a non-canonical DER length");
                }
                offset += count;
            }
            if (length <= 0 || offset + length != encoded.length) {
                throw new VerificationFailure(description + " has an invalid DER value length");
            }
            return java.util.Arrays.copyOfRange(encoded, offset, encoded.length);
        }

        private static void verifyTransparency(
                Map<String, Object> entry,
                Map<String, Object> envelope,
                X509Certificate leaf,
                PublicKey rekorKey,
                long integratedTime) {
            requireKeys(entry, Set.of(
                    "canonicalizedBody", "inclusionPromise", "inclusionProof", "integratedTime",
                    "kindVersion", "logId", "logIndex"), "Sigstore tlog entry");
            if (canonicalLong(
                    StrictJson.string(entry.get("integratedTime"), "Sigstore integratedTime"),
                    "Sigstore integratedTime") != integratedTime) {
                throw new VerificationFailure("Sigstore integratedTime changed during verification");
            }
            Map<String, Object> kindVersion = StrictJson.object(
                    entry.get("kindVersion"), "Sigstore tlog kindVersion");
            requireKeys(kindVersion, Set.of("kind", "version"), "Sigstore tlog kindVersion");
            if (!"intoto".equals(kindVersion.get("kind"))
                    || !"0.0.2".equals(kindVersion.get("version"))) {
                throw new VerificationFailure("Sigstore tlog kind/version is invalid");
            }
            Map<String, Object> logId = StrictJson.object(entry.get("logId"), "Sigstore logId");
            requireKeys(logId, Set.of("keyId"), "Sigstore logId");
            byte[] expectedLogId = sha256Bytes(rekorKey.getEncoded());
            byte[] actualLogId = base64(
                    StrictJson.string(logId.get("keyId"), "Sigstore logId keyId"),
                    "Sigstore logId keyId");
            if (!MessageDigest.isEqual(expectedLogId, actualLogId)) {
                throw new VerificationFailure("Sigstore logId differs from policy Rekor key");
            }

            byte[] bodyBytes = base64(
                    StrictJson.string(entry.get("canonicalizedBody"), "Sigstore canonicalized body"),
                    "Sigstore canonicalized body");
            Object bodyValue = new JsonParser(
                    decodeUtf8(bodyBytes, "Sigstore canonicalized body"),
                    "Sigstore canonicalized body").parse();
            if (!java.util.Arrays.equals(bodyBytes, StrictJson.canonicalBytes(bodyValue))) {
                throw new VerificationFailure("Sigstore tlog body is not canonical JSON");
            }
            Map<String, Object> body = StrictJson.object(bodyValue, "Sigstore tlog body");
            requireKeys(body, Set.of("apiVersion", "kind", "spec"), "Sigstore tlog body");
            if (!"0.0.2".equals(body.get("apiVersion")) || !"intoto".equals(body.get("kind"))) {
                throw new VerificationFailure("Sigstore tlog body profile is invalid");
            }
            validateIntoto(body.get("spec"), envelope, leaf);

            Map<String, Object> promise = StrictJson.object(
                    entry.get("inclusionPromise"), "Sigstore inclusion promise");
            requireKeys(promise, Set.of("signedEntryTimestamp"), "Sigstore inclusion promise");
            byte[] set = base64(
                    StrictJson.string(promise.get("signedEntryTimestamp"), "Sigstore SET"),
                    "Sigstore SET");
            if (!verifySignature(rekorKey, setVerificationPayload(entry, bodyBytes), set)) {
                throw new VerificationFailure("Sigstore signed entry timestamp is invalid");
            }
            verifyInclusionProof(entry, bodyBytes, rekorKey);
        }

        private static void validateIntoto(
                Object value, Map<String, Object> envelope, X509Certificate leaf) {
            Map<String, Object> spec = StrictJson.object(value, "Sigstore intoto spec");
            requireKeys(spec, Set.of("content"), "Sigstore intoto spec");
            Map<String, Object> content = StrictJson.object(
                    spec.get("content"), "Sigstore intoto content");
            requireKeys(content, Set.of("envelope", "hash", "payloadHash"),
                    "Sigstore intoto content");
            Map<String, Object> payloadHash = StrictJson.object(
                    content.get("payloadHash"), "Sigstore intoto payloadHash");
            requireKeys(payloadHash, Set.of("algorithm", "value"), "Sigstore intoto payloadHash");
            byte[] payload = base64(
                    StrictJson.string(envelope.get("payload"), "Sigstore DSSE payload"),
                    "Sigstore DSSE payload");
            if (!"sha256".equals(payloadHash.get("algorithm"))
                    || !sha256(payload).equals(payloadHash.get("value"))) {
                throw new VerificationFailure("Sigstore intoto payloadHash differs from DSSE payload");
            }
            Map<String, Object> hash = StrictJson.object(
                    content.get("hash"), "Sigstore intoto envelope hash");
            requireKeys(hash, Set.of("algorithm", "value"), "Sigstore intoto envelope hash");
            if (!"sha256".equals(hash.get("algorithm"))
                    || !(hash.get("value") instanceof String digest)
                    || !SHA256.matcher(digest).matches()) {
                throw new VerificationFailure("Sigstore intoto envelope hash is invalid");
            }

            Map<String, Object> loggedEnvelope = StrictJson.object(
                    content.get("envelope"), "Sigstore intoto envelope");
            requireKeys(loggedEnvelope, Set.of("payload", "payloadType", "signatures"),
                    "Sigstore intoto envelope");
            if (!Objects.equals(envelope.get("payloadType"), loggedEnvelope.get("payloadType"))
                    || !MessageDigest.isEqual(payload, doubleBase64(
                            StrictJson.string(loggedEnvelope.get("payload"),
                                    "Sigstore intoto payload"),
                            "Sigstore intoto payload"))) {
                throw new VerificationFailure("Sigstore intoto envelope payload differs from DSSE");
            }
            List<Object> loggedSignatures = StrictJson.array(
                    loggedEnvelope.get("signatures"), "Sigstore intoto signatures");
            List<Object> envelopeSignatures = StrictJson.array(
                    envelope.get("signatures"), "Sigstore DSSE signatures");
            if (loggedSignatures.size() != 1 || envelopeSignatures.size() != 1) {
                throw new VerificationFailure("Sigstore intoto signature count is invalid");
            }
            Map<String, Object> loggedSignature = StrictJson.object(
                    loggedSignatures.getFirst(), "Sigstore intoto signature");
            requireKeys(loggedSignature, Set.of("publicKey", "sig"), "Sigstore intoto signature");
            Map<String, Object> envelopeSignature = StrictJson.object(
                    envelopeSignatures.getFirst(), "Sigstore DSSE signature");
            byte[] expectedSignature = base64(
                    StrictJson.string(envelopeSignature.get("sig"), "Sigstore DSSE signature"),
                    "Sigstore DSSE signature");
            if (!MessageDigest.isEqual(expectedSignature, doubleBase64(
                    StrictJson.string(loggedSignature.get("sig"), "Sigstore intoto signature"),
                    "Sigstore intoto signature"))) {
                throw new VerificationFailure("Sigstore intoto signature differs from DSSE");
            }
            byte[] pem = base64(
                    StrictJson.string(loggedSignature.get("publicKey"), "Sigstore intoto verifier"),
                    "Sigstore intoto verifier");
            List<byte[]> certificates = pemBlocks(
                    decodeUtf8(pem, "Sigstore intoto verifier"), "CERTIFICATE");
            try {
                if (certificates.size() != 1
                        || !MessageDigest.isEqual(leaf.getEncoded(), certificates.getFirst())) {
                    throw new VerificationFailure("Sigstore intoto verifier differs from certificate");
                }
            } catch (GeneralSecurityException failure) {
                throw new VerificationFailure("Sigstore intoto certificate cannot be encoded");
            }
        }

        private static byte[] doubleBase64(String value, String description) {
            String inner = decodeUtf8(base64(value, description), description + " inner base64");
            return base64(inner, description + " inner base64");
        }

        private static void verifyInclusionProof(
                Map<String, Object> entry, byte[] bodyBytes, PublicKey rekorKey) {
            Map<String, Object> proof = StrictJson.object(
                    entry.get("inclusionProof"), "Sigstore inclusion proof");
            requireKeys(proof, Set.of("checkpoint", "hashes", "logIndex", "rootHash", "treeSize"),
                    "Sigstore inclusion proof");
            long logIndex = canonicalLong(
                    StrictJson.string(entry.get("logIndex"), "Sigstore logIndex"),
                    "Sigstore logIndex");
            long proofIndex = canonicalLong(
                    StrictJson.string(proof.get("logIndex"), "Sigstore proof logIndex"),
                    "Sigstore proof logIndex");
            long treeSize = canonicalLong(
                    StrictJson.string(proof.get("treeSize"), "Sigstore proof treeSize"),
                    "Sigstore proof treeSize");
            if (treeSize == 0 || logIndex != proofIndex || logIndex >= treeSize) {
                throw new VerificationFailure("Sigstore inclusion proof index or tree size is invalid");
            }
            byte[] rootHash = base64(
                    StrictJson.string(proof.get("rootHash"), "Sigstore proof rootHash"),
                    "Sigstore proof rootHash");
            if (rootHash.length != 32) {
                throw new VerificationFailure("Sigstore proof rootHash must be SHA-256");
            }
            List<Object> hashes = StrictJson.array(proof.get("hashes"), "Sigstore proof hashes");
            if (hashes.size() > 64) {
                throw new VerificationFailure("Sigstore inclusion proof exceeds its depth limit");
            }
            byte[] calculated = hashNode((byte) 0, bodyBytes, new byte[0]);
            long node = logIndex;
            long last = treeSize - 1;
            for (Object item : hashes) {
                byte[] sibling = base64(
                        StrictJson.string(item, "Sigstore proof hash"), "Sigstore proof hash");
                if (sibling.length != 32) {
                    throw new VerificationFailure("Sigstore proof hash must be SHA-256");
                }
                if ((node & 1) == 1 || node == last) {
                    calculated = hashNode((byte) 1, sibling, calculated);
                    while ((node & 1) == 0 && node != 0) {
                        node >>= 1;
                        last >>= 1;
                    }
                } else {
                    calculated = hashNode((byte) 1, calculated, sibling);
                }
                node >>= 1;
                last >>= 1;
            }
            if (last != 0 || !MessageDigest.isEqual(calculated, rootHash)) {
                throw new VerificationFailure("Sigstore Merkle inclusion proof is invalid");
            }
            verifyCheckpoint(proof.get("checkpoint"), treeSize, rootHash, rekorKey);
        }

        private static void verifyCheckpoint(
                Object value, long treeSize, byte[] rootHash, PublicKey rekorKey) {
            Map<String, Object> checkpoint = StrictJson.object(value, "Sigstore checkpoint");
            requireKeys(checkpoint, Set.of("envelope"), "Sigstore checkpoint");
            String note = StrictJson.string(checkpoint.get("envelope"), "Sigstore checkpoint envelope");
            int separator = note.indexOf("\n\n");
            if (separator < 0 || !note.endsWith("\n")) {
                throw new VerificationFailure("Sigstore checkpoint envelope grammar is invalid");
            }
            String signedBody = note.substring(0, separator + 1);
            String[] header = signedBody.substring(0, signedBody.length() - 1).split("\n", -1);
            if (header.length < 3 || header.length > 1024 || header[0].isEmpty()
                    || canonicalLong(header[1], "Sigstore checkpoint treeSize") != treeSize
                    || !MessageDigest.isEqual(
                            base64(header[2], "Sigstore checkpoint rootHash"), rootHash)) {
                throw new VerificationFailure("Sigstore checkpoint does not bind the inclusion root");
            }
            String[] signatureLines = note.substring(separator + 2).split("\n", -1);
            if (signatureLines.length < 2 || signatureLines.length > 33
                    || !signatureLines[signatureLines.length - 1].isEmpty()) {
                throw new VerificationFailure("Sigstore checkpoint signature grammar is invalid");
            }
            byte[] expectedHint = java.util.Arrays.copyOf(sha256Bytes(rekorKey.getEncoded()), 4);
            boolean hintMatched = false;
            boolean signatureVerified = false;
            for (int index = 0; index < signatureLines.length - 1; index++) {
                String line = signatureLines[index];
                int nameEnd = line.indexOf(' ', 2);
                if (!line.startsWith("\u2014 ") || nameEnd <= 2
                        || line.substring(2, nameEnd).chars().anyMatch(Character::isWhitespace)) {
                    throw new VerificationFailure("Sigstore checkpoint signature grammar is invalid");
                }
                byte[] noteSignature = base64(
                        line.substring(nameEnd + 1), "Sigstore checkpoint signature");
                if (noteSignature.length <= 4 || !MessageDigest.isEqual(
                        expectedHint, java.util.Arrays.copyOf(noteSignature, 4))) {
                    continue;
                }
                hintMatched = true;
                if (verifySignature(
                        rekorKey,
                        signedBody.getBytes(StandardCharsets.UTF_8),
                        java.util.Arrays.copyOfRange(noteSignature, 4, noteSignature.length))) {
                    signatureVerified = true;
                }
            }
            if (!hintMatched) {
                throw new VerificationFailure("Sigstore checkpoint key hint is invalid");
            }
            if (!signatureVerified) {
                throw new VerificationFailure("Sigstore checkpoint signature is invalid");
            }
        }

        private static Statement validateStatement(
                byte[] payload, String expectedPredicateType, Policy policy) {
            Object value = new JsonParser(
                    decodeUtf8(payload, "Sigstore statement"), "Sigstore statement").parse();
            if (!java.util.Arrays.equals(payload, StrictJson.canonicalBytes(value))) {
                throw new VerificationFailure("Sigstore statement is not canonical JSON");
            }
            Map<String, Object> statement = StrictJson.object(value, "Sigstore statement");
            requireKeys(statement, Set.of("_type", "subject", "predicateType", "predicate"),
                    "Sigstore statement");
            if (!"https://in-toto.io/Statement/v1".equals(statement.get("_type"))
                    || !expectedPredicateType.equals(statement.get("predicateType"))) {
                throw new VerificationFailure("Sigstore statement type or predicateType is invalid");
            }
            Map<String, String> subjects = validateSubjects(statement.get("subject"));
            Map<String, Object> predicate = StrictJson.object(
                    statement.get("predicate"), "Sigstore predicate");
            Set<String> expectedKeys = switch (expectedPredicateType) {
                case "https://taskflowinsight.dev/attestation/artifact-provenance/v1" -> ARTIFACT_KEYS;
                case "https://taskflowinsight.dev/attestation/secret-scan/v1" -> SECRET_KEYS;
                case "https://taskflowinsight.dev/attestation/release-evidence/v1" -> EVIDENCE_KEYS;
                default -> throw new VerificationFailure("Sigstore predicateType is unsupported");
            };
            requireKeys(predicate, expectedKeys, "Sigstore predicate");
            validatePredicateTypes(predicate);
            validatePredicatePolicyBindings(predicate, expectedPredicateType, policy);
            return new Statement(Map.copyOf(subjects), Map.copyOf(predicate));
        }

        private static Map<String, String> validateSubjects(Object value) {
            List<Object> rows = StrictJson.array(value, "Sigstore subjects");
            if (rows.isEmpty() || rows.size() > 8192) {
                throw new VerificationFailure("Sigstore subjects count is invalid");
            }
            Map<String, String> result = new LinkedHashMap<>();
            String previous = null;
            for (Object row : rows) {
                Map<String, Object> subject = StrictJson.object(row, "Sigstore subject");
                requireKeys(subject, Set.of("name", "digest"), "Sigstore subject");
                String name = StrictJson.string(subject.get("name"), "Sigstore subject name");
                requireRelativePosix(name, "Sigstore subject name");
                if (previous != null && compareUtf8(previous, name) >= 0) {
                    throw new VerificationFailure("Sigstore subjects must be UTF-8 sorted and unique");
                }
                Map<String, Object> digest = StrictJson.object(
                        subject.get("digest"), "Sigstore subject digest");
                requireKeys(digest, Set.of("sha256"), "Sigstore subject digest");
                String sha = StrictJson.string(digest.get("sha256"), "Sigstore subject SHA-256");
                if (!SHA256.matcher(sha).matches() || result.putIfAbsent(name, sha) != null) {
                    throw new VerificationFailure("Sigstore subject SHA-256 or identity is invalid");
                }
                previous = name;
            }
            return result;
        }

        private static int compareUtf8(String left, String right) {
            byte[] first = left.getBytes(StandardCharsets.UTF_8);
            byte[] second = right.getBytes(StandardCharsets.UTF_8);
            int length = Math.min(first.length, second.length);
            for (int index = 0; index < length; index++) {
                int compared = Integer.compare(Byte.toUnsignedInt(first[index]), Byte.toUnsignedInt(second[index]));
                if (compared != 0) {
                    return compared;
                }
            }
            return Integer.compare(first.length, second.length);
        }

        private static void validatePredicateTypes(Map<String, Object> predicate) {
            Set<String> integers = Set.of(
                    "firstActualExit", "firstFindings", "secondActualExit", "secondFindings");
            for (Map.Entry<String, Object> entry : predicate.entrySet()) {
                String key = entry.getKey();
                if (integers.contains(key)) {
                    canonicalInteger(entry.getValue(), "Sigstore predicate " + key);
                } else {
                    String text = StrictJson.string(entry.getValue(), "Sigstore predicate " + key);
                    if (key.endsWith("Sha256") && !SHA256.matcher(text).matches()) {
                        throw new VerificationFailure("Sigstore predicate " + key + " is not SHA-256");
                    }
                    if (key.endsWith("AtUtc")) {
                        requireCanonicalInstant(text, "Sigstore predicate " + key);
                    }
                }
            }
        }

        private static void requireCanonicalInstant(String value, String description) {
            try {
                Instant parsed = Instant.parse(value);
                if (!parsed.toString().equals(value)) {
                    throw new VerificationFailure(description + " must be canonical UTC RFC 3339");
                }
            } catch (DateTimeParseException failure) {
                throw new VerificationFailure(description + " must be canonical UTC RFC 3339");
            }
        }

        private static void validatePredicatePolicyBindings(
                Map<String, Object> predicate, String predicateType, Policy policy) {
            requireValue(predicate, "productionPolicySha256", policy.sha256());
            String[] builder = policy.values().get("trustedBuilder").split("\\|", -1);
            String[] workflow = policy.values().get("provenanceWorkflow").split("\\|", -1);
            if (builder.length != 2 || workflow.length != 3) {
                throw new VerificationFailure("Sigstore builder or workflow policy identity is invalid");
            }
            if (ARTIFACT_KEYS.equals(predicate.keySet())) {
                for (String key : List.of(
                        "repository", "protectedRef", "candidateRevision", "finalVersion", "releaseTarget",
                        "releaseExecutionPolicySha256", "buildToolchainManifestSha256")) {
                    requireValue(predicate, key, policy.values().get(key));
                }
                requireValue(predicate, "vulnerabilityScannerBundleSha256",
                        bundleSha(policy.values().get("vulnerabilityScanner")));
                requireValue(predicate, "secretScannerBundleSha256",
                        bundleSha(policy.values().get("secretScanner")));
                requireValue(predicate, "sbomGeneratorBundleSha256",
                        bundleSha(policy.values().get("sbomGenerator")));
                requireOrderedTimes(predicate, "buildStartedAtUtc", "buildFinishedAtUtc");
            } else if (SECRET_KEYS.equals(predicate.keySet())) {
                requireValue(predicate, "secretScannerBundleSha256",
                        bundleSha(policy.values().get("secretScanner")));
                for (String key : List.of(
                        "firstActualExit", "firstFindings", "secondActualExit", "secondFindings")) {
                    if (canonicalInteger(predicate.get(key), "Sigstore predicate " + key) != 0) {
                        throw new VerificationFailure("Sigstore secret predicate must attest a clean scan");
                    }
                }
                requireOrderedTimes(predicate, "executionStartedAtUtc", "executionFinishedAtUtc");
            }
            if (ARTIFACT_KEYS.equals(predicate.keySet()) || EVIDENCE_KEYS.equals(predicate.keySet())) {
                requireValue(predicate, "builderIssuer", builder[0]);
                requireValue(predicate, "builderId", builder[1]);
                requireValue(predicate, "workflowRepository", workflow[0]);
                requireValue(predicate, "workflowPath", workflow[1]);
                requireValue(predicate, "workflowRevision", workflow[2]);
            }
        }

        private static void requireOrderedTimes(
                Map<String, Object> predicate, String startKey, String finishKey) {
            Instant start = Instant.parse(StrictJson.string(predicate.get(startKey), startKey));
            Instant finish = Instant.parse(StrictJson.string(predicate.get(finishKey), finishKey));
            if (finish.isBefore(start)) {
                throw new VerificationFailure("Sigstore predicate time interval is reversed");
            }
        }

        private static void requireValue(
                Map<String, Object> predicate, String key, String expected) {
            if (!expected.equals(predicate.get(key))) {
                throw new VerificationFailure("Sigstore predicate " + key + " differs from authority");
            }
        }

        private static String bundleSha(String identity) {
            int marker = identity.indexOf("#bundle-sha256:");
            return identity.substring(marker + "#bundle-sha256:".length());
        }

        private static void validateCrossLayerBindings(
                Path evidence,
                Policy policy,
                Map<String, Attestation> attestations) {
            Attestation artifact = attestations.get(
                    "https://taskflowinsight.dev/attestation/artifact-provenance/v1");
            Attestation secret = attestations.get(
                    "https://taskflowinsight.dev/attestation/secret-scan/v1");
            Attestation finale = attestations.get(
                    "https://taskflowinsight.dev/attestation/release-evidence/v1");
            if (artifact == null || secret == null || finale == null) {
                throw new VerificationFailure("Sigstore three-layer predicates are incomplete");
            }

            SealedBytes publishable = readRelative(
                    evidence, "metadata/publishable-artifacts.tsv",
                    "publishable artifact set", MAX_AUTHORITY_BYTES);
            Map<String, String> publishableSubjects = loadPublishableSubjects(
                    evidence, publishable, "publishable artifact set", 8192);
            if (!artifact.subjects().equals(publishableSubjects)) {
                throw new VerificationFailure("Sigstore artifact subjects differ from publishable artifacts");
            }
            requireSha(artifact.predicate(), "publishableArtifactSetSha256", publishable.sha256());
            requireFileSha(artifact.predicate(), "expectedCommandsSha256", evidence,
                    "metadata/expected-commands.tsv", "expected command authority");
            requireFileSha(artifact.predicate(), "actualBuildToolchainSha256", evidence,
                    "supply-chain/tool-closures/build-toolchain.tsv", "actual build toolchain");

            Map<String, String> secretSubjects = new LinkedHashMap<>();
            for (String path : List.of(
                    "security/secret-scan/report-self-scan-scope.tsv",
                    "security/secret-scan/report-self-scan.tsv",
                    "security/secret-scan/report.json",
                    "security/secret-scan/scope.tsv")) {
                SealedBytes file = readRelative(evidence, path, "secret attestation subject",
                        MAX_AUTHORITY_BYTES);
                secretSubjects.put(path, file.sha256());
            }
            if (!secret.subjects().equals(secretSubjects)) {
                throw new VerificationFailure("Sigstore secret subjects differ from two-pass scan bytes");
            }
            requireFileSha(secret.predicate(), "firstScopeSha256", evidence,
                    "security/secret-scan/scope.tsv", "first secret scope");
            requireFileSha(secret.predicate(), "firstReportSha256", evidence,
                    "security/secret-scan/report.json", "first secret report");
            requireFileSha(secret.predicate(), "secondScopeSha256", evidence,
                    "security/secret-scan/report-self-scan-scope.tsv", "second secret scope");
            requireFileSha(secret.predicate(), "secondReportSha256", evidence,
                    "security/secret-scan/report-self-scan.tsv", "second secret report");
            requireFileSha(secret.predicate(), "releaseExecutionsSha256", evidence,
                    "metadata/release-executions.tsv", "release execution ledger");
            requireFileSha(secret.predicate(), "toolExecutionsSha256", evidence,
                    "metadata/tool-executions.tsv", "tool execution ledger");

            SealedBytes subjectManifest = readRelative(
                    evidence, "metadata/evidence-subject-manifest.tsv",
                    "evidence subject manifest", MAX_AUTHORITY_BYTES);
            if (!finale.subjects().equals(Map.of(
                    "metadata/evidence-subject-manifest.tsv", subjectManifest.sha256()))) {
                throw new VerificationFailure("Sigstore final subject must be the evidence subject manifest");
            }
            validateSubjectManifest(evidence, subjectManifest, artifact, secret);
            requireSha(finale.predicate(), "evidenceSubjectManifestSha256", subjectManifest.sha256());
            requireSha(finale.predicate(), "publishableArtifactSetSha256", publishable.sha256());
            requireFileSha(finale.predicate(), "actualCommandLedgersSha256", evidence,
                    "metadata/actual-command-ledgers.tsv", "actual command ledger index");
            requireFileSha(finale.predicate(), "releaseExecutionsSha256", evidence,
                    "metadata/release-executions.tsv", "release execution ledger");
            requireFileSha(finale.predicate(), "toolExecutionsSha256", evidence,
                    "metadata/tool-executions.tsv", "tool execution ledger");
            requireFileSha(finale.predicate(), "actualBuildToolchainSha256", evidence,
                    "supply-chain/tool-closures/build-toolchain.tsv", "actual build toolchain");
            requireSha(finale.predicate(), "artifactProvenanceSha256", artifact.bundleSha256());
            requireSha(finale.predicate(), "secretProcessAttestationSha256", secret.bundleSha256());

            Instant buildStarted = Instant.parse(StrictJson.string(
                    artifact.predicate().get("buildStartedAtUtc"), "artifact build start"));
            Instant buildFinished = Instant.parse(StrictJson.string(
                    artifact.predicate().get("buildFinishedAtUtc"), "artifact build finish"));
            validateArtifactSignatureWindow(evidence, policy, buildStarted, buildFinished);
            Instant scanStarted = Instant.parse(StrictJson.string(
                    secret.predicate().get("executionStartedAtUtc"), "secret scan start"));
            Instant scanFinished = Instant.parse(StrictJson.string(
                    secret.predicate().get("executionFinishedAtUtc"), "secret scan finish"));
            Instant attested = Instant.parse(StrictJson.string(
                    finale.predicate().get("attestedAtUtc"), "final attestation time"));
            if (scanStarted.isBefore(buildFinished) || attested.isBefore(scanFinished)) {
                throw new VerificationFailure("Sigstore attestation layer order is invalid");
            }
            Instant artifactIntegrated = Instant.ofEpochSecond(artifact.integratedTime());
            Instant secretIntegrated = Instant.ofEpochSecond(secret.integratedTime());
            Instant finalIntegrated = Instant.ofEpochSecond(finale.integratedTime());
            if (artifactIntegrated.isBefore(buildFinished)
                    || secretIntegrated.isBefore(scanFinished)
                    || finalIntegrated.isBefore(attested)) {
                throw new VerificationFailure(
                        "Sigstore attestation integratedTime precedes signed predicate facts");
            }
            if (secretIntegrated.isBefore(artifactIntegrated)
                    || finalIntegrated.isBefore(secretIntegrated)) {
                throw new VerificationFailure("Sigstore transparency layer order is invalid");
            }
            requireValue(finale.predicate(), "productionPolicySha256", policy.sha256());
        }

        private static void validateArtifactSignatureWindow(
                Path evidence, Policy policy, Instant buildStarted, Instant buildFinished) {
            if (!Set.of(policy.values().get("requiredSignatures").split(",", -1))
                    .contains("SIGSTORE")) {
                return;
            }
            SealedBytes results = readRelative(
                    evidence,
                    ArtifactSignatures.RESULTS_PATH,
                    "artifact signature results",
                    MAX_AUTHORITY_BYTES);
            List<String[]> rows = parseTable(
                    results,
                    "artifact signature results",
                    ArtifactSignatures.RESULTS_HEADER,
                    10,
                    8192);
            for (String[] row : rows) {
                if (!"SIGSTORE".equals(row[1])) {
                    continue;
                }
                Instant integrated = Instant.ofEpochSecond(
                        canonicalLong(row[8], "Sigstore artifact integratedTime"));
                if (integrated.isBefore(buildStarted) || integrated.isAfter(buildFinished)) {
                    throw new VerificationFailure(
                            "Sigstore artifact signature is outside build window");
                }
            }
        }

        private static Map<String, String> loadPublishableSubjects(
                Path evidence, SealedBytes table, String description, int maximumRows) {
            List<String[]> rows = parseTable(
                    table, description,
                    "ordinal\tsubjectOrdinal\tsubjectCoordinate\trepositoryPath\trole\t"
                            + "sidecarKind\tsha256",
                    7, maximumRows);
            Map<String, String> result = new LinkedHashMap<>();
            for (int index = 0; index < rows.size(); index++) {
                String[] row = rows.get(index);
                if (!Integer.toString(index + 1).equals(row[0])
                        || !Set.of("POM", "BINARY", "SOURCES", "JAVADOC", "CHECKSUM", "SIGNATURE")
                        .contains(row[4])) {
                    throw new VerificationFailure(description + " ordinal or role is invalid");
                }
                requireRelativePosix(row[3], description + " repositoryPath");
                if (!SHA256.matcher(row[6]).matches()) {
                    throw new VerificationFailure(description + " SHA-256 is invalid");
                }
                String subjectPath = "artifacts/publishable-repository/" + row[3];
                SealedBytes subject = readRelative(
                        evidence, subjectPath, description + " subject", 256 * 1024 * 1024);
                if (!subject.sha256().equals(row[6])) {
                    throw new VerificationFailure(description + " subject SHA-256 differs from bytes");
                }
                if (result.putIfAbsent(subjectPath, row[6]) != null) {
                    throw new VerificationFailure(description + " repositoryPath must be unique");
                }
            }
            if (result.isEmpty()) {
                throw new VerificationFailure(description + " must not be empty");
            }
            return Map.copyOf(result);
        }

        private static void validateSubjectManifest(
                Path evidence,
                SealedBytes manifest,
                Attestation artifact,
                Attestation secret) {
            List<String[]> rows = parseTable(
                    manifest, "evidence subject manifest", "role\tevidencePath\tsha256", 3, 2);
            Map<String, String[]> expected = Map.of(
                    "ARTIFACT_PROVENANCE", new String[]{
                            "supply-chain/provenance/artifact-provenance.sigstore.json",
                            artifact.bundleSha256()},
                    "SECRET_PROCESS", new String[]{
                            "security/secret-scan/process-attestation.sigstore.json",
                            secret.bundleSha256()});
            if (rows.size() != expected.size()) {
                throw new VerificationFailure("evidence subject manifest closure is incomplete");
            }
            Set<String> seen = new java.util.HashSet<>();
            for (String[] row : rows) {
                String[] binding = expected.get(row[0]);
                if (binding == null || !seen.add(row[0])
                        || !binding[0].equals(row[1]) || !binding[1].equals(row[2])) {
                    throw new VerificationFailure("evidence subject manifest binding is invalid");
                }
                SealedBytes actual = readRelative(
                        evidence, row[1], "evidence subject", MAX_AUTHORITY_BYTES);
                if (!actual.sha256().equals(row[2])) {
                    throw new VerificationFailure("evidence subject manifest SHA-256 differs from bytes");
                }
            }
        }

        private static void requireFileSha(
                Map<String, Object> predicate,
                String key,
                Path evidence,
                String path,
                String description) {
            SealedBytes file = readRelative(evidence, path, description, MAX_AUTHORITY_BYTES);
            requireSha(predicate, key, file.sha256());
        }

        private static void requireSha(
                Map<String, Object> predicate, String key, String expected) {
            requireValue(predicate, key, expected);
        }

        private static void requireKeys(
                Map<String, Object> object, Set<String> expected, String description) {
            if (!object.keySet().equals(expected)) {
                throw new VerificationFailure(description + " has unknown or missing JSON keys");
            }
        }

        private static byte[] base64(String value, String description) {
            try {
                byte[] decoded = Base64.getDecoder().decode(value);
                if (!Base64.getEncoder().encodeToString(decoded).equals(value)) {
                    throw new VerificationFailure(description + " is not canonical base64");
                }
                return decoded;
            } catch (IllegalArgumentException failure) {
                throw new VerificationFailure(description + " is not valid base64");
            }
        }

        private static long canonicalLong(String value, String description) {
            try {
                long parsed = Long.parseLong(value);
                if (parsed < 0 || !Long.toString(parsed).equals(value)) {
                    throw new VerificationFailure(description + " must be a canonical non-negative integer");
                }
                return parsed;
            } catch (NumberFormatException failure) {
                throw new VerificationFailure(description + " must be a canonical non-negative integer");
            }
        }

        private static byte[] setVerificationPayload(
                Map<String, Object> entry, byte[] canonicalizedBody) {
            String integratedTime = StrictJson.string(
                    entry.get("integratedTime"), "Sigstore SET integratedTime");
            String logIndex = StrictJson.string(entry.get("logIndex"), "Sigstore SET logIndex");
            canonicalLong(integratedTime, "Sigstore SET integratedTime");
            canonicalLong(logIndex, "Sigstore SET logIndex");
            Map<String, Object> logId = StrictJson.object(entry.get("logId"), "Sigstore SET logId");
            requireKeys(logId, Set.of("keyId"), "Sigstore SET logId");
            byte[] logIdBytes = base64(
                    StrictJson.string(logId.get("keyId"), "Sigstore SET logId keyId"),
                    "Sigstore SET logId keyId");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("body", Base64.getEncoder().encodeToString(canonicalizedBody));
            payload.put("integratedTime", new JsonNumber(integratedTime));
            payload.put("logID", HexFormat.of().formatHex(logIdBytes));
            payload.put("logIndex", new JsonNumber(logIndex));
            return StrictJson.canonicalBytes(payload);
        }

        private static long canonicalInteger(Object value, String description) {
            if (!(value instanceof JsonNumber number)) {
                throw new VerificationFailure(description + " must be a JSON integer");
            }
            return canonicalLong(number.canonical(), description);
        }

        private static boolean verifySignature(PublicKey key, byte[] content, byte[] signatureBytes) {
            String algorithm = switch (key.getAlgorithm()) {
                case "EC" -> "SHA256withECDSA";
                case "RSA" -> "SHA256withRSA";
                case "Ed25519", "EdDSA" -> "Ed25519";
                default -> throw new VerificationFailure(
                        "Sigstore signature key algorithm is unsupported");
            };
            try {
                Signature verifier = Signature.getInstance(algorithm);
                verifier.initVerify(key);
                verifier.update(content);
                return verifier.verify(signatureBytes);
            } catch (GeneralSecurityException failure) {
                return false;
            }
        }

        private static byte[] dssePae(String payloadType, byte[] payload) {
            byte[] type = payloadType.getBytes(StandardCharsets.UTF_8);
            byte[] prefix = ("DSSEv1 " + type.length + " ").getBytes(StandardCharsets.UTF_8);
            byte[] separator = (" " + payload.length + " ").getBytes(StandardCharsets.UTF_8);
            byte[] result = new byte[prefix.length + type.length + separator.length + payload.length];
            int offset = 0;
            System.arraycopy(prefix, 0, result, offset, prefix.length);
            offset += prefix.length;
            System.arraycopy(type, 0, result, offset, type.length);
            offset += type.length;
            System.arraycopy(separator, 0, result, offset, separator.length);
            offset += separator.length;
            System.arraycopy(payload, 0, result, offset, payload.length);
            return result;
        }

        private static byte[] sha256Bytes(byte[] bytes) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(bytes);
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 is unavailable", impossible);
            }
        }

        private static byte[] hashNode(byte prefix, byte[] left, byte[] right) {
            byte[] input = new byte[1 + left.length + right.length];
            input[0] = prefix;
            System.arraycopy(left, 0, input, 1, left.length);
            System.arraycopy(right, 0, input, 1 + left.length, right.length);
            return sha256Bytes(input);
        }

        /** @param roots policy-pinned certificate roots; @param rekorKey policy-pinned Rekor key. */
        private record Trust(List<X509Certificate> roots, PublicKey rekorKey) {
        }

        /**
         * @param bundleSha256 signed bundle raw-byte identity
         * @param subjects statement subject path-to-SHA closure
         * @param predicate closed predicate values
         * @param integratedTime policy Rekor log integration epoch second
         */
        private record Attestation(
                String bundleSha256,
                Map<String, String> subjects,
                Map<String, Object> predicate,
                long integratedTime) {
        }

        /**
         * @param subjects statement subject path-to-SHA closure
         * @param predicate closed predicate values
         */
        private record Statement(Map<String, String> subjects, Map<String, Object> predicate) {
        }
    }

    private static final class IntegrityEvidence {
        /** 单个 evidence 文件上界，防止完整性复算无界分配。 */
        private static final int MAX_EVIDENCE_FILE_BYTES = 256 * 1024 * 1024;
        /** 固定 3.0 API baseline authority manifest，不允许候选自填其他合法摘要。 */
        private static final String BASELINE_MANIFEST_SHA256 =
                "3c2badbdb56559c6a1503a92e05e7f643c199c9eea2eb6ea5c702814cc635fa6";
        /** PREPARED marker 的顺序和值域属于发布协议。 */
        private static final List<String> PREPARED_KEYS = List.of(
                "candidateRevision", "candidateSetSha256", "baselineManifestSha256",
                "reviewAssignmentId", "productionPolicySha256", "finalVersion", "releaseTarget",
                "publishableArtifactSetSha256", "sbomSha256", "evidencePreparer",
                "independentReviewer", "evidenceStatus");
        /** CI_ONLY marker 不得携带 production assignment 或 reviewer identity。 */
        private static final List<String> CI_KEYS = List.of(
                "candidateRevision", "candidateSetSha256", "mode");

        private IntegrityEvidence() {
        }

        private static void verify(Path evidence, Path expectedReports) {
            Map<String, String> before = snapshot(evidence);
            validateManifest(evidence, before);
            Map<String, String> marker = validateMarker(evidence, before);
            validateCandidateArtifacts(evidence, before, marker);
            validateExpectedReports(evidence, expectedReports, before);
            if (before.containsKey("metadata/expected-commands.tsv")) {
                validateCommandEvidence(evidence, before, marker);
                validateArtifactConsumers(evidence);
                validatePerformanceEvidence(evidence);
                validateModuleQualityEvidence(evidence);
            }
            Map<String, String> after = snapshot(evidence);
            if (!before.equals(after)) {
                throw new VerificationFailure("evidence changed during read-only integrity verification");
            }
        }

        private static void verifyPreparedContent(Path evidence, Path expectedReports) {
            writeComplexityEvidence(evidence);
            Map<String, String> before = snapshot(evidence);
            SealedBytes candidateManifest = readRelative(
                    evidence,
                    "metadata/candidate-artifacts.sha256",
                    "candidate artifact manifest",
                    1024 * 1024);
            validateCandidateArtifacts(
                    evidence,
                    before,
                    Map.of("candidateSetSha256", candidateManifest.sha256()));
            validateExpectedReports(evidence, expectedReports, before);
            validateCommandEvidence(evidence, before, Map.of());
            validateArtifactConsumers(evidence);
            validatePerformanceEvidence(evidence);
            validateModuleQualityEvidence(evidence);
            if (!before.equals(snapshot(evidence))) {
                throw new VerificationFailure("evidence changed during structured verification");
            }
        }

        private static void writeComplexityEvidence(Path evidence) {
            SealedBytes report = readRelative(
                    evidence,
                    "module-verify/tfi-compare/pmd.xml",
                    "retained Compare PMD report",
                    MAX_EVIDENCE_FILE_BYTES);
            Map<String, Integer> required = Map.of(
                    "processFrame\tCognitiveComplexity", 26,
                    "processFrame\tCyclomaticComplexity", 23,
                    "processFrame\tNPathComplexity", 266112,
                    "validateTruth\tCyclomaticComplexity", 46,
                    "validateTruth\tNPathComplexity", 1161216);
            Map<String, Integer> actual = new LinkedHashMap<>();
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setFeature(
                        "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                factory.setXIncludeAware(false);
                factory.setExpandEntityReferences(false);
                var document = factory.newDocumentBuilder().parse(
                        new ByteArrayInputStream(report.bytes()));
                if (document.getDoctype() != null) {
                    throw new VerificationFailure("retained PMD XML is unsafe");
                }
                var violations = document.getElementsByTagName("violation");
                Pattern valuePattern = Pattern.compile("complexity of ([0-9]+)");
                for (int index = 0; index < violations.getLength(); index++) {
                    Element violation = (Element) violations.item(index);
                    String method = violation.getAttribute("method");
                    String rule = violation.getAttribute("rule");
                    String key = method + "\t" + rule;
                    if (!required.containsKey(key)) {
                        continue;
                    }
                    String expectedClass = "processFrame".equals(method)
                            ? "RequestLocalSnapshot" : "CompareResult";
                    if (!expectedClass.equals(violation.getAttribute("class"))) {
                        throw new VerificationFailure("complexity method owner differs");
                    }
                    var matcher = valuePattern.matcher(violation.getTextContent());
                    if (!matcher.find()
                            || actual.putIfAbsent(key, Integer.parseInt(matcher.group(1))) != null) {
                        throw new VerificationFailure("complexity PMD row is missing or duplicate");
                    }
                }
            } catch (VerificationFailure failure) {
                throw failure;
            } catch (ParserConfigurationException | SAXException | IOException | RuntimeException failure) {
                throw new VerificationFailure("retained PMD XML is unsafe or malformed");
            }
            if (!required.equals(actual)) {
                throw new VerificationFailure("XRT-11 complexity authority changed");
            }
            List<String> rows = new ArrayList<>(List.of("method\trule\tvalue"));
            for (String key : List.of(
                    "processFrame\tCognitiveComplexity",
                    "processFrame\tCyclomaticComplexity",
                    "processFrame\tNPathComplexity",
                    "validateTruth\tCyclomaticComplexity",
                    "validateTruth\tNPathComplexity")) {
                rows.add(key + "\t" + actual.get(key));
            }
            Path output = evidence.resolve("architecture/xrt-11/complexity.tsv");
            byte[] expectedBytes = (String.join("\n", rows) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            try {
                if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)
                            || !java.util.Arrays.equals(Files.readAllBytes(output), expectedBytes)) {
                        throw new VerificationFailure("XRT-11 complexity evidence differs");
                    }
                } else {
                    Files.write(output, expectedBytes, StandardOpenOption.CREATE_NEW);
                }
            } catch (IOException failure) {
                throw new VerificationFailure("XRT-11 complexity evidence cannot be written");
            }
        }

        private static void validateCommandEvidence(
                Path evidence, Map<String, String> files, Map<String, String> marker) {
            SealedBytes retainedExpected = readRelative(
                    evidence,
                    "metadata/expected-commands.tsv",
                    "retained expected commands",
                    MAX_AUTHORITY_BYTES);
            Path repositoryExpected = requireReadableFile(
                    Path.of("scripts/release-evidence/expected-commands.tsv"),
                    "repository expected commands");
            SealedBytes externalExpected = readSealed(
                    repositoryExpected, "repository expected commands", MAX_AUTHORITY_BYTES);
            if (!java.util.Arrays.equals(retainedExpected.bytes(), externalExpected.bytes())) {
                throw new VerificationFailure("expected command authority differs from retained bytes");
            }
            List<String[]> expected = parseTable(
                    retainedExpected,
                    "expected commands",
                    "ordinal\tcommandId\tphase\tcwd\targv\texpectedExit\timmediateCopy",
                    7,
                    128);
            SealedBytes actualFile = readRelative(
                    evidence, "metadata/commands.tsv", "actual commands", MAX_AUTHORITY_BYTES);
            List<String[]> actual = parseTable(
                    actualFile,
                    "actual commands",
                    "ordinal\tcommandId\tphase\tcwd\targv\texpectedExit\timmediateCopy\t"
                            + "startedAtUtc\tendedAtUtc\tactualExit\tcopyStatus",
                    11,
                    128);
            if (expected.size() != 51 || actual.size() != expected.size()) {
                throw new VerificationFailure("command ledger must contain exactly 51 rows");
            }
            Map<String, String> bindings = new LinkedHashMap<>();
            Set<String> disposableRepositories = new java.util.HashSet<>();
            String repositoryRoot = actual.getFirst()[3];
            if (!Path.of(repositoryRoot).isAbsolute()) {
                throw new VerificationFailure("actual command repository root must be absolute");
            }
            for (int index = 0; index < expected.size(); index++) {
                String[] template = expected.get(index);
                String[] executed = actual.get(index);
                String ordinal = Integer.toString(index + 1);
                if (!ordinal.equals(template[0])
                        || !ordinal.equals(executed[0])
                        || !template[1].equals(executed[1])
                        || !template[2].equals(executed[2])
                        || !template[5].equals(executed[5])
                        || !template[6].equals(executed[6])
                        || !"PASS".equals(executed[10])) {
                    throw new VerificationFailure("expected and actual command rows differ");
                }
                if (!"-".equals(template[6])) {
                    for (String path : template[6].split(",", -1)) {
                        requireRelativePosix(path, "command immediateCopy path");
                        if (!files.containsKey(path)) {
                            throw new VerificationFailure("command immediateCopy evidence is missing");
                        }
                    }
                }
                String expectedCwd = "<REPO_ROOT>".equals(template[3])
                        ? repositoryRoot
                        : Path.of(repositoryRoot).resolve(template[3]).normalize().toString();
                if (!expectedCwd.equals(executed[3])) {
                    throw new VerificationFailure("actual command cwd differs from authority");
                }
                bindCommandTemplate(
                        template[4], executed[4], bindings, disposableRepositories);
                int exit = canonicalNonNegative(executed[9], "actual command exit");
                if ("C-MIXED".equals(template[1])) {
                    if (!"NON_ZERO_DEPENDENCY_CONVERGENCE".equals(template[5]) || exit == 0) {
                        throw new VerificationFailure("mixed command exit semantics differ");
                    }
                } else if (!"0".equals(template[5]) || exit != 0) {
                    throw new VerificationFailure("zero-exit command semantics differ");
                }
                Instant started = parseCommandTime(executed[7]);
                Instant ended = parseCommandTime(executed[8]);
                if (ended.isBefore(started)) {
                    throw new VerificationFailure("command time interval is reversed");
                }
            }
            validateCommandBindings(bindings, disposableRepositories, marker);
        }

        private static Instant parseCommandTime(String value) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException failure) {
                throw new VerificationFailure("command timestamp is invalid");
            }
        }

        private static void bindCommandTemplate(
                String template,
                String actual,
                Map<String, String> bindings,
                Set<String> disposableRepositories) {
            Pattern placeholder = Pattern.compile("<([A-Z_]+)>");
            var placeholders = placeholder.matcher(template);
            StringBuilder expression = new StringBuilder("^");
            List<String> names = new ArrayList<>();
            int offset = 0;
            while (placeholders.find()) {
                expression.append(Pattern.quote(template.substring(offset, placeholders.start())));
                expression.append("(.+?)");
                names.add(placeholders.group(1));
                offset = placeholders.end();
            }
            expression.append(Pattern.quote(template.substring(offset))).append('$');
            var matched = Pattern.compile(expression.toString()).matcher(actual);
            if (!matched.matches()) {
                throw new VerificationFailure("actual command argv differs from authority");
            }
            Set<String> allowed = Set.of(
                    "RUN_REPO", "DISPOSABLE_REPO", "EVIDENCE", "CANDIDATE_VERSION",
                    "CANDIDATE_REVISION", "AUDIT_MODE", "PRODUCTION_POLICY", "FINAL_VERSION");
            for (int index = 0; index < names.size(); index++) {
                String name = names.get(index);
                String value = matched.group(index + 1);
                if (!allowed.contains(name) || value.isEmpty()
                        || value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0) {
                    throw new VerificationFailure("command placeholder binding is invalid");
                }
                if ("DISPOSABLE_REPO".equals(name)) {
                    disposableRepositories.add(value);
                    continue;
                }
                String previous = bindings.putIfAbsent(name, value);
                if (previous != null && !previous.equals(value)) {
                    throw new VerificationFailure("command placeholder binding changed between rows");
                }
            }
        }

        private static void validateCommandBindings(
                Map<String, String> bindings,
                Set<String> disposableRepositories,
                Map<String, String> marker) {
            Set<String> expected = Set.of(
                    "RUN_REPO", "EVIDENCE", "CANDIDATE_VERSION", "CANDIDATE_REVISION",
                    "AUDIT_MODE", "PRODUCTION_POLICY", "FINAL_VERSION");
            if (!bindings.keySet().equals(expected) || disposableRepositories.size() != 8) {
                throw new VerificationFailure("command placeholder closure differs");
            }
            String finalVersion = bindings.get("FINAL_VERSION");
            if (!FIXED_VERSION.matcher(finalVersion).matches()
                    || "3.0.0".equals(finalVersion)
                    || !finalVersion.equals(bindings.get("CANDIDATE_VERSION"))) {
                throw new VerificationFailure("command candidate version binding is invalid");
            }
            if (!bindings.get("CANDIDATE_REVISION").matches("[0-9a-f]{40}")
                    || !Set.of("auto", "pre-terminal").contains(bindings.get("AUDIT_MODE"))
                    || !bindings.get("PRODUCTION_POLICY").endsWith("/policy/production-policy.tsv")) {
                throw new VerificationFailure("command release identity binding is invalid");
            }
            for (String path : List.of(
                    bindings.get("RUN_REPO"), bindings.get("EVIDENCE"),
                    bindings.get("PRODUCTION_POLICY"))) {
                if (!path.startsWith("/") || path.contains("\t") || path.contains("\n")) {
                    throw new VerificationFailure("command path binding is invalid");
                }
            }
            if (!marker.isEmpty()) {
                if (!marker.get("candidateRevision").equals(bindings.get("CANDIDATE_REVISION"))) {
                    throw new VerificationFailure("command candidate revision differs from marker");
                }
                String markerVersion = marker.get("finalVersion");
                if (markerVersion != null && !markerVersion.equals(finalVersion)) {
                    throw new VerificationFailure("command final version differs from marker");
                }
                boolean prepared = marker.containsKey("evidenceStatus");
                String expectedAudit = prepared ? "pre-terminal" : "auto";
                if (!expectedAudit.equals(bindings.get("AUDIT_MODE"))) {
                    throw new VerificationFailure("command audit mode differs from marker mode");
                }
            }
        }

        private static void validateArtifactConsumers(Path evidence) {
            Map<String, String> candidate = loadChecksumManifest(
                    evidence,
                    "metadata/candidate-artifacts.sha256",
                    "candidate artifact manifest");
            Map<String, String> baseline = loadChecksumManifest(
                    evidence,
                    "metadata/normalized-baseline-artifacts.sha256",
                    "normalized baseline artifact manifest");
            String version = candidateVersion(candidate.keySet());
            Map<String, Set<String>> classes = Map.of(
                    "starter-only", Set.of(
                            "com.syy.taskflowinsight.compare.spring.TfiCompareAutoConfiguration",
                            "com.syy.taskflowinsight.tracking.compare.CompareEngine"),
                    "ops-only", Set.of(
                            "com.syy.taskflowinsight.ops.compare.CompareObservationAutoConfiguration"),
                    "composed-boot", Set.of(
                            "com.syy.taskflowinsight.compare.spring.TfiCompareAutoConfiguration",
                            "com.syy.taskflowinsight.tracking.compare.CompareEngine",
                            "com.syy.taskflowinsight.ops.compare.ObservedCompareOperations"),
                    "context-hierarchy", Set.of(
                            "com.syy.taskflowinsight.compare.spring.TfiCompareAutoConfiguration",
                            "com.syy.taskflowinsight.tracking.compare.CompareEngine",
                            "com.syy.taskflowinsight.ops.compare.ObservedCompareOperations"));
            for (Map.Entry<String, Set<String>> fixture : classes.entrySet()) {
                String base = "artifact-consumers/" + fixture.getKey();
                validateCodeSources(
                        evidence, base + "/codesource.tsv", fixture.getValue(), candidate, version);
                validateCandidateTree(
                        readRelative(evidence, base + "/dependency-tree.txt",
                                "artifact dependency tree", MAX_AUTHORITY_BYTES),
                        fixture.getKey(), version);
            }
            Set<String> rollbackClasses = Set.of(
                    "com.syy.taskflowinsight.api.TFI",
                    "com.syy.taskflowinsight.tracking.compare.CompareResult");
            for (String phase : List.of("baseline-before", "candidate", "baseline-after")) {
                String base = "artifact-consumers/baseline-upgrade-rollback/" + phase;
                boolean candidatePhase = "candidate".equals(phase);
                validateCodeSources(
                        evidence,
                        base + "/codesource.tsv",
                        rollbackClasses,
                        candidatePhase ? candidate : baseline,
                        candidatePhase ? version : "3.0.0");
                readRelative(evidence, base + "/dependency-tree.txt",
                        "rollback dependency tree", MAX_AUTHORITY_BYTES);
            }
            requireEqualEvidence(
                    evidence,
                    "artifact-consumers/baseline-upgrade-rollback/baseline-before/semantic-result.tsv",
                    "artifact-consumers/baseline-upgrade-rollback/candidate/semantic-result.tsv");
            requireEqualEvidence(
                    evidence,
                    "artifact-consumers/baseline-upgrade-rollback/candidate/semantic-result.tsv",
                    "artifact-consumers/baseline-upgrade-rollback/baseline-after/semantic-result.tsv");
            validateMixedFailure(evidence, version);
        }

        private static Map<String, String> loadChecksumManifest(
                Path evidence, String path, String description) {
            SealedBytes manifest = readRelative(evidence, path, description, MAX_AUTHORITY_BYTES);
            Map<String, String> result = new LinkedHashMap<>();
            String previous = null;
            for (String line : decodeLines(manifest.bytes(), description)) {
                if (line.length() < 67 || line.charAt(64) != ' ' || line.charAt(65) != ' ') {
                    throw new VerificationFailure(description + " row is malformed");
                }
                String sha = line.substring(0, 64);
                String relative = line.substring(66);
                requireRelativePosix(relative, description + " path");
                if (!SHA256.matcher(sha).matches()
                        || previous != null && previous.compareTo(relative) >= 0
                        || result.putIfAbsent(relative, sha) != null) {
                    throw new VerificationFailure(description + " rows are unordered or duplicate");
                }
                previous = relative;
            }
            return Map.copyOf(result);
        }

        private static void validateCodeSources(
                Path evidence,
                String path,
                Set<String> expectedClasses,
                Map<String, String> expectedShas,
                String version) {
            SealedBytes file = readRelative(evidence, path, "artifact CodeSource", MAX_AUTHORITY_BYTES);
            List<String[]> rows = parseTable(
                    file,
                    "artifact CodeSource",
                    "className\trepositoryPath\tactualSha256\texpectedSha256\tstatus",
                    5,
                    32);
            Set<String> actualClasses = new java.util.HashSet<>();
            String previous = null;
            for (String[] row : rows) {
                requireRelativePosix(row[1], "CodeSource repository path");
                if (!actualClasses.add(row[0])
                        || previous != null && previous.compareTo(row[0]) >= 0
                        || !row[1].contains("/" + version + "/")
                        || !row[1].endsWith("-" + version + ".jar")
                        || !SHA256.matcher(row[2]).matches()
                        || !row[2].equals(row[3])
                        || !row[3].equals(expectedShas.get(row[1]))
                        || !"PASS".equals(row[4])) {
                    throw new VerificationFailure("artifact CodeSource row is invalid");
                }
                previous = row[0];
            }
            if (!actualClasses.equals(expectedClasses)) {
                throw new VerificationFailure("artifact CodeSource class closure differs");
            }
        }

        private static void validateCandidateTree(
                SealedBytes tree, String fixture, String version) {
            String content = decodeUtf8(tree.bytes(), "artifact dependency tree");
            if (content.contains("com.syy:tfi-kernel:")
                    || content.contains("SNAPSHOT")) {
                throw new VerificationFailure("candidate artifact tree contains a forbidden dependency");
            }
            if ("starter-only".equals(fixture)) {
                if (!content.contains("org.springframework.boot:spring-boot-starter:")
                        || content.contains("com.syy:tfi-flow-spring-starter:")
                        || content.contains("com.syy:tfi-ops-spring:")) {
                    throw new VerificationFailure("starter-only dependency tree differs");
                }
            } else if ("ops-only".equals(fixture)) {
                if (content.contains("com.syy:tfi-compare")) {
                    throw new VerificationFailure("ops-only dependency tree contains Compare");
                }
                return;
            }
            if (!content.contains("com.syy:tfi-flow-core:jar:" + version + ":")) {
                throw new VerificationFailure("candidate artifact tree has no exact Flow Core version");
            }
            var tfiVersion = Pattern.compile(
                    "com\\.syy:[^:\\s]+:(?:jar|pom):([^:\\s]+):").matcher(content);
            while (tfiVersion.find()) {
                if (!version.equals(tfiVersion.group(1))) {
                    throw new VerificationFailure("candidate artifact tree mixes TFI versions");
                }
            }
        }

        private static void requireEqualEvidence(Path evidence, String leftPath, String rightPath) {
            SealedBytes left = readRelative(
                    evidence, leftPath, "rollback semantic evidence", MAX_AUTHORITY_BYTES);
            SealedBytes right = readRelative(
                    evidence, rightPath, "rollback semantic evidence", MAX_AUTHORITY_BYTES);
            if (!java.util.Arrays.equals(left.bytes(), right.bytes())) {
                throw new VerificationFailure("rollback semantic evidence differs between phases");
            }
        }

        private static void validateMixedFailure(Path evidence, String version) {
            SealedBytes file = readRelative(
                    evidence,
                    "artifact-consumers/baseline-upgrade-rollback/mixed/expected-failure.tsv",
                    "mixed dependency failure",
                    MAX_AUTHORITY_BYTES);
            List<String[]> rows = parseTable(
                    file,
                    "mixed dependency failure",
                    "actualExit\tconflictGA\tbaselineVersion\tcandidateVersion\tstatus",
                    5,
                    2);
            if (rows.size() != 1
                    || canonicalNonNegative(rows.getFirst()[0], "mixed actualExit") == 0
                    || !"com.syy:tfi-compare".equals(rows.getFirst()[1])
                    || !"3.0.0".equals(rows.getFirst()[2])
                    || !version.equals(rows.getFirst()[3])
                    || !"PASS".equals(rows.getFirst()[4])) {
                throw new VerificationFailure("mixed dependency failure metadata differs");
            }
            SealedBytes log = readRelative(
                    evidence,
                    "artifact-consumers/baseline-upgrade-rollback/mixed/maven.log",
                    "mixed dependency failure log",
                    MAX_AUTHORITY_BYTES);
            String content = decodeUtf8(log.bytes(), "mixed dependency failure log");
            if (!content.contains("Dependency convergence error for com.syy:tfi-compare:jar:3.0.0")
                    || !content.contains("com.syy:tfi-compare:jar:" + version + ":compile")) {
                throw new VerificationFailure("mixed dependency failure log differs");
            }
        }

        private static void validatePerformanceEvidence(Path evidence) {
            SealedBytes success = readRelative(
                    evidence,
                    "performance/compare-production/_SUCCESS",
                    "production performance marker",
                    MAX_AUTHORITY_BYTES);
            Path root = success.path().getParent();
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new VerificationFailure("production performance root is missing");
            }
            List<String> lines = decodeLines(success.bytes(), "production performance marker");
            if (lines.size() < 4
                    || !"schema\tTFI_COMPARE_JMH_V1".equals(lines.get(0))
                    || !"workloadCount\t21".equals(lines.get(1))
                    || !"entryType\trelativePath\tsha256".equals(lines.get(2))) {
                throw new VerificationFailure("production performance marker header differs");
            }
            Map<String, String[]> expected = new LinkedHashMap<>();
            for (String line : lines.subList(3, lines.size())) {
                String[] row = line.split("\t", -1);
                if (row.length != 3 || !Set.of("directory", "file").contains(row[0])) {
                    throw new VerificationFailure("production performance marker row is invalid");
                }
                requireRelativePosix(row[1], "production performance path");
                if (("directory".equals(row[0]) && !"-".equals(row[2]))
                        || ("file".equals(row[0]) && !SHA256.matcher(row[2]).matches())
                        || "_SUCCESS".equals(row[1])
                        || "_SUCCESS.tmp".equals(row[1])
                        || expected.putIfAbsent(row[1], row) != null) {
                    throw new VerificationFailure("production performance marker closure is invalid");
                }
            }
            Map<String, String[]> actual = performanceTree(root);
            if (!actual.keySet().equals(expected.keySet())) {
                throw new VerificationFailure("production performance tree closure differs");
            }
            for (String path : expected.keySet()) {
                if (!java.util.Arrays.equals(expected.get(path), actual.get(path))) {
                    throw new VerificationFailure("production performance tree SHA differs");
                }
            }
            validatePerformanceSemantics(evidence, expected);
            validatePerformanceArtifacts(evidence);
            readRelative(evidence, "performance/tfi-routing-enabled.json",
                    "routing performance report", MAX_EVIDENCE_FILE_BYTES);
            readRelative(evidence, "performance/tfi-routing-legacy.json",
                    "legacy performance report", MAX_EVIDENCE_FILE_BYTES);
        }

        private static Map<String, String[]> performanceTree(Path root) {
            List<Path> paths;
            try (var walk = Files.walk(root)) {
                paths = walk.filter(path -> !path.equals(root))
                        .filter(path -> !path.equals(root.resolve("_SUCCESS")))
                        .sorted()
                        .toList();
            } catch (IOException failure) {
                throw new VerificationFailure("production performance tree cannot be read");
            }
            Map<String, String[]> result = new LinkedHashMap<>();
            for (Path path : paths) {
                String relative = root.relativize(path).toString()
                        .replace(java.io.File.separatorChar, '/');
                requireRelativePosix(relative, "production performance path");
                BasicFileAttributes attributes;
                try {
                    attributes = Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                } catch (IOException failure) {
                    throw new VerificationFailure("production performance entry changed");
                }
                String[] row;
                if (attributes.isDirectory()) {
                    row = new String[]{"directory", relative, "-"};
                } else if (attributes.isRegularFile()) {
                    row = new String[]{"file", relative,
                            readSealed(path, "production performance file",
                                    MAX_EVIDENCE_FILE_BYTES).sha256()};
                } else {
                    throw new VerificationFailure("production performance entry is unsafe");
                }
                result.put(relative, row);
            }
            return Map.copyOf(result);
        }

        private static void validatePerformanceSemantics(
                Path evidence, Map<String, String[]> tree) {
            SealedBytes semantic = readRelative(
                    evidence,
                    "performance/compare-production/semantic-facts.tsv",
                    "production performance semantics",
                    MAX_AUTHORITY_BYTES);
            List<String[]> rows = parseTable(
                    semantic,
                    "production performance semantics",
                    "workloadId\tscenario\tthreads\toutcome\tcompletion\tchangeCount\t"
                            + "changeTokens\tlimitationCodes\tdistinctInputs\tobservedDecorator",
                    10,
                    32);
            Set<String> scenarios = Set.of(
                    "NESTED_POJO", "LIST", "MAP", "SET_SCALAR", "SET_ENTITY",
                    "SET_AMBIGUOUS", "OBSERVED_COMPARE");
            Set<String> expectedWorkloads = new java.util.HashSet<>();
            for (String scenario : scenarios) {
                for (String threads : List.of("1", "8", "32")) {
                    expectedWorkloads.add(
                            scenario.toLowerCase(java.util.Locale.ROOT) + "-t" + threads);
                }
            }
            Set<String> actualWorkloads = new java.util.HashSet<>();
            for (String[] row : rows) {
                boolean ambiguous = "SET_AMBIGUOUS".equals(row[1]);
                boolean observed = "OBSERVED_COMPARE".equals(row[1]);
                if (!scenarios.contains(row[1])
                        || !Set.of("1", "8", "32").contains(row[2])
                        || !row[0].equals(row[1].toLowerCase(java.util.Locale.ROOT) + "-t" + row[2])
                        || !(ambiguous ? "INDETERMINATE" : "DIFFERENT").equals(row[3])
                        || !(ambiguous ? "PARTIAL" : "COMPLETE").equals(row[4])
                        || (ambiguous && (!"0".equals(row[5])
                                || !"KEY_AMBIGUOUS".equals(row[7])))
                        || !"true".equals(row[8])
                        || !Boolean.toString(observed).equals(row[9])
                        || !actualWorkloads.add(row[0])) {
                    throw new VerificationFailure("production performance semantic row differs");
                }
                String rawPath = "raw/" + row[0] + ".json";
                if (!tree.containsKey(rawPath) || !"file".equals(tree.get(rawPath)[0])) {
                    throw new VerificationFailure("production performance raw workload is missing");
                }
            }
            if (!actualWorkloads.equals(expectedWorkloads)) {
                throw new VerificationFailure("production performance workload closure differs");
            }
        }

        private static void validatePerformanceArtifacts(Path evidence) {
            SealedBytes file = readRelative(
                    evidence, "performance/artifacts.tsv", "performance artifact index",
                    MAX_AUTHORITY_BYTES);
            List<String[]> rows = parseTable(
                    file,
                    "performance artifact index",
                    "repositoryPath\tsha256",
                    2,
                    32);
            Map<String, String> expected = loadChecksumManifest(
                    evidence,
                    "metadata/candidate-artifacts.sha256",
                    "candidate artifact manifest");
            Map<String, String> actual = new LinkedHashMap<>();
            for (String[] row : rows) {
                requireRelativePosix(row[0], "performance artifact path");
                if (!SHA256.matcher(row[1]).matches()
                        || actual.putIfAbsent(row[0], row[1]) != null) {
                    throw new VerificationFailure("performance artifact index is invalid");
                }
            }
            if (!actual.equals(expected)) {
                throw new VerificationFailure("performance artifacts differ from candidate bytes");
            }
        }

        private static void validateModuleQualityEvidence(Path evidence) {
            for (String module : List.of("tfi-compare", "tfi-compare-spring-starter")) {
                String root = "module-verify/" + module + "/";
                validateXmlRoot(evidence, root + "jacoco.xml", "report", null);
                validateXmlRoot(evidence, root + "checkstyle.xml", "checkstyle", null);
                validateXmlRoot(evidence, root + "pmd.xml", "pmd", null);
                validateXmlRoot(evidence, root + "spotbugs.xml", "BugCollection", "BugInstance");
            }
            SealedBytes log = readRelative(
                    evidence, "module-verify/maven.log", "owning module verify log",
                    MAX_EVIDENCE_FILE_BYTES);
            String content = decodeUtf8(log.bytes(), "owning module verify log");
            for (String gate : List.of(
                    "jacoco:0.8.12:check (check)",
                    "spotbugs:4.8.6.6:check (spotbugs-check)",
                    "exec:3.1.0:exec (enforce-compare-starter-static-analysis-baseline)")) {
                if (!content.contains(gate)) {
                    throw new VerificationFailure("owning module verify omitted a required quality gate");
                }
            }
            SealedBytes summary = readRelative(
                    evidence, "portfolio/test-summary.tsv", "portfolio test summary",
                    MAX_AUTHORITY_BYTES);
            List<String[]> rows = parseTable(
                    summary, "portfolio test summary", "metric\tvalue", 2, 8);
            Map<String, String> values = new LinkedHashMap<>();
            for (String[] row : rows) {
                if (values.putIfAbsent(row[0], row[1]) != null) {
                    throw new VerificationFailure("portfolio test summary has a duplicate metric");
                }
            }
            if (!"0".equals(values.get("reactorExit"))
                    || !"PASS".equals(values.get("status"))
                    || canonicalNonNegative(values.get("surefireReportCount"),
                            "portfolio Surefire report count") == 0) {
                throw new VerificationFailure("portfolio test summary differs");
            }
        }

        private static void validateXmlRoot(
                Path evidence, String path, String rootName, String forbiddenElement) {
            SealedBytes file = readRelative(evidence, path, "module quality XML", MAX_EVIDENCE_FILE_BYTES);
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setFeature(
                        "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                factory.setXIncludeAware(false);
                factory.setExpandEntityReferences(false);
                var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(file.bytes()));
                if (document.getDoctype() != null
                        || document.getDocumentElement() == null
                        || !rootName.equals(document.getDocumentElement().getTagName())
                        || forbiddenElement != null
                        && document.getElementsByTagName(forbiddenElement).getLength() != 0) {
                    throw new VerificationFailure("module quality XML gate differs");
                }
            } catch (VerificationFailure failure) {
                throw failure;
            } catch (ParserConfigurationException | SAXException | IOException | RuntimeException failure) {
                throw new VerificationFailure("module quality XML is unsafe or malformed");
            }
        }

        private static Map<String, String> snapshot(Path evidence) {
            List<Path> paths;
            try (var walk = Files.walk(evidence)) {
                paths = walk.sorted().toList();
            } catch (IOException failure) {
                throw new VerificationFailure("evidence directory cannot be traversed");
            }
            Map<String, String> result = new LinkedHashMap<>();
            for (Path path : paths) {
                if (path.equals(evidence)) {
                    continue;
                }
                BasicFileAttributes attributes;
                try {
                    attributes = Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                } catch (IOException failure) {
                    throw new VerificationFailure("evidence entry changed during traversal");
                }
                if (attributes.isSymbolicLink() || attributes.isOther()) {
                    throw new VerificationFailure("evidence must not contain symbolic or special entries");
                }
                if (attributes.isDirectory()) {
                    continue;
                }
                if (!attributes.isRegularFile()) {
                    throw new VerificationFailure("evidence contains an unsupported entry type");
                }
                String relative = evidence.relativize(path).toString()
                        .replace(java.io.File.separatorChar, '/');
                requireRelativePosix(relative, "evidence path");
                SealedBytes bytes = readSealed(path, "evidence file", MAX_EVIDENCE_FILE_BYTES);
                if (result.putIfAbsent(relative, bytes.sha256()) != null) {
                    throw new VerificationFailure("evidence contains a duplicate relative path");
                }
            }
            return Map.copyOf(result);
        }

        private static void validateManifest(Path evidence, Map<String, String> actual) {
            SealedBytes manifest = readRelative(
                    evidence, "evidence-manifest.sha256", "evidence manifest", 64 * 1024 * 1024);
            List<String> lines = decodeLines(manifest.bytes(), "evidence manifest");
            if (lines.isEmpty()) {
                throw new VerificationFailure("evidence manifest is empty");
            }
            Map<String, String> expected = new LinkedHashMap<>();
            String previous = null;
            for (String line : lines) {
                if (line.length() < 67 || line.charAt(64) != ' ' || line.charAt(65) != ' ') {
                    throw new VerificationFailure("evidence manifest row is malformed");
                }
                String digest = line.substring(0, 64);
                String relative = line.substring(66);
                requireRelativePosix(relative, "evidence manifest path");
                if (!SHA256.matcher(digest).matches()
                        || "evidence-manifest.sha256".equals(relative)
                        || (previous != null && previous.compareTo(relative) >= 0)
                        || expected.putIfAbsent(relative, digest) != null) {
                    throw new VerificationFailure("evidence manifest paths must be sorted and unique");
                }
                previous = relative;
            }
            Map<String, String> actualWithoutManifest = new LinkedHashMap<>(actual);
            actualWithoutManifest.remove("evidence-manifest.sha256");
            if (!expected.keySet().equals(actualWithoutManifest.keySet())) {
                throw new VerificationFailure("evidence manifest file closure differs");
            }
            for (Map.Entry<String, String> entry : expected.entrySet()) {
                if (!entry.getValue().equals(actualWithoutManifest.get(entry.getKey()))) {
                    throw new VerificationFailure("evidence manifest SHA differs from retained bytes");
                }
            }
        }

        private static Map<String, String> validateMarker(
                Path evidence, Map<String, String> files) {
            boolean prepared = files.containsKey("PREPARED");
            boolean ciOnly = files.containsKey("CI_ONLY");
            if (prepared == ciOnly) {
                throw new VerificationFailure("evidence requires exactly one PREPARED or CI_ONLY marker");
            }
            String markerPath = prepared ? "PREPARED" : "CI_ONLY";
            SealedBytes marker = readRelative(evidence, markerPath, "evidence marker", 64 * 1024);
            byte[] markerBytes = marker.bytes();
            if (markerBytes.length < 2
                    || markerBytes[markerBytes.length - 1] != '\n'
                    || markerBytes[markerBytes.length - 2] == '\n') {
                throw new VerificationFailure("evidence marker must end with one LF");
            }
            List<String> keys = prepared ? PREPARED_KEYS : CI_KEYS;
            List<String> lines = decodeLines(marker.bytes(), "evidence marker");
            if (lines.size() != keys.size()) {
                throw new VerificationFailure("evidence marker has an invalid line count");
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int index = 0; index < keys.size(); index++) {
                String[] row = lines.get(index).split("\t", -1);
                if (row.length != 2 || !keys.get(index).equals(row[0]) || row[1].isEmpty()) {
                    throw new VerificationFailure("evidence marker key order or value is invalid");
                }
                values.put(row[0], row[1]);
            }
            validateMarkerValues(values, prepared);
            return Map.copyOf(values);
        }

        private static void validateMarkerValues(Map<String, String> values, boolean prepared) {
            if (!values.get("candidateRevision").matches("[0-9a-f]{40}")
                    || !SHA256.matcher(values.get("candidateSetSha256")).matches()) {
                throw new VerificationFailure("evidence marker candidate identity is invalid");
            }
            if (!prepared) {
                if (!"CI_ONLY".equals(values.get("mode"))) {
                    throw new VerificationFailure("CI marker mode is invalid");
                }
                return;
            }
            for (String key : List.of(
                    "baselineManifestSha256", "productionPolicySha256",
                    "publishableArtifactSetSha256", "sbomSha256")) {
                if (!SHA256.matcher(values.get(key)).matches()) {
                    throw new VerificationFailure("PREPARED marker SHA field is invalid");
                }
            }
            if (!BASELINE_MANIFEST_SHA256.equals(values.get("baselineManifestSha256"))) {
                throw new VerificationFailure("PREPARED marker baseline authority SHA is invalid");
            }
            if (!FIXED_VERSION.matcher(values.get("finalVersion")).matches()
                    || "3.0.0".equals(values.get("finalVersion"))) {
                throw new VerificationFailure("PREPARED marker finalVersion is invalid");
            }
            Policy.validateReleaseTarget(values.get("releaseTarget"));
            if (!values.get("reviewAssignmentId").contains(":")
                    || !actorIdentity(values.get("evidencePreparer"))
                    || !actorIdentity(values.get("independentReviewer"))
                    || values.get("evidencePreparer").equals(values.get("independentReviewer"))
                    || !"PREPARED".equals(values.get("evidenceStatus"))) {
                throw new VerificationFailure("PREPARED marker authority identity is invalid");
            }
        }

        private static boolean actorIdentity(String value) {
            return value.matches("[^:\\s]+:[^:\\s]+:[^:\\s]+");
        }

        private static void validateCandidateArtifacts(
                Path evidence, Map<String, String> files, Map<String, String> marker) {
            SealedBytes manifest = readRelative(
                    evidence,
                    "metadata/candidate-artifacts.sha256",
                    "candidate artifact manifest",
                    1024 * 1024);
            List<String> lines = decodeLines(manifest.bytes(), "candidate artifact manifest");
            if (lines.size() != 13) {
                throw new VerificationFailure("candidate artifact manifest must contain 13 rows");
            }
            Map<String, String> retained = new LinkedHashMap<>();
            String previous = null;
            for (String line : lines) {
                if (line.length() < 67 || line.charAt(64) != ' ' || line.charAt(65) != ' ') {
                    throw new VerificationFailure("candidate artifact manifest row is malformed");
                }
                String digest = line.substring(0, 64);
                String repositoryPath = line.substring(66);
                requireRelativePosix(repositoryPath, "candidate artifact repository path");
                if (!SHA256.matcher(digest).matches()
                        || previous != null && previous.compareTo(repositoryPath) >= 0
                        || retained.putIfAbsent(repositoryPath, digest) != null) {
                    throw new VerificationFailure(
                            "candidate artifact manifest paths must be sorted and unique");
                }
                previous = repositoryPath;
            }
            String version = candidateVersion(retained.keySet());
            Set<String> expected = Set.copyOf(candidateArtifactPaths(version));
            if (!retained.keySet().equals(expected)) {
                throw new VerificationFailure("candidate artifact manifest closure differs");
            }
            Set<String> actualPaths = new java.util.HashSet<>();
            for (String path : files.keySet()) {
                if (path.startsWith("artifacts/repository/")) {
                    actualPaths.add(path.substring("artifacts/repository/".length()));
                }
            }
            if (!actualPaths.equals(expected)) {
                throw new VerificationFailure("candidate artifact repository closure differs");
            }
            for (Map.Entry<String, String> entry : retained.entrySet()) {
                String actual = files.get("artifacts/repository/" + entry.getKey());
                if (!entry.getValue().equals(actual)) {
                    throw new VerificationFailure("candidate artifact SHA differs from retained bytes");
                }
            }
            if (!manifest.sha256().equals(marker.get("candidateSetSha256"))) {
                throw new VerificationFailure("candidate set SHA differs from artifact manifest bytes");
            }
            String markerVersion = marker.get("finalVersion");
            if (markerVersion != null && !version.equals(markerVersion)) {
                throw new VerificationFailure("candidate artifact version differs from marker");
            }
        }

        private static String candidateVersion(Set<String> paths) {
            String prefix = "com/syy/taskflowinsight-parent/";
            List<String> parentPaths = paths.stream()
                    .filter(path -> path.startsWith(prefix))
                    .toList();
            if (parentPaths.size() != 1) {
                throw new VerificationFailure("candidate artifact parent POM is not unique");
            }
            String[] parts = parentPaths.getFirst().split("/", -1);
            if (parts.length != 5
                    || !parts[4].equals("taskflowinsight-parent-" + parts[3] + ".pom")
                    || !FIXED_VERSION.matcher(parts[3]).matches()
                    || "3.0.0".equals(parts[3])) {
                throw new VerificationFailure("candidate artifact version is invalid");
            }
            return parts[3];
        }

        private static List<String> candidateArtifactPaths(String version) {
            List<String> paths = new ArrayList<>();
            paths.add("com/syy/taskflowinsight-parent/" + version
                    + "/taskflowinsight-parent-" + version + ".pom");
            for (String artifact : List.of(
                    "tfi-flow-core", "tfi-flow-spring-starter", "tfi-compare",
                    "tfi-compare-spring-starter", "tfi-ops-spring", "TaskFlowInsight")) {
                String base = "com/syy/" + artifact + "/" + version + "/" + artifact + "-" + version;
                paths.add(base + ".jar");
                paths.add(base + ".pom");
            }
            return List.copyOf(paths);
        }

        private static void validateExpectedReports(
                Path evidence,
                Path expectedReports,
                Map<String, String> files) {
            SealedBytes authority = readSealed(
                    expectedReports, "expected reports", MAX_AUTHORITY_BYTES);
            SealedBytes retainedAuthority = readRelative(
                    evidence,
                    "metadata/expected-reports.tsv",
                    "retained expected reports",
                    MAX_AUTHORITY_BYTES);
            if (!java.util.Arrays.equals(authority.bytes(), retainedAuthority.bytes())) {
                throw new VerificationFailure(
                        "expected reports authority differs from retained bytes");
            }
            List<String[]> rows = parseTable(
                    retainedAuthority,
                    "expected reports",
                    "phase\tmodule\treportPath\tminimumTests\tallowSkipped",
                    5,
                    4096);
            Set<String> paths = new java.util.HashSet<>();
            for (String[] row : rows) {
                requireRelativePosix(row[2], "expected report path");
                int minimum = canonicalNonNegative(row[3], "expected report minimumTests");
                if (minimum == 0
                        || !row[0].matches("[A-Z0-9][A-Z0-9_-]{0,63}")
                        || !row[1].matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                        || !Set.of("true", "false").contains(row[4])
                        || !paths.add(row[2])
                        || !files.containsKey(row[2])) {
                    throw new VerificationFailure("expected report row is invalid, duplicate, or missing");
                }
                SealedBytes report = readRelative(
                        evidence, row[2], "expected test report", MAX_AUTHORITY_BYTES);
                validateTestReport(report, minimum, Boolean.parseBoolean(row[4]));
            }
        }

        private static void validateTestReport(
                SealedBytes report, int minimumTests, boolean allowSkipped) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                factory.setXIncludeAware(false);
                factory.setExpandEntityReferences(false);
                var document = factory.newDocumentBuilder().parse(
                        new ByteArrayInputStream(report.bytes()));
                if (document.getDoctype() != null) {
                    throw new VerificationFailure("expected test report XML is unsafe or malformed");
                }
                Element suite = document.getDocumentElement();
                if (suite == null || !"testsuite".equals(suite.getTagName())) {
                    throw new VerificationFailure("expected test report root must be testsuite");
                }
                int tests = xmlCount(suite, "tests");
                int failures = xmlCount(suite, "failures");
                int errors = xmlCount(suite, "errors");
                int skipped = xmlCount(suite, "skipped");
                if (tests < minimumTests || failures != 0 || errors != 0
                        || (!allowSkipped && skipped != 0) || skipped > tests) {
                    throw new VerificationFailure("expected test report does not satisfy its test gate");
                }
            } catch (VerificationFailure failure) {
                throw failure;
            } catch (ParserConfigurationException | SAXException | IOException | RuntimeException failure) {
                throw new VerificationFailure("expected test report XML is unsafe or malformed");
            }
        }

        private static int xmlCount(Element suite, String attribute) {
            if (!suite.hasAttribute(attribute)) {
                throw new VerificationFailure("expected test report count is missing: " + attribute);
            }
            return canonicalNonNegative(suite.getAttribute(attribute), "expected test report " + attribute);
        }

        private static int canonicalNonNegative(String value, String description) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed < 0 || !Integer.toString(parsed).equals(value)) {
                    throw new NumberFormatException();
                }
                return parsed;
            } catch (NumberFormatException failure) {
                throw new VerificationFailure(description + " must be a canonical non-negative integer");
            }
        }
    }

    /** 候选派生的 runtime inventory 与 SBOM raw-normalized-summary 验证。 */

    private static final class SupplyChain {
        /** SBOM raw JSON 的硬上界。 */
        private static final int MAX_SBOM_BYTES = 16 * 1024 * 1024;
        /** runtime inventory 的唯一 schema。 */
        private static final String RUNTIME_HEADER = "componentPurl\tscope\tgroupId\tartifactId\tversion\t"
                + "classifier\textension\tcomponentArtifactPath\tcomponentArtifactSha256\t"
                + "containingBinaryCoordinate\tcontainingBinaryPath\tcontainingBinarySha256";
        /** normalized component identity 的唯一 schema。 */
        private static final String COMPONENTS_HEADER = "componentPurl\trawComponentIdentitySha256\t"
                + "declaredSpdxExpression\tscope\tcomponentArtifactPath\tcomponentArtifactSha256\t"
                + "containingBinaryCoordinate\tcontainingBinaryPath\tcontainingBinarySha256";
        /** scope 冻结后唯一允许新增或最终定稿的 closed-schema 文件。 */
        private static final Set<String> SECRET_POST_SCOPE_PATHS = Set.of(
                "security/secret-scan/scope.tsv",
                "security/secret-scan/report.json",
                "security/secret-scan/normalized-findings.tsv",
                "security/secret-scan/report-self-scan-scope.tsv",
                "security/secret-scan/report-self-scan.tsv",
                "security/secret-scan/commands.tsv",
                "security/secret-scan/summary.tsv",
                "security/secret-scan/process-attestation.sigstore.json",
                "supply-chain/provenance/artifact-provenance.sigstore.json",
                "metadata/tool-executions.tsv",
                "metadata/release-executions.tsv",
                "metadata/actual-command-ledgers.tsv",
                "metadata/commands.tsv",
                "metadata/report-summary.tsv",
                "architecture/xrt-11/complexity.tsv",
                "metadata/evidence-subject-manifest.tsv",
                "supply-chain/signatures/artifact-signature-results.tsv",
                "supply-chain/provenance/evidence-attestation.sigstore.json",
                "PREPARED",
                "CI_ONLY",
                "evidence-manifest.sha256");

        private SupplyChain() {
        }

        private static void verify(Path evidence, Policy policy, boolean requireAttestations) {
            Map<String, RuntimeArtifact> inventory = loadRuntimeInventory(evidence);
            String format = policy.values().get("sbomFormat");
            String rawPath = "CycloneDX-1.6".equals(format)
                    ? "supply-chain/sbom/bom.cdx.json"
                    : "supply-chain/sbom/bom.spdx.json";
            String forbiddenPath = "CycloneDX-1.6".equals(format)
                    ? "supply-chain/sbom/bom.spdx.json"
                    : "supply-chain/sbom/bom.cdx.json";
            if (Files.exists(evidence.resolve(forbiddenPath), LinkOption.NOFOLLOW_LINKS)) {
                throw new VerificationFailure("evidence contains an unselected SBOM format");
            }
            SealedBytes raw = readRelative(evidence, rawPath, "SBOM JSON", MAX_SBOM_BYTES);
            Object document = StrictJson.parse(raw, "SBOM JSON");
            if (!java.util.Arrays.equals(raw.bytes(), StrictJson.canonicalBytes(document))) {
                throw new VerificationFailure("SBOM JSON must use RFC 8785 canonical bytes");
            }
            Map<String, RawComponent> rawComponents = parseCycloneDx(document, format, inventory);
            validateNormalizedComponents(evidence, rawComponents, inventory);
            validateLicenses(evidence, policy, rawComponents, inventory);
            validateVulnerability(evidence, policy);
            validateSecretEvidence(evidence, policy);
            validateSensitiveLogEvidence(evidence, policy);
            validateSbomSummary(evidence, rawComponents.size(), inventory.size());
            ExecutionEvidence.verify(evidence, policy);
            ArtifactSignatures.verify(evidence, policy);
            SigstoreAttestations.verify(evidence, policy, requireAttestations);
        }

        private static Map<String, RuntimeArtifact> loadRuntimeInventory(Path evidence) {
            SealedBytes file = readRelative(
                    evidence, "metadata/runtime-artifacts.tsv", "runtime artifact inventory", 8 * 1024 * 1024);
            List<String[]> rows = parseTable(file, "runtime artifact inventory", RUNTIME_HEADER, 12, 100_000);
            Map<String, RuntimeArtifact> result = new LinkedHashMap<>();
            for (String[] row : rows) {
                if (!row[0].startsWith("pkg:maven/")
                        || !Set.of("RUNTIME", "BUNDLED").contains(row[1])
                        || !Set.of("-", "jar", "pom").contains(row[6])) {
                    throw new VerificationFailure("runtime artifact identity is invalid");
                }
                SealedBytes artifact = readRelative(
                        evidence, row[7], "runtime artifact", MAX_AUTHORITY_BYTES);
                if (!SHA256.matcher(row[8]).matches() || !artifact.sha256().equals(row[8])) {
                    throw new VerificationFailure("runtime artifact SHA does not match retained bytes");
                }
                boolean runtime = "RUNTIME".equals(row[1]);
                boolean containingAbsent = "-".equals(row[9])
                        && "-".equals(row[10]) && "-".equals(row[11]);
                if (runtime != containingAbsent) {
                    throw new VerificationFailure("runtime containing-binary fields do not match scope");
                }
                if (!runtime) {
                    if ("-".equals(row[9]) || "-".equals(row[10]) || "-".equals(row[11])
                            || !"jar".equals(row[6])) {
                        throw new VerificationFailure("BUNDLED component requires a complete containing binary");
                    }
                    AuthoritySchemas.repositoryPath(row[9]);
                    SealedBytes containing = sealRelative(
                            evidence, row[10], row[11], "BUNDLED containing binary");
                    validateNestedContainment(containing.bytes(), artifact.bytes());
                }
                RuntimeArtifact value = new RuntimeArtifact(
                        row[0], row[1], row[2], row[3], row[4], row[5], row[6], row[7], row[8],
                        row[9], row[10], row[11], artifact.bytes());
                if (result.putIfAbsent(row[0], value) != null) {
                    throw new VerificationFailure("runtime componentPurl must be unique");
                }
            }
            return Map.copyOf(result);
        }

        private static void validateNestedContainment(byte[] containingBinary, byte[] component) {
            Set<String> names = new java.util.HashSet<>();
            int entries = 0;
            int matches = 0;
            try (ZipInputStream input = new ZipInputStream(
                    new ByteArrayInputStream(containingBinary), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    entries++;
                    if (entries > 100_000) {
                        throw new VerificationFailure("BUNDLED containing binary has too many entries");
                    }
                    String validatedName = entry.isDirectory() && entry.getName().endsWith("/")
                            ? entry.getName().substring(0, entry.getName().length() - 1)
                            : entry.getName();
                    requireRelativePosix(validatedName, "BUNDLED containing entry");
                    if (!names.add(entry.getName())) {
                        throw new VerificationFailure("BUNDLED containing binary has duplicate entries");
                    }
                    if (!entry.isDirectory() && entry.getName().endsWith(".jar")) {
                        byte[] candidate = input.readNBytes(component.length + 1);
                        if (candidate.length == component.length
                                && java.util.Arrays.equals(candidate, component)) {
                            matches++;
                        }
                    }
                }
            } catch (VerificationFailure failure) {
                throw failure;
            } catch (IOException failure) {
                throw new VerificationFailure("BUNDLED containing binary is not a readable ZIP");
            }
            if (matches != 1) {
                throw new VerificationFailure(
                        "BUNDLED component bytes must occur exactly once in the containing binary");
            }
        }

        private static Map<String, RawComponent> parseCycloneDx(
                Object document,
                String format,
                Map<String, RuntimeArtifact> inventory) {
            if ("SPDX-2.3".equals(format)) {
                return parseSpdx(document, inventory);
            }
            Map<String, Object> root = StrictJson.object(document, "CycloneDX document");
            if (!"CycloneDX".equals(root.get("bomFormat")) || !"1.6".equals(root.get("specVersion"))) {
                throw new VerificationFailure("CycloneDX identity does not match policy");
            }
            List<Object> components = StrictJson.array(root.get("components"), "CycloneDX components");
            Map<String, RawComponent> result = new LinkedHashMap<>();
            for (Object value : components) {
                Map<String, Object> component = StrictJson.object(value, "CycloneDX component");
                String purl = StrictJson.string(component.get("purl"), "CycloneDX purl");
                RuntimeArtifact runtime = inventory.get(purl);
                if (runtime == null) {
                    throw new VerificationFailure("SBOM contains a component absent from runtime inventory");
                }
                RawComponent raw = rawComponent(component, runtime);
                if (result.putIfAbsent(purl, raw) != null) {
                    throw new VerificationFailure("SBOM componentPurl must be unique");
                }
            }
            return Map.copyOf(result);
        }

        private static Map<String, RawComponent> parseSpdx(
                Object document, Map<String, RuntimeArtifact> inventory) {
            Map<String, Object> root = StrictJson.object(document, "SPDX document");
            if (!"SPDX-2.3".equals(root.get("spdxVersion"))
                    || !"SPDXRef-DOCUMENT".equals(root.get("SPDXID"))
                    || !"CC0-1.0".equals(root.get("dataLicense"))) {
                throw new VerificationFailure("SPDX identity does not match policy");
            }
            List<Object> packages = StrictJson.array(root.get("packages"), "SPDX packages");
            Map<String, RawComponent> result = new LinkedHashMap<>();
            Set<String> spdxIds = new java.util.HashSet<>();
            for (Object value : packages) {
                Map<String, Object> component = StrictJson.object(value, "SPDX package");
                String spdxId = StrictJson.string(component.get("SPDXID"), "SPDX package id");
                if (!spdxId.matches("SPDXRef-[A-Za-z0-9.-]+") || !spdxIds.add(spdxId)) {
                    throw new VerificationFailure("SPDX package id is invalid or duplicate");
                }
                String purl = spdxPurl(component);
                RuntimeArtifact runtime = inventory.get(purl);
                if (runtime == null) {
                    throw new VerificationFailure("SBOM contains a component absent from runtime inventory");
                }
                String declared = StrictJson.string(
                        component.get("licenseDeclared"), "SPDX declared license");
                String concluded = StrictJson.string(
                        component.get("licenseConcluded"), "SPDX concluded license");
                if (!runtime.artifactId().equals(component.get("name"))
                        || !runtime.version().equals(component.get("versionInfo"))
                        || !declared.equals(concluded)
                        || !declared.matches("[A-Za-z0-9][A-Za-z0-9.+()-]{0,127}")
                        || !Boolean.FALSE.equals(component.get("filesAnalyzed"))) {
                    throw new VerificationFailure("SPDX package identity/license differs from runtime inventory");
                }
                validateSpdxChecksum(component, runtime.componentArtifactSha256());
                RawComponent raw = new RawComponent(
                        runtime.purl(),
                        sha256(StrictJson.canonicalBytes(component)),
                        declared,
                        runtime.scope(),
                        runtime.componentArtifactPath(),
                        runtime.componentArtifactSha256(),
                        runtime.containingBinaryCoordinate(),
                        runtime.containingBinaryPath(),
                        runtime.containingBinarySha256());
                if (result.putIfAbsent(purl, raw) != null) {
                    throw new VerificationFailure("SBOM componentPurl must be unique");
                }
            }
            return Map.copyOf(result);
        }

        private static String spdxPurl(Map<String, Object> component) {
            String purl = null;
            for (Object value : StrictJson.array(component.get("externalRefs"), "SPDX external refs")) {
                Map<String, Object> reference = StrictJson.object(value, "SPDX external ref");
                if ("PACKAGE-MANAGER".equals(reference.get("referenceCategory"))
                        && "purl".equals(reference.get("referenceType"))) {
                    if (purl != null) {
                        throw new VerificationFailure("SPDX package must contain exactly one purl ref");
                    }
                    purl = StrictJson.string(
                            reference.get("referenceLocator"), "SPDX purl locator");
                }
            }
            if (purl == null || !purl.startsWith("pkg:maven/")) {
                throw new VerificationFailure("SPDX package must contain exactly one Maven purl ref");
            }
            return purl;
        }

        private static void validateSpdxChecksum(
                Map<String, Object> component, String expectedSha) {
            List<Object> checksums = StrictJson.array(component.get("checksums"), "SPDX checksums");
            int matches = 0;
            for (Object value : checksums) {
                Map<String, Object> checksum = StrictJson.object(value, "SPDX checksum");
                if ("SHA256".equals(checksum.get("algorithm"))) {
                    String digest = StrictJson.string(
                            checksum.get("checksumValue"), "SPDX SHA256 checksum");
                    if (!expectedSha.equals(digest)) {
                        throw new VerificationFailure("SPDX checksum differs from retained artifact bytes");
                    }
                    matches++;
                }
            }
            if (matches != 1) {
                throw new VerificationFailure("SPDX package requires exactly one SHA256 checksum");
            }
        }

        private static RawComponent rawComponent(
                Map<String, Object> component, RuntimeArtifact runtime) {
            if (!runtime.groupId().equals(component.get("group"))
                    || !runtime.artifactId().equals(component.get("name"))
                    || !runtime.version().equals(component.get("version"))) {
                throw new VerificationFailure("SBOM component coordinate differs from runtime inventory");
            }
            List<Object> licenses = StrictJson.array(component.get("licenses"), "CycloneDX licenses");
            if (licenses.size() != 1) {
                throw new VerificationFailure("CycloneDX component requires one declared SPDX expression");
            }
            String expression = StrictJson.string(
                    StrictJson.object(licenses.getFirst(), "CycloneDX license").get("expression"),
                    "CycloneDX SPDX expression");
            Map<String, String> properties = new LinkedHashMap<>();
            for (Object value : StrictJson.array(component.get("properties"), "CycloneDX properties")) {
                Map<String, Object> property = StrictJson.object(value, "CycloneDX property");
                String name = StrictJson.string(property.get("name"), "CycloneDX property name");
                String propertyValue = StrictJson.string(property.get("value"), "CycloneDX property value");
                if (properties.putIfAbsent(name, propertyValue) != null) {
                    throw new VerificationFailure("CycloneDX property name must be unique");
                }
            }
            if (!runtime.scope().equals(properties.get("tfi:scope"))
                    || !runtime.componentArtifactPath().equals(properties.get("tfi:artifactPath"))) {
                throw new VerificationFailure("SBOM component scope/path differs from runtime inventory");
            }
            return new RawComponent(
                    runtime.purl(),
                    sha256(StrictJson.canonicalBytes(component)),
                    expression,
                    runtime.scope(),
                    runtime.componentArtifactPath(),
                    runtime.componentArtifactSha256(),
                    runtime.containingBinaryCoordinate(),
                    runtime.containingBinaryPath(),
                    runtime.containingBinarySha256());
        }

        private static void validateNormalizedComponents(
                Path evidence,
                Map<String, RawComponent> raw,
                Map<String, RuntimeArtifact> inventory) {
            SealedBytes file = readRelative(
                    evidence, "supply-chain/sbom/components.tsv", "SBOM normalized components", 16 * 1024 * 1024);
            List<String> lines = decodeLines(file.bytes(), "SBOM normalized components");
            if (lines.isEmpty() || !COMPONENTS_HEADER.equals(lines.getFirst())) {
                throw new VerificationFailure("SBOM normalized components has an invalid header");
            }
            Map<String, List<String>> normalized = new LinkedHashMap<>();
            for (int index = 1; index < lines.size(); index++) {
                String[] row = lines.get(index).split("\t", -1);
                if (row.length != 9 || normalized.putIfAbsent(row[0], List.of(row)) != null) {
                    throw new VerificationFailure("SBOM normalized component row is duplicate or malformed");
                }
            }
            if (!raw.keySet().equals(inventory.keySet()) || !raw.keySet().equals(normalized.keySet())) {
                throw new VerificationFailure("SBOM raw and normalized component closure differs");
            }
            for (Map.Entry<String, RawComponent> entry : raw.entrySet()) {
                List<String> actual = normalized.get(entry.getKey());
                if (!entry.getValue().columns().equals(actual)) {
                    throw new VerificationFailure("SBOM normalized component identity differs from raw bytes");
                }
            }
        }

        private static void validateSbomSummary(
                Path evidence, int rawComponentCount, int normalizedComponentCount) {
            SealedBytes file = readRelative(
                    evidence, "supply-chain/sbom/summary.tsv", "SBOM summary", 64 * 1024);
            List<String> lines = decodeLines(file.bytes(), "SBOM summary");
            String header = "rawComponentCount\tnormalizedComponentCount\tmissingInventory\t"
                    + "extraInventory\tidentityMismatches\tlicenseMismatches\tanalysisErrors\tstatus";
            if (lines.size() != 2 || !header.equals(lines.getFirst())) {
                throw new VerificationFailure("SBOM summary must contain one exact data row");
            }
            String expected = rawComponentCount + "\t" + normalizedComponentCount
                    + "\t0\t0\t0\t0\t0\tPASS";
            if (!expected.equals(lines.get(1))) {
                throw new VerificationFailure("SBOM summary does not match raw-derived counts");
            }
        }

        private static void validateLicenses(
                Path evidence,
                Policy policy,
                Map<String, RawComponent> rawComponents,
                Map<String, RuntimeArtifact> inventory) {
            Map<String, LicenseRule> rules = licenseRules(policy.authorities().get("licensePolicy"));
            SealedBytes file = readRelative(
                    evidence, "security/license/license-evidence.tsv", "license evidence", 16 * 1024 * 1024);
            List<String[]> rows = parseTable(
                    file,
                    "license evidence",
                    "componentPurl\tscope\tevidenceArtifactPath\tevidenceArtifactSha256\t"
                            + "declaredSpdxExpression\tdetectedSpdxExpression\tlicenseTextSha256\t"
                            + "decision\tnoticeRequired\tevidenceKind\tentryPath\tentrySha256\tstatus",
                    13,
                    100_000);
            Set<String> unique = new java.util.HashSet<>();
            Set<String> licenses = new java.util.HashSet<>();
            Set<String> notices = new java.util.HashSet<>();
            for (String[] row : rows) {
                RuntimeArtifact runtime = inventory.get(row[0]);
                RawComponent raw = rawComponents.get(row[0]);
                if (runtime == null || raw == null || !unique.add(row[0] + "\t" + row[9] + "\t" + row[10])) {
                    throw new VerificationFailure("license evidence component/key is missing or duplicate");
                }
                if (!runtime.scope().equals(row[1])
                        || !runtime.componentArtifactPath().equals(row[2])
                        || !runtime.componentArtifactSha256().equals(row[3])
                        || !raw.declaredSpdx().equals(row[4])) {
                    throw new VerificationFailure("license evidence identity differs from SBOM/runtime inventory");
                }
                byte[] entry = archiveEntry(runtime.artifactBytes(), row[10]);
                String entrySha = sha256(entry);
                if (!entrySha.equals(row[11])) {
                    throw new VerificationFailure("license entry SHA does not match retained artifact bytes");
                }
                if (!"PASS".equals(row[12])) {
                    throw new VerificationFailure("license evidence status must be PASS");
                }
                if ("LICENSE".equals(row[9])) {
                    LicenseRule rule = rules.get(row[4] + "\t" + row[6]);
                    if (rule == null
                            || !"ALLOW".equals(rule.decision())
                            || !row[4].equals(row[5])
                            || !row[6].equals(entrySha)
                            || !rule.noticeRequired().equals(row[8])
                            || !row[7].equals(rule.decision())
                            || !licenseEntryPath(row[10])) {
                        throw new VerificationFailure("license evidence does not match an exact ALLOW policy row");
                    }
                    licenses.add(row[0]);
                } else if ("NOTICE".equals(row[9])) {
                    if (!noticeEntryPath(row[10]) || !"-".equals(row[5]) || !"-".equals(row[6])) {
                        throw new VerificationFailure("NOTICE evidence row is invalid");
                    }
                    notices.add(row[0]);
                } else {
                    throw new VerificationFailure("license evidence kind is unsupported");
                }
            }
            for (RawComponent component : rawComponents.values()) {
                if (!licenses.contains(component.purl())) {
                    throw new VerificationFailure("required LICENSE evidence is missing");
                }
                boolean noticeRequired = rules.values().stream().anyMatch(rule ->
                        rule.expression().equals(component.declaredSpdx())
                                && "true".equals(rule.noticeRequired()));
                if (noticeRequired && !notices.contains(component.purl())) {
                    throw new VerificationFailure("required NOTICE evidence is missing");
                }
            }
            validateLicenseSummary(evidence);
        }

        private static Map<String, LicenseRule> licenseRules(SealedBytes policy) {
            List<String[]> rows = parseTable(
                    policy,
                    "license policy",
                    "spdxExpression\tdecision\tnoticeRequired\tlicenseTextSha256",
                    4,
                    4096);
            Map<String, LicenseRule> result = new LinkedHashMap<>();
            for (String[] row : rows) {
                LicenseRule rule = new LicenseRule(row[0], row[1], row[2], row[3]);
                result.put(row[0] + "\t" + row[3], rule);
            }
            return Map.copyOf(result);
        }

        private static byte[] archiveEntry(byte[] archive, String entryPath) {
            requireRelativePosix(entryPath, "license archive entry");
            Set<String> names = new java.util.HashSet<>();
            try (ZipInputStream input = new ZipInputStream(
                    new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    if (!names.add(entry.getName())) {
                        throw new VerificationFailure("retained artifact contains a duplicate ZIP entry");
                    }
                    if (entryPath.equals(entry.getName()) && !entry.isDirectory()) {
                        byte[] bytes = input.readNBytes(1024 * 1024 + 1);
                        if (bytes.length > 1024 * 1024) {
                            throw new VerificationFailure("license entry exceeds its byte limit");
                        }
                        return bytes;
                    }
                }
            } catch (VerificationFailure failure) {
                throw failure;
            } catch (IOException failure) {
                throw new VerificationFailure("retained license artifact is not a readable ZIP");
            }
            throw new VerificationFailure("license entry is missing from retained artifact");
        }

        private static boolean licenseEntryPath(String value) {
            return evidenceEntryPath(value, "(LICENSE|COPYING)(\\.[A-Za-z0-9._-]+)?");
        }

        private static boolean noticeEntryPath(String value) {
            return evidenceEntryPath(value, "NOTICE(\\.[A-Za-z0-9._-]+)?");
        }

        private static boolean evidenceEntryPath(String value, String basenamePattern) {
            String basename = value.startsWith("META-INF/") ? value.substring("META-INF/".length()) : value;
            return !basename.contains("/") && basename.matches(basenamePattern);
        }

        private static void validateLicenseSummary(Path evidence) {
            SealedBytes summary = readRelative(
                    evidence, "security/license/summary.tsv", "license summary", 64 * 1024);
            List<String> lines = decodeLines(summary.bytes(), "license summary");
            if (lines.size() != 2
                    || !"licenseForbidden\tlicenseUnknown\tlicenseNoticeMissing\tanalysisErrors\tstatus"
                    .equals(lines.getFirst())
                    || !"0\t0\t0\t0\tPASS".equals(lines.get(1))) {
                throw new VerificationFailure("license summary does not match raw-derived PASS counts");
            }
        }

        private static void validateVulnerability(Path evidence, Policy policy) {
            SealedBytes inputs = readRelative(
                    evidence, "security/vulnerability/scan-inputs.tsv",
                    "vulnerability scan inputs", 1024 * 1024);
            List<String[]> rows = parseTable(
                    inputs,
                    "vulnerability scan inputs",
                    "role\tevidencePath\tsha256\tproducedAtUtc\tsourceId\tsnapshotVersion\t"
                            + "snapshotSequence\tsignaturePath\tsignatureSha256\tsignerKeyId",
                    10,
                    4);
            if (rows.size() != 4) {
                throw new VerificationFailure("vulnerability scan inputs must contain exactly four rows");
            }
            List<String> expectedRoles = List.of("CONFIG", "RULES", "DATABASE", "SUPPRESSIONS");
            for (int index = 0; index < expectedRoles.size(); index++) {
                if (!expectedRoles.get(index).equals(rows.get(index)[0])) {
                    throw new VerificationFailure("vulnerability scan input role order is invalid");
                }
            }
            validateSimpleScanInput(evidence, rows.get(0));
            validateSimpleScanInput(evidence, rows.get(1));
            validateDatabaseInput(evidence, policy, rows.get(2));
            if (!java.util.Arrays.equals(
                    rows.get(3),
                    new String[]{"SUPPRESSIONS", "NONE", "-", "-", "-", "-", "-", "-", "-", "-"})) {
                throw new VerificationFailure("unapproved vulnerability suppression authority is present");
            }
            readRelative(evidence, "security/vulnerability/scanner.log", "vulnerability scanner log", 8 * 1024 * 1024);
            validateVulnerabilityReport(evidence, policy);
        }

        private static void validateSimpleScanInput(Path evidence, String[] row) {
            SealedBytes retained = sealRelative(evidence, row[1], row[2], "vulnerability " + row[0]);
            if (retained.bytes().length == 0
                    || java.util.Arrays.stream(row, 3, row.length).anyMatch(value -> !"-".equals(value))) {
                throw new VerificationFailure("vulnerability " + row[0] + " row is invalid");
            }
        }

        private static void validateDatabaseInput(Path evidence, Policy policy, String[] row) {
            SealedBytes manifest = sealRelative(evidence, row[1], row[2], "vulnerability database manifest");
            SealedBytes signature = sealRelative(evidence, row[7], row[8], "vulnerability database signature");
            List<String[]> databaseRows = parseTable(
                    manifest,
                    "vulnerability database manifest",
                    "sourceId\tsnapshotVersion\tsnapshotSequence\tproducedAtUtc\tdatabasePath\tdatabaseSha256",
                    6,
                    100_000);
            String previousPath = null;
            for (String[] database : databaseRows) {
                if (!database[0].equals(row[4])
                        || !database[1].equals(row[5])
                        || !database[2].equals(row[6])
                        || !database[3].equals(row[3])) {
                    throw new VerificationFailure("vulnerability database identity differs from scan input");
                }
                if (previousPath != null && previousPath.compareTo(database[4]) >= 0) {
                    throw new VerificationFailure("vulnerability database paths must be sorted and unique");
                }
                sealRelative(evidence, database[4], database[5], "vulnerability database bytes");
                previousPath = database[4];
            }
            try {
                long sequence = Long.parseLong(row[6]);
                Instant producedAt = Instant.parse(row[3]);
                long maximumAge = Long.parseLong(policy.values().get("vulnerabilityDbMaxAgeHours"));
                Duration age = Duration.between(producedAt, Instant.now());
                if (sequence < 0 || age.isNegative() || age.compareTo(Duration.ofHours(maximumAge)) > 0) {
                    throw new VerificationFailure("vulnerability database is stale");
                }
            } catch (NumberFormatException | DateTimeParseException failure) {
                throw new VerificationFailure("vulnerability database time/sequence is invalid");
            }
            DatabaseTrust trust = databaseTrust(
                    policy, row[4], row[6], row[9]);
            String expectedTestSignature = "TEST_ONLY-SHA256:" + manifest.sha256() + "\n";
            if (policy.values().get("policyId").contains("test-policy")
                    && expectedTestSignature.equals(
                    decodeUtf8(signature.bytes(), "database signature"))) {
                return;
            }
            if (!"SIGSTORE".equals(trust.scheme())) {
                throw new VerificationFailure(
                        "PGP database verification requires a sealed external verifier authority");
            }
            SigstoreAttestations.Trust sigstore = SigstoreAttestations.loadTrustMaterial(
                    trust.base(),
                    new String[]{trust.scheme(), trust.keyId(), trust.materialPath(), trust.materialSha256()},
                    "vulnerability database Sigstore trust material");
            long integratedTime = SigstoreAttestations.verifyDatabaseMessageSignature(
                    signature, manifest, trust.keyId(), sigstore);
            Instant producedAt = Instant.parse(row[3]);
            Instant integratedAt = Instant.ofEpochSecond(integratedTime);
            if (integratedAt.isBefore(producedAt) || integratedAt.isAfter(Instant.now())) {
                throw new VerificationFailure(
                        "vulnerability database signature time does not cover the snapshot");
            }
        }

        private static DatabaseTrust databaseTrust(
                Policy policy, String sourceId, String sequenceValue, String signerKeyId) {
            Path base = policy.authorities().get("productionAuthoritiesManifest").path().getParent();
            List<String> authorityLines = decodeLines(
                    policy.authorities().get("productionAuthoritiesManifest").bytes(),
                    "production authorities manifest");
            String[] authority = authorityLines.get(3).split("\t", -1);
            SealedBytes trust = sealRelative(base, authority[1], authority[2], authority[0]);
            List<String[]> rows = parseTable(
                    trust,
                    "vulnerability database trust manifest",
                    "sourceId\tscheme\tkeyId\tmaterialPath\tmaterialSha256\tminimumSnapshotSequence",
                    6,
                    256);
            long sequence = Long.parseLong(sequenceValue);
            String[] match = rows.stream().filter(row -> row[0].equals(sourceId)
                    && row[2].equals(signerKeyId)
                    && sequence >= Long.parseLong(row[5]))
                    .findFirst()
                    .orElseThrow(() -> new VerificationFailure(
                            "vulnerability database signer/sequence lacks authority"));
            return new DatabaseTrust(match[1], match[2], match[3], match[4], base);
        }

        private static void validateVulnerabilityReport(Path evidence, Policy policy) {
            SealedBytes report = readRelative(
                    evidence, "security/vulnerability/report.json", "vulnerability raw report", 16 * 1024 * 1024);
            Object document = StrictJson.parse(report, "vulnerability raw report");
            if (!java.util.Arrays.equals(report.bytes(), StrictJson.canonicalBytes(document))) {
                throw new VerificationFailure("vulnerability raw report must use canonical JSON bytes");
            }
            Map<String, Object> root = StrictJson.object(document, "vulnerability raw report");
            if (!policy.values().get("vulnerabilityScanner").equals(root.get("scannerIdentity"))
                    || !"PASS".equals(root.get("status"))) {
                throw new VerificationFailure("vulnerability scanner identity/status differs from policy");
            }
            List<Object> analysisErrors = StrictJson.array(
                    root.get("analysisErrors"), "vulnerability analysisErrors");
            List<Object> findings = StrictJson.array(root.get("findings"), "vulnerability findings");
            BigDecimal threshold = new BigDecimal(policy.values().get("vulnerabilityFailCvssThreshold"));
            Set<String> findingIds = new java.util.HashSet<>();
            List<String> normalized = new ArrayList<>();
            normalized.add("findingId\tcve\tgav\tcvss\tclassification\tstatus\t"
                    + "suppressionRowSha256\terrorCode");
            int critical = 0;
            int high = 0;
            int policyViolations = 0;
            int errors = analysisErrors.size();
            int suppressed = 0;
            for (Object value : findings) {
                Map<String, Object> finding = StrictJson.object(value, "vulnerability finding");
                String id = StrictJson.string(finding.get("findingId"), "vulnerability findingId");
                String cve = StrictJson.string(finding.get("cve"), "vulnerability CVE");
                String gav = StrictJson.string(finding.get("gav"), "vulnerability GAV");
                String status = StrictJson.string(finding.get("status"), "vulnerability status");
                if (!findingIds.add(id) || !cve.matches("CVE-[0-9]{4}-[0-9]{4,}")
                        || !gav.matches("[^:\\s]+:[^:\\s]+:[^:\\s]+")
                        || !Set.of("OPEN", "SUPPRESSED").contains(status)
                        || !(finding.get("cvss") instanceof JsonNumber number)) {
                    throw new VerificationFailure("vulnerability raw finding identity is invalid");
                }
                BigDecimal cvss = new BigDecimal(number.canonical());
                String classification;
                if (cvss.compareTo(new BigDecimal("9.0")) >= 0) {
                    classification = "CRITICAL";
                } else if (cvss.compareTo(threshold) >= 0) {
                    classification = "HIGH";
                } else {
                    classification = "BELOW_THRESHOLD";
                }
                if ("SUPPRESSED".equals(status)) {
                    throw new VerificationFailure("vulnerability suppression lacks external authority");
                }
                if ("CRITICAL".equals(classification)) {
                    critical++;
                } else if ("HIGH".equals(classification)) {
                    high++;
                }
                normalized.add(String.join("\t", id, cve, gav, number.canonical(), classification,
                        status, "-", "-"));
            }
            for (int index = 0; index < analysisErrors.size(); index++) {
                StrictJson.string(analysisErrors.get(index), "vulnerability analysis error");
                String id = "ANALYSIS-" + (index + 1);
                normalized.add(id + "\t-\t-\t-\tANALYSIS_ERROR\tANALYSIS_ERROR\t-\tSCANNER_ERROR");
            }
            normalized.subList(1, normalized.size()).sort(String::compareTo);
            SealedBytes normalizedFile = readRelative(
                    evidence, "security/vulnerability/normalized-findings.tsv",
                    "vulnerability normalized findings", 16 * 1024 * 1024);
            byte[] expectedNormalized = (String.join("\n", normalized) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            if (!java.util.Arrays.equals(expectedNormalized, normalizedFile.bytes())) {
                throw new VerificationFailure("vulnerability raw and normalized findings differ");
            }
            String status = critical + high + policyViolations + errors == 0 ? "PASS" : "BLOCKING";
            String summaryRow = String.join("\t",
                    Integer.toString(critical), Integer.toString(high), Integer.toString(policyViolations),
                    Integer.toString(errors), Integer.toString(suppressed), status);
            SealedBytes summary = readRelative(
                    evidence, "security/vulnerability/summary.tsv", "vulnerability summary", 64 * 1024);
            List<String> summaryLines = decodeLines(summary.bytes(), "vulnerability summary");
            String header = "vulnerabilityCritical\tvulnerabilityHigh\tvulnerabilityPolicyViolations\t"
                    + "vulnerabilityAnalysisErrors\tvulnerabilitySuppressed\tstatus";
            if (summaryLines.size() != 2
                    || !header.equals(summaryLines.getFirst())
                    || !summaryRow.equals(summaryLines.get(1))) {
                throw new VerificationFailure("vulnerability summary differs from raw-derived counts");
            }
            if (!"PASS".equals(status)) {
                throw new VerificationFailure("vulnerability evidence contains blocking findings");
            }
        }

        private static void validateSecretEvidence(Path evidence, Policy policy) {
            SealedBytes scope = readRelative(
                    evidence, "security/secret-scan/scope.tsv", "secret first-pass scope", 16 * 1024 * 1024);
            List<String[]> scopeRows = parseTable(
                    scope,
                    "secret first-pass scope",
                    "scopeRoot\trelativePath\tsha256",
                    3,
                    200_000);
            String previous = null;
            Map<String, String> candidateScope = new LinkedHashMap<>();
            Map<String, String> evidenceScope = new LinkedHashMap<>();
            for (String[] row : scopeRows) {
                String key = row[0] + "\t" + row[1];
                if (!Set.of("CANDIDATE_TREE", "EVIDENCE").contains(row[0])
                        || previous != null && SigstoreAttestations.compareUtf8(previous, key) >= 0
                        || !SHA256.matcher(row[2]).matches()) {
                    throw new VerificationFailure("secret first-pass scope must be sorted and unique");
                }
                Map<String, String> target = "CANDIDATE_TREE".equals(row[0])
                        ? candidateScope : evidenceScope;
                if (target.putIfAbsent(row[1], row[2]) != null) {
                    throw new VerificationFailure("secret first-pass scope path is duplicate");
                }
                if ("EVIDENCE".equals(row[0])) {
                    sealRelative(evidence, row[1], row[2], "secret first-pass scoped bytes");
                }
                previous = key;
            }
            validateCandidateSecretScope(evidence, candidateScope);
            validateEvidenceSecretScope(evidence, policy, evidenceScope);
            SealedBytes report = readRelative(
                    evidence, "security/secret-scan/report.json", "secret first-pass report", 16 * 1024 * 1024);
            Object document = StrictJson.parse(report, "secret first-pass report");
            if (!java.util.Arrays.equals(report.bytes(), StrictJson.canonicalBytes(document))) {
                throw new VerificationFailure("secret first-pass report must use canonical JSON bytes");
            }
            Map<String, Object> root = StrictJson.object(document, "secret first-pass report");
            if (!root.keySet().equals(Set.of(
                    "analysisErrors", "findings", "scannerIdentity", "scopeSha256", "status"))) {
                throw new VerificationFailure(
                        "secret first-pass report has unknown or missing JSON keys");
            }
            List<Object> analysisErrors = StrictJson.array(
                    root.get("analysisErrors"), "secret first-pass analysisErrors");
            List<Object> findings = StrictJson.array(root.get("findings"), "secret first-pass findings");
            if (!policy.values().get("secretScanner").equals(root.get("scannerIdentity"))
                    || !scope.sha256().equals(root.get("scopeSha256"))
                    || !"PASS".equals(root.get("status"))) {
                throw new VerificationFailure("secret first-pass identity/scope/status differs from authority");
            }
            if (!analysisErrors.isEmpty()) {
                throw new VerificationFailure("secret first-pass evidence contains analysis errors");
            }
            SealedBytes normalized = readRelative(
                    evidence, "security/secret-scan/normalized-findings.tsv",
                    "secret normalized findings", 16 * 1024 * 1024);
            List<String[]> normalizedRows = parseTableAllowEmpty(
                    normalized,
                    "secret normalized findings",
                    "ruleId\tscopeRoot\trelativePath\tfingerprint\tstatus",
                    5,
                    200_000);
            if (findings.size() != normalizedRows.size()) {
                throw new VerificationFailure("secret raw and normalized finding closure differs");
            }
            if (!findings.isEmpty()) {
                throw new VerificationFailure("secret first-pass evidence contains findings");
            }

            SealedBytes selfScope = readRelative(
                    evidence, "security/secret-scan/report-self-scan-scope.tsv",
                    "secret self-scan scope", 64 * 1024);
            List<String[]> selfRows = parseTable(
                    selfScope,
                    "secret self-scan scope",
                    "scopeRoot\trelativePath\tsha256",
                    3,
                    3);
            List<String> expectedPaths = List.of(
                    "security/secret-scan/normalized-findings.tsv",
                    "security/secret-scan/report.json",
                    "security/secret-scan/scope.tsv");
            if (selfRows.size() != 3) {
                throw new VerificationFailure("secret self-scan scope must contain exactly three rows");
            }
            for (int index = 0; index < expectedPaths.size(); index++) {
                String[] row = selfRows.get(index);
                if (!"EVIDENCE".equals(row[0]) || !expectedPaths.get(index).equals(row[1])) {
                    throw new VerificationFailure("secret self-scan scope path closure differs");
                }
                try {
                    sealRelative(evidence, row[1], row[2], "secret self-scan scope entry");
                } catch (VerificationFailure failure) {
                    throw new VerificationFailure("secret self-scan scope SHA differs from retained bytes");
                }
            }
            SealedBytes selfReport = readRelative(
                    evidence, "security/secret-scan/report-self-scan.tsv",
                    "secret self-scan report", 64 * 1024);
            List<String[]> selfReportRows = parseTable(
                    selfReport,
                    "secret self-scan report",
                    "scannerIdentity\tscopeSha256\tactualExit\tfindings\tanalysisErrors\tstatus",
                    6,
                    1);
            String[] self = selfReportRows.getFirst();
            if (selfReportRows.size() != 1
                    || !policy.values().get("secretScanner").equals(self[0])
                    || !selfScope.sha256().equals(self[1])
                    || !java.util.Arrays.equals(
                    java.util.Arrays.copyOfRange(self, 2, 6), new String[]{"0", "0", "0", "PASS"})) {
                throw new VerificationFailure("secret self-scan result is not an exact zero-finding PASS");
            }
            validateSecretCommandsAndSummary(evidence, policy, scope, report, selfScope, selfReport);
        }

        private static void validateCandidateSecretScope(
                Path evidence, Map<String, String> candidateScope) {
            SealedBytes manifest = readRelative(
                    evidence, "metadata/candidate-tree.tsv",
                    "candidate tree manifest", 16 * 1024 * 1024);
            List<String[]> rows = parseTable(
                    manifest, "candidate tree manifest", "relativePath\tsha256", 2, 200_000);
            Map<String, String> expected = new LinkedHashMap<>();
            String previous = null;
            for (String[] row : rows) {
                requireRelativePosix(row[0], "candidate tree path");
                if (!SHA256.matcher(row[1]).matches()
                        || previous != null
                        && SigstoreAttestations.compareUtf8(previous, row[0]) >= 0
                        || expected.putIfAbsent(row[0], row[1]) != null) {
                    throw new VerificationFailure(
                            "candidate tree manifest must be sorted and unique");
                }
                previous = row[0];
            }
            if (!expected.equals(candidateScope)) {
                throw new VerificationFailure(
                        "secret first-pass CANDIDATE_TREE scope closure differs");
            }
        }

        private static void validateEvidenceSecretScope(
                Path evidence, Policy policy, Map<String, String> evidenceScope) {
            List<Path> paths;
            try (var stream = Files.walk(evidence)) {
                paths = stream.toList();
            } catch (IOException failure) {
                throw new VerificationFailure("secret first-pass evidence tree cannot be enumerated");
            }
            if (paths.size() > 200_000) {
                throw new VerificationFailure("secret first-pass evidence tree exceeds its path limit");
            }
            Map<String, String> required = new LinkedHashMap<>();
            Set<String> postScopePaths = new java.util.HashSet<>(SECRET_POST_SCOPE_PATHS);
            postScopePaths.addAll(ArtifactSignatures.postScopeSidecarPaths(evidence, policy));
            Path normalizedRoot = evidence.toAbsolutePath().normalize();
            for (Path path : paths) {
                BasicFileAttributes attributes;
                try {
                    attributes = Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                } catch (IOException failure) {
                    throw new VerificationFailure("secret first-pass evidence entry cannot be inspected");
                }
                if (attributes.isDirectory()) {
                    continue;
                }
                if (!attributes.isRegularFile()) {
                    throw new VerificationFailure(
                            "secret first-pass evidence tree contains a symlink or special entry");
                }
                String relative = normalizedRoot.relativize(path.toAbsolutePath().normalize())
                        .toString().replace(java.io.File.separatorChar, '/');
                requireRelativePosix(relative, "secret first-pass evidence path");
                if (postScopePaths.contains(relative)) {
                    continue;
                }
                String digest = readSealed(
                        path, "secret first-pass evidence bytes", 256 * 1024 * 1024).sha256();
                required.put(relative, digest);
            }
            for (Map.Entry<String, String> entry : required.entrySet()) {
                if (!entry.getValue().equals(evidenceScope.get(entry.getKey()))) {
                    throw new VerificationFailure(
                            "secret first-pass EVIDENCE scope closure differs");
                }
            }
            if (!required.keySet().containsAll(evidenceScope.keySet().stream()
                    .filter(path -> !postScopePaths.contains(path)).toList())) {
                throw new VerificationFailure("secret first-pass EVIDENCE scope closure differs");
            }
        }

        private static String[] executionRow(Policy policy, String role) {
            List<String[]> rows = parseTable(
                    policy.authorities().get("releaseExecutionPolicy"),
                    "release execution policy",
                    "executionId\trole\tcommandId\tcommandSpecSha256\tconfigPath\tconfigSha256\t"
                            + "rulesPath\trulesSha256\tscopeRule",
                    9,
                    4096);
            return rows.stream().filter(row -> role.equals(row[1])).findFirst()
                    .orElseThrow(() -> new VerificationFailure("release execution role is missing: " + role));
        }

        private static void validateSecretCommandsAndSummary(
                Path evidence,
                Policy policy,
                SealedBytes firstScope,
                SealedBytes firstReport,
                SealedBytes selfScope,
                SealedBytes selfReport) {
            SealedBytes commands = readRelative(
                    evidence, "security/secret-scan/commands.tsv", "secret command ledger", 64 * 1024);
            List<String[]> rows = parseTable(
                    commands,
                    "secret command ledger",
                    "ordinal\tcommandId\texecutionId\tcommandSpecSha256\tscopeSha256\tactualExit\t"
                            + "findings\tanalysisErrors\treportSha256\tstartedAtUtc\tendedAtUtc\tstatus",
                    12,
                    2);
            if (rows.size() != 2) {
                throw new VerificationFailure("secret command ledger must contain FIRST and SELF rows");
            }
            String[] firstAuthority = executionRow(policy, "SECRET_SCAN_FIRST");
            String[] selfAuthority = executionRow(policy, "SECRET_SCAN_SELF");
            validateSecretCommand(rows.get(0), 1, firstAuthority, firstScope.sha256(), firstReport.sha256());
            validateSecretCommand(rows.get(1), 2, selfAuthority, selfScope.sha256(), selfReport.sha256());

            SealedBytes summary = readRelative(
                    evidence, "security/secret-scan/summary.tsv", "secret summary", 64 * 1024);
            List<String> summaryLines = decodeLines(summary.bytes(), "secret summary");
            if (summaryLines.size() != 2
                    || !"firstPassFindings\tsecondPassFindings\tanalysisErrors\tsecretFindings\tstatus"
                    .equals(summaryLines.getFirst())
                    || !"0\t0\t0\t0\tPASS".equals(summaryLines.get(1))) {
                throw new VerificationFailure("secret summary differs from raw two-pass results");
            }
        }

        private static void validateSecretCommand(
                String[] row,
                int ordinal,
                String[] authority,
                String scopeSha,
                String reportSha) {
            if (!Integer.toString(ordinal).equals(row[0])
                    || !authority[2].equals(row[1])
                    || !authority[0].equals(row[2])
                    || !authority[3].equals(row[3])
                    || !scopeSha.equals(row[4])
                    || !reportSha.equals(row[8])
                    || !java.util.Arrays.equals(
                    java.util.Arrays.copyOfRange(row, 5, 8), new String[]{"0", "0", "0"})
                    || !"PASS".equals(row[11])) {
                throw new VerificationFailure("secret command row differs from authority/raw evidence");
            }
            try {
                Instant started = Instant.parse(row[9]);
                Instant ended = Instant.parse(row[10]);
                if (started.isAfter(ended)) {
                    throw new VerificationFailure("secret command timestamps are reversed");
                }
            } catch (DateTimeParseException failure) {
                throw new VerificationFailure("secret command timestamp is invalid");
            }
        }

        private static void validateSensitiveLogEvidence(Path evidence, Policy policy) {
            String[] authority = executionRow(policy, "SENSITIVE_LOG_SCAN");
            Path policyBase = policy.authorities().get("releaseExecutionPolicy").path().getParent();
            SealedBytes coverage = sealRelative(
                    policyBase, authority[6], authority[7], "sensitive-log coverage");
            List<String[]> coverageRows = parseTable(
                    coverage,
                    "sensitive-log coverage",
                    "canaryId\tcanaryKind\tsinkKind\tinjectionDriverId",
                    4,
                    77);
            Set<String> canaryKinds = Set.of(
                    "BEFORE_VALUE", "AFTER_VALUE", "CREDENTIAL", "TOKEN", "PII", "ENTITY_KEY", "STORE_KEY");
            Set<String> sinkKinds = Set.of(
                    "APPLICATION_LOG", "MAVEN_LOG", "EXCEPTION", "METER", "ACTUATOR", "SUREFIRE",
                    "FAILSAFE", "DEPENDENCY_TREE", "JSON", "TSV", "ARTIFACT");
            Map<String, String[]> coverageById = new LinkedHashMap<>();
            Set<String> combinations = new java.util.HashSet<>();
            for (String[] row : coverageRows) {
                if (!row[0].matches("[A-Z0-9][A-Z0-9_-]{0,63}")
                        || !row[3].matches("[A-Z0-9][A-Z0-9_-]{0,127}")
                        || !canaryKinds.contains(row[1]) || !sinkKinds.contains(row[2])
                        || coverageById.putIfAbsent(row[0], row) != null
                        || !combinations.add(row[1] + "\t" + row[2])) {
                    throw new VerificationFailure("sensitive-log coverage identity is invalid or duplicate");
                }
            }
            if (coverageRows.size() != 77 || combinations.size() != 77) {
                throw new VerificationFailure("sensitive-log coverage must be the exact 7 x 11 closure");
            }

            SealedBytes receiptsFile = readRelative(
                    evidence, "security/sensitive-log/injection-receipts.tsv",
                    "sensitive-log injection receipts", 8 * 1024 * 1024);
            List<String[]> receipts = parseTable(
                    receiptsFile,
                    "sensitive-log injection receipts",
                    "canaryId\tcanaryKind\tsinkKind\tinjectionDriverId\tcanarySha256\t"
                            + "evidencePath\tevidenceSha256\tinjectionStatus",
                    8,
                    77);
            Map<String, String[]> receiptsById = new LinkedHashMap<>();
            Map<String, String> retainedPaths = new java.util.HashMap<>();
            for (String[] row : receipts) {
                String[] expected = coverageById.get(row[0]);
                if (expected == null
                        || !java.util.Arrays.equals(
                        java.util.Arrays.copyOfRange(row, 0, 4), expected)
                        || !SHA256.matcher(row[4]).matches()
                        || !"INJECTED".equals(row[7])
                        || receiptsById.putIfAbsent(row[0], row) != null) {
                    throw new VerificationFailure("sensitive-log receipt differs from coverage or injection failed");
                }
                sealEvidenceOnce(
                        evidence, row[5], row[6], "sensitive-log receipt evidence", retainedPaths);
            }
            if (!coverageById.keySet().equals(receiptsById.keySet())) {
                throw new VerificationFailure("sensitive-log coverage and receipt closure differs");
            }

            SealedBytes scope = readRelative(
                    evidence, "security/sensitive-log/scope.tsv", "sensitive-log scope", 1024 * 1024);
            List<String[]> scopeRows = parseTable(
                    scope,
                    "sensitive-log scope",
                    "sinkKind\tevidencePath\tsha256",
                    3,
                    11);
            Set<String> scopedSinks = new java.util.HashSet<>();
            for (String[] row : scopeRows) {
                if (!sinkKinds.contains(row[0]) || !scopedSinks.add(row[0])) {
                    throw new VerificationFailure("sensitive-log scope sink is invalid or duplicate");
                }
                sealRelative(evidence, row[1], row[2], "sensitive-log scoped bytes");
                boolean receiptMatch = receipts.stream().anyMatch(receipt ->
                        receipt[2].equals(row[0]) && receipt[5].equals(row[1]) && receipt[6].equals(row[2]));
                if (!receiptMatch) {
                    throw new VerificationFailure("sensitive-log scope is not covered by a receipt");
                }
            }
            if (!scopedSinks.equals(sinkKinds)) {
                throw new VerificationFailure("sensitive-log scope must contain all 11 sinks");
            }

            SealedBytes rawResult = readRelative(
                    evidence, "security/sensitive-log/raw-result.tsv", "sensitive-log raw result", 64 * 1024);
            List<String[]> resultRows = parseTable(
                    rawResult,
                    "sensitive-log raw result",
                    "scannerIdentity\texecutionId\tcoverageSha256\tscopeSha256\tcanarySetSha256\t"
                            + "actualExit\tfindings\tanalysisErrors\tstatus",
                    9,
                    1);
            String[] result = resultRows.getFirst();
            if (resultRows.size() != 1
                    || !policy.values().get("secretScanner").equals(result[0])
                    || !authority[0].equals(result[1])
                    || !coverage.sha256().equals(result[2])
                    || !scope.sha256().equals(result[3])
                    || !canarySetSha(receipts).equals(result[4])) {
                throw new VerificationFailure("sensitive-log raw result identity differs from retained closure");
            }
            if (!java.util.Arrays.equals(
                    java.util.Arrays.copyOfRange(result, 5, 9), new String[]{"0", "0", "0", "PASS"})) {
                throw new VerificationFailure("sensitive-log raw result contains findings or errors");
            }
            validateSensitiveLogFindingsAndSummary(evidence);
        }

        private static String canarySetSha(List<String[]> receipts) {
            List<String> rows = receipts.stream()
                    .map(row -> row[0] + "\t" + row[4])
                    .sorted()
                    .toList();
            return sha256((String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8));
        }

        private static void sealEvidenceOnce(
                Path evidence,
                String relative,
                String digest,
                String description,
                Map<String, String> sealedPaths) {
            String previous = sealedPaths.putIfAbsent(relative, digest);
            if (previous == null) {
                sealRelative(evidence, relative, digest, description);
            } else if (!previous.equals(digest)) {
                throw new VerificationFailure(description + " has conflicting hashes");
            }
        }

        private static void validateSensitiveLogFindingsAndSummary(Path evidence) {
            SealedBytes findings = readRelative(
                    evidence, "security/sensitive-log/findings.tsv",
                    "sensitive-log findings", 8 * 1024 * 1024);
            List<String[]> findingRows = parseTableAllowEmpty(
                    findings,
                    "sensitive-log findings",
                    "canaryId\tsinkKind\tevidencePath\tfingerprint\tstatus",
                    5,
                    100_000);
            if (!findingRows.isEmpty()) {
                throw new VerificationFailure("sensitive-log findings are blocking");
            }
            SealedBytes summary = readRelative(
                    evidence, "security/sensitive-log/summary.tsv", "sensitive-log summary", 64 * 1024);
            List<String> lines = decodeLines(summary.bytes(), "sensitive-log summary");
            if (lines.size() != 2
                    || !"sensitiveLogFindings\tanalysisErrors\tstatus".equals(lines.getFirst())
                    || !"0\t0\tPASS".equals(lines.get(1))) {
                throw new VerificationFailure("sensitive-log summary differs from raw-derived counts");
            }
        }

        /**
         * Release owner 预先批准或拒绝的 exact license identity。
         *
         * @param expression canonical SPDX expression
         * @param decision ALLOW 或 DENY
         * @param noticeRequired 是否必须从实际分发物读取 NOTICE
         * @param textSha256 approved LICENSE bytes SHA 或 dash
         */
        private record LicenseRule(
                String expression, String decision, String noticeRequired, String textSha256) {
        }

        /**
         * Runtime component identity retained by 05A.
         *
         * @param purl canonical Maven Package URL
         * @param scope RUNTIME 或 BUNDLED
         * @param groupId Maven groupId
         * @param artifactId Maven artifactId
         * @param version fixed component version
         * @param classifier classifier 或 dash
         * @param extension retained artifact extension
         * @param componentArtifactPath evidence-relative component bytes
         * @param componentArtifactSha256 component exact bytes SHA-256
         * @param containingBinaryCoordinate BUNDLED 所属发布 binary 或 dash
         * @param containingBinaryPath BUNDLED 所属发布路径或 dash
         * @param containingBinarySha256 BUNDLED 所属发布 bytes SHA-256 或 dash
         * @param artifactBytes 本轮单次读取并通过 inventory SHA 的 component bytes
         */
        private record RuntimeArtifact(
                String purl, String scope, String groupId, String artifactId, String version,
                String classifier, String extension, String componentArtifactPath,
                String componentArtifactSha256, String containingBinaryCoordinate,
                String containingBinaryPath, String containingBinarySha256, byte[] artifactBytes) {
        }

        /** normalized row is derived only from raw component and retained inventory bytes. */
        private record RawComponent(
                String purl,
                String rawIdentitySha,
                String declaredSpdx,
                String scope,
                String artifactPath,
                String artifactSha,
                String containingCoordinate,
                String containingPath,
                String containingSha) {

            private List<String> columns() {
                return List.of(
                        purl, rawIdentitySha, declaredSpdx, scope, artifactPath, artifactSha,
                        containingCoordinate, containingPath, containingSha);
            }
        }

        /**
         * @param scheme policy-pinned database signature scheme
         * @param keyId exact signer identity from the database trust manifest
         * @param materialPath policy-relative certificate/Rekor material path
         * @param materialSha256 exact retained trust material SHA-256
         * @param base production authority base used for relative material resolution
         */
        private record DatabaseTrust(
                String scheme,
                String keyId,
                String materialPath,
                String materialSha256,
                Path base) {
        }
    }

    /** 有界 JSON AST 与 RFC 8785 canonical byte 生成器。 */
    private static final class StrictJson {
        private StrictJson() {
        }

        private static Object parse(SealedBytes input, String description) {
            return new JsonParser(decodeUtf8(input.bytes(), description), description).parse();
        }

        private static byte[] canonicalBytes(Object value) {
            StringBuilder output = new StringBuilder();
            appendCanonical(value, output);
            return output.toString().getBytes(StandardCharsets.UTF_8);
        }

        @SuppressWarnings("unchecked")
        private static void appendCanonical(Object value, StringBuilder output) {
            if (value == null) {
                output.append("null");
            } else if (value instanceof String text) {
                appendString(text, output);
            } else if (value instanceof Boolean bool) {
                output.append(bool);
            } else if (value instanceof JsonNumber number) {
                output.append(number.canonical());
            } else if (value instanceof List<?> list) {
                output.append('[');
                for (int index = 0; index < list.size(); index++) {
                    if (index > 0) {
                        output.append(',');
                    }
                    appendCanonical(list.get(index), output);
                }
                output.append(']');
            } else if (value instanceof Map<?, ?> raw) {
                Map<String, Object> object = (Map<String, Object>) raw;
                List<String> keys = new ArrayList<>(object.keySet());
                keys.sort(String::compareTo);
                output.append('{');
                for (int index = 0; index < keys.size(); index++) {
                    if (index > 0) {
                        output.append(',');
                    }
                    String key = keys.get(index);
                    appendString(key, output);
                    output.append(':');
                    appendCanonical(object.get(key), output);
                }
                output.append('}');
            } else {
                throw new VerificationFailure("JSON AST contains an unsupported value");
            }
        }

        private static void appendString(String value, StringBuilder output) {
            output.append('"');
            for (int offset = 0; offset < value.length(); ) {
                int codePoint = value.codePointAt(offset);
                offset += Character.charCount(codePoint);
                switch (codePoint) {
                    case '"' -> output.append("\\\"");
                    case '\\' -> output.append("\\\\");
                    case '\b' -> output.append("\\b");
                    case '\t' -> output.append("\\t");
                    case '\n' -> output.append("\\n");
                    case '\f' -> output.append("\\f");
                    case '\r' -> output.append("\\r");
                    default -> {
                        if (codePoint < 0x20) {
                            output.append(String.format("\\u%04x", codePoint));
                        } else {
                            output.appendCodePoint(codePoint);
                        }
                    }
                }
            }
            output.append('"');
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> object(Object value, String description) {
            if (!(value instanceof Map<?, ?>)) {
                throw new VerificationFailure(description + " must be a JSON object");
            }
            return (Map<String, Object>) value;
        }

        @SuppressWarnings("unchecked")
        private static List<Object> array(Object value, String description) {
            if (!(value instanceof List<?>)) {
                throw new VerificationFailure(description + " must be a JSON array");
            }
            return (List<Object>) value;
        }

        private static String string(Object value, String description) {
            if (!(value instanceof String text) || text.isEmpty()) {
                throw new VerificationFailure(description + " must be a non-empty JSON string");
            }
            return text;
        }
    }

    /** JSON number retains its canonicalized numeric identity rather than its input spelling. */
    private record JsonNumber(String canonical) {
    }

    /** 严格、有界且拒绝 duplicate key 的 JSON parser。 */
    private static final class JsonParser {
        /** 防止恶意 SBOM 通过深层数组/对象耗尽调用栈。 */
        private static final int MAX_DEPTH = 64;
        /** 单份 JSON 的最大 value 节点数。 */
        private static final int MAX_NODES = 100_000;
        /** 单个 JSON string 解码后的最大 UTF-16 单元数。 */
        private static final int MAX_STRING_CHARS = 1_000_000;

        /** 待解析且已经通过严格 UTF-8 解码的文本。 */
        private final String input;
        /** 稳定错误分类使用的输入角色名。 */
        private final String description;
        /** 下一个尚未消费的 UTF-16 offset。 */
        private int index;
        /** 当前已接受的 JSON value 节点数。 */
        private int nodes;

        private JsonParser(String input, String description) {
            this.input = input;
            this.description = description;
        }

        private Object parse() {
            skipWhitespace();
            Object value = parseValue(0);
            skipWhitespace();
            if (index != input.length()) {
                fail("contains trailing data");
            }
            return value;
        }

        private Object parseValue(int depth) {
            if (depth > MAX_DEPTH || ++nodes > MAX_NODES || index >= input.length()) {
                fail("exceeds structural limits or ends unexpectedly");
            }
            return switch (input.charAt(index)) {
                case '{' -> parseObject(depth + 1);
                case '[' -> parseArray(depth + 1);
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject(int depth) {
            index++;
            skipWhitespace();
            Map<String, Object> result = new LinkedHashMap<>();
            if (consume('}')) {
                return result;
            }
            while (true) {
                if (index >= input.length() || input.charAt(index) != '"') {
                    fail("object key must be a string");
                }
                String key = parseString();
                skipWhitespace();
                require(':');
                skipWhitespace();
                Object value = parseValue(depth);
                if (result.containsKey(key)) {
                    fail("contains a duplicate object key");
                }
                result.put(key, value);
                skipWhitespace();
                if (consume('}')) {
                    return result;
                }
                require(',');
                skipWhitespace();
            }
        }

        private List<Object> parseArray(int depth) {
            index++;
            skipWhitespace();
            List<Object> result = new ArrayList<>();
            if (consume(']')) {
                return result;
            }
            while (true) {
                result.add(parseValue(depth));
                skipWhitespace();
                if (consume(']')) {
                    return result;
                }
                require(',');
                skipWhitespace();
            }
        }

        private String parseString() {
            require('"');
            StringBuilder result = new StringBuilder();
            while (index < input.length()) {
                char character = input.charAt(index++);
                if (character == '"') {
                    if (result.length() > MAX_STRING_CHARS) {
                        fail("contains an overlong string");
                    }
                    return result.toString();
                }
                if (character < 0x20) {
                    fail("contains an unescaped control character");
                }
                if (character != '\\') {
                    if (Character.isSurrogate(character)) {
                        fail("contains an unpaired surrogate");
                    }
                    result.append(character);
                    continue;
                }
                if (index >= input.length()) {
                    fail("ends in a string escape");
                }
                char escaped = input.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> result.append(escaped);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> appendUnicodeEscape(result);
                    default -> fail("contains an invalid string escape");
                }
            }
            fail("contains an unterminated string");
            return "";
        }

        private void appendUnicodeEscape(StringBuilder target) {
            char first = readHexCodeUnit();
            if (Character.isLowSurrogate(first)) {
                fail("contains an unpaired low surrogate");
            }
            if (Character.isHighSurrogate(first)) {
                if (index + 2 > input.length() || input.charAt(index) != '\\'
                        || input.charAt(index + 1) != 'u') {
                    fail("contains an unpaired high surrogate");
                }
                index += 2;
                char second = readHexCodeUnit();
                if (!Character.isLowSurrogate(second)) {
                    fail("contains an invalid surrogate pair");
                }
                target.append(first).append(second);
            } else {
                target.append(first);
            }
        }

        private char readHexCodeUnit() {
            if (index + 4 > input.length()) {
                fail("contains a truncated unicode escape");
            }
            int value = 0;
            for (int count = 0; count < 4; count++) {
                int digit = Character.digit(input.charAt(index++), 16);
                if (digit < 0) {
                    fail("contains an invalid unicode escape");
                }
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private JsonNumber parseNumber() {
            int start = index;
            consume('-');
            if (index >= input.length()) {
                fail("contains a truncated number");
            }
            if (consume('0')) {
                if (index < input.length() && Character.isDigit(input.charAt(index))) {
                    fail("contains a leading-zero number");
                }
            } else {
                requireDigits();
            }
            if (consume('.')) {
                requireDigits();
            }
            if (index < input.length() && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
                index++;
                if (index < input.length() && (input.charAt(index) == '+' || input.charAt(index) == '-')) {
                    index++;
                }
                requireDigits();
            }
            String raw = input.substring(start, index);
            try {
                double value = Double.parseDouble(raw);
                if (!Double.isFinite(value)) {
                    throw new NumberFormatException();
                }
                return new JsonNumber(canonicalNumber(value));
            } catch (NumberFormatException failure) {
                fail("contains an invalid or non-finite number");
                return null;
            }
        }

        private static String canonicalNumber(double value) {
            if (value == 0.0d) {
                return "0";
            }
            BigDecimal decimal = BigDecimal.valueOf(value).stripTrailingZeros();
            int exponent = decimal.precision() - decimal.scale() - 1;
            if (exponent >= -6 && exponent < 21) {
                return decimal.toPlainString();
            }
            String sign = decimal.signum() < 0 ? "-" : "";
            String digits = decimal.unscaledValue().abs().toString();
            String mantissa = digits.length() == 1
                    ? digits
                    : digits.charAt(0) + "." + digits.substring(1);
            return sign + mantissa + "e" + (exponent >= 0 ? "+" : "") + exponent;
        }

        private void requireDigits() {
            int start = index;
            while (index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }
            if (start == index) {
                fail("number requires digits");
            }
        }

        private Object parseLiteral(String literal, Object value) {
            if (!input.startsWith(literal, index)) {
                fail("contains an invalid literal");
            }
            index += literal.length();
            return value;
        }

        private boolean consume(char expected) {
            if (index < input.length() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void require(char expected) {
            if (!consume(expected)) {
                fail("expected '" + expected + "'");
            }
        }

        private void skipWhitespace() {
            while (index < input.length()
                    && Set.of(' ', '\t', '\n', '\r').contains(input.charAt(index))) {
                index++;
            }
        }

        private void fail(String detail) {
            throw new VerificationFailure(description + " " + detail);
        }
    }

    /**
     * 单次读取的 authority bytes 及其身份。
     *
     * @param path NOFOLLOW 解析后的绝对路径
     * @param bytes 本轮唯一读取的原始 bytes
     * @param sha256 原始 bytes 的 lowercase SHA-256
     */
    private record SealedBytes(Path path, byte[] bytes, String sha256) {
    }

    private static Path requireReadableFile(Path input, String description) {
        Path path = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(path)) {
            throw new VerificationFailure(description + " is not a readable regular file");
        }
        return path;
    }

    private static Path requireReadableDirectory(Path input, String description) {
        Path path = input.toAbsolutePath().normalize();
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(path)) {
            throw new VerificationFailure(description + " is not a readable directory");
        }
        return path;
    }

    /** 预期输入错误不采集堆栈，避免泄漏宿主路径或扫描内容。 */
    private static final class VerificationFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private VerificationFailure(String message) {
            super(message, null, false, false);
        }
    }

    /** CLI 调用格式错误；与证据内容验证失败使用不同退出码。 */
    private static final class UsageFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private UsageFailure(String message) {
            super(message, null, false, false);
        }
    }
}
