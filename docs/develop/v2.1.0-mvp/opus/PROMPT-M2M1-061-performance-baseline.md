# PROMPT-M2M1-061-PerformanceBaseline 开发提示词

## 1) SYSTEM
你是**资深 Java 开发工程师**与**AI 结对编程引导者**。你需要基于下述"上下文与证据"，**按步骤**完成实现并给出**可提交的变更**（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../task/v2.1.0-vip/testing-quality/M2M1-061-PerformanceBaseline.md
- 相关代码：
  - src/test/java/com/syy/taskflowinsight/performance#性能测试基础
  - src/main/java/com/syy/taskflowinsight/tracking#待测试模块
- 性能测试框架：
  - JMH (Java Microbenchmark Harness) 1.37
  - async-profiler 2.9
  - JFR (Java Flight Recorder)
- 相关配置：
  - pom.xml: jmh-core, jmh-generator-annprocess
  - jmh.properties: 基准测试配置
- 工程操作规范：../../develop/开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：建立可复现的性能基线与不回退验证机制，护栏为"<5% CPU + 不劣化既有基线"
- 技术目标：
  - 建立 JMH 性能基准测试套件
  - 实现自动化性能回归检测
  - 生成性能报告和趋势分析
  - 集成 CI/CD 性能门禁

## 4) SCOPE
- In Scope（当次实现必做）：
  - [ ] 创建 JMH 基准测试框架
  - [ ] 实现核心模块性能测试
  - [ ] 建立性能基线数据
  - [ ] 实现性能回归检测
  - [ ] 生成性能报告（JSON/Markdown）
  - [ ] 集成 async-profiler 热点分析
  - [ ] 配置 CI 性能门禁
