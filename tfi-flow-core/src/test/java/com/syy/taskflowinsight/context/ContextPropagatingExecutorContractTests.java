package com.syy.taskflowinsight.context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 4.0 唯一 Context 传播执行器的结构与行为契约。
 *
 * <p>删除旧类型不能只靠编译碰巧通过；本契约同时锁定 source owner 和 canonical construction，
 * 防止后续以新类名重新复制整套 forwarding。
 */
class ContextPropagatingExecutorContractTests {

    private static final String LEGACY_EXECUTOR =
            "com.syy.taskflowinsight.context.TFIAwareExecutor";
    private ExecutorService delegate;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        ThreadContext.clear();
        delegate = Executors.newFixedThreadPool(2);
        executor = ContextPropagatingExecutor.wrap(delegate);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        ThreadContext.clear();
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void removesLegacyExecutorTypeAndSource() {
        assertThatThrownBy(() -> Class.forName(LEGACY_EXECUTOR))
                .isInstanceOf(ClassNotFoundException.class);
        assertThat(contextSourceRoot().resolve("TFIAwareExecutor.java")).doesNotExist();
    }

    @Test
    void keepsOneExecutorServiceImplementationOwner() throws Exception {
        List<Path> owners;
        try (var sources = Files.list(contextSourceRoot())) {
            owners = sources
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> read(path).contains("implements ExecutorService"))
                    .toList();
        }

        assertThat(owners)
                .extracting(path -> path.getFileName().toString())
                .containsExactly("ContextPropagatingExecutor.java");
    }

    @Test
    void wrapRejectsNullAndKeepsCanonicalWrapperIdempotent() {
        assertThatThrownBy(() -> ContextPropagatingExecutor.wrap(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Executor cannot be null");

        ExecutorService delegate = Executors.newSingleThreadExecutor();
        ExecutorService wrapped = ContextPropagatingExecutor.wrap(delegate);
        try {
            assertThat(ContextPropagatingExecutor.wrap(wrapped)).isSameAs(wrapped);
        } finally {
            wrapped.shutdownNow();
        }
    }

    @Test
    void forwardsExecuteAndAllSubmitOverloads() throws Exception {
        CountDownLatch executed = new CountDownLatch(1);
        executor.execute(executed::countDown);

        AtomicBoolean runnableExecuted = new AtomicBoolean();
        Future<?> runnable = executor.submit(() -> runnableExecuted.set(true));
        Future<String> runnableWithResult = executor.submit(() -> { }, "result");
        Future<String> callable = executor.submit(() -> "callable");

        assertThat(executed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(runnable.get(5, TimeUnit.SECONDS)).isNull();
        assertThat(runnableExecuted).isTrue();
        assertThat(runnableWithResult.get(5, TimeUnit.SECONDS)).isEqualTo("result");
        assertThat(callable.get(5, TimeUnit.SECONDS)).isEqualTo("callable");
    }

    @Test
    void forwardsBothInvokeAllOverloadsInInputOrder() throws Exception {
        List<Callable<Integer>> tasks = List.of(() -> 1, () -> 2, () -> 3);

        assertThat(values(executor.invokeAll(tasks))).containsExactly(1, 2, 3);
        assertThat(values(executor.invokeAll(tasks, 5, TimeUnit.SECONDS)))
                .containsExactly(1, 2, 3);
    }

    @Test
    void forwardsBothInvokeAnyOverloadsAndTimeout() throws Exception {
        List<Callable<String>> tasks = List.of(() -> "one", () -> "two");
        assertThat(executor.invokeAny(tasks)).isIn("one", "two");
        assertThat(executor.invokeAny(tasks, 5, TimeUnit.SECONDS)).isIn("one", "two");

        CountDownLatch release = new CountDownLatch(1);
        try {
            assertThatThrownBy(() -> executor.invokeAny(
                    List.of(() -> {
                        release.await();
                        return "late";
                    }),
                    20,
                    TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            release.countDown();
        }
    }

    @Test
    void preservesBusinessFailureAsFutureCause() {
        RuntimeException failure = new RuntimeException("executor business failure");
        Future<String> future = executor.submit(() -> {
            throw failure;
        });

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCause(failure);
    }

    @Test
    void handlesEmptyCollectionsAndRejectsSubmissionAfterShutdown() throws Exception {
        assertThat(executor.invokeAll(List.<Callable<Object>>of())).isEmpty();
        assertThatThrownBy(() -> executor.invokeAny(List.<Callable<Object>>of()))
                .isInstanceOf(IllegalArgumentException.class);

        executor.shutdown();

        assertThatThrownBy(() -> executor.submit(() -> "rejected"))
                .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void closesPropagatedChildBeforeFutureCompletion() throws Exception {
        SafeContextManager manager = SafeContextManager.getInstance();
        long activeBefore = manager.metrics().activeContexts();

        try (ManagedThreadContext parent = ManagedThreadContext.create("executor-parent")) {
            String parentId = parent.getContextId();
            Future<ContextObservation> future = executor.submit(() -> {
                ManagedThreadContext child = ManagedThreadContext.current();
                return new ContextObservation(
                        child.getContextId(), child.getAttribute("parent.contextId"));
            });

            ContextObservation observation = future.get(5, TimeUnit.SECONDS);
            assertThat(observation.contextId()).isNotEqualTo(parentId);
            assertThat(observation.parentContextId()).isEqualTo(parentId);
            assertThat(manager.metrics().activeContexts()).isEqualTo(activeBefore + 1);
        }

        assertThat(manager.metrics().activeContexts()).isEqualTo(activeBefore);
    }

    @Test
    void forwardsShutdownNowAndAwaitTermination() throws Exception {
        ExecutorService singleDelegate = Executors.newSingleThreadExecutor();
        ExecutorService single = ContextPropagatingExecutor.wrap(singleDelegate);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        single.execute(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        single.submit(() -> { });

        try {
            assertThat(single.shutdownNow()).hasSize(1);
            assertThat(single.isShutdown()).isTrue();
            assertThat(single.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(single.isTerminated()).isTrue();
        } finally {
            release.countDown();
            single.shutdownNow();
        }
    }

    private static List<Integer> values(List<Future<Integer>> futures) {
        return futures.stream().map(future -> {
            try {
                return future.get(5, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot read executor result", exception);
            }
        }).toList();
    }

    private static Path contextSourceRoot() {
        Path direct = Path.of("src/main/java/com/syy/taskflowinsight/context");
        return Files.isDirectory(direct)
                ? direct
                : Path.of("tfi-flow-core/src/main/java/com/syy/taskflowinsight/context");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read source: " + path, exception);
        }
    }

    private record ContextObservation(String contextId, String parentContextId) {
    }
}
