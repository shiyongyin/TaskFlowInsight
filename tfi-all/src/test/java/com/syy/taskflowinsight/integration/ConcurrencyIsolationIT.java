package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.context.TFIAwareExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发隔离与归属正确性测试（CARD-261）
 * - 使用 TFIAwareExecutor 并发运行子任务
 * - 每个子任务 start -> track/修改 -> stop
 * - 导出 JSON，断言每子节点 TaskNode.messages 仅包含自身的 CHANGE，且不重复
 */
class ConcurrencyIsolationIT {

    static class Item {
        private String status = "PENDING";
        private int count = 0;

        void setStatus(String s) { this.status = s; }
        void setCount(int c) { this.count = c; }
    }

    @BeforeEach
    void init() {
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
        TFI.clear();
    }

    @AfterEach
    void cleanup() {
        TFI.endSession();
        TFI.clear();
    }

    @Test
    void isolationWith10Threads() throws Exception {
        runIsolationScenario(10);
    }

    @Test
    void isolationWith16Threads() throws Exception {
        runIsolationScenario(16);
    }

    private void runIsolationScenario(int threads) throws Exception {
        TFIAwareExecutor executor = TFIAwareExecutor.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        ConcurrentMap<Integer, String> jsonByTask = new ConcurrentHashMap<>();

        // 主会话（用于提供上下文快照）
        TFI.startSession("ConcurrentIsolationSession");
        TFI.start("Main");

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TFI.start("sub-" + idx);

                    Item item = new Item();
                    TFI.track("Obj" + idx, item, "status", "count");

                    item.setStatus("DONE-" + idx);
                    item.setCount(idx);

                    TFI.stop();

                    // 导出子会话 JSON
                    String json = TFI.exportToJson();
                    jsonByTask.put(idx, json);
                } catch (Exception e) {
                    fail("Subtask failed: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(20, TimeUnit.SECONDS));

        // 结束主任务
        TFI.stop();

        // 验证每个子任务的 JSON 仅包含自己的变更
        for (int i = 0; i < threads; i++) {
            String json = jsonByTask.get(i);
            assertNotNull(json, "json for sub-" + i + " should not be null");

            // 子任务自身名称与变更存在
            assertTrue(json.contains("\"name\":\"sub-" + i + "\""), "JSON should contain sub node name");
            assertTrue(json.contains("Obj" + i + ".status: PENDING → DONE-" + i), "status change must exist");
            assertTrue(json.contains("Obj" + i + ".count: 0 → " + i), "count change must exist");

            // 不应包含其他任务的变更
            for (int j = 0; j < threads; j++) {
                if (j == i) continue;
                assertFalse(json.contains("Obj" + j + ".status:"), "should not contain other task status");
                assertFalse(json.contains("Obj" + j + ".count:"), "should not contain other task count");
            }
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
}

