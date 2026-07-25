package com.syy.taskflowinsight.compatibility;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compare 3.0 runtime资源、配置读取与CI资产的双向兼容契约。
 *
 * <p>该合同只冻结W0当前事实及后继owner，不把旧资源和配置提升为4.0目标；
 * 后续任务必须通过唯一
 * breaking manifest翻转对应条目，不能靠遗漏扫描范围静默删除。</p>
 */
class CompareResourceInventoryContractTests {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    @TempDir
    Path temporaryDirectory;

    @Test
    void repositoryResourceInventoryDeclaresFixedBaselinePolicy() throws Exception {
        try (InputStream input = CompareResourceInventoryContractTests.class.getResourceAsStream(
                "/compatibility/current-resource-inventory-v3.json")) {
            assertThat(input)
                    .as("W0 resource inventory必须作为唯一测试资源进入兼容门禁")
                    .isNotNull();

            JsonNode inventory = MAPPER.readTree(input);
            assertThat(inventory.path("schemaVersion").asInt()).isEqualTo(1);
            assertThat(inventory.path("baselineVersion").asText()).isEqualTo("3.0.0");
            assertThat(inventory.path("policy").asText())
                    .isEqualTo("CURRENT_FACT_WITH_EXPLICIT_TARGET_TASK");
        }
    }

    @Test
    void repositoryRuntimeResourcesExactlyMatchWorkspace() throws Exception {
        CompareResourceInventory.validateRuntimeAssets(
                repositoryInventory(), CompareApiInventory.repositoryRoot());
    }

    @Test
    void repositoryConfigurationOwnersExactlyMatchWorkspace() throws Exception {
        CompareResourceInventory.validateConfigurationAssets(
                repositoryInventory(), CompareApiInventory.repositoryRoot());
    }

    @Test
    void repositoryMetadataAndWorkflowsExactlyMatchWorkspace() throws Exception {
        CompareResourceInventory.validateMetadataAndWorkflows(
                repositoryInventory(), CompareApiInventory.repositoryRoot());
    }

    @Test
    void missingReleaseEvidenceAssetIsRejected() throws Exception {
        ObjectNode inventory = repositoryInventory();
        ((ArrayNode) inventory.path("releaseEvidenceAssets")).remove(0);

        assertThatThrownBy(() -> CompareResourceInventory.validateMetadataAndWorkflows(
                inventory, CompareApiInventory.repositoryRoot()))
                .hasMessageContaining("release evidence asset closure changed");
    }

    @Test
    void repositoryStaticAnalysisEvidenceIsCompareScoped() throws Exception {
        CompareResourceInventory.validateStaticAnalysisEvidence(
                repositoryInventory(), CompareApiInventory.repositoryRoot());
    }

    @Test
    void missingRuntimeResourceInventoryEntryIsRejected() throws Exception {
        ObjectNode inventory = repositoryInventory();
        ((ArrayNode) inventory.path("runtimeResources")).remove(0);

        assertThatThrownBy(() -> CompareResourceInventory.validateRuntimeAssets(
                inventory, CompareApiInventory.repositoryRoot()))
                .hasMessageContaining("runtime resource paths mismatch");
    }

    @Test
    void extraRuntimeResourceInventoryEntryIsRejected() throws Exception {
        ObjectNode inventory = repositoryInventory();
        ArrayNode resources = (ArrayNode) inventory.path("runtimeResources");
        ObjectNode extra = resources.get(0).deepCopy();
        extra.put("path", "META-INF/unowned-extra-resource");
        resources.add(extra);

        assertThatThrownBy(() -> CompareResourceInventory.validateRuntimeAssets(
                inventory, CompareApiInventory.repositoryRoot()))
                .hasMessageContaining("runtime resource paths mismatch");
    }

