package com.syy.taskflowinsight;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot configuration for tests in {@code tfi-all}.
 *
 * <p>Many historical tests rely on Spring Boot's default configuration discovery
 * (searching upwards from the test package for a {@code @SpringBootConfiguration}).
 * After the multi-module split, the demo application's {@code TaskFlowInsightApplication}
 * lives in {@code tfi-examples} and is not on this module's test classpath.
 * This class restores a stable test bootstrap target for {@code @SpringBootTest}
 * without introducing a dependency cycle on {@code tfi-examples}.</p>
 */
@SpringBootApplication
public class TestApplication {
    // no-op
}

