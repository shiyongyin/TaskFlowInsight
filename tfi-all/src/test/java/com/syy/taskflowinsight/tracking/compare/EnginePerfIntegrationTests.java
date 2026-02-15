package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.cache.ReflectionMetaCache;
import com.syy.taskflowinsight.tracking.cache.StrategyCache;
import com.syy.taskflowinsight.tracking.compare.list.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EnginePerfIntegrationTests {

    private CompareService service;
    private ListCompareExecutor executor;
    private StrategyCache strategyCache;
    private ReflectionMetaCache reflectionCache;

    @BeforeEach
    void setUp() {
        List<ListCompareStrategy> strategies = Arrays.asList(
            new SimpleListStrategy(), new AsSetListStrategy(), new LcsListStrategy(),
            new LevenshteinListStrategy(), new EntityListStrategy()
        );
        executor = new ListCompareExecutor(strategies);

        // 注入自动路由配置（使 LCS 在 detectMoves=true 且 <300 时启用）
        try {
            Field f = ListCompareExecutor.class.getDeclaredField("autoRouteProps");
            f.setAccessible(true);
            CompareRoutingProperties props = new CompareRoutingProperties();
            props.getLcs().setEnabled(true);
            props.getLcs().setPreferLcsWhenDetectMoves(true);
            f.set(executor, props);
        } catch (Exception ignored) {}

        strategyCache = new StrategyCache(true, 10_000, 300_000);
        reflectionCache = new ReflectionMetaCache(true, 10_000, 300_000);
        try {
            com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils.setReflectionMetaCache(reflectionCache);
            com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep.setReflectionMetaCache(reflectionCache);
            com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep.setReflectionMetaCache(reflectionCache);
            com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot.setReflectionMetaCache(reflectionCache);
        } catch (Throwable ignored) {}

        service = new CompareService(
            executor,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(strategyCache)
        );
    }

    @Test
    void smallList_usesLcs_whenDetectMoves() {
        List<Integer> a = generateList(200);
        List<Integer> b = new ArrayList<>(a);
        // 制造少量移动
        b.add(0, b.remove(b.size() - 1));

        CompareResult r = service.compare(a, b, CompareOptions.builder().detectMoves(true).build());
        assertEquals("LCS", r.getAlgorithmUsed(), "<300 且 detectMoves=true 应使用 LCS");
    }

    @Test
    void largeList_degradesToSimple() {
        List<Integer> a = generateList(500);
        List<Integer> b = new ArrayList<>(a);
        Collections.reverse(b);

        CompareResult r = service.compare(a, b, CompareOptions.builder().detectMoves(true).build());
        assertEquals("SIMPLE", r.getAlgorithmUsed(), ">=300 应回落 SIMPLE（自动路由未选择 LCS）");
    }

    @Test
    void strategyCache_hitRateImproves_afterWarmup() {
        Map<String, Integer> m1 = new LinkedHashMap<>();
        Map<String, Integer> m2 = new LinkedHashMap<>();
        for (int i = 0; i < 200; i++) {
            m1.put("k" + i, i);
            m2.put("k" + i, i);
        }
        // 冷启动一次
        service.compare(m1, m2, CompareOptions.DEFAULT);
        // 加热多次
        for (int i = 0; i < 20; i++) {
            service.compare(m1, m2, CompareOptions.DEFAULT);
        }
        assertTrue(strategyCache.getRequestCount() > 0, "应产生缓存请求统计");
        assertTrue(strategyCache.getHitRate() > 0.20, "命中率应显著提升到>20%");
    }

    private static List<Integer> generateList(int size) {
        List<Integer> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) list.add(i);
        return list;
    }
}

