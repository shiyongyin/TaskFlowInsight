package com.syy.taskflowinsight.testkit;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared base class for non-Spring TFI unit tests.
 *
 * <p>Provides consistent lifecycle management:
 * <ul>
 *   <li>{@code @BeforeEach}: Resets Provider state, then enables TFI and change tracking</li>
 *   <li>{@code @AfterEach}: Clears public facade state, disables TFI, and resets Providers</li>
 * </ul>
 *
 * <p>Usage: extend this class instead of repeating boilerplate in each test.
 *
 * <pre>{@code
 * class MyTest extends TfiTestBase {
 *     @Test
 *     void shouldTrackExactlyOnce() {
 *         TFI.withTracked("obj", target, action);
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public abstract class TfiTestBase {

    @BeforeEach
    void tfiSetUp() {
        TFI.clear();
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        System.clearProperty("tfi.api.routing.enabled");
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
    }

    @AfterEach
    void tfiTearDown() {
        TFI.clear();
        TFI.disable();
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        System.clearProperty("tfi.api.routing.enabled");
    }

    /**
     * Enables v4.0.0 Provider routing for the current test.
     */
    protected void enableRouting() {
        System.setProperty("tfi.api.routing.enabled", "true");
    }

    /**
     * Disables v4.0.0 Provider routing for the current test.
     */
    protected void disableRouting() {
        System.setProperty("tfi.api.routing.enabled", "false");
    }

    /**
     * Clears all registered providers from the ProviderRegistry.
     */
    protected void clearProviderRegistry() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
    }
}
