# M2M1-060: 测试套件

## 任务概述

| 属性 | 值 |
|------|-----|
| 任务ID | M2M1-060 |
| 任务名称 | 测试套件 |
| 所属模块 | 测试与质量 (Testing & Quality) |
| 优先级 | P1 |
| 预估工期 | L (5-8天) |
| 依赖任务 | M2M1-001至M2M1-050 |

## 背景

TaskFlow Insight需要完整的测试覆盖来保证代码质量。需要建立单元测试、集成测试、并发测试框架，确保各模块功能正确、性能达标、并发安全。

## 目标

1. 建立完整的单元测试体系
2. 实现关键场景集成测试
3. 提供并发安全测试框架
4. 建立测试数据生成器
5. 实现测试覆盖率监控

## 非目标

- 不实现UI自动化测试
- 不提供性能压测平台
- 不实现混沌工程测试
- 不支持分布式测试

## 实现要点

### 1. 测试基础设施

```java
@TestConfiguration
public class TfiTestConfiguration {
    
    @Bean
    @Primary
    public TestDataGenerator testDataGenerator() {
        return new TestDataGenerator();
    }
    
    @Bean
    @Primary
    public MockTimeProvider timeProvider() {
        return new MockTimeProvider();
    }
    
    @Bean
    @Primary
    public InMemoryStore testStore() {
        return new InMemoryStore();
    }
}

// 测试基类
@SpringBootTest
@Import(TfiTestConfiguration.class)
@ActiveProfiles("test")
public abstract class TfiTestBase {
    
    @Autowired
    protected TestDataGenerator dataGenerator;
    
    @Autowired
    protected MockTimeProvider timeProvider;
    
    @BeforeEach
    public void setUp() {
        timeProvider.reset();
        clearCaches();
    }
    
    protected void clearCaches() {
        // 清理所有缓存
    }
}
```

### 2. 单元测试框架

```java
// 快照测试
@Test
class ObjectSnapshotDeepTest extends TfiTestBase {
    
    @Autowired
    private ObjectSnapshotDeep snapshotDeep;
    
    @Test
    void testSimpleObject() {
        // 准备测试数据
        TestObject obj = dataGenerator.simpleObject();
        
        // 执行快照
        Map<String, FieldSnapshot> result = 
            snapshotDeep.traverse(obj, 3);
        
        // 验证结果
        assertThat(result)
            .hasSize(5)
            .containsKeys("name", "age", "email", "active", "createdAt");
            
        assertThat(result.get("name").getValue())
            .isEqualTo(obj.getName());
    }
    
    @Test
    void testNestedObject() {
        TestObject obj = dataGenerator.nestedObject(3);
        
        Map<String, FieldSnapshot> result = 
            snapshotDeep.traverse(obj, 3);
        
        assertThat(result)
            .containsKeys(
                "user.profile.name",
                "user.profile.address.city",
                "user.profile.address.zipCode"
            );
    }
    
    @Test
    void testCircularReference() {
        TestObject obj = dataGenerator.circularReference();
        
        Map<String, FieldSnapshot> result = 
            snapshotDeep.traverse(obj, 5);
        
        // 验证循环引用被正确检测
        assertThat(result.values())
            .filteredOn(FieldSnapshot::isCircular)
            .hasSize(1);
    }
    
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 5, 10})
    void testDepthLimit(int maxDepth) {
        TestObject obj = dataGenerator.deeplyNested(10);
        
        Map<String, FieldSnapshot> result = 
            snapshotDeep.traverse(obj, maxDepth);
        
        // 验证深度限制
        int actualMaxDepth = result.keySet().stream()
            .mapToInt(path -> path.split("\\.").length)
            .max()
            .orElse(0);
            
        assertThat(actualMaxDepth)
            .isLessThanOrEqualTo(maxDepth);
    }
}
```

### 3. 集成测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class TfiIntegrationTest extends TfiTestBase {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private SnapshotFacade snapshotFacade;
    
    @Autowired
    private CaffeineStore store;
    
    @Test
    void testEndToEndFlow() throws Exception {
        // 1. 创建初始快照
        Order orderV1 = dataGenerator.order("ORD-001");
        Map<String, FieldSnapshot> snapshot1 = 
            snapshotFacade.takeSnapshot(orderV1, null);
        
        // 2. 存储快照
        SnapshotEntry entry1 = new SnapshotEntry(
            "snap-1", "session-1", 
            System.currentTimeMillis(), snapshot1, null
        );
        store.storeSnapshot(entry1);
        
        // 3. 修改对象
        Order orderV2 = dataGenerator.modifyOrder(orderV1);
        Map<String, FieldSnapshot> snapshot2 = 
            snapshotFacade.takeSnapshot(orderV2, null);
        
        // 4. 计算差异
        DiffDetector diffDetector = new DiffDetector();
        List<DiffResult> diffs = 
            diffDetector.detect(snapshot1, snapshot2);
        
        // 5. 导出结果
        ExportRequest request = ExportRequest.builder()
            .sessionId("session-1")
            .format(ExportFormat.JSONL)
            .build();
            
        ExportResult result = jsonExporter.export(request);
        
        // 6. 验证Actuator端点
        mockMvc.perform(get("/actuator/tfi/effective-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.snapshot.maxDepth").value(3));
    }
}
```

### 4. 并发测试框架

```java
@Test
class ConcurrencyTest extends TfiTestBase {
    
    @Autowired
    private PathMatcherCache pathMatcherCache;
    
    @Autowired
    private CaffeineStore store;
    
