# TFI-MVP-250 性能基准测试

## 任务概述
使用JMH或微基准测试验证变更追踪的性能指标，确保写路径P95 CPU ≤ 3%，2字段P95 ≤ 200μs的目标。

## 核心目标
- [ ] 实现JMH基准测试套件
- [ ] 覆盖核心性能场景（2/20字段，并发8/16线程）
- [ ] 输出延迟分位数和CPU观测
- [ ] 提供性能基准报告
- [ ] 验证是否达成性能目标

## 实现清单

### 1. JMH基准测试主类
```java
package com.syy.taskflowinsight.benchmark;

import com.syy.taskflowinsight.core.TFI;
import com.syy.taskflowinsight.core.ChangeTracker;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class ChangeTrackingBenchmark {
    
    private TestOrder order2Fields;
    private TestOrder order20Fields;
    
    @Setup
    public void setUp() {
        order2Fields = new TestOrder("ORDER-001", "PENDING");
        order20Fields = createOrder20Fields();
    }
    
    @Benchmark
    @Threads(1)
    public void benchmark_2Fields_SingleThread(Blackhole bh) {
        ChangeTracker tracker = TFI.start("benchmark-2f-1t");
        try {
            tracker.track("order", order2Fields);
            
            // 模拟业务修改
            order2Fields.setStatus("PAID");
            order2Fields.setAmount(100.0);
            
            bh.consume(tracker.getChanges());
        } finally {
            TFI.stop();
        }
    }
    
    @Benchmark
    @Threads(8)
    public void benchmark_2Fields_8Threads(Blackhole bh) {
        ChangeTracker tracker = TFI.start("benchmark-2f-8t");
        try {
            tracker.track("order", order2Fields);
            
            order2Fields.setStatus("PROCESSING");
            order2Fields.setAmount(200.0);
            
            bh.consume(tracker.getChanges());
        } finally {
            TFI.stop();
        }
    }
    
    @Benchmark
    @Threads(16)
    public void benchmark_2Fields_16Threads(Blackhole bh) {
        ChangeTracker tracker = TFI.start("benchmark-2f-16t");
        try {
            tracker.track("order", order2Fields);
            
            order2Fields.setStatus("COMPLETED");
            order2Fields.setAmount(300.0);
            
            bh.consume(tracker.getChanges());
        } finally {
            TFI.stop();
        }
    }
    
    @Benchmark
    @Threads(1)
    public void benchmark_20Fields_SingleThread(Blackhole bh) {
        ChangeTracker tracker = TFI.start("benchmark-20f-1t");
        try {
            tracker.track("order", order20Fields);
            
            // 修改20个字段
            modifyAllFields(order20Fields);
            
            bh.consume(tracker.getChanges());
        } finally {
            TFI.stop();
        }
    }
    
    @Benchmark
    @Threads(8)
    public void benchmark_20Fields_8Threads(Blackhole bh) {
        ChangeTracker tracker = TFI.start("benchmark-20f-8t");
        try {
            tracker.track("order", order20Fields);
            modifyAllFields(order20Fields);
            bh.consume(tracker.getChanges());
        } finally {
            TFI.stop();
        }
    }
    
    @Benchmark
    @Threads(16)  
    public void benchmark_20Fields_16Threads(Blackhole bh) {
        ChangeTracker tracker = TFI.start("benchmark-20f-16t");
        try {
            tracker.track("order", order20Fields);
            modifyAllFields(order20Fields);
            bh.consume(tracker.getChanges());
        } finally {
            TFI.stop();
        }
    }
    
    @Benchmark
    public void benchmark_100Iterations_2Fields(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            ChangeTracker tracker = TFI.start("benchmark-100-" + i);
            try {
                tracker.track("order", order2Fields);
                order2Fields.setStatus("ITER-" + i);
                order2Fields.setAmount(i * 10.0);
                bh.consume(tracker.getChanges());
            } finally {
                TFI.stop();
            }
        }
    }
    
    private TestOrder createOrder20Fields() {
        return new TestOrder20Fields();
    }
    
    private void modifyAllFields(TestOrder order) {
        if (order instanceof TestOrder20Fields) {
            TestOrder20Fields o = (TestOrder20Fields) order;
            o.setField1("modified1");
            o.setField2("modified2");
            // ... 修改所有20个字段
            o.setField20("modified20");
        }
    }
    
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(ChangeTrackingBenchmark.class.getSimpleName())
                .resultFormat(ResultFormatType.JSON)
                .result("benchmark-results.json")
                .build();
                
        new Runner(opt).run();
    }
}
```

### 2. 测试数据类
```java
package com.syy.taskflowinsight.benchmark;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor 
@NoArgsConstructor
public class TestOrder {
    private String orderId;
    private String status;
    private Double amount = 0.0;
}

@Data
public class TestOrder20Fields extends TestOrder {
    private String field1 = "value1";
    private String field2 = "value2";
    private String field3 = "value3";
    private String field4 = "value4";
    private String field5 = "value5";
    private String field6 = "value6";
    private String field7 = "value7";
    private String field8 = "value8";
    private String field9 = "value9";
    private String field10 = "value10";
    private String field11 = "value11";
    private String field12 = "value12";
    private String field13 = "value13";
    private String field14 = "value14";
    private String field15 = "value15";
    private String field16 = "value16";
    private String field17 = "value17";
    private String field18 = "value18";
    private String field19 = "value19";
    private String field20 = "value20";
}
```

