package com.syy.taskflowinsight.tracking.precision;

import com.syy.taskflowinsight.annotation.NumericPrecision;
import com.syy.taskflowinsight.tracking.detector.DiffDetectorService;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeepOptimized;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

class NumericDetectorDebugTest {
    static class Price {
        @NumericPrecision(absoluteTolerance = 0.01, compareMethod = "COMPARE_TO")
        BigDecimal amount;
        Price(String v) { this.amount = new BigDecimal(v); }
    }

    @Test
    void debugDetector() {
        Price before = new Price("1.000");
        Price after = new Price("1.02");
        SnapshotConfig config = new SnapshotConfig();
        config.setEnableDeep(true);
        config.setMaxDepth(10);
        ObjectSnapshotDeepOptimized snapshot = new ObjectSnapshotDeepOptimized(config);
        Map<String, Object> s1 = snapshot.captureDeep(before, 10, java.util.Collections.emptySet(), java.util.Collections.emptySet());
        Map<String, Object> s2 = snapshot.captureDeep(after, 10, java.util.Collections.emptySet(), java.util.Collections.emptySet());
        DiffDetectorService svc = new DiffDetectorService();
        svc.programmaticInitNoSpring();
        svc.registerObjectType("Price", Price.class);
        var changes = svc.diff("Price", s1, s2);
        for (ChangeRecord cr : changes) {
            System.out.println("field=" + cr.getFieldName() + " type=" + cr.getChangeType() + " old=" + cr.getOldValue() + " new=" + cr.getNewValue());
        }
    }
}
