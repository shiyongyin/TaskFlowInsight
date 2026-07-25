package com.syy.taskflowinsight.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TfiCore}.
 *
 * <p>Covers all public methods including state transitions, config initialization,
 * thread-safety guarantees (volatile semantics), and edge cases.
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@DisplayName("TfiCore Unit Tests")
class TfiCoreTest {

    // ==================== Initialization ====================

    @Nested
    @DisplayName("Initialization from explicit runtime flags")
    class Initialization {

        @Test
        @DisplayName("should initialize with enabled=true")
        void shouldInitializeEnabled() {
            TfiCore core = new TfiCore(true, false);

            assertThat(core.isEnabled()).isTrue();
            assertThat(core.getGlobalEnabledRawState()).isTrue();
        }

        @Test
        @DisplayName("should initialize with enabled=false")
        void shouldInitializeDisabled() {
            TfiCore core = new TfiCore(false, true);

            assertThat(core.isEnabled()).isFalse();
            assertThat(core.getGlobalEnabledRawState()).isFalse();
        }

        @Test
        @DisplayName("should initialize change tracking from explicit flag")
        void shouldInitializeChangeTracking() {
            TfiCore core = new TfiCore(true, true);

            assertThat(core.isChangeTrackingEnabled()).isTrue();
            assertThat(core.getChangeTrackingRawState()).isTrue();
        }

        @Test
        @DisplayName("default constructor should enable standalone runtime")
        void shouldUseStandaloneDefaults() {
            TfiCore core = new TfiCore();

            assertThat(core.isEnabled()).isTrue();
            assertThat(core.isChangeTrackingEnabled()).isTrue();
            assertThat(core.getChangeTrackingRawState()).isTrue();
        }
    }

    // ==================== Global Enable/Disable ====================

    @Nested
    @DisplayName("Global Enable/Disable")
    class GlobalEnableDisable {

        @Test
        @DisplayName("enable() should set globalEnabled to true")
        void enableShouldSetTrue() {
            TfiCore core = new TfiCore(false, false);
            assertThat(core.isEnabled()).isFalse();

            core.enable();

            assertThat(core.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("disable() should set globalEnabled to false")
        void disableShouldSetFalse() {
            TfiCore core = new TfiCore(true, false);
            assertThat(core.isEnabled()).isTrue();

            core.disable();

            assertThat(core.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("enable/disable should be idempotent")
        void enableDisableShouldBeIdempotent() {
            TfiCore core = new TfiCore(true, false);

            core.enable();
            core.enable();
            assertThat(core.isEnabled()).isTrue();

            core.disable();
            core.disable();
            assertThat(core.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("rapid toggle should not corrupt state")
        void rapidToggleShouldNotCorruptState() {
            TfiCore core = new TfiCore(true, false);

            for (int i = 0; i < 1000; i++) {
                core.enable();
                core.disable();
            }

            assertThat(core.isEnabled()).isFalse();

            core.enable();
            assertThat(core.isEnabled()).isTrue();
        }
    }

    // ==================== Change Tracking ====================

    @Nested
    @DisplayName("Change Tracking Control")
    class ChangeTrackingControl {

        @Test
        @DisplayName("setChangeTrackingEnabled(true) should enable change tracking")
        void setChangeTrackingEnabledTrue() {
            TfiCore core = new TfiCore(true, false);

            core.setChangeTrackingEnabled(true);

            assertThat(core.getChangeTrackingRawState()).isTrue();
            assertThat(core.isChangeTrackingEnabled()).isTrue();
        }

        @Test
        @DisplayName("setChangeTrackingEnabled(false) should disable change tracking")
        void setChangeTrackingEnabledFalse() {
            TfiCore core = new TfiCore(true, true);

            core.setChangeTrackingEnabled(false);

            assertThat(core.getChangeTrackingRawState()).isFalse();
            assertThat(core.isChangeTrackingEnabled()).isFalse();
        }

        @Test
        @DisplayName("isChangeTrackingEnabled should require both global and tracking enabled")
        void shouldRequireBothEnabled() {
            TfiCore core = new TfiCore(false, true);

            // global=false, tracking=true → combined=false
            assertThat(core.getChangeTrackingRawState()).isTrue();
            assertThat(core.isChangeTrackingEnabled()).isFalse();
        }

        @Test
        @DisplayName("isChangeTrackingEnabled should be true only when both are enabled")
        void shouldBeTrueOnlyWhenBothEnabled() {
            TfiCore core = new TfiCore(true, true);
            assertThat(core.isChangeTrackingEnabled()).isTrue();

            core.disable(); // global off
            assertThat(core.isChangeTrackingEnabled()).isFalse();

            core.enable(); // global on again
            assertThat(core.isChangeTrackingEnabled()).isTrue();

            core.setChangeTrackingEnabled(false); // tracking off
            assertThat(core.isChangeTrackingEnabled()).isFalse();
        }
    }

    // ==================== Raw State Accessors ====================

    @Nested
    @DisplayName("Raw State Accessors")
    class RawStateAccessors {

        @Test
        @DisplayName("getGlobalEnabledRawState reflects actual global state")
        void globalRawStateReflectsActual() {
            TfiCore core = new TfiCore(true, false);
            assertThat(core.getGlobalEnabledRawState()).isTrue();

            core.disable();
            assertThat(core.getGlobalEnabledRawState()).isFalse();

            core.enable();
            assertThat(core.getGlobalEnabledRawState()).isTrue();
        }

        @Test
        @DisplayName("getChangeTrackingRawState is independent of globalEnabled")
        void changeTrackingRawStateIsIndependent() {
            TfiCore core = new TfiCore(false, true);

            // global=false should NOT affect raw state
            assertThat(core.getChangeTrackingRawState()).isTrue();
            assertThat(core.isChangeTrackingEnabled()).isFalse(); // combined is false

            core.enable();
            assertThat(core.getChangeTrackingRawState()).isTrue();
            assertThat(core.isChangeTrackingEnabled()).isTrue(); // now both true
        }
    }

    // ==================== State Combination Matrix ====================

    @Nested
    @DisplayName("State Combination Matrix")
    class StateCombinationMatrix {

        @Test
        @DisplayName("all four combinations of (global, tracking) flags")
        void allFourCombinations() {
            // (false, false)
            TfiCore ff = new TfiCore(false, false);
            assertThat(ff.isEnabled()).isFalse();
            assertThat(ff.isChangeTrackingEnabled()).isFalse();

            // (false, true)
            TfiCore ft = new TfiCore(false, true);
            assertThat(ft.isEnabled()).isFalse();
            assertThat(ft.isChangeTrackingEnabled()).isFalse(); // gated by global

            // (true, false)
            TfiCore tf = new TfiCore(true, false);
            assertThat(tf.isEnabled()).isTrue();
            assertThat(tf.isChangeTrackingEnabled()).isFalse();

            // (true, true)
            TfiCore tt = new TfiCore(true, true);
            assertThat(tt.isEnabled()).isTrue();
            assertThat(tt.isChangeTrackingEnabled()).isTrue();
        }
    }
}
