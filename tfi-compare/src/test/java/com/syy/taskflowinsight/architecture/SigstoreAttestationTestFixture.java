package com.syy.taskflowinsight.architecture;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 生成真实 DSSE/X.509/Rekor signature 的 JUnit-only Sigstore v0.3 fixture。 */
final class SigstoreAttestationTestFixture {
    /** 临时 PKCS12 只在 fixture 创建期间存在；该测试密码不构成生产凭据。 */
    private static final char[] PASSWORD = "changeit".toCharArray();
    /** 非 Fulcio 合同使用的测试 X.500 issuer。 */
    private static final String BUILDER_ISSUER = "CN=TFI Test Root";
    /** Fulcio 扩展合同使用的 TEST_ONLY OIDC issuer。 */
    private static final String OIDC_ISSUER = "https://issuer.example.test";
    /** leaf certificate SAN 与 predicate 共同绑定的测试 builder identity。 */
    private static final String BUILDER_ID = "https://builder.example.test/workflow";
    /** Sigstore legacy Fulcio OIDC issuer extension OID。 */
    private static final String FULCIO_ISSUER_OID = "1.3.6.1.4.1.57264.1.1";

    private SigstoreAttestationTestFixture() {
    }

    static Paths add(Path evidence, Path policy) throws Exception {
        Crypto crypto = createCrypto(policy.getParent().resolve("crypto-work"), null);
        configureTrust(policy, crypto, BUILDER_ISSUER);
        return writeAttestations(evidence, policy, crypto, Variant.DEFAULT);
    }

    static Paths addWithOidcIssuer(Path evidence, Path policy) throws Exception {
        Crypto crypto = createCrypto(policy.getParent().resolve("crypto-work"), OIDC_ISSUER);
        configureTrust(policy, crypto, OIDC_ISSUER);
        return writeAttestations(evidence, policy, crypto, Variant.DEFAULT);
    }

    static Paths addWithMultiLeafProof(Path evidence, Path policy) throws Exception {
        Crypto crypto = createCrypto(policy.getParent().resolve("crypto-work"), null);
        configureTrust(policy, crypto, BUILDER_ISSUER);
        return writeAttestations(evidence, policy, crypto, Variant.MULTI_LEAF);
    }

    static Paths addWithMissingArtifactSignature(Path evidence, Path policy) throws Exception {
        Crypto crypto = createCrypto(policy.getParent().resolve("crypto-work"), null);
        configureTrust(policy, crypto, BUILDER_ISSUER);
        configureArtifactSignatureTrust(policy, crypto);
        return writeAttestations(evidence, policy, crypto, Variant.DEFAULT);
    }

    static Paths addWithArtifactSignature(Path evidence, Path policy) throws Exception {
        Crypto crypto = createCrypto(policy.getParent().resolve("crypto-work"), null);
        configureTrust(policy, crypto, BUILDER_ISSUER);
        configureArtifactSignatureTrust(policy, crypto);
        return writeAttestations(evidence, policy, crypto, Variant.ARTIFACT_SIGNATURE);
    }

    static Paths addWithTamperedArtifactSignature(Path evidence, Path policy) throws Exception {
        Crypto crypto = createCrypto(policy.getParent().resolve("crypto-work"), null);
        configureTrust(policy, crypto, BUILDER_ISSUER);
        configureArtifactSignatureTrust(policy, crypto);
        return writeAttestations(evidence, policy, crypto, Variant.TAMPERED_ARTIFACT_SIGNATURE);
    }

    static Paths addWithOutOfWindowArtifactSignature(Path evidence, Path policy) throws Exception {
        Crypto crypto = createCrypto(policy.getParent().resolve("crypto-work"), null);
        configureTrust(policy, crypto, BUILDER_ISSUER);
        configureArtifactSignatureTrust(policy, crypto);
        return writeAttestations(evidence, policy, crypto, Variant.OUT_OF_WINDOW_ARTIFACT_SIGNATURE);
    }

    static Paths addWithFuturePredicateTimes(Path evidence, Path policy) throws Exception {
        Crypto crypto = createCrypto(policy.getParent().resolve("crypto-work"), null);
        configureTrust(policy, crypto, BUILDER_ISSUER);
        return writeAttestations(evidence, policy, crypto, Variant.FUTURE_PREDICATE_TIMES);
    }

