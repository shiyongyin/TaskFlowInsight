package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.context.ContextMetrics;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TaskflowContextEndpoint} 的 4.0 typed response 契约。
 */
class TaskflowContextEndpointTest {

    @Test
    void returnsTheCanonicalContextMetricsSnapshot() {
        Object response = new TaskflowContextEndpoint().taskflow();

        assertThat(response).isInstanceOf(ContextMetrics.class);
        ContextMetrics metrics = (ContextMetrics) response;
        assertThat(metrics.activeContexts()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.createdContexts()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.closedContexts()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.capturedAt()).isNotNull();
    }

    @Test
    void everyResponsePathCapturesAtMostOneContextSnapshot() throws Exception {
        assertSourceContract("actuator/TaskflowContextEndpoint.java", 1);
        assertSourceContract("actuator/TfiEndpoint.java", 1);
        assertSourceContract("actuator/TfiAdvancedEndpoint.java", 1);
        assertSourceContract("actuator/SecureTfiEndpoint.java", 1);
        assertSourceContract("actuator/support/TfiStatsAggregator.java", 1);
        assertSourceContract("actuator/support/TfiHealthCalculator.java", 2);
    }

    private static void assertSourceContract(String relativePath, int expectedMetricCalls)
            throws Exception {
        String source = Files.readString(sourcePath(relativePath));
        assertThat(source).doesNotContain("ThreadContext");
        assertThat(occurrences(source, "SafeContextManager.getInstance().metrics()"))
                .isEqualTo(expectedMetricCalls);
    }

    private static Path sourcePath(String relativePath) {
        Path modulePath = Path.of("src/main/java/com/syy/taskflowinsight", relativePath);
        return Files.isRegularFile(modulePath)
                ? modulePath
                : Path.of("tfi-ops-spring", modulePath.toString());
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        for (int index = source.indexOf(token); index >= 0;
             index = source.indexOf(token, index + token.length())) {
            count++;
        }
        return count;
    }
}
