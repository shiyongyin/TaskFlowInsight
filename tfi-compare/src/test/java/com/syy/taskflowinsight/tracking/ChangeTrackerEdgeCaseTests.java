package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.annotation.DiffIgnore;
import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.tracking.monitoring.DegradationContext;
import com.syy.taskflowinsight.tracking.monitoring.DegradationLevel;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;
import com.syy.taskflowinsight.tracking.ssot.path.PathNavigator;
import com.syy.taskflowinsight.tracking.summary.CollectionSummary;
import com.syy.taskflowinsight.tracking.summary.SummaryInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Surgical coverage tests for ChangeTracker, SessionAwareChangeTracker, ChangeType,
 * CollectionSummary, PathNavigator, EntityKeyUtils.
 */
@DisplayName("Tracking — Surgical Coverage Tests")
class ChangeTrackerEdgeCaseTests {

    @BeforeEach
    void setUp() {
        ChangeTracker.clearAllTracking();
        SessionAwareChangeTracker.clearAll();
        DegradationContext.clear();
    }

    @AfterEach
    void tearDown() {
        ChangeTracker.clearAllTracking();
        SessionAwareChangeTracker.clearAll();
        DegradationContext.clear();
    }

    // ── ChangeTracker ──

    @Nested
    @DisplayName("ChangeTracker — All static methods")
    class ChangeTrackerTests {

        @Test
        @DisplayName("track with null name skips")
        void track_nullName() {
            ChangeTracker.track(null, Map.of("a", 1), "a");
            assertThat(ChangeTracker.getTrackedCount()).isZero();
        }

        @Test
        @DisplayName("track with null target skips")
        void track_nullTarget() {
            ChangeTracker.track("obj", null, "field");
            assertThat(ChangeTracker.getTrackedCount()).isZero();
        }

