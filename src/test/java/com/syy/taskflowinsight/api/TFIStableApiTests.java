package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stability-focused tests for TFI facade on normal paths (no exception branches).
 */
class TFIStableApiTests {

    @BeforeEach
    void enable() {
        TFI.enable();
        // ensure clean state
        TFI.clear();
    }

    @AfterEach
    void cleanup() {
        TFI.clear();
    }

    @Test
    void startSession_and_nestedStages_workNormally() {
        String sid = TFI.startSession("API Test Session");
        assertThat(sid).isNotBlank();

        try (TaskContext main = TFI.start("Main Stage")) {
            assertThat(main).isNotNull();
            main.message("hello");
            try (TaskContext sub = main.subtask("Sub Stage")) {
                sub.attribute("k", "v").success();
            }
            main.success();
        }

        Session s = TFI.getCurrentSession();
        assertThat(s).isNotNull();
        TaskNode root = s.getRootTask();
        assertThat(root).isNotNull();
        assertThat(root.getChildren()).isNotEmpty();

        TFI.endSession();
        assertThat(TFI.getCurrentSession()).isNull();
    }

    @Test
    void run_and_call_executeBusinessLogic() {
        final int[] counter = {0};
        TFI.run("Run Stage", () -> counter[0]++);
        Integer val = TFI.call("Call Stage", () -> 42);

        assertThat(counter[0]).isEqualTo(1);
        assertThat(val).isEqualTo(42);
    }

    @Test
    void functionalStage_executes_and_returns() {
        String res = TFI.stage("Func Stage", stage -> {
            stage.attribute("foo", "bar");
            return "ok";
        });
        assertThat(res).isEqualTo("ok");
    }

    @Test
    void withTracked_recordsChangesAndCleans() {
        TFI.startSession("Tracked Session");
        TFI.withTracked("User", new Object(), () -> {
            // simulate business writes no exception
        }, "id", "name");

        // Export to map should not throw when session exists
        Map<String, Object> exported = TFI.exportToMap();
        assertThat(exported).isNotNull();
        TFI.endSession();
    }

    @Test
    void disabledMode_executesBusiness_andAvoidsTracking() {
        TFI.disable();
        // startSession returns null when disabled
        String sid = TFI.startSession("WillNotStart");
        assertThat(sid).isNull();

        final int[] cnt = {0};
        // run should still execute user code
        TFI.run("DisabledStage", () -> cnt[0]++);
        assertThat(cnt[0]).isEqualTo(1);
        // cleanup no-throw
        TFI.clear();
        // enable back for isolation
        TFI.enable();
    }
}
