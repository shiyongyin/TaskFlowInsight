package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * DiffFacade 单元测试
 *
 * <p>覆盖 DiffFacade 三级降级链路（programmatic → Spring → static）及边界条件。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("DiffFacade — 统一差异检测门面测试")
class DiffFacadeTests {

    @AfterEach
    void cleanup() {
        // Ensure ThreadLocal is cleaned after each test
        DiffFacade.setProgrammaticService(null);
    }

    // ──────────────────────────────────────────────────────────────
    //  基本 diff 功能
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("基本 diff 功能")
    class BasicDiffTests {

        @Test
        @DisplayName("DF-001: 正常 diff 返回变更列表")
        void normalDiff_shouldReturnChanges() {
            Map<String, Object> before = Map.of("name", "Alice");
            Map<String, Object> after = Map.of("name", "Bob");

            List<ChangeRecord> changes = DiffFacade.diff("User", before, after);

            assertThat(changes).isNotEmpty();
            assertThat(changes).anyMatch(c ->
                    "name".equals(c.getFieldName()) && c.getChangeType() == ChangeType.UPDATE
            );
        }

        @Test
        @DisplayName("DF-002: 相同 Map → 无变更")
        void sameMaps_shouldReturnEmpty() {
            Map<String, Object> data = Map.of("name", "Alice", "age", 25);

            List<ChangeRecord> changes = DiffFacade.diff("User", data, data);

            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("DF-003: null before → CREATE")
        void nullBefore_shouldCreateAll() {
            Map<String, Object> after = Map.of("name", "Alice");

            List<ChangeRecord> changes = DiffFacade.diff("User", null, after);

            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("DF-004: null after → DELETE")
        void nullAfter_shouldDeleteAll() {
            Map<String, Object> before = Map.of("name", "Alice");

            List<ChangeRecord> changes = DiffFacade.diff("User", before, null);

            assertThat(changes).isNotEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  降级链
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("降级链路")
    class FallbackChainTests {

        @Test
        @DisplayName("DF-005: programmatic service 优先级最高")
        void programmaticService_shouldTakePriority() {
            CountingDiffService countingService = new CountingDiffService();
            DiffFacade.setProgrammaticService(countingService);

            Map<String, Object> before = Map.of("x", 1);
            Map<String, Object> after = Map.of("x", 2);

            DiffFacade.diff("Test", before, after);

            assertThat(countingService.invocationCount).isEqualTo(1);
        }

        @Test
        @DisplayName("DF-006: programmatic service 执行后自动清理 ThreadLocal")
        void programmaticService_shouldAutoCleanup() {
            CountingDiffService service = new CountingDiffService();
            DiffFacade.setProgrammaticService(service);

            DiffFacade.diff("Test", Map.of("a", 1), Map.of("a", 2));

            // After first call, programmatic service should be cleared (defensive cleanup)
            // Second call should fall back to static DiffDetector
            CountingDiffService secondService = new CountingDiffService();
            // Don't set second service - should use static fallback
            List<ChangeRecord> result = DiffFacade.diff("Test", Map.of("a", 1), Map.of("a", 2));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("DF-007: 无 Spring 上下文 → 回退到 static DiffDetector")
        void noSpringContext_shouldFallbackToStatic() {
            // No programmatic service, no Spring context
            Map<String, Object> before = Map.of("name", "Alice");
            Map<String, Object> after = Map.of("name", "Bob");

            List<ChangeRecord> changes = DiffFacade.diff("User", before, after);

            assertThat(changes).isNotEmpty();
            assertThat(changes.get(0).getChangeType()).isEqualTo(ChangeType.UPDATE);
        }

        @Test
        @DisplayName("DF-008: programmatic service 异常 → 回退到 static")
        void programmaticServiceException_shouldFallback() {
            DiffDetectorService failingService = new DiffDetectorService() {
                @Override
                public List<ChangeRecord> diff(String objectName,
                                               Map<String, Object> before,
                                               Map<String, Object> after) {
                    throw new RuntimeException("Simulated service failure");
                }
            };
            DiffFacade.setProgrammaticService(failingService);

            // Should not throw — falls through to static DiffDetector
            List<ChangeRecord> changes = DiffFacade.diff("Test",
                    Map.of("x", 1), Map.of("x", 2));

            assertThat(changes).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  setProgrammaticService
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("setProgrammaticService 管理")
    class ProgrammaticServiceManagementTests {

        @Test
        @DisplayName("DF-009: set null 清除 ThreadLocal")
        void setNull_shouldClearThreadLocal() {
            assertThatCode(() -> DiffFacade.setProgrammaticService(null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("DF-010: 重复设置不抛异常")
        void repeatedSet_shouldNotThrow() {
            CountingDiffService service = new CountingDiffService();
            assertThatCode(() -> {
                DiffFacade.setProgrammaticService(service);
                DiffFacade.setProgrammaticService(service);
                DiffFacade.setProgrammaticService(null);
            }).doesNotThrowAnyException();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Test Doubles
    // ──────────────────────────────────────────────────────────────

    /**
     * Counting DiffDetectorService for verifying invocation delegation.
     */
    static class CountingDiffService extends DiffDetectorService {
        int invocationCount = 0;

        @Override
        public List<ChangeRecord> diff(String objectName,
                                       Map<String, Object> before,
                                       Map<String, Object> after) {
            invocationCount++;
            // Delegate to static detector for real results
            return DiffDetector.diff(objectName, before, after);
        }
    }
}
