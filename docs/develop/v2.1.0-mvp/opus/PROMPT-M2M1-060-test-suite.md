# PROMPT-M2M1-060-TestSuite 开发提示词

## 1) SYSTEM
你是**资深 Java 开发工程师**与**AI 结对编程引导者**。你需要基于下述"上下文与证据"，**按步骤**完成实现并给出**可提交的变更**（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../task/v2.1.0-vip/testing-quality/M2M1-060-TestSuite.md
- 相关代码：
  - src/test/java/com/syy/taskflowinsight#现有测试
  - src/main/java/com/syy/taskflowinsight/tracking#待测试模块
  - src/main/java/com/syy/taskflowinsight/changetracking.spring#Spring集成
- 测试框架：
  - JUnit 5 (Jupiter)
  - Spring Boot Test
  - Mockito
  - AssertJ
- 工程操作规范：../../develop/开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：建立覆盖所有核心模块的测试套件，关键路径覆盖≥80%
- 技术目标：
  - 单元测试全覆盖
  - 集成测试关键场景
  - compat/enhanced 双模式断言
  - 并发测试上下文隔离
  - 性能基线验证

## 4) SCOPE
- In Scope（当次实现必做）：
  - [ ] 创建测试基础设施（TestFixtures、TestData）
  - [ ] ObjectSnapshotDeep 单元测试
  - [ ] CollectionSummary 单元测试
  - [ ] PathMatcherCache 单元测试
  - [ ] CompareService 单元测试
  - [x] DiffDetector 扩展测试
  - [x] JsonExporter compat/enhanced 测试
  - [x] Spring Boot 集成测试
  - [ ] Actuator 端点测试
  - [x] 并发场景测试
- Out of Scope（排除项）：
  - [ ] 性能压测（单独任务）
  - [ ] 混沌测试

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 测试基础设施：
```java
// TestFixtures.java
public class TestFixtures {
    // 测试数据构建器
    public static class UserBuilder {
        private String id = UUID.randomUUID().toString();
        private String name = "TestUser";
        private int age = 25;
        private Address address;
        private List<Order> orders = new ArrayList<>();
        
        public UserBuilder withName(String name) {
            this.name = name;
            return this;
        }
        
        public User build() {
            return new User(id, name, age, address, orders);
        }
    }
    
    // 预定义测试对象
    public static User simpleUser() {
        return new UserBuilder().build();
    }
    
    public static User complexUser() {
        return new UserBuilder()
            .withAddress(new Address("Street", "City"))
            .withOrders(List.of(
                new Order("O1", 100.0),
                new Order("O2", 200.0)
            ))
            .build();
    }
    
    public static User userWithCycle() {
        User user = simpleUser();
        user.setManager(user); // 自引用
        return user;
    }
}

// TestDataSets.java
public class TestDataSets {
    // 边界值数据集
    public static Stream<Arguments> depthTestCases() {
        return Stream.of(
            Arguments.of(0, 1),   // depth=0, 期望1个字段
            Arguments.of(1, 5),   // depth=1, 期望5个字段
            Arguments.of(2, 15),  // depth=2, 期望15个字段
            Arguments.of(3, 35)   // depth=3, 期望35个字段
        );
    }
    
    // 集合数据集
    public static Stream<Arguments> collectionTestCases() {
        return Stream.of(
            Arguments.of(List.of(), 0, false),           // 空集合
            Arguments.of(List.of(1), 1, false),          // 单元素
            Arguments.of(range(0, 999), 999, false),     // 边界内
            Arguments.of(range(0, 1001), 1001, true)     // 降级
        );
    }
}
```

