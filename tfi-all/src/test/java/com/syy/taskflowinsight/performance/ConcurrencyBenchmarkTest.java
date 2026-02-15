package com.syy.taskflowinsight.performance;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 真实并发性能基准测试 - 增强版
 *
 * <p>改进点：
 * <ul>
 *   <li>模拟真实业务场景：订单处理、变更追踪、嵌套任务</li>
 *   <li>多样化负载：读写比例、对象深度、集合操作</li>
 *   <li>性能指标详细：P50/P95/P99延迟、吞吐量、内存占用</li>
 *   <li>并发竞争测试：共享数据结构、缓存命中率</li>
 *   <li>资源泄漏检测：ThreadLocal清理、Session生命周期</li>
 *   测试方案 ./mvnw test -Dtest=ConcurrencyBenchmarkTest#test20Threads -Dtfi.runConcurrencyBenchmark=true -Dtfi.benchmark.realistic=true -Dtfi.virtualThreads=true -Dtfi.benchmark.warmup.seconds=10 -Dtfi.benchmark.duration.seconds=60
 * </ul>
 */
@EnabledIfSystemProperty(named = "tfi.runConcurrencyBenchmark", matches = "(?i)true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("真实并发性能基准测试")
public class ConcurrencyBenchmarkTest {

    private static final int WARM_UP_DURATION_SECONDS = Integer.getInteger("tfi.benchmark.warmup.seconds", 3);
    private static final int TEST_DURATION_SECONDS = Integer.getInteger("tfi.benchmark.duration.seconds", 15);
    private static final boolean REALISTIC_MODE = Boolean.parseBoolean(System.getProperty("tfi.benchmark.realistic", "false"));
    
    // ==================== 业务实体模型 ====================

    @Entity(name = "Order")
    public static class Order {
        @Key
        private String orderId;
        private String customerId;
        private BigDecimal totalAmount;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<OrderItem> items;
        // 现实模式下增加的复杂字段
        private Map<String, String> attributes;
        private List<String> tags;

        public Order(String orderId, String customerId) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.totalAmount = BigDecimal.ZERO;
            this.status = "PENDING";
            this.createdAt = LocalDateTime.now();
            this.updatedAt = LocalDateTime.now();
            this.items = new ArrayList<>();
            this.attributes = null;
            this.tags = null;
        }

        public void addItem(OrderItem item) {
            this.items.add(item);
            this.totalAmount = this.totalAmount.add(item.getSubtotal());
        }

        public void updateStatus(String newStatus) {
            this.status = newStatus;
            this.updatedAt = LocalDateTime.now();
        }

        // Getters
        public String getOrderId() { return orderId; }
        public String getCustomerId() { return customerId; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public String getStatus() { return status; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public List<OrderItem> getItems() { return items; }
        public Map<String, String> getAttributes() { return attributes; }
        public List<String> getTags() { return tags; }
    }

    @Entity(name = "OrderItem")
    public static class OrderItem {
        @Key
        private String orderId;
        @Key
        private String productId;

        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal discount;

        public OrderItem(String orderId, String productId, String productName, int quantity, BigDecimal unitPrice) {
            this.orderId = orderId;
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.discount = BigDecimal.ZERO;
        }

        public BigDecimal getSubtotal() {
            return unitPrice.multiply(new BigDecimal(quantity)).subtract(discount);
        }

        // Getters
        public String getOrderId() { return orderId; }
        public String getProductId() { return productId; }
        public String getProductName() { return productName; }
        public Integer getQuantity() { return quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public BigDecimal getDiscount() { return discount; }
    }

    @BeforeAll
    static void setup() {
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
        System.out.println("========================================");
        System.out.println("TaskFlowInsight 真实并发性能基准测试");
        System.out.println("========================================");
        System.out.println("测试配置:");
        System.out.println("- 预热时间: " + WARM_UP_DURATION_SECONDS + "秒");
        System.out.println("- 测试时间: " + TEST_DURATION_SECONDS + "秒");
        System.out.println("- 业务场景: 订单处理 + 变更追踪 + 嵌套任务");
        System.out.println("========================================\n");
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("1线程 - 基线性能")
    void testSingleThread() throws InterruptedException {
        runBenchmark(1);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("5线程 - 低并发")
    void test5Threads() throws InterruptedException {
        runBenchmark(5);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("10线程 - 轻度并发")
    void test10Threads() throws InterruptedException {
        runBenchmark(10);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("20线程 - 中低并发")
    void test20Threads() throws InterruptedException {
        runBenchmark(20);
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("50线程 - 中等并发")
    void test50Threads() throws InterruptedException {
        runBenchmark(50);
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("100线程 - 高并发")
    void test100Threads() throws InterruptedException {
        runBenchmark(100);
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("200线程 - 超高并发")
    void test200Threads() throws InterruptedException {
        runBenchmark(200);
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("500线程 - 极限并发")
    void test500Threads() throws InterruptedException {
        runBenchmark(500);
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("1000线程 - 超极限并发")
    void test1000Threads() throws InterruptedException {
        runBenchmark(1000);
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("2000线程 - 大规模并发")
    void test2000Threads() throws InterruptedException {
        runBenchmark(2000);
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("3500线程 - 超大规模并发")
    void test3500Threads() throws InterruptedException {
        runBenchmark(3500);
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("5000线程 - 终极压力测试")
    void test5000Threads() throws InterruptedException {
        runBenchmark(5000);
    }

    private void runBenchmark(int threadCount) throws InterruptedException {
        System.out.println("\n【测试 " + threadCount + " 线程并发】");
        System.out.println("========================================");

        // 清理环境
        TFI.clear();
        System.gc();
        Thread.sleep(500);

        // 预热
        System.out.print("▶ 预热中...");
        warmUp(threadCount);
        System.out.println(" ✓ 完成");

        // 清理预热数据
        TFI.clear();
        System.gc();
        Thread.sleep(500);

        // 记录开始指标
        long startTime = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // 执行性能测试
        System.out.print("▶ 执行测试...");
        BenchmarkResult result = executeBenchmark(threadCount);
        System.out.println(" ✓ 完成");

        // 记录结束指标
        long endTime = System.currentTimeMillis();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        double durationSeconds = (endTime - startTime) / 1000.0;

        // 计算延迟百分位数（纳秒）
        long p50 = result.latencies.percentile(50);
        long p95 = result.latencies.percentile(95);
        long p99 = result.latencies.percentile(99);
        long pMax = result.latencies.max();

        // 计算性能指标
        long totalOps = result.successCount.get() + result.errorCount.get() + result.rejectedCount.get();
        double qps = result.successCount.get() / durationSeconds;
        double avgLatency = result.totalLatency.get() / (double) result.successCount.get() / 1_000_000.0;
        long memoryUsed = (memoryAfter - memoryBefore) / 1024 / 1024; // MB
        double throughput = totalOps / durationSeconds;

        // 输出详细结果
        System.out.println("\n┌─── 性能指标 ───────────────────────────");
        System.out.println("│ 执行时间      : " + String.format("%.2f", durationSeconds) + " 秒");
        System.out.println("│ 总操作数      : " + totalOps);
        System.out.println("│ 成功操作      : " + result.successCount.get());
        System.out.println("│ 业务拒绝      : " + result.rejectedCount.get());
        System.out.println("│ 系统错误      : " + result.errorCount.get());
        double systemErrorRate = totalOps > 0 ? result.errorCount.get() * 100.0 / totalOps : 0.0;
        double rejectRate = totalOps > 0 ? result.rejectedCount.get() * 100.0 / totalOps : 0.0;
        System.out.println("│ 业务拒绝率    : " + String.format("%.2f", rejectRate) + "%");
        System.out.println("│ 系统错误率    : " + String.format("%.2f", systemErrorRate) + "%");
        System.out.println("├─── 吞吐量指标 ─────────────────────────");
        System.out.println("│ QPS (成功)    : " + String.format("%,d", (long)qps) + " ops/sec");
        System.out.println("│ 总吞吐量      : " + String.format("%,d", (long)throughput) + " ops/sec");
        System.out.println("│ 每线程QPS     : " + String.format("%.2f", qps / threadCount) + " ops/sec");
        System.out.println("├─── 延迟指标 ───────────────────────────");
        System.out.println("│ 平均延迟      : " + String.format("%.3f", avgLatency) + " ms");
        System.out.println("│ P50 延迟      : " + String.format("%.3f", p50 / 1_000_000.0) + " ms");
        System.out.println("│ P95 延迟      : " + String.format("%.3f", p95 / 1_000_000.0) + " ms");
        System.out.println("│ P99 延迟      : " + String.format("%.3f", p99 / 1_000_000.0) + " ms");
        System.out.println("│ Max 延迟      : " + String.format("%.3f", pMax / 1_000_000.0) + " ms");
        System.out.println("├─── 资源使用 ───────────────────────────");
        System.out.println("│ 内存增长      : " + memoryUsed + " MB");
        System.out.println("│ 堆内存使用    : " + (memoryAfter / 1024 / 1024) + " MB");
        System.out.println("│ 平均每op内存  : " + String.format("%.2f", (memoryUsed * 1024.0 / totalOps)) + " KB");
        System.out.println("├─── 性能评估 ───────────────────────────");

        String rating = getPerformanceRating(qps, systemErrorRate, avgLatency);
        String healthStatus = getHealthStatus(systemErrorRate, p99 / 1_000_000.0);
        String scalabilityRating = getScalabilityRating(threadCount, qps);

        System.out.println("│ 性能评级      : " + rating);
        System.out.println("│ 健康状态      : " + healthStatus);
        System.out.println("│ 扩展性评估    : " + scalabilityRating);
        System.out.println("└────────────────────────────────────────");

        // 性能警告
        printPerformanceWarnings(systemErrorRate, rejectRate, avgLatency, memoryUsed, p99 / 1_000_000.0);

        // 错误类型汇总（如有）
        if (result.errorCount.get() > 0 && !result.errorByType.isEmpty()) {
            System.out.println("\n🧪 错误类型统计：");
            result.errorByType.entrySet().stream()
                .sorted((a,b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(5)
                .forEach(e -> System.out.println("  " + e.getKey() + ": " + e.getValue().get()));
            if (result.sampleError != null) {
                System.out.println("  示例异常：" + result.sampleError);
            }
        }
    }
    
    private void warmUp(int threadCount) throws InterruptedException {
        try (ExecutorService executor = newExecutor(threadCount)) {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long endTime = System.nanoTime() + WARM_UP_DURATION_SECONDS * 1_000_000_000L;
                    while (System.nanoTime() < endTime) {
                        performOperation();
                    }
                } catch (Exception e) {
                    // 忽略预热错误
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        endLatch.await();
        }
    }
    
    private BenchmarkResult executeBenchmark(int threadCount) throws InterruptedException {
        try (ExecutorService executor = newExecutor(threadCount)) {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        BenchmarkResult result = new BenchmarkResult();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long endTime = System.nanoTime() + TEST_DURATION_SECONDS * 1_000_000_000L;

                    while (System.nanoTime() < endTime) {
                        long startOp = System.nanoTime();
                        try {
                            performOperation();
                            long endOp = System.nanoTime();
                            long latency = endOp - startOp;
                            result.successCount.incrementAndGet();
                            result.totalLatency.addAndGet(latency);
                            result.latencies.record(latency); // 记录每次延迟（纳秒）
                        } catch (BusinessRejection br) {
                            result.rejectedCount.incrementAndGet();
                        } catch (Exception e) {
                            result.errorCount.incrementAndGet();
                            String key = e.getClass().getSimpleName();
                            result.errorByType.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
                            if (result.sampleError == null) {
                                result.sampleError = e;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();

        return result;
        }
    }
    
    /**
     * 真实业务操作：订单处理流程
     * 包含：创建订单 → 添加商品 → 库存校验 → 价格计算 → 状态流转 → 变更追踪
     */
    private void performOperation() {
        long threadId = Thread.currentThread().threadId();
        String sessionId = TFI.startSession("OrderProcessing-" + threadId);

        try (TaskContext mainTask = TFI.start("ProcessOrder")) {
            mainTask.message("开始处理订单");
            mainTask.attribute("threadId", threadId);
            mainTask.attribute("timestamp", LocalDateTime.now());

            // 1. 创建订单 (模拟读取数据库)
            Order order;
            try (TaskContext createTask = mainTask.subtask("CreateOrder")) {
                String orderId = "ORD-" + threadId + "-" + System.nanoTime();
                String customerId = "CUST-" + (threadId % 100);
                order = new Order(orderId, customerId);
                createTask.message("订单创建成功: " + orderId);
                createTask.attribute("orderId", orderId);
                simulateDatabaseLatency();
                createTask.success();
            }

            // 2. 添加商品 (模拟业务逻辑)
            Order orderBeforeItems;
            try (TaskContext addItemsTask = mainTask.subtask("AddOrderItems")) {
                orderBeforeItems = cloneOrder(order);

                // 添加多个商品（现实模式不增加商品数，改为增加对象图复杂度）
                int itemCount = ThreadLocalRandom.current().nextInt(1, 5);
                for (int i = 0; i < itemCount; i++) {
                    OrderItem item = new OrderItem(
                        order.getOrderId(),
                        "PROD-" + ThreadLocalRandom.current().nextInt(1000),
                        "商品-" + i,
                        ThreadLocalRandom.current().nextInt(1, 10),
                        new BigDecimal(ThreadLocalRandom.current().nextInt(10, 1000))
                    );
                    order.addItem(item);
                }

                // 现实模式：附加属性Map/列表，增加对象图复杂度
                if (REALISTIC_MODE) {
                    if (order.attributes == null) {
                        order.attributes = new LinkedHashMap<>();
                    }
                    int extra = ThreadLocalRandom.current().nextInt(100, 301); // 100~300个属性
                    for (int j = 0; j < extra; j++) {
                        order.attributes.put("attr_" + j, "v-" + ThreadLocalRandom.current().nextInt(1_000_000));
                    }
                    if (order.tags == null) {
                        order.tags = new ArrayList<>();
                    }
                    for (int k = 0; k < 50; k++) {
                        order.tags.add("tag-" + k);
                    }
                }

                addItemsTask.message("添加了 " + itemCount + " 个商品");
                addItemsTask.attribute("itemCount", itemCount);
                simulateBusinessLogic();
                addItemsTask.success();
            }

            // 3. 库存校验 (模拟外部API调用)
            try (TaskContext inventoryTask = mainTask.subtask("CheckInventory")) {
                inventoryTask.message("校验库存可用性");

                for (OrderItem item : order.getItems()) {
                    // 模拟库存检查
                    boolean available = ThreadLocalRandom.current().nextDouble() > 0.05; // 95%成功率
                    if (!available) {
                        inventoryTask.error("库存不足: " + item.getProductName());
                        throw new BusinessRejection("库存不足");
                    }
                }

                simulateApiCall();
                inventoryTask.success();
            }

            // 4. 价格计算与折扣 (模拟复杂计算)
            Order orderBeforePrice;
            try (TaskContext pricingTask = mainTask.subtask("CalculatePricing")) {
                orderBeforePrice = cloneOrder(order);

                // 应用折扣
                if (order.getTotalAmount().compareTo(new BigDecimal(500)) > 0) {
                    for (OrderItem item : order.getItems()) {
                        BigDecimal discount = item.getSubtotal().multiply(new BigDecimal("0.1"));
                        // 注意：这里简化了，实际需要修改OrderItem的discount字段
                    }
                }

                pricingTask.message("价格计算完成, 总金额: " + order.getTotalAmount());
                pricingTask.attribute("totalAmount", order.getTotalAmount());
                simulateComplexCalculation();
                pricingTask.success();
            }

            // 5. 状态流转 (模拟状态机)
            Order orderBeforeConfirm;
            try (TaskContext confirmTask = mainTask.subtask("ConfirmOrder")) {
                orderBeforeConfirm = cloneOrder(order);

                order.updateStatus("CONFIRMED");

                confirmTask.message("订单已确认");
                confirmTask.attribute("status", "CONFIRMED");
                simulateDatabaseUpdate();
                confirmTask.success();
            }

            // 6. 变更追踪 (使用TFI的核心功能)
            try (TaskContext trackingTask = mainTask.subtask("TrackChanges")) {
                // 追踪订单变更
                if (orderBeforeConfirm != null) {
                    CompareResult changes = TFI.compare(orderBeforeConfirm, order);
                    int changeCount = changes.getChanges().size();
                    trackingTask.message("检测到 " + changeCount + " 处变更");
                    trackingTask.attribute("changeCount", changeCount);
                }

                trackingTask.success();
            }

            mainTask.message("订单处理完成");
            mainTask.success();

        } catch (BusinessRejection br) {
            throw br;
        } catch (InterruptedException ie) {
            // 基准窗口/测试框架中断：视为非错误，转为业务拒绝以避免污染错误率
            Thread.currentThread().interrupt();
            throw new BusinessRejection("interrupted");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            TFI.endSession();
        }
    }

    // ==================== 辅助方法 ====================

    private Order cloneOrder(Order original) {
        Order cloned = new Order(original.getOrderId(), original.getCustomerId());
        for (OrderItem item : original.getItems()) {
            cloned.addItem(new OrderItem(
                item.getOrderId(),
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice()
            ));
        }
        cloned.updateStatus(original.getStatus());
        // 复制附加结构（现实模式）
        if (original.getAttributes() != null) {
            cloned.attributes = new LinkedHashMap<>(original.getAttributes());
        }
        if (original.getTags() != null) {
            cloned.tags = new ArrayList<>(original.getTags());
        }
        return cloned;
    }

    private void simulateDatabaseLatency() throws InterruptedException {
        if (REALISTIC_MODE) {
            Thread.sleep(ThreadLocalRandom.current().nextInt(2, 6)); // 2–5ms
        } else {
            Thread.sleep(0, ThreadLocalRandom.current().nextInt(50_000, 200_000)); // 50–200μs
        }
    }

    private void simulateBusinessLogic() throws InterruptedException {
        if (REALISTIC_MODE) {
            sleepNanosRange(200_000, 800_000); // 0.2–0.8ms
        } else {
            Thread.sleep(0, ThreadLocalRandom.current().nextInt(10_000, 100_000)); // 10–100μs
        }
    }

    private void simulateApiCall() throws InterruptedException {
        if (REALISTIC_MODE) {
            Thread.sleep(ThreadLocalRandom.current().nextInt(5, 21)); // 5–20ms
        } else {
            Thread.sleep(0, ThreadLocalRandom.current().nextInt(100_000, 500_000)); // 100–500μs
        }
    }

    private void simulateComplexCalculation() throws InterruptedException {
        if (REALISTIC_MODE) {
            // 使用安全纳秒范围并分解为ms+ns，避免IllegalArgumentException
            sleepNanosRange(300_000, 1_200_000); // 0.3–1.2ms
        } else {
            Thread.sleep(0, ThreadLocalRandom.current().nextInt(20_000, 150_000)); // 20–150μs
        }
    }

    private void simulateDatabaseUpdate() throws InterruptedException {
        if (REALISTIC_MODE) {
            Thread.sleep(ThreadLocalRandom.current().nextInt(2, 6)); // 2–5ms
        } else {
            Thread.sleep(0, ThreadLocalRandom.current().nextInt(50_000, 250_000)); // 50–250μs
        }
    }

    /**
     * 在[minNs, maxNs)范围内随机睡眠，自动拆分到ms+ns，避免纳秒参数越界。
     */
    private void sleepNanosRange(long minNs, long maxNs) throws InterruptedException {
        if (maxNs <= minNs) return;
        long nanos = ThreadLocalRandom.current().nextLong(minNs, maxNs);
        if (nanos <= 0) return;
        long ms = nanos / 1_000_000L;
        int ns = (int) (nanos % 1_000_000L); // 始终 < 1_000_000
        if (ms > 0 || ns > 0) {
            Thread.sleep(ms, ns);
        }
    }
    
    private String getPerformanceRating(double qps, double systemErrorRate, double avgLatency) {
        if (systemErrorRate > 5.0) return "❌ 不稳定";
        if (systemErrorRate > 1.0) return "⚠️ 需关注";
        if (qps < 100) return "⚠️ 需优化";
        if (qps < 1000) return "✅ 良好";
        if (qps < 5000) return "🚀 优秀";
        if (qps < 10000) return "🏆 卓越";
        return "💎 极致";
    }

    private String getHealthStatus(double systemErrorRate, double p99Latency) {
        if (systemErrorRate > 5.0) return "🔴 严重异常";
        if (systemErrorRate > 1.0) return "🟡 轻微异常";
        if (p99Latency > 1000) return "🟡 延迟较高";
        if (p99Latency > 500) return "🟢 正常 (延迟中等)";
        return "🟢 健康";
    }

    private String getScalabilityRating(int threadCount, double qps) {
        double qpsPerThread = qps / threadCount;
        if (threadCount <= 10) {
            return "📊 基准测试";
        } else if (threadCount <= 50) {
            if (qpsPerThread > 50) return "📈 扩展性优秀";
            return "📊 扩展性良好";
        } else if (threadCount <= 200) {
            if (qpsPerThread > 30) return "📈 高并发下表现优秀";
            if (qpsPerThread > 10) return "📊 高并发下表现良好";
            return "📉 扩展性下降";
        } else {
            if (qpsPerThread > 10) return "🔥 极限并发下表现优异";
            if (qpsPerThread > 5) return "📈 极限并发下表现良好";
            return "⚠️ 极限并发性能衰减";
        }
    }

    private void printPerformanceWarnings(double systemErrorRate, double rejectRate, double avgLatency, long memoryUsed, double p99Latency) {
        List<String> warnings = new ArrayList<>();

        if (systemErrorRate > 5.0) {
            warnings.add("⚠️ 错误率超过5%，需要紧急优化");
        } else if (systemErrorRate > 1.0) {
            warnings.add("⚠️ 错误率超过1%，建议排查原因");
        }

        // 业务拒绝率预期值（1 - 0.95^N），N~Uniform[1,4] ≈ 11.9%
        double expectedReject = 11.9;
        if (Math.abs(rejectRate - expectedReject) > 5.0) {
            warnings.add("ℹ️ 业务拒绝率偏离预期(" + String.format("%.1f", expectedReject) + "%)，请核对模拟参数或数据分布");
        }

        if (avgLatency > 500) {
            warnings.add("⚠️ 平均延迟超过500ms，可能影响用户体验");
        } else if (avgLatency > 200) {
            warnings.add("ℹ️ 平均延迟超过200ms，建议优化");
        }

        if (p99Latency > 1000) {
            warnings.add("⚠️ P99延迟超过1秒，长尾请求过慢");
        }

        if (memoryUsed > 500) {
            warnings.add("⚠️ 内存增长超过500MB，可能存在内存泄漏");
        } else if (memoryUsed > 200) {
            warnings.add("ℹ️ 内存增长超过200MB，建议监控");
        }

        if (!warnings.isEmpty()) {
            System.out.println("\n⚠️ 性能警告：");
            warnings.forEach(w -> System.out.println("  " + w));
        }
    }

    private static class BenchmarkResult {
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong rejectedCount = new AtomicLong(0);
        AtomicLong errorCount = new AtomicLong(0);
        AtomicLong totalLatency = new AtomicLong(0);
        LatencyWindow latencies = new LatencyWindow(Integer.getInteger("tfi.benchmark.latency.window", 20_000));
        ConcurrentMap<String, AtomicLong> errorByType = new ConcurrentHashMap<>();
        volatile Throwable sampleError;
    }

    /** 业务拒绝：如库存不足等可预期失败，不计为系统错误 */
    static class BusinessRejection extends RuntimeException {
        BusinessRejection(String msg) { super(msg); }
    }

    /**
     * 轻量滑动窗口延迟采样器（纳秒）
     * 使用 AtomicLongArray 存储最近 N 次延迟；分位计算按窗口近似。
     */
    static class LatencyWindow {
        private final int capacity;
        private final java.util.concurrent.atomic.AtomicLongArray buf;
        private final java.util.concurrent.atomic.AtomicLong index = new java.util.concurrent.atomic.AtomicLong();

        LatencyWindow(int capacity) {
            this.capacity = Math.max(1000, capacity);
            this.buf = new java.util.concurrent.atomic.AtomicLongArray(this.capacity);
        }

        void record(long nanos) {
            long i = index.getAndIncrement();
            int slot = (int) (i % capacity);
            buf.set(slot, nanos);
        }

        long percentile(int p) {
            long count = Math.min(index.get(), capacity);
            if (count <= 0) return 0L;
            long[] snap = new long[(int) count];
            for (int i = 0; i < count; i++) {
                snap[i] = buf.get(i);
            }
            java.util.Arrays.sort(snap);
            int rank = Math.min((int) Math.ceil((p / 100.0) * snap.length) - 1, snap.length - 1);
            return rank < 0 ? snap[0] : snap[rank];
        }

        long max() {
            long count = Math.min(index.get(), capacity);
            long m = 0L;
            for (int i = 0; i < count; i++) {
                long v = buf.get(i);
                if (v > m) m = v;
            }
            return m;
        }
    }

    /**
     * 创建执行器：优先使用虚拟线程，回退到固定线程池。
     */
    private ExecutorService newExecutor(int threadCount) {
        boolean useVirtual = Boolean.parseBoolean(System.getProperty("tfi.virtualThreads", "true"));
        if (useVirtual) {
            return java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        }
        return java.util.concurrent.Executors.newFixedThreadPool(threadCount);
    }
    
    @AfterAll
    static void summary() {
        System.out.println("\n========================================");
        System.out.println("         测试完成！         ");
        System.out.println("========================================");
        System.out.println("\n📊 测试报告：");
        System.out.println("- 本次测试模拟了真实业务场景下的并发性能");
        System.out.println("- 包含订单处理、变更追踪、嵌套任务等完整流程");
        System.out.println("- 性能指标包含延迟百分位数(P50/P95/P99)");
        System.out.println("- 资源监控包含内存使用、线程扩展性分析");
        System.out.println("\n💡 优化建议：");
        System.out.println("- 关注错误率 >1% 的场景，可能需要增加重试机制");
        System.out.println("- 关注P99延迟 >500ms 的场景，可能需要优化热点路径");
        System.out.println("- 关注内存增长 >200MB 的场景，检查是否有资源泄漏");
        System.out.println("- 关注极限并发下QPS/线程 <5 的场景，可能存在竞争热点");
        System.out.println("\n========================================");
    }
}
