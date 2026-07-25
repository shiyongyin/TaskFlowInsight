package com.syy.tfi.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** RateSampler 的额度是实例全局总量，不按高基数 name 建桶。 */
class DefaultSamplerContractTest {

    @Test
    void kvSp01And02FixedWindowQuotaAdvancesButNeverResetsOnRollback() {
        MutableClock clock = new MutableClock(1_000L);
        Sampler sampler = Sampler.rateLimited(2, clock);

        assertThat(sampler.shouldRecord("first")).isTrue();
        assertThat(sampler.shouldRecord("second-name")).isTrue();
        assertThat(sampler.shouldRecord("third")).isFalse();
        clock.wallTime = 2_000L;
        assertThat(sampler.shouldRecord("next-window")).isTrue();
        clock.wallTime = 1_000L;
        assertThat(sampler.shouldRecord("rollback-uses-current-window")).isTrue();
        assertThat(sampler.shouldRecord("rollback-does-not-reset")).isFalse();
    }

    @Test
    void concurrentCallsCannotExceedTheSharedWindowQuota() throws Exception {
        MutableClock clock = new MutableClock(1_000L);
        Sampler sampler = Sampler.rateLimited(10, clock);
        List<Callable<Boolean>> calls = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            calls.add(() -> sampler.shouldRecord("same-process"));
        }

        try (var executor = Executors.newFixedThreadPool(8)) {
            long accepted = executor.invokeAll(calls).stream().filter(future -> result(future)).count();
            assertThat(accepted).isEqualTo(10L);
        }
    }

    private static boolean result(Future<Boolean> future) {
        try {
            return future.get();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static final class MutableClock implements KernelClock {
        private volatile long wallTime;

        private MutableClock(long wallTime) {
            this.wallTime = wallTime;
        }

        @Override
        public long wallTimeMillis() {
            return wallTime;
        }

        @Override
        public long monotonicNanos() {
            return 0L;
        }
    }
}
