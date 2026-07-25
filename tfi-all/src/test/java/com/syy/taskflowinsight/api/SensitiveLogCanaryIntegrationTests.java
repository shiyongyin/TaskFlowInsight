package com.syy.taskflowinsight.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Policy coverage 到真实 pre-redaction source 和 retained sink 的 7 x 11 canary 闭集。 */
class SensitiveLogCanaryIntegrationTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void policyCoverageInvokesEveryTrustedPreRedactionDriver() throws Exception {
        Path coverage = repositoryRoot().resolve(
                "scripts/release-evidence/fixtures/production-policy/config/canary-coverage.tsv");

        SensitiveLogCanaryHarness.Result result =
                new SensitiveLogCanaryHarness(temporaryDirectory).execute(coverage);

        assertThat(result.receipts()).hasSize(77);
        assertThat(result.preRedactionObservations()).hasSize(77);
        assertThat(result.coveredCombinations()).hasSize(77);
        assertThat(result.allRetainedBytesExcludeCanaries()).isTrue();
    }

    @Test
    void duplicateCanaryIdCannotProduceTrustedReceipt() throws Exception {
        Path original = repositoryRoot().resolve(
                "scripts/release-evidence/fixtures/production-policy/config/canary-coverage.tsv");
        List<String> rows = new ArrayList<>(Files.readAllLines(original, StandardCharsets.UTF_8));
        String[] duplicate = rows.get(2).split("\t", -1);
        duplicate[0] = rows.get(1).split("\t", -1)[0];
        rows.set(2, String.join("\t", duplicate));
        Path forged = temporaryDirectory.resolve("forged-coverage.tsv");
        Files.write(forged, rows, StandardCharsets.UTF_8);
        Path evidence = Files.createDirectory(temporaryDirectory.resolve("evidence"));

        assertThatThrownBy(() -> new SensitiveLogCanaryHarness(evidence).execute(forged))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sensitive-log coverage row has no trusted driver");
    }

    private static Path repositoryRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
    }
}
