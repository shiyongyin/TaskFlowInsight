package com.syy.taskflowinsight.actuator.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link SessionIdMasker}.
 */
class SessionIdMaskerTest {

    @Test
    void mask_nullInput_returnsMask() {
        assertEquals("***", SessionIdMasker.mask(null));
    }

    @Test
    void mask_emptyString_returnsMask() {
        assertEquals("***", SessionIdMasker.mask(""));
    }

    @Test
    void mask_shortString_returnsMask() {
        assertEquals("***", SessionIdMasker.mask("short"));
        assertEquals("***", SessionIdMasker.mask("1234567"));
    }

    @Test
    void mask_exactly8Chars_keepsFirst4AndLast4() {
        assertEquals("1234***5678", SessionIdMasker.mask("12345678"));
    }

    @Test
    void mask_longerString_masksMiddle() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        assertEquals("a1b2***7890", SessionIdMasker.mask(uuid));
    }
}
