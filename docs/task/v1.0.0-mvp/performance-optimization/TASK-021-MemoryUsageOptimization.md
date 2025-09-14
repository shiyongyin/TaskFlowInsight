# TASK-021: 内存使用优化实现

> ⚠️ **注意**: 此任务为**后期优化**任务，不在MVP范围内。MVP阶段采用简单的对象模型即可。

## 背景

内存使用是TaskFlowInsight性能的关键指标之一，系统设计目标是在1000+并发线程场景下内存使用不超过5MB。需要对所有组件进行内存使用优化，减少对象分配、优化数据结构、实现高效的内存管理策略。

## 目标

### 主要目标
1. **对象分配优化**：减少不必要的对象创建和GC压力
2. **数据结构优化**：选择内存效率最高的数据结构
3. **内存池化管理**：重用对象减少分配开销
4. **内存使用监控**：实时监控和预警机制

### 次要目标
1. **字符串优化**：减少字符串对象的创建和驻留
2. **集合类优化**：优化集合类的内存使用
3. **缓存策略优化**：平衡缓存效果和内存使用

## 实现方案

### 1. 对象池化管理器
```java
public final class ObjectPoolManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectPoolManager.class);
    
    // 各种对象池
    private static final ObjectPool<StringBuilder> stringBuilderPool = new StringBuilderPool();
    private static final ObjectPool<ArrayList<?>> arrayListPool = new ArrayListPool();
    private static final ObjectPool<HashMap<?, ?>> hashMapPool = new HashMapPool();
    
    /**
     * 获取StringBuilder实例
     */
    public static StringBuilder borrowStringBuilder() {
        return stringBuilderPool.borrow();
    }
    
    /**
     * 归还StringBuilder实例
     */
    public static void returnStringBuilder(StringBuilder sb) {
        if (sb != null) {
            sb.setLength(0); // 清空内容
            stringBuilderPool.returnObject(sb);
        }
    }
    
    /**
     * 获取ArrayList实例
     */
    @SuppressWarnings("unchecked")
    public static <T> ArrayList<T> borrowArrayList() {
        return (ArrayList<T>) arrayListPool.borrow();
    }
    
    /**
     * 归还ArrayList实例
     */
    public static void returnArrayList(ArrayList<?> list) {
        if (list != null) {
            list.clear();
            arrayListPool.returnObject(list);
        }
    }
    
    /**
     * 带自动管理的字符串构建器
     */
    public static String buildString(StringBuilderFunction function) {
        StringBuilder sb = borrowStringBuilder();
        try {
            function.apply(sb);
            return sb.toString();
        } finally {
            returnStringBuilder(sb);
        }
    }
    
    @FunctionalInterface
    public interface StringBuilderFunction {
        void apply(StringBuilder sb);
    }
}

/**
 * 基础对象池接口
 */
public interface ObjectPool<T> {
    T borrow();
    void returnObject(T object);
    int size();
    void clear();
}

/**
 * StringBuilder对象池实现
 */
public static final class StringBuilderPool implements ObjectPool<StringBuilder> {
    private final ConcurrentLinkedQueue<StringBuilder> pool = new ConcurrentLinkedQueue<>();
    private final AtomicInteger poolSize = new AtomicInteger(0);
    private static final int MAX_POOL_SIZE = 64; // 每个池最大64个对象
    private static final int INITIAL_CAPACITY = 256; // StringBuilder初始容量
    
    @Override
    public StringBuilder borrow() {
        StringBuilder sb = pool.poll();
        if (sb == null) {
            sb = new StringBuilder(INITIAL_CAPACITY);
        } else {
            poolSize.decrementAndGet();
        }
        return sb;
    }
    
    @Override
    public void returnObject(StringBuilder sb) {
        if (sb != null && poolSize.get() < MAX_POOL_SIZE) {
            // 限制StringBuilder的容量避免内存膨胀
            if (sb.capacity() > INITIAL_CAPACITY * 4) {
                sb = new StringBuilder(INITIAL_CAPACITY);
            }
            pool.offer(sb);
            poolSize.incrementAndGet();
        }
    }
    
    @Override
    public int size() {
        return poolSize.get();
    }
    
    @Override
    public void clear() {
        pool.clear();
        poolSize.set(0);
    }
}
```

