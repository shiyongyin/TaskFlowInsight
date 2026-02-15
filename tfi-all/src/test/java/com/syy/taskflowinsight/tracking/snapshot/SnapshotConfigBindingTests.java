package com.syy.taskflowinsight.tracking.snapshot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 shallow-reference-mode 配置项绑定
 */
@SpringBootTest
@TestPropertySource(properties = {
        "tfi.change-tracking.snapshot.shallow-reference-mode=COMPOSITE_STRING"
})
class SnapshotConfigBindingTests {

    @Autowired
    private SnapshotConfig snapshotConfig;

    @Test
    void testShallowReferenceModeBinding() {
        assertNotNull(snapshotConfig);
        assertEquals(ShallowReferenceMode.COMPOSITE_STRING, snapshotConfig.getShallowReferenceMode());
    }
}

