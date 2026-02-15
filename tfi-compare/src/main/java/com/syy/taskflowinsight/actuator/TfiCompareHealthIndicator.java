package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.tracking.ChangeTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Spring Boot Actuator {@link HealthIndicator} for the TFI Compare module.
 *
 * <p>Exposes the health of the change-tracking and comparison subsystem under
 * {@code /actuator/health/tfiCompare}. The indicator checks:
 * <ul>
 *   <li>Whether change tracking is functional (can track + clear without error)</li>
 *   <li>Current tracked-object count vs. the configured maximum</li>
 *   <li>Memory pressure heuristic (tracked objects &gt; 80% of max → DEGRADED)</li>
 * </ul>
 *
 * <p>This bean is auto-configured by {@link TfiCompareHealthAutoConfiguration}
 * when Spring Boot Actuator is on the classpath and {@code tfi.change-tracking.enabled=true}.
 *
 * @author TFI Team
 * @since 3.0.0
 */
public class TfiCompareHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(TfiCompareHealthIndicator.class);

    /** Threshold ratio above which the indicator reports DEGRADED status. */
    private static final double PRESSURE_THRESHOLD = 0.80;

    @Override
    public Health health() {
        try {
            // Functional check: verify the tracker is responsive
            int maxTrackedObjects = ChangeTracker.getMaxTrackedObjects();
            int currentCount = ChangeTracker.getTrackedCount();
            double usageRatio = maxTrackedObjects > 0
                    ? (double) currentCount / maxTrackedObjects
                    : 0.0;

            Health.Builder builder;
            if (usageRatio >= 1.0) {
                builder = Health.down()
                        .withDetail("reason", "Tracked objects at capacity — new tracking calls may be rejected");
            } else if (usageRatio >= PRESSURE_THRESHOLD) {
                builder = Health.status("DEGRADED")
                        .withDetail("reason",
                                String.format("Tracked objects at %.0f%% of max — consider calling clearAllTracking()",
                                        usageRatio * 100));
            } else {
                builder = Health.up();
            }

            return builder
                    .withDetail("trackedObjects", currentCount)
                    .withDetail("maxTrackedObjects", maxTrackedObjects)
                    .withDetail("usagePercent", String.format("%.1f%%", usageRatio * 100))
                    .build();

        } catch (Exception e) {
            logger.warn("TFI Compare health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }
}
