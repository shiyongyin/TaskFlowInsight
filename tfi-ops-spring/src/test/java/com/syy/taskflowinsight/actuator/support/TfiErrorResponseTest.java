package com.syy.taskflowinsight.actuator.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link TfiErrorResponse}.
 */
class TfiErrorResponseTest {

    @Test
    void notFound_createsCorrectCode() {
        TfiErrorResponse response = TfiErrorResponse.notFound("Session", "check /actuator/tfi-advanced/sessions");
        assertEquals("TFI-404", response.code());
        assertEquals("Session not found", response.message());
        assertEquals("check /actuator/tfi-advanced/sessions", response.hint());
        assertNotNull(response.timestamp());
    }

    @Test
    void unavailable_createsCorrectCode() {
        TfiErrorResponse response = TfiErrorResponse.unavailable("Metrics", "check tfi.metrics.enabled");
        assertEquals("TFI-503", response.code());
        assertEquals("Metrics unavailable", response.message());
        assertEquals("check tfi.metrics.enabled", response.hint());
        assertNotNull(response.timestamp());
    }

    @Test
    void badRequest_createsCorrectCode() {
        TfiErrorResponse response = TfiErrorResponse.badRequest("Invalid sessionId format", "use UUID format");
        assertEquals("TFI-400", response.code());
        assertEquals("Invalid sessionId format", response.message());
        assertEquals("use UUID format", response.hint());
        assertNotNull(response.timestamp());
    }

    @Test
    void timestamp_isNotNull() {
        TfiErrorResponse notFound = TfiErrorResponse.notFound("X", "hint");
        TfiErrorResponse unavailable = TfiErrorResponse.unavailable("Y", "hint");
        TfiErrorResponse badRequest = TfiErrorResponse.badRequest("Z", "hint");
        assertNotNull(notFound.timestamp());
        assertNotNull(unavailable.timestamp());
        assertNotNull(badRequest.timestamp());
    }

    @Test
    void hint_isPreservedCorrectly() {
        String hint = "custom hint for debugging";
        TfiErrorResponse response = TfiErrorResponse.notFound("Resource", hint);
        assertEquals(hint, response.hint());
    }
}
