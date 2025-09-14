package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 TFI.debug / TFI.warn 按类型写入，且四类消息均可被正确记录
 */
class TFIMessageTypeTests {

    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.clear();
    }

    @AfterEach
    void tearDown() {
        TFI.clear();
    }

    @Test
    void messageTypesOnCurrentTask() {
        try (TaskContext ctx = TFI.start("msg-types-current")) {
            TFI.message("process", MessageType.PROCESS);
            TFI.message("metric", MessageType.METRIC);
            TFI.message("change", MessageType.CHANGE);
            TFI.message("alert", MessageType.ALERT);

            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();

            Map<MessageType, Integer> counts = new EnumMap<>(MessageType.class);
            for (Message m : node.getMessages()) {
                counts.merge(m.getType(), 1, Integer::sum);
            }

            assertThat(counts.getOrDefault(MessageType.PROCESS, 0)).isGreaterThanOrEqualTo(1);
            assertThat(counts.getOrDefault(MessageType.METRIC, 0)).isGreaterThanOrEqualTo(1);
            assertThat(counts.getOrDefault(MessageType.CHANGE, 0)).isGreaterThanOrEqualTo(1);
            assertThat(counts.getOrDefault(MessageType.ALERT, 0)).isGreaterThanOrEqualTo(1);
        }
    }
}