    /** 为漏洞库 manifest 写入真实 messageSignature bundle，并同步 retained trust authority。 */
    static void addDatabaseSignature(Path evidence, Path policy) throws Exception {
        Crypto crypto = createCrypto(policy.getParent().resolve("database-crypto-work"), null);
        Path trustDirectory = policy.getParent().resolve("trust");
        Path material = trustDirectory.resolve("vulnerability-database-material.pem");
        String publicPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(crypto.rekor().getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        Files.writeString(material,
                "# TEST_ONLY DATABASE SIGSTORE TRUST\n" + crypto.rootPem() + publicPem,
                StandardCharsets.US_ASCII);

        String keyId = BUILDER_ISSUER + "|" + BUILDER_ID;
        Path databaseTrust = trustDirectory.resolve("vulnerability-database.tsv");
        Files.write(databaseTrust, List.of(
                "sourceId\tscheme\tkeyId\tmaterialPath\tmaterialSha256\tminimumSnapshotSequence",
                "fixture-db\tSIGSTORE\t" + keyId
                        + "\ttrust/vulnerability-database-material.pem\t"
                        + sha256(Files.readAllBytes(material)) + "\t0"),
                StandardCharsets.UTF_8);
        List<String> authorities = new ArrayList<>(Files.readAllLines(
                policy.getParent().resolve("authorities.tsv"), StandardCharsets.UTF_8));
        authorities.set(3, "VULNERABILITY_DATABASE_TRUST_ROOT\ttrust/vulnerability-database.tsv\t"
                + sha256(Files.readAllBytes(databaseTrust)));
        ReleasePolicyParserContractTests.replaceReference(
                policy, "productionAuthoritiesManifest", authorities);

        Path manifest = evidence.resolve("security/vulnerability/database/manifest.tsv");
        Path signature = evidence.resolve("security/vulnerability/database/manifest.sig");
        writeMessageSignatureBundle(signature, manifest, crypto);
        List<String> scanInputs = new ArrayList<>(Files.readAllLines(
                evidence.resolve("security/vulnerability/scan-inputs.tsv"), StandardCharsets.UTF_8));
        String[] database = scanInputs.get(3).split("\t", -1);
        database[8] = sha256(Files.readAllBytes(signature));
        database[9] = keyId;
        scanInputs.set(3, String.join("\t", database));
        Files.write(evidence.resolve("security/vulnerability/scan-inputs.tsv"),
                scanInputs, StandardCharsets.UTF_8);
    }

    private static Crypto createCrypto(Path directory, String oidcIssuer) throws Exception {
        Files.createDirectory(directory);
        Path rootStore = directory.resolve("root.p12");
        Path leafStore = directory.resolve("leaf.p12");
        Path request = directory.resolve("leaf.csr");
        Path signedLeaf = directory.resolve("leaf.crt");
        Path rootPem = directory.resolve("root.pem");
        keytool("-genkeypair", "-alias", "root", "-keyalg", "EC", "-groupname", "secp256r1",
                "-dname", "CN=TFI Test Root", "-validity", "3650", "-ext", "bc=ca:true",
                "-storetype", "PKCS12", "-keystore", rootStore.toString(), "-storepass", "changeit",
                "-keypass", "changeit", "-noprompt");
        keytool("-genkeypair", "-alias", "leaf", "-keyalg", "EC", "-groupname", "secp256r1",
                "-dname", "CN=TFI Test Builder", "-validity", "365",
                "-ext", "san=uri:" + BUILDER_ID, "-storetype", "PKCS12",
                "-keystore", leafStore.toString(), "-storepass", "changeit",
                "-keypass", "changeit", "-noprompt");
        keytool("-certreq", "-alias", "leaf", "-keystore", leafStore.toString(),
                "-storepass", "changeit", "-file", request.toString(),
                "-ext", "san=uri:" + BUILDER_ID);
        List<String> certificateArguments = new ArrayList<>(List.of(
                "-gencert", "-alias", "root", "-keystore", rootStore.toString(),
                "-storepass", "changeit", "-infile", request.toString(),
                "-outfile", signedLeaf.toString(), "-rfc", "-validity", "365",
                "-ext", "KU=digitalSignature", "-ext", "EKU=codeSigning",
                "-ext", "san=uri:" + BUILDER_ID));
        if (oidcIssuer != null) {
            certificateArguments.add("-ext");
            certificateArguments.add(FULCIO_ISSUER_OID + "="
                    + HexFormat.of().formatHex(oidcIssuer.getBytes(StandardCharsets.UTF_8)));
        }
        keytool(certificateArguments.toArray(String[]::new));
        keytool("-exportcert", "-alias", "root", "-keystore", rootStore.toString(),
                "-storepass", "changeit", "-rfc", "-file", rootPem.toString());
        keytool("-importcert", "-alias", "root", "-keystore", leafStore.toString(),
                "-storepass", "changeit", "-file", rootPem.toString(), "-noprompt");
        keytool("-importcert", "-alias", "leaf", "-keystore", leafStore.toString(),
                "-storepass", "changeit", "-file", signedLeaf.toString(), "-noprompt");

        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(leafStore)) {
            store.load(input, PASSWORD);
        }
        PrivateKey leafKey = (PrivateKey) store.getKey("leaf", PASSWORD);
        Certificate[] chain = store.getCertificateChain("leaf");
        X509Certificate leaf = (X509Certificate) chain[0];
        X509Certificate root = (X509Certificate) chain[1];
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair rekor = generator.generateKeyPair();
        String rootText = Files.readString(rootPem, StandardCharsets.US_ASCII);
        for (Path privateFile : List.of(rootStore, leafStore, request, signedLeaf, rootPem)) {
            Files.delete(privateFile);
        }
        Files.delete(directory);
        return new Crypto(leafKey, leaf, root, rekor, rootText,
                oidcIssuer == null ? BUILDER_ISSUER : oidcIssuer);
    }