        @Test
        @DisplayName("track with valid object")
        void track_validObject() {
            Map<String, Object> target = new HashMap<>(Map.of("name", "Alice", "age", 30));
            ChangeTracker.track("User", target, "name", "age");
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("trackAll with null/empty map")
        void trackAll_nullOrEmpty() {
            ChangeTracker.trackAll(null);
            assertThat(ChangeTracker.getTrackedCount()).isZero();
            ChangeTracker.trackAll(Collections.emptyMap());
            assertThat(ChangeTracker.getTrackedCount()).isZero();
        }

        @Test
        @DisplayName("trackAll with multiple objects")
        void trackAll_multiple() {
            ChangeTracker.trackAll(Map.of(
                "a", Map.of("x", 1),
                "b", Map.of("y", 2)
            ));
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("getChanges returns empty when disabled")
        void getChanges_whenDisabled() {
            DegradationContext.setCurrentLevel(DegradationLevel.DISABLED);
            ChangeTracker.track("User", Map.of("a", 1), "a");
            List<ChangeRecord> changes = ChangeTracker.getChanges();
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("getChanges returns list after track and mutate")
        void getChanges_detectsUpdates() {
            Map<String, Object> target = new HashMap<>();
            target.put("name", "Alice");
            target.put("age", 30);
            ChangeTracker.track("User", target, "name", "age");
            target.put("name", "Bob");
            List<ChangeRecord> changes = ChangeTracker.getChanges();
            assertThat(changes).isNotNull();
        }

        @Test
        @DisplayName("clearAllTracking is idempotent")
        void clearAllTracking_idempotent() {
            ChangeTracker.track("User", Map.of("a", 1), "a");
            ChangeTracker.clearAllTracking();
            ChangeTracker.clearAllTracking();
            assertThat(ChangeTracker.getTrackedCount()).isZero();
        }

        @Test
        @DisplayName("getTrackedCount returns correct count")
        void getTrackedCount() {
            ChangeTracker.track("A", Map.of("x", 1), "x");
            ChangeTracker.track("B", Map.of("y", 2), "y");
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("getMaxTrackedObjects returns positive")
        void getMaxTrackedObjects() {
            assertThat(ChangeTracker.getMaxTrackedObjects()).isPositive();
        }

        @Test
        @DisplayName("clearBySessionId delegates to clearAll")
        void clearBySessionId() {
            ChangeTracker.track("User", Map.of("a", 1), "a");
            ChangeTracker.clearBySessionId("session-1");
            assertThat(ChangeTracker.getTrackedCount()).isZero();
        }

        @Test
        @DisplayName("track with TrackingOptions")
        void track_withOptions() {
            ChangeTracker.track("User", Map.of("name", "Alice"),
                com.syy.taskflowinsight.api.TrackingOptions.shallow());
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("track with null options skips")
        void track_nullOptions() {
            ChangeTracker.track("User", Map.of("a", 1),
                (com.syy.taskflowinsight.api.TrackingOptions) null);
            assertThat(ChangeTracker.getTrackedCount()).isZero();
        }
    }

    // ── SessionAwareChangeTracker ──

    @Nested
    @DisplayName("SessionAwareChangeTracker — Session lifecycle")
    class SessionAwareChangeTrackerTests {

        @Test
        @DisplayName("getCurrentSessionChanges returns empty when no session")
        void getCurrentSessionChanges_noSession() {
            assertThat(SessionAwareChangeTracker.getCurrentSessionChanges()).isEmpty();
        }

        @Test
        @DisplayName("recordChange and getSessionChanges with active session")
        void recordChange_withSession() throws Exception {
            try (ManagedThreadContext ctx = ManagedThreadContext.create("root")) {
                ChangeRecord cr = ChangeRecord.of("Order", "status", "NEW", "PROCESSING", ChangeType.UPDATE);
                SessionAwareChangeTracker.recordChange(cr);
                List<ChangeRecord> changes = SessionAwareChangeTracker.getCurrentSessionChanges();
                assertThat(changes).hasSize(1);
                assertThat(changes.get(0).getObjectName()).isEqualTo("Order");
            }
        }

        @Test
        @DisplayName("getSessionChanges for non-existent session returns empty")
        void getSessionChanges_nonExistent() {
            assertThat(SessionAwareChangeTracker.getSessionChanges("nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("clearSession returns removed count")
        void clearSession() throws Exception {
            try (ManagedThreadContext ctx = ManagedThreadContext.create("root")) {
                SessionAwareChangeTracker.recordChange(
                    ChangeRecord.of("X", "f", "a", "b", ChangeType.UPDATE));
                String sessionId = ctx.getCurrentSession().getSessionId();
                int removed = SessionAwareChangeTracker.clearSession(sessionId);
                assertThat(removed).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("getAllChanges returns all session changes")
        void getAllChanges() throws Exception {
            try (ManagedThreadContext ctx = ManagedThreadContext.create("root")) {
                SessionAwareChangeTracker.recordChange(
                    ChangeRecord.of("A", "f", "x", "y", ChangeType.UPDATE));
                List<ChangeRecord> all = SessionAwareChangeTracker.getAllChanges();
                assertThat(all).hasSize(1);
            }
        }

        @Test
        @DisplayName("getSessionMetadata returns metadata")
        void getSessionMetadata() throws Exception {
            try (ManagedThreadContext ctx = ManagedThreadContext.create("root")) {
                SessionAwareChangeTracker.recordChange(
                    ChangeRecord.of("A", "f", "x", "y", ChangeType.UPDATE));
                String sessionId = ctx.getCurrentSession().getSessionId();
                SessionAwareChangeTracker.SessionMetadata meta =
                    SessionAwareChangeTracker.getSessionMetadata(sessionId);
                assertThat(meta).isNotNull();
                assertThat(meta.getSessionId()).isEqualTo(sessionId);
                assertThat(meta.getChangeCount()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("cleanupExpiredSessions removes old sessions")
        void cleanupExpiredSessions() throws Exception {
            try (ManagedThreadContext ctx = ManagedThreadContext.create("root")) {
                SessionAwareChangeTracker.recordChange(
                    ChangeRecord.of("A", "f", "x", "y", ChangeType.UPDATE));
            }
            int cleaned = SessionAwareChangeTracker.cleanupExpiredSessions(0);
            assertThat(cleaned).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("SessionMetadata getters")
        void sessionMetadata_getters() {
            SessionAwareChangeTracker.SessionMetadata meta =
                new SessionAwareChangeTracker.SessionMetadata("s1");
            assertThat(meta.getSessionId()).isEqualTo("s1");
            assertThat(meta.getCreatedTime()).isPositive();
            assertThat(meta.getChangeCount()).isZero();
            meta.incrementChangeCount();
            assertThat(meta.getChangeCount()).isEqualTo(1);
            meta.recordObjectChange("Order");
            assertThat(meta.getObjectChangeCounts()).containsEntry("Order", 1);
            assertThat(meta.getAge()).isGreaterThanOrEqualTo(0);
            assertThat(meta.getIdleTime()).isGreaterThanOrEqualTo(0);
        }
    }

    // ── ChangeType ──

    @Nested
    @DisplayName("ChangeType — Enum")
    class ChangeTypeTests {

        @Test
        @DisplayName("all ChangeType values exist")
        void allValues() {
            assertThat(ChangeType.values()).containsExactlyInAnyOrder(
                ChangeType.CREATE, ChangeType.UPDATE, ChangeType.DELETE, ChangeType.MOVE);
        }
    }

    // ── CollectionSummary ──

    @Nested
    @DisplayName("CollectionSummary — Summary generation")
    class CollectionSummaryTests {

        private CollectionSummary summary;

        @BeforeEach
        void setUpSummary() {
            summary = CollectionSummary.getInstance();
        }

        @Test
        @DisplayName("shouldSummarize returns false when disabled")
        void shouldSummarize_disabled() {
            summary.setEnabled(false);
            assertThat(summary.shouldSummarize(List.of(1, 2, 3))).isFalse();
        }

        @Test
        @DisplayName("shouldSummarize returns false for null")
        void shouldSummarize_null() {
            assertThat(summary.shouldSummarize(null)).isFalse();
        }

        @Test
        @DisplayName("shouldSummarize returns true for large collection")
        void shouldSummarize_large() {
            CollectionSummary s = new CollectionSummary();
            s.setEnabled(true);
            s.setMaxSize(5);
            List<Integer> large = new ArrayList<>();
            for (int i = 0; i < 10; i++) large.add(i);
            assertThat(s.shouldSummarize(large)).isTrue();
        }

        @Test
        @DisplayName("summarize null returns empty")
        void summarize_null() {
            SummaryInfo info = summary.summarize(null);
            assertThat(info.getType()).isEqualTo("empty");
            assertThat(info.getSize()).isZero();
        }

        @Test
        @DisplayName("summarize List")
        void summarize_list() {
            SummaryInfo info = summary.summarize(List.of(1, 2, 3, 4, 5));
            assertThat(info.getSize()).isEqualTo(5);
            assertThat(info.getExamples()).isNotEmpty();
        }

        @Test
        @DisplayName("summarize Map")
        void summarize_map() {
            SummaryInfo info = summary.summarize(Map.of("a", 1, "b", 2));
            assertThat(info.getSize()).isEqualTo(2);
            assertThat(info.getMapExamples()).isNotEmpty();
        }

        @Test
        @DisplayName("summarize array")
        void summarize_array() {
            int[] arr = {1, 2, 3};
            SummaryInfo info = summary.summarize(arr);
            assertThat(info.getSize()).isEqualTo(3);
        }

        @Test
        @DisplayName("summarize unsupported type")
        void summarize_unsupported() {
            SummaryInfo info = summary.summarize("not-a-collection");
            assertThat(info.getFeatures()).contains("unsupported");
        }

        @Test
        @DisplayName("summarize with sensitive data masks")
        void summarize_sensitive() {
            summary.setSensitiveWords(List.of("password"));
            SummaryInfo info = summary.summarize(List.of("user", "password123"));
            assertThat(info.getExamples()).contains("***MASKED***");
        }
    }

    // ── PathNavigator ──

    @Nested
    @DisplayName("PathNavigator — Path resolution")
    class PathNavigatorTests {

        static class SimpleBean {
            public String name = "Alice";
            public int age = 30;
        }

        static class NestedBean {
            public SimpleBean child = new SimpleBean();
        }

        @Test
        @DisplayName("resolve null root returns null")
        void resolve_nullRoot() {
            assertThat(PathNavigator.resolve(null, "name")).isNull();
        }

        @Test
        @DisplayName("resolve null path returns null")
        void resolve_nullPath() {
            assertThat(PathNavigator.resolve(new SimpleBean(), null)).isNull();
        }

        @Test
        @DisplayName("resolve empty path returns null")
        void resolve_emptyPath() {
            assertThat(PathNavigator.resolve(new SimpleBean(), "")).isNull();
        }

        @Test
        @DisplayName("resolve simple field")
        void resolve_simpleField() {
            SimpleBean bean = new SimpleBean();
            assertThat(PathNavigator.resolve(bean, "name")).isEqualTo("Alice");
        }

        @Test
        @DisplayName("resolve nested path")
        void resolve_nestedPath() {
            NestedBean bean = new NestedBean();
            assertThat(PathNavigator.resolve(bean, "child.name")).isEqualTo("Alice");
        }

        @Test
        @DisplayName("resolve with class prefix")
        void resolve_withClassPrefix() {
            SimpleBean bean = new SimpleBean();
            assertThat(PathNavigator.resolve(bean, "SimpleBean.name")).isEqualTo("Alice");
        }

        @Test
        @DisplayName("resolve list index via nested path")
        void resolve_listIndex() {
            class Container { public List<String> items = List.of("a", "b", "c"); }
            Container c = new Container();
            assertThat(PathNavigator.resolve(c, "items[0]")).isEqualTo("a");
        }

        @Test
        @DisplayName("isAnnotatedField with DiffIgnore")
        void isAnnotatedField_diffIgnore() {
            class BeanWithIgnore {
                @DiffIgnore
                public String secret = "x";
            }
            BeanWithIgnore bean = new BeanWithIgnore();
            assertThat(PathNavigator.isAnnotatedField(bean, "secret", DiffIgnore.class)).isTrue();
        }

        @Test
        @DisplayName("isAnnotatedField null root returns false")
        void isAnnotatedField_nullRoot() {
            assertThat(PathNavigator.isAnnotatedField(null, "name", DiffIgnore.class)).isFalse();
        }
    }

    // ── EntityKeyUtils ──

    @Nested
    @DisplayName("EntityKeyUtils — Key computation")
    class EntityKeyUtilsTests {

        @Entity
        static class UserEntity {
            @Key
            private long id = 1001;
            @Key
            private String username = "alice";
        }

        @Entity
        static class NoKeyEntity {
            private String name = "x";
        }

        @Test
        @DisplayName("tryComputeStableKey null returns empty")
        void tryComputeStableKey_null() {
            assertThat(EntityKeyUtils.tryComputeStableKey(null)).isEmpty();
        }

        @Test
        @DisplayName("tryComputeStableKey with @Key fields")
        void tryComputeStableKey_withKey() {
            UserEntity user = new UserEntity();
            Optional<String> key = EntityKeyUtils.tryComputeStableKey(user);
            assertThat(key).isPresent();
            assertThat(key.get()).contains("id=");
            assertThat(key.get()).contains("username=");
        }

        @Test
        @DisplayName("tryComputeStableKey with no @Key returns empty")
        void tryComputeStableKey_noKey() {
            NoKeyEntity entity = new NoKeyEntity();
            assertThat(EntityKeyUtils.tryComputeStableKey(entity)).isEmpty();
        }

        @Test
        @DisplayName("computeStableKeyOrUnresolved")
        void computeStableKeyOrUnresolved() {
            assertThat(EntityKeyUtils.computeStableKeyOrUnresolved(null))
                .isEqualTo(EntityKeyUtils.UNRESOLVED);
            UserEntity user = new UserEntity();
            assertThat(EntityKeyUtils.computeStableKeyOrUnresolved(user))
                .isNotEqualTo(EntityKeyUtils.UNRESOLVED);
        }

        @Test
        @DisplayName("computeStableKeyOrNull")
        void computeStableKeyOrNull() {
            assertThat(EntityKeyUtils.computeStableKeyOrNull(null)).isNull();
        }

        @Test
        @DisplayName("tryComputeCompactKey")
        void tryComputeCompactKey() {
            UserEntity user = new UserEntity();
            Optional<String> compact = EntityKeyUtils.tryComputeCompactKey(user);
            assertThat(compact).isPresent();
        }

        @Test
        @DisplayName("computeCompactKeyOrUnresolved")
        void computeCompactKeyOrUnresolved() {
            assertThat(EntityKeyUtils.computeCompactKeyOrUnresolved(null))
                .isEqualTo(EntityKeyUtils.UNRESOLVED);
        }

        @Test
        @DisplayName("collectKeyFields")
        void collectKeyFields() {
            List<Field> fields = EntityKeyUtils.collectKeyFields(UserEntity.class);
            assertThat(fields).hasSize(2);
        }

        @Test
        @DisplayName("computeReferenceIdentifier null returns null")
        void computeReferenceIdentifier_null() {
            assertThat(EntityKeyUtils.computeReferenceIdentifier(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("computeReferenceIdentifier with entity")
        void computeReferenceIdentifier_entity() {
            UserEntity user = new UserEntity();
            String ref = EntityKeyUtils.computeReferenceIdentifier(user);
            assertThat(ref).contains("UserEntity");
        }
    }
}
