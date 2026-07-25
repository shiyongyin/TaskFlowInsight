package com.syy.taskflowinsight.spi.fixtures;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在构造边界可控阻塞的 Provider，用于证明扫描在生命周期锁外且 publication 受 epoch fencing。
 *
 * @since 4.0.0
 */
public final class BlockingFlowProvider implements ProviderCapacityTestTypes.RaceA,
        ProviderCapacityTestTypes.RaceB {

    private static final AtomicInteger sequence = new AtomicInteger();
    private static final ArrayBlockingQueue<Integer> constructionStarts =
        new ArrayBlockingQueue<>(8);
    private static final Semaphore constructionPermits = new Semaphore(0);
    private static volatile BlockingFlowProvider firstInstance;

    private final int instanceId;

    /** ServiceLoader 使用的公共无参构造器。 */
    public BlockingFlowProvider() {
        instanceId = sequence.incrementAndGet();
        synchronized (BlockingFlowProvider.class) {
            if (firstInstance == null) {
                firstInstance = this;
            }
        }
        if (!constructionStarts.offer(instanceId)) {
            throw new IllegalStateException("Provider construction queue capacity exceeded");
        }
        try {
            if (!constructionPermits.tryAcquire(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release Provider construction");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to release Provider construction",
                interrupted);
        }
    }

    /** 在没有活动构造时恢复初始 fixture 状态。 */
    public static void reset() {
        sequence.set(0);
        constructionStarts.clear();
        constructionPermits.drainPermits();
        firstInstance = null;
    }

    /**
     * 等待下一次构造进入阻塞点。
     *
     * @param timeout 最长等待时间
     * @return 新实例的递增 ID
     */
    public static int awaitConstructionStarted(Duration timeout) {
        try {
            Integer instance = constructionStarts.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (instance == null) {
                throw new IllegalStateException("Timed out waiting for Provider construction");
            }
            return instance;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Provider construction",
                interrupted);
        }
    }

    /** 允许一个已进入阻塞点的构造器继续。 */
    public static void allowNextConstruction() {
        constructionPermits.release();
    }

    /** @return 本轮首次构造的实例；尚未构造时为 null */
    public static BlockingFlowProvider firstInstance() {
        return firstInstance;
    }

    /** @return 本实例的递增构造 ID */
    public int instanceId() {
        return instanceId;
    }

    @Override
    public String startSession(String name) {
        return "blocking-" + instanceId;
    }

    @Override
    public void endSession() {
    }

    @Override
    public TaskNode startTask(String name) {
        return null;
    }

    @Override
    public void endTask() {
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
    }

    @Override
    public int priority() {
        return 1_000;
    }
}
