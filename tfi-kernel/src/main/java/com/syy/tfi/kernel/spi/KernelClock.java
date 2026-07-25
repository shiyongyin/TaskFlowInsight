package com.syy.tfi.kernel.spi;

/**
 * 同时提供墙钟和单调时钟，避免用可回拨时间计算持续时长。
 */
public interface KernelClock {

    /** 返回用于跨系统对齐的 epoch 毫秒。 */
    long wallTimeMillis();

    /** 返回只用于计算持续时长的单调纳秒。 */
    long monotonicNanos();

    /** 返回 JDK 系统时钟实现。 */
    static KernelClock system() {
        return SystemKernelClock.INSTANCE;
    }
}

/** 分离墙钟与单调时钟，防止系统校时把持续时间计算成负数。 */
final class SystemKernelClock implements KernelClock {
    /** 复用 JDK 墙钟与单调时钟的无状态系统时钟单例。 */
    static final SystemKernelClock INSTANCE = new SystemKernelClock();

    private SystemKernelClock() {
    }

    @Override
    public long wallTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public long monotonicNanos() {
        return System.nanoTime();
    }
}