    @Test
    void testConcurrentCacheAccess() throws InterruptedException {
        int threadCount = 100;
        int operationsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String pattern = "user." + (j % 10) + ".*";
                        String path = "user." + threadId + ".field" + j;
                        
                        boolean result = pathMatcherCache.match(pattern, path);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        
        assertThat(successCount.get())
            .isEqualTo(threadCount * operationsPerThread);
        assertThat(errorCount.get()).isZero();
    }
    
    @Test
    void testStoreRaceCondition() {
        int writers = 50;
        int readers = 50;
        CyclicBarrier barrier = new CyclicBarrier(writers + readers);
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // 写入线程
        for (int i = 0; i < writers; i++) {
            final int id = i;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    barrier.await();
                    SnapshotEntry entry = dataGenerator.snapshotEntry("w-" + id);
                    store.storeSnapshot(entry);
                } catch (Exception e) {
                    fail("Writer failed", e);
                }
            }));
        }
        
        // 读取线程
        for (int i = 0; i < readers; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    barrier.await();
                    List<SnapshotEntry> results = 
                        store.querySnapshots("session-1", 0, Long.MAX_VALUE);
                    assertThat(results).isNotNull();
                } catch (Exception e) {
                    fail("Reader failed", e);
                }
            }));
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .join();
    }
}
```

### 5. 测试数据生成器

```java
@Component
public class TestDataGenerator {
    private final Faker faker = new Faker();
    private final Random random = new Random();
    
    public TestObject simpleObject() {
        return TestObject.builder()
            .id(UUID.randomUUID().toString())
            .name(faker.name().fullName())
            .age(random.nextInt(100))
            .email(faker.internet().emailAddress())
            .active(random.nextBoolean())
            .createdAt(Instant.now())
            .build();
    }
    
    public TestObject nestedObject(int depth) {
        if (depth <= 0) {
            return simpleObject();
        }
        
        TestObject obj = simpleObject();
        obj.setNested(nestedObject(depth - 1));
        obj.setChildren(IntStream.range(0, 3)
            .mapToObj(i -> nestedObject(depth - 1))
            .collect(Collectors.toList()));
            
        return obj;
    }
    
    public TestObject circularReference() {
        TestObject parent = simpleObject();
        TestObject child = simpleObject();
        parent.setNested(child);
        child.setNested(parent); // 循环引用
        return parent;
    }
    
    public Order order(String orderId) {
        return Order.builder()
            .orderId(orderId)
            .customerId(faker.idNumber().valid())
            .items(generateOrderItems(random.nextInt(5) + 1))
            .totalAmount(BigDecimal.valueOf(faker.number().randomDouble(2, 10, 1000)))
            .status(OrderStatus.values()[random.nextInt(OrderStatus.values().length)])
            .createdAt(LocalDateTime.now())
            .build();
    }
    
    public List<OrderItem> generateOrderItems(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> OrderItem.builder()
                .productId(faker.commerce().productName())
                .quantity(random.nextInt(10) + 1)
                .price(BigDecimal.valueOf(faker.number().randomDouble(2, 1, 100)))
                .build())
            .collect(Collectors.toList());
    }
}
```

## 测试要求

### 测试覆盖

1. **单元测试**
   - 行覆盖率 > 80%
   - 分支覆盖率 > 70%
   - 异常路径覆盖

2. **集成测试**
   - 主要流程覆盖
   - 配置场景测试
   - 错误处理验证

3. **并发测试**
   - 线程安全验证
   - 竞态条件测试
   - 死锁检测

### 测试性能

- 单元测试总时长 < 30s
- 集成测试总时长 < 2min
- 并发测试稳定性 > 99%

## 验收标准

### 功能验收

- [ ] 单元测试完整
- [ ] 集成测试通过
- [ ] 并发测试稳定
- [ ] 测试数据丰富
- [ ] 覆盖率达标

### 质量验收

- [ ] 测试代码规范
- [ ] 断言清晰准确
- [ ] 错误信息明确
- [ ] 文档完整

### 维护性

- [ ] 测试易于理解
- [ ] 数据易于生成
- [ ] 可独立运行
- [ ] CI/CD集成

## 风险评估

### 技术风险

1. **R037: 测试不稳定**
   - 缓解：隔离环境
   - 重试：失败重试

2. **R038: 测试太慢**
   - 缓解：并行执行
   - 优化：分层测试

3. **R039: 覆盖率不足**
   - 缓解：强制门槛
   - 工具：覆盖率报告

### 依赖风险

- 测试框架版本

## 需要澄清

1. 覆盖率目标值
2. 性能测试基线
3. CI/CD集成方式

## 代码示例

### Maven配置

```xml
<dependencies>
    <!-- 测试依赖 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>com.github.javafaker</groupId>
        <artifactId>javafaker</artifactId>
        <version>1.0.2</version>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>org.awaitility</groupId>
        <artifactId>awaitility</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <executions>
                <execution>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 测试配置

```yaml
# application-test.yml
tfi:
  enabled: true
  snapshot:
    max-depth: 5
    max-fields: 100
  store:
    type: memory
    max-snapshots: 1000
  
logging:
  level:
    com.syy.taskflowinsight: DEBUG

spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
```

## 实施计划

### Day 1-2: 基础框架
- 测试配置
- 数据生成器
- 基类实现

### Day 3-4: 单元测试
- 核心模块测试
- 工具类测试
- 异常测试

### Day 5-6: 集成测试
- 端到端流程
- 配置测试
- Actuator测试

### Day 7-8: 并发与优化
- 并发测试
- 性能验证
- 覆盖率提升

## 参考资料

1. Spring Boot测试最佳实践
2. AssertJ使用指南
3. 并发测试框架设计

---

*文档版本*: v1.0.0  
*创建日期*: 2025-01-12  
*状态*: 待开发