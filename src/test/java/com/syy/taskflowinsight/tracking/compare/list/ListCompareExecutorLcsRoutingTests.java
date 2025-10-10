package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ListCompareExecutor 路由到 LCS 的切片测试
 * 通过反射注入 CompareRoutingProperties，验证小规模 + detectMoves 路径自动路由到 LCS。
 */
class ListCompareExecutorLcsRoutingTests {

    private static void injectAutoRouteProps(ListCompareExecutor exec, boolean lcsEnabled, boolean preferWhenDetectMoves) {
        try {
            CompareRoutingProperties props = new CompareRoutingProperties();
            CompareRoutingProperties.LcsConfig lcs = new CompareRoutingProperties.LcsConfig();
            lcs.setEnabled(lcsEnabled);
            lcs.setPreferLcsWhenDetectMoves(preferWhenDetectMoves);
            props.setLcs(lcs);

            Field f = ListCompareExecutor.class.getDeclaredField("autoRouteProps");
            f.setAccessible(true);
            f.set(exec, props);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void routesToLcs_whenDetectMovesAndSmallSize() {
        // Given: 注册 SIMPLE + LCS 两个策略
        ListCompareExecutor exec = new ListCompareExecutor(Arrays.asList(
            new SimpleListStrategy(),
            new LcsListStrategy()
        ));
        injectAutoRouteProps(exec, true, true);

        List<Integer> a = Arrays.asList(1, 2, 3);
        List<Integer> b = Arrays.asList(2, 3, 4);
        CompareOptions opts = CompareOptions.builder().detectMoves(true).build();

        // When
        CompareResult r = exec.compare(a, b, opts);

        // Then: 应由 LCS 策略处理并标记 algorithmUsed=LCS
        assertNotNull(r);
        assertEquals("LCS", r.getAlgorithmUsed());
        assertFalse(r.isIdentical());
    }

    @Test
    void fallsBackToSimple_whenSizeExceedsLcsThreshold() {
        // Given
        ListCompareExecutor exec = new ListCompareExecutor(Arrays.asList(
            new SimpleListStrategy(),
            new LcsListStrategy()
        ));
        injectAutoRouteProps(exec, true, true);

        List<Integer> a = java.util.stream.IntStream.range(0, 400).boxed().toList();
        List<Integer> b = java.util.stream.IntStream.range(1, 401).boxed().toList();
        CompareOptions opts = CompareOptions.builder().detectMoves(true).build();

        // When
        CompareResult r = exec.compare(a, b, opts);

        // Then: 超过 300 时应不路由 LCS，默认 SIMPLE；algorithmUsed=SIMPLE
        assertNotNull(r);
        assertEquals("SIMPLE", r.getAlgorithmUsed());
    }
}

