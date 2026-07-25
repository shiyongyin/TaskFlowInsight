package com.syy.taskflowinsight.model;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 为跨包 Context 并发测试提供 Session 捕获入口。
 *
 * <p>该类型只编译到 test output，不进入 production JAR；它只转发真实 package-private capture seam，
 * 不保存锁、permit 或 callback，也不为生产代码扩展可见性。
 *
 * @since 4.0.0
 */
public final class TaskTreeCaptureTestAccess {

    private TaskTreeCaptureTestAccess() {
    }

    /**
     * 在指定 Session 的真实写侧 gate 内运行测试捕获动作。
     *
     * @param session 被捕获的 Session，不可为 null
     * @param captureAction 在 gate 内执行的测试动作，不可为 null
     * @param <T> 捕获结果类型
     * @return 捕获动作的返回值
     */
    public static <T> T capture(Session session, Supplier<T> captureAction) {
        return Objects.requireNonNull(session, "session")
                .captureExport(Objects.requireNonNull(captureAction, "captureAction"));
    }
}
