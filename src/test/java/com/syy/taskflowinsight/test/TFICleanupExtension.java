package com.syy.taskflowinsight.test;

import com.syy.taskflowinsight.api.TFI;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Global JUnit 5 extension to ensure TaskFlow Insight cleans up thread-local
 * resources between tests. This helps avoid cross-test interference and
 * suppresses warnings about closing active contexts.
 */
public class TFICleanupExtension implements AfterEachCallback, AfterAllCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        safeClear();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        safeClear();
    }

    private void safeClear() {
        try {
            TFI.clear();
        } catch (Throwable ignored) {
            // Best-effort cleanup; do not fail tests on cleanup path.
        }
    }
}

