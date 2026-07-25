package com.syy.taskflowinsight.compatibility;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compare 4.0 五类破坏清单的机器契约。
 *
 * <p>主版本升级只允许清单内的精确变化；ABI、资源、配置、schema与行为共享一个owner，
 * 但各自仍由匹配的消费者测试提供证据。
 */
class CompareBreakingChangeManifestTests {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    private static final Set<String> KINDS = Set.of(
            "API", "RESOURCE", "CONFIG", "SCHEMA", "BEHAVIOR");

    @Test
    void repositoryManifestDeclaresFiveKindClosedSchema() throws Exception {
        try (InputStream input = CompareBreakingChangeManifestTests.class.getResourceAsStream(
                "/compatibility/breaking-changes-v4.json")) {
            assertThat(input)
                    .as("4.0 breaking manifest 必须作为唯一测试资源随门禁发布")
                    .isNotNull();

            JsonNode manifest = MAPPER.readTree(input);
            assertThat(manifest.path("schemaVersion").asInt()).isEqualTo(1);
            assertThat(manifest.path("baselineVersion").asText()).isEqualTo("3.0.0");
            assertThat(manifest.path("targetVersion").asText()).isEqualTo("4.0.0");
            assertThat(manifest.path("policy").asText())
                    .isEqualTo("BREAKING_MAJOR_4_DIRECT_REMOVAL_EXACT_MANIFEST");
            assertThat(manifest.path("kinds")).extracting(JsonNode::asText)
                    .containsExactlyInAnyOrderElementsOf(KINDS);
            assertThat(manifest.path("entries").isArray()).isTrue();
            CompareBreakingManifest.validateSchema((ObjectNode) manifest);
        }
    }

