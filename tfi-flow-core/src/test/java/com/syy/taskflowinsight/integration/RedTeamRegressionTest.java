package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.api.TfiFlow;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.context.ContextManagerConfig;
import com.syy.taskflowinsight.context.SafeContextManager;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 红队审计回归测试。
 *
 * <p>每个用例对应一项已修复的审计发现（H1/H2/H3/H4/H5/H6），
 * 防止并发与内存缺陷回潮。
 */
class RedTeamRegressionTest {

    @BeforeEach
    void setUp() {
        TfiFlow.enable();
        TfiFlow.clear();
    }

    @AfterEach
    void tearDown() {
        TfiFlow.clear();
        SafeContextManager.getInstance().apply(ContextManagerConfig.defaults());
    }

    @Test
    @DisplayName("H1 - 包装任务内联执行(CallerRunsPolicy)不得关闭调用者的活动上下文")
    void inlineExecutionPreservesCallerContext() {
        ManagedThreadContext caller = ManagedThreadContext.create("caller-task");
        try {
            Runnable wrapped = SafeContextManager.getInstance().wrapRunnable(() -> { });
            // 模拟 CallerRunsPolicy：任务在提交线程上直接 run()
            wrapped.run();

            assertThat(caller.isClosed()).as("调用者上下文不应被内联任务关闭").isFalse();
            assertThat(caller.getCurrentSession()).isNotNull();
            assertThat(caller.getCurrentSession().isActive()).isTrue();
            assertThat(ManagedThreadContext.current()).isSameAs(caller);
        } finally {
            caller.close();
        }
    }

    @Test
    @DisplayName("H2 - 乱序 close 不得弹出不相干的任务")
    void outOfOrderCloseDoesNotPopWrongTask() {
        TfiFlow.startSession("s");
        TaskContext a = TfiFlow.start("a");
        TaskContext b = a.subtask("b");

        // 外层先关闭：应收栈到 a（连带结束子任务 b），而不是只弹出栈顶 b
        a.close();
        TaskContext c = TfiFlow.start("c");
        TaskNode current = TfiFlow.getCurrentTask();
        assertThat(current.getTaskName()).isEqualTo("c");
        assertThat(current.getParent().getTaskName())
            .as("c 应挂在根任务下，而不是已完成的 a 下")
            .isEqualTo("s");

        // b 已不在栈上：close 必须是 no-op，不得弹出正在运行的 c
        b.close();
        assertThat(TfiFlow.getCurrentTask().getTaskName()).isEqualTo("c");

        c.close();
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("H3 - run/call/stage(fn) 必须传播业务异常")
    void businessExceptionsPropagate() {
        TfiFlow.startSession("s");

        assertThatThrownBy(() -> TfiFlow.call("t", () -> {
            throw new IllegalStateException("业务失败");
        })).isInstanceOf(IllegalStateException.class).hasMessage("业务失败");

        assertThatThrownBy(() -> TfiFlow.run("t", () -> {
            throw new IllegalArgumentException("参数错误");
        })).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> TfiFlow.stage("t", stage -> {
            throw new IllegalStateException("stage 失败");
        })).isInstanceOf(IllegalStateException.class);

        // 受检异常包装为 RuntimeException 传播，保留原因链
        assertThatThrownBy(() -> TfiFlow.call("t", (Callable<Object>) () -> {
            throw new Exception("受检异常");
        })).isInstanceOf(RuntimeException.class)
           .hasCauseInstanceOf(Exception.class);

        // 禁用态同样传播，不得吞掉
        TfiFlow.disable();
        try {
            assertThatThrownBy(() -> TfiFlow.call("t", () -> {
                throw new IllegalStateException("禁用态业务失败");
            })).isInstanceOf(IllegalStateException.class);
        } finally {
            TfiFlow.enable();
        }
        TfiFlow.endSession();
    }

    @Test
    @DisplayName("H4 - 线程死亡且未 close 的上下文不得被注册表钉在内存里")
    void orphanContextsAreReclaimedByGc() throws Exception {
        SafeContextManager manager = SafeContextManager.getInstance();
        int baseline = manager.getActiveContextCount();

        for (int i = 0; i < 5; i++) {
            Thread t = new Thread(() -> ManagedThreadContext.create("orphan"));
            t.start();
            t.join();
        }

        // 弱引用回收依赖 GC，带超时轮询
        long deadline = System.currentTimeMillis() + 10_000;
        while (manager.getActiveContextCount() > baseline && System.currentTimeMillis() < deadline) {
            System.gc();
            Thread.sleep(50);
        }
        assertThat(manager.getActiveContextCount())
            .as("孤儿上下文应随 GC 从注册表消失")
            .isLessThanOrEqualTo(baseline);
    }

    @Test
    @DisplayName("H5 - 重复启用泄漏检测不得累积调度任务")
    void leakDetectionSchedulingIsIdempotent() throws Exception {
        SafeContextManager manager = SafeContextManager.getInstance();
        ContextManagerConfig enabled = new ContextManagerConfig(3_600_000L, true, 60_000L);

        Field runtimeField = SafeContextManager.class.getDeclaredField("detectorRuntime");
        runtimeField.setAccessible(true);

        manager.apply(enabled);
        Object first = runtimeField.get(manager);
        manager.apply(enabled);
        manager.apply(enabled);
        Object second = runtimeField.get(manager);

        assertThat(first).isNotNull();
        assertThat(second).as("重复 enable 不应重新注册周期任务").isSameAs(first);
    }

    @Test
    @DisplayName("H6 - JSON 导出必须转义用户提供的属性键")
    void jsonAttributeKeysAreEscaped() {
        TfiFlow.startSession("s");
        try (TaskContext stage = TfiFlow.stage("t")) {
            stage.attribute("bad\"key", "v");
        }
        String json = TfiFlow.exportToJson();
        assertThat(json).contains("bad\\\"key");
        assertThat(json).doesNotContain("\"bad\"key\"");
        TfiFlow.endSession();
    }
}
