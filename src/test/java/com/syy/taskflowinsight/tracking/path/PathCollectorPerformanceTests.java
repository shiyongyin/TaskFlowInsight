package com.syy.taskflowinsight.tracking.path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class PathCollectorPerformanceTests {

    @Test
    @DisplayName("路径收集延迟应 < 100ms；内存增长受控")
    void latencyAndMemoryFootprint() {
        PathDeduplicationConfig config = new PathDeduplicationConfig();
        config.setMaxCollectionDepth(5);
        config.setCacheEnabled(true);
        PathCollector collector = new PathCollector(config);

        // 构造中等复杂对象
        Map<String,Object> root = new HashMap<>();
        Map<String,Object> profile = new HashMap<>();
        profile.put("firstName", "Alice");
        profile.put("lastName", "Doe");
        List<Map<String,Object>> addresses = new ArrayList<>();
        for (int i=0;i<20;i++) {
            Map<String,Object> addr = new HashMap<>();
            addr.put("city", "City"+i);
            addr.put("zip", "1000"+i);
            addresses.add(addr);
        }
        Map<String,String> tags = new HashMap<>();
        for (int i=0;i<50;i++) tags.put("k"+i, "v"+i);
        root.put("profile", profile);
        root.put("addresses", addresses);
        root.put("tags", tags);

        Object target = profile;

        // 预热
        collector.collectPathsForObject(target, "user.profile", root);

        long memBefore = usedMem();
        long start = System.nanoTime();
        List<PathArbiter.PathCandidate> paths = collector.collectPathsForObject(target, "user.profile", root);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        long memAfter = usedMem();

        assertThat(paths).isNotEmpty();
        assertThat(elapsedMs).isLessThan(100L);
        // 内存增长粗略控制在 10MB 内（测试环境友好阈值）
        long memGrowthMb = Math.max(0, (memAfter - memBefore)) / (1024*1024);
        assertThat(memGrowthMb).isLessThan(10);
    }

    private long usedMem() {
        System.gc();
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}

