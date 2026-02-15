package com.syy.taskflowinsight.tracking.perf;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * PerfGuard 性能预算配置（外部化 P2.1）
 * <p>
 * 配置项 (application.yml):
 * <pre>
 * tfi:
 *   perf-guard:
 *     time-budget-ms: 5000      # 时间预算（毫秒），默认 5 秒
 *     max-list-size: 1000       # 最大列表大小，默认 1000
 *     lazy-snapshot: true       # 懒加载快照，默认启用
 *     enabled: true             # 是否启用 PerfGuard，默认启用
 * </pre>
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M1
 * @since 2025-10-04
 */
@Configuration
@ConfigurationProperties(prefix = "tfi.perf-guard")
public class PerfGuardConfig {

    /** 是否启用 PerfGuard 检查（默认 true） */
    private boolean enabled = true;

    /** 时间预算（毫秒），默认 5000ms */
    private long timeBudgetMs = 5000;

    /** 最大列表大小，默认 1000 */
    private int maxListSize = 1000;

    /** 懒加载快照（默认 true） */
    private boolean lazySnapshot = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getTimeBudgetMs() {
        return timeBudgetMs;
    }

    public void setTimeBudgetMs(long timeBudgetMs) {
        this.timeBudgetMs = timeBudgetMs;
    }

    public int getMaxListSize() {
        return maxListSize;
    }

    public void setMaxListSize(int maxListSize) {
        this.maxListSize = maxListSize;
    }

    public boolean isLazySnapshot() {
        return lazySnapshot;
    }

    public void setLazySnapshot(boolean lazySnapshot) {
        this.lazySnapshot = lazySnapshot;
    }

    /**
     * 转换为 PerfGuard.PerfOptions
     */
    public PerfGuard.PerfOptions toPerfOptions() {
        return new PerfGuard.PerfOptions(timeBudgetMs, maxListSize, lazySnapshot);
    }
}
