package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.spi.ExportProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通过公开生命周期验证 ExportProvider 的解析与 facade 路由。
 */
class TFIRoutingDebugTest {

    private TestExportProvider testProvider;

    @BeforeEach
    void setUp() {
        TFI.clear();
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        testProvider = new TestExportProvider();
        TFI.registerExportProvider(testProvider);
        TFI.enable();
    }

    @AfterEach
    void tearDown() {
        TFI.clear();
        TFI.disable();
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
    }

    @Test
    void publicLookupAndFacadeRouteUseRegisteredExportProvider() {
        ExportProvider resolved = ProviderRegistry.resolve(ExportProvider.class);

        boolean exported = TFI.exportToConsole(false);

        assertTrue(TFI.isEnabled());
        assertSame(testProvider, resolved);
        assertTrue(exported);
        assertEquals(1, testProvider.getMethodCallCount("exportToConsole"));
    }
}