- Out of Scope（排除项）：
  - [ ] 分布式性能测试
  - [ ] 负载测试
  - [ ] 压力测试

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 性能测试基础设施：
```java
// PerformanceBenchmarkBase.java
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {
    "-Xms2G", 
    "-Xmx2G", 
    "-XX:+UseG1GC",
    "-XX:+UnlockCommercialFeatures",
    "-XX:+FlightRecorder",
    "-XX:StartFlightRecording=duration=60s,filename=./jfr/benchmark.jfr"
})
public abstract class PerformanceBenchmarkBase {
    
    protected static final int SMALL_SIZE = 10;
    protected static final int MEDIUM_SIZE = 100;
    protected static final int LARGE_SIZE = 1000;
    protected static final int XLARGE_SIZE = 10000;
    
    @Setup(Level.Trial)
    public void setUp() {
        System.out.println("=== Performance Benchmark Setup ===");
        System.out.println("JVM: " + System.getProperty("java.version"));
        System.out.println("OS: " + System.getProperty("os.name"));
        System.out.println("Cores: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Memory: " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + "MB");
        recordEnvironment();
    }
    
    @TearDown(Level.Trial)
    public void tearDown() {
        System.gc();
        System.out.println("=== Benchmark Completed ===");
    }
    
    private void recordEnvironment() {
        PerformanceMetadata metadata = PerformanceMetadata.builder()
            .timestamp(Instant.now())
            .gitCommit(getGitCommit())
            .jvmVersion(System.getProperty("java.version"))
            .osName(System.getProperty("os.name"))
            .cpuCores(Runtime.getRuntime().availableProcessors())
            .maxMemory(Runtime.getRuntime().maxMemory())
            .build();
        
        saveMetadata(metadata);
    }
}

// ObjectSnapshotBenchmark.java
@State(Scope.Benchmark)
public class ObjectSnapshotBenchmark extends PerformanceBenchmarkBase {
    
    private ObjectSnapshotDeep snapshotDeep;
    private SnapshotConfig config;
    private User simpleUser;
    private User complexUser;
    private User deeplyNestedUser;
    
    @Setup(Level.Trial)
    public void setUp() {
        super.setUp();
        
        config = new SnapshotConfig();
        config.setMaxDepth(3);
        snapshotDeep = new ObjectSnapshotDeep();
        
        // 准备测试数据
        simpleUser = TestDataGenerator.generateSimpleUser();
        complexUser = TestDataGenerator.generateComplexUser(MEDIUM_SIZE);
        deeplyNestedUser = TestDataGenerator.generateDeepUser(5, LARGE_SIZE);
    }
    
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public Map<String, Object> benchmarkSimpleObject() {
        return snapshotDeep.captureDeep(simpleUser, config);
    }
    
    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public Map<String, Object> benchmarkComplexObject() {
        return snapshotDeep.captureDeep(complexUser, config);
    }
    
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Map<String, Object> benchmarkLargeObject() {
        return snapshotDeep.captureDeep(deeplyNestedUser, config);
    }
    
    @Benchmark
    @OperationsPerInvocation(100)
    public void benchmarkThroughput(Blackhole blackhole) {
        for (int i = 0; i < 100; i++) {
            blackhole.consume(snapshotDeep.captureDeep(simpleUser, config));
        }
    }
}

// PathMatcherCacheBenchmark.java
@State(Scope.Benchmark)
public class PathMatcherCacheBenchmark extends PerformanceBenchmarkBase {
    
    private PathMatcherCache cache;
    private List<String> patterns;
    private List<String> paths;
    
    @Param({"10", "100", "1000"})
    private int cacheSize;
    
    @Param({"0.5", "0.8", "0.95"})
    private double hitRate;
    
    @Setup(Level.Trial)
    public void setUp() {
        super.setUp();
        
        cache = new PathMatcherCache(cacheSize);
        patterns = generatePatterns(cacheSize);
        paths = generatePaths(1000, hitRate);
        
        // 预热缓存
        patterns.forEach(cache::precompile);
    }
    
    @Benchmark
    public boolean benchmarkCacheHit() {
        String path = paths.get(ThreadLocalRandom.current().nextInt(paths.size()));
        return cache.matches(patterns.get(0), path);
    }
    
    @Benchmark
    public boolean benchmarkCacheMiss() {
        String pattern = "never.cached.pattern." + ThreadLocalRandom.current().nextInt();
        return cache.matches(pattern, "some/path");
    }
}

// PerformanceRegression.java
public class PerformanceRegression {
    private static final double TOLERANCE = 0.05; // 5% 容差
    
    public static void main(String[] args) throws Exception {
        // 运行基准测试
        Options opt = new OptionsBuilder()
            .include(ObjectSnapshotBenchmark.class.getSimpleName())
            .include(PathMatcherCacheBenchmark.class.getSimpleName())
            .resultFormat(ResultFormatType.JSON)
            .result("target/jmh-results.json")
            .build();
        
        Collection<RunResult> results = new Runner(opt).run();
        
        // 加载基线数据
        PerformanceBaseline baseline = loadBaseline();
        
        // 比较结果
        RegressionReport report = compareWithBaseline(results, baseline);
        
        // 生成报告
        generateReport(report);
        
        // 检查回归
        if (report.hasRegression()) {
            System.err.println("Performance regression detected!");
            System.err.println(report.getRegressionSummary());
            System.exit(1);
        }
        
        // 更新基线（如果改进）
        if (report.hasImprovement() && isBaselineUpdate()) {
            updateBaseline(results);
            System.out.println("Performance baseline updated with improvements");
        }
    }
    
    private static RegressionReport compareWithBaseline(
            Collection<RunResult> current, PerformanceBaseline baseline) {
        
        RegressionReport report = new RegressionReport();
        
        for (RunResult result : current) {
            String benchmark = result.getParams().getBenchmark();
            double currentScore = result.getPrimaryResult().getScore();
            
            BaselineEntry baselineEntry = baseline.get(benchmark);
            if (baselineEntry != null) {
                double baselineScore = baselineEntry.getScore();
                double change = (currentScore - baselineScore) / baselineScore;
                
                if (change > TOLERANCE) {
                    report.addRegression(benchmark, baselineScore, currentScore, change);
                } else if (change < -TOLERANCE) {
                    report.addImprovement(benchmark, baselineScore, currentScore, change);
                } else {
                    report.addStable(benchmark, baselineScore, currentScore, change);
                }
            } else {
                report.addNew(benchmark, currentScore);
            }
        }
        
        return report;
    }
}

// PerformanceProfiler.java
public class PerformanceProfiler {
    
    @Test
    @EnabledIfSystemProperty(named = "perf.profile", matches = "true")
    public void profileObjectSnapshot() throws Exception {
        // 启动 async-profiler
        AsyncProfiler profiler = AsyncProfiler.getInstance();
        profiler.start("cpu,alloc", 1_000_000); // 1ms 采样间隔
        
        try {
            // 执行性能测试
            ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep();
            User complexUser = TestDataGenerator.generateComplexUser(1000);
            
            long startTime = System.nanoTime();
            for (int i = 0; i < 10000; i++) {
                snapshot.captureDeep(complexUser, new SnapshotConfig());
            }
            long duration = System.nanoTime() - startTime;
            
            System.out.printf("Total time: %.2f ms%n", duration / 1_000_000.0);
            System.out.printf("Per operation: %.2f μs%n", duration / 10_000_000.0);
            
        } finally {
            // 停止并保存profile
            profiler.stop();
            profiler.dumpFlat(new File("target/profile-snapshot.txt"));
            profiler.dumpTraces(new File("target/profile-snapshot.html"));
            profiler.dumpCollapsed(new File("target/profile-snapshot.collapsed"));
        }
        
        // 生成火焰图
        generateFlameGraph("target/profile-snapshot.collapsed", 
                          "target/flamegraph-snapshot.svg");
    }
    
    private void generateFlameGraph(String collapsed, String output) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "flamegraph.pl",
            "--title", "Object Snapshot CPU Profile",
            "--width", "1200",
            collapsed
        );
        pb.redirectOutput(new File(output));
        Process process = pb.start();
        process.waitFor();
    }
}

// PerformanceReport.java
@Data
@Builder
public class PerformanceReport {
    private String version;
    private Instant timestamp;
    private EnvironmentInfo environment;
    private List<BenchmarkResult> benchmarks;
    private RegressionSummary regression;
    private String markdownReport;
    
    public void generateMarkdown() {
        StringBuilder md = new StringBuilder();
        
        md.append("# Performance Test Report\n\n");
        md.append("## Environment\n");
        md.append(String.format("- Version: %s\n", version));
        md.append(String.format("- Timestamp: %s\n", timestamp));
        md.append(String.format("- JVM: %s\n", environment.getJvmVersion()));
        md.append(String.format("- OS: %s\n", environment.getOsName()));
        md.append(String.format("- CPU Cores: %d\n", environment.getCpuCores()));
        md.append("\n");
        
        md.append("## Benchmark Results\n\n");
        md.append("| Benchmark | Score | Unit | Baseline | Change | Status |\n");
        md.append("|-----------|-------|------|----------|--------|--------|\n");
        
        for (BenchmarkResult result : benchmarks) {
            String status = getStatusEmoji(result.getChangePercent());
            md.append(String.format("| %s | %.2f | %s | %.2f | %+.1f%% | %s |\n",
                result.getName(),
                result.getScore(),
                result.getUnit(),
                result.getBaseline(),
                result.getChangePercent(),
                status
            ));
        }
        
        md.append("\n## Performance Guards\n");
        md.append(String.format("- CPU Usage: %.1f%% (limit: 5%%)\n", getCpuUsage()));
        md.append(String.format("- Memory: %d MB (limit: 100 MB)\n", getMemoryUsage()));
        md.append(String.format("- P95 Latency: %.2f ms (limit: 50 ms)\n", getP95Latency()));
        
        this.markdownReport = md.toString();
    }
    
    private String getStatusEmoji(double change) {
        if (change > 5) return "❌ Regression";
        if (change < -5) return "✅ Improved";
        return "✔️ Stable";
    }
}
```