    private static void configureTrust(Path policy, Crypto crypto, String issuer) throws Exception {
        Path trustDirectory = policy.getParent().resolve("trust");
        Path material = trustDirectory.resolve("provenance-material.pem");
        String publicPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(crypto.rekor().getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        Files.writeString(material, "# TEST_ONLY SIGSTORE TRUST\n" + crypto.rootPem() + publicPem,
                StandardCharsets.US_ASCII);
        Path provenance = trustDirectory.resolve("provenance.tsv");
        Files.write(provenance, List.of(
                "scheme\tkeyId\tmaterialPath\tmaterialSha256",
                "SIGSTORE\t" + issuer + "|" + BUILDER_ID
                        + "\ttrust/provenance-material.pem\t" + sha256(Files.readAllBytes(material))),
                StandardCharsets.UTF_8);
        List<String> authorities = new ArrayList<>(Files.readAllLines(
                policy.getParent().resolve("authorities.tsv"), StandardCharsets.UTF_8));
        authorities.set(0, "PROVENANCE_TRUST_ROOT\ttrust/provenance.tsv\t"
                + sha256(Files.readAllBytes(provenance)));
        ReleasePolicyParserContractTests.replaceReference(
                policy, "productionAuthoritiesManifest", authorities);
        ReleaseToolchainEvidenceTestFixture.replacePolicyValue(
                policy, "trustedBuilder", issuer + "|" + BUILDER_ID);
    }

    private static void configureArtifactSignatureTrust(Path policy, Crypto crypto) throws Exception {
        Path trustDirectory = policy.getParent().resolve("trust");
        Path material = trustDirectory.resolve("provenance-material.pem");
        Path manifest = trustDirectory.resolve("artifact-signatures.tsv");
        Files.write(manifest, List.of(
                "scheme\tkeyId\tmaterialPath\tmaterialSha256",
                "SIGSTORE\t" + BUILDER_ISSUER + "|" + BUILDER_ID
                        + "\ttrust/provenance-material.pem\t"
                        + sha256(Files.readAllBytes(material))), StandardCharsets.UTF_8);
        List<String> authorities = new ArrayList<>(Files.readAllLines(
                policy.getParent().resolve("authorities.tsv"), StandardCharsets.UTF_8));
        authorities.set(2, "ARTIFACT_SIGNATURE_TRUST_ROOT\ttrust/artifact-signatures.tsv\t"
                + sha256(Files.readAllBytes(manifest)));
        ReleasePolicyParserContractTests.replaceReference(
                policy, "productionAuthoritiesManifest", authorities);
        ReleaseToolchainEvidenceTestFixture.replacePolicyValue(
                policy, "requiredSignatures", "SIGSTORE");
    }

    private static void keytool(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException("keytool fixture generation failed: " + output);
        }
    }

