package com.syy.taskflowinsight.perf;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.function.Supplier;

/**
 * TFI 性能基线与基准采样
 *
 * <p>本测试在 CI 中自动禁用，仅用于本地手动性能基线采集。</p>
 *
 * <p><b>测试场景：</b></p>
 * <ul>
 *   <li>List(10k): 10000 个元素，修改 1 个元素</li>
 *   <li>Map(2k): 2000 个键，修改 1 个键</li>
 * </ul>
 *
 * <p><b>性能指标：</b></p>
 * <ul>
 *   <li>P50/P95/avg: 排序后取中位数/95分位/平均值</li>
 *   <li>GC: 采样期间的 GC 次数增量</li>
 *   <li>Heap: 堆内存使用增量（MB）</li>
 *   <li>门面开销: (TFI - SVC) / SVC * 100%</li>
 * </ul>
 *
 * <p><b>运行方式：</b></p>
 * <pre>
 * # 本地执行（去掉 CI 环境变量）
 * ./mvnw test -Dtest=TFIPerformanceBenchmarks
 *
 * # CI 自动跳过（设置 CI=true 时）
 * CI=true ./mvnw test -Dtest=TFIPerformanceBenchmarks
 * </pre>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 */
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
@EnabledIfSystemProperty(named = "tfi.runPerfTests", matches = "true")
class TFIPerformanceBenchmarks {

    private static final int WARMUP_ROUNDS = 10;
    private static final int SAMPLE_ROUNDS = 30;

    private static CompareService compareService;

    @BeforeAll
    static void setup() {
        System.out.println("=".repeat(80));
        System.out.println("TFI 性能基线与基准采样");
        System.out.println("=".repeat(80));
        System.out.println("测试环境:");
        System.out.println("  JDK: " + System.getProperty("java.version"));
        System.out.println("  OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("  CPU: " + Runtime.getRuntime().availableProcessors() + " cores");
        System.out.println("  Heap: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB");
        System.out.println();

        // 初始化 CompareService（用于对比测试）
        compareService = ensureCompareService();
    }

    @Test
    void benchmarkList10k() {
        System.out.println("=".repeat(80));
        System.out.println("场景1: List<String>(10k) 性能基线");
        System.out.println("=".repeat(80));

        // 准备测试数据
        List<String> list1 = createList(10000);
        List<String> list2 = new ArrayList<>(list1);
        list2.set(5000, "MODIFIED_ELEMENT"); // 修改中间元素

        // TFI.compare 基准
        System.out.println("\n▶ TFI.compare 基准:");
        BenchmarkResult tfiResult = runBenchmark(() -> TFI.compare(list1, list2));
        printResult(tfiResult);

        // CompareService.compare 基准
        System.out.println("\n▶ CompareService.compare 基准:");
        BenchmarkResult svcResult = runBenchmark(() -> compareService.compare(list1, list2, CompareOptions.DEFAULT));
        printResult(svcResult);

        // 门面开销分析
        System.out.println("\n▶ 门面开销分析:");
        double overhead = calculateOverhead(tfiResult.avgMs, svcResult.avgMs);
        System.out.printf("  TFI avg: %.2f ms%n", tfiResult.avgMs);
        System.out.printf("  SVC avg: %.2f ms%n", svcResult.avgMs);
        System.out.printf("  门面开销: %.2f%% %s%n", overhead, overhead <= 2.0 ? "✅" : "⚠️");

        // 性能预算检查
        System.out.println("\n▶ 性能预算检查:");
        checkBudget("List(10k) P50", tfiResult.p50Ms, 900.0);
        checkBudget("List(10k) P95", tfiResult.p95Ms, 2500.0);
    }

    @Test
    void benchmarkMap2k() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("场景2: Map<String, Object>(2k) 性能基线");
        System.out.println("=".repeat(80));

        // 准备测试数据
        Map<String, Object> map1 = createMap(2000);
        Map<String, Object> map2 = new LinkedHashMap<>(map1);
        map2.put("key_1000", "MODIFIED_VALUE"); // 修改中间键

        // TFI.compare 基准
        System.out.println("\n▶ TFI.compare 基准:");
        BenchmarkResult tfiResult = runBenchmark(() -> TFI.compare(map1, map2));
        printResult(tfiResult);

        // CompareService.compare 基准
        System.out.println("\n▶ CompareService.compare 基准:");
        BenchmarkResult svcResult = runBenchmark(() -> compareService.compare(map1, map2, CompareOptions.DEFAULT));
        printResult(svcResult);

        // 门面开销分析
        System.out.println("\n▶ 门面开销分析:");
        double overhead = calculateOverhead(tfiResult.avgMs, svcResult.avgMs);
        System.out.printf("  TFI avg: %.2f ms%n", tfiResult.avgMs);
        System.out.printf("  SVC avg: %.2f ms%n", svcResult.avgMs);
        System.out.printf("  门面开销: %.2f%% %s%n", overhead, overhead <= 2.0 ? "✅" : "⚠️");

        // 性能预算检查
        System.out.println("\n▶ 性能预算检查:");
        checkBudget("Map(2k) P50", tfiResult.p50Ms, 180.0);
        checkBudget("Map(2k) P95", tfiResult.p95Ms, 500.0);
    }

