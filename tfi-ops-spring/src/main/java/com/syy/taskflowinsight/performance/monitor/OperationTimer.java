package com.syy.taskflowinsight.performance.monitor;

/**
 * 操作计时器。
 *
 * <p>实现 {@link AutoCloseable}，在 {@link #close()} 时自动记录操作耗时和成功状态。
 * 典型用法：</p>
 * <pre>{@code
 * try (var t = monitor.startTimer("op")) {
 *     // ... business logic ...
 *     t.setSuccess(false); // mark failure if needed
 * }
 * }</pre>
 *
 * <p>线程安全：每个实例仅由创建线程使用，无需同步。</p>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
public class OperationTimer implements AutoCloseable {

    private final String operation;
    private final PerformanceMonitor monitor;
    private final long startNanos;
    private boolean success = true;

    /**
     * 创建计时器（包级别可见，由 {@link PerformanceMonitor#startTimer(String)} 调用）。
     *
     * @param operation 操作名称
     * @param monitor   所属监控器，用于回调记录
     */
    OperationTimer(String operation, PerformanceMonitor monitor) {
        this.operation = operation;
        this.monitor = monitor;
        this.startNanos = System.nanoTime();
    }

    /**
     * 设置操作是否成功。
     *
     * <p>默认为 {@code true}；在业务异常时调用 {@code setSuccess(false)}。</p>
     *
     * @param success {@code true} 表示成功，{@code false} 表示失败
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * 关闭计时器并记录操作耗时。
     *
     * <p>自动计算从创建到 close 的纳秒时长，回调 {@link PerformanceMonitor#recordOperation}。</p>
     */
    @Override
    public void close() {
        long duration = System.nanoTime() - startNanos;
        monitor.recordOperation(operation, duration, success);
    }
}
