package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.api.TfiFlow;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.context.SafeContextManager;
import com.syy.taskflowinsight.exporter.json.JsonExporter;
import com.syy.taskflowinsight.exporter.map.MapExporter;
import com.syy.taskflowinsight.exporter.text.ConsoleExporter;
import com.syy.taskflowinsight.internal.FlowConfigDefaults;
import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.spi.FlowProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * 红队审计回归测试（第二轮，2026-07 审计）。
 *
 * <p>每个用例对应一项已修复的审计发现（H7-H21），防止并发、内存与导出缺陷回潮。
 */
class RedTeamRegression2Test {

    @BeforeEach
    void setUp() {
        TfiFlow.enable();
        TfiFlow.clear();
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
    }

    @AfterEach
    void tearDown() {
        TfiFlow.enable();
        TfiFlow.clear();
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
    }

    @Test
    @DisplayName("H7 - disable() 之后清理类 API 仍必须释放 ThreadLocal 上下文")
    void clearWorksAfterDisable() {
        TfiFlow.startSession("h7");
        assertThat(TfiFlow.getCurrentSession()).isNotNull();

        // 运行期熔断降级：在途请求的收尾清理不得因 enabled=false 短路
        TfiFlow.disable();
        TfiFlow.clear();
        TfiFlow.enable();

        assertThat(TfiFlow.getCurrentSession()).as("disable 后 clear 必须真正清理会话").isNull();
        assertThat(ManagedThreadContext.current()).isNull();
    }

