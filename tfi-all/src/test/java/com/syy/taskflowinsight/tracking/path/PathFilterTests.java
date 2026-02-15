package com.syy.taskflowinsight.tracking.path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PathFilterTests {

    @Test
    @DisplayName("包含与排除规则应正确生效")
    void includeExcludeRules() {
        PathDeduplicationConfig cfg = new PathDeduplicationConfig();
        cfg.setIncludePatterns(List.of("user.*", "order[*]"));
        cfg.setExcludePatterns(List.of("user.secret*", "user.password"));

        assertThat(cfg.isPathAllowed("user.name")).isTrue();
        assertThat(cfg.isPathAllowed("user.profile.email")).isTrue();
        assertThat(cfg.isPathAllowed("user.password")).isFalse();
        assertThat(cfg.isPathAllowed("user.secretToken")).isFalse();
        assertThat(cfg.isPathAllowed("order[\"id\"]")).isTrue();
        assertThat(cfg.isPathAllowed("product.sku")).isFalse();
    }
}
