package com.syy.taskflowinsight.testkit;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.ChangeTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Shared base class for Spring-Boot-based TFI integration tests.
 *
 * <p>Extends {@link TfiTestBase} with Spring context management.
 * Subclasses get a fully initialized Spring context with TFI enabled.
 *
 * <p>For tests that do NOT need Spring context, use {@link TfiTestBase} instead
 * to avoid the ~2-3 second Spring Boot startup penalty per test class.
 *
 * <pre>{@code
 * class MySpringIT extends TfiSpringTestBase {
 *
 *     @Autowired
 *     CompareService compareService;
 *
 *     @Test
 *     void shouldCompareViaSpring() {
 *         // Spring context and TFI are both ready
 *     }
 * }
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
@SpringBootTest
@TestPropertySource(properties = {
        "tfi.enabled=true",
        "tfi.change-tracking.enabled=true"
})
public abstract class TfiSpringTestBase {

    @BeforeEach
    void springTfiSetUp() {
        TFI.clear();
    }

    @AfterEach
    void springTfiTearDown() {
        TFI.clear();
        try {
            ChangeTracker.clearAllTracking();
        } catch (Exception ignored) { }
        System.clearProperty("tfi.api.routing.enabled");
    }
}