2. Maven 配置：
```xml
<dependencies>
    <!-- JMH -->
    <dependency>
        <groupId>org.openjdk.jmh</groupId>
        <artifactId>jmh-core</artifactId>
        <version>1.37</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.openjdk.jmh</groupId>
        <artifactId>jmh-generator-annprocess</artifactId>
        <version>1.37</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>exec-maven-plugin</artifactId>
            <executions>
                <execution>
                    <id>run-benchmarks</id>
                    <phase>integration-test</phase>
                    <goals>
                        <goal>exec</goal>
                    </goals>
                    <configuration>
                        <executable>java</executable>
                        <arguments>
                            <argument>-cp</argument>
                            <classpath/>
                            <argument>com.syy.taskflowinsight.performance.PerformanceRegression</argument>
                        </arguments>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

3. CI/CD 集成：
```yaml
# .github/workflows/performance.yml
name: Performance Tests

on:
  pull_request:
    branches: [ main, develop ]
  schedule:
    - cron: '0 2 * * *' # 每天凌晨2点

jobs:
  performance:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Cache Maven dependencies
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
    
    - name: Run Performance Tests
      run: |
        mvn clean compile test-compile
        mvn exec:java -Dexec.mainClass="com.syy.taskflowinsight.performance.PerformanceRegression"
    
    - name: Upload Performance Report
      uses: actions/upload-artifact@v3
      with:
        name: performance-report
        path: |
          target/jmh-results.json
          target/performance-report.md
          target/profile-*.txt
          target/flamegraph-*.svg
    
    - name: Comment PR with Performance Results
      if: github.event_name == 'pull_request'
      uses: actions/github-script@v6
      with:
        script: |
          const fs = require('fs');
          const report = fs.readFileSync('target/performance-report.md', 'utf8');
          github.rest.issues.createComment({
            issue_number: context.issue.number,
            owner: context.repo.owner,
            repo: context.repo.repo,
            body: report
          });
    
    - name: Fail on Regression
      run: |
        if [ -f target/regression-detected ]; then
          echo "Performance regression detected!"
          exit 1
        fi
