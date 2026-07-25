package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** SBOM、runtime inventory 与 license evidence 的 raw-to-summary 合同。 */
class SupplyChainEvidenceContractTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void canonicalCycloneDxAndRuntimeInventoryAreAccepted() throws Exception {
        Fixture fixture = writeFixture("valid-sbom");

        ProcessResult result = runVerifier(
                "verify-supply-chain", fixture.evidence().toString(), fixture.policy().toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("SUPPLY_CHAIN_OK");
    }

    @Test
    void canonicalSpdxAndRuntimeInventoryAreAccepted() throws Exception {
        Fixture fixture = writeFixtureAt(
                temporaryDirectory.resolve("valid-spdx"), "SPDX-2.3");

        ProcessResult result = runVerifier(
                "verify-supply-chain", fixture.evidence().toString(), fixture.policy().toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("SUPPLY_CHAIN_OK");
    }

    @Test
    void bundledComponentMustBePresentInItsContainingBinary() throws Exception {
        Fixture fixture = writeFixtureAt(
                temporaryDirectory.resolve("valid-bundled"), "CycloneDX-1.6", true);
        Fixture missing = writeFixtureAt(
                temporaryDirectory.resolve("missing-bundled"), "CycloneDX-1.6", true);
        Path containing = missing.evidence().resolve("artifacts/publishable/TaskFlowInsight.jar");
        writeContainingJar(containing, "different nested bytes".getBytes(StandardCharsets.UTF_8));
        List<String> inventory = Files.readAllLines(missing.runtimeInventory(), StandardCharsets.UTF_8);
        String[] bundled = inventory.get(1).split("\t", -1);
        bundled[11] = sha256(Files.readAllBytes(containing));
        inventory.set(1, String.join("\t", bundled));
        Files.write(missing.runtimeInventory(), inventory, StandardCharsets.UTF_8);

        ProcessResult result = runVerifier(
                "verify-supply-chain", fixture.evidence().toString(), fixture.policy().toString());
        ProcessResult missingResult = runVerifier(
                "verify-supply-chain", missing.evidence().toString(), missing.policy().toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(missingResult.exitCode()).isNotZero();
        assertThat(missingResult.output()).contains(
                "BUNDLED component bytes must occur exactly once in the containing binary");
    }

    @Test
    void sbomRejectsNonCanonicalJsonMissingNormalizedRowAndArtifactDigestDrift() throws Exception {
        Fixture nonCanonical = writeFixture("non-canonical");
        String bom = Files.readString(nonCanonical.bom(), StandardCharsets.UTF_8);
        Files.writeString(nonCanonical.bom(), bom.replace("{\"bomFormat\"", "{ \"bomFormat\""),
                StandardCharsets.UTF_8);

        Fixture missingRow = writeFixture("missing-row");
        Files.writeString(missingRow.components(), COMPONENTS_HEADER + "\n", StandardCharsets.UTF_8);

        Fixture digestDrift = writeFixture("digest-drift");
        List<String> inventory = Files.readAllLines(digestDrift.runtimeInventory(), StandardCharsets.UTF_8);
        String[] row = inventory.get(1).split("\t", -1);
        row[8] = "0".repeat(64);
        inventory.set(1, String.join("\t", row));
        Files.write(digestDrift.runtimeInventory(), inventory, StandardCharsets.UTF_8);

        ProcessResult canonicalResult = runVerifier(
                "verify-supply-chain", nonCanonical.evidence().toString(), nonCanonical.policy().toString());
        ProcessResult missingResult = runVerifier(
                "verify-supply-chain", missingRow.evidence().toString(), missingRow.policy().toString());
        ProcessResult digestResult = runVerifier(
                "verify-supply-chain", digestDrift.evidence().toString(), digestDrift.policy().toString());

        assertThat(canonicalResult.exitCode()).isNotZero();
        assertThat(canonicalResult.output()).contains("SBOM JSON must use RFC 8785 canonical bytes");
        assertThat(missingResult.exitCode()).isNotZero();
        assertThat(missingResult.output()).contains("SBOM raw and normalized component closure differs");
        assertThat(digestResult.exitCode()).isNotZero();
        assertThat(digestResult.output()).contains("runtime artifact SHA does not match retained bytes");
    }

    @Test
    void licenseEvidenceRejectsSelfReportedEntryHashAndMissingRequiredNotice() throws Exception {
        Fixture entryDrift = writeFixture("license-entry-drift");
        List<String> evidenceRows = Files.readAllLines(entryDrift.licenseEvidence(), StandardCharsets.UTF_8);
        String[] evidence = evidenceRows.get(1).split("\t", -1);
        evidence[11] = "0".repeat(64);
        evidenceRows.set(1, String.join("\t", evidence));
        Files.write(entryDrift.licenseEvidence(), evidenceRows, StandardCharsets.UTF_8);

        Fixture missingNotice = writeFixture("license-missing-notice");
        ReleasePolicyParserContractTests.replaceReference(missingNotice.policy(), "licensePolicy", List.of(
                "spdxExpression\tdecision\tnoticeRequired\tlicenseTextSha256",
                "Apache-2.0\tALLOW\ttrue\t" + sha256(LICENSE_BYTES)));
        List<String> noticeRows = Files.readAllLines(missingNotice.licenseEvidence(), StandardCharsets.UTF_8);
        String[] notice = noticeRows.get(1).split("\t", -1);
        notice[8] = "true";
        noticeRows.set(1, String.join("\t", notice));
        Files.write(missingNotice.licenseEvidence(), noticeRows, StandardCharsets.UTF_8);

        ProcessResult driftResult = runVerifier(
                "verify-supply-chain", entryDrift.evidence().toString(), entryDrift.policy().toString());
        ProcessResult noticeResult = runVerifier(
                "verify-supply-chain", missingNotice.evidence().toString(), missingNotice.policy().toString());

        assertThat(driftResult.exitCode()).isNotZero();
        assertThat(driftResult.output()).contains("license entry SHA does not match retained artifact bytes");
        assertThat(noticeResult.exitCode()).isNotZero();
        assertThat(noticeResult.output()).contains("required NOTICE evidence is missing");
    }

    @Test
    void vulnerabilityRejectsHiddenCriticalFindingAndStaleDatabase() throws Exception {
        Fixture hiddenCritical = writeFixture("vulnerability-critical");
        String critical = "{\"analysisErrors\":[],\"findings\":[{\"cve\":\"CVE-2026-0001\","
                + "\"cvss\":9.8,\"findingId\":\"F-1\",\"gav\":\"com.syy:tfi-compare:4.0.0\","
                + "\"status\":\"OPEN\"}],"
                + "\"scannerIdentity\":\""
                + ReleaseToolchainEvidenceTestFixture.policyValue(
                        hiddenCritical.policy(), "vulnerabilityScanner")
                + "\",\"status\":\"PASS\"}";
        Files.writeString(hiddenCritical.vulnerabilityReport(), critical, StandardCharsets.UTF_8);

        Fixture staleDatabase = writeFixture("vulnerability-stale");
        rewriteDatabaseTime(staleDatabase, Instant.now().minus(48, ChronoUnit.HOURS));

        ProcessResult criticalResult = runVerifier(
                "verify-supply-chain", hiddenCritical.evidence().toString(), hiddenCritical.policy().toString());
        ProcessResult staleResult = runVerifier(
                "verify-supply-chain", staleDatabase.evidence().toString(), staleDatabase.policy().toString());

        assertThat(criticalResult.exitCode()).isNotZero();
        assertThat(criticalResult.output()).contains("vulnerability raw and normalized findings differ");
        assertThat(staleResult.exitCode()).isNotZero();
        assertThat(staleResult.output()).contains("vulnerability database is stale");
    }

    @Test
    void vulnerabilityDatabaseAcceptsPolicyPinnedSigstoreManifestSignature() throws Exception {
        Fixture fixture = writeFixture("vulnerability-sigstore");
        SigstoreAttestationTestFixture.addDatabaseSignature(fixture.evidence(), fixture.policy());
        refreshSecurityClosure(fixture);

        ProcessResult result = runVerifier(
                "verify-supply-chain", fixture.evidence().toString(), fixture.policy().toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("SUPPLY_CHAIN_OK");
    }

    @Test
    void vulnerabilityDatabaseRejectsTamperedSigstoreManifestSignature() throws Exception {
        Fixture fixture = writeFixture("vulnerability-sigstore-tampered");
        SigstoreAttestationTestFixture.addDatabaseSignature(fixture.evidence(), fixture.policy());
        String bundle = Files.readString(fixture.databaseSignature(), StandardCharsets.UTF_8);
        String marker = "\"signature\":\"";
        int signatureOffset = bundle.indexOf(marker) + marker.length();
        char replacement = bundle.charAt(signatureOffset) == 'A' ? 'B' : 'A';
        Files.writeString(fixture.databaseSignature(),
                bundle.substring(0, signatureOffset) + replacement
                        + bundle.substring(signatureOffset + 1),
                StandardCharsets.UTF_8);
        List<String> scanInputs = new ArrayList<>(Files.readAllLines(
                fixture.vulnerabilityScanInputs(), StandardCharsets.UTF_8));
        String[] database = scanInputs.get(3).split("\t", -1);
        database[8] = sha256(Files.readAllBytes(fixture.databaseSignature()));
        scanInputs.set(3, String.join("\t", database));
        Files.write(fixture.vulnerabilityScanInputs(), scanInputs, StandardCharsets.UTF_8);
        refreshSecurityClosure(fixture);

        ProcessResult result = runVerifier(
                "verify-supply-chain", fixture.evidence().toString(), fixture.policy().toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Sigstore artifact message signature is invalid");
    }

    @Test
    void supplyChainRejectsMissingBuildToolchainMeasurementIndex() throws Exception {
        Fixture fixture = writeFixture("missing-actual-toolchain");
        Files.delete(fixture.evidence().resolve(
                "supply-chain/tool-closures/build-toolchain-measurements.tsv"));
        refreshSecurityClosure(fixture);

        ProcessResult result = runVerifier(
                "verify-supply-chain", fixture.evidence().toString(), fixture.policy().toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("build toolchain measurements");
    }

    @Test
    void supplyChainRejectsForgedProcessLocatorWithoutRetainedObservation() throws Exception {
        Fixture fixture = writeFixture("forged-process-locator");
        Path raw = fixture.evidence().resolve(
                "supply-chain/tool-measurements/build-toolchain/002.tsv");
        List<String> facts = new ArrayList<>(Files.readAllLines(raw, StandardCharsets.UTF_8));
        String[] forged = facts.get(1).split("\t", -1);
        assertThat(forged[1]).isEqualTo("PROCESS_EXECUTABLE_MAP");
        forged[2] = "/proc/999999/maps:/forged/tool";
        facts.set(1, String.join("\t", forged));
        Files.write(raw, facts, StandardCharsets.UTF_8);

        Path index = fixture.evidence().resolve(
                "supply-chain/tool-closures/build-toolchain-measurements.tsv");
        List<String> measurements = new ArrayList<>(
                Files.readAllLines(index, StandardCharsets.UTF_8));
        String[] indexed = measurements.get(2).split("\t", -1);
        indexed[4] = sha256(Files.readAllBytes(raw));
        measurements.set(2, String.join("\t", indexed));
        Files.write(index, measurements, StandardCharsets.UTF_8);
        refreshSecurityClosure(fixture);

        ProcessResult result = runVerifier(
                "verify-supply-chain", fixture.evidence().toString(), fixture.policy().toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("retained raw observation");
    }

    @Test
    void supplyChainRejectsJvmLocatorAbsentFromRetainedObservation() throws Exception {
        Fixture fixture = writeFixture("forged-jvm-locator");
        forgeBuildRawSource(fixture, 3, "file:/different/wrapper.jar\n");

        ProcessResult result = runVerifier(
                "verify-supply-chain", fixture.evidence().toString(), fixture.policy().toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("does not derive JVM locator");
    }

    @Test
    void supplyChainRejectsOciDigestAbsentFromRetainedObservation() throws Exception {
        Fixture fixture = writeFixture("forged-oci-locator");
        forgeBuildRawSource(fixture, 4,
                "{\"RepoDigests\":[\"example.test/other@sha256:" + "0".repeat(64) + "\"]}\n");

        ProcessResult result = runVerifier(
                "verify-supply-chain", fixture.evidence().toString(), fixture.policy().toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("does not derive OCI locator");
    }

    @Test
    void supplyChainRejectsExecutionLocatorAbsentFromRetainedObservation() throws Exception {
        Fixture fixture = writeFixture("forged-execution-locator");
        Path ledger = fixture.evidence().resolve("metadata/tool-executions.tsv");
        List<String> ledgerRows = new ArrayList<>(Files.readAllLines(ledger, StandardCharsets.UTF_8));
        String[] execution = ledgerRows.get(1).split("\t", -1);
        Path raw = fixture.evidence().resolve(execution[9]);
        List<String> factRows = new ArrayList<>(Files.readAllLines(raw, StandardCharsets.UTF_8));
        String[] fact = factRows.get(1).split("\t", -1);
        Path rawSource = fixture.evidence().resolve(fact[9]);
        Files.writeString(rawSource,
                "00400000-00452000 r-xp 00000000 08:01 12345 /different/scanner\n",
                StandardCharsets.UTF_8);
        fact[10] = sha256(Files.readAllBytes(rawSource));
        factRows.set(1, String.join("\t", fact));
        Files.write(raw, factRows, StandardCharsets.UTF_8);
        execution[10] = sha256(Files.readAllBytes(raw));
        ledgerRows.set(1, String.join("\t", execution));
        Files.write(ledger, ledgerRows, StandardCharsets.UTF_8);
        refreshSecurityClosure(fixture);

        ProcessResult result = runVerifier(
                "verify-supply-chain", fixture.evidence().toString(), fixture.policy().toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("does not derive process locator");
    }

    @Test
    void supplyChainRejectsMissingScannerAndGeneratorToolClosures() throws Exception {
        Fixture fixture = writeFixture("missing-tool-closures");
        Files.delete(fixture.evidence().resolve(
                "security/tool-closures/vulnerability-scanner.tsv"));
        refreshSecurityClosure(fixture);

        ProcessResult result = runVerifier(
                "verify-supply-chain", fixture.evidence().toString(), fixture.policy().toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("vulnerability scanner tool closure");
    }

    @Test
    void supplyChainRejectsMissingPerExecutionLoadedByteLedger() throws Exception {
        Fixture fixture = writeFixture("missing-tool-executions");
        Files.delete(fixture.evidence().resolve("metadata/tool-executions.tsv"));

        ProcessResult result = runVerifier(
                "verify-supply-chain", fixture.evidence().toString(), fixture.policy().toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("tool execution ledger");
    }

    @Test
    void supplyChainRejectsMissingReleaseExecutionLedger() throws Exception {
        Fixture fixture = writeFixture("missing-release-executions");
        Files.delete(fixture.evidence().resolve("metadata/release-executions.tsv"));

        ProcessResult result = runVerifier(
                "verify-supply-chain", fixture.evidence().toString(), fixture.policy().toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("release execution ledger");
    }

    @Test
    void verifyAllRejectsEveryTestOnlyAuthorityPath() throws Exception {
        Fixture fixture = writeFixture("verify-all-test-only");
        Fixture disguised = writeFixture("verify-all-disguised-test-only");
        List<String> policyLines = Files.readAllLines(disguised.policy(), StandardCharsets.UTF_8);
        policyLines.set(0, "policyId\tauthority:release-policy");
        Files.write(disguised.policy(), policyLines, StandardCharsets.UTF_8);
        Path reports = prepareVerifyAllInputs(fixture);
        Path disguisedReports = prepareVerifyAllInputs(disguised);

        ProcessResult result = runVerifier(
                "verify-all", fixture.evidence().toString(), reports.toString());
        ProcessResult disguisedResult = runVerifier(
                "verify-all", disguised.evidence().toString(), disguisedReports.toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("TEST_ONLY authority is forbidden in production mode");
        assertThat(disguisedResult.exitCode()).isNotZero();
        assertThat(disguisedResult.output()).contains(
                "TEST_ONLY authority is forbidden in production mode");
    }

    private Path prepareVerifyAllInputs(Fixture fixture) throws Exception {
        Path source = fixture.policy().getParent();
        Path target = Files.createDirectories(fixture.evidence().resolve("policy"));
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
        }
        Files.copy(fixture.policy(), target.resolve("production-policy.tsv"));
        Path metadata = Files.createDirectories(fixture.evidence().resolve("metadata"));
        Files.writeString(metadata.resolve("review-assignment.tsv"), "release-mode\n",
                StandardCharsets.UTF_8);
        Path reports = temporaryDirectory.resolve(
                fixture.evidence().getParent().getFileName() + "-verify-all-reports.tsv");
        Files.writeString(reports,
                "phase\tmodule\treportPath\tminimumTests\tallowSkipped\n",
                StandardCharsets.UTF_8);
        return reports;
    }

    private Fixture writeFixture(String name) throws Exception {
        return writeFixtureAt(temporaryDirectory.resolve(name));
    }

    private static void refreshSecurityClosure(Fixture fixture) throws Exception {
        ReleaseSecurityEvidenceTestFixture.add(fixture.evidence(), fixture.policy());
        ReleaseToolchainEvidenceTestFixture.addReleaseExecutions(
                fixture.evidence(), fixture.policy());
    }

    private static void forgeBuildRawSource(
            Fixture fixture, int ordinal, String replacement) throws Exception {
        String rawRelative = "supply-chain/tool-measurements/build-toolchain/"
                + String.format("%03d.tsv", ordinal);
        Path raw = fixture.evidence().resolve(rawRelative);
        List<String> facts = new ArrayList<>(Files.readAllLines(raw, StandardCharsets.UTF_8));
        String[] fact = facts.get(1).split("\t", -1);
        Path rawSource = fixture.evidence().resolve(fact[7]);
        Files.writeString(rawSource, replacement, StandardCharsets.UTF_8);
        fact[8] = sha256(Files.readAllBytes(rawSource));
        facts.set(1, String.join("\t", fact));
        Files.write(raw, facts, StandardCharsets.UTF_8);

        Path index = fixture.evidence().resolve(
                "supply-chain/tool-closures/build-toolchain-measurements.tsv");
        List<String> measurements = new ArrayList<>(
                Files.readAllLines(index, StandardCharsets.UTF_8));
        String[] indexed = measurements.get(ordinal).split("\t", -1);
        indexed[4] = sha256(Files.readAllBytes(raw));
        measurements.set(ordinal, String.join("\t", indexed));
        Files.write(index, measurements, StandardCharsets.UTF_8);
        refreshSecurityClosure(fixture);
    }

    static Fixture writeFixtureAt(Path root) throws Exception {
        return writeFixtureAt(root, "CycloneDX-1.6");
    }

    static Fixture writeFixtureAt(Path root, String sbomFormat) throws Exception {
        return writeFixtureAt(root, sbomFormat, false);
    }

    static Fixture writeFixtureAt(Path root, String sbomFormat, boolean bundled) throws Exception {
        Files.createDirectory(root);
        Path policy = ReleasePolicyParserContractTests.writePolicyFixture(
                root.resolve("policy"), Map.of("sbomFormat", sbomFormat));
        ReleaseToolchainEvidenceTestFixture.addCommandSpecs(policy);
        Path evidence = Files.createDirectory(root.resolve("evidence"));
        Path artifact = evidence.resolve("artifacts/runtime-dependencies/tfi-compare.jar");
        writeJar(artifact);
        String artifactSha = sha256(Files.readAllBytes(artifact));
        String scope = bundled ? "BUNDLED" : "RUNTIME";
        String containingCoordinate = bundled ? "com.syy:TaskFlowInsight:jar:4.0.0" : "-";
        String containingPath = bundled ? "artifacts/publishable/TaskFlowInsight.jar" : "-";
        String containingSha = "-";
        if (bundled) {
            Path containing = evidence.resolve(containingPath);
            writeContainingJar(containing, Files.readAllBytes(artifact));
            containingSha = sha256(Files.readAllBytes(containing));
        }
        String licenseSha = sha256(LICENSE_BYTES);
        ReleasePolicyParserContractTests.replaceReference(policy, "licensePolicy", List.of(
                "spdxExpression\tdecision\tnoticeRequired\tlicenseTextSha256",
                "Apache-2.0\tALLOW\tfalse\t" + licenseSha));
        String purl = "pkg:maven/com.syy/tfi-compare@4.0.0";
        String component;
        String bom;
        String bomRelative;
        if ("CycloneDX-1.6".equals(sbomFormat)) {
            component = "{\"bom-ref\":\"" + purl + "\",\"group\":\"com.syy\","
                    + "\"licenses\":[{\"expression\":\"Apache-2.0\"}],\"name\":\"tfi-compare\","
                    + "\"properties\":[{\"name\":\"tfi:artifactPath\",\"value\":"
                    + "\"artifacts/runtime-dependencies/tfi-compare.jar\"},{\"name\":\"tfi:scope\","
                    + "\"value\":\"" + scope + "\"}],\"purl\":\"" + purl
                    + "\",\"type\":\"library\",\"version\":\"4.0.0\"}";
            bom = "{\"bomFormat\":\"CycloneDX\",\"components\":[" + component
                    + "],\"specVersion\":\"1.6\",\"version\":1}";
            bomRelative = "supply-chain/sbom/bom.cdx.json";
        } else {
            component = "{\"SPDXID\":\"SPDXRef-Package-tfi-compare\",\"checksums\":[{"
                    + "\"algorithm\":\"SHA256\",\"checksumValue\":\"" + artifactSha + "\"}],"
                    + "\"externalRefs\":[{\"referenceCategory\":\"PACKAGE-MANAGER\","
                    + "\"referenceLocator\":\"" + purl + "\",\"referenceType\":\"purl\"}],"
                    + "\"filesAnalyzed\":false,\"licenseConcluded\":\"Apache-2.0\","
                    + "\"licenseDeclared\":\"Apache-2.0\",\"name\":\"tfi-compare\","
                    + "\"versionInfo\":\"4.0.0\"}";
            bom = "{\"SPDXID\":\"SPDXRef-DOCUMENT\",\"dataLicense\":\"CC0-1.0\","
                    + "\"packages\":[" + component + "],\"spdxVersion\":\"SPDX-2.3\"}";
            bomRelative = "supply-chain/sbom/bom.spdx.json";
        }
        Path bomPath = evidence.resolve(bomRelative);
        Files.createDirectories(bomPath.getParent());
        Files.writeString(bomPath, bom, StandardCharsets.UTF_8);

        Path runtime = evidence.resolve("metadata/runtime-artifacts.tsv");
        Files.createDirectories(runtime.getParent());
        Files.write(runtime, List.of(
                RUNTIME_HEADER,
                purl + "\t" + scope + "\tcom.syy\ttfi-compare\t4.0.0\t-\tjar\t"
                        + "artifacts/runtime-dependencies/tfi-compare.jar\t" + artifactSha
                        + "\t" + containingCoordinate + "\t" + containingPath + "\t" + containingSha),
                StandardCharsets.UTF_8);
        Path components = evidence.resolve("supply-chain/sbom/components.tsv");
        Files.write(components, List.of(
                COMPONENTS_HEADER,
                purl + "\t" + sha256(component.getBytes(StandardCharsets.UTF_8))
                        + "\tApache-2.0\t" + scope
                        + "\tartifacts/runtime-dependencies/tfi-compare.jar\t"
                        + artifactSha + "\t" + containingCoordinate + "\t" + containingPath
                        + "\t" + containingSha), StandardCharsets.UTF_8);
        Files.write(evidence.resolve("supply-chain/sbom/summary.tsv"), List.of(
                "rawComponentCount\tnormalizedComponentCount\tmissingInventory\textraInventory\t"
                        + "identityMismatches\tlicenseMismatches\tanalysisErrors\tstatus",
                "1\t1\t0\t0\t0\t0\t0\tPASS"), StandardCharsets.UTF_8);
        Path licenseEvidence = evidence.resolve("security/license/license-evidence.tsv");
        Files.createDirectories(licenseEvidence.getParent());
        Files.write(licenseEvidence, List.of(
                LICENSE_EVIDENCE_HEADER,
                purl + "\t" + scope + "\tartifacts/runtime-dependencies/tfi-compare.jar\t" + artifactSha
                        + "\tApache-2.0\tApache-2.0\t" + licenseSha
                        + "\tALLOW\tfalse\tLICENSE\tMETA-INF/LICENSE\t" + licenseSha + "\tPASS"),
                StandardCharsets.UTF_8);
        Files.write(evidence.resolve("security/license/summary.tsv"), List.of(
                "licenseForbidden\tlicenseUnknown\tlicenseNoticeMissing\tanalysisErrors\tstatus",
                "0\t0\t0\t0\tPASS"), StandardCharsets.UTF_8);
        ReleaseToolchainEvidenceTestFixture.addToolClosures(evidence, policy);
        VulnerabilityFixture vulnerability = writeVulnerabilityEvidence(evidence, policy);
        ReleaseToolchainEvidenceTestFixture.addBuildToolchain(evidence, policy);
        ReleaseToolchainEvidenceTestFixture.addToolExecutions(evidence, policy);
        ReleaseToolchainEvidenceTestFixture.addReleaseExecutionInputs(evidence, policy);
        ReleaseSecurityEvidenceTestFixture.add(evidence, policy);
        ReleaseToolchainEvidenceTestFixture.addReleaseExecutions(evidence, policy);
        return new Fixture(
                policy, evidence, artifact, runtime, bomPath, components, licenseEvidence,
                vulnerability.report(), vulnerability.scanInputs(), vulnerability.databaseManifest(),
                vulnerability.databaseSignature());
    }

    private static VulnerabilityFixture writeVulnerabilityEvidence(Path evidence, Path policy)
            throws Exception {
        Path root = evidence.resolve("security/vulnerability");
        Path databaseDirectory = root.resolve("database");
        Files.createDirectories(databaseDirectory);
        Path config = root.resolve("config.json");
        Path rules = root.resolve("rules.tsv");
        Files.copy(policy.getParent().resolve("config/tool-config.json"), config);
        Files.copy(policy.getParent().resolve("config/tool-rules.tsv"), rules);
        Path database = databaseDirectory.resolve("database.bin");
        Files.writeString(database, "TEST_ONLY VULNERABILITY DATABASE\n", StandardCharsets.UTF_8);
        String producedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        Path manifest = databaseDirectory.resolve("manifest.tsv");
        Files.write(manifest, List.of(
                "sourceId\tsnapshotVersion\tsnapshotSequence\tproducedAtUtc\tdatabasePath\tdatabaseSha256",
                "fixture-db\tfixture-v1\t1\t" + producedAt
                        + "\tsecurity/vulnerability/database/database.bin\t"
                        + sha256(Files.readAllBytes(database))), StandardCharsets.UTF_8);
        Path signature = databaseDirectory.resolve("manifest.sig");
        Files.writeString(signature, "TEST_ONLY-SHA256:" + sha256(Files.readAllBytes(manifest)) + "\n",
                StandardCharsets.UTF_8);

        Path scanInputs = root.resolve("scan-inputs.tsv");
        Files.write(scanInputs, List.of(
                SCAN_INPUTS_HEADER,
                scanInput("CONFIG", "security/vulnerability/config.json", sha256(Files.readAllBytes(config))),
                scanInput("RULES", "security/vulnerability/rules.tsv", sha256(Files.readAllBytes(rules))),
                "DATABASE\tsecurity/vulnerability/database/manifest.tsv\t"
                        + sha256(Files.readAllBytes(manifest)) + "\t" + producedAt
                        + "\tfixture-db\tfixture-v1\t1\tsecurity/vulnerability/database/manifest.sig\t"
                        + sha256(Files.readAllBytes(signature))
                        + "\thttps://issuer.example.test|database/test",
                "SUPPRESSIONS\tNONE\t-\t-\t-\t-\t-\t-\t-\t-"), StandardCharsets.UTF_8);
        Path report = root.resolve("report.json");
        Files.writeString(report, "{\"analysisErrors\":[],\"findings\":[],\"scannerIdentity\":\""
                + ReleaseToolchainEvidenceTestFixture.policyValue(policy, "vulnerabilityScanner")
                + "\",\"status\":\"PASS\"}", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("scanner.log"), "TEST_ONLY SCANNER EXIT 0\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("normalized-findings.tsv"), NORMALIZED_FINDINGS_HEADER + "\n",
                StandardCharsets.UTF_8);
        Files.write(root.resolve("summary.tsv"), List.of(
                "vulnerabilityCritical\tvulnerabilityHigh\tvulnerabilityPolicyViolations\t"
                        + "vulnerabilityAnalysisErrors\tvulnerabilitySuppressed\tstatus",
                "0\t0\t0\t0\t0\tPASS"), StandardCharsets.UTF_8);
        return new VulnerabilityFixture(report, scanInputs, manifest, signature);
    }

    private static String scanInput(String role, String path, String sha) {
        return role + "\t" + path + "\t" + sha + "\t-\t-\t-\t-\t-\t-\t-";
    }

    private static void rewriteDatabaseTime(Fixture fixture, Instant producedAt) throws Exception {
        List<String> manifest = Files.readAllLines(fixture.databaseManifest(), StandardCharsets.UTF_8);
        String[] database = manifest.get(1).split("\t", -1);
        database[3] = producedAt.truncatedTo(ChronoUnit.SECONDS).toString();
        manifest.set(1, String.join("\t", database));
        Files.write(fixture.databaseManifest(), manifest, StandardCharsets.UTF_8);
        Files.writeString(fixture.databaseSignature(),
                "TEST_ONLY-SHA256:" + sha256(Files.readAllBytes(fixture.databaseManifest())) + "\n",
                StandardCharsets.UTF_8);
        List<String> inputs = Files.readAllLines(fixture.vulnerabilityScanInputs(), StandardCharsets.UTF_8);
        String[] input = inputs.get(3).split("\t", -1);
        input[2] = sha256(Files.readAllBytes(fixture.databaseManifest()));
        input[3] = database[3];
        input[8] = sha256(Files.readAllBytes(fixture.databaseSignature()));
        inputs.set(3, String.join("\t", input));
        Files.write(fixture.vulnerabilityScanInputs(), inputs, StandardCharsets.UTF_8);
    }

    private static void writeJar(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry("META-INF/LICENSE"));
            output.write(LICENSE_BYTES);
            output.closeEntry();
        }
    }

    private static void writeContainingJar(Path path, byte[] nestedJar) throws IOException {
        Files.createDirectories(path.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry("BOOT-INF/lib/tfi-compare.jar"));
            output.write(nestedJar);
            output.closeEntry();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static ProcessResult runVerifier(String... arguments)
            throws IOException, InterruptedException {
        Path root = repositoryRoot();
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add(root.resolve("scripts/release-evidence/ReleaseEvidenceVerifier.java").toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private static Path repositoryRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
    }

    /** Test-only LICENSE bytes are never accepted by verify-all as production trust material. */
    private static final byte[] LICENSE_BYTES =
            "TEST_ONLY APACHE-2.0 LICENSE\n".getBytes(StandardCharsets.UTF_8);
    /** Runtime inventory schema used by candidate evidence. */
    private static final String RUNTIME_HEADER = "componentPurl\tscope\tgroupId\tartifactId\tversion\t"
            + "classifier\textension\tcomponentArtifactPath\tcomponentArtifactSha256\t"
            + "containingBinaryCoordinate\tcontainingBinaryPath\tcontainingBinarySha256";
    /** Normalized SBOM component schema. */
    private static final String COMPONENTS_HEADER = "componentPurl\trawComponentIdentitySha256\t"
            + "declaredSpdxExpression\tscope\tcomponentArtifactPath\tcomponentArtifactSha256\t"
            + "containingBinaryCoordinate\tcontainingBinaryPath\tcontainingBinarySha256";
    /** License evidence schema bound to actual archive entries. */
    private static final String LICENSE_EVIDENCE_HEADER = "componentPurl\tscope\tevidenceArtifactPath\t"
            + "evidenceArtifactSha256\tdeclaredSpdxExpression\tdetectedSpdxExpression\t"
            + "licenseTextSha256\tdecision\tnoticeRequired\tevidenceKind\tentryPath\tentrySha256\tstatus";
    /** Vulnerability scan input schema has four role-ordered rows. */
    private static final String SCAN_INPUTS_HEADER = "role\tevidencePath\tsha256\tproducedAtUtc\t"
            + "sourceId\tsnapshotVersion\tsnapshotSequence\tsignaturePath\tsignatureSha256\tsignerKeyId";
    /** Normalized vulnerability rows never retain native snippets. */
    private static final String NORMALIZED_FINDINGS_HEADER =
            "findingId\tcve\tgav\tcvss\tclassification\tstatus\tsuppressionRowSha256\terrorCode";

    static record Fixture(
            Path policy,
            Path evidence,
            Path artifact,
            Path runtimeInventory,
            Path bom,
            Path components,
            Path licenseEvidence,
            Path vulnerabilityReport,
            Path vulnerabilityScanInputs,
            Path databaseManifest,
            Path databaseSignature) {
    }

    private record VulnerabilityFixture(
            Path report, Path scanInputs, Path databaseManifest, Path databaseSignature) {
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
