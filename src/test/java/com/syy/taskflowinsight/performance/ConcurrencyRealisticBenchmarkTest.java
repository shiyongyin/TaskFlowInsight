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
 * 现实模式（近生产链路）
 *       - JAVA_TOOL_OPTIONS="-Xms1g -Xmx1g" ./mvnw test -Dtest=ConcurrencyRealisticBenchmarkTest -Dtfi.runRealisticBenchmark=true -Dtfi.virtualThreads=true -Dtfi.benchmark.warmup.seconds=3
 *         -Dtfi.benchmark.duration.seconds=15
 */
@EnabledIfSystemProperty(named = "tfi.runRealisticBenchmark", matches = "(?i)true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("并发性能基准（现实模式）")
public class ConcurrencyRealisticBenchmarkTest {

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
        private Map<String,String> attributes;
        private List<String> tags;
        public Order(String orderId, String customerId){ this.orderId=orderId; this.customerId=customerId; this.totalAmount=BigDecimal.ZERO; this.status="PENDING"; this.createdAt=LocalDateTime.now(); this.updatedAt=LocalDateTime.now(); this.items=new ArrayList<>(); }
        public void addItem(OrderItem item){ this.items.add(item); this.totalAmount=this.totalAmount.add(item.getSubtotal()); }
        public void updateStatus(String s){ this.status=s; this.updatedAt=LocalDateTime.now(); }
        public String getOrderId(){ return orderId;} public List<OrderItem> getItems(){ return items;} public Map<String,String> getAttributes(){ return attributes;} public List<String> getTags(){ return tags;}
    }
    @Entity(name="OrderItem")
    public static class OrderItem {
        @Key private String orderId; @Key private String productId; private String productName; private Integer quantity; private BigDecimal unitPrice; private BigDecimal discount=BigDecimal.ZERO;
        public OrderItem(String orderId,String productId,String name,int qty, BigDecimal price){ this.orderId=orderId; this.productId=productId; this.productName=name; this.quantity=qty; this.unitPrice=price; }
        public BigDecimal getSubtotal(){ return unitPrice.multiply(new BigDecimal(quantity)).subtract(discount);} public String getProductName(){ return productName; }
    }

    @BeforeAll static void setup(){ TFI.enable(); TFI.setChangeTrackingEnabled(true);
        System.out.println("========================================\n并发性能基准（现实模式）\n========================================");
    }

    @Test @org.junit.jupiter.api.Order(1) @DisplayName("20线程") void t20() throws Exception { run(20); }

    private void run(int threads) throws Exception {
        System.out.println("\n【测试 "+threads+" 线程并发（现实）】\n==============================");
        TFI.clear(); System.gc(); Thread.sleep(300); warm(threads); TFI.clear(); System.gc(); Thread.sleep(300);
        long start=System.currentTimeMillis(); long memBefore=usedHeap(); BenchmarkResult r=exec(threads); long dur=System.currentTimeMillis()-start; long memAfter=usedHeap();
        long p50=r.latencies.percentile(50), p95=r.latencies.percentile(95), p99=r.latencies.percentile(99), pMax=r.latencies.max();
        long total=r.success.get()+r.reject.get()+r.errors.get(); double sec=dur/1000.0; double qps=r.success.get()/sec; double thr=total/sec; double avgMs=r.totalNs.get()/(double)Math.max(1,r.success.get())/1_000_000.0;
        double errRate= total>0? r.errors.get()*100.0/total:0.0;
        System.out.printf("  执行: %.2fs, 成功:%d, 拒绝:%d, 错误:%d%n", sec, r.success.get(), r.reject.get(), r.errors.get());
        System.out.printf("  QPS(成功): %.0f, 总吞吐: %.0f, 每线程: %.2f%n", qps, thr, qps/threads);
        System.out.printf("  延迟: avg=%.3fms, P50=%.3fms, P95=%.3fms, P99=%.3fms, Max=%.3fms%n", avgMs, p50/1_000_000.0, p95/1_000_000.0, p99/1_000_000.0, pMax/1_000_000.0);
        System.out.printf("  错误率: %.2f%%, 内存增长:%dMB, 堆:%dMB%n", errRate, (memAfter-memBefore)/1024/1024, memAfter/1024/1024);
        if (r.errors.get()>0 && !r.errorByType.isEmpty()) { System.out.println("  错误Top:"); r.errorByType.entrySet().stream().sorted((a,b)->Long.compare(b.getValue().get(), a.getValue().get())).limit(5).forEach(e-> System.out.println("    "+e.getKey()+": "+e.getValue().get())); }
    }

    private void warm(int threads) throws Exception { try(ExecutorService ex=newExec(threads)){ CountDownLatch st=new CountDownLatch(1), ed=new CountDownLatch(threads); for(int i=0;i<threads;i++) ex.submit(()->{ try{ st.await(); long end=System.nanoTime()+WARM_UP_DURATION_SECONDS*1_000_000_000L; while(System.nanoTime()<end) op(); } catch(Exception ignore){} finally { ed.countDown(); }}); st.countDown(); ed.await(); } }
    private BenchmarkResult exec(int threads) throws Exception { try(ExecutorService ex=newExec(threads)){ CountDownLatch st=new CountDownLatch(1), ed=new CountDownLatch(threads); BenchmarkResult r=new BenchmarkResult(); for(int i=0;i<threads;i++) ex.submit(()->{ try{ st.await(); long end=System.nanoTime()+TEST_DURATION_SECONDS*1_000_000_000L; while(System.nanoTime()<end){ long s=System.nanoTime(); try{ op(); long e=System.nanoTime(); long d=e-s; r.success.incrementAndGet(); r.totalNs.addAndGet(d); r.latencies.record(d);} catch(BusinessRejection br){ r.reject.incrementAndGet(); } catch(Exception e){ r.errors.incrementAndGet(); r.errorByType.computeIfAbsent(e.getClass().getSimpleName(), k-> new AtomicLong()).incrementAndGet(); } } } catch(Exception e){ r.errors.incrementAndGet(); } finally { ed.countDown(); }}); st.countDown(); ed.await(); return r; } }

    private void op() throws Exception {
        long tid=Thread.currentThread().threadId(); TFI.startSession("Order-"+tid);
        try(TaskContext root=TFI.start("ProcessOrder")){
            Order order; try(TaskContext t=root.subtask("CreateOrder")){ order=new Order("ORD-"+System.nanoTime(), "CUST-"+(tid%100)); sleepMs(2,5); t.success(); }
            try(TaskContext t=root.subtask("AddOrderItems")){ int n=ThreadLocalRandom.current().nextInt(1,5); for(int i=0;i<n;i++) order.addItem(new OrderItem(order.getOrderId(), "PROD-"+ThreadLocalRandom.current().nextInt(1000), "商品-"+i, ThreadLocalRandom.current().nextInt(1,10), new BigDecimal(ThreadLocalRandom.current().nextInt(10,1000))));
                if (order.attributes==null) order.attributes=new LinkedHashMap<>(); int extra=ThreadLocalRandom.current().nextInt(100,301); for(int j=0;j<extra;j++) order.attributes.put("attr_"+j, "v-"+ThreadLocalRandom.current().nextInt(1_000_000)); if(order.tags==null) order.tags=new ArrayList<>(); for(int k=0;k<50;k++) order.tags.add("tag-"+k);
                sleepNanosRange(200_000,800_000); t.success(); }
            try(TaskContext t=root.subtask("CheckInventory")){ for (OrderItem it: order.getItems()) if (ThreadLocalRandom.current().nextDouble()<=0.05) throw new BusinessRejection("库存不足"); sleepMs(5,20); t.success(); }
            try(TaskContext t=root.subtask("CalculatePricing")){ sleepNanosRange(300_000,1_200_000); t.success(); }
            try(TaskContext t=root.subtask("ConfirmOrder")){ order.updateStatus("CONFIRMED"); sleepMs(2,5); t.success(); }
            try(TaskContext t=root.subtask("TrackChanges")){ CompareResult c=TFI.compare(order, order); t.attribute("changes", c.getChanges().size()); t.success(); }
            root.success();
        } catch (BusinessRejection br){ throw br; } catch (InterruptedException ie){ Thread.currentThread().interrupt(); throw new BusinessRejection("interrupted"); } catch (Exception e){ throw e; } finally { TFI.endSession(); }
    }

    private void sleepMs(int min, int max) throws InterruptedException { Thread.sleep(ThreadLocalRandom.current().nextInt(min,max+1)); }
    private void sleepNanosRange(long minNs, long maxNs) throws InterruptedException { if(maxNs<=minNs) return; long nanos=ThreadLocalRandom.current().nextLong(minNs, maxNs); if(nanos<=0)return; long ms=nanos/1_000_000L; int ns=(int)(nanos%1_000_000L); Thread.sleep(ms, ns); }

    private ExecutorService newExec(int threads){ boolean vt=Boolean.parseBoolean(System.getProperty("tfi.virtualThreads","true")); return vt? Executors.newVirtualThreadPerTaskExecutor(): Executors.newFixedThreadPool(threads); }
    private long usedHeap(){ Runtime r=Runtime.getRuntime(); return r.totalMemory()-r.freeMemory(); }

    static class BusinessRejection extends RuntimeException { BusinessRejection(String m){ super(m);} }
    static class BenchmarkResult { AtomicLong success=new AtomicLong(); AtomicLong reject=new AtomicLong(); AtomicLong errors=new AtomicLong(); AtomicLong totalNs=new AtomicLong(); LatencyWindow latencies=new LatencyWindow(Integer.getInteger("tfi.benchmark.latency.window",20_000)); ConcurrentMap<String, AtomicLong> errorByType=new ConcurrentHashMap<>(); }
    static class LatencyWindow { private final int cap; private final java.util.concurrent.atomic.AtomicLongArray buf; private final java.util.concurrent.atomic.AtomicLong idx=new java.util.concurrent.atomic.AtomicLong(); LatencyWindow(int c){ cap=Math.max(1000,c); buf=new java.util.concurrent.atomic.AtomicLongArray(cap);} void record(long ns){ long i=idx.getAndIncrement(); buf.set((int)(i%cap), ns);} long percentile(int p){ long n=Math.min(idx.get(),cap); if(n<=0)return 0; long[] a=new long[(int)n]; for(int i=0;i<n;i++) a[i]=buf.get(i); java.util.Arrays.sort(a); int r=Math.min((int)Math.ceil(p/100.0*a.length)-1, a.length-1); return r<0? a[0]:a[r]; } long max(){ long n=Math.min(idx.get(),cap), m=0; for(int i=0;i<n;i++){ long v=buf.get(i); if(v>m)m=v;} return m; } }
}
