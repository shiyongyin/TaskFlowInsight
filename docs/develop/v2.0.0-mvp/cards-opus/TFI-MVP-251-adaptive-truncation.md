# TFI-MVP-251 自适应截断

## 任务概述
实现自适应截断与水位策略验证，在高水位情况下动态收紧截断上限，水位回落后恢复正常，确保系统稳定性。（M1阶段任务）

## 核心目标
- [ ] 实现水位监控机制
- [ ] 实现自适应截断策略
- [ ] 验证水位阈值切换行为
- [ ] 验证水位回落恢复机制
- [ ] 确保不会导致OOM
- [ ] 验证吞吐量影响可接受

## 实现清单

### 1. 水位监控器
```java
package com.syy.taskflowinsight.core.watermark;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class WatermarkMonitor {
    
    private final AtomicLong totalMemoryUsed = new AtomicLong(0);
    private final AtomicLong maxMemoryLimit = new AtomicLong(0);
    private final AtomicInteger currentWatermarkLevel = new AtomicInteger(0);
    
    // 水位阈值
    private static final double HIGH_WATERMARK_80 = 0.80;
    private static final double HIGH_WATERMARK_90 = 0.90;
    
    // 截断限制
    private static final int DEFAULT_TRUNCATION_LIMIT = 8192;
    private static final int HIGH_WATERMARK_LIMIT_80 = 2048;
    private static final int HIGH_WATERMARK_LIMIT_90 = 512;
    
    public WatermarkMonitor() {
        this.maxMemoryLimit.set(Runtime.getRuntime().maxMemory());
    }
    
    /**
     * 更新内存使用量
     */
    public void updateMemoryUsage(long used) {
        totalMemoryUsed.set(used);
        updateWatermarkLevel();
    }
    
    /**
     * 获取当前截断限制
     */
    public int getCurrentTruncationLimit() {
        int level = currentWatermarkLevel.get();
        switch (level) {
            case 2: // >90%
                log.debug("High watermark (>90%) - using truncation limit: {}", 
                    HIGH_WATERMARK_LIMIT_90);
                return HIGH_WATERMARK_LIMIT_90;
            case 1: // >80%
                log.debug("High watermark (>80%) - using truncation limit: {}", 
                    HIGH_WATERMARK_LIMIT_80);
                return HIGH_WATERMARK_LIMIT_80;
            default: // <80%
                return DEFAULT_TRUNCATION_LIMIT;
        }
    }
    
    /**
     * 获取当前内存使用率
     */
    public double getCurrentMemoryUsageRatio() {
        long used = totalMemoryUsed.get();
        long max = maxMemoryLimit.get();
        return max > 0 ? (double) used / max : 0.0;
    }
    
    /**
     * 获取当前水位等级
     */
    public int getCurrentWatermarkLevel() {
        return currentWatermarkLevel.get();
    }
    
    private void updateWatermarkLevel() {
        double ratio = getCurrentMemoryUsageRatio();
        int oldLevel = currentWatermarkLevel.get();
        int newLevel;
        
        if (ratio >= HIGH_WATERMARK_90) {
            newLevel = 2;
        } else if (ratio >= HIGH_WATERMARK_80) {
            newLevel = 1;
        } else {
            newLevel = 0;
        }
        
        if (oldLevel != newLevel) {
            currentWatermarkLevel.set(newLevel);
            log.info("Watermark level changed: {} -> {} (memory usage: {:.2f}%)", 
                oldLevel, newLevel, ratio * 100);
        }
    }
    
    /**
     * 强制垃圾回收并更新内存统计
     */
    public void forceGCAndUpdateStats() {
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        updateMemoryUsage(used);
    }
}
```

