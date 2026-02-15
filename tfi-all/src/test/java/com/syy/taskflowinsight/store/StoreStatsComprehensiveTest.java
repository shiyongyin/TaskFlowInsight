package com.syy.taskflowinsight.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StoreStats综合测试
 * 专门提升StoreStats覆盖率从0%到100%
 * 
 * @author TaskFlow Insight Team
 * @since 2025-01-13
 */
@DisplayName("StoreStats综合测试 - 目标覆盖率100%")
class StoreStatsComprehensiveTest {

    @Nested
    @DisplayName("Builder模式测试")
    class BuilderPatternTests {

        @Test
        @DisplayName("Builder应该正确构建StoreStats")
        void builderShouldCreateStoreStatsCorrectly() {
            StoreStats stats = StoreStats.builder()
                .hitCount(100)
                .missCount(50)
                .loadSuccessCount(40)
                .loadFailureCount(10)
                .evictionCount(5)
                .totalLoadTime(1000000) // 1ms in nanoseconds
                .estimatedSize(200)
                .hitRate(0.75)
                .missRate(0.25)
                .build();

            assertThat(stats.getHitCount()).isEqualTo(100);
            assertThat(stats.getMissCount()).isEqualTo(50);
            assertThat(stats.getLoadSuccessCount()).isEqualTo(40);
            assertThat(stats.getLoadFailureCount()).isEqualTo(10);
            assertThat(stats.getEvictionCount()).isEqualTo(5);
            assertThat(stats.getTotalLoadTime()).isEqualTo(1000000);
            assertThat(stats.getEstimatedSize()).isEqualTo(200);
            assertThat(stats.getHitRate()).isEqualTo(0.75);
            assertThat(stats.getMissRate()).isEqualTo(0.25);
        }

        @Test
        @DisplayName("Builder应该支持部分字段构建")
        void builderShouldSupportPartialFieldConstruction() {
            StoreStats stats = StoreStats.builder()
                .hitCount(80)
                .missCount(20)
                .build();

            assertThat(stats.getHitCount()).isEqualTo(80);
            assertThat(stats.getMissCount()).isEqualTo(20);
            assertThat(stats.getLoadSuccessCount()).isEqualTo(0); // 默认值
            assertThat(stats.getEvictionCount()).isEqualTo(0); // 默认值
        }
    }

    @Nested
    @DisplayName("命中率计算测试")
    class HitRateCalculationTests {

        @Test
        @DisplayName("正常情况下应该正确计算命中率")
        void shouldCalculateHitRateCorrectlyInNormalCase() {
            StoreStats stats = StoreStats.builder()
                .hitCount(80)
                .missCount(20)
                .build();

            double hitRate = stats.calculateHitRate();

            assertThat(hitRate).isEqualTo(0.8);
        }

        @Test
        @DisplayName("全部命中时命中率应该为1.0")
        void hitRateShouldBeOneWhenAllHits() {
            StoreStats stats = StoreStats.builder()
                .hitCount(100)
                .missCount(0)
                .build();

            double hitRate = stats.calculateHitRate();

            assertThat(hitRate).isEqualTo(1.0);
        }

        @Test
        @DisplayName("全部未命中时命中率应该为0.0")
        void hitRateShouldBeZeroWhenAllMisses() {
            StoreStats stats = StoreStats.builder()
                .hitCount(0)
                .missCount(100)
                .build();

            double hitRate = stats.calculateHitRate();

            assertThat(hitRate).isEqualTo(0.0);
        }

        @Test
        @DisplayName("没有请求时命中率应该为0.0")
        void hitRateShouldBeZeroWhenNoRequests() {
            StoreStats stats = StoreStats.builder()
                .hitCount(0)
                .missCount(0)
                .build();

            double hitRate = stats.calculateHitRate();

            assertThat(hitRate).isEqualTo(0.0);
        }

        @Test
        @DisplayName("高精度命中率计算应该正确")
        void highPrecisionHitRateCalculationShouldBeCorrect() {
            StoreStats stats = StoreStats.builder()
                .hitCount(1)
                .missCount(3)
                .build();

            double hitRate = stats.calculateHitRate();

            assertThat(hitRate).isEqualTo(0.25);
        }
    }

