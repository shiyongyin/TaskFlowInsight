package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 发布加固任务的最小最终集成审计。 */
class CompareReleaseHardeningCompletionAuditTests {
    /** 七张 owner 卡必须先独立完成，最终集成卡不得替代 owner 验证。 */
    private static final List<String> OWNER_CARDS = List.of(
            "01", "02", "03", "04", "06", "07", "08");
    /** Machine header 的全角冒号和单反引号属于协议，不能宽松解析同义文本。 */
    private static final Pattern MACHINE_HEADER = Pattern.compile(
            "^> \\*\\*([^*]+)\\*\\*：`([^`]+)`$");
    /** 05A 只允许替换这些运行时值，禁止在执行时临场改写命令结构。 */
    private static final Set<String> COMMAND_PLACEHOLDERS = Set.of(
            "RUN_REPO", "DISPOSABLE_REPO", "EVIDENCE", "CANDIDATE_VERSION",
            "CANDIDATE_REVISION", "AUDIT_MODE", "PRODUCTION_POLICY", "FINAL_VERSION");
    /** 发布流水线共有 51 个有序命令，其中 focused 回归固定占 25 个。 */
    private static final List<String> EXPECTED_COMMAND_IDS = List.of(
            "B-GIT-STATUS", "B-GIT-REVISION", "B-BASELINE-DIFF", "B-BASELINE-CHECK",
            "B-BASELINE-AUTHORITY", "B-INSTALL-CANDIDATE",
            "F01-COMPARE", "F01-ALL", "F01-EXAMPLES", "F02-OPS", "F02-ALL",
            "F03-SCRIPT-TEST", "F03-FLOW", "F03-STARTER", "F03-RATCHET", "F03-OPS", "F03-ALL",
            "F04-COMPARE", "F04-STARTER", "F04-EXAMPLES", "F04-ALL",
            "F06-COMPARE", "F06-OPS", "F06-EXAMPLES",
            "F07-PUBLISH", "F07-COMPARE", "F07-ARTIFACT",
            "F08-COMPARE", "F08-ALL", "F08-POLICY", "F-COMPLETION-PRE",
            "M-OWNERS-VERIFY",
            "C-STARTER", "C-OPS", "C-COMPOSED", "C-HIERARCHY", "C-ROLLBACK-BEFORE",
            "C-ROLLBACK-CANDIDATE", "C-ROLLBACK-AFTER", "C-MIXED",
            "A-BASELINE-CHECK", "A-CONTRACTS", "A-JAPICMP", "A-TREE",
            "P-JMH", "P-STRICT", "V-PORTFOLIO",
            "S-PUBLISH-ASSEMBLE", "S-SUPPLY-COLLECT", "S-SECRET-FINALIZE", "S-VERIFY-SUPPLY");
    /** exact-byte 摘要锁定 cwd、argv、退出语义和即时复制清单，防止只保持 ID 形状的漂移。 */
    private static final String EXPECTED_COMMANDS_SHA256 =
            "5072a70eee14e449f9e8e6374a8bd6444b7c53e2deb5abf3ca75fdcf1ab209d3";
    /** 只解析命令权威中显式占位符，不把 shell 转义或普通尖括号当作动态值。 */
    private static final Pattern COMMAND_PLACEHOLDER = Pattern.compile("<([A-Z_]+)>");
    /** 05A 必须保留且由 DOM verifier 逐个验真的 test report 路径闭集。 */
    private static final Set<String> EXPECTED_REPORT_PATHS = Set.of(
            "focused/hrd-01/tfi-compare/TEST-com.syy.taskflowinsight.compatibility.LegacySnapshotRemovalContractTests.xml",
            "focused/hrd-01/tfi-compare/TEST-com.syy.taskflowinsight.architecture.CompareArtifactStaticStateContractTests.xml",
            "focused/hrd-01/tfi-compare/TEST-com.syy.taskflowinsight.tracking.ssot.key.EntityKeyClassCacheContractTests.xml",
            "focused/hrd-02/tfi-all/TEST-com.syy.taskflowinsight.api.CompareOpsBootAutoConfigurationIntegrationTests.xml",
            "focused/hrd-02/tfi-ops-spring/TEST-com.syy.taskflowinsight.ops.compare.ObservedCompareOperationsContractTests.xml",
            "focused/hrd-03/tfi-compare-spring-starter/TEST-com.syy.taskflowinsight.compare.spring.CompareContextIsolationTests.xml",
            "focused/hrd-03/tfi-compare-spring-starter/TEST-com.syy.taskflowinsight.compare.spring.TfiTaskDeepTrackingDelegateContractTests.xml",
            "focused/hrd-03/tfi-ops-spring/TEST-com.syy.taskflowinsight.ops.compare.CompareOpsAutoConfigurationContractTests.xml",
            "focused/hrd-03/tfi-flow-spring-starter/TEST-com.syy.taskflowinsight.config.ContextMonitoringAutoConfigurationTest.xml",
            "focused/hrd-03/tfi-all/TEST-com.syy.taskflowinsight.api.CompareFlowContextIsolationIntegrationTests.xml",
            "focused/hrd-03/tfi-compare-spring-starter/TEST-com.syy.taskflowinsight.compare.spring.CompareStarterBuildConfigurationContractTests.xml",
            "focused/hrd-04/tfi-compare/TEST-com.syy.taskflowinsight.architecture.CompareDocumentationContractTests.xml",
            "focused/hrd-04/tfi-compare-spring-starter/TEST-com.syy.taskflowinsight.compare.spring.CompareAutoConfigurationContractTests.xml",
            "focused/hrd-04/tfi-compare-spring-starter/TEST-com.syy.taskflowinsight.compare.spring.CompareConfigurationContractTests.xml",
            "focused/hrd-04/tfi-examples/TEST-com.syy.taskflowinsight.demo.CompareProfilesStartupContractTests.xml",
            "focused/hrd-06/tfi-compare/TEST-com.syy.taskflowinsight.tracking.compare.internal.CompareContainerCapacityContractTests.xml",
            "focused/hrd-06/tfi-ops-spring/TEST-com.syy.taskflowinsight.store.StoreSensitiveLoggingContractTests.xml",
            "focused/hrd-06/tfi-ops-spring/TEST-com.syy.taskflowinsight.ops.compare.ObservedCompareOperationsContractTests.xml",
            "focused/hrd-06/tfi-examples/TEST-com.syy.taskflowinsight.benchmark.CompareProductionBenchmarkRunnerTests.xml",
            "focused/hrd-07/tfi-compare/TEST-com.syy.taskflowinsight.architecture.ComparePublishabilityContractTests.xml",
            "focused/hrd-07/tfi-compare/TEST-com.syy.taskflowinsight.architecture.PublishArtifactAssemblerContractTests.xml",
            "artifact-consumers/publish-layout/TEST-com.syy.taskflowinsight.it.PublishLayoutArtifactTests.xml",
            "focused/hrd-08/tfi-compare/TEST-com.syy.taskflowinsight.architecture.ReleasePolicyParserContractTests.xml",
            "focused/hrd-08/tfi-compare/TEST-com.syy.taskflowinsight.architecture.SupplyChainEvidenceContractTests.xml",
            "focused/hrd-08/tfi-compare/TEST-com.syy.taskflowinsight.architecture.SecretFinalizeContractTests.xml",
            "focused/hrd-08/tfi-all/TEST-com.syy.taskflowinsight.api.SensitiveLogCanaryIntegrationTests.xml",
            "artifact-consumers/starter-only/TEST-com.syy.taskflowinsight.it.StarterOnlyArtifactTests.xml",
            "artifact-consumers/ops-only/TEST-com.syy.taskflowinsight.it.OpsOnlyArtifactTests.xml",
            "artifact-consumers/composed-boot/TEST-com.syy.taskflowinsight.it.ComposedBootArtifactTests.xml",
            "artifact-consumers/context-hierarchy/TEST-com.syy.taskflowinsight.it.ContextHierarchyArtifactTests.xml",
            "artifact-consumers/baseline-upgrade-rollback/baseline-before/TEST-com.syy.taskflowinsight.it.StableFacadeSmokeTests.xml",
            "artifact-consumers/baseline-upgrade-rollback/candidate/TEST-com.syy.taskflowinsight.it.StableFacadeSmokeTests.xml",
            "artifact-consumers/baseline-upgrade-rollback/baseline-after/TEST-com.syy.taskflowinsight.it.StableFacadeSmokeTests.xml",
            "performance/TEST-com.syy.taskflowinsight.perf.TfiRoutingPerfGateTests.xml",
            "performance/TEST-com.syy.taskflowinsight.perf.TfiRoutingPerfGateIT.xml",
            "focused/hrd-05/tfi-compare/TEST-com.syy.taskflowinsight.architecture.CompareReleaseHardeningCompletionAuditTests.xml",
            "focused/hrd-05/tfi-compare/TEST-com.syy.taskflowinsight.architecture.ReleaseEvidenceVerifierContractTests.xml");

