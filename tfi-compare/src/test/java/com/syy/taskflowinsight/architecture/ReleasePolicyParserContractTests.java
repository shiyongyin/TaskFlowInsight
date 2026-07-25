package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 生产发布 policy 与 authority parser 的公开 CLI 合同。 */
class ReleasePolicyParserContractTests {

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

    @TempDir
    Path temporaryDirectory;

    @Test
    void cliRejectsUnknownModeWrongArityAndMissingPolicy() throws Exception {
        ProcessResult unknown = runVerifier("inspect", "unused");
        ProcessResult wrongArity = runVerifier("verify-policy");
        ProcessResult missing = runVerifier(
                "verify-policy", temporaryDirectory.resolve("missing-policy.tsv").toString());

        assertThat(unknown.exitCode()).isNotZero();
        assertThat(wrongArity.exitCode()).isNotZero();
        assertThat(missing.exitCode()).isNotZero();
        assertThat(unknown.output()).contains("unknown mode: inspect");
        assertThat(wrongArity.output()).contains("Usage: ReleaseEvidenceVerifier");
        assertThat(missing.output()).contains("production policy is not a readable regular file");
        assertThat(unknown.output()).doesNotContain("Exception", "\tat ");
    }

    @Test
    void validInternalPolicyIsAccepted() throws Exception {
        Path policy = writePolicy("valid", Map.of());

        ProcessResult result = runVerifier("verify-policy", policy.toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("POLICY_OK");
    }

    @Test
    void policyRejectsWrongOrderMutableVersionTraversalAndDigestMismatch() throws Exception {
        Path wrongOrder = writePolicy("wrong-order", Map.of());
        List<String> reordered = Files.readAllLines(wrongOrder, StandardCharsets.UTF_8);
        String first = reordered.get(0);
        reordered.set(0, reordered.get(1));
        reordered.set(1, first);
        Files.write(wrongOrder, reordered, StandardCharsets.UTF_8);

        ProcessResult orderResult = runVerifier("verify-policy", wrongOrder.toString());
        ProcessResult mutableResult = runVerifier("verify-policy", writePolicy(
                "mutable", Map.of("finalVersion", "4.0.0-SNAPSHOT")).toString());
        ProcessResult traversalResult = runVerifier("verify-policy", writePolicy(
                "traversal", Map.of("publishArtifactManifest", "../publish.tsv")).toString());
        ProcessResult digestResult = runVerifier("verify-policy", writePolicy(
                "digest", Map.of("publishArtifactManifestSha256", "0".repeat(64))).toString());

        assertThat(orderResult.exitCode()).isNotZero();
        assertThat(orderResult.output()).contains("policy key at line 1 must be policyId");
        assertThat(mutableResult.exitCode()).isNotZero();
        assertThat(mutableResult.output()).contains("finalVersion must be a fixed release version");
        assertThat(traversalResult.exitCode()).isNotZero();
        assertThat(traversalResult.output()).contains("publishArtifactManifest must be a relative POSIX path");
        assertThat(digestResult.exitCode()).isNotZero();
        assertThat(digestResult.output()).contains("publishArtifactManifestSha256 does not match retained bytes");
    }

    @Test
    void productionAuthoritiesRejectWrongOrderMissingRequiredRootAndSymlink() throws Exception {
        Path wrongOrder = writePolicy("authority-order", Map.of());
        List<String> wrongOrderLines = authorityLines(wrongOrder);
        String first = wrongOrderLines.get(0);
        wrongOrderLines.set(0, wrongOrderLines.get(1));
        wrongOrderLines.set(1, first);
        replaceAuthorityManifest(wrongOrder, wrongOrderLines);

        Path missingRoot = writePolicy("authority-missing", Map.of());
        List<String> missingRootLines = authorityLines(missingRoot);
        missingRootLines.set(0, "PROVENANCE_TRUST_ROOT\tNONE\t-");
        replaceAuthorityManifest(missingRoot, missingRootLines);

        Path symbolic = writePolicy("authority-symbolic", Map.of());
        Path trustDirectory = symbolic.getParent().resolve("trust");
        Files.delete(trustDirectory.resolve("provenance.tsv"));
        Files.createSymbolicLink(
                trustDirectory.resolve("provenance.tsv"), Path.of("vulnerability-database.tsv"));

        ProcessResult orderResult = runVerifier("verify-policy", wrongOrder.toString());
        ProcessResult missingResult = runVerifier("verify-policy", missingRoot.toString());
        ProcessResult symbolicResult = runVerifier("verify-policy", symbolic.toString());

        assertThat(orderResult.exitCode()).isNotZero();
        assertThat(orderResult.output())
                .contains("production authorities key at line 1 must be PROVENANCE_TRUST_ROOT");
        assertThat(missingResult.exitCode()).isNotZero();
        assertThat(missingResult.output())
                .contains("PROVENANCE_TRUST_ROOT must reference retained trust material");
        assertThat(symbolicResult.exitCode()).isNotZero();
        assertThat(symbolicResult.output())
                .contains("PROVENANCE_TRUST_ROOT must not traverse a symbolic link");
    }

    @Test
    void referencedSchemasRejectWrongHeaderDuplicateLicenseAndToolchainDigestDrift() throws Exception {
        Path wrongHeader = writePolicy("schema-header", Map.of());
        replaceReference(wrongHeader, "publishArtifactManifest", List.of(
                "wrong\theader",
                "1\t-\tcom.syy:taskflowinsight-parent:pom:4.0.0\t"
                        + "com/syy/taskflowinsight-parent/4.0.0/taskflowinsight-parent-4.0.0.pom\tPOM\t-"));

        Path duplicateLicense = writePolicy("schema-license", Map.of());
        List<String> licenseLines = referenceLines(duplicateLicense, "licensePolicy");
        licenseLines.add(licenseLines.get(1));
        replaceReference(duplicateLicense, "licensePolicy", licenseLines);

        Path toolchainDrift = writePolicy("schema-toolchain", Map.of());
        List<String> toolchainLines = referenceLines(toolchainDrift, "buildToolchainManifest");
        String[] toolchainRow = toolchainLines.get(1).split("\t", -1);
        toolchainRow[4] = "0".repeat(64);
        toolchainLines.set(1, String.join("\t", toolchainRow));
        replaceReference(toolchainDrift, "buildToolchainManifest", toolchainLines);

        ProcessResult headerResult = runVerifier("verify-policy", wrongHeader.toString());
        ProcessResult licenseResult = runVerifier("verify-policy", duplicateLicense.toString());
        ProcessResult toolchainResult = runVerifier("verify-policy", toolchainDrift.toString());

        assertThat(headerResult.exitCode()).isNotZero();
        assertThat(headerResult.output()).contains("publish artifact manifest has an invalid header");
        assertThat(licenseResult.exitCode()).isNotZero();
        assertThat(licenseResult.output()).contains("license policy keys must be sorted and unique");
        assertThat(toolchainResult.exitCode()).isNotZero();
        assertThat(toolchainResult.output()).contains("build toolchain component SHA does not match retained bytes");
    }

    @Test
    void authorityClosuresRejectMissingPrimaryWorkloadDuplicateEdgeAndWrongScope() throws Exception {
        Path missingPrimary = writePolicy("closure-primary", Map.of());
        List<String> publishLines = referenceLines(missingPrimary, "publishArtifactManifest");
        publishLines.removeLast();
        publishLines.removeLast();
        publishLines.remove(25);
        renumberOrdinals(publishLines);
        replaceReference(missingPrimary, "publishArtifactManifest", publishLines);

        Path missingWorkload = writePolicy("closure-performance", Map.of());
        List<String> performanceLines = referenceLines(missingWorkload, "runtimePerformancePolicy");
        performanceLines.removeLast();
        replaceReference(missingWorkload, "runtimePerformancePolicy", performanceLines);

        Path duplicateEdge = writePolicy("closure-compatibility", Map.of());
        List<String> compatibilityLines = referenceLines(duplicateEdge, "compatibilityMatrix");
        compatibilityLines.add(compatibilityLines.get(1));
        replaceReference(duplicateEdge, "compatibilityMatrix", compatibilityLines);

        Path wrongScope = writePolicy("closure-execution", Map.of());
        List<String> executionLines = referenceLines(wrongScope, "releaseExecutionPolicy");
        String[] execution = executionLines.getLast().split("\t", -1);
        execution[8] = "SECRET_REPORT_ONLY";
        executionLines.set(executionLines.size() - 1, String.join("\t", execution));
        replaceReference(wrongScope, "releaseExecutionPolicy", executionLines);

        ProcessResult primaryResult = runVerifier("verify-policy", missingPrimary.toString());
        ProcessResult workloadResult = runVerifier("verify-policy", missingWorkload.toString());
        ProcessResult edgeResult = runVerifier("verify-policy", duplicateEdge.toString());
        ProcessResult scopeResult = runVerifier("verify-policy", wrongScope.toString());

        assertThat(primaryResult.exitCode()).isNotZero();
        assertThat(primaryResult.output()).contains("publish primary closure is incomplete");
        assertThat(workloadResult.exitCode()).isNotZero();
        assertThat(workloadResult.output()).contains("performance policy must contain exactly 21 workloads");
        assertThat(edgeResult.exitCode()).isNotZero();
        assertThat(edgeResult.output()).contains("compatibility matrix keys must be unique");
        assertThat(scopeResult.exitCode()).isNotZero();
        assertThat(scopeResult.output()).contains("release execution role and scopeRule do not match");
    }

    private Path writePolicy(String directoryName, Map<String, String> overrides) throws Exception {
        return writePolicyFixture(temporaryDirectory.resolve(directoryName), overrides);
    }

    static Path writePolicyFixture(Path directory, Map<String, String> overrides) throws Exception {
        Files.createDirectory(directory);
        Map<String, String> references = Map.of(
                "publishArtifactManifest", "publish.tsv",
                "runtimePerformancePolicy", "performance.tsv",
                "compatibilityMatrix", "compatibility.tsv",
                "releaseExecutionPolicy", "executions.tsv",
                "buildToolchainManifest", "toolchain.tsv",
                "productionAuthoritiesManifest", "authorities.tsv",
                "licensePolicy", "licenses.tsv");
        Map<String, String> values = new HashMap<>();
        values.put("policyId", "authority:test-policy");
        values.put("reviewAssignmentId", "assignment:test-review");
        values.put("repository", "https://github.com/shiyongyin/TaskFlowInsight");
        values.put("protectedRef", "refs/heads/main");
        values.put("candidateRevision", "a".repeat(40));
        values.put("finalVersion", "4.0.0");
        values.put("releaseTarget", "INTERNAL_REPOSITORY:https://repo.example.test/releases");
        values.put("externalPublicationAuthority", "NONE");
        values.put("externalPublicationAuthoritySha256", "-");
        values.put("trustedBuilder", "https://issuer.example.test|builder/test");
        for (Map.Entry<String, String> reference : references.entrySet()) {
            Path file = directory.resolve(reference.getValue());
            Files.writeString(file, "fixture\n", StandardCharsets.UTF_8);
            values.put(reference.getKey(), reference.getValue());
            values.put(reference.getKey() + "Sha256", sha256(Files.readAllBytes(file)));
        }
        writeReferencedSchemas(directory, values, "4.0.0");
        writeProductionAuthorities(directory, values);
        values.put("provenanceWorkflow", "github.com/shiyongyin/TaskFlowInsight|"
                + ".github/workflows/release.yml|" + "b".repeat(40));
        String tool = "fixture@1.0.0#bundle-sha256:" + "c".repeat(64);
        values.put("vulnerabilityScanner", tool);
        values.put("vulnerabilityFailCvssThreshold", "7.0");
        values.put("vulnerabilityDbMaxAgeHours", "24");
        values.put("secretScanner", tool);
        values.put("sbomGenerator", tool);
        values.put("sbomFormat", "CycloneDX-1.6");
        values.put("requiredSignatures", "NONE");
        values.putAll(overrides);

        Path policy = directory.resolve("policy.tsv");
        Files.write(policy, POLICY_KEYS.stream()
                .map(key -> key + "\t" + values.get(key))
                .toList(), StandardCharsets.UTF_8);
        return policy;
    }

    private static void writeReferencedSchemas(
            Path directory, Map<String, String> values, String version) throws Exception {
        writePublishManifest(directory.resolve("publish.tsv"), version);
        writePerformancePolicy(directory, version);
        Files.write(directory.resolve("compatibility.tsv"), List.of(
                "edgeKind\tconsumerGa\tconsumerVersion\tdependencyGa\tdependencyVersion\t"
                        + "expected\tenforcement\tevidenceCommandId",
                "DEPENDENCY\tcom.syy:tfi-compare\t" + version + "\tcom.syy:tfi-flow-core\t"
                        + version + "\tSUPPORTED\tARTIFACT_TEST\tCMD-COMPAT"), StandardCharsets.UTF_8);
        writeExecutionPolicy(directory);
        writeToolchainPolicy(directory);
        Files.write(directory.resolve("licenses.tsv"), List.of(
                "spdxExpression\tdecision\tnoticeRequired\tlicenseTextSha256",
                "Apache-2.0\tALLOW\tfalse\t" + "f".repeat(64)), StandardCharsets.UTF_8);
        for (String key : List.of(
                "publishArtifactManifest", "runtimePerformancePolicy", "compatibilityMatrix",
                "releaseExecutionPolicy", "buildToolchainManifest", "licensePolicy")) {
            String relative = values.get(key);
            values.put(key + "Sha256", sha256(Files.readAllBytes(directory.resolve(relative))));
        }
    }

    private static void writePublishManifest(Path path, String version) throws IOException {
        List<String[]> primaries = new ArrayList<>();
        addPrimary(primaries, "taskflowinsight-parent", "POM", "pom", null, version);
        for (String artifact : List.of(
                "tfi-flow-core", "tfi-flow-spring-starter", "tfi-compare",
                "tfi-compare-spring-starter", "tfi-ops-spring", "TaskFlowInsight")) {
            addPrimary(primaries, artifact, "POM", "pom", null, version);
            addPrimary(primaries, artifact, "BINARY", "jar", null, version);
            addPrimary(primaries, artifact, "SOURCES", "jar", "sources", version);
            addPrimary(primaries, artifact, "JAVADOC", "jar", "javadoc", version);
        }
        List<String> lines = new ArrayList<>();
        lines.add("ordinal\tsubjectOrdinal\tsubjectCoordinate\trepositoryPath\trole\tsidecarKind");
        for (int index = 0; index < primaries.size(); index++) {
            String[] primary = primaries.get(index);
            lines.add((index + 1) + "\t-\t" + primary[0] + "\t" + primary[1]
                    + "\t" + primary[2] + "\t-");
        }
        int ordinal = primaries.size() + 1;
        for (int index = 0; index < primaries.size(); index++) {
            String[] primary = primaries.get(index);
            int subject = index + 1;
            lines.add(ordinal++ + "\t" + subject + "\t" + primary[0] + "\t"
                    + primary[1] + ".sha256\tCHECKSUM\tSHA256");
            lines.add(ordinal++ + "\t" + subject + "\t" + primary[0] + "\t"
                    + primary[1] + ".sha512\tCHECKSUM\tSHA512");
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private static void addPrimary(
            List<String[]> target,
            String artifact,
            String role,
            String extension,
            String classifier,
            String version) {
        String classifierCoordinate = classifier == null ? "" : ":" + classifier;
        String classifierFile = classifier == null ? "" : "-" + classifier;
        String coordinate = "com.syy:" + artifact + ":" + extension + classifierCoordinate + ":" + version;
        String repositoryPath = "com/syy/" + artifact + "/" + version + "/"
                + artifact + "-" + version + classifierFile + "." + extension;
        target.add(new String[]{coordinate, repositoryPath, role});
    }

    private static void writePerformancePolicy(Path directory, String version) throws Exception {
        Path fixture = directory.resolve("performance-fixtures.tsv");
        Path oracle = directory.resolve("performance-oracle.tsv");
        Path environment = directory.resolve("performance-environment.tsv");
        Files.writeString(fixture, "fixture\n", StandardCharsets.UTF_8);
        Files.writeString(oracle, "oracle\n", StandardCharsets.UTF_8);
        Files.writeString(environment, "environment\n", StandardCharsets.UTF_8);
        String fixtureSha = sha256(Files.readAllBytes(fixture));
        String oracleSha = sha256(Files.readAllBytes(oracle));
        String environmentSha = sha256(Files.readAllBytes(environment));
        List<String> lines = new ArrayList<>();
        lines.add("workloadId\tscenario\tthreads\tfixtureManifestPath\tfixtureManifestSha256\t"
                + "semanticOraclePath\tsemanticOracleSha256\tbenchmarkEnvironmentPath\t"
                + "benchmarkEnvironmentSha256\trunnerProfile\tevidenceCommandId\tcommandSpecSha256\t"
                + "absoluteP99NanosMax\tbaselineArtifactSetSha256\tmaxRegressionPercent\tallocationBytesPerOpMax");
        for (String scenario : List.of(
                "NESTED_POJO", "LIST", "MAP", "SET_SCALAR", "SET_ENTITY", "SET_AMBIGUOUS",
                "OBSERVED_COMPARE")) {
            for (String threads : List.of("1", "8", "32")) {
                String id = "PERF-" + scenario + "-" + threads;
                lines.add(id + "\t" + scenario + "\t" + threads + "\tperformance-fixtures.tsv\t"
                        + fixtureSha + "\tperformance-oracle.tsv\t" + oracleSha
                        + "\tperformance-environment.tsv\t" + environmentSha
                        + "\tJMH\tCMD-PERF\t" + "d".repeat(64)
                        + "\t1000000\t" + "e".repeat(64) + "\t5.0\t1048576");
            }
        }
        Files.write(directory.resolve("performance.tsv"), lines, StandardCharsets.UTF_8);
    }

    private static void writeExecutionPolicy(Path directory) throws Exception {
        Path configDirectory = Files.createDirectory(directory.resolve("config"));
        Path config = configDirectory.resolve("tool-config.json");
        Path rules = configDirectory.resolve("tool-rules.tsv");
        Files.writeString(config, "{}\n", StandardCharsets.UTF_8);
        Files.writeString(rules, "rule\n", StandardCharsets.UTF_8);
        Path coverage = configDirectory.resolve("canary-coverage.tsv");
        List<String> coverageRows = new ArrayList<>();
        coverageRows.add("canaryId\tcanaryKind\tsinkKind\tinjectionDriverId");
        int canaryOrdinal = 1;
        for (String kind : List.of(
                "BEFORE_VALUE", "AFTER_VALUE", "CREDENTIAL", "TOKEN", "PII", "ENTITY_KEY", "STORE_KEY")) {
            for (String sink : List.of(
                    "APPLICATION_LOG", "MAVEN_LOG", "EXCEPTION", "METER", "ACTUATOR", "SUREFIRE",
                    "FAILSAFE", "DEPENDENCY_TREE", "JSON", "TSV", "ARTIFACT")) {
                coverageRows.add(String.format("CANARY_%03d\t%s\t%s\tDRIVER_%s_%s",
                        canaryOrdinal++, kind, sink, kind, sink));
            }
        }
        Files.write(coverage, coverageRows, StandardCharsets.UTF_8);
        String configSha = sha256(Files.readAllBytes(config));
        String rulesSha = sha256(Files.readAllBytes(rules));
        String coverageSha = sha256(Files.readAllBytes(coverage));
        String commandSha = "d".repeat(64);
        List<String> lines = new ArrayList<>();
        lines.add("executionId\trole\tcommandId\tcommandSpecSha256\tconfigPath\tconfigSha256\t"
                + "rulesPath\trulesSha256\tscopeRule");
        lines.add("EXEC-PERF\tPERFORMANCE\tCMD-PERF\t" + commandSha
                + "\tNONE\t-\tNONE\t-\tPERFORMANCE_POLICY");
        lines.add("EXEC-COMPAT\tCOMPATIBILITY\tCMD-COMPAT\t" + commandSha
                + "\tNONE\t-\tNONE\t-\tCOMPATIBILITY_MATRIX");
        for (String[] role : List.of(
                new String[]{"EXEC-VULN", "VULNERABILITY_SCAN", "RUNTIME_DEPENDENCY_CLOSURE"},
                new String[]{"EXEC-SECRET-FIRST", "SECRET_SCAN_FIRST", "SECRET_BEARING_EVIDENCE"},
                new String[]{"EXEC-SECRET-SELF", "SECRET_SCAN_SELF", "SECRET_REPORT_ONLY"},
                new String[]{"EXEC-SBOM", "SBOM_GENERATE", "PUBLISHABLE_RUNTIME_CLOSURE"})) {
            lines.add(role[0] + "\t" + role[1] + "\tCMD-" + role[0] + "\t" + commandSha
                    + "\tconfig/tool-config.json\t" + configSha
                    + "\tconfig/tool-rules.tsv\t" + rulesSha + "\t" + role[2]);
        }
        lines.add("EXEC-SENSITIVE\tSENSITIVE_LOG_SCAN\tCMD-EXEC-SENSITIVE\t" + commandSha
                + "\tconfig/tool-config.json\t" + configSha
                + "\tconfig/canary-coverage.tsv\t" + coverageSha + "\tSENSITIVE_LOG_EVIDENCE");
        Files.write(directory.resolve("executions.tsv"), lines, StandardCharsets.UTF_8);
    }

    private static void writeToolchainPolicy(Path directory) throws Exception {
        Path toolBytes = Files.createDirectory(directory.resolve("tool-bytes"));
        List<String[]> components = List.of(
                new String[]{"JDK_RUNTIME", "jdk:Zml4dHVyZQ:YnVpbGQ:linux:amd64:amF2YQ", "jdk.bin"},
                new String[]{"MAVEN_DISTRIBUTION", "maven-dist:3.9.11:Y29yZQ", "maven.bin"},
                new String[]{"MAVEN_WRAPPER", "maven-wrapper:3.3.4:d3JhcHBlcg", "wrapper.jar"},
                new String[]{"RUNNER_IMAGE", "oci:example.test/tfi-runner@sha256:" + "e".repeat(64),
                        "runner.json"});
        List<String> lines = new ArrayList<>();
        lines.add("ordinal\trole\tcoordinate\tevidencePath\tsha256");
        for (int index = 0; index < components.size(); index++) {
            String[] component = components.get(index);
            Path bytes = toolBytes.resolve(component[2]);
            Files.writeString(bytes, "TEST_ONLY " + component[0] + "\n", StandardCharsets.UTF_8);
            lines.add((index + 1) + "\t" + component[0] + "\t" + component[1]
                    + "\ttool-bytes/" + component[2] + "\t" + sha256(Files.readAllBytes(bytes)));
        }
        Files.write(directory.resolve("toolchain.tsv"), lines, StandardCharsets.UTF_8);
    }

    private static void writeProductionAuthorities(
            Path directory, Map<String, String> values) throws Exception {
        Path trust = Files.createDirectory(directory.resolve("trust"));
        Path provenanceMaterial = trust.resolve("provenance-material.pem");
        Path databaseMaterial = trust.resolve("database-material.pem");
        Files.writeString(provenanceMaterial, "TEST_ONLY PROVENANCE TRUST\n", StandardCharsets.UTF_8);
        Files.writeString(databaseMaterial, "TEST_ONLY DATABASE TRUST\n", StandardCharsets.UTF_8);

        Path provenance = trust.resolve("provenance.tsv");
        Files.write(provenance, List.of(
                "scheme\tkeyId\tmaterialPath\tmaterialSha256",
                "SIGSTORE\thttps://issuer.example.test|builder/test\ttrust/provenance-material.pem\t"
                        + sha256(Files.readAllBytes(provenanceMaterial))), StandardCharsets.UTF_8);
        Path database = trust.resolve("vulnerability-database.tsv");
        Files.write(database, List.of(
                "sourceId\tscheme\tkeyId\tmaterialPath\tmaterialSha256\tminimumSnapshotSequence",
                "fixture-db\tSIGSTORE\thttps://issuer.example.test|database/test\t"
                        + "trust/database-material.pem\t" + sha256(Files.readAllBytes(databaseMaterial)) + "\t1"),
                StandardCharsets.UTF_8);

        Path authorities = directory.resolve("authorities.tsv");
        Files.write(authorities, List.of(
                "PROVENANCE_TRUST_ROOT\ttrust/provenance.tsv\t" + sha256(Files.readAllBytes(provenance)),
                "EXTERNAL_PUBLICATION_TRUST_ROOT\tNONE\t-",
                "ARTIFACT_SIGNATURE_TRUST_ROOT\tNONE\t-",
                "VULNERABILITY_DATABASE_TRUST_ROOT\ttrust/vulnerability-database.tsv\t"
                        + sha256(Files.readAllBytes(database)),
                "VULNERABILITY_SUPPRESSIONS\tNONE\t-"), StandardCharsets.UTF_8);
        values.put("productionAuthoritiesManifestSha256", sha256(Files.readAllBytes(authorities)));
    }

    private static List<String> authorityLines(Path policy) throws IOException {
        return new ArrayList<>(Files.readAllLines(
                policy.getParent().resolve("authorities.tsv"), StandardCharsets.UTF_8));
    }

    private static void replaceAuthorityManifest(Path policy, List<String> lines) throws Exception {
        Path authority = policy.getParent().resolve("authorities.tsv");
        Files.write(authority, lines, StandardCharsets.UTF_8);
        List<String> policyLines = Files.readAllLines(policy, StandardCharsets.UTF_8);
        int digestLine = POLICY_KEYS.indexOf("productionAuthoritiesManifestSha256");
        policyLines.set(digestLine, "productionAuthoritiesManifestSha256\t"
                + sha256(Files.readAllBytes(authority)));
        Files.write(policy, policyLines, StandardCharsets.UTF_8);
    }

    private static List<String> referenceLines(Path policy, String key) throws IOException {
        List<String> policyLines = Files.readAllLines(policy, StandardCharsets.UTF_8);
        String relative = policyLines.get(POLICY_KEYS.indexOf(key)).split("\t", -1)[1];
        return new ArrayList<>(Files.readAllLines(policy.getParent().resolve(relative), StandardCharsets.UTF_8));
    }

    static void replaceReference(Path policy, String key, List<String> lines) throws Exception {
        List<String> policyLines = Files.readAllLines(policy, StandardCharsets.UTF_8);
        int pathLine = POLICY_KEYS.indexOf(key);
        String relative = policyLines.get(pathLine).split("\t", -1)[1];
        Path authority = policy.getParent().resolve(relative);
        Files.write(authority, lines, StandardCharsets.UTF_8);
        int digestLine = POLICY_KEYS.indexOf(key + "Sha256");
        policyLines.set(digestLine, key + "Sha256\t" + sha256(Files.readAllBytes(authority)));
        Files.write(policy, policyLines, StandardCharsets.UTF_8);
    }

    private static void renumberOrdinals(List<String> lines) {
        for (int index = 1; index < lines.size(); index++) {
            String[] row = lines.get(index).split("\t", -1);
            row[0] = Integer.toString(index);
            lines.set(index, String.join("\t", row));
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static ProcessResult runVerifier(String... arguments)
            throws IOException, InterruptedException {
        Path root = repositoryRoot();
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add(root.resolve("scripts/release-evidence/ReleaseEvidenceVerifier.java").toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private static Path repositoryRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
    }

    private static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
