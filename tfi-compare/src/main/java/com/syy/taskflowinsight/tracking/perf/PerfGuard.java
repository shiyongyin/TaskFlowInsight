package com.syy.taskflowinsight.tracking.perf;

import java.util.ArrayList;
import java.util.List;

public final class PerfGuard {

    public PerfDecision checkBudget(long startMs, PerfOptions opts, boolean strictMode) {
        long elapsed = System.currentTimeMillis() - startMs;
        if (elapsed <= opts.timeBudgetMs()) return PerfDecision.ok();
        if (strictMode) return PerfDecision.degraded("TIME_BUDGET_EXCEEDED", true);
        return PerfDecision.degraded("TIME_BUDGET_EXCEEDED", false);
    }

    public boolean shouldDegradeList(int size, PerfOptions opts) {
        return size > opts.maxListSize();
    }

    public boolean lazySnapshot(PerfOptions opts) { return opts.lazySnapshot(); }

    public record PerfOptions(long timeBudgetMs, int maxListSize, boolean lazySnapshot) {
        public static PerfOptions defaults() { return new PerfOptions(5000, 1000, true); }
    }

    public static final class PerfDecision {
        public final boolean ok;
        public final boolean partial; // 是否仅返回部分结果
        public final List<String> reasons = new ArrayList<>();

        private PerfDecision(boolean ok, boolean partial) { this.ok = ok; this.partial = partial; }
        public static PerfDecision ok() { return new PerfDecision(true, false); }
        public static PerfDecision degraded(String reason, boolean partial) {
            PerfDecision d = new PerfDecision(false, partial); d.reasons.add(reason); return d;
        }
        public PerfDecision add(String reason) { this.reasons.add(reason); return this; }
    }
}