```

## 6) DELIVERABLES（输出必须包含）
- 代码改动：
  - 新文件：性能测试基类、各模块Benchmark类
  - 新文件：性能回归检测器
  - 新文件：性能报告生成器
  - 配置：pom.xml JMH依赖
  - CI配置：GitHub Actions workflow
- 测试产出：
  - 性能基线数据（JSON）
  - 性能报告（Markdown/HTML）
  - 火焰图（SVG）
  - JFR记录文件

## 7) API & MODELS（必须具体化）
- JMH注解使用：
  - @Benchmark - 标记基准测试方法
  - @BenchmarkMode - 测试模式(吞吐量/平均时间/采样)
  - @OutputTimeUnit - 输出时间单位
  - @Warmup/@Measurement - 预热和测量配置
  - @Fork - JVM fork配置
- 性能指标：
  - Throughput (ops/sec)
  - Average Time (μs/op)
  - Sample Time (percentiles)
  - Allocation Rate (MB/sec)

## 8) DATA & STORAGE
- 基线存储：target/performance-baseline.json
- 测试结果：target/jmh-results.json
- Profile数据：target/profile-*.jfr
- 历史趋势：performance-history.db (SQLite)

## 9) PERFORMANCE & RELIABILITY
- 性能目标：
  - ObjectSnapshot: < 50ms for 1000 fields
  - PathMatcher: < 0.1ms per match
  - CollectionSummary: < 10ms for 10000 items
  - CPU占用: < 5% 增量
  - 内存: < 100MB 增量
- 测试稳定性：
  - 5次预热 + 5次测量
  - 2个JVM fork
  - 统计误差 < 5%

## 10) TEST PLAN（可运行、可断言）
- 基准测试：
  - [ ] 覆盖所有核心模块
  - [ ] 小/中/大数据集
  - [ ] 冷启动 vs 预热后
  - [ ] 并发 vs 单线程
- 回归检测：
  - [ ] 自动比较基线
  - [ ] 5% 容差阈值
  - [ ] CI/CD 集成
- Profile分析：
  - [ ] CPU热点
  - [ ] 内存分配
  - [ ] 锁竞争

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 基线建立完成
- [ ] 回归检测有效
- [ ] 报告自动生成
- [ ] CI集成成功
- [ ] 性能目标达成

## 12) RISKS & MITIGATIONS
- 环境差异：不同机器性能不同 → 记录环境元数据
- 测试抖动：结果不稳定 → 增加预热和测量次数
- 基线漂移：逐渐劣化 → 定期review和重置基线
- CI资源：测试耗时长 → 并行执行 + 增量测试

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 建议使用 JMH Suite 组织多个benchmark

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 问题1：性能基线的更新策略？
  - 责任人：架构组
  - 期限：首次运行前
  - 所需：基线管理流程
- [ ] 问题2：是否需要分环境基线？
  - 责任人：DevOps
  - 期限：部署前
  - 所需：环境差异评估