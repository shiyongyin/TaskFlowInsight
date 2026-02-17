package com.syy.taskflowinsight.util;

import org.junit.jupiter.api.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link DiagnosticLogger} 单元测试
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.0
 */
class DiagnosticLoggerTest {

    @BeforeEach
    void setup() {
        DiagnosticLogger.reset();
    }

    @AfterEach
    void cleanup() {
        DiagnosticLogger.reset();
    }

    @Test
    @DisplayName("once - 同一 code 仅输出一次")
    void onceLogsOnlyOnce() {
        DiagnosticLogger.once("TEST-001", "Test", "reason", "advise");
        DiagnosticLogger.once("TEST-001", "Test", "reason", "advise");

        Map<String, Integer> stats = DiagnosticLogger.getGlobalStatistics();
        // 全局统计计数 2 次调用，但日志只输出 1 次（线程本地去重）
        assertThat(stats).containsKey("TEST-001");
    }

    @Test
    @DisplayName("once - 不同 code 都能输出")
    void onceDifferentCodes() {
        DiagnosticLogger.once("TEST-001", "Test1", "reason1", "advise1");
        DiagnosticLogger.once("TEST-002", "Test2", "reason2", "advise2");

        Map<String, Integer> stats = DiagnosticLogger.getGlobalStatistics();
        assertThat(stats).containsKeys("TEST-001", "TEST-002");
    }

    @Test
    @DisplayName("once - null/空 code 静默忽略")
    void onceNullCodeIgnored() {
        DiagnosticLogger.once(null, "Test", "reason", "advise");
        DiagnosticLogger.once("", "Test", "reason", "advise");

        Map<String, Integer> stats = DiagnosticLogger.getGlobalStatistics();
        assertThat(stats).isEmpty();
    }

    @Test
    @DisplayName("once - null 参数不抛异常")
    void onceNullParamsNoException() {
        assertThatNoException().isThrownBy(() ->
                DiagnosticLogger.once("CODE", null, null, null));
    }

    @Test
    @DisplayName("clearThreadLocal - 清理后可再次输出")
    void clearThreadLocalAllowsReOutput() {
        DiagnosticLogger.once("TEST-001", "Test", "reason", "advise");
        DiagnosticLogger.clearThreadLocal();
        DiagnosticLogger.once("TEST-001", "Test", "reason", "advise");

        Map<String, Integer> stats = DiagnosticLogger.getGlobalStatistics();
        assertThat(stats.get("TEST-001")).isEqualTo(2);
    }

    @Test
    @DisplayName("reset - 清空全局和线程本地")
    void resetClearsAll() {
        DiagnosticLogger.once("TEST-001", "Test", "reason", "advise");
        DiagnosticLogger.reset();

        Map<String, Integer> stats = DiagnosticLogger.getGlobalStatistics();
        assertThat(stats).isEmpty();
    }

    @Test
    @DisplayName("getGlobalStatistics - 返回不可修改 Map")
    void globalStatisticsIsUnmodifiable() {
        DiagnosticLogger.once("TEST-001", "Test", "reason", "advise");
        Map<String, Integer> stats = DiagnosticLogger.getGlobalStatistics();
        assertThatThrownBy(() -> stats.put("hack", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
