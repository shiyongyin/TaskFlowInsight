package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TfiEndpointUnitTests {

    private TfiEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new TfiEndpoint(null);
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
        // 清理可能的历史追踪
        TFI.clearAllTracking();
    }

    @Test
    void infoContainsExpectedSections() {
        Map<String, Object> info = endpoint.info();
        assertThat(info).containsKeys("version", "timestamp", "changeTracking", "threadContext", "health");
        Map<String, Object> change = (Map<String, Object>) info.get("changeTracking");
        assertThat(change).containsKeys("enabled", "globalEnabled", "totalChanges", "activeTrackers");
    }

    @Test
    void toggleTrackingNull_flipsState() {
        boolean before = TFI.isChangeTrackingEnabled();
        Map<String, Object> result = endpoint.toggleTracking(null);
        boolean previous = (boolean) result.get("previousState");
        boolean current = (boolean) result.get("currentState");
        assertThat(previous).isEqualTo(current);
        assertThat(current).isEqualTo(before);
        assertThat((String) result.get("message")).contains("Runtime toggling is not supported");
        assertThat(TFI.isChangeTrackingEnabled()).isEqualTo(before);
    }

    @Test
    void clearAllClearsChanges() {
        // 先造一些变更
        TFI.recordChange("User", "name", "A", "B", ChangeType.UPDATE);
        TFI.recordChange("Order", "status", "NEW", "PAID", ChangeType.UPDATE);

        Map<String, Object> res = endpoint.clearAll();
        assertThat(res).containsKeys("clearedChanges", "message", "timestamp");
        // 再次查询应为空
        Map<String, Object> info = endpoint.info();
        Map<String, Object> change = (Map<String, Object>) info.get("changeTracking");
        assertThat((Integer) change.get("totalChanges")).isZero();
    }
}