2. 核心模块单元测试：
```java
// ObjectSnapshotDeepTest.java
@ExtendWith(MockitoExtension.class)
class ObjectSnapshotDeepTest {
    
    private ObjectSnapshotDeep snapshot;
    private SnapshotConfig config;
    
    @BeforeEach
    void setUp() {
        config = new SnapshotConfig();
        config.setMaxDepth(3);
        config.setMaxStackDepth(1000);
        snapshot = new ObjectSnapshotDeep();
    }
    
    @Test
    @DisplayName("应该正确处理简单对象")
    void shouldCaptureSimpleObject() {
        // Given
        User user = TestFixtures.simpleUser();
        
        // When
        Map<String, Object> result = snapshot.captureDeep(user, config);
        
        // Then
        assertThat(result)
            .containsKeys("id", "name", "age")
            .hasSize(3);
        assertThat(result.get("name")).isEqualTo(user.getName());
    }
    
    @Test
    @DisplayName("应该遵守深度限制")
    void shouldRespectDepthLimit() {
        // Given
        User user = TestFixtures.complexUser();
        config.setMaxDepth(1);
        
        // When
        Map<String, Object> result = snapshot.captureDeep(user, config);
        
        // Then
        assertThat(result).doesNotContainKey("address.street");
        assertThat(result).containsKey("address"); // 但包含摘要
    }
    
    @Test
    @DisplayName("应该检测循环引用")
    void shouldDetectCycle() {
        // Given
        User user = TestFixtures.userWithCycle();
        
        // When
        Map<String, Object> result = snapshot.captureDeep(user, config);
        
        // Then
        assertThat(result).containsEntry("manager", "[CIRCULAR]");
    }
    
    @ParameterizedTest
    @MethodSource("com.syy.taskflowinsight.test.TestDataSets#depthTestCases")
    @DisplayName("深度参数化测试")
    void depthParameterizedTest(int depth, int expectedFields) {
        // Given
        config.setMaxDepth(depth);
        User user = TestFixtures.complexUser();
        
        // When
        Map<String, Object> result = snapshot.captureDeep(user, config);
        
        // Then
        assertThat(result).hasSize(expectedFields);
    }
}

// CollectionSummaryTest.java
class CollectionSummaryTest {
    
    private CollectionSummary collectionSummary;
    private SummaryConfig config;
    
    @BeforeEach
    void setUp() {
        config = new SummaryConfig();
        config.setMaxSize(1000);
        config.setMaxExamples(10);
        collectionSummary = new CollectionSummary();
    }
    
    @Test
    @DisplayName("应该生成正确的摘要")
    void shouldGenerateSummary() {
        // Given
        List<String> data = List.of("apple", "banana", "cherry");
        
        // When
        Summary summary = collectionSummary.summarize("fruits", data, config);
        
        // Then
        assertThat(summary.getSize()).isEqualTo(3);
        assertThat(summary.getKind()).isEqualTo("List");
        assertThat(summary.getExamples()).containsExactly("apple", "banana", "cherry");
        assertThat(summary.isDegraded()).isFalse();
    }
    
    @Test
    @DisplayName("应该脱敏敏感词")
    void shouldSanitizeSensitiveData() {
        // Given
        List<String> data = List.of("user123", "password123", "token456");
        
        // When
        Summary summary = collectionSummary.summarize("auth", data, config);
        
        // Then
        assertThat(summary.getExamples())
            .contains("user123", "***REDACTED***");
    }
}
```

3. 集成测试：
```java
// ChangeTrackingIntegrationTest.java
@SpringBootTest
@AutoConfigureMockMvc
class ChangeTrackingIntegrationTest {
    
    @Autowired
    private ChangeTracker changeTracker;
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @DisplayName("完整变更追踪流程")
    void fullChangeTrackingFlow() {
        // Given
        User before = TestFixtures.simpleUser();
        User after = TestFixtures.simpleUser();
        after.setName("ModifiedName");
        after.setAge(30);
        
        // When
        changeTracker.startTracking("session-1", "trace-1");
        changeTracker.capture("before", before);
        changeTracker.capture("after", after);
        List<Change> changes = changeTracker.detectChanges();
        
        // Then
        assertThat(changes)
            .hasSize(2)
            .extracting(Change::getPath)
            .containsExactlyInAnyOrder("name", "age");
    }
    
    @Test
    @DisplayName("Actuator端点可访问")
    void actuatorEndpointAccessible() throws Exception {
        // When & Then
        mockMvc.perform(get("/actuator/tfi/effective-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value("2.1.0"))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.configuration.snapshot.maxDepth").value(3));
    }
}

// CompatibilityTest.java
@SpringBootTest
class CompatibilityTest {
    
    @Autowired
    private JsonExporter jsonExporter;
    
    @Test
    @DisplayName("compat模式向后兼容")
    void compatModeBackwardCompatible() {
        // Given
        List<Change> changes = createTestChanges();
        ExportContext context = ExportContext.builder()
            .mode(ExportMode.COMPAT)
            .build();
        
        // When
        String json = jsonExporter.export(changes, context);
        JsonNode root = objectMapper.readTree(json);
        
        // Then
        // 验证只包含旧字段
        JsonNode change = root.get("changes").get(0);
        assertThat(change.has("path")).isTrue();
        assertThat(change.has("oldValue")).isTrue();
        assertThat(change.has("newValue")).isTrue();
        assertThat(change.has("valueKind")).isFalse(); // 新字段不出现
    }
    
    @Test
    @DisplayName("enhanced模式包含新字段")
    void enhancedModeIncludesNewFields() {
        // Given
        List<Change> changes = createTestChanges();
        ExportContext context = ExportContext.builder()
            .mode(ExportMode.ENHANCED)
            .build();
        
        // When
        String json = jsonExporter.export(changes, context);
        JsonNode root = objectMapper.readTree(json);
        
        // Then
        JsonNode change = root.get("changes").get(0);
        assertThat(change.has("valueKind")).isTrue();
        assertThat(change.has("valueRepr")).isTrue();
        assertThat(change.has("threadId")).isTrue();
    }
}
```

