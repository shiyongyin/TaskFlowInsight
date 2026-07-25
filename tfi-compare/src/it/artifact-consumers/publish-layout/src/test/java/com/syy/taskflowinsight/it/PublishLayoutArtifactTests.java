package com.syy.taskflowinsight.it;

import org.junit.jupiter.api.Test;

/** publish-layout exec 验证的可保留 Surefire 合同入口。 */
class PublishLayoutArtifactTests {

    @Test
    void retainedLayoutLoadsAllSixBinaryOwners() throws Exception {
        PublishLayoutConsumer.main(new String[]{
                requiredProperty("tfi.expected.artifacts"),
                requiredProperty("tfi.expected.repository"),
                requiredProperty("tfi.expected.version")});
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name, "UNSET");
        if (value.isBlank() || "UNSET".equals(value)) {
            throw new IllegalStateException(name + " must be injected");
        }
        return value;
    }
}
