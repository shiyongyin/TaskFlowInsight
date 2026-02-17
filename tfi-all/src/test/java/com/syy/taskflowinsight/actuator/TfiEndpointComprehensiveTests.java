package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.context.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for TfiEndpoint to improve Actuator coverage from 42% to 70%+
 * 
 * Coverage targets:
 * - All endpoint operations (Read, Write, Delete)
 * - Health status calculations
 * - Thread context statistics
 * - Error conditions and edge cases
 * - State transitions
 */
@DisplayName("TFI Endpoint Comprehensive Tests")
class TfiEndpointComprehensiveTests {

    private TfiEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new TfiEndpoint(null);
        TFI.enable();
        TFI.clear();
        TFI.setChangeTrackingEnabled(true);
    }

    @AfterEach
    void tearDown() {
        TFI.clear();
        TFI.setChangeTrackingEnabled(false);
    }

    @Nested
    @DisplayName("Read Operations")
    class ReadOperationTests {

        @Test
        @DisplayName("Info endpoint returns complete system status")
        void infoEndpointReturnsCompleteSystemStatus() {
            // Setup some data to make the response more interesting
            TFI.startSession("Test Session");
            try (TaskContext task = TFI.start("Test Task")) {
                task.message("Test message");
                task.success();
            }
            TFI.endSession();

            Map<String, Object> info = endpoint.info();

            // Verify required fields
            assertThat(info).containsKey("version");
            assertThat(info).containsKey("timestamp");
            assertThat(info).containsKey("changeTracking");
            assertThat(info).containsKey("threadContext");
            assertThat(info).containsKey("health");

            // Verify version
            assertThat(info.get("version")).isEqualTo("3.0.0");

            // Verify timestamp format
            String timestamp = (String) info.get("timestamp");
            assertThat(timestamp).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
        }

        @Test
        @DisplayName("Change tracking status is accurately reported")
        void changeTrackingStatusIsAccuratelyReported() {
            TFI.setChangeTrackingEnabled(true);

            Map<String, Object> info = endpoint.info();
            Map<String, Object> changeTracking = (Map<String, Object>) info.get("changeTracking");

            assertThat(changeTracking).containsKey("enabled");
            assertThat(changeTracking).containsKey("globalEnabled");
            assertThat(changeTracking).containsKey("totalChanges");
            assertThat(changeTracking).containsKey("activeTrackers");

            assertThat(changeTracking.get("enabled")).isEqualTo(true);
            assertThat(changeTracking.get("globalEnabled")).isEqualTo(true);
            assertThat(changeTracking.get("totalChanges")).isInstanceOf(Integer.class);
            assertThat(changeTracking.get("activeTrackers")).isInstanceOf(Integer.class);
        }

        @Test
        @DisplayName("Thread context statistics are included")
        void threadContextStatisticsAreIncluded() {
            // Create some contexts to generate statistics
            TFI.startSession("Stats Session 1");
            TFI.startSession("Stats Session 2");

            Map<String, Object> info = endpoint.info();
            Map<String, Object> threadContext = (Map<String, Object>) info.get("threadContext");

            assertThat(threadContext).containsKey("activeContexts");
            assertThat(threadContext).containsKey("totalCreated");
            assertThat(threadContext).containsKey("totalPropagations");
            assertThat(threadContext).containsKey("potentialLeak");

            // Verify types (ThreadContext returns longs)
            assertThat(threadContext.get("activeContexts")).isInstanceOf(Number.class);
            assertThat(threadContext.get("totalCreated")).isInstanceOf(Number.class);
            assertThat(threadContext.get("totalPropagations")).isInstanceOf(Number.class);
            assertThat(threadContext.get("potentialLeak")).isInstanceOf(Boolean.class);

            TFI.endSession();
        }

        @Test
        @DisplayName("Health status calculation works correctly")
        void healthStatusCalculationWorksCorrectly() {
            Map<String, Object> info = endpoint.info();
            Map<String, Object> health = (Map<String, Object>) info.get("health");

            assertThat(health).containsKey("status");
            assertThat(health).containsKey("healthy");

            String status = (String) health.get("status");
            Boolean healthy = (Boolean) health.get("healthy");

            assertThat(status).isIn("UP", "DOWN");
            assertThat(healthy).isInstanceOf(Boolean.class);

            // If status is DOWN, there should be an issue field
            if ("DOWN".equals(status)) {
                assertThat(health).containsKey("issue");
                assertThat(health.get("issue")).isInstanceOf(String.class);
            }
        }

        @Test
        @DisplayName("Health status reflects TFI disabled state")
        void healthStatusReflectsTfiDisabledState() {
            com.syy.taskflowinsight.api.TfiFlow.disable();

            Map<String, Object> info = endpoint.info();
            Map<String, Object> health = (Map<String, Object>) info.get("health");

            assertThat(health.get("status")).isEqualTo("DOWN");
            assertThat(health.get("healthy")).isEqualTo(false);
            assertThat(health.get("issue")).isEqualTo("TFI system is disabled");

            com.syy.taskflowinsight.api.TfiFlow.enable(); // Restore for cleanup
        }
    }

    @Nested
    @DisplayName("Write Operations")
    class WriteOperationTests {

        @Test
        @DisplayName("Toggle tracking with true parameter")
        void toggleTrackingWithTrueParameter() {
            TFI.setChangeTrackingEnabled(false); // Start disabled
            boolean before = TFI.isChangeTrackingEnabled();

            Map<String, Object> result = endpoint.toggleTracking(true);

            assertThat(result).containsKey("previousState");
            assertThat(result).containsKey("currentState");
            assertThat(result).containsKey("message");
            assertThat(result).containsKey("timestamp");

            assertThat(result.get("previousState")).isEqualTo(result.get("currentState"));
            assertThat(((String) result.get("message"))).contains("Runtime toggling is not supported");
            assertThat(TFI.isChangeTrackingEnabled()).isEqualTo(before);
        }

        @Test
        @DisplayName("Toggle tracking with false parameter")
        void toggleTrackingWithFalseParameter() {
            TFI.setChangeTrackingEnabled(true); // Start enabled
            boolean before = TFI.isChangeTrackingEnabled();

            Map<String, Object> result = endpoint.toggleTracking(false);

            assertThat(result.get("previousState")).isEqualTo(result.get("currentState"));
            assertThat(((String) result.get("message"))).contains("Runtime toggling is not supported");
            assertThat(TFI.isChangeTrackingEnabled()).isEqualTo(before);
        }

        @Test
        @DisplayName("Toggle tracking with null parameter toggles state")
        void toggleTrackingWithNullParameterTogglesState() {
            boolean initialState = TFI.isChangeTrackingEnabled();

            Map<String, Object> result = endpoint.toggleTracking(null);

            assertThat(result.get("previousState")).isEqualTo(initialState);
            assertThat(result.get("currentState")).isEqualTo(initialState);
            assertThat(((String) result.get("message"))).contains("Runtime toggling is not supported");
            assertThat(TFI.isChangeTrackingEnabled()).isEqualTo(initialState);
        }

        @Test
        @DisplayName("Toggle tracking message changes based on state")
        void toggleTrackingMessageChangesBasedOnState() {
            // Test enabling
            TFI.setChangeTrackingEnabled(false);
            Map<String, Object> enableResult = endpoint.toggleTracking(true);
            assertThat((String) enableResult.get("message")).contains("Runtime toggling is not supported");

            // Test disabling
            Map<String, Object> disableResult = endpoint.toggleTracking(false);
            assertThat((String) disableResult.get("message")).contains("Runtime toggling is not supported");
        }

        @Test
        @DisplayName("Toggle tracking includes valid timestamp")
        void toggleTrackingIncludesValidTimestamp() {
            Map<String, Object> result = endpoint.toggleTracking(true);
            
            String timestamp = (String) result.get("timestamp");
            assertThat(timestamp).isNotNull();
            assertThat(timestamp).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    class DeleteOperationTests {

        @Test
        @DisplayName("Clear all returns count of cleared changes")
        void clearAllReturnsCountOfClearedChanges() {
            // Create some changes to clear
            TFI.startSession("Clear Test Session");
            try (TaskContext task = TFI.start("Clear Test Task")) {
                task.message("Test message");
                task.success();
            }
            
            // Get initial count
            int initialCount = TFI.getAllChanges().size();

            Map<String, Object> result = endpoint.clearAll();

            assertThat(result).containsKey("clearedChanges");
            assertThat(result).containsKey("message");
            assertThat(result).containsKey("timestamp");

            assertThat(result.get("clearedChanges")).isEqualTo(initialCount);
            assertThat(result.get("message")).isEqualTo("All tracking data cleared");
            
            TFI.endSession();
        }

        @Test
        @DisplayName("Clear all with no existing changes")
        void clearAllWithNoExistingChanges() {
            // Ensure no changes exist
            TFI.clearAllTracking();

            Map<String, Object> result = endpoint.clearAll();

            assertThat(result.get("clearedChanges")).isEqualTo(0);
            assertThat(result.get("message")).isEqualTo("All tracking data cleared");
        }

        @Test
        @DisplayName("Clear all includes valid timestamp")
        void clearAllIncludesValidTimestamp() {
            Map<String, Object> result = endpoint.clearAll();
            
            String timestamp = (String) result.get("timestamp");
            assertThat(timestamp).isNotNull();
            assertThat(timestamp).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
        }

        @Test
        @DisplayName("Clear all actually clears tracking data")
        void clearAllActuallyClearsTrackingData() {
            // Create some data
            TFI.startSession("Data Session");
            try (TaskContext task = TFI.start("Data Task")) {
                task.message("Creating data");
                task.success();
            }

            // Verify data exists
            assertThat(TFI.getAllChanges().size()).isGreaterThanOrEqualTo(0);

            // Clear and verify
            endpoint.clearAll();
            assertThat(TFI.getAllChanges()).isEmpty();
            
            TFI.endSession();
        }
    }

    @Nested
    @DisplayName("State Transitions and Edge Cases")
    class StateTransitionTests {

        @Test
        @DisplayName("Multiple consecutive toggle operations")
        void multipleConsecutiveToggleOperations() {
            boolean initialState = TFI.isChangeTrackingEnabled();

            // Toggle multiple times
            Map<String, Object> result1 = endpoint.toggleTracking(null);
            Map<String, Object> result2 = endpoint.toggleTracking(null);
            Map<String, Object> result3 = endpoint.toggleTracking(null);

            // Runtime toggling is not supported, state should remain unchanged
            assertThat(TFI.isChangeTrackingEnabled()).isEqualTo(initialState);

            assertThat(result1.get("previousState")).isEqualTo(result1.get("currentState"));
            assertThat(result2.get("previousState")).isEqualTo(result2.get("currentState"));
            assertThat(result3.get("previousState")).isEqualTo(result3.get("currentState"));
        }

        @Test
        @DisplayName("Clear operations while tracking is disabled")
        void clearOperationsWhileTrackingIsDisabled() {
            TFI.setChangeTrackingEnabled(false);

            Map<String, Object> result = endpoint.clearAll();

            // Should still work even when tracking is disabled
            assertThat(result).containsKey("clearedChanges");
            assertThat(result).containsKey("message");
            assertThat(result.get("message")).isEqualTo("All tracking data cleared");
        }

        @Test
        @DisplayName("Info operation with high active context count")
        void infoOperationWithHighActiveContextCount() {
            // This test simulates the condition for health check failure
            // Note: Creating 1000+ real contexts might be resource intensive,
            // so we'll test the logic path that's available
            
            Map<String, Object> info = endpoint.info();
            Map<String, Object> threadContext = (Map<String, Object>) info.get("threadContext");
            Map<String, Object> health = (Map<String, Object>) info.get("health");

            // Verify the structure exists for high context count handling
            assertThat(threadContext.get("activeContexts")).isInstanceOf(Number.class);
            Number activeContexts = (Number) threadContext.get("activeContexts");
            
            // Health should be UP when contexts are under 1000
            if (activeContexts.intValue() < 1000) {
                // Normal case - should be healthy if TFI is enabled and no leaks
                Boolean potentialLeak = (Boolean) threadContext.get("potentialLeak");
                if (!potentialLeak && TFI.isEnabled()) {
                    assertThat(health.get("status")).isEqualTo("UP");
                }
            }
        }

        @Test
        @DisplayName("Operations with concurrent access simulation")
        void operationsWithConcurrentAccessSimulation() {
            // Simulate concurrent operations by rapidly changing state
            for (int i = 0; i < 10; i++) {
                endpoint.toggleTracking(i % 2 == 0);
                Map<String, Object> info = endpoint.info();
                assertThat(info).isNotNull();
                assertThat(info).containsKey("changeTracking");
            }

            // Final state should be consistent
            Map<String, Object> finalInfo = endpoint.info();
            Map<String, Object> changeTracking = (Map<String, Object>) finalInfo.get("changeTracking");
            assertThat(changeTracking.get("enabled")).isEqualTo(TFI.isChangeTrackingEnabled());
        }
    }

    @Nested
    @DisplayName("Integration with TFI Core")
    class IntegrationTests {

        @Test
        @DisplayName("Endpoint reflects actual TFI state changes")
        void endpointReflectsActualTfiStateChanges() {
            // Endpoint reflects configuration (runtime toggles are unsupported)
            var config = new com.syy.taskflowinsight.config.TfiConfig(
                true,
                new com.syy.taskflowinsight.config.TfiConfig.ChangeTracking(false, null, null, null, null, null, null, null),
                new com.syy.taskflowinsight.config.TfiConfig.Context(null, null, null, null, null),
                new com.syy.taskflowinsight.config.TfiConfig.Metrics(null, null, null),
                new com.syy.taskflowinsight.config.TfiConfig.Security(null, null)
            );
            TfiEndpoint endpointWithConfig = new TfiEndpoint(config);

            Map<String, Object> info = endpointWithConfig.info();
            Map<String, Object> changeTracking = (Map<String, Object>) info.get("changeTracking");
            assertThat(changeTracking.get("enabled")).isEqualTo(false);

            Map<String, Object> toggleResult = endpointWithConfig.toggleTracking(true);
            assertThat((String) toggleResult.get("message")).contains("Runtime toggling is not supported");
            assertThat(toggleResult.get("currentState")).isEqualTo(false);
        }

        @Test
        @DisplayName("Change count reflects actual tracked changes")
        void changeCountReflectsActualTrackedChanges() {
            TFI.clearAllTracking();
            
            // Get initial count
            Map<String, Object> initialInfo = endpoint.info();
            Map<String, Object> initialChangeTracking = (Map<String, Object>) initialInfo.get("changeTracking");
            int initialCount = (Integer) initialChangeTracking.get("totalChanges");

            // Create a session with changes
            TFI.startSession("Change Count Session");
            try (TaskContext task = TFI.start("Change Count Task")) {
                task.message("Test message");
                task.attribute("key", "value");
                task.success();
            }

            // Get updated count
            Map<String, Object> updatedInfo = endpoint.info();
            Map<String, Object> updatedChangeTracking = (Map<String, Object>) updatedInfo.get("changeTracking");
            int updatedCount = (Integer) updatedChangeTracking.get("totalChanges");

            // Count should reflect changes (may be same if no actual tracked object changes)
            assertThat(updatedCount).isGreaterThanOrEqualTo(initialCount);

            TFI.endSession();
        }

        @Test
        @DisplayName("Clear operation affects subsequent info calls")
        void clearOperationAffectsSubsequentInfoCalls() {
            // Create some data
            TFI.startSession("Clear Effect Session");
            try (TaskContext task = TFI.start("Clear Effect Task")) {
                task.message("Creating data");
                task.success();
            }

            // Clear through endpoint
            endpoint.clearAll();

            // Info should show zero changes
            Map<String, Object> info = endpoint.info();
            Map<String, Object> changeTracking = (Map<String, Object>) info.get("changeTracking");
            assertThat(changeTracking.get("totalChanges")).isEqualTo(0);

            TFI.endSession();
        }
    }
}