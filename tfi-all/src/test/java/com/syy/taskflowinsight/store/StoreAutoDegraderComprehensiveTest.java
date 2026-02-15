package com.syy.taskflowinsight.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StoreAutoDegrader综合测试
 * 专门提升StoreAutoDegrader覆盖率从13%到80%
 * 
 * @author TaskFlow Insight Team
 * @since 2025-01-13
 */
@DisplayName("StoreAutoDegrader综合测试 - 目标覆盖率80%")
class StoreAutoDegraderComprehensiveTest {

    private StoreAutoDegrader degrader;

    @BeforeEach
    void setUp() {
        degrader = new StoreAutoDegrader();
        
        // 设置测试用的阈值
        ReflectionTestUtils.setField(degrader, "minHitRate", 0.2);
        ReflectionTestUtils.setField(degrader, "maxEvictions", 10000L);
        ReflectionTestUtils.setField(degrader, "recoveryHitRate", 0.5);
        ReflectionTestUtils.setField(degrader, "recoveryCount", 5);
    }

    @Nested
    @DisplayName("降级检测测试")
    class DegradationDetectionTests {

        @Test
        @DisplayName("初始状态应该未降级")
        void initialStateShouldNotBeDegraded() {
            assertThat(degrader.isDegraded()).isFalse();
            assertThat(degrader.getDegradationCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("命中率过低应该触发降级")
        void lowHitRateShouldTriggerDegradation() {
            StoreStats stats = StoreStats.builder()
                .hitCount(10)
                .missCount(90)
                .hitRate(0.1) // 低于0.2阈值
                .evictionCount(5000)
                .build();

            degrader.evaluate(stats);

            assertThat(degrader.isDegraded()).isTrue();
            assertThat(degrader.getDegradationCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("驱逐次数过多应该触发降级")
        void highEvictionCountShouldTriggerDegradation() {
            StoreStats stats = StoreStats.builder()
                .hitCount(80)
                .missCount(20)
                .hitRate(0.8) // 高命中率
                .evictionCount(15000) // 超过10000阈值
                .build();

            degrader.evaluate(stats);

            assertThat(degrader.isDegraded()).isTrue();
            assertThat(degrader.getDegradationCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("命中率过低且驱逐次数过多应该触发降级")
        void lowHitRateAndHighEvictionShouldTriggerDegradation() {
            StoreStats stats = StoreStats.builder()
                .hitCount(10)
                .missCount(90)
                .hitRate(0.1) // 低于0.2阈值
                .evictionCount(15000) // 超过10000阈值
                .build();

            degrader.evaluate(stats);

            assertThat(degrader.isDegraded()).isTrue();
            assertThat(degrader.getDegradationCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("良好状态不应该触发降级")
        void goodStatsShouldNotTriggerDegradation() {
            StoreStats stats = StoreStats.builder()
                .hitCount(80)
                .missCount(20)
                .hitRate(0.8) // 高命中率
                .evictionCount(5000) // 正常驱逐次数
                .build();

            degrader.evaluate(stats);

            assertThat(degrader.isDegraded()).isFalse();
            assertThat(degrader.getDegradationCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("重复降级应该增加降级计数")
        void repeatedDegradationShouldIncreaseCount() {
            StoreStats badStats = StoreStats.builder()
                .hitCount(10)
                .missCount(90)
                .hitRate(0.1)
                .evictionCount(5000)
                .build();

            // 第一次降级
            degrader.evaluate(badStats);
            assertThat(degrader.getDegradationCount()).isEqualTo(1);

            // 恢复然后再次降级
            degrader.forceRecover();
            degrader.evaluate(badStats);
            assertThat(degrader.getDegradationCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("恢复机制测试")
    class RecoveryMechanismTests {

        @BeforeEach
        void setUpDegradedState() {
            // 先降级
            StoreStats badStats = StoreStats.builder()
                .hitCount(10)
                .missCount(90)
                .hitRate(0.1)
                .evictionCount(5000)
                .build();
            degrader.evaluate(badStats);
            assertThat(degrader.isDegraded()).isTrue();
        }

        @Test
        @DisplayName("达到恢复条件应该逐步恢复")
        void meetingRecoveryConditionsShouldGraduallyRecover() {
            StoreStats goodStats = StoreStats.builder()
                .hitCount(70)
                .missCount(30)
                .hitRate(0.7) // 超过0.5恢复阈值
                .evictionCount(5000) // 正常驱逐次数
                .build();

            // 连续5次良好检查才能恢复
            for (int i = 1; i < 5; i++) {
                degrader.evaluate(goodStats);
                assertThat(degrader.isDegraded()).isTrue(); // 还未恢复
            }

            // 第5次检查应该恢复
            degrader.evaluate(goodStats);
            assertThat(degrader.isDegraded()).isFalse();
        }

        @Test
        @DisplayName("恢复过程中遇到差状态应该重置计数")
        void badStatsInRecoveryShouldResetCount() {
            StoreStats goodStats = StoreStats.builder()
                .hitCount(70)
                .missCount(30)
                .hitRate(0.7)
                .evictionCount(5000)
                .build();

            StoreStats badStats = StoreStats.builder()
                .hitCount(40)
                .missCount(60)
                .hitRate(0.4) // 低于0.5恢复阈值
                .evictionCount(5000)
                .build();

            // 先进行3次良好检查
            for (int i = 0; i < 3; i++) {
                degrader.evaluate(goodStats);
            }
            assertThat(degrader.isDegraded()).isTrue();

            // 一次差状态应该重置恢复计数
            degrader.evaluate(badStats);
            assertThat(degrader.isDegraded()).isTrue();

            // 再次需要5次良好检查才能恢复
            for (int i = 0; i < 5; i++) {
                degrader.evaluate(goodStats);
            }
            assertThat(degrader.isDegraded()).isFalse();
        }

        @Test
        @DisplayName("驱逐次数过多应该阻止恢复")
        void highEvictionsShouldPreventRecovery() {
            StoreStats stats = StoreStats.builder()
                .hitCount(70)
                .missCount(30)
                .hitRate(0.7) // 高命中率
                .evictionCount(15000) // 驱逐次数过多
                .build();

            // 即使命中率高，驱逐次数过多也不能恢复
            for (int i = 0; i < 10; i++) {
                degrader.evaluate(stats);
            }
            assertThat(degrader.isDegraded()).isTrue();
        }
    }

    @Nested
    @DisplayName("强制操作测试")
    class ForceOperationTests {

        @Test
        @DisplayName("强制降级应该立即降级")
        void forceDegradeShouldImmediatelyDegrade() {
            assertThat(degrader.isDegraded()).isFalse();
            
            degrader.forceDegrade();
            
            assertThat(degrader.isDegraded()).isTrue();
            assertThat(degrader.getDegradationCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("强制恢复应该立即恢复")
        void forceRecoverShouldImmediatelyRecover() {
            // 先降级
            degrader.forceDegrade();
            assertThat(degrader.isDegraded()).isTrue();
            
            degrader.forceRecover();
            
            assertThat(degrader.isDegraded()).isFalse();
        }

        @Test
        @DisplayName("多次强制降级应该增加计数")
        void multipleForceDegradesShouldIncreaseCount() {
            degrader.forceDegrade();
            assertThat(degrader.getDegradationCount()).isEqualTo(1);
            
            degrader.forceRecover();
            degrader.forceDegrade();
            assertThat(degrader.getDegradationCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("重置应该清除所有状态")
        void resetShouldClearAllState() {
            // 设置一些状态
            degrader.forceDegrade();
            degrader.forceDegrade(); // 降级两次
            assertThat(degrader.isDegraded()).isTrue();
            assertThat(degrader.getDegradationCount()).isEqualTo(2);
            
            degrader.reset();
            
            assertThat(degrader.isDegraded()).isFalse();
            assertThat(degrader.getDegradationCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryConditionTests {

        @Test
        @DisplayName("命中率等于阈值不应该降级")
        void hitRateEqualToThresholdShouldNotDegrade() {
            StoreStats stats = StoreStats.builder()
                .hitCount(20)
                .missCount(80)
                .hitRate(0.2) // 等于0.2阈值
                .evictionCount(5000)
                .build();

            degrader.evaluate(stats);

            assertThat(degrader.isDegraded()).isFalse();
        }

        @Test
        @DisplayName("驱逐次数等于阈值不应该降级")
        void evictionCountEqualToThresholdShouldNotDegrade() {
            StoreStats stats = StoreStats.builder()
                .hitCount(80)
                .missCount(20)
                .hitRate(0.8)
                .evictionCount(10000) // 等于10000阈值
                .build();

            degrader.evaluate(stats);

            assertThat(degrader.isDegraded()).isFalse();
        }

        @Test
        @DisplayName("恢复命中率等于阈值应该可以恢复")
        void recoveryHitRateEqualToThresholdShouldRecover() {
            // 先降级
            degrader.forceDegrade();
            
            StoreStats stats = StoreStats.builder()
                .hitCount(50)
                .missCount(50)
                .hitRate(0.5) // 等于0.5恢复阈值
                .evictionCount(5000)
                .build();

            // 连续5次检查
            for (int i = 0; i < 5; i++) {
                degrader.evaluate(stats);
            }

            assertThat(degrader.isDegraded()).isFalse();
        }

        @Test
        @DisplayName("零值统计应该正确处理")
        void zeroStatsShouldBeHandledCorrectly() {
            StoreStats stats = StoreStats.builder()
                .hitCount(0)
                .missCount(0)
                .hitRate(0.0)
                .evictionCount(0)
                .build();

            degrader.evaluate(stats);

            // 零命中率应该触发降级
            assertThat(degrader.isDegraded()).isTrue();
        }
    }

    @Nested
    @DisplayName("配置参数测试")
    class ConfigurationParameterTests {

        @Test
        @DisplayName("不同的最小命中率阈值应该正确工作")
        void differentMinHitRateThresholdShouldWork() {
            // 设置更严格的阈值
            ReflectionTestUtils.setField(degrader, "minHitRate", 0.8);
            
            StoreStats stats = StoreStats.builder()
                .hitCount(70)
                .missCount(30)
                .hitRate(0.7) // 低于新的0.8阈值
                .evictionCount(5000)
                .build();

            degrader.evaluate(stats);

            assertThat(degrader.isDegraded()).isTrue();
        }

        @Test
        @DisplayName("不同的最大驱逐次数应该正确工作")
        void differentMaxEvictionsThresholdShouldWork() {
            // 设置更严格的阈值
            ReflectionTestUtils.setField(degrader, "maxEvictions", 5000L);
            
            StoreStats stats = StoreStats.builder()
                .hitCount(80)
                .missCount(20)
                .hitRate(0.8)
                .evictionCount(6000) // 超过新的5000阈值
                .build();

            degrader.evaluate(stats);

            assertThat(degrader.isDegraded()).isTrue();
        }

        @Test
        @DisplayName("不同的恢复计数应该正确工作")
        void differentRecoveryCountShouldWork() {
            // 设置不同的恢复计数
            ReflectionTestUtils.setField(degrader, "recoveryCount", 3);
            
            // 先降级
            degrader.forceDegrade();
            
            StoreStats goodStats = StoreStats.builder()
                .hitCount(70)
                .missCount(30)
                .hitRate(0.7)
                .evictionCount(5000)
                .build();

            // 只需3次良好检查就能恢复
            for (int i = 0; i < 3; i++) {
                degrader.evaluate(goodStats);
            }

            assertThat(degrader.isDegraded()).isFalse();
        }
    }
}