package com.syy.taskflowinsight.tracking.cache;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareStrategy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StrategyCacheTests {

    private static class DummyStrategy implements CompareStrategy<Object> {
        @Override
        public CompareResult compare(Object obj1, Object obj2, CompareOptions options) {
            return CompareResult.identical();
        }
        @Override
        public String getName() { return "DUMMY"; }
        @Override
        public boolean supports(Class<?> type) { return true; }
    }

    @Test
    void enabledCache_shouldHitOnSecondLookup() {
        StrategyCache cache = new StrategyCache(true, 100, 60_000);
        AtomicInteger resolveCalls = new AtomicInteger();

        CompareStrategy<?> s1 = cache.getOrResolve(String.class, t -> { resolveCalls.incrementAndGet(); return new DummyStrategy(); });
        CompareStrategy<?> s2 = cache.getOrResolve(String.class, t -> { resolveCalls.incrementAndGet(); return new DummyStrategy(); });

        assertNotNull(s1);
        assertNotNull(s2);
        assertEquals(1, resolveCalls.get(), "Should resolve only once when cache enabled");
        assertTrue(cache.getHitRate() >= 0.0);
    }

    @Test
    void disabledCache_shouldBypass() {
        StrategyCache cache = new StrategyCache(false, 100, 60_000);
        AtomicInteger resolveCalls = new AtomicInteger();

        cache.getOrResolve(Integer.class, t -> { resolveCalls.incrementAndGet(); return new DummyStrategy(); });
        cache.getOrResolve(Integer.class, t -> { resolveCalls.incrementAndGet(); return new DummyStrategy(); });

        assertEquals(2, resolveCalls.get(), "Disabled cache should not cache results");
        assertEquals(-1.0, cache.getHitRate(), 0.0001);
    }
}