### 2. 自适应截断处理器
```java
package com.syy.taskflowinsight.core.truncation;

import com.syy.taskflowinsight.core.watermark.WatermarkMonitor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AdaptiveTruncationProcessor {
    
    private final WatermarkMonitor watermarkMonitor;
    
    /**
     * 根据水位自适应截断字符串
     */
    public String truncateWithWatermark(String input) {
        if (input == null) {
            return "null";
        }
        
        int limit = watermarkMonitor.getCurrentTruncationLimit();
        return truncateString(input, limit);
    }
    
    /**
     * 截断字符串实现
     */
    private String truncateString(String input, int limit) {
        if (input.length() <= limit) {
            return input;
        }
        
        // 先转义特殊字符
        String escaped = escapeSpecialCharacters(input);
        
        // 再截断
        if (escaped.length() <= limit) {
            return escaped;
        }
        
        String truncated = escaped.substring(0, limit - 15); // 留出"... (truncated)"空间
        return truncated + "... (truncated)";
    }
    
    /**
     * 转义特殊字符
     */
    private String escapeSpecialCharacters(String input) {
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
    
    /**
     * 批量处理值表示
     */
    public String processValueRepr(Object value) {
        String repr = String.valueOf(value);
        return truncateWithWatermark(repr);
    }
    
    /**
     * 获取当前截断统计信息
     */
    public TruncationStats getCurrentStats() {
        return TruncationStats.builder()
            .currentLimit(watermarkMonitor.getCurrentTruncationLimit())
            .watermarkLevel(watermarkMonitor.getCurrentWatermarkLevel())
            .memoryUsageRatio(watermarkMonitor.getCurrentMemoryUsageRatio())
            .build();
    }
}
```

### 3. 截断统计信息
```java
package com.syy.taskflowinsight.core.truncation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TruncationStats {
    private int currentLimit;
    private int watermarkLevel;
    private double memoryUsageRatio;
    private long totalTruncations;
    private long totalProcessed;
    
    public String getWatermarkLevelDescription() {
        switch (watermarkLevel) {
            case 2: return "HIGH (>90%)";
            case 1: return "MEDIUM (>80%)";
            default: return "NORMAL (<80%)";
        }
    }
    
    public double getTruncationRatio() {
        return totalProcessed > 0 ? (double) totalTruncations / totalProcessed : 0.0;
    }
}
```

### 4. 水位策略测试
```java
package com.syy.taskflowinsight.core.watermark;

import com.syy.taskflowinsight.core.truncation.AdaptiveTruncationProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveTruncationTest {
    
    private WatermarkMonitor watermarkMonitor;
    private AdaptiveTruncationProcessor processor;
    
    @BeforeEach
    void setUp() {
        watermarkMonitor = new WatermarkMonitor();
        processor = new AdaptiveTruncationProcessor(watermarkMonitor);
    }
    
    @Test
    void testNormalWatermarkTruncation() {
        // 模拟低水位情况
        watermarkMonitor.updateMemoryUsage(100 * 1024 * 1024); // 100MB
        
        assertEquals(8192, watermarkMonitor.getCurrentTruncationLimit());
        assertEquals(0, watermarkMonitor.getCurrentWatermarkLevel());
        
        String longString = "a".repeat(10000);
        String result = processor.truncateWithWatermark(longString);
        
        assertTrue(result.length() <= 8192);
        assertFalse(result.endsWith("... (truncated)"));
    }
    
    @Test
    void testHighWatermark80Truncation() {
        // 模拟80%水位
        Runtime runtime = Runtime.getRuntime();
        long highUsage = (long) (runtime.maxMemory() * 0.85);
        watermarkMonitor.updateMemoryUsage(highUsage);
        
        assertEquals(2048, watermarkMonitor.getCurrentTruncationLimit());
        assertEquals(1, watermarkMonitor.getCurrentWatermarkLevel());
        
        String longString = "b".repeat(5000);
        String result = processor.truncateWithWatermark(longString);
        
        assertTrue(result.length() <= 2048);
        assertTrue(result.endsWith("... (truncated)"));
    }
    
    @Test
    void testHighWatermark90Truncation() {
        // 模拟90%水位
        Runtime runtime = Runtime.getRuntime();
        long veryHighUsage = (long) (runtime.maxMemory() * 0.95);
        watermarkMonitor.updateMemoryUsage(veryHighUsage);
        
        assertEquals(512, watermarkMonitor.getCurrentTruncationLimit());
        assertEquals(2, watermarkMonitor.getCurrentWatermarkLevel());
        
        String longString = "c".repeat(1000);
        String result = processor.truncateWithWatermark(longString);
        
        assertTrue(result.length() <= 512);
    }
    
    @Test
    void testWatermarkRecovery() {
        // 先升高水位
        Runtime runtime = Runtime.getRuntime();
        long highUsage = (long) (runtime.maxMemory() * 0.85);
        watermarkMonitor.updateMemoryUsage(highUsage);
        assertEquals(1, watermarkMonitor.getCurrentWatermarkLevel());
        
        // 再降低水位
        long lowUsage = (long) (runtime.maxMemory() * 0.70);
        watermarkMonitor.updateMemoryUsage(lowUsage);
        assertEquals(0, watermarkMonitor.getCurrentWatermarkLevel());
        assertEquals(8192, watermarkMonitor.getCurrentTruncationLimit());
    }
    
    @Test
    void testSpecialCharacterEscaping() {
        watermarkMonitor.updateMemoryUsage(100 * 1024 * 1024);
        
        String inputWithSpecialChars = "test\nline\twith\"quotes\\backslash";
        String result = processor.truncateWithWatermark(inputWithSpecialChars);
        
        assertTrue(result.contains("\\n"));
        assertTrue(result.contains("\\t"));
        assertTrue(result.contains("\\\""));
        assertTrue(result.contains("\\\\"));
    }
    
    @Test
    void testMemoryPressureSimulation() throws InterruptedException {
        // 模拟内存压力场景
        Runtime runtime = Runtime.getRuntime();
        
        // 逐步增加内存使用
        for (double ratio = 0.5; ratio <= 0.95; ratio += 0.05) {
            long usage = (long) (runtime.maxMemory() * ratio);
            watermarkMonitor.updateMemoryUsage(usage);
            
            int expectedLevel = ratio >= 0.90 ? 2 : (ratio >= 0.80 ? 1 : 0);
            assertEquals(expectedLevel, watermarkMonitor.getCurrentWatermarkLevel());
            
            // 验证截断限制
            int limit = watermarkMonitor.getCurrentTruncationLimit();
            if (ratio >= 0.90) {
                assertEquals(512, limit);
            } else if (ratio >= 0.80) {
                assertEquals(2048, limit);
            } else {
                assertEquals(8192, limit);
            }
            
            Thread.sleep(10); // 模拟时间流逝
        }
    }
    
    @Test
    void testConcurrentWatermarkUpdates() throws InterruptedException {
        // 并发更新水位测试
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    Runtime runtime = Runtime.getRuntime();
                    long usage = (long) (runtime.maxMemory() * (0.5 + threadId * 0.05));
                    watermarkMonitor.updateMemoryUsage(usage);
                    
                    String testString = "thread" + threadId + "iteration" + j;
                    processor.truncateWithWatermark(testString.repeat(100));
                }
            });
        }
        
        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }
        
        // 等待完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        // 验证最终状态一致性
        assertTrue(watermarkMonitor.getCurrentWatermarkLevel() >= 0);
        assertTrue(watermarkMonitor.getCurrentWatermarkLevel() <= 2);
    }
}
```

### 5. 集成测试
```java
package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.core.TFI;
import com.syy.taskflowinsight.core.ChangeTracker;
import com.syy.taskflowinsight.core.watermark.WatermarkMonitor;
import com.syy.taskflowinsight.core.truncation.AdaptiveTruncationProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveTruncationIntegrationTest {
    
    @Test
    void testEndToEndAdaptiveTruncation() {
        // 创建大量长字符串对象模拟内存压力
        List<TestObject> objects = new ArrayList<>();
        
        ChangeTracker tracker = TFI.start("adaptive-truncation-test");
        try {
            // 在不同水位下测试截断行为
            for (int i = 0; i < 1000; i++) {
                TestObject obj = new TestObject();
                obj.setLongValue("x".repeat(20000)); // 长字符串
                
                objects.add(obj);
                tracker.track("obj" + i, obj);
                
                // 修改对象触发变更记录
                obj.setLongValue("y".repeat(15000));
                
                // 模拟内存压力
                if (i % 100 == 0) {
                    System.gc(); // 触发GC
                }
            }
            
            var changes = tracker.getChanges();
            assertFalse(changes.isEmpty());
            
            // 验证变更记录中的值都被正确截断
            changes.forEach(change -> {
                String valueRepr = change.getNewValueRepr();
                assertTrue(valueRepr.length() <= 8192, 
                    "Value representation should be truncated");
            });
            
        } finally {
            TFI.stop();
        }
    }
    
    private static class TestObject {
        private String longValue;
        
        public String getLongValue() { return longValue; }
        public void setLongValue(String longValue) { this.longValue = longValue; }
    }
}
```

## 验证步骤
- [ ] 水位监控机制正常工作
- [ ] 80%水位时截断限制为2048
- [ ] 90%水位时截断限制为512
- [ ] 水位回落后恢复8192限制
- [ ] 不会导致OOM错误
- [ ] 吞吐量影响可接受
- [ ] 并发场景下水位切换正确

## 完成标准（M1阶段）
- [ ] 所有水位策略测试用例通过
- [ ] 策略参数可配置
- [ ] 日志和指标输出清晰可读
- [ ] 行为符合设计文档
- [ ] 性能影响在可接受范围内