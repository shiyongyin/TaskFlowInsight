package com.syy.taskflowinsight.testkit;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.core.TfiCore;
import com.syy.taskflowinsight.tracking.ChangeTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Field;

/**
 * Shared base class for non-Spring TFI unit tests.
 *
 * <p>Provides consistent lifecycle management:
 * <ul>
 *   <li>{@code @BeforeEach}: Initializes TfiCore, enables TFI + change tracking</li>
 *   <li>{@code @AfterEach}: Clears all state, resets providers, cleans system properties</li>
 * </ul>
 *
 * <p>Usage: extend this class instead of repeating boilerplate in each test.
 *
 * <pre>{@code
 * class MyTest extends TfiTestBase {
 *     @Test
 *     void shouldTrack() {
 *         TFI.track("obj", target, "field");
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public abstract class TfiTestBase {

    protected TfiCore tfiCore;

    @BeforeEach
    void tfiSetUp() throws Exception {
        TfiConfig config = new TfiConfig(
                true,
                new TfiConfig.ChangeTracking(true, null, null, null, null, null, null, null),
                new TfiConfig.Context(null, null, null, null, null),
                new TfiConfig.Metrics(null, null, null),
                new TfiConfig.Security(null, null)
        );
        tfiCore = new TfiCore(config);
        injectTfiCore(tfiCore);
    }

    @AfterEach
    void tfiTearDown() {
        TFI.clear();
        try {
            ChangeTracker.clearAllTracking();
        } catch (Exception ignored) { }
        try {
            clearProviderCache();
        } catch (Exception ignored) { }
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
     * Injects a TfiCore instance into the TFI static field via reflection.
     */
    protected void injectTfiCore(TfiCore core) throws Exception {
        Field coreField = TFI.class.getDeclaredField("core");
        coreField.setAccessible(true);
        coreField.set(null, core);
    }

    /**
     * Clears all cached Provider instances in TFI.
     */
    protected void clearProviderCache() throws Exception {
        String[] cacheFieldNames = {
                "cachedTrackingProvider", "cachedFlowProvider",
                "cachedExportProvider", "cachedRenderProvider",
                "cachedComparisonProvider"
        };
        for (String fieldName : cacheFieldNames) {
            try {
                Field field = TFI.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(null, null);
            } catch (NoSuchFieldException ignored) { }
        }
    }

    /**
     * Clears all registered providers from the ProviderRegistry.
     */
    protected void clearProviderRegistry() {
        try {
            Class<?> registryClass = Class.forName("com.syy.taskflowinsight.spi.ProviderRegistry");
            java.lang.reflect.Method clearMethod = registryClass.getMethod("clearAll");
            clearMethod.invoke(null);
        } catch (Exception ignored) { }
    }
}
