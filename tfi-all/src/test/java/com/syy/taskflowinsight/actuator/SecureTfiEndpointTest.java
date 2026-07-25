package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.actuator.support.TfiHealthCalculator;
import com.syy.taskflowinsight.actuator.support.TfiStatsAggregator;
import com.syy.taskflowinsight.api.TfiFlow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证安全端点只发布当前进程能够证明的Flow与Context事实。
 */
@DisplayName("SecureTfiEndpoint runtime facts")
class SecureTfiEndpointTest {

    private SecureTfiEndpoint endpoint;

    @BeforeEach
    void setUp() {
        TfiFlow.enable();
        endpoint = newEndpoint();
    }

    @AfterEach
    void restoreFlowState() {
        TfiFlow.enable();
    }

    @Test
    void publishesOnlyRealRuntimeFacts() {
        Map<String, Object> response = endpoint.taskflow();

        assertThat(response).containsOnlyKeys(
                "version", "enabled", "uptime", "timestamp", "components",
                "stats", "healthScore", "healthLevel");
        assertThat(response).containsEntry("version", "4.0.0");
        assertThat(response).doesNotContainKey("config");

        assertThat(componentStatus(response)).containsOnlyKeys("flow", "context")
                .containsEntry("flow", "ENABLED");
    }

    @Test
    void reportsTheActualFlowSwitch() {
        TfiFlow.disable();

        Map<String, Object> response = newEndpoint().taskflow();

        assertThat(response).containsEntry("enabled", false);
        assertThat(componentStatus(response)).containsEntry("flow", "DISABLED");
    }

    @Test
    void cachesTheCompleteResponse() {
        Map<String, Object> first = endpoint.taskflow();
        Map<String, Object> second = endpoint.taskflow();

        assertThat(second).isSameAs(first);
    }

    @Test
    void exposesNoMutationOperation() {
        for (Method method : SecureTfiEndpoint.class.getDeclaredMethods()) {
            assertThat(method.isAnnotationPresent(WriteOperation.class)).isFalse();
            assertThat(method.isAnnotationPresent(DeleteOperation.class)).isFalse();
        }
    }

    @Test
    void responseContainsNoConfigurationOrSensitiveValues() {
        String response = endpoint.taskflow().toString().toLowerCase();

        assertThat(response)
                .doesNotContain("password", "secret", "token", "credential")
                .doesNotContain("datamaskingenabled", "changetrackingenabled");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> componentStatus(Map<String, Object> response) {
        return (Map<String, Object>) response.get("components");
    }

    private static SecureTfiEndpoint newEndpoint() {
        TfiHealthCalculator healthCalculator = new TfiHealthCalculator();
        ReflectionTestUtils.setField(healthCalculator, "memoryThreshold", 0.8);
        ReflectionTestUtils.setField(healthCalculator, "maxActiveContexts", 100);
        SecureTfiEndpoint endpoint = new SecureTfiEndpoint(
                healthCalculator,
                new TfiStatsAggregator());
        ReflectionTestUtils.setField(endpoint, "cacheTtlMs", 5_000L);
        return endpoint;
    }
}