    @Test
    @DisplayName("H8 - executeAsync 的任务抛 Error 时 Future 必须完成而非永久阻塞")
    void executeAsyncCompletesOnError() {
        CompletableFuture<Void> future = SafeContextManager.getInstance()
            .executeAsync("h8", () -> {
                throw new AssertionError("boom");
            });

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("H9 - 根任务被并发终止后 session.complete() 仍收敛到一致状态")
    void sessionCompleteConvergesWhenRootTaskAlreadyTerminated() {
        Session session = Session.create("h9");
        // 模拟并发线程（如泄漏检测器）先终止了根任务
        session.getRootTask().fail();

        assertThatNoException().isThrownBy(session::complete);
        assertThat(session.isCompleted()).isTrue();
        assertThat(session.getCompletedMillis()).as("markCompleted 不得被竞态跳过").isNotNull();
        assertThat(session.getDurationMillis()).isNotNull();
    }

    @Test
    @DisplayName("H10 - 对已终止任务调用 fail(msg) 抛异常但不得残留幽灵错误消息")
    void failOnTerminatedTaskLeavesNoGhostMessage() {
        TaskNode node = new TaskNode("h10");
        node.complete();

        assertThatThrownBy(() -> node.fail("late failure")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> node.fail(new RuntimeException("late")))
            .isInstanceOf(IllegalStateException.class);
        assertThat(node.getMessages()).as("先校验后副作用：不得残留错误消息").isEmpty();

        // 幂等变体：不抛异常、不加消息
        assertThat(node.tryFail("late")).isFalse();
        assertThat(node.getMessages()).isEmpty();
    }

    @Test
    @DisplayName("H11 - stage.success() 与 close() 并发竞争后任务栈必须收敛")
    void closeConvergesUnderConcurrentSuccess() throws Exception {
        TfiFlow.startSession("h11");
        for (int i = 0; i < 100; i++) {
            TaskContext stage = TfiFlow.stage("iter" + i);
            CountDownLatch start = new CountDownLatch(1);
            Thread racer = new Thread(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                stage.success();
            });
            racer.start();
            start.countDown();
            stage.close();
            racer.join(5000);

            TaskNode current = TfiFlow.getCurrentTask();
            assertThat(current).isNotNull();
            assertThat(current.getTaskName())
                .as("第 %d 轮竞争后栈必须收敛回根任务", i)
                .isEqualTo("h11");
        }
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("H12 - ConsoleExporter 共享实例并发导出互不污染")
    void consoleExporterHasNoSharedMutableState() throws Exception {
        Session plain = Session.create("plain");
        plain.getRootTask().addInfo("plain message");
        plain.complete();

        Session other = Session.create("other");
        other.getRootTask().addInfo("other message");
        other.complete();

        ConsoleExporter shared = new ConsoleExporter();
        AtomicBoolean polluted = new AtomicBoolean(false);
        CountDownLatch start = new CountDownLatch(1);

        Thread noisy = new Thread(() -> {
            try {
                start.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            for (int i = 0; i < 500; i++) {
                shared.exportSimple(other, true);
            }
        });
        noisy.start();
        start.countDown();

        for (int i = 0; i < 500; i++) {
            String output = shared.exportSimple(plain, false);
            if (output.contains(" @")) {
                polluted.set(true);
                break;
            }
        }
        noisy.join(10000);

        assertThat(polluted).as("showTimestamp=false 的输出不得被并发调用污染出时间戳").isFalse();
    }

    @Test
    @DisplayName("H13 - 超深任务树导出不得栈溢出，超限子树被截断标记")
    void deepTreeExportDoesNotOverflow() {
        Session session = Session.create("deep");
        TaskNode cursor = session.getRootTask();
        for (int i = 0; i < FlowConfigDefaults.MAX_EXPORT_DEPTH + 500; i++) {
            cursor = cursor.createChild("c");
        }
        session.complete();

        JsonExporter jsonExporter = new JsonExporter();
        AtomicReference<String> json = new AtomicReference<>();
        assertThatNoException().isThrownBy(() -> json.set(jsonExporter.export(session)));
        assertThat(json.get()).contains("childrenTruncated");

        ConsoleExporter consoleExporter = new ConsoleExporter();
        assertThatNoException().isThrownBy(() -> consoleExporter.export(session));
        assertThatNoException().isThrownBy(() -> consoleExporter.exportSimple(session, false));

        AtomicReference<Map<String, Object>> map = new AtomicReference<>();
        assertThatNoException().isThrownBy(() -> map.set(MapExporter.export(session)));
        assertThat(map.get()).isNotEmpty();
    }

    @Test
    @DisplayName("H14 - JSON 导出必须转义孤立代理项与 U+2028/U+2029")
    void jsonEscapesLoneSurrogatesAndLineSeparators() {
        Session session = Session.create("surrogate");
        session.getRootTask().addInfo("bad\uD83Dtail");
        session.getRootTask().addInfo("line\u2028sep");
        session.complete();

        String json = new JsonExporter().export(session);

        assertThat(json).contains("bad\\ud83dtail");
        assertThat(json).contains("line\\u2028sep");
        assertThat(json.indexOf('\u2028')).as("原始 U+2028 不得出现在输出中").isEqualTo(-1);
        // 输出中不得残留裸的孤立代理字符（完整代理对除外——本用例没有）
        for (int i = 0; i < json.length(); i++) {
            assertThat(Character.isSurrogate(json.charAt(i)))
                .as("位置 %d 出现裸代理字符", i)
                .isFalse();
        }
    }

    @Test
    @DisplayName("H15 - MapExporter 仅发布 canonical V2 rootTask 与纳秒耗时口径")
    @SuppressWarnings("unchecked")
    void mapExporterPublishesCanonicalV2Root() {
        Session session = Session.create("h15");
        session.getRootTask().createChild("child").complete();
        session.complete();

        Map<String, Object> map = MapExporter.export(session);
        Map<String, Object> rootTask = (Map<String, Object>) map.get("rootTask");
        List<Map<String, Object>> children =
                (List<Map<String, Object>>) rootTask.get("children");

        assertThat(map)
                .containsEntry("schemaVersion", 2)
                .doesNotContainKeys("task", "tasks");
        assertThat(rootTask).containsKeys(
                "selfDurationNanos", "accumulatedDurationNanos", "children");
        assertThat(children).singleElement().satisfies(child ->
                assertThat(child).containsKeys(
                        "durationNanos", "selfDurationNanos", "accumulatedDurationNanos"));
    }

    @Test
    @DisplayName("H16 - Provider 的 priority() 抛异常不得破坏注册与查找")
    void throwingPriorityDoesNotBreakRegistry() {
        FlowProvider badProvider = new NoOpFlowProvider() {
            @Override
            public int priority() {
                throw new RuntimeException("priority boom");
            }
        };

        assertThatNoException().isThrownBy(() -> ProviderRegistry.register(FlowProvider.class, badProvider));
        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isNotNull();
    }

    @Test
    @DisplayName("H17 - 默认 clear() 面对不推进的 endTask() 不得死循环")
    void defaultClearTerminatesWithoutProgress() {
        TaskNode stuck = new TaskNode("stuck");
        FlowProvider nonProgressing = new NoOpFlowProvider() {
            @Override
            public TaskNode currentTask() {
                return stuck; // endTask() 吞掉失败、永不弹栈
            }
        };

        assertTimeoutPreemptively(Duration.ofSeconds(5), nonProgressing::clear);
    }

    @Test
    @DisplayName("H18 - 首次选择后收紧白名单必须被拒绝且不改变选择")
    void runtimeAllowListMutationIsRejectedWithoutChangingSelection() {
        FlowProvider provider = new NoOpFlowProvider();
        ProviderRegistry.register(FlowProvider.class, provider);
        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isSameAs(provider);

        assertThatThrownBy(() ->
            ProviderRegistry.setAllowedProviders(List.of("com.example.OnlyThisProvider")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Provider registry is frozen after runtime start");
        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isSameAs(provider);
    }

    @Test
    @DisplayName("H19 - 单节点消息数量达到软上限后截断并追加哨兵告警")
    void messageCapPreventsUnboundedGrowth() {
        TaskNode node = new TaskNode("h19");
        int limit = FlowConfigDefaults.MAX_MESSAGES_PER_NODE;
        for (int i = 0; i < limit + 50; i++) {
            node.addInfo("m" + i);
        }

        List<Message> messages = node.getMessages();
        assertThat(messages).hasSize(limit + 1); // 上限 + 1 条哨兵
        assertThat(messages.get(limit).getContent()).contains("Message limit");
    }

    @Test
    @DisplayName("H20 - 上下文 close 后必须释放会话引用")
    void closeReleasesSessionReference() {
        ManagedThreadContext context = ManagedThreadContext.create("h20");
        assertThat(context.getCurrentSession()).isNotNull();

        context.close();

        assertThat(context.getCurrentSession())
            .as("closed 上下文不得继续钉住 Session/TaskNode 树")
            .isNull();
    }

    @Test
    @DisplayName("H21 - error(null, throwable) 不得产生字面 'null - ' 前缀")
    void errorWithNullContentUsesThrowableDescription() {
        TfiFlow.startSession("h21");
        try (TaskContext stage = TfiFlow.stage("work")) {
            TfiFlow.error(null, new IllegalStateException("boom"));
            TaskNode current = TfiFlow.getCurrentTask();
            assertThat(current).isNotNull();
            List<Message> messages = current.getMessages();
            assertThat(messages).isNotEmpty();
            String content = messages.get(messages.size() - 1).getContent();
            assertThat(content).doesNotStartWith("null");
            assertThat(content).contains("IllegalStateException");
        } finally {
            TfiFlow.endSession();
        }
    }

    /**
     * 无操作的 FlowProvider 基类，供匿名子类覆盖单个行为。
     */
    private static class NoOpFlowProvider implements FlowProvider {
        @Override
        public String startSession(String name) {
            return null;
        }

        @Override
        public void endSession() {
            // no-op
        }

        @Override
        public TaskNode startTask(String name) {
            return null;
        }

        @Override
        public void endTask() {
            // no-op
        }

        @Override
        public Session currentSession() {
            return null;
        }

        @Override
        public TaskNode currentTask() {
            return null;
        }

        @Override
        public void message(String content, String label) {
            // no-op
        }
    }
}