    // ==================== 辅助方法 ====================

    /**
     * 运行基准测试
     */
    private BenchmarkResult runBenchmark(Supplier<CompareResult> operation) {
        // 预热
        warmup(operation, WARMUP_ROUNDS);

        // 强制 GC 并记录初始状态
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { }

        long gcBefore = getTotalGcCount();
        long heapBefore = getUsedHeapMB();

        // 采样
        long[] samples = sample(operation, SAMPLE_ROUNDS);

        // 记录最终状态
        long gcAfter = getTotalGcCount();
        long heapAfter = getUsedHeapMB();

        // 计算统计指标
        Arrays.sort(samples);
        double p50 = samples[samples.length / 2] / 1_000_000.0;
        double p95 = samples[(int) (samples.length * 0.95)] / 1_000_000.0;
        double avg = Arrays.stream(samples).average().orElse(0) / 1_000_000.0;

        return new BenchmarkResult(p50, p95, avg, gcAfter - gcBefore, heapAfter - heapBefore);
    }

    /**
     * 预热
     */
    private void warmup(Supplier<CompareResult> operation, int rounds) {
        for (int i = 0; i < rounds; i++) {
            operation.get();
        }
    }

    /**
     * 采样
     */
    private long[] sample(Supplier<CompareResult> operation, int rounds) {
        long[] samples = new long[rounds];
        for (int i = 0; i < rounds; i++) {
            long start = System.nanoTime();
            operation.get();
            samples[i] = System.nanoTime() - start;
        }
        return samples;
    }

    /**
     * 创建测试 List
     */
    private List<String> createList(int size) {
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add("element_" + i);
        }
        return list;
    }

    /**
     * 创建测试 Map
     */
    private Map<String, Object> createMap(int size) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            map.put("key_" + i, "value_" + i);
        }
        return map;
    }

    /**
     * 获取总 GC 次数
     */
    private long getTotalGcCount() {
        long total = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount();
            if (count > 0) {
                total += count;
            }
        }
        return total;
    }

    /**
     * 获取已使用堆内存（MB）
     */
    private long getUsedHeapMB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
    }

    /**
     * 计算门面开销百分比
     */
    private double calculateOverhead(double tfiAvg, double svcAvg) {
        return ((tfiAvg - svcAvg) / svcAvg) * 100.0;
    }

    /**
     * 打印基准结果
     */
    private void printResult(BenchmarkResult result) {
        System.out.printf("  P50: %.2f ms%n", result.p50Ms);
        System.out.printf("  P95: %.2f ms%n", result.p95Ms);
        System.out.printf("  AVG: %.2f ms%n", result.avgMs);
        System.out.printf("  GC: %d 次%n", result.gcCount);
        System.out.printf("  Heap: %+d MB%n", result.heapDeltaMB);
    }

    /**
     * 检查性能预算
     */
    private void checkBudget(String metric, double actual, double budget) {
        boolean pass = actual <= budget;
        System.out.printf("  %s: %.2f ms / %.2f ms %s%n",
            metric, actual, budget, pass ? "✅" : "⚠️");
        if (!pass) {
            System.out.printf("    ⚠️ 超出预算 %.2f ms (%.1f%%)%n",
                actual - budget, ((actual - budget) / budget) * 100);
        }
    }

    /**
     * 确保 CompareService 实例（从 TFI 获取或创建）
     */
    private static CompareService ensureCompareService() {
        try {
            // 尝试从 TFI 获取（使用反射访问 private 方法）
            java.lang.reflect.Method method = TFI.class.getDeclaredMethod("ensureCompareService");
            method.setAccessible(true);
            return (CompareService) method.invoke(null);
        } catch (Exception e) {
            // 降级：创建新实例
            com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor executor =
                new com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor(Collections.emptyList());
            return new CompareService(executor);
        }
    }

    /**
     * 基准结果
     */
    private static class BenchmarkResult {
        final double p50Ms;
        final double p95Ms;
        final double avgMs;
        final long gcCount;
        final long heapDeltaMB;

        BenchmarkResult(double p50Ms, double p95Ms, double avgMs, long gcCount, long heapDeltaMB) {
            this.p50Ms = p50Ms;
            this.p95Ms = p95Ms;
            this.avgMs = avgMs;
            this.gcCount = gcCount;
            this.heapDeltaMB = heapDeltaMB;
        }
    }
}
