package com.syy.taskflowinsight.tracking.format;

import com.syy.taskflowinsight.annotation.DateFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Field;
import java.time.*;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TfiDateTimeFormatter单元测试
 * 验证ISO-8601格式、时区处理、Duration/Period格式化
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
class TfiDateTimeFormatterTest {
    
    private TfiDateTimeFormatter formatter;
    
    @BeforeEach
    void setUp() {
        formatter = new TfiDateTimeFormatter("yyyy-MM-dd HH:mm:ss", "UTC");
    }
    
    @Test
    @DisplayName("LocalDateTime格式化")
    void testFormatLocalDateTime() {
        // Given
        LocalDateTime dateTime = LocalDateTime.of(2023, 12, 25, 15, 30, 45);
        
        // When
        String result = formatter.format(dateTime, null);
        
        // Then
        assertEquals("2023-12-25 15:30:45", result);
    }
    
    @Test
    @DisplayName("Instant格式化（UTC时区）")
    void testFormatInstant() {
        // Given
        Instant instant = Instant.ofEpochSecond(1703516445); // 2023-12-25 15:00:45 UTC (actual)
        
        // When
        String result = formatter.format(instant, null);
        
        // Then
        assertTrue(result.contains("2023-12-25"));
        assertTrue(result.contains("15:00:45"));
    }
    
    @Test
    @DisplayName("ZonedDateTime格式化")
    void testFormatZonedDateTime() {
        // Given
        ZonedDateTime zdt = ZonedDateTime.of(2023, 12, 25, 15, 30, 45, 0, ZoneOffset.UTC);
        
        // When
        String result = formatter.format(zdt, null);
        
        // Then
        assertEquals("2023-12-25 15:30:45", result);
    }
    
    @Test
    @DisplayName("LocalDate格式化")
    void testFormatLocalDate() {
        // Given
        LocalDate date = LocalDate.of(2023, 12, 25);
        
        // When
        String result = formatter.format(date, null);
        
        // Then
        assertEquals("2023-12-25", result);
    }
    
    @Test
    @DisplayName("LocalTime格式化")
    void testFormatLocalTime() {
        // Given
        LocalTime time = LocalTime.of(15, 30, 45);
        
        // When
        String result = formatter.format(time, null);
        
        // Then
        assertEquals("15:30:45", result);
    }
    
    @Test
    @DisplayName("Duration ISO-8601格式化")
    void testFormatDuration() {
        // Given & When & Then
        
        // 简单秒数
        assertEquals("PT30S", formatter.formatDuration(Duration.ofSeconds(30)));
        
        // 分钟和秒
        assertEquals("PT2M30S", formatter.formatDuration(Duration.ofMinutes(2).plusSeconds(30)));
        
        // 小时、分钟、秒
        assertEquals("PT1H2M30S", formatter.formatDuration(Duration.ofHours(1).plusMinutes(2).plusSeconds(30)));
        
        // 天、小时、分钟、秒
        assertEquals("P1DT1H2M30S", formatter.formatDuration(
            Duration.ofDays(1).plusHours(1).plusMinutes(2).plusSeconds(30)));
        
        // 毫秒
        assertEquals("PT10.500S", formatter.formatDuration(Duration.ofSeconds(10).plusMillis(500)));
        
        // 零时长
        assertEquals("PT0S", formatter.formatDuration(Duration.ZERO));
        
        // null处理
        assertNull(formatter.formatDuration(null));
    }
    
    @Test
    @DisplayName("Period ISO-8601格式化")
    void testFormatPeriod() {
        // Given & When & Then
        
        // 年月日
        assertEquals("P1Y2M3D", formatter.formatPeriod(Period.of(1, 2, 3)));
        
        // 仅年
        assertEquals("P1Y", formatter.formatPeriod(Period.ofYears(1)));
        
        // 仅月
        assertEquals("P2M", formatter.formatPeriod(Period.ofMonths(2)));
        
        // 仅日
        assertEquals("P3D", formatter.formatPeriod(Period.ofDays(3)));
        
        // 零时长
        assertEquals("P0D", formatter.formatPeriod(Period.ZERO));
        
        // null处理
        assertNull(formatter.formatPeriod(null));
    }
    
    @Test
    @DisplayName("多种格式解析")
    void testParseWithTolerance() {
        // ISO格式
        LocalDateTime result1 = (LocalDateTime) formatter.parseWithTolerance(
            "2023-12-25T15:30:45.123", LocalDateTime.class);
        assertNotNull(result1);
        assertEquals(2023, result1.getYear());
        assertEquals(12, result1.getMonthValue());
        assertEquals(25, result1.getDayOfMonth());
        
        // 标准格式
        LocalDateTime result2 = (LocalDateTime) formatter.parseWithTolerance(
            "2023-12-25 15:30:45", LocalDateTime.class);
        assertNotNull(result2);
        
        // 日期格式
        LocalDate result3 = (LocalDate) formatter.parseWithTolerance(
            "2023-12-25", LocalDate.class);
        assertNotNull(result3);
        assertEquals(LocalDate.of(2023, 12, 25), result3);
    }
    
