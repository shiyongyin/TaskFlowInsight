package com.syy.tfi.kernel.spi;

import java.util.Objects;

/**
 * 在 Session 创建前决定是否承担记录成本，每个 Session 只调用一次。
 */
@FunctionalInterface
public interface Sampler {

    /** 返回本次业务流是否需要记录。 */
    boolean shouldRecord(String name);

    /** 返回始终记录的无状态单例。 */
    static Sampler always() {
        return AlwaysSampler.INSTANCE;
    }

    /** 返回进程级固定窗口限速采样器。 */
    static Sampler rateLimited(int permitsPerSecond, KernelClock clock) {
        return new RateSampler(permitsPerSecond, Objects.requireNonNull(clock, "clock"));
    }
}

/** 默认采样器复用无状态单例，避免每次配置产生没有意义的对象。 */
enum AlwaysSampler implements Sampler {
    /** 对每个候选 Session 都返回 true 的无状态采样器单例。 */
    INSTANCE;

    @Override
    public boolean shouldRecord(String name) {
        return true;
    }
}

/** 固定墙钟窗口换取零后台线程；它只限制新 Session 数量，不参与运行中 Session 生命周期。 */
final class RateSampler implements Sampler {
    /** 每个一秒墙钟窗口允许创建的 Session 数，构造时保证大于 0。 */
    private final int permitsPerSecond;
    /** 用于计算 epoch-second 固定窗口编号的非空墙钟来源。 */
    private final KernelClock clock;
    /** 已观察到的最大 epoch-second 窗口编号；初值表示尚未采样，墙钟回拨时不后退。 */
    private long latestWindow = Long.MIN_VALUE;
    /** 当前窗口已经消耗的许可数，范围为 0..permitsPerSecond，新窗口开始时归零。 */
    private int usedPermits;

    RateSampler(int permitsPerSecond, KernelClock clock) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be positive");
        }
        this.permitsPerSecond = permitsPerSecond;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized boolean shouldRecord(String name) {
        long observedWindow = Math.floorDiv(clock.wallTimeMillis(), 1_000L);
        if (observedWindow > latestWindow) {
            latestWindow = observedWindow;
            usedPermits = 0;
        }
        if (usedPermits >= permitsPerSecond) {
            return false;
        }
        usedPermits++;
        return true;
    }
}
