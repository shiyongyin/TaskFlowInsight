package com.syy.tfi.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** 默认 ULID 以逻辑毫秒和大端 randomness 保持单实例严格单调。 */
class DefaultIdGeneratorContractTest {

    @Test
    void publicFactoryCreatesFormattedIdsAndRejectsNullClock() {
        IdGenerator generator = IdGenerator.ulid(KernelClock.system());

        assertThat(generator.nextId()).matches("[0-9A-HJKMNP-TV-Z]{26}");
        assertThatThrownBy(() -> IdGenerator.ulid(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void kvId01OneThousandIdsInOneMillisecondAreUniqueAndStrictlyIncreasing() {
        MutableClock clock = new MutableClock(1_000L);
        IdGenerator generator = new UlidGenerator(clock, new FixedEntropy(0L));
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) {
            ids.add(generator.nextId());
        }

        assertThat(ids).allMatch(id -> id.matches("[0-9A-HJKMNP-TV-Z]{26}"));
        assertThat(new HashSet<>(ids)).hasSize(1_000);
        assertThat(ids).isSorted();
        for (int index = 1; index < ids.size(); index++) {
            assertThat(ids.get(index)).isGreaterThan(ids.get(index - 1));
        }
    }

    @Test
    void kvId02ClockRollbackAndRandomOverflowNeverMoveIdsBackward() {
        MutableClock clock = new MutableClock(1_000L);
        IdGenerator generator = new UlidGenerator(clock, new FixedEntropy(-1L));
        String first = generator.nextId();
        clock.wallTime = 999L;
        String overflowAdvanced = generator.nextId();
        clock.wallTime = 998L;
        String afterRollback = generator.nextId();

        assertThat(overflowAdvanced).isGreaterThan(first);
        assertThat(afterRollback).isGreaterThan(overflowAdvanced);
        assertThat(afterRollback.substring(0, 10)).isGreaterThanOrEqualTo(first.substring(0, 10));
    }

    @Test
    void concurrentGenerationRemainsUnique() throws Exception {
        MutableClock clock = new MutableClock(1_000L);
        IdGenerator generator = new UlidGenerator(clock, new FixedEntropy(0L));
        Set<String> ids = ConcurrentHashMap.newKeySet();
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Void>> calls = new ArrayList<>();
            for (int index = 0; index < 2_000; index++) {
                calls.add(() -> {
                    ids.add(generator.nextId());
                    return null;
                });
            }
            for (var result : executor.invokeAll(calls)) {
                result.get();
            }
        }
        assertThat(ids).hasSize(2_000);
    }

    @Test
    void wallTimeMustFitTheUlidFortyEightBitRange() {
        MutableClock clock = new MutableClock(-1L);
        IdGenerator generator = new UlidGenerator(clock, new FixedEntropy(0L));
        assertThatThrownBy(generator::nextId).isInstanceOf(IllegalStateException.class);
        clock.wallTime = 1L << 48;
        assertThatThrownBy(generator::nextId).isInstanceOf(IllegalStateException.class);
    }

    private static final class MutableClock implements KernelClock {
        private volatile long wallTime;

        private MutableClock(long wallTime) {
            this.wallTime = wallTime;
        }

        @Override public long wallTimeMillis() { return wallTime; }
        @Override public long monotonicNanos() { return 0L; }
    }

    private record FixedEntropy(long value) implements RandomGenerator {
        @Override public long nextLong() { return value; }
    }
}