    @Nested
    @DisplayName("未命中率计算测试")
    class MissRateCalculationTests {

        @Test
        @DisplayName("正常情况下应该正确计算未命中率")
        void shouldCalculateMissRateCorrectlyInNormalCase() {
            StoreStats stats = StoreStats.builder()
                .hitCount(80)
                .missCount(20)
                .build();

            double missRate = stats.calculateMissRate();

            assertThat(missRate).isEqualTo(0.2);
        }

        @Test
        @DisplayName("全部命中时未命中率应该为0.0")
        void missRateShouldBeZeroWhenAllHits() {
            StoreStats stats = StoreStats.builder()
                .hitCount(100)
                .missCount(0)
                .build();

            double missRate = stats.calculateMissRate();

            assertThat(missRate).isEqualTo(0.0);
        }

        @Test
        @DisplayName("全部未命中时未命中率应该为1.0")
        void missRateShouldBeOneWhenAllMisses() {
            StoreStats stats = StoreStats.builder()
                .hitCount(0)
                .missCount(100)
                .build();

            double missRate = stats.calculateMissRate();

            assertThat(missRate).isEqualTo(1.0);
        }

        @Test
        @DisplayName("没有请求时未命中率应该为0.0")
        void missRateShouldBeZeroWhenNoRequests() {
            StoreStats stats = StoreStats.builder()
                .hitCount(0)
                .missCount(0)
                .build();

            double missRate = stats.calculateMissRate();

            assertThat(missRate).isEqualTo(0.0);
        }