    @Test
    void manifestRejectsUnknownKind() {
        ObjectNode manifest = manifestWithEntry();
        ((ObjectNode) manifest.withArray("entries").get(0)).put("kind", "PACKAGE");

        assertThatThrownBy(() -> CompareBreakingManifest.validateSchema(manifest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PACKAGE");
    }

    @Test
    void manifestRejectsDuplicateStableId() {
        ObjectNode manifest = manifestWithEntry();
        ArrayNode entries = manifest.withArray("entries");
        entries.add(entries.get(0).deepCopy());

        assertThatThrownBy(() -> CompareBreakingManifest.validateSchema(manifest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate id");
    }

    @Test
    void manifestRejectsBlankReplacement() {
        ObjectNode manifest = manifestWithEntry();
        ((ObjectNode) manifest.withArray("entries").get(0)).put("replacement", " ");

        assertThatThrownBy(() -> CompareBreakingManifest.validateSchema(manifest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("replacement");
    }

    @Test
    void compareOwnsProtectedApiCompatibilityProfile() throws Exception {
        Path pom = CompareApiInventory.repositoryRoot().resolve("tfi-compare/pom.xml");
        String xml = Files.readString(pom);

        assertThat(xml).contains("<id>api-compat</id>");
        assertThat(xml).contains("<artifactId>tfi-compare</artifactId>");
        assertThat(xml).contains("<accessModifier>protected</accessModifier>");
        assertThat(xml).contains("<breakBuildOnBinaryIncompatibleModifications>true");
        assertThat(xml).contains("<breakBuildOnSourceIncompatibleModifications>true");
    }

    @Test
    void apiCompatibilityProfileUsesRepositoryFixedBaselineJar() throws Exception {
        Path pom = CompareApiInventory.repositoryRoot().resolve("tfi-compare/pom.xml");
        String xml = Files.readString(pom);
        String oldVersion = xml.substring(
                xml.indexOf("<oldVersion>"), xml.indexOf("</oldVersion>") + "</oldVersion>".length());

        assertThat(oldVersion)
                .contains("${maven.multiModuleProjectDirectory}/.mvn/api-baseline/repository/"
                        + "com/syy/tfi-compare/3.0.0/tfi-compare-3.0.0.jar")
                .doesNotContain("<dependency>");
    }

    @Test
    void compareCiRunsChecksumContractsAndJapicmp() throws Exception {
        Path workflow = CompareApiInventory.repositoryRoot().resolve(
                ".github/workflows/tfi-compare-ci.yml");
        String yaml = Files.readString(workflow);

        assertThat(yaml).contains("shasum -a 256 -c SHA256SUMS");
        assertThat(yaml).contains(
                "CompareApiInventoryContractTests",
                "CompareBreakingChangeManifestTests",
                "CompareManifestCoverageTests",
                "CompareResourceInventoryContractTests",
                "CompareServiceLoaderContractTests",
                "CompareArchitectureContractTests",
                "CompareBuildConfigurationContractTests");
        assertThat(yaml).doesNotContain("ApiSurfaceCompatibilityTests", "continue-on-error");
        assertThat(yaml).contains("-Papi-compat verify -DskipTests");
    }

    @Test
    void repositoryManifestMatchesPomAndResolvableEvidence() throws Exception {
        ObjectNode manifest = repositoryManifest();
        Path root = CompareApiInventory.repositoryRoot();

        CompareBreakingManifest.validateRepository(manifest, root.resolve("tfi-compare/pom.xml"), root);
    }

    @Test
    void repositoryValidationRejectsOrphanPomExclusion() throws Exception {
        ObjectNode manifest = repositoryManifest();
        manifest.withArray("entries").remove(0);
        Path root = CompareApiInventory.repositoryRoot();

        assertThatThrownBy(() -> CompareBreakingManifest.validateRepository(
                manifest, root.resolve("tfi-compare/pom.xml"), root))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orphan POM exclusion");
    }

    @Test
    void repositoryValidationRejectsManifestExclusionWithoutPomOwner() throws Exception {
        ObjectNode manifest = repositoryManifest();
        ObjectNode extra = manifest.withArray("entries").get(0).deepCopy();
        extra.put("id", "CMP-BRK-API-9999");
        extra.put("japicmpExclusion", "com.syy.taskflowinsight.api.Missing#method()");
        manifest.withArray("entries").add(extra);
        Path root = CompareApiInventory.repositoryRoot();

        assertThatThrownBy(() -> CompareBreakingManifest.validateRepository(
                manifest, root.resolve("tfi-compare/pom.xml"), root))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no exact POM owner");
    }

    @Test
    void repositoryValidationRejectsExclusionWithoutRealRemoval() throws Exception {
        ObjectNode manifest = repositoryManifest();
        ObjectNode stale = manifest.withArray("entries").get(0).deepCopy();
        stale.put("id", "CMP-BRK-API-9999");
        stale.put("japicmpExclusion",
                "com.syy.taskflowinsight.config.resolver.ConfigDefaults#MAX_DEPTH");
        manifest.withArray("entries").add(stale);

        assertThatThrownBy(() -> CompareBreakingManifest.validateApiExclusionsAgainstCurrent(
                manifest, CompareApiInventory.repositoryRoot()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("real API removal");
    }

    @Test
    void repositoryValidationRejectsCompatibleApiAddition() throws Exception {
        ObjectNode manifest = repositoryManifest();
        ObjectNode compatibleAddition = manifest.withArray("entries").get(0).deepCopy();
        compatibleAddition.put("id", "CMP-BRK-API-9999");
        compatibleAddition.put("japicmpExclusion",
                "com.syy.taskflowinsight.tracking.TrackingExecutor#withTracked("
                        + "java.lang.String,java.lang.Object,java.lang.Runnable,"
                        + "com.syy.taskflowinsight.tracking.compare.CompareOptions)");
        manifest.withArray("entries").add(compatibleAddition);

        assertThatThrownBy(() -> CompareBreakingManifest.validateApiExclusionsAgainstCurrent(
                manifest, CompareApiInventory.repositoryRoot()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("real API removal");
    }

    @Test
    void repositoryValidationRejectsMissingOwnerTask() throws Exception {
        ObjectNode manifest = repositoryManifest();
        ((ObjectNode) manifest.withArray("entries").get(0)).put("ownerTask", "CMP-GRD-99");
        Path root = CompareApiInventory.repositoryRoot();

        assertThatThrownBy(() -> CompareBreakingManifest.validateRepository(
                manifest, root.resolve("tfi-compare/pom.xml"), root))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner task");
    }

    @Test
    void repositoryValidationRejectsMissingConsumerTest() throws Exception {
        ObjectNode manifest = repositoryManifest();
        ((ObjectNode) manifest.withArray("entries").get(0)).put(
                "consumerTest", "com.syy.missing.NoTest#missingMethod");
        Path root = CompareApiInventory.repositoryRoot();

        assertThatThrownBy(() -> CompareBreakingManifest.validateRepository(
                manifest, root.resolve("tfi-compare/pom.xml"), root))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("consumer test");
    }

    @Test
    void repositoryValidationRejectsNonTestConsumerMethod() throws Exception {
        ObjectNode manifest = repositoryManifest();
        ((ObjectNode) manifest.withArray("entries").get(0)).put(
                "consumerTest", getClassName() + "#ordinaryVoidHelper");
        Path root = CompareApiInventory.repositoryRoot();

        assertThatThrownBy(() -> CompareBreakingManifest.validateRepository(
                manifest, root.resolve("tfi-compare/pom.xml"), root))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JUnit test");
    }

    private static ObjectNode repositoryManifest() throws Exception {
        try (InputStream input = CompareBreakingChangeManifestTests.class.getResourceAsStream(
                "/compatibility/breaking-changes-v4.json")) {
            return (ObjectNode) MAPPER.readTree(input);
        }
    }

    private static ObjectNode manifestWithEntry() {
        ObjectNode manifest = MAPPER.createObjectNode();
        manifest.put("schemaVersion", 1);
        manifest.put("baselineVersion", "3.0.0");
        manifest.put("targetVersion", "4.0.0");
        manifest.put("policy", "BREAKING_MAJOR_4_DIRECT_REMOVAL_EXACT_MANIFEST");
        ArrayNode kinds = manifest.putArray("kinds");
        KINDS.stream().sorted().forEach(kinds::add);
        ObjectNode entry = manifest.putArray("entries").addObject();
        entry.put("id", "CMP-BRK-BEHAVIOR-0001");
        entry.put("kind", "BEHAVIOR");
        entry.put("before", "legacy behavior");
        entry.put("after", "target behavior");
        entry.put("replacement", "Use the target behavior contract");
        entry.put("reason", "The legacy behavior violates the accepted single-owner contract.");
        entry.put("ownerTask", "CMP-GRD-01");
        entry.put("consumerTest", getClassName() + "#repositoryManifestDeclaresFiveKindClosedSchema");
        entry.putNull("japicmpExclusion");
        return manifest;
    }

    private static String getClassName() {
        return CompareBreakingChangeManifestTests.class.getName();
    }

    private static void ordinaryVoidHelper() {
    }
}
