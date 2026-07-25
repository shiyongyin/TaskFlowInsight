package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.context.ContextMetrics;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskflowContextEndpointTests {

    @Test
    void taskflowReturnsCanonicalContextMetrics() {
        TaskflowContextEndpoint ep = new TaskflowContextEndpoint();
        ContextMetrics metrics = ep.taskflow();

        assertThat(metrics.activeContexts()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.createdContexts()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.capturedAt()).isNotNull();
    }
}