    private static Paths writeAttestations(
            Path evidence,
            Path policy,
            Crypto crypto,
            Variant variant) throws Exception {
        Path metadata = evidence.resolve("metadata");
        Files.writeString(metadata.resolve("expected-commands.tsv"), "TEST_ONLY EXPECTED COMMANDS\n",
                StandardCharsets.UTF_8);
        Files.writeString(evidence.resolve("commands.tsv"), "TEST_ONLY COMMAND LEDGER\n",
                StandardCharsets.UTF_8);
        Path publishable = metadata.resolve("publishable-artifacts.tsv");
        Path artifact = evidence.resolve("artifacts/runtime-dependencies/tfi-compare.jar");
        String repositoryPath = "com/syy/tfi-compare/4.0.0/tfi-compare-4.0.0.jar";
        Path publishedArtifact = evidence.resolve("artifacts/publishable-repository")
                .resolve(repositoryPath);
        Files.createDirectories(publishedArtifact.getParent());
        Files.copy(artifact, publishedArtifact);
        List<String> publishableRows = new ArrayList<>(List.of(
                "ordinal\tsubjectOrdinal\tsubjectCoordinate\trepositoryPath\trole\t"
                        + "sidecarKind\tsha256",
                "1\t-\tcom.syy:tfi-compare:jar:4.0.0\t" + repositoryPath
                        + "\tBINARY\t-\t" + sha256(Files.readAllBytes(publishedArtifact))));
        List<Map<String, Object>> artifactSubjects = new ArrayList<>();
        artifactSubjects.add(subject(
                "artifacts/publishable-repository/" + repositoryPath, publishedArtifact));
        if (variant.artifactSignature()) {
            Path sidecar = Path.of(publishedArtifact + ".sigstore.json");
            MessageBundle message = writeMessageSignatureBundle(sidecar, publishedArtifact, crypto);
            if (variant.tamperedArtifactSignature()) {
                tamperBase64Field(sidecar, "\"signature\":\"");
            }
            String sidecarSha = sha256(Files.readAllBytes(sidecar));
            publishableRows.add("2\t1\tcom.syy:tfi-compare:jar:4.0.0\t" + repositoryPath
                    + ".sigstore.json\tSIGNATURE\tSIGSTORE\t" + sidecarSha);
            artifactSubjects.add(subject(
                    "artifacts/publishable-repository/" + repositoryPath + ".sigstore.json",
                    sidecar));
            Path results = evidence.resolve("supply-chain/signatures/artifact-signature-results.tsv");
            Files.createDirectories(results.getParent());
            Files.write(results, List.of(
                    "subjectOrdinal\tscheme\tsubjectSha256\tsidecarPath\tsidecarSha256\t"
                            + "signerKeyId\tdigestAlgorithm\tsignatureAlgorithm\t"
                            + "integratedTime\tstatus",
                    "1\tSIGSTORE\t" + sha256(Files.readAllBytes(publishedArtifact))
                            + "\tartifacts/publishable-repository/" + repositoryPath
                            + ".sigstore.json\t" + sidecarSha + "\t" + BUILDER_ISSUER + "|"
                            + BUILDER_ID + "\tSHA2_256\tECDSA_P256_SHA256\t"
                            + message.integratedTime() + "\tPASS"), StandardCharsets.UTF_8);
        }
        Files.write(publishable, publishableRows, StandardCharsets.UTF_8);

        String now = (variant.futurePredicateTimes()
                ? Instant.now().plusSeconds(60) : Instant.now())
                .truncatedTo(ChronoUnit.SECONDS).toString();
        String buildStarted = (variant.futurePredicateTimes()
                ? Instant.parse(now) : Instant.parse(now).minusSeconds(5)).toString();
        String[] workflow = policyValue(policy, "provenanceWorkflow").split("\\|", -1);
        Map<String, Object> artifactPredicate = new LinkedHashMap<>();
        artifactPredicate.put("productionPolicySha256", sha256(Files.readAllBytes(policy)));
        artifactPredicate.put("repository", policyValue(policy, "repository"));
        artifactPredicate.put("protectedRef", policyValue(policy, "protectedRef"));
        artifactPredicate.put("candidateRevision", policyValue(policy, "candidateRevision"));
        artifactPredicate.put("finalVersion", policyValue(policy, "finalVersion"));
        artifactPredicate.put("releaseTarget", policyValue(policy, "releaseTarget"));
        artifactPredicate.put("publishableArtifactSetSha256", sha256(Files.readAllBytes(publishable)));
        artifactPredicate.put("builderIssuer", crypto.issuer());
        artifactPredicate.put("builderId", BUILDER_ID);
        artifactPredicate.put("workflowRepository", workflow[0]);
        artifactPredicate.put("workflowPath", workflow[1]);
        artifactPredicate.put("workflowRevision", workflow[2]);
        artifactPredicate.put("expectedCommandsSha256", sha256(Files.readAllBytes(
                metadata.resolve("expected-commands.tsv"))));
        artifactPredicate.put("releaseExecutionPolicySha256",
                policyValue(policy, "releaseExecutionPolicySha256"));
        artifactPredicate.put("buildToolchainManifestSha256",
                policyValue(policy, "buildToolchainManifestSha256"));
        artifactPredicate.put("actualBuildToolchainSha256", sha256(Files.readAllBytes(
                evidence.resolve("supply-chain/tool-closures/build-toolchain.tsv"))));
        artifactPredicate.put("vulnerabilityScannerBundleSha256",
                bundleSha(policyValue(policy, "vulnerabilityScanner")));
        artifactPredicate.put("secretScannerBundleSha256",
                bundleSha(policyValue(policy, "secretScanner")));
        artifactPredicate.put("sbomGeneratorBundleSha256",
                bundleSha(policyValue(policy, "sbomGenerator")));
        artifactPredicate.put("buildStartedAtUtc", buildStarted);
        artifactPredicate.put("buildFinishedAtUtc", now);
        Path artifactBundle = evidence.resolve(
                "supply-chain/provenance/artifact-provenance.sigstore.json");
        writeBundle(artifactBundle, statement(
                "https://taskflowinsight.dev/attestation/artifact-provenance/v1",
                artifactSubjects,
                artifactPredicate), crypto, variant.multiLeafProof());

        // Publishable bytes 必须先进入 first scan；三层签名自身属于固定后置路径，避免签名与 scope 自引用。
        ReleaseSecurityEvidenceTestFixture.add(evidence, policy);
        ReleaseToolchainEvidenceTestFixture.addReleaseExecutions(evidence, policy);
        Files.write(metadata.resolve("actual-command-ledgers.tsv"), List.of(
                "ledgerPath\tsha256",
                "commands.tsv\t" + sha256(Files.readAllBytes(evidence.resolve("commands.tsv"))),
                "security/secret-scan/commands.tsv\t" + sha256(Files.readAllBytes(
                        evidence.resolve("security/secret-scan/commands.tsv")))), StandardCharsets.UTF_8);

        Map<String, Object> secretPredicate = new LinkedHashMap<>();
        Path firstScope = evidence.resolve("security/secret-scan/scope.tsv");
        Path firstReport = evidence.resolve("security/secret-scan/report.json");
        Path secondScope = evidence.resolve("security/secret-scan/report-self-scan-scope.tsv");
        Path secondReport = evidence.resolve("security/secret-scan/report-self-scan.tsv");
        secretPredicate.put("productionPolicySha256", sha256(Files.readAllBytes(policy)));
        secretPredicate.put("secretScannerBundleSha256", bundleSha(policyValue(policy, "secretScanner")));
        secretPredicate.put("firstScopeSha256", sha256(Files.readAllBytes(firstScope)));
        secretPredicate.put("firstReportSha256", sha256(Files.readAllBytes(firstReport)));
        secretPredicate.put("firstActualExit", 0);
        secretPredicate.put("firstFindings", 0);
        secretPredicate.put("secondScopeSha256", sha256(Files.readAllBytes(secondScope)));
        secretPredicate.put("secondReportSha256", sha256(Files.readAllBytes(secondReport)));
        secretPredicate.put("secondActualExit", 0);
        secretPredicate.put("secondFindings", 0);
        secretPredicate.put("releaseExecutionsSha256", sha256(Files.readAllBytes(
                metadata.resolve("release-executions.tsv"))));
        secretPredicate.put("toolExecutionsSha256", sha256(Files.readAllBytes(
                metadata.resolve("tool-executions.tsv"))));
        secretPredicate.put("executionStartedAtUtc", now);
        secretPredicate.put("executionFinishedAtUtc", now);
        Path secretBundle = evidence.resolve("security/secret-scan/process-attestation.sigstore.json");
        writeBundle(secretBundle, statement(
                "https://taskflowinsight.dev/attestation/secret-scan/v1",
                List.of(
                        subject("security/secret-scan/report-self-scan-scope.tsv", secondScope),
                        subject("security/secret-scan/report-self-scan.tsv", secondReport),
                        subject("security/secret-scan/report.json", firstReport),
                        subject("security/secret-scan/scope.tsv", firstScope)),
                secretPredicate), crypto, variant.multiLeafProof());

        Path subjectManifest = metadata.resolve("evidence-subject-manifest.tsv");
        Files.write(subjectManifest, List.of(
                "role\tevidencePath\tsha256",
                "ARTIFACT_PROVENANCE\tsupply-chain/provenance/artifact-provenance.sigstore.json\t"
                        + sha256(Files.readAllBytes(artifactBundle)),
                "SECRET_PROCESS\tsecurity/secret-scan/process-attestation.sigstore.json\t"
                        + sha256(Files.readAllBytes(secretBundle))), StandardCharsets.UTF_8);
        Map<String, Object> evidencePredicate = evidencePredicate(
                evidence, policy, publishable, artifactBundle, secretBundle, subjectManifest,
                workflow, now, crypto);
        Path evidenceBundle = evidence.resolve(
                "supply-chain/provenance/evidence-attestation.sigstore.json");
        writeBundle(evidenceBundle, statement(
                "https://taskflowinsight.dev/attestation/release-evidence/v1",
                List.of(subject("metadata/evidence-subject-manifest.tsv", subjectManifest)),
                evidencePredicate), crypto, variant.multiLeafProof());
        return new Paths(artifactBundle, secretBundle, evidenceBundle);
    }