### 2. 内存优化的数据结构
```java
/**
 * 内存优化的TaskNode实现
 */
public final class OptimizedTaskNode {
    // 使用原始类型减少包装对象
    private final long nodeIdHash;        // 使用hash代替完整UUID字符串
    private final int nameHash;           // 使用hash代替完整name字符串  
    private final byte depth;             // 使用byte代替int（深度不会超过255）
    private final long startNano;         // 纳秒时间戳
    private volatile long endNano;        // 结束时间
    
    // 子节点使用数组而非List（避免ArrayList的开销）
    private volatile OptimizedTaskNode[] children;
    private volatile int childCount;
    
    // 消息使用紧凑存储
    private volatile CompactMessage[] messages;
    private volatile int messageCount;
    
    public OptimizedTaskNode(String nodeId, String name, int depth, long startNano) {
        this.nodeIdHash = nodeId.hashCode() | ((long)nodeId.length() << 32); // 包含长度信息
        this.nameHash = name.hashCode();
        this.depth = (byte) Math.min(depth, 255);
        this.startNano = startNano;
        this.endNano = 0;
        this.children = null; // 延迟初始化
        this.childCount = 0;
        this.messages = null; // 延迟初始化
        this.messageCount = 0;
    }
    
    /**
     * 添加子节点（内存优化）
     */
    public void addChild(OptimizedTaskNode child) {
        synchronized (this) {
            if (children == null) {
                children = new OptimizedTaskNode[4]; // 初始容量4
            } else if (childCount >= children.length) {
                // 扩容（2倍增长）
                OptimizedTaskNode[] newChildren = new OptimizedTaskNode[children.length * 2];
                System.arraycopy(children, 0, newChildren, 0, childCount);
                children = newChildren;
            }
            children[childCount++] = child;
        }
    }
    
    /**
     * 添加消息（紧凑存储）
     */
    public void addMessage(String message, long timestamp) {
        synchronized (this) {
            if (messages == null) {
                messages = new CompactMessage[2]; // 初始容量2
            } else if (messageCount >= messages.length) {
                CompactMessage[] newMessages = new CompactMessage[messages.length * 2];
                System.arraycopy(messages, 0, newMessages, 0, messageCount);
                messages = newMessages;
            }
            messages[messageCount++] = new CompactMessage(message, timestamp);
        }
    }
}

/**
 * 紧凑的消息存储
 */
public static final class CompactMessage {
    private final int messageHash;    // 消息hash
    private final short messageLength; // 消息长度
    private final long timestamp;     // 时间戳
    
    // 使用弱引用缓存原始消息（可被GC回收）
    private final WeakReference<String> originalMessage;
    
    public CompactMessage(String message, long timestamp) {
        this.messageHash = message.hashCode();
        this.messageLength = (short) Math.min(message.length(), Short.MAX_VALUE);
        this.timestamp = timestamp;
        this.originalMessage = new WeakReference<>(message);
    }
    
    public String getMessage() {
        String cached = originalMessage.get();
        if (cached != null) {
            return cached;
        }
        // 如果原始消息被GC，返回hash表示
        return "Message#" + Integer.toHexString(messageHash) + "[" + messageLength + "]";
    }
}
```

