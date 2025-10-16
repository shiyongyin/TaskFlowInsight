package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.core.TfiCore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 最小化调试测试 - 用于诊断 Provider 路由问题
 */
public class TFIRoutingDebugTest {

    @Test
    void debugExportToConsoleRouting() throws Exception {
        System.out.println("\n=== DEBUG START ===");

        // Step 1: Set routing enabled
        System.setProperty("tfi.api.routing.enabled", "true");
        System.out.println("1. Set tfi.api.routing.enabled=true");

        // Step 2: Register test provider
        TestExportProvider testProvider = new TestExportProvider();
        TFI.registerExportProvider(testProvider);
        System.out.println("2. Registered TestExportProvider");

        // Step 3: Check if routing is enabled
        boolean routingEnabled = com.syy.taskflowinsight.config.TfiFeatureFlags.isRoutingEnabled();
        System.out.println("3. isRoutingEnabled() = " + routingEnabled);

        // Step 4: Create and inject TfiCore
        TfiConfig config = new TfiConfig(
            true,
            new TfiConfig.ChangeTracking(true, null, null, null, null, null, null, null),
            new TfiConfig.Context(null, null, null, null, null),
            new TfiConfig.Metrics(null, null, null),
            new TfiConfig.Security(null, null)
        );
        TfiCore core = new TfiCore(config);
        Field coreField = TFI.class.getDeclaredField("core");
        coreField.setAccessible(true);
        coreField.set(null, core);
        System.out.println("4. Injected TfiCore, isEnabled() = " + TFI.isEnabled());

        // Step 5: Lookup provider directly
        com.syy.taskflowinsight.spi.ExportProvider lookedUpProvider =
            com.syy.taskflowinsight.spi.ProviderRegistry.lookup(com.syy.taskflowinsight.spi.ExportProvider.class);
        System.out.println("5. Looked up provider: " + (lookedUpProvider != null ? lookedUpProvider.getClass().getSimpleName() : "null"));
        System.out.println("   Is TestExportProvider? " + (lookedUpProvider instanceof TestExportProvider));

        // Step 6: Call TFI.exportToConsole
        System.out.println("6. Calling TFI.exportToConsole(false)...");
        boolean result = TFI.exportToConsole(false);
        System.out.println("   Result: " + result);

        // Step 7: Check if test provider method was called
        boolean wasCalled = testProvider.wasMethodCalled("exportToConsole");
        System.out.println("7. Test provider method called? " + wasCalled);
        System.out.println("   Method calls: " + testProvider.getMethodCallCount("exportToConsole"));

        System.out.println("=== DEBUG END ===\n");

        // Assertions
        assertTrue(routingEnabled, "Routing should be enabled");
        assertTrue(TFI.isEnabled(), "TFI should be enabled");
        assertNotNull(lookedUpProvider, "Provider lookup should return a provider");
        assertTrue(lookedUpProvider instanceof TestExportProvider, "Should be TestExportProvider");
        assertTrue(wasCalled, "TestExportProvider.exportToConsole should have been called");

        // Cleanup
        System.clearProperty("tfi.api.routing.enabled");
    }
}
