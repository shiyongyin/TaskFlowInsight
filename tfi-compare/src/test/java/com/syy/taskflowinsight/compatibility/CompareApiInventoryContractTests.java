package com.syy.taskflowinsight.compatibility;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compare 3.0 公共表面的逐成员兼容契约。
 *
 * <p>该门禁绑定仓库内 checksum 固定的基线，而不是开发者本地 Maven 缓存，避免同一 GAV
 * 在不同机器上解析成不同 API 事实。
 */
class CompareApiInventoryContractTests {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    @Test
    void repositoryInventoryIsBoundToFixedBaseline() throws Exception {
        try (InputStream input = CompareApiInventoryContractTests.class.getResourceAsStream(
                "/compatibility/current-api-inventory-v3.json")) {
            assertThat(input)
                    .as("3.0 API inventory 必须作为测试资源随兼容门禁发布")
                    .isNotNull();

            JsonNode inventory = MAPPER.readTree(input);
            assertThat(inventory.path("baselineVersion").asText()).isEqualTo("3.0.0");
            assertThat(inventory.path("baselineSha256").asText())
                    .isEqualTo("f73ae87e7b141dc6ec290b89687ba5eccceebdc0e75135466c1256a378aa3423");
        }
    }

    @Test
    void repositoryInventoryClassifiesEveryPublicTopLevelType() throws Exception {
        try (InputStream input = CompareApiInventoryContractTests.class.getResourceAsStream(
                "/compatibility/current-api-inventory-v3.json")) {
            JsonNode types = MAPPER.readTree(input).path("types");

            assertThat(types).hasSize(175);
            assertThat(types).allSatisfy(type -> {
                assertThat(type.path("name").asText()).startsWith("com.syy.taskflowinsight.");
                assertThat(type.path("classification").asText()).isNotBlank();
                assertThat(type.path("ownerTask").asText()).startsWith("CMP-");
                assertThat(type.path("hierarchy").isArray()).isTrue();
                assertThat(type.path("members").isArray()).isTrue();
            });
        }
    }

    @Test
    void repositoryInventoryExactlyMatchesFixedBaselineJar() throws Exception {
        ObjectNode generated = CompareApiInventory.baseline(
                CompareApiInventory.repositoryRoot(), MAPPER);
        try (InputStream input = CompareApiInventoryContractTests.class.getResourceAsStream(
                "/compatibility/current-api-inventory-v3.json")) {
            ObjectNode repository = (ObjectNode) MAPPER.readTree(input);
            CompareApiInventory.validateExact(generated, repository);
        }
    }

    @Test
    void currentInventoryIsGeneratedFromCompiledArtifact() throws Exception {
        ObjectNode current = CompareApiInventory.current(
                CompareApiInventory.repositoryRoot(), MAPPER);
        JsonNode configDefaults = findType(
                current, "com.syy.taskflowinsight.config.resolver.ConfigDefaults");

        assertThat(configDefaults).isNotNull();
        assertThat(configDefaults.path("members"))
                .noneSatisfy(member -> assertThat(member.path("signature").asText())
                        .contains("#NESTED_STAGE_MAX_DEPTH:"));
    }

    @Test
    void inventoryValidationRejectsUnexpectedDeclaration() throws Exception {
        ObjectNode generated = CompareApiInventory.baseline(
                CompareApiInventory.repositoryRoot(), MAPPER);
        ObjectNode withUnexpectedType = generated.deepCopy();
        ObjectNode unexpected = withUnexpectedType.withArray("types").get(0).deepCopy();
        unexpected.put("name", "com.syy.taskflowinsight.unexpected.ExtraType");
        withUnexpectedType.withArray("types").add(unexpected);

        assertThatThrownBy(() -> CompareApiInventory.validateExact(generated, withUnexpectedType))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unexpected");
    }

    @Test
    void inventoryValidationRejectsMissingDeclaration() throws Exception {
        ObjectNode generated = CompareApiInventory.baseline(
                CompareApiInventory.repositoryRoot(), MAPPER);
        ObjectNode withMissingType = generated.deepCopy();
        withMissingType.withArray("types").remove(0);

        assertThatThrownBy(() -> CompareApiInventory.validateExact(generated, withMissingType))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void legacyApiExistenceGateIsRemovedAfterMachineContractsTakeOwnership() {
        Path source = CompareApiInventory.repositoryRoot().resolve(
                "tfi-compare/src/test/java/com/syy/taskflowinsight/api/ApiSurfaceCompatibilityTests.java");

        assertThat(source).doesNotExist();
    }

    @Test
    void repositoryInventoryUsesClosedMemberKindsAndResolvableOwners() throws Exception {
        ObjectNode inventory = repositoryInventory();
        CompareApiInventory.validateSchema(inventory, CompareApiInventory.repositoryRoot());

        Set<String> memberKinds = new HashSet<>();
        inventory.withArray("types").forEach(type -> type.path("members")
                .forEach(member -> memberKinds.add(member.path("kind").asText())));
        assertThat(memberKinds).contains("FIELD", "CONSTRUCTOR", "METHOD", "NESTED_TYPE");
    }

    @Test
    void inventorySchemaRejectsUnknownClassification() throws Exception {
        ObjectNode inventory = repositoryInventory();
        ((ObjectNode) inventory.withArray("types").get(0)).put("classification", "UNKNOWN");

        assertThatThrownBy(() -> CompareApiInventory.validateSchema(
                inventory, CompareApiInventory.repositoryRoot()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("classification");
    }

    @Test
    void inventorySchemaRejectsMissingOwnerTask() throws Exception {
        ObjectNode inventory = repositoryInventory();
        ((ObjectNode) inventory.withArray("types").get(0)).put("ownerTask", "CMP-GRD-99");

        assertThatThrownBy(() -> CompareApiInventory.validateSchema(
                inventory, CompareApiInventory.repositoryRoot()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner task");
    }

    private static ObjectNode repositoryInventory() throws Exception {
        try (InputStream input = CompareApiInventoryContractTests.class.getResourceAsStream(
                "/compatibility/current-api-inventory-v3.json")) {
            return (ObjectNode) MAPPER.readTree(input);
        }
    }

    private static JsonNode findType(ObjectNode inventory, String name) {
        for (JsonNode type : inventory.withArray("types")) {
            if (name.equals(type.path("name").asText())) {
                return type;
            }
        }
        return null;
    }
}