### 3. 内存使用监控器
```java
public final class MemoryUsageMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryUsageMonitor.class);
    
    private static final AtomicLong totalAllocatedObjects = new AtomicLong(0);
    private static final AtomicLong totalDeallocatedObjects = new AtomicLong(0);
    private static final AtomicLong peakMemoryUsage = new AtomicLong(0);
    
    // 各组件内存使用统计
    private static final ConcurrentHashMap<String, ComponentMemoryStats> componentStats = new ConcurrentHashMap<>();
    
    /**
     * 记录对象分配
     */
    public static void recordAllocation(String component, int size) {
        totalAllocatedObjects.incrementAndGet();
        
        componentStats.compute(component, (key, stats) -> {
            if (stats == null) {
                stats = new ComponentMemoryStats(component);
            }
            stats.recordAllocation(size);
            return stats;
        });
    }
    
    /**
     * 记录对象释放
     */
    public static void recordDeallocation(String component, int size) {
        totalDeallocatedObjects.incrementAndGet();
        
        ComponentMemoryStats stats = componentStats.get(component);
        if (stats != null) {
            stats.recordDeallocation(size);
        }
    }
    
    /**
     * 获取内存使用报告
     */
    public static MemoryUsageReport generateReport() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        
        long currentHeapUsage = heapUsage.getUsed();
        peakMemoryUsage.updateAndGet(current -> Math.max(current, currentHeapUsage));
        
        Map<String, ComponentMemoryStats> statsSnapshot = new HashMap<>(componentStats);
        
        return new MemoryUsageReport(
            currentHeapUsage,
            heapUsage.getMax(),
            peakMemoryUsage.get(),
            totalAllocatedObjects.get(),
            totalDeallocatedObjects.get(),
            statsSnapshot
        );
    }
    
    /**
     * 检查内存使用是否超过阈值
     */
    public static boolean checkMemoryThreshold(long thresholdBytes) {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long currentUsage = memoryBean.getHeapMemoryUsage().getUsed();
        
        if (currentUsage > thresholdBytes) {
            LOGGER.warn("Memory usage {} MB exceeds threshold {} MB", 
                currentUsage / 1024 / 1024, thresholdBytes / 1024 / 1024);
            return false;
        }
        return true;
    }
    
    public static final class ComponentMemoryStats {
        private final String componentName;
        private final AtomicLong allocatedBytes = new AtomicLong(0);
        private final AtomicLong deallocatedBytes = new AtomicLong(0);
        private final AtomicLong peakBytes = new AtomicLong(0);
        private final AtomicLong allocationCount = new AtomicLong(0);
        
        public ComponentMemoryStats(String componentName) {
            this.componentName = componentName;
        }
        
        public void recordAllocation(int size) {
            allocatedBytes.addAndGet(size);
            allocationCount.incrementAndGet();
            peakBytes.updateAndGet(current -> Math.max(current, getCurrentUsage()));
        }
        
        public void recordDeallocation(int size) {
            deallocatedBytes.addAndGet(size);
        }
        
        public long getCurrentUsage() {
            return allocatedBytes.get() - deallocatedBytes.get();
        }
        
        // getters...
    }
}
```

## 测试标准

### 内存使用要求
1. **基础内存占用**：<1MB（空闲状态）
2. **1000线程场景**：<5MB总内存使用
3. **对象分配率**：<100KB/s持续分配
4. **GC影响**：GC暂停时间<10ms

### 内存优化效果
1. **对象分配减少**：相比基础版本减少50%+
2. **内存回收效率**：内存能够及时回收
3. **内存碎片控制**：长期运行无内存碎片积累
4. **缓存效率**：缓存命中率>80%

## 验收标准

### 功能验收
- [ ] 对象池化管理器工作正常
- [ ] 优化数据结构功能完整
- [ ] 内存使用监控准确
- [ ] 智能缓存管理有效

### 性能验收
- [ ] 内存使用满足<5MB要求
- [ ] 对象分配减少>50%
- [ ] GC压力显著降低
- [ ] 缓存命中率>80%

## 依赖关系

### 前置依赖
- TASK-001: Session会话模型
- TASK-002: TaskNode任务节点  
- TASK-017: 内存泄漏检测机制

### 后置依赖
- TASK-022: CPU开销优化实现
- TASK-023: 综合性能调优实现

## 实施计划

### 第8周（3天）
- **Day 1**: 对象池化和优化数据结构
- **Day 2**: 内存监控和智能缓存
- **Day 3**: 内存优化测试和验证

## 交付物

1. **对象池管理器** (`ObjectPoolManager.java`)
2. **优化数据结构** (`OptimizedTaskNode.java`, `CompactMessage.java`)
3. **内存使用监控器** (`MemoryUsageMonitor.java`)
4. **智能缓存管理器** (`IntelligentCacheManager.java`)
5. **内存优化工具类** (`MemoryOptimizationUtils.java`)
6. **内存使用测试套件** (`MemoryOptimizationTest.java`)