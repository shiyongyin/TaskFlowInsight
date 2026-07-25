package com.syy.tfi.kernel.spi;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * 为 Session 生成稳定标识，可由宿主替换为现有 traceId 体系。
 */
@FunctionalInterface
public interface IdGenerator {

    /** 返回下一个 Session 标识。 */
    String nextId();

    /** 返回由指定时钟驱动的单调 ULID 生成器。 */
    static IdGenerator ulid(KernelClock clock) {
        return new UlidGenerator(Objects.requireNonNull(clock, "clock"));
    }
}

/** 在同毫秒和墙钟回拨时推进随机段，保证单实例生成顺序单调且不启动协调线程。 */
final class UlidGenerator implements IdGenerator {
    /** ULID 使用的 Crockford Base32 字母表；下标直接表示 5-bit 值，顺序不可调整。 */
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    /** ULID 48-bit 时间字段可表示的最大 epoch 毫秒值，即 {@code 2^48 - 1}。 */
    private static final long MAX_TIME = (1L << 48) - 1L;

    /** 为 ULID 提供墙钟 epoch 毫秒的非空时间源。 */
    private final KernelClock clock;
    /** 为新逻辑毫秒播种 80-bit 随机段的非空熵源。 */
    private final RandomGenerator entropy;
    /** 当前 10-byte 随机段；同一逻辑毫秒内按无符号大端整数递增。 */
    private final byte[] randomness = new byte[10];
    /** 最近一次 ID 使用的逻辑 epoch 毫秒；-1 表示尚未生成，之后保持单调不减。 */
    private long logicalTime = -1L;

    UlidGenerator(KernelClock clock) {
        this(clock, new SecureRandom());
    }

    UlidGenerator(KernelClock clock, RandomGenerator entropy) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.entropy = Objects.requireNonNull(entropy, "entropy");
    }

    @Override
    public synchronized String nextId() {
        long wallTime = clock.wallTimeMillis();
        requireValidTime(wallTime);
        if (wallTime > logicalTime) {
            logicalTime = wallTime;
            entropy.nextBytes(randomness);
        } else if (!incrementRandomness()) {
            if (logicalTime == MAX_TIME) {
                throw new IllegalStateException("ULID time exceeds 48-bit range");
            }
            logicalTime++;
            entropy.nextBytes(randomness);
        }
        return encode(logicalTime, randomness);
    }

    private static void requireValidTime(long time) {
        if (time < 0L || time > MAX_TIME) {
            throw new IllegalStateException("ULID time must fit in 48 bits");
        }
    }

    private boolean incrementRandomness() {
        for (int index = randomness.length - 1; index >= 0; index--) {
            randomness[index]++;
            if (randomness[index] != 0) {
                return true;
            }
        }
        return false;
    }

    private static String encode(long time, byte[] random) {
        char[] result = new char[26];
        for (int index = 9; index >= 0; index--) {
            result[index] = ALPHABET[(int) (time & 31L)];
            time >>>= 5;
        }
        int accumulator = 0;
        int bits = 0;
        int output = 10;
        for (byte value : random) {
            accumulator = (accumulator << 8) | Byte.toUnsignedInt(value);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                result[output++] = ALPHABET[(accumulator >>> bits) & 31];
            }
        }
        return new String(result);
    }
}
