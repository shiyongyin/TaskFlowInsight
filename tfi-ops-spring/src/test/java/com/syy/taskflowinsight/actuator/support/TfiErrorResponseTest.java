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
        TfiErrorResponse response = TfiErrorResponse.notFound("Metric", "check /actuator/tfi-metrics");
        assertEquals("TFI-404", response.code());
        assertEquals("Metric not found", response.message());
        assertEquals("check /actuator/tfi-metrics", response.hint());
        assertNotNull(response.timestamp());
    }

    @Test
    void unavailable_createsCorrectCode() {
        TfiErrorResponse response = TfiErrorResponse.unavailable("Metrics", "configure host MeterRegistry");
        assertEquals("TFI-503", response.code());
        assertEquals("Metrics unavailable", response.message());
        assertEquals("configure host MeterRegistry", response.hint());
        assertNotNull(response.timestamp());
    }

    @Test
    void badRequest_createsCorrectCode() {
        TfiErrorResponse response = TfiErrorResponse.badRequest("Invalid metric name", "use a published metric name");
        assertEquals("TFI-400", response.code());
        assertEquals("Invalid metric name", response.message());
        assertEquals("use a published metric name", response.hint());
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
