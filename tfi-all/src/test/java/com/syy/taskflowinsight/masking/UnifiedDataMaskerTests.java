package com.syy.taskflowinsight.masking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedDataMaskerTests {

    private final UnifiedDataMasker masker = new UnifiedDataMasker();

    @Test
    void masksSensitiveFieldByName() {
        String masked = masker.maskValue("password", "SecretValue");
        assertThat(masked).isNotEqualTo("SecretValue");
        assertThat(masked).contains("***");
    }

    @Test
    void masksEmailPhoneAndCreditCardInContent() {
        String input = "email=john.doe@example.com, phone=123-456-7890, card=4111 1111 1111 1111";
        String masked = masker.maskValue("note", input);

        assertThat(masked).doesNotContain("john.doe@example.com");
        assertThat(masked).doesNotContain("123-456-7890");
        assertThat(masked).doesNotContain("4111 1111 1111 1111");
    }

    @Test
    void forceMaskOverrides() {
        String masked = masker.maskValue("any", "ValueToMask", true);
        assertThat(masked).contains("***");
    }

    @Test
    void nonSensitiveValuesPassThrough() {
        String input = "regular text with nothing sensitive";
        String masked = masker.maskValue("description", input);
        assertThat(masked).isEqualTo(input);
    }
}