    @Test
    void unknownRuntimeResourceKindIsRejected() throws Exception {
        ObjectNode inventory = repositoryInventory();
        ((ObjectNode) inventory.path("runtimeResources").get(0)).put("kind", "UNKNOWN_KIND");

        assertThatThrownBy(() -> CompareResourceInventory.validateRuntimeAssets(
                inventory, CompareApiInventory.repositoryRoot()))
                .hasMessageContaining("runtime resource kind changed");
    }

    @Test
    void duplicateStaticAnalysisConfigPathIsRejected() throws Exception {
        ObjectNode baseline = (ObjectNode) MAPPER.readTree(
                CompareApiInventory.repositoryRoot().resolve(".mvn/static-analysis-baseline.json").toFile());
        ArrayNode configs = (ArrayNode) baseline.at("/moduleEvidence/tfi-compare/configFiles");
        while (configs.size() < 4) {
            configs.add(configs.get(0).deepCopy());
        }
        configs.set(configs.size() - 1, configs.get(0).deepCopy());

        assertThatThrownBy(() -> CompareResourceInventory.validateStaticAnalysisConfigFiles(
                configs, CompareApiInventory.repositoryRoot()))
                .hasMessageContaining("static analysis config paths changed");
    }

    @Test
    void weakenedRootStaticAnalysisPluginConfigurationIsRejected() throws Exception {
        String rootPom = Files.readString(CompareApiInventory.repositoryRoot().resolve("pom.xml"));
        Path weakened = temporaryDirectory.resolve("pom.xml");
        Files.writeString(weakened, rootPom.replace(
                "<ruleset>${maven.multiModuleProjectDirectory}/config/pmd/ruleset.xml</ruleset>",
                "<ruleset>unowned-ruleset.xml</ruleset>"));

        assertThatThrownBy(() -> CompareResourceInventory.validateRootPomOwnsStaticAnalysisDefaults(weakened))
                .hasMessageContaining("root POM static analysis gate missing: maven-pmd-plugin");
    }

    @Test
    void missingStarterBootstrapIsRejected() throws Exception {
        ObjectNode baseline = (ObjectNode) MAPPER.readTree(
                CompareApiInventory.repositoryRoot().resolve(".mvn/static-analysis-baseline.json").toFile());
        ((ArrayNode) baseline.path("moduleBootstraps")).removeAll();

        assertThatThrownBy(() -> CompareResourceInventory.validateStarterBootstrap(
                baseline, CompareApiInventory.repositoryRoot()))
                .hasMessageContaining("exactly one starter bootstrap");
    }

    @Test
    void duplicateStarterBootstrapIsRejected() throws Exception {
        ObjectNode baseline = (ObjectNode) MAPPER.readTree(
                CompareApiInventory.repositoryRoot().resolve(".mvn/static-analysis-baseline.json").toFile());
        ArrayNode bootstraps = (ArrayNode) baseline.path("moduleBootstraps");
        bootstraps.add(bootstraps.get(0).deepCopy());

        assertThatThrownBy(() -> CompareResourceInventory.validateStarterBootstrap(
                baseline, CompareApiInventory.repositoryRoot()))
                .hasMessageContaining("exactly one starter bootstrap");
    }

    @Test
    void detachedStarterBootstrapToolEvidenceIsRejected() throws Exception {
        ObjectNode baseline = (ObjectNode) MAPPER.readTree(
                CompareApiInventory.repositoryRoot().resolve(".mvn/static-analysis-baseline.json").toFile());
        ObjectNode pmd = (ObjectNode) baseline.at("/moduleBootstraps/0/tools/pmd");
        pmd.put("findingCount", pmd.path("findingCount").asInt() + 1);

        assertThatThrownBy(() -> CompareResourceInventory.validateStarterBootstrap(
                baseline, CompareApiInventory.repositoryRoot()))
                .hasMessageContaining("evidence does not match baseline entries");
    }

    private static ObjectNode repositoryInventory() throws Exception {
        try (InputStream input = CompareResourceInventoryContractTests.class.getResourceAsStream(
                "/compatibility/current-resource-inventory-v3.json")) {
            return (ObjectNode) MAPPER.readTree(input);
        }
    }
}
