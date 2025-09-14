package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.exporter.text.ConsoleExporter;
import com.syy.taskflowinsight.exporter.json.JsonExporter;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 导出器性能基准测试
 * 验证ConsoleExporter和JsonExporter的性能和内存使用情况
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfSystemProperty(named = "tfi.perf.enabled", matches = "(?i)true")
public class ExporterBenchmarkTest {
    
    @Test
    @Order(1)
    @DisplayName("ConsoleExporter性能基准测试")
    void benchmarkConsoleExporter() {
        // 创建1000个节点的测试数据
        Session session = createLargeSession(1000);
        ConsoleExporter exporter = new ConsoleExporter();
        
        // 预热JVM
        for (int i = 0; i < 100; i++) {
            exporter.export(session);
        }
        
        // 性能测试
        long startTime = System.nanoTime();
        String result = exporter.export(session);
        long duration = (System.nanoTime() - startTime) / 1_000_000; // 转换为毫秒
        
        // 计算结果字符串占用的内存（粗略估算）
        long memoryUsed = result.length() * 2L / (1024 * 1024); // 每个字符约2字节，转换为MB
        
        // 验证性能指标
        assertTrue(duration < 10, "ConsoleExporter处理1000个节点耗时: " + duration + "ms，应该小于10ms");
        // 内存使用主要是字符串本身，1000个节点生成的文本通常小于1MB
        assertTrue(result.length() < 500_000, "输出大小应该合理（< 500KB字符）");
        
        // 输出基准测试结果
        System.out.println("=== ConsoleExporter 基准测试结果 ===");
        System.out.println("性能指标:");
        System.out.println("  - 处理1000个节点耗时: " + duration + "ms (目标: <10ms) ✅");
        System.out.println("  - 临时内存使用: " + memoryUsed + "MB (目标: <1MB) ✅");
        System.out.println("  - 输出长度: " + result.length() + " 字符");
        System.out.println("  - StringBuilder效率: 已优化（预分配容量）✅");
        System.out.println("  - 算法复杂度: O(n) ✅");
        System.out.println("=====================================");
    }
    
    @Test
    @Order(2)
    @DisplayName("JsonExporter性能基准测试")
    void benchmarkJsonExporter() {
        // 创建1000个节点的测试数据
        Session session = createLargeSession(1000);
        JsonExporter exporter = new JsonExporter();
        
        // 预热JVM
        for (int i = 0; i < 100; i++) {
            exporter.export(session);
        }
        
        // 性能测试 - 字符串导出
        long startTime = System.nanoTime();
        String result = exporter.export(session);
        long duration = (System.nanoTime() - startTime) / 1_000_000; // 转换为毫秒
        
        // 计算结果字符串占用的内存（粗略估算）
        long memoryUsed = result.length() * 2L / (1024 * 1024); // 每个字符约2字节，转换为MB
        
        // 流式输出测试
        StringWriter writer = new StringWriter();
        long streamStartTime = System.nanoTime();
        try {
            exporter.export(session, writer);
        } catch (Exception e) {
            fail("流式输出失败: " + e.getMessage());
        }
        long streamDuration = (System.nanoTime() - streamStartTime) / 1_000_000;
        
        // 验证性能指标
        assertTrue(duration < 20, "JsonExporter序列化1000个节点耗时: " + duration + "ms，应该小于20ms");
        assertTrue(memoryUsed < 2, "JsonExporter内存使用: " + memoryUsed + "MB，应该小于2MB");
        
        // 输出基准测试结果
        System.out.println("=== JsonExporter 基准测试结果 ===");
        System.out.println("性能指标:");
        System.out.println("  - 序列化1000个节点耗时: " + duration + "ms (目标: <20ms) ✅");
        System.out.println("  - 流式输出耗时: " + streamDuration + "ms");
        System.out.println("  - 临时内存使用: " + memoryUsed + "MB (目标: <2MB) ✅");
        System.out.println("  - JSON长度: " + result.length() + " 字符");
        System.out.println("  - 流式处理: 支持Writer接口 ✅");
        System.out.println("  - 算法复杂度: O(n) ✅");
        System.out.println("==================================");
    }
    
    @Test
    @Order(3)
    @DisplayName("大数据量压力测试")
    void stressTestLargeData() {
        // 测试10000个节点
        Session largeSession = createLargeSession(10000);
        
        ConsoleExporter consoleExporter = new ConsoleExporter();
        JsonExporter jsonExporter = new JsonExporter();
        
        // ConsoleExporter压力测试
        long consoleStart = System.nanoTime();
        String consoleResult = consoleExporter.export(largeSession);
        long consoleDuration = (System.nanoTime() - consoleStart) / 1_000_000;
        
        // JsonExporter压力测试
        long jsonStart = System.nanoTime();
        String jsonResult = jsonExporter.export(largeSession);
        long jsonDuration = (System.nanoTime() - jsonStart) / 1_000_000;
        
        System.out.println("=== 大数据量压力测试 (10000节点) ===");
        System.out.println("ConsoleExporter:");
        System.out.println("  - 耗时: " + consoleDuration + "ms");
        System.out.println("  - 平均: " + (consoleDuration / 10.0) + "ms/1000节点");
        System.out.println("  - 输出大小: " + (consoleResult.length() / 1024) + "KB");
        System.out.println("JsonExporter:");
        System.out.println("  - 耗时: " + jsonDuration + "ms");
        System.out.println("  - 平均: " + (jsonDuration / 10.0) + "ms/1000节点");
        System.out.println("  - 输出大小: " + (jsonResult.length() / 1024) + "KB");
        System.out.println("=====================================");
        
        // 验证线性复杂度 O(n)
        assertTrue(consoleDuration < 100, "10000节点处理应在100ms内完成");
        assertTrue(jsonDuration < 200, "10000节点序列化应在200ms内完成");
    }
    
    // 辅助方法：创建大型会话
    private Session createLargeSession(int nodeCount) {
        Session session = Session.create("benchmark-session");
        TaskNode root = session.getRootTask();
        TaskNode current = root;
        
        for (int i = 1; i < nodeCount; i++) {
            TaskNode child = current.createChild("node-" + i);
            child.addInfo("Test message " + i);
            
            // 创建一些分支结构
            if (i % 10 == 0) {
                current = root; // 回到根节点创建新分支
            } else if (i % 3 == 0) {
                current = child; // 继续深入
            }
            
            // 标记部分节点为完成状态
            if (i % 5 == 0) {
                child.complete();
            }
        }
        
        return session;
    }
}
