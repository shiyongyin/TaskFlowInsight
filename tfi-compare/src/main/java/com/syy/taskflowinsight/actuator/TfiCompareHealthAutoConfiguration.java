package com.syy.taskflowinsight.actuator;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the TFI Compare {@link HealthIndicator}.
 *
 * <p>Activates only when:
 * <ol>
 *   <li>Spring Boot Actuator is on the classpath ({@link HealthIndicator})</li>
 *   <li>{@code tfi.change-tracking.enabled=true} (matches by default)</li>
 * </ol>
 *
 * <p>Exposes health status at {@code /actuator/health/tfiCompare}.
 *
 * @author TFI Team
 * @since 3.0.0
 */
@AutoConfiguration
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "tfi.change-tracking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TfiCompareHealthAutoConfiguration {

    /**
     * Registers a {@link TfiCompareHealthIndicator} bean exposing {@code /actuator/health/tfiCompare}.
     *
     * @return the health indicator instance
     */
    @Bean
    public TfiCompareHealthIndicator tfiCompareHealthIndicator() {
        return new TfiCompareHealthIndicator();
    }
}