    @Test
    @DisplayName("解析失败处理")
    void testParseFailure() {
        // 无效日期字符串
        assertThrows(IllegalArgumentException.class, () -> 
            formatter.parseWithTolerance("invalid-date", LocalDateTime.class));
        
        // null和空字符串
        assertNull(formatter.parseWithTolerance(null, LocalDateTime.class));
        assertNull(formatter.parseWithTolerance("", LocalDateTime.class));
        assertNull(formatter.parseWithTolerance("   ", LocalDateTime.class));
    }
    
    @Test
    @DisplayName("Duration解析")
    void testParseDuration() {
        // 标准ISO格式
        assertEquals(Duration.ofSeconds(30), formatter.parseDuration("PT30S"));
        assertEquals(Duration.ofMinutes(2).plusSeconds(30), formatter.parseDuration("PT2M30S"));
        assertEquals(Duration.ofHours(1).plusMinutes(2).plusSeconds(30), formatter.parseDuration("PT1H2M30S"));
        
        // 带毫秒
        assertEquals(Duration.ofSeconds(10).plusMillis(500), formatter.parseDuration("PT10.5S"));
        
        // 零时长
        assertEquals(Duration.ZERO, formatter.parseDuration("PT0S"));
        
        // 无效格式
        assertNull(formatter.parseDuration("invalid"));
        assertNull(formatter.parseDuration(null));
    }
    
    @Test
    @DisplayName("Period解析")
    void testParsePeriod() {
        // 标准ISO格式
        assertEquals(Period.of(1, 2, 3), formatter.parsePeriod("P1Y2M3D"));
        assertEquals(Period.ofYears(1), formatter.parsePeriod("P1Y"));
        assertEquals(Period.ofMonths(2), formatter.parsePeriod("P2M"));
        assertEquals(Period.ofDays(3), formatter.parsePeriod("P3D"));
        
        // 零时长
        assertEquals(Period.ZERO, formatter.parsePeriod("P0D"));
        
        // 无效格式
        assertNull(formatter.parsePeriod("invalid"));
        assertNull(formatter.parsePeriod(null));
    }
    
    @Test
    @DisplayName("字段注解格式覆盖")
    void testFieldAnnotationOverride() throws NoSuchFieldException {
        // Given
        Field field = TestClass.class.getDeclaredField("customFormatDate");
        LocalDateTime dateTime = LocalDateTime.of(2023, 12, 25, 15, 30, 45);
        
        // When
        String result = formatter.format(dateTime, field);
        
        // Then - 应该使用注解中指定的格式
        assertEquals("25/12/2023 15:30", result);
    }
    
    @Test
    @DisplayName("时区配置测试")
    void testTimezoneConfiguration() {
        // 系统默认时区
        TfiDateTimeFormatter systemFormatter = new TfiDateTimeFormatter("yyyy-MM-dd HH:mm:ss", "SYSTEM");
        assertEquals(ZoneId.systemDefault(), systemFormatter.getDefaultZoneId());
        
        // UTC时区
        TfiDateTimeFormatter utcFormatter = new TfiDateTimeFormatter("yyyy-MM-dd HH:mm:ss", "UTC");
        assertEquals(ZoneOffset.UTC, utcFormatter.getDefaultZoneId());
        
        // 具体时区
        TfiDateTimeFormatter shanghaiFormatter = new TfiDateTimeFormatter("yyyy-MM-dd HH:mm:ss", "Asia/Shanghai");
        assertEquals(ZoneId.of("Asia/Shanghai"), shanghaiFormatter.getDefaultZoneId());
        
        // 无效时区降级到系统默认
        TfiDateTimeFormatter invalidFormatter = new TfiDateTimeFormatter("yyyy-MM-dd HH:mm:ss", "Invalid/Zone");
        assertEquals(ZoneId.systemDefault(), invalidFormatter.getDefaultZoneId());
    }
    
    @Test
    @DisplayName("格式化器缓存测试")
    void testFormatterCache() {
        // 多次格式化相同模式，应该复用缓存
        LocalDateTime dateTime = LocalDateTime.of(2023, 12, 25, 15, 30, 45);
        
        String result1 = formatter.format(dateTime, null);
        String result2 = formatter.format(dateTime, null);
        
        assertEquals(result1, result2);
        assertEquals("2023-12-25 15:30:45", result1);
        
        // 清除缓存
        formatter.clearCache();
        String result3 = formatter.format(dateTime, null);
        assertEquals(result1, result3);
    }
    
    @Test
    @DisplayName("null值处理")
    void testNullHandling() {
        assertNull(formatter.format(null, null));
        assertNull(formatter.formatDuration(null));
        assertNull(formatter.formatPeriod(null));
    }
    
    @Test
    @DisplayName("异常格式降级处理")
    void testInvalidPatternFallback() {
        // 使用无效格式模式，应该降级到默认格式
        TfiDateTimeFormatter invalidFormatter = new TfiDateTimeFormatter("invalid-pattern", "UTC");
        
        LocalDateTime dateTime = LocalDateTime.of(2023, 12, 25, 15, 30, 45);
        
        // 应该不抛异常，而是使用默认格式
        assertDoesNotThrow(() -> {
            String result = invalidFormatter.format(dateTime, null);
            assertNotNull(result);
        });
    }
    
    // 测试用内部类
    static class TestClass {
        @DateFormat(pattern = "dd/MM/yyyy HH:mm")
        private LocalDateTime customFormatDate;
    }
}