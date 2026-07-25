package com.syy.taskflowinsight.compatibility;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 五类breaking manifest的最终覆盖门禁。
 *
 * <p>基础schema测试之外，本类要求每一类变化都有真实entry，并复用repository validator核对
 * API exclusion、owner任务和可执行消费者证据。</p>
 */
class CompareManifestCoverageTests {

    private static final Set<String> REQUIRED_KINDS = Set.of(
            "API", "RESOURCE", "CONFIG", "SCHEMA", "BEHAVIOR");

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    @Test
    void repositoryManifestCoversEveryKindWithResolvableEvidence() throws Exception {
        ObjectNode manifest = repositoryManifest();
        Path root = CompareApiInventory.repositoryRoot();

        assertEveryKindCovered(manifest);
        CompareBreakingManifest.validateRepository(manifest, root.resolve("tfi-compare/pom.xml"), root);
    }

    @Test
    void removingOneManifestKindIsRejected() throws Exception {
        ObjectNode manifest = repositoryManifest();
        ArrayNode entries = manifest.withArray("entries");
        for (int index = entries.size() - 1; index >= 0; index--) {
            if ("SCHEMA".equals(entries.get(index).path("kind").asText())) {
                entries.remove(index);
            }
        }

        assertThatThrownBy(() -> assertEveryKindCovered(manifest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("manifest kinds");
    }

    private static void assertEveryKindCovered(ObjectNode manifest) {
        Set<String> actualKinds = StreamSupport.stream(
                        manifest.withArray("entries").spliterator(), false)
                .map(entry -> entry.path("kind").asText())
                .collect(Collectors.toSet());
        if (!actualKinds.equals(REQUIRED_KINDS)) {
            throw new IllegalStateException("manifest kinds changed: " + actualKinds);
        }
    }

    private static ObjectNode repositoryManifest() throws Exception {
        try (InputStream input = CompareManifestCoverageTests.class.getResourceAsStream(
                "/compatibility/breaking-changes-v4.json")) {
            assertThat(input).isNotNull();
            JsonNode manifest = MAPPER.readTree(input);
            return (ObjectNode) manifest;
        }
    }
}
