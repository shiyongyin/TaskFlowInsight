package com.syy.taskflowinsight.tracking.path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 锁定 legacy display path 的兼容格式，同时避免其被误当作 canonical identity。 */
class PathBuilderContractTests {

    @Test
    void shouldBuildStandardAndLegacyMapKeys() {
        assertThat(PathBuilder.mapKey("root", null)).isEqualTo("root[null]");
        assertThat(PathBuilder.mapKey("root", "plain")).isEqualTo("root[\"plain\"]");
        assertThat(PathBuilder.mapKey("root", "a\"\\\n\t\r"))
                .isEqualTo("root[\"a\\\"\\\\\\n\\t\\r\"]");
        assertThat(PathBuilder.mapKey("root", "a'b", false)).isEqualTo("root['a\\'b']");
    }

    @Test
    void shouldBuildFieldArrayAndWrapperPaths() {
        assertThat(PathBuilder.fieldPath(null, "name")).isEqualTo("name");
        assertThat(PathBuilder.fieldPath("", "name")).isEqualTo("name");
        assertThat(PathBuilder.fieldPath("root", "name")).isEqualTo("root.name");
        assertThat(PathBuilder.arrayIndex("items", 2)).isEqualTo("items[2]");
        assertThat(PathBuilder.buildFieldPath("root", "name")).isEqualTo("root.name");
        assertThat(PathBuilder.buildMapKeyPath("root", "key")).isEqualTo("root[\"key\"]");
        assertThat(PathBuilder.buildArrayIndexPath("items", 1)).isEqualTo("items[1]");
    }

    @Test
    void shouldBuildStableSetElementPathsForNullAndNegativeHashes() {
        assertThat(PathBuilder.setElement("items", null)).isEqualTo("items[id=null]");
        String candidate = stringWithNegativeCombinedHash();
        String path = PathBuilder.setElement("items", candidate);

        assertThat(("String:" + candidate).hashCode()).isNegative();
        assertThat(path).matches("items\\[id=String[0-9A-F]{8}]");
        assertThat(PathBuilder.buildSetElementPath("items", candidate)).isEqualTo(path);
    }

    @Test
    void shouldBuildInvocationLocalChains() {
        PathBuilder.PathBuilderChain chain = PathBuilder.start(null)
                .field("order")
                .mapKey("line")
                .arrayIndex(3);

        assertThat(chain.build()).isEqualTo("order[\"line\"][3]");
        assertThat(chain).hasToString(chain.build());
        assertThat(PathBuilder.start("root").field("name").build()).isEqualTo("root.name");
    }

    private static String stringWithNegativeCombinedHash() {
        for (int index = 0; index < 100_000; index++) {
            String candidate = "value-" + index;
            if (("String:" + candidate).hashCode() < 0) {
                return candidate;
            }
        }
        throw new AssertionError("failed to create negative deterministic hash fixture");
    }
}