    private static void tamperBase64Field(Path path, String marker) throws Exception {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        int markerIndex = content.indexOf(marker);
        if (markerIndex < 0) {
            throw new IllegalStateException("Sigstore fixture field is missing: " + marker);
        }
        int start = markerIndex + marker.length();
        char replacement = content.charAt(start) == 'A' ? 'B' : 'A';
        Files.writeString(path, content.substring(0, start) + replacement
                + content.substring(start + 1), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> evidencePredicate(
            Path evidence, Path policy, Path publishable, Path artifactBundle, Path secretBundle,
            Path subjectManifest, String[] workflow, String now, Crypto crypto) throws Exception {
        Map<String, Object> predicate = new LinkedHashMap<>();
        predicate.put("productionPolicySha256", sha256(Files.readAllBytes(policy)));
        predicate.put("evidenceSubjectManifestSha256", sha256(Files.readAllBytes(subjectManifest)));
        predicate.put("publishableArtifactSetSha256", sha256(Files.readAllBytes(publishable)));
        predicate.put("actualCommandLedgersSha256", sha256(Files.readAllBytes(
                evidence.resolve("metadata/actual-command-ledgers.tsv"))));
        predicate.put("releaseExecutionsSha256", sha256(Files.readAllBytes(
                evidence.resolve("metadata/release-executions.tsv"))));
        predicate.put("toolExecutionsSha256", sha256(Files.readAllBytes(
                evidence.resolve("metadata/tool-executions.tsv"))));
        predicate.put("actualBuildToolchainSha256", sha256(Files.readAllBytes(
                evidence.resolve("supply-chain/tool-closures/build-toolchain.tsv"))));
        predicate.put("artifactProvenanceSha256", sha256(Files.readAllBytes(artifactBundle)));
        predicate.put("secretProcessAttestationSha256", sha256(Files.readAllBytes(secretBundle)));
        predicate.put("builderIssuer", crypto.issuer());
        predicate.put("builderId", BUILDER_ID);
        predicate.put("workflowRepository", workflow[0]);
        predicate.put("workflowPath", workflow[1]);
        predicate.put("workflowRevision", workflow[2]);
        predicate.put("attestedAtUtc", now);
        return predicate;
    }

    private static Map<String, Object> statement(
            String predicateType,
            List<Map<String, Object>> subjects,
            Map<String, Object> predicate) {
        Map<String, Object> statement = new LinkedHashMap<>();
        statement.put("_type", "https://in-toto.io/Statement/v1");
        statement.put("subject", subjects);
        statement.put("predicateType", predicateType);
        statement.put("predicate", predicate);
        return statement;
    }

    private static Map<String, Object> subject(String name, Path path) throws Exception {
        Map<String, Object> digest = Map.of("sha256", sha256(Files.readAllBytes(path)));
        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("name", name);
        subject.put("digest", digest);
        return subject;
    }

    private static MessageBundle writeMessageSignatureBundle(
            Path path, Path artifact, Crypto crypto) throws Exception {
        byte[] artifactBytes = Files.readAllBytes(artifact);
        byte[] digest = sha256Bytes(artifactBytes);
        byte[] signatureBytes = sign(crypto.leafKey(), artifactBytes);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageDigest", Map.of(
                "algorithm", "SHA2_256",
                "digest", Base64.getEncoder().encodeToString(digest)));
        message.put("signature", Base64.getEncoder().encodeToString(signatureBytes));

        Map<String, Object> hash = Map.of(
                "algorithm", "sha256", "value", HexFormat.of().formatHex(digest));
        Map<String, Object> publicKey = Map.of(
                "content", Base64.getEncoder().encodeToString(certificatePem(crypto.leaf())));
        Map<String, Object> signature = new LinkedHashMap<>();
        signature.put("content", Base64.getEncoder().encodeToString(signatureBytes));
        signature.put("publicKey", publicKey);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("apiVersion", "0.0.1");
        body.put("kind", "hashedrekord");
        body.put("spec", Map.of("data", Map.of("hash", hash), "signature", signature));
        byte[] bodyBytes = canonical(body);
        byte[] leafHash = sha256Bytes(prefixed((byte) 0, bodyBytes));
        String integratedTime = Long.toString(Instant.now().getEpochSecond());
        byte[] logId = sha256Bytes(crypto.rekor().getPublic().getEncoded());
        byte[] set = sign(crypto.rekor().getPrivate(),
                setPayload(bodyBytes, integratedTime, "0", logId));
        String checkpointText = "tfi.test.log\n1\n"
                + Base64.getEncoder().encodeToString(leafHash) + "\n";
        byte[] checkpointSignature = sign(
                crypto.rekor().getPrivate(), checkpointText.getBytes(StandardCharsets.UTF_8));
        byte[] noteSignature = new byte[checkpointSignature.length + 4];
        System.arraycopy(sha256Bytes(crypto.rekor().getPublic().getEncoded()), 0,
                noteSignature, 0, 4);
        System.arraycopy(checkpointSignature, 0, noteSignature, 4, checkpointSignature.length);
        String checkpoint = checkpointText + "\n\u2014 tfi.test.log "
                + Base64.getEncoder().encodeToString(noteSignature) + "\n";

        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("checkpoint", Map.of("envelope", checkpoint));
        proof.put("hashes", List.of());
        proof.put("logIndex", "0");
        proof.put("rootHash", Base64.getEncoder().encodeToString(leafHash));
        proof.put("treeSize", "1");
        Map<String, Object> tlog = new LinkedHashMap<>();
        tlog.put("canonicalizedBody", Base64.getEncoder().encodeToString(bodyBytes));
        tlog.put("inclusionPromise", Map.of(
                "signedEntryTimestamp", Base64.getEncoder().encodeToString(set)));
        tlog.put("inclusionProof", proof);
        tlog.put("integratedTime", integratedTime);
        tlog.put("kindVersion", Map.of("kind", "hashedrekord", "version", "0.0.1"));
        tlog.put("logId", Map.of("keyId", Base64.getEncoder().encodeToString(logId)));
        tlog.put("logIndex", "0");

        Map<String, Object> chain = Map.of("certificates", List.of(
                Map.of("rawBytes", Base64.getEncoder().encodeToString(crypto.leaf().getEncoded())),
                Map.of("rawBytes", Base64.getEncoder().encodeToString(crypto.root().getEncoded()))));
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("tlogEntries", List.of(tlog));
        material.put("x509CertificateChain", chain);
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("mediaType", "application/vnd.dev.sigstore.bundle.v0.3+json");
        bundle.put("messageSignature", message);
        bundle.put("verificationMaterial", material);
        Files.write(path, canonical(bundle));
        return new MessageBundle(integratedTime);
    }

