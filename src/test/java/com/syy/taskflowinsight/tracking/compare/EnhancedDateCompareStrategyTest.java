package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.*;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EnhancedDateCompareStrategy单元测试
 * 验证毫秒级容差、多种时间类型、Duration/Period支持
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
class EnhancedDateCompareStrategyTest {
    
    private final EnhancedDateCompareStrategy strategy = new EnhancedDateCompareStrategy();
    
    @Test
    @DisplayName("Date默认精确比较（0ms容差）")
    void testDateDefaultPreciseComparison() {
        // Given
        Date date1 = new Date(1000L);
        Date date2 = new Date(1001L); // 1ms差异
        
        // When - 使用默认容差（0ms）
        boolean result = strategy.compareDates(date1, date2);
        
        // Then - 应该不等（精确比较）
        assertFalse(result, "Dates with 1ms difference should not be equal with 0ms tolerance");
        
        // 完全相同应该相等
        assertTrue(strategy.compareDates(date1, date1));
    }
    
    @Test
    @DisplayName("Date容差比较")
    void testDateComparisonWithTolerance() {
        // Given
        Date date1 = new Date(1000L);
        Date date2 = new Date(1100L); // 100ms差异
        
        // When - 使用200ms容差
        boolean result = strategy.compareDates(date1, date2, 200L);
        
        // Then - 应该相等（在容差范围内）
        assertTrue(result, "Dates within tolerance should be equal");
        
        // 超出容差应该不等
        assertFalse(strategy.compareDates(date1, date2, 50L));
    }
    
    @Test
    @DisplayName("Instant高精度比较")
    void testInstantComparison() {
        // Given
        Instant instant1 = Instant.ofEpochMilli(1000L);
        Instant instant2 = Instant.ofEpochMilli(1050L); // 50ms差异
        
        // When - 使用100ms容差
        boolean result = strategy.compareInstants(instant1, instant2, 100L);
        
        // Then
        assertTrue(result, "Instants within tolerance should be equal");
        
        // 精确比较
        assertFalse(strategy.compareInstants(instant1, instant2, 0L));
    }
    
    @Test
    @DisplayName("LocalDateTime比较（忽略时区）")
    void testLocalDateTimeComparison() {
        // Given
        LocalDateTime dt1 = LocalDateTime.of(2023, 12, 25, 15, 30, 45);
        LocalDateTime dt2 = LocalDateTime.of(2023, 12, 25, 15, 30, 45, 100_000_000); // 100ms纳秒差
        
        // When - 精确比较
        boolean preciseResult = strategy.compareLocalDateTimes(dt1, dt2, 0L);
        assertFalse(preciseResult, "LocalDateTimes with nano difference should not be equal");
        
        // When - 容差比较
        boolean toleranceResult = strategy.compareLocalDateTimes(dt1, dt2, 200L);
        assertTrue(toleranceResult, "LocalDateTimes within tolerance should be equal");
    }
    
    @Test
    @DisplayName("LocalDate日期比较")
    void testLocalDateComparison() {
        // Given
        LocalDate date1 = LocalDate.of(2023, 12, 25);
        LocalDate date2 = LocalDate.of(2023, 12, 25);
        LocalDate date3 = LocalDate.of(2023, 12, 26);
        
        // When - 相同日期
        assertTrue(strategy.compareLocalDates(date1, date2, 0L));
        
        // When - 不同日期
        assertFalse(strategy.compareLocalDates(date1, date3, 0L));
        
        // When - 使用容差（1天 = 86400000ms）
        assertTrue(strategy.compareLocalDates(date1, date3, 86400000L));
    }
    
    @Test
    @DisplayName("Duration比较")
    void testDurationComparison() {
        // Given
        Duration duration1 = Duration.ofSeconds(10);
        Duration duration2 = Duration.ofSeconds(10).plusMillis(50);
        
        // When - 精确比较
        assertFalse(strategy.compareDurations(duration1, duration2, 0L));
        
        // When - 容差比较
        assertTrue(strategy.compareDurations(duration1, duration2, 100L));
    }
    
    @Test
    @DisplayName("Period比较（年月日）")
    void testPeriodComparison() {
        // Given
        Period period1 = Period.of(1, 2, 3);
        Period period2 = Period.of(1, 2, 3);
        Period period3 = Period.of(1, 2, 4);
        
        // When - Period只支持精确比较
        assertTrue(strategy.comparePeriods(period1, period2));
        assertFalse(strategy.comparePeriods(period1, period3));
    }
    
    @Test
    @DisplayName("通用时间比较接口")
    void testCompareTemporal() {
        // Date类型
        Date date1 = new Date(1000L);
        Date date2 = new Date(1100L);
        assertTrue(strategy.compareTemporal(date1, date2, 200L));
        
        // Instant类型
        Instant instant1 = Instant.ofEpochMilli(1000L);
        Instant instant2 = Instant.ofEpochMilli(1050L);
        assertTrue(strategy.compareTemporal(instant1, instant2, 100L));
        
        // 不同类型不相等
        assertFalse(strategy.compareTemporal(date1, instant1, 1000000L));
        
        // null处理
        assertTrue(strategy.compareTemporal(null, null, 0L));
        assertFalse(strategy.compareTemporal(date1, null, 0L));
    }
    
    @Test
    @DisplayName("类型判断工具方法")
    void testTypeDetection() {
        // 时间类型判断
        assertTrue(EnhancedDateCompareStrategy.isTemporalType(new Date()));
        assertTrue(EnhancedDateCompareStrategy.isTemporalType(Instant.now()));
        assertTrue(EnhancedDateCompareStrategy.isTemporalType(LocalDateTime.now()));
        assertTrue(EnhancedDateCompareStrategy.isTemporalType(LocalDate.now()));
        assertTrue(EnhancedDateCompareStrategy.isTemporalType(LocalTime.now()));
        assertTrue(EnhancedDateCompareStrategy.isTemporalType(Duration.ofSeconds(1)));
        assertTrue(EnhancedDateCompareStrategy.isTemporalType(Period.ofDays(1)));
        
        assertFalse(EnhancedDateCompareStrategy.isTemporalType("2023-12-25"));
        assertFalse(EnhancedDateCompareStrategy.isTemporalType(100L));
        assertFalse(EnhancedDateCompareStrategy.isTemporalType(null));
        
        // 需要时间比较判断
        assertTrue(EnhancedDateCompareStrategy.needsTemporalCompare(
            new Date(), new Date()));
        assertTrue(EnhancedDateCompareStrategy.needsTemporalCompare(
            Instant.now(), Instant.now()));
        
        assertFalse(EnhancedDateCompareStrategy.needsTemporalCompare(
            new Date(), "2023-12-25"));
        assertFalse(EnhancedDateCompareStrategy.needsTemporalCompare(
            null, new Date()));
    }
    
    @Test
    @DisplayName("示例代码验证：1秒容差仅为示例，默认为0ms")
    void testDocumentedDefaultBehavior() {
        // 验证文档中说明的行为
        // DateCompareStrategy.java:1 的 1秒容差仅为示例代码
        // 实际默认容差应该是 0ms
        
        Date date1 = new Date(1000L);
        Date date2 = new Date(2000L); // 1秒差异
        
        // 默认应该不相等（0ms容差）
        assertFalse(strategy.compareDates(date1, date2), 
            "Default tolerance should be 0ms, not 1 second");
        
        // 显式指定1秒容差才相等
        assertTrue(strategy.compareDates(date1, date2, 1000L), 
            "With explicit 1s tolerance, dates should be equal");
    }
}