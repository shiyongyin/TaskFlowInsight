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
 * - 轻量内存内（算法/引擎开销）
 *       - JAVA_TOOL_OPTIONS="-Xms1g -Xmx1g" ./mvnw test -Dtest=ConcurrencyInMemoryBenchmarkTest -Dtfi.runInMemoryBenchmark=true -Dtfi.virtualThreads=true -Dtfi.benchmark.warmup.seconds=3
 *         -Dtfi.benchmark.duration.seconds=15
 */
@EnabledIfSystemProperty(named = "tfi.runInMemoryBenchmark", matches = "(?i)true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("内存内并发性能基准（轻量模式）")
public class ConcurrencyInMemoryBenchmarkTest {

    private static final int WARM_UP_DURATION_SECONDS = Integer.getInteger("tfi.benchmark.warmup.seconds", 3);
    private static final int TEST_DURATION_SECONDS = Integer.getInteger("tfi.benchmark.duration.seconds", 15);

    @Entity(name = "Order")
    public static class Order {
        @Key private String orderId;
        private String customerId;
        private BigDecimal totalAmount;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<OrderItem> items;

        public Order(String orderId, String customerId) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.totalAmount = BigDecimal.ZERO;
            this.status = "PENDING";
            this.createdAt = LocalDateTime.now();
            this.updatedAt = LocalDateTime.now();
            this.items = new ArrayList<>();
        }
        public void addItem(OrderItem item) { this.items.add(item); this.totalAmount = this.totalAmount.add(item.getSubtotal()); }
        public void updateStatus(String newStatus) { this.status = newStatus; this.updatedAt = LocalDateTime.now(); }
        public String getOrderId() { return orderId; }
        public String getCustomerId() { return customerId; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public String getStatus() { return status; }
        public List<OrderItem> getItems() { return items; }
    }

    @Entity(name = "OrderItem")
    public static class OrderItem {
        @Key private String orderId;
        @Key private String productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal discount;
        public OrderItem(String orderId, String productId, String productName, int quantity, BigDecimal unitPrice) {
            this.orderId = orderId; this.productId = productId; this.productName = productName;
            this.quantity = quantity; this.unitPrice = unitPrice; this.discount = BigDecimal.ZERO;
        }
        public BigDecimal getSubtotal() { return unitPrice.multiply(new BigDecimal(quantity)).subtract(discount); }
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
        System.out.println("内存内并发性能基准（轻量模式）");
        System.out.println("========================================");
        System.out.println("- 预热: " + WARM_UP_DURATION_SECONDS + "s, 测试: " + TEST_DURATION_SECONDS + "s");
    }

    @Test @org.junit.jupiter.api.Order(1) @DisplayName("1线程") void t1() throws Exception { run(1); }
    @Test @org.junit.jupiter.api.Order(2) @DisplayName("5线程") void t5() throws Exception { run(5); }
    @Test @org.junit.jupiter.api.Order(3) @DisplayName("10线程") void t10() throws Exception { run(10); }
    @Test @org.junit.jupiter.api.Order(4) @DisplayName("20线程") void t20() throws Exception { run(20); }

    private void run(int threads) throws Exception {
        System.out.println("\n【测试 " + threads + " 线程并发（轻量）】\n==============================");
        TFI.clear(); System.gc(); Thread.sleep(300);
        warm(threads); TFI.clear(); System.gc(); Thread.sleep(300);

        long start = System.currentTimeMillis();
        long memBefore = usedHeap();
        BenchmarkResult r = exec(threads);
        long dur = System.currentTimeMillis() - start;
        long memAfter = usedHeap();

        long p50 = r.latencies.percentile(50), p95 = r.latencies.percentile(95), p99 = r.latencies.percentile(99), pMax = r.latencies.max();
        long total = r.success.get() + r.reject.get() + r.errors.get();
        double sec = dur / 1000.0;
        double qps = r.success.get() / sec;
        double thr = total / sec;
        double avgMs = r.totalNs.get() / (double) Math.max(1, r.success.get()) / 1_000_000.0;
        double errRate = total > 0 ? r.errors.get() * 100.0 / total : 0.0;

        System.out.printf("  执行: %.2fs, 成功:%d, 拒绝:%d, 错误:%d%n", sec, r.success.get(), r.reject.get(), r.errors.get());
        System.out.printf("  QPS(成功): %.0f, 总吞吐: %.0f, 每线程: %.2f%n", qps, thr, qps/threads);
        System.out.printf("  延迟: avg=%.3fms, P50=%.3fms, P95=%.3fms, P99=%.3fms, Max=%.3fms%n",
                avgMs, p50/1_000_000.0, p95/1_000_000.0, p99/1_000_000.0, pMax/1_000_000.0);
        System.out.printf("  错误率: %.2f%%, 内存增长:%dMB, 堆:%dMB%n", errRate, (memAfter-memBefore)/1024/1024, memAfter/1024/1024);
        if (r.errors.get() > 0 && !r.errorByType.isEmpty()) {
            System.out.println("  错误Top:");
            r.errorByType.entrySet().stream()
                    .sorted((a,b)-> Long.compare(b.getValue().get(), a.getValue().get()))
                    .limit(5)
                    .forEach(e-> System.out.println("    " + e.getKey() + ": " + e.getValue().get()));
        }
    }

    private void warm(int threads) throws Exception {
        try (ExecutorService ex = newExec(threads)) {
            CountDownLatch st = new CountDownLatch(1), ed = new CountDownLatch(threads);
            for (int i=0;i<threads;i++) ex.submit(() -> { try { st.await(); long end=System.nanoTime()+WARM_UP_DURATION_SECONDS*1_000_000_000L; while (System.nanoTime()<end) op(); } catch (Exception ignore) {} finally { ed.countDown(); }});
            st.countDown(); ed.await();
        }
    }

    private BenchmarkResult exec(int threads) throws Exception {
        try (ExecutorService ex = newExec(threads)) {
            CountDownLatch st = new CountDownLatch(1), ed = new CountDownLatch(threads);
            BenchmarkResult r = new BenchmarkResult();
            for (int i=0;i<threads;i++) ex.submit(() -> { try { st.await(); long end=System.nanoTime()+TEST_DURATION_SECONDS*1_000_000_000L; while (System.nanoTime()<end) {
                        long s=System.nanoTime();
                        try { op(); long e=System.nanoTime(); long d=e-s; r.success.incrementAndGet(); r.totalNs.addAndGet(d); r.latencies.record(d); }
                        catch (BusinessRejection br) { r.reject.incrementAndGet(); }
                        catch (Exception e) { r.errors.incrementAndGet(); r.errorByType.computeIfAbsent(e.getClass().getSimpleName(), k-> new AtomicLong()).incrementAndGet(); }
                    } } catch (Exception e){ r.errors.incrementAndGet(); } finally { ed.countDown(); }});
            st.countDown(); ed.await(); return r;
        }
    }

    private void op() throws Exception {
        long tid = Thread.currentThread().threadId();
        TFI.startSession("Order-"+tid);
        try (TaskContext root = TFI.start("ProcessOrder")) {
            Order order;
            try (TaskContext t = root.subtask("CreateOrder")) {
                order = new Order("ORD-"+System.nanoTime(), "CUST-"+(tid%100));
                simDb(); t.success();
            }
            try (TaskContext t = root.subtask("AddOrderItems")) {
                int n = ThreadLocalRandom.current().nextInt(1,5);
                for (int i=0;i<n;i++) order.addItem(new OrderItem(order.getOrderId(), "PROD-"+ThreadLocalRandom.current().nextInt(1000), "商品-"+i, ThreadLocalRandom.current().nextInt(1,10), new BigDecimal(ThreadLocalRandom.current().nextInt(10,1000))));
                simBiz(); t.success();
            }
            try (TaskContext t = root.subtask("CheckInventory")) {
                for (OrderItem it : order.getItems()) if (ThreadLocalRandom.current().nextDouble() <= 0.05) throw new BusinessRejection("库存不足");
                simApi(); t.success();
            }
            try (TaskContext t = root.subtask("CalculatePricing")) { simCalc(); t.success(); }
            try (TaskContext t = root.subtask("ConfirmOrder")) { order.updateStatus("CONFIRMED"); simUpdate(); t.success(); }
            try (TaskContext t = root.subtask("TrackChanges")) { CompareResult c = TFI.compare(order, order); t.attribute("changes", c.getChanges().size()); t.success(); }
            root.success();
        } catch (BusinessRejection br) { throw br; }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new BusinessRejection("interrupted"); }
        catch (Exception e) { throw e; }
        finally { TFI.endSession(); }
    }

    // 轻量延迟（微秒级）
    private void simDb() throws InterruptedException { Thread.sleep(0, ThreadLocalRandom.current().nextInt(50_000,200_000)); }
    private void simBiz() throws InterruptedException { Thread.sleep(0, ThreadLocalRandom.current().nextInt(10_000,100_000)); }
    private void simApi() throws InterruptedException { Thread.sleep(0, ThreadLocalRandom.current().nextInt(100_000,500_000)); }
    private void simCalc() throws InterruptedException { Thread.sleep(0, ThreadLocalRandom.current().nextInt(20_000,150_000)); }
    private void simUpdate() throws InterruptedException { Thread.sleep(0, ThreadLocalRandom.current().nextInt(50_000,250_000)); }

    private ExecutorService newExec(int threads) {
        boolean vt = Boolean.parseBoolean(System.getProperty("tfi.virtualThreads","true"));
        return vt ? Executors.newVirtualThreadPerTaskExecutor() : Executors.newFixedThreadPool(threads);
    }
    private long usedHeap(){ Runtime r=Runtime.getRuntime(); return r.totalMemory()-r.freeMemory(); }

    static class BusinessRejection extends RuntimeException { BusinessRejection(String m){ super(m);} }
    static class BenchmarkResult {
        AtomicLong success=new AtomicLong(); AtomicLong reject=new AtomicLong(); AtomicLong errors=new AtomicLong(); AtomicLong totalNs=new AtomicLong();
        LatencyWindow latencies=new LatencyWindow(Integer.getInteger("tfi.benchmark.latency.window",20_000));
        ConcurrentMap<String, AtomicLong> errorByType=new ConcurrentHashMap<>();
    }
    static class LatencyWindow { private final int cap; private final java.util.concurrent.atomic.AtomicLongArray buf; private final java.util.concurrent.atomic.AtomicLong idx=new java.util.concurrent.atomic.AtomicLong();
        LatencyWindow(int c){ cap=Math.max(1000,c); buf=new java.util.concurrent.atomic.AtomicLongArray(cap);} void record(long ns){ long i=idx.getAndIncrement(); buf.set((int)(i%cap), ns);} long percentile(int p){ long n=Math.min(idx.get(),cap); if(n<=0)return 0; long[] a=new long[(int)n]; for(int i=0;i<n;i++) a[i]=buf.get(i); java.util.Arrays.sort(a); int r=Math.min((int)Math.ceil(p/100.0*a.length)-1, a.length-1); return r<0? a[0]:a[r]; } long max(){ long n=Math.min(idx.get(),cap), m=0; for(int i=0;i<n;i++){ long v=buf.get(i); if(v>m)m=v;} return m; } }
}