    @Test
    void lifecycleStateRequiresCompletedOwnersAndMinimalClosure() throws Exception {
        Path taskRoot = repositoryRoot().resolve("tfi-compare/docs/release-hardening-task");

        for (String card : OWNER_CARDS) {
            Map<String, String> values = headers(
                    taskRoot.resolve("TASK-CMP-HRD-" + card + ".md"));
            assertThat(values.get("deliveryStatus")).as("HRD-%s delivery", card)
                    .isEqualTo("COMPLETE");
            assertThat(values.get("reviewStatus")).as("HRD-%s review", card)
                    .isEqualTo("PASS");
        }

        Map<String, String> task = headers(taskRoot.resolve("TASK-CMP-HRD-05.md"));
        Map<String, String> index = headers(taskRoot.resolve("INDEX.md"));
        assertThat(task.get("deliveryStatus")).isIn("IN_PROGRESS", "COMPLETE");
        assertThat(task.get("reviewStatus")).isEqualTo(
                "COMPLETE".equals(task.get("deliveryStatus")) ? "PASS" : "PENDING");
        assertThat(index.get("deliveryStatus")).isEqualTo(task.get("deliveryStatus"));
        assertThat(index.get("reviewStatus")).isEqualTo(task.get("reviewStatus"));
    }