### 3. 微基准测试（JUnit备选）
```java
package com.syy.taskflowinsight.benchmark;

import com.syy.taskflowinsight.core.TFI;
import com.syy.taskflowinsight.core.ChangeTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class MicroBenchmarkTest {
    
    private TestOrder order;
    
    @BeforeEach
    void setUp() {
        order = new TestOrder("ORDER-001", "PENDING");
    }
    
    @RepeatedTest(10)
    void microBenchmark_2Fields_Performance() {
        long startTime = System.nanoTime();
        
        ChangeTracker tracker = TFI.start("micro-benchmark");
        try {
            tracker.track("order", order);
            
            order.setStatus("PAID");
            order.setAmount(100.0);
            
            tracker.getChanges();
        } finally {
            TFI.stop();
        }
        
        long duration = System.nanoTime() - startTime;
        long microseconds = duration / 1000;
        
        // 验证P95目标：≤ 200μs
        assertTrue(microseconds <= 200, 
            "Duration " + microseconds + "μs exceeds 200μs target");
        
        System.out.printf("2 fields tracking took: %d μs%n", microseconds);
    }
    
    @Test
    void concurrentBenchmark_8Threads() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<Long>> futures = new ArrayList<>();
        
        for (int i = 0; i < 8; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                long startTime = System.nanoTime();
                
                ChangeTracker tracker = TFI.start("concurrent-" + threadId);
                try {
                    TestOrder localOrder = new TestOrder("ORDER-" + threadId, "PENDING");
                    tracker.track("order", localOrder);
                    
                    localOrder.setStatus("PROCESSING-" + threadId);
                    localOrder.setAmount(threadId * 100.0);
                    
                    tracker.getChanges();
                    return (System.nanoTime() - startTime) / 1000; // microseconds
                } finally {
                    TFI.stop();
                }
            }));
        }
        
        List<Long> durations = new ArrayList<>();
        for (Future<Long> future : futures) {
            durations.add(future.get());
        }
        
        executor.shutdown();
        
        // 计算P95
        durations.sort(Long::compareTo);
        int p95Index = (int) (durations.size() * 0.95);
        long p95Duration = durations.get(p95Index);
        
        System.out.printf("Concurrent P95 duration: %d μs%n", p95Duration);
        assertTrue(p95Duration <= 200, "P95 duration exceeds 200μs target");
    }
    
    @Test
    void benchmark_100Iterations() {
        List<Long> durations = new ArrayList<>();
        
        for (int i = 0; i < 100; i++) {
            long startTime = System.nanoTime();
            
            ChangeTracker tracker = TFI.start("iteration-" + i);
            try {
                TestOrder iterOrder = new TestOrder("ORDER-" + i, "PENDING");
                tracker.track("order", iterOrder);
                
                iterOrder.setStatus("COMPLETED-" + i);
                iterOrder.setAmount(i * 50.0);
                
                tracker.getChanges();
                
                long duration = (System.nanoTime() - startTime) / 1000;
                durations.add(duration);
            } finally {
                TFI.stop();
            }
        }
        
        // 统计分析
        durations.sort(Long::compareTo);
        long p50 = durations.get(49);
        long p95 = durations.get(94);
        long p99 = durations.get(98);
        
        System.out.printf("100 iterations - P50: %d μs, P95: %d μs, P99: %d μs%n", 
            p50, p95, p99);
        
        assertTrue(p95 <= 200, "P95 duration exceeds target");
    }
}
```

### 4. Maven配置更新
```xml
<!-- 在pom.xml中添加JMH依赖 -->
<dependencies>
    <!-- JMH Core -->
    <dependency>
        <groupId>org.openjdk.jmh</groupId>
        <artifactId>jmh-core</artifactId>
        <version>1.37</version>
        <scope>test</scope>
    </dependency>
    
    <!-- JMH Annotation Processor -->
    <dependency>
        <groupId>org.openjdk.jmh</groupId>
        <artifactId>jmh-generator-annprocess</artifactId>
        <version>1.37</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- JMH Plugin -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.4.1</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                    <configuration>
                        <finalName>benchmarks</finalName>
                        <transformers>
                            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                <mainClass>org.openjdk.jmh.Main</mainClass>
                            </transformer>
                        </transformers>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 5. 基准测试运行脚本
```bash
#!/bin/bash
# run-benchmarks.sh

echo "Building project..."
./mvnw clean compile test-compile

echo "Running JMH benchmarks..."
java -jar target/benchmarks.jar -rf json -rff benchmark-results.json

echo "Running micro benchmarks..."
./mvnw test -Dtest=MicroBenchmarkTest

echo "Benchmark results saved to benchmark-results.json"
```

## 验证步骤
- [ ] JMH测试在本地环境运行稳定
- [ ] CI环境中测试结果可复现
- [ ] 2字段场景P95延迟 ≤ 200μs
- [ ] 20字段场景性能表现可接受
- [ ] 并发测试无异常和竞争条件
- [ ] 生成完整的性能基准报告

## 完成标准
- [ ] 提交完整基准测试报告（markdown + 截图）
- [ ] 2字段P95延迟符合 ≤ 200μs 目标
- [ ] CPU占比评估报告（非硬性门槛）
- [ ] 如未达成目标，提供改进计划和Issue
- [ ] 文档化运行命令和环境配置