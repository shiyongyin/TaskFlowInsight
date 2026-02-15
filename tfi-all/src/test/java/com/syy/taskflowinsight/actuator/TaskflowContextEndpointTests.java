package com.syy.taskflowinsight.actuator;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskflowContextEndpointTests {

    @Test
    void taskflowReturnsContextAndThreadLocalSections() {
        TaskflowContextEndpoint ep = new TaskflowContextEndpoint();
        Map<String, Object> res = ep.taskflow();
        assertThat(res).containsKeys("contextManager", "threadLocalManager");
        Map<String, Object> cm = (Map<String, Object>) res.get("contextManager");
        Map<String, Object> tl = (Map<String, Object>) res.get("threadLocalManager");
        assertThat(cm).isNotNull();
        assertThat(tl).isNotNull();
    }
}