    @Test
    void expectedReportsAuthorityHasExactRequiredClosure() throws Exception {
        Path authority = repositoryRoot().resolve("scripts/release-evidence/expected-reports.tsv");
        List<String> lines = Files.readAllLines(authority, StandardCharsets.UTF_8);
        assertThat(lines.getFirst())
                .isEqualTo("phase\tmodule\treportPath\tminimumTests\tallowSkipped");
        Set<String> actual = new java.util.HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] row = line.split("\t", -1);
            assertThat(row).hasSize(5);
            assertThat(Integer.parseInt(row[3])).isPositive();
            assertThat(row[4]).isEqualTo("false");
            assertThat(actual.add(row[2])).as(row[2]).isTrue();
        }
        assertThat(actual).containsExactlyInAnyOrderElementsOf(EXPECTED_REPORT_PATHS);
    }

    @Test
    void expectedCommandsAuthorityHasExactOrderedClosure() throws Exception {
        Path authority = repositoryRoot().resolve("scripts/release-evidence/expected-commands.tsv");
        assertThat(java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(authority))))
                .isEqualTo(EXPECTED_COMMANDS_SHA256);
        List<String> lines = Files.readAllLines(authority, StandardCharsets.UTF_8);
        assertThat(lines.getFirst())
                .isEqualTo("ordinal\tcommandId\tphase\tcwd\targv\texpectedExit\timmediateCopy");
        assertThat(lines).hasSize(EXPECTED_COMMAND_IDS.size() + 1);

        java.util.HashSet<String> placeholders = new java.util.HashSet<>();
        for (int index = 1; index < lines.size(); index++) {
            String[] row = lines.get(index).split("\t", -1);
            assertThat(row).as("command row %s", index).hasSize(7);
            assertThat(row[0]).isEqualTo(Integer.toString(index));
            assertThat(row[1]).isEqualTo(EXPECTED_COMMAND_IDS.get(index - 1));
            assertThat(row[2]).isNotBlank();
            assertThat(row[3]).matches("<REPO_ROOT>|[A-Za-z0-9._/-]+");
            assertThat(row[4]).isNotBlank().doesNotContain("surefire.failIfNoSpecifiedTests");
            assertThat(row[5]).isEqualTo(row[1].equals("C-MIXED")
                    ? "NON_ZERO_DEPENDENCY_CONVERGENCE" : "0");
            assertThat(row[6]).isNotBlank().doesNotContain("*", "\\");

            Matcher matcher = COMMAND_PLACEHOLDER.matcher(row[4] + " " + row[6]);
            while (matcher.find()) {
                assertThat(COMMAND_PLACEHOLDERS).contains(matcher.group(1));
                placeholders.add(matcher.group(1));
            }
        }
        assertThat(EXPECTED_COMMAND_IDS.stream().filter(id -> id.startsWith("F")).count())
                .isEqualTo(25);
        assertThat(placeholders).containsExactlyInAnyOrderElementsOf(COMMAND_PLACEHOLDERS);
    }

    @Test
    void artifactConsumerRunnerRejectsIncompleteReleaseEvidenceInvocation() throws Exception {
        Path script = repositoryRoot().resolve("scripts/verify_tfi_compare_artifact_consumers.sh");
        Process process = new ProcessBuilder("bash", script.toString(), "--release-evidence").start();
        String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).isEqualTo(64);
        assertThat(error).contains(
                "--release-evidence <evidence-dir> --candidate-version <version>",
                "--run-repository <repository> --commands-ledger <commands.tsv>");
    }

    @Test
    void artifactConsumerCommandLoaderPublishesAuthorityWithoutClobberingCaller() throws Exception {
        Path script = repositoryRoot().resolve("scripts/verify_tfi_compare_artifact_consumers.sh");
        String source = Files.readString(script, StandardCharsets.UTF_8);
        int start = source.indexOf("load_expected_command() {");
        int end = source.indexOf("\n}\n\ncanonical_command_display()", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);

        Path authorityRoot = Files.createTempDirectory("artifact-command-loader");
        Path authority = authorityRoot.resolve("scripts/release-evidence/expected-commands.tsv");
        Files.createDirectories(authority.getParent());
        Files.writeString(authority, String.join("\n",
                "ordinal\tcommandId\tphase\tcwd\targv\texpectedExit\timmediateCopy",
                "33\tC-STARTER\tartifact-consumer\t<REPO_ROOT>\texpected-argv\t0\tevidence.xml",
                ""), StandardCharsets.UTF_8);

        String harness = """
                set -euo pipefail
                repository_root="$TFI_TEST_AUTHORITY_ROOT"
                %s
                command_id=CALLER-COMMAND
                load_expected_command C-STARTER
                printf '%%s\t%%s\t%%s\t%%s\t%%s\t%%s\t%%s\t%%s\n' \
                    "$command_id" "$expected_ordinal" "$expected_command_id" "$expected_phase" \
                    "$expected_cwd" "$expected_argv" "$expected_exit" "$expected_immediate_copy"
                """.formatted(source.substring(start, end + 2));
        ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", harness);
        processBuilder.environment().put("TFI_TEST_AUTHORITY_ROOT", authorityRoot.toString());
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(process.waitFor()).as(error).isZero();
        assertThat(output).isEqualTo(
                "CALLER-COMMAND\t33\tC-STARTER\tartifact-consumer\t<REPO_ROOT>\t"
                        + "expected-argv\t0\tevidence.xml\n");
    }

    @Test
    void artifactConsumerToolchainIsPinnedAndSeededBeforeDisposableRuns() throws Exception {
        Path root = repositoryRoot();
        String fixtureParent = Files.readString(
                root.resolve("tfi-compare/src/it/artifact-consumers/pom.xml"),
                StandardCharsets.UTF_8);
        Map<String, String> requiredPlugins = Map.of(
                "maven-clean-plugin", "3.4.1",
                "maven-resources-plugin", "3.3.1",
                "maven-compiler-plugin", "3.14.0",
                "maven-surefire-plugin", "3.5.3",
                "maven-jar-plugin", "3.4.2",
                "maven-dependency-plugin", "3.9.0");
        requiredPlugins.forEach((artifactId, version) ->
                assertPluginVersion(fixtureParent, artifactId, version));

        String rollback = Files.readString(root.resolve(
                "tfi-compare/src/it/artifact-consumers/baseline-upgrade-rollback/pom.xml"),
                StandardCharsets.UTF_8);
        assertPluginVersion(rollback, "maven-enforcer-plugin", "3.4.1");

        String installCommand = Files.readAllLines(
                        root.resolve("scripts/release-evidence/expected-commands.tsv"),
                        StandardCharsets.UTF_8).stream()
                .filter(row -> row.contains("\tB-INSTALL-CANDIDATE\t"))
                .findFirst()
                .orElseThrow();
        assertThat(installCommand).contains(
                "org.apache.maven.plugins:maven-dependency-plugin:3.9.0:help",
                "-Ddetail=false -Dgoal=tree");
    }

    @Test
    void evidencePreparerRejectsAmbiguousCliBeforeCreatingEvidence() throws Exception {
        Path script = repositoryRoot().resolve("scripts/prepare_tfi_compare_release_evidence.sh");
        Process process = new ProcessBuilder("bash", script.toString(), "--ci", "unexpected").start();
        String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).isEqualTo(64);
        assertThat(error).contains(
                "prepare_tfi_compare_release_evidence.sh --ci",
                "prepare_tfi_compare_release_evidence.sh --release <assignment.tsv>");
    }

    private static void assertPluginVersion(String pom, String artifactId, String version) {
        Pattern plugin = Pattern.compile(
                "(?s)<plugin>\\s*(?:(?!</plugin>).)*<artifactId>"
                        + Pattern.quote(artifactId)
                        + "</artifactId>(?:(?!</plugin>).)*<version>"
                        + Pattern.quote(version) + "</version>(?:(?!</plugin>).)*</plugin>");
        assertThat(plugin.matcher(pom).find()).as("%s:%s", artifactId, version).isTrue();
    }

    private static Map<String, String> headers(Path document) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(document, StandardCharsets.UTF_8)) {
            Matcher matcher = MACHINE_HEADER.matcher(line);
            if (matcher.matches()) {
                assertThat(values.putIfAbsent(matcher.group(1), matcher.group(2)))
                        .as("duplicate machine header %s in %s", matcher.group(1), document)
                        .isNull();
            }
        }
        return Map.copyOf(values);
    }

    private static Path repositoryRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
    }
}