4. 并发测试：
```java
// ConcurrencyTest.java
class ConcurrencyTest {
    
    @Test
    @DisplayName("并发快照上下文隔离")
    void concurrentSnapshotIsolation() throws Exception {
        // Given
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // When
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    User user = TestFixtures.simpleUser();
                    user.setName("User-" + index);
                    return snapshot.captureDeep(user, config);
                } finally {
                    latch.countDown();
                }
            }));
        }
        
        latch.await(5, TimeUnit.SECONDS);
        
        // Then
        Set<String> names = new HashSet<>();
        for (Future<Map<String, Object>> future : futures) {
            Map<String, Object> result = future.get();
            names.add((String) result.get("name"));
        }
        
        assertThat(names).hasSize(threadCount); // 无串扰
    }
}
```

## 6) DELIVERABLES（输出必须包含）
- 测试代码：
  - 基础设施：TestFixtures.java, TestDataSets.java
  - 单元测试：各模块Test类
  - 集成测试：IntegrationTest类
  - 并发测试：ConcurrencyTest.java
- 测试配置：
  - application-test.yml
  - 测试数据文件
- 覆盖率报告：
  - JaCoCo 配置
  - 覆盖率 ≥ 80%

## 7) API & MODELS（必须具体化）
- 测试注解使用：
  - @Test - 基础测试
  - @ParameterizedTest - 参数化测试
  - @DisplayName - 测试描述
  - @SpringBootTest - 集成测试
- 断言框架：
  - AssertJ fluent API
  - JsonPath 验证
- Mock策略：
  - 外部依赖 Mock
  - 核心逻辑不 Mock

## 8) DATA & STORAGE
- 测试数据：内存构建
- 测试隔离：@DirtiesContext
- 数据清理：@AfterEach

## 9) PERFORMANCE & RELIABILITY
- 单测执行：< 1秒/测试
- 集成测试：< 5秒/测试
- 并发测试：线程安全验证
- 测试稳定性：无随机失败

## 10) TEST PLAN（可运行、可断言）
- 测试覆盖：
  - [ ] 行覆盖率 ≥ 80%
  - [ ] 分支覆盖率 ≥ 70%
  - [ ] 关键路径 100%
- 测试类型：
  - [ ] 单元测试：快速、隔离
  - [ ] 集成测试：端到端
  - [ ] 并发测试：线程安全
  - [ ] 兼容测试：compat/enhanced

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 覆盖率达标
- [ ] 测试全部通过
- [ ] 无随机失败
- [ ] 执行时间合理
- [ ] 文档完整

## 12) RISKS & MITIGATIONS
- 测试耦合：测试间依赖 → 独立数据构建
- 执行慢：大量集成测试 → 分层测试策略
- 维护难：测试代码复杂 → 测试基础设施

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 建议使用 @Nested 组织测试类

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 问题1：是否需要契约测试？
  - 责任人：架构组
  - 期限：下版本评估
  - 所需：消费方接口规范