    private static byte[] setPayload(
            byte[] body, String integratedTime, String logIndex, byte[] logId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("body", Base64.getEncoder().encodeToString(body));
        payload.put("integratedTime", Long.parseLong(integratedTime));
        payload.put("logID", HexFormat.of().formatHex(logId));
        payload.put("logIndex", Long.parseLong(logIndex));
        return canonical(payload);
    }

    private static byte[] certificatePem(X509Certificate certificate) throws Exception {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(certificate.getEncoded());
        return ("-----BEGIN CERTIFICATE-----\n" + encoded
                + "\n-----END CERTIFICATE-----\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static void writeBundle(
            Path path, Map<String, Object> statement, Crypto crypto, boolean multiLeafProof)
            throws Exception {
        byte[] payload = canonical(statement);
        String payloadType = "application/vnd.in-toto+json";
        byte[] dsseSignature = sign(crypto.leafKey(), pae(payloadType, payload));
        Map<String, Object> signature = Map.of(
                "keyid", "", "sig", Base64.getEncoder().encodeToString(dsseSignature));
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("payload", Base64.getEncoder().encodeToString(payload));
        envelope.put("payloadType", payloadType);
        envelope.put("signatures", List.of(signature));
        String certificate = Base64.getEncoder().encodeToString(crypto.leaf().getEncoded());

        String tlogPayload = Base64.getEncoder().encodeToString(
                Base64.getEncoder().encodeToString(payload).getBytes(StandardCharsets.UTF_8));
        String tlogSignature = Base64.getEncoder().encodeToString(
                Base64.getEncoder().encodeToString(dsseSignature).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> tlogEnvelope = new LinkedHashMap<>();
        tlogEnvelope.put("payload", tlogPayload);
        tlogEnvelope.put("payloadType", payloadType);
        tlogEnvelope.put("signatures", List.of(Map.of(
                "publicKey", Base64.getEncoder().encodeToString(certificatePem(crypto.leaf())),
                "sig", tlogSignature)));
        Map<String, Object> bodyContent = new LinkedHashMap<>();
        bodyContent.put("envelope", tlogEnvelope);
        bodyContent.put("hash", Map.of(
                "algorithm", "sha256", "value", sha256(canonical(tlogEnvelope))));
        bodyContent.put("payloadHash", Map.of(
                "algorithm", "sha256", "value", sha256(payload)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("apiVersion", "0.0.2");
        body.put("kind", "intoto");
        body.put("spec", Map.of("content", bodyContent));
        byte[] bodyBytes = canonical(body);
        byte[] leafHash = sha256Bytes(prefixed((byte) 0, bodyBytes));
        byte[] siblingHash = sha256Bytes(prefixed(
                (byte) 0, "TEST_ONLY_OTHER_LOG_LEAF".getBytes(StandardCharsets.UTF_8)));
        byte[] rootHash = multiLeafProof ? nodeHash(leafHash, siblingHash) : leafHash;
        String treeSize = multiLeafProof ? "2" : "1";
        String integratedTime = Long.toString(Instant.now().getEpochSecond());
        byte[] logId = sha256Bytes(crypto.rekor().getPublic().getEncoded());
        byte[] set = sign(crypto.rekor().getPrivate(),
                setPayload(bodyBytes, integratedTime, "0", logId));
        String checkpointText = "tfi.test.log\n" + treeSize + "\n"
                + Base64.getEncoder().encodeToString(rootHash) + "\n";
        byte[] checkpointSignature = sign(
                crypto.rekor().getPrivate(), checkpointText.getBytes(StandardCharsets.UTF_8));
        byte[] noteSignature = new byte[checkpointSignature.length + 4];
        System.arraycopy(sha256Bytes(crypto.rekor().getPublic().getEncoded()), 0,
                noteSignature, 0, 4);
        System.arraycopy(checkpointSignature, 0, noteSignature, 4, checkpointSignature.length);
        String checkpoint = checkpointText + "\n\u2014 tfi.test.log "
                + Base64.getEncoder().encodeToString(noteSignature) + "\n";

        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("checkpoint", Map.of("envelope", checkpoint));
        proof.put("hashes", multiLeafProof
                ? List.of(Base64.getEncoder().encodeToString(siblingHash)) : List.of());
        proof.put("logIndex", "0");
        proof.put("rootHash", Base64.getEncoder().encodeToString(rootHash));
        proof.put("treeSize", treeSize);
        Map<String, Object> tlog = new LinkedHashMap<>();
        tlog.put("canonicalizedBody", Base64.getEncoder().encodeToString(bodyBytes));
        tlog.put("inclusionPromise", Map.of(
                "signedEntryTimestamp", Base64.getEncoder().encodeToString(set)));
        tlog.put("inclusionProof", proof);
        tlog.put("integratedTime", integratedTime);
        tlog.put("kindVersion", Map.of("kind", "intoto", "version", "0.0.2"));
        tlog.put("logId", Map.of("keyId", Base64.getEncoder().encodeToString(logId)));
        tlog.put("logIndex", "0");

        Map<String, Object> chain = Map.of("certificates", List.of(
                Map.of("rawBytes", certificate),
                Map.of("rawBytes", Base64.getEncoder().encodeToString(crypto.root().getEncoded()))));
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("tlogEntries", List.of(tlog));
        material.put("x509CertificateChain", chain);
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("dsseEnvelope", envelope);
        bundle.put("mediaType", "application/vnd.dev.sigstore.bundle.v0.3+json");
        bundle.put("verificationMaterial", material);
        Files.createDirectories(path.getParent());
        Files.write(path, canonical(bundle));
    }

    private static byte[] pae(String payloadType, byte[] payload) {
        byte[] type = payloadType.getBytes(StandardCharsets.UTF_8);
        String prefix = "DSSEv1 " + type.length + " ";
        String middle = " " + payload.length + " ";
        byte[] first = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] second = middle.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[first.length + type.length + second.length + payload.length];
        int offset = 0;
        System.arraycopy(first, 0, result, offset, first.length);
        offset += first.length;
        System.arraycopy(type, 0, result, offset, type.length);
        offset += type.length;
        System.arraycopy(second, 0, result, offset, second.length);
        offset += second.length;
        System.arraycopy(payload, 0, result, offset, payload.length);
        return result;
    }

    private static byte[] sign(PrivateKey key, byte[] value) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(key);
        signature.update(value);
        return signature.sign();
    }

    private static byte[] prefixed(byte prefix, byte[] value) {
        byte[] result = new byte[value.length + 1];
        result[0] = prefix;
        System.arraycopy(value, 0, result, 1, value.length);
        return result;
    }

    private static byte[] nodeHash(byte[] left, byte[] right) throws Exception {
        byte[] input = new byte[1 + left.length + right.length];
        input[0] = 1;
        System.arraycopy(left, 0, input, 1, left.length);
        System.arraycopy(right, 0, input, 1 + left.length, right.length);
        return sha256Bytes(input);
    }

    private static byte[] canonical(Object value) {
        StringBuilder output = new StringBuilder();
        appendJson(output, value);
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendJson(StringBuilder output, Object value) {
        if (value instanceof Map<?, ?> map) {
            output.append('{');
            List<String> keys = map.keySet().stream().map(Object::toString).sorted().toList();
            for (int index = 0; index < keys.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                String key = keys.get(index);
                appendJson(output, key);
                output.append(':');
                appendJson(output, map.get(key));
            }
            output.append('}');
        } else if (value instanceof List<?> list) {
            output.append('[');
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                appendJson(output, list.get(index));
            }
            output.append(']');
        } else if (value instanceof String string) {
            output.append('"');
            for (int index = 0; index < string.length(); index++) {
                char character = string.charAt(index);
                switch (character) {
                    case '"' -> output.append("\\\"");
                    case '\\' -> output.append("\\\\");
                    case '\b' -> output.append("\\b");
                    case '\f' -> output.append("\\f");
                    case '\n' -> output.append("\\n");
                    case '\r' -> output.append("\\r");
                    case '\t' -> output.append("\\t");
                    default -> {
                        if (character < 0x20) {
                            output.append(String.format("\\u%04x", (int) character));
                        } else {
                            output.append(character);
                        }
                    }
                }
            }
            output.append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            output.append(value);
        } else {
            throw new IllegalArgumentException("Unsupported JSON fixture value");
        }
    }

    private static String policyValue(Path policy, String key) throws Exception {
        return ReleaseToolchainEvidenceTestFixture.policyValue(policy, key);
    }

    private static String bundleSha(String identity) {
        return identity.substring(identity.indexOf("#bundle-sha256:") + "#bundle-sha256:".length());
    }

    private static byte[] sha256Bytes(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(sha256Bytes(bytes));
    }

    /** Temporary signing keys, public verification material, and the asserted issuer identity. */
    private record Crypto(
            PrivateKey leafKey,
            X509Certificate leaf,
            X509Certificate root,
            KeyPair rekor,
            String rootPem,
            String issuer) {
    }

    /** @param integratedTime Rekor inclusion epoch second written to the result row. */
    private record MessageBundle(String integratedTime) {
    }

    /** Fixture 只开放任务卡需要的四种可解释组合，避免布尔参数产生无效状态。 */
    private enum Variant {
        /** 单叶、无 artifact signature 的标准三层链。 */
        DEFAULT,
        /** 三层 bundle 均使用两叶 Rekor proof。 */
        MULTI_LEAF,
        /** 增加有效的 publishable artifact messageSignature。 */
        ARTIFACT_SIGNATURE,
        /** 外层 SHA 自洽但 artifact ECDSA 已篡改。 */
        TAMPERED_ARTIFACT_SIGNATURE,
        /** artifact signature 有效但早于 predicate 声明的 build window。 */
        OUT_OF_WINDOW_ARTIFACT_SIGNATURE,
        /** 三层 predicate 时间均晚于实际 Rekor integration。 */
        FUTURE_PREDICATE_TIMES;

        private boolean multiLeafProof() {
            return this == MULTI_LEAF;
        }

        private boolean artifactSignature() {
            return this == ARTIFACT_SIGNATURE
                    || this == TAMPERED_ARTIFACT_SIGNATURE
                    || this == OUT_OF_WINDOW_ARTIFACT_SIGNATURE;
        }

        private boolean tamperedArtifactSignature() {
            return this == TAMPERED_ARTIFACT_SIGNATURE;
        }

        private boolean futurePredicateTimes() {
            return this == OUT_OF_WINDOW_ARTIFACT_SIGNATURE || this == FUTURE_PREDICATE_TIMES;
        }
    }

    /** @param artifact artifact provenance; @param secret secret process; @param evidence final evidence. */
    record Paths(Path artifact, Path secret, Path evidence) {
    }
}