        @Test
        @DisplayName("命中率和未命中率之和应该为1.0")
        void hitRateAndMissRateSumShouldBeOne() {
            StoreStats stats = StoreStats.builder()
                .hitCount(75)
                .missCount(25)
                .build();

            double hitRate = stats.calculateHitRate();
            double missRate = stats.calculateMissRate();

            assertThat(hitRate + missRate).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("平均加载时间计算测试")
    class AverageLoadTimeCalculationTests {

        @Test
        @DisplayName("正常情况下应该正确计算平均加载时间")
        void shouldCalculateAverageLoadTimeCorrectlyInNormalCase() {
            StoreStats stats = StoreStats.builder()
                .loadSuccessCount(10)
                .totalLoadTime(5000000) // 5ms total
                .build();

            long averageLoadTime = stats.getAverageLoadTime();

            assertThat(averageLoadTime).isEqualTo(500000); // 0.5ms average
        }

        @Test
        @DisplayName("没有成功加载时平均加载时间应该为0")
        void averageLoadTimeShouldBeZeroWhenNoSuccessfulLoads() {
            StoreStats stats = StoreStats.builder()
                .loadSuccessCount(0)
                .totalLoadTime(5000000)
                .build();

            long averageLoadTime = stats.getAverageLoadTime();

            assertThat(averageLoadTime).isEqualTo(0);
        }

        @Test
        @DisplayName("单次加载的平均时间应该等于总时间")
        void averageLoadTimeShouldEqualTotalTimeForSingleLoad() {
            StoreStats stats = StoreStats.builder()
                .loadSuccessCount(1)
                .totalLoadTime(2000000) // 2ms
                .build();

            long averageLoadTime = stats.getAverageLoadTime();

            assertThat(averageLoadTime).isEqualTo(2000000);
        }

        @Test
        @DisplayName("大量加载的平均时间计算应该正确")
        void averageLoadTimeCalculationShouldBeCorrectForManyLoads() {
            StoreStats stats = StoreStats.builder()
                .loadSuccessCount(1000)
                .totalLoadTime(100000000) // 100ms total
                .build();

            long averageLoadTime = stats.getAverageLoadTime();

            assertThat(averageLoadTime).isEqualTo(100000); // 0.1ms average
        }
    }

    @Nested
    @DisplayName("数据一致性测试")
    class DataConsistencyTests {

        @Test
        @DisplayName("Lombok Data注解应该正确生成equals和hashCode")
        void lombokDataShouldGenerateEqualsAndHashCodeCorrectly() {
            StoreStats stats1 = StoreStats.builder()
                .hitCount(100)
                .missCount(50)
                .loadSuccessCount(40)
                .build();

            StoreStats stats2 = StoreStats.builder()
                .hitCount(100)
                .missCount(50)
                .loadSuccessCount(40)
                .build();

            StoreStats stats3 = StoreStats.builder()
                .hitCount(101) // 不同的值
                .missCount(50)
                .loadSuccessCount(40)
                .build();

            assertThat(stats1).isEqualTo(stats2);
            assertThat(stats1.hashCode()).isEqualTo(stats2.hashCode());
            assertThat(stats1).isNotEqualTo(stats3);
        }

        @Test
        @DisplayName("Lombok Data注解应该正确生成toString")
        void lombokDataShouldGenerateToStringCorrectly() {
            StoreStats stats = StoreStats.builder()
                .hitCount(100)
                .missCount(50)
                .hitRate(0.75)
                .build();

            String toString = stats.toString();

            assertThat(toString).contains("hitCount=100");
            assertThat(toString).contains("missCount=50");
            assertThat(toString).contains("hitRate=0.75");
        }

        @Test
        @DisplayName("所有getter方法应该返回正确的值")
        void allGetterMethodsShouldReturnCorrectValues() {
            StoreStats stats = StoreStats.builder()
                .hitCount(100)
                .missCount(50)
                .loadSuccessCount(40)
                .loadFailureCount(10)
                .evictionCount(5)
                .totalLoadTime(1000000)
                .estimatedSize(200)
                .hitRate(0.75)
                .missRate(0.25)
                .build();

            assertThat(stats.getHitCount()).isEqualTo(100);
            assertThat(stats.getMissCount()).isEqualTo(50);
            assertThat(stats.getLoadSuccessCount()).isEqualTo(40);
            assertThat(stats.getLoadFailureCount()).isEqualTo(10);
            assertThat(stats.getEvictionCount()).isEqualTo(5);
            assertThat(stats.getTotalLoadTime()).isEqualTo(1000000);
            assertThat(stats.getEstimatedSize()).isEqualTo(200);
            assertThat(stats.getHitRate()).isEqualTo(0.75);
            assertThat(stats.getMissRate()).isEqualTo(0.25);
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryValueTests {

        @Test
        @DisplayName("最大值应该正确处理")
        void maxValuesShouldBeHandledCorrectly() {
            StoreStats stats = StoreStats.builder()
                .hitCount(1000000)
                .missCount(1000000)
                .loadSuccessCount(1000000)
                .totalLoadTime(Long.MAX_VALUE)
                .build();

            // 验证大数值不会溢出
            assertThat(stats.getHitCount()).isEqualTo(1000000);
            assertThat(stats.getMissCount()).isEqualTo(1000000);
            assertThat(stats.getLoadSuccessCount()).isEqualTo(1000000);
            assertThat(stats.getTotalLoadTime()).isEqualTo(Long.MAX_VALUE);
            
            // 验证计算方法在大数值时的行为
            double hitRate = stats.calculateHitRate();
            assertThat(hitRate).isEqualTo(0.5); // 相等的hit和miss应该是50%
        }

        @Test
        @DisplayName("零值应该正确处理")
        void zeroValuesShouldBeHandledCorrectly() {
            StoreStats stats = StoreStats.builder()
                .hitCount(0)
                .missCount(0)
                .loadSuccessCount(0)
                .loadFailureCount(0)
                .evictionCount(0)
                .totalLoadTime(0)
                .estimatedSize(0)
                .hitRate(0.0)
                .missRate(0.0)
                .build();

            assertThat(stats.calculateHitRate()).isEqualTo(0.0);
            assertThat(stats.calculateMissRate()).isEqualTo(0.0);
            assertThat(stats.getAverageLoadTime()).isEqualTo(0);
        }
    }
}