package com.syy.taskflowinsight.tracking.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;

/**
 * 资源监控器
 * 
 * 监控系统资源使用情况：
 * - 内存使用率（Heap）
 * - 可用内存
 * - 线程数量
 * - CPU使用率（通过线程时间估算）
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
@Component
@ConditionalOnProperty(prefix = "tfi.change-tracking.degradation", name = "enabled", havingValue = "true")
public class ResourceMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(ResourceMonitor.class);
    
    // JMX Bean 引用
    private final MemoryMXBean memoryMXBean;
    private final ThreadMXBean threadMXBean;
    private final Runtime runtime;
    
    // CPU 使用率计算相关
    private volatile long lastCpuTime = 0;
    private volatile long lastSystemTime = 0;
    
    public ResourceMonitor() {
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.runtime = Runtime.getRuntime();
        
        // 初始化CPU时间基线
        initializeCpuBaseline();
    }
    
    /**
     * 获取当前内存使用率（百分比）
     * 
     * @return 内存使用率 0.0-100.0
     */
    public double getMemoryUsagePercent() {
        try {
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            long used = heapUsage.getUsed();
            long max = heapUsage.getMax();
            
            if (max <= 0) {
                // 如果max为-1（无限制），使用committed作为基准
                max = heapUsage.getCommitted();
            }
            
            return max > 0 ? (double) used / max * 100.0 : 0.0;
        } catch (Exception e) {
            logger.warn("Failed to get memory usage", e);
            return 0.0;
        }
    }
    
    /**
     * 获取可用内存（MB）
     * 
     * @return 可用内存大小（MB）
     */
    public long getAvailableMemoryMB() {
        try {
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            long max = heapUsage.getMax();
            long used = heapUsage.getUsed();
            
            if (max <= 0) {
                // 使用运行时可用内存作为fallback
                return runtime.freeMemory() / (1024 * 1024);
            }
            
            return Math.max(0, (max - used) / (1024 * 1024));
        } catch (Exception e) {
            logger.warn("Failed to get available memory", e);
            return 0;
        }
    }
    
    /**
     * 获取活跃线程数
     * 
     * @return 当前活跃线程数
     */
    public int getActiveThreadCount() {
        return threadMXBean.getThreadCount();
    }
    
    /**
     * 获取CPU使用率估算（百分比）
     * 注意：这是一个简化的估算，基于线程CPU时间变化
     * 
     * @return CPU使用率 0.0-100.0
     */
    public double getCpuUsagePercent() {
        try {
            if (!threadMXBean.isCurrentThreadCpuTimeSupported()) {
                return 0.0;
            }
            
            long currentCpuTime = getCurrentProcessCpuTime();
            long currentSystemTime = System.nanoTime();
            
            if (lastCpuTime == 0 || lastSystemTime == 0) {
                // 第一次调用，无法计算，更新基线
                lastCpuTime = currentCpuTime;
                lastSystemTime = currentSystemTime;
                return 0.0;
            }
            
            long cpuTimeDiff = currentCpuTime - lastCpuTime;
            long systemTimeDiff = currentSystemTime - lastSystemTime;
            
            // 更新基线
            lastCpuTime = currentCpuTime;
            lastSystemTime = currentSystemTime;
            
            if (systemTimeDiff <= 0) {
                return 0.0;
            }
            
            // 计算CPU使用率，考虑可用处理器数量
            int availableProcessors = runtime.availableProcessors();
            double cpuUsage = (double) cpuTimeDiff / systemTimeDiff * 100.0 / availableProcessors;
            
            return Math.max(0.0, Math.min(100.0, cpuUsage));
        } catch (Exception e) {
            logger.debug("Failed to calculate CPU usage", e);
            return 0.0;
        }
    }
    
    /**
     * 获取可用处理器数量
     * 
     * @return 可用处理器数
     */
    public int getAvailableProcessors() {
        return runtime.availableProcessors();
    }
    
    /**
     * 检查是否存在内存压力
     * 
     * @param threshold 内存使用率阈值（百分比）
     * @return true 如果内存使用率超过阈值
     */
    public boolean isMemoryPressureHigh(double threshold) {
        return getMemoryUsagePercent() >= threshold;
    }
    
    /**
     * 检查是否应该触发降级
     * 基于内存和CPU使用率的综合判断
     * 
     * @return true 如果应该触发降级
     */
    public boolean shouldTriggerDegradation() {
        double memoryUsage = getMemoryUsagePercent();
        double cpuUsage = getCpuUsagePercent();
        
        // 内存使用率超过80%或CPU使用率超过90%
        return memoryUsage >= 80.0 || cpuUsage >= 90.0;
    }
    
    /**
     * 获取资源使用摘要
     * 
     * @return 资源使用统计信息
     */
    public String getResourceSummary() {
        return String.format(
            "ResourceStats{memory=%.1f%%, availableMem=%dMB, threads=%d, cpu=%.1f%%, processors=%d}",
            getMemoryUsagePercent(),
            getAvailableMemoryMB(),
            getActiveThreadCount(),
            getCpuUsagePercent(),
            getAvailableProcessors()
        );
    }
    
    /**
     * 初始化CPU时间基线
     */
    private void initializeCpuBaseline() {
        try {
            lastCpuTime = getCurrentProcessCpuTime();
            lastSystemTime = System.nanoTime();
        } catch (Exception e) {
            logger.debug("Failed to initialize CPU baseline", e);
        }
    }
    
    /**
     * 获取当前进程的CPU时间（所有线程总和）
     * 这是一个简化实现，实际生产环境可能需要更精确的方法
     */
    private long getCurrentProcessCpuTime() {
        // 简化实现：获取主线程的CPU时间作为代表
        return threadMXBean.getCurrentThreadCpuTime();
    }
}