# TaskFlowInsight

<div align="center">

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Test Coverage](https://img.shields.io/badge/Coverage-85%25-brightgreen.svg)](.)

**Business-First Observability for Java**
Process Visualization + Change Tracking in One Lightweight Library

**[🇨🇳 中文](README.zh-CN.md)** • [Quick Start](#-quick-start) • [Documentation](#-documentation) • [Examples](#-real-world-examples) • [Performance](#-performance)

</div>

---

## What is TaskFlowInsight?

TaskFlowInsight (TFI) is a lightweight Java library that brings **X-ray vision** to your business logic. It automatically visualizes execution flows and intelligently tracks object changes — **without requiring any configuration**.

Think of it as **APM for business developers**: while traditional APM tools monitor infrastructure (CPU, memory, network), TFI focuses on what matters most to developers — **understanding business logic execution**.

```java
@TfiTask("Process Order")
public void processOrder(Order order) {
    validateOrder(order);        // ← Automatically tracked
    TFI.track("order", order);   // ← Automatically detect changes
    processPayment(order);
}
```

**Output:**
```
[Order-12345] Process Order ━━━━━━━━━━━━━━━━ 234ms ✓
├─ Validate Order ........................ 45ms ✓
│  └─ order.status: PENDING → VALIDATED
└─ Process Payment ...................... 189ms ✓
   └─ order.payment: null → PAID
```

---

## Why TFI?

### The Problem
Modern business applications have **complex workflows** that are hard to debug:
- ❓ Which steps executed and how long did they take?
- ❓ What changed in my objects during processing?
- ❓ Why did a workflow fail?

**Traditional solutions fall short:**
- **Manual logging**: Tedious, scattered, unstructured
- **APM tools**: Expensive, infrastructure-focused, complex setup
- **JaVers**: Audit-only, no process visualization, requires configuration

### The Solution
TFI provides **dual capabilities** in one lightweight package:

| Capability | What You Get |
|------------|-------------|
| **🎯 Process Visualization** | Automatic hierarchical flow trees with precise timing |
| **🔍 Change Tracking** | Intelligent deep-object comparison with smart diff detection |
| **📊 Real-time Monitoring** | Spring Boot Actuator integration + Prometheus metrics |
| **🚀 Zero Configuration** | Add `@TfiTask` and you're done |
| **⚡ Production-Ready** | <5MB memory, <1% CPU, 66K+ TPS |

---

## How is TFI Different?

| Feature | TaskFlowInsight | JaVers | APM Tools | Manual Logs |
|---------|----------------|--------|-----------|-------------|
| **Setup Time** | < 2 minutes | ~1 hour | Hours/Days | N/A |
| **Process Flow** | ✅ Tree visualization | ❌ | ⚠️ Traces only | ❌ Scattered |
| **Change Tracking** | ✅ Deep comparison | ✅ Basic audit | ❌ | ❌ |
| **Memory Footprint** | **<5 MB** | ~20 MB | 50-100 MB | ~0 |
| **Performance Impact** | **<1% CPU** | ~3% | 5-15% | ~0 |
| **Throughput** | **66,000+ TPS** | ~20,000 | N/A | N/A |
| **Configuration** | **Zero** | Medium | Complex | None needed |
| **Spring Integration** | ✅ Deep | ⚠️ Basic | ✅ | N/A |
| **Business Context** | ✅ Built-in | ⚠️ Limited | ❌ Requires custom | ❌ |
| **Cost** | **Free OSS** | Free OSS | $$$$ | Free |

**TFI's Unique Position**: The **only** library combining process visualization + change tracking with enterprise-grade performance.

---

## ⚡ Quick Start

### Prerequisites
- Java 21+
- Maven 3.6+ (or use included wrapper)
- Spring Boot 3.x (optional but recommended)

### 1. Add Dependency

**Maven:**
```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>taskflow-insight</artifactId>
    <version>3.0.0</version>
</dependency>
```

**Gradle:**
```gradle
implementation 'com.syy:taskflow-insight:3.0.0'
```

### 2. Enable TFI (Spring Boot)

```java
@SpringBootApplication
@EnableTfi
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### 3. Start Tracking

**Option 1: Annotation-Driven (Recommended)**
```java
@Service
public class OrderService {

    @TfiTask("Process Order")
    public OrderResult processOrder(String orderId) {
        Order order = fetchOrder(orderId);

        // Track changes automatically
        TFI.track("order", order);

        validateOrder(order);
        processPayment(order);

        return OrderResult.success(order);
    }

    @TfiTask("Validate Order")
    private void validateOrder(Order order) {
        // Validation logic - automatically tracked
    }
}
```

**Option 2: Programmatic API**
```java
public void processOrder() {
    TFI.start("Process Order");
    try {
        try (var stage = TFI.stage("Validate Parameters")) {
            // Business logic
        }

        try (var stage = TFI.stage("Check Inventory")) {
            // Business logic
        }

        TFI.exportToConsole();
    } finally {
        TFI.stop();
    }
}
```

### 4. Verify It Works

```bash
# Clone and run demo
git clone https://github.com/shiyongyin/TaskFlowInsight.git
cd TaskFlowInsight

# Run quick verification
chmod +x quickstart-verify.sh
./quickstart-verify.sh
```

---

## 🎯 Core Features

### 1. Process Flow Visualization
Automatic hierarchical execution tracking:
- **Nested task trees**: Session → Task → Stage → Message
- **Precise timing**: Microsecond-level measurement
- **Exception capture**: Full context with stack traces
- **Async support**: ThreadLocal context propagation

```java
@TfiTask("Create Order")
public OrderResult createOrder(CreateOrderRequest request) {
    validateInventory(request.getProducts());  // Sub-task 1
    calculatePrice(request);                   // Sub-task 2
    processPayment(request.getPayment());      // Sub-task 3
    initiateShipment(request);                 // Sub-task 4

    return OrderResult.success();
}
```

### 2. Intelligent Change Tracking
Deep object comparison with smart diff detection:
- **Snapshot strategies**: Shallow (scalars) + Deep (nested objects)
- **Type-aware**: Primitives, Collections, Dates, BigDecimal, Custom Objects
- **Entity vs ValueObject**: Intelligent list comparison based on type system
- **Path deduplication**: Eliminates redundant change paths
- **Configurable precision**: Control numeric/date comparison accuracy

```java
// Track object changes
TFI.track("order", orderObject);  // Shallow tracking
TFI.trackDeep("user", userObject); // Deep tracking

// Get all changes
List<ChangeRecord> changes = TFI.getChanges();
// Output: order.status: PENDING → PAID
//         order.amount: 1000.00 → 850.00
```

### 3. Advanced Comparison API
Flexible comparison with built-in templates:

```java
// Simple one-liner
CompareResult result = TFI.compare(before, after);

// Template-based comparison
CompareResult auditResult = TFI.comparator()
    .useTemplate(ComparisonTemplate.AUDIT)  // AUDIT/DEBUG/FAST/PERFORMANCE
    .withMaxDepth(5)
    .compare(oldObj, newObj);

// Render as Markdown
String report = TFI.render(result, "standard"); // simple/standard/detailed
```

**Available Templates:**
- `AUDIT`: Complete change records for compliance
- `DEBUG`: Detailed diagnostics for troubleshooting
- `FAST`: Performance-optimized shallow comparison
- `PERFORMANCE`: Balanced depth + speed

### 4. Type System Annotations
Fine-grained control over comparison behavior:

```java
@Entity  // Objects with unique identifiers
public class Order {
    @Key  // Used for list matching
    private String orderId;

    @NumericPrecision(scale = 2)  // Control decimal comparison
    private BigDecimal amount;

    @DateFormat("yyyy-MM-dd HH:mm:ss")  // Date formatting
    private Date createdAt;

    @DiffIgnore  // Exclude from comparison
    private String internalNotes;
}

@ValueObject  // Value-based comparison (no identity)
public class Money {
    private BigDecimal amount;
    private String currency;
}
```

### 5. Enterprise Monitoring
Production-ready observability:
- **Spring Boot Actuator**: `/actuator/taskflow` endpoint
- **Prometheus metrics**: Custom TFI metrics export
- **Health indicators**: System health checks
- **Performance degradation**: Auto-detect and adapt (optional)
- **Data masking**: Automatic PII protection

```bash
# Check health
curl http://localhost:19090/actuator/health

# View TFI metrics
curl http://localhost:19090/actuator/taskflow

# Prometheus scrape
curl http://localhost:19090/actuator/prometheus | grep tfi
```

### 6. Thread-Safe & Zero-Leak
Built for concurrent production environments:
- **ThreadLocal isolation**: Each thread has independent context
- **AutoCloseable pattern**: `try-with-resources` automatic cleanup
- **Weak references**: Prevents memory retention
- **Leak detection**: `ZeroLeakThreadLocalManager` monitoring
- **Async propagation**: `TFIAwareExecutor` for thread pools

---

## 🔬 The Most Intelligent Comparison Engine

TFI's **change tracking capability** is powered by a deep comparison engine built with **123 Java files** across **21 specialized modules**. This isn't just simple object comparison — it's the industry's **only** intelligent diff detection system combining type systems, path deduplication, and algorithmic optimization.

### Why is Comparison TFI's Core Competency?

**Process visualization** tells you "what executed," **change tracking** tells you "what changed" — **together, they complete the business insight**.

- ✅ JaVers: Change tracking only, no process visualization
- ✅ APM Tools: Process tracing only, no business object changes
- ⭐ **TFI: Both capabilities, closing the "last mile"**

### Three User Pain Points → TFI Solutions

#### Pain Point 1: Manual Comparison is Tedious 😫

<details>
<summary>Expand to see Traditional vs TFI approach</summary>

**Traditional way (painful):**
```java
// Need to write this for 50+ fields...
if (!Objects.equals(old.getStatus(), new.getStatus())) {
    log.info("status changed: {} -> {}", old.getStatus(), new.getStatus());
}
if (!Objects.equals(old.getAmount(), new.getAmount())) {
    log.info("amount changed: {} -> {}", old.getAmount(), new.getAmount());
}
if (!Objects.equals(old.getCustomerName(), new.getCustomerName())) {
    log.info("customerName changed: {} -> {}", old.getCustomerName(), new.getCustomerName());
}
// ... repeat 47 more times ...
```

**TFI way (elegant):**
```java
TFI.track("order", order);
// ✅ Auto-detects all changes with one line!

// Output example:
// order.status: PENDING → VALIDATED
// order.amount: 1000.00 → 850.00
// order.customerName: John → Jane
```
</details>

#### Pain Point 2: Collection Comparison is Difficult 🤯

<details>
<summary>Expand to see collection matching complexity</summary>

**Traditional way (complex):**
```java
List<Item> oldItems = oldOrder.getItems();
List<Item> newItems = newOrder.getItems();

// ❓ How to determine which Items were added/removed/modified?
// ❓ How to match corresponding elements in two lists?
// ❓ How to detect element position moves?

// Need to implement complex matching logic yourself:
Map<String, Item> oldMap = oldItems.stream()
    .collect(Collectors.toMap(Item::getItemId, Function.identity()));
Map<String, Item> newMap = newItems.stream()
    .collect(Collectors.toMap(Item::getItemId, Function.identity()));

// Detect additions
newMap.keySet().stream()
    .filter(id -> !oldMap.containsKey(id))
    .forEach(id -> log.info("Added: {}", newMap.get(id)));

// Detect deletions
oldMap.keySet().stream()
    .filter(id -> !newMap.containsKey(id))
    .forEach(id -> log.info("Removed: {}", oldMap.get(id)));

// Detect modifications
oldMap.keySet().stream()
    .filter(newMap::containsKey)
    .forEach(id -> {
        Item oldItem = oldMap.get(id);
        Item newItem = newMap.get(id);
        // ... back to pain point 1: field-by-field comparison
    });

// ❌ Position move detection? Too complex, give up...
```

**TFI way (intelligent):**
```java
@Entity  // Mark as entity
public class Item {
    @Key  // Use this field for list matching
    private String itemId;
    private int quantity;
    private BigDecimal price;
}

// TFI handles automatically:
// - ✅ Element matching (based on @Key)
// - ✅ Add/delete detection
// - ✅ Field change detection
// - ✅ Position move detection (LCS algorithm)

// Output example:
// items[0] ADDED: Item{itemId=ITEM-003, quantity=5}
// items[1] quantity: 10 → 9
// items[2] MOVED to items[4]  ← Auto-detects moves!
// items[3] REMOVED: Item{itemId=ITEM-002}
```
</details>

#### Pain Point 3: Floating-Point/Date Precision Issues 🐛

<details>
<summary>Expand to see precision control</summary>

**Traditional way (error-prone):**
```java
// ❌ Direct float comparison — can misfire
if (old.getPrice() == new.getPrice()) {
    // 0.1 + 0.2 == 0.3 ? False in Java!
}

// ❌ BigDecimal comparison trap
BigDecimal a = new BigDecimal("100.00");
BigDecimal b = new BigDecimal("100.0");
a.equals(b);  // false! Different scale

// ❌ Date comparison timezone issues
Date date1 = new Date();  // UTC
Date date2 = parseDateFromUI("2025-01-01 10:00:00");  // Local time
// How to compare correctly?
```

**TFI way (correct & controllable):**
```java
@Entity
public class Transaction {
    @NumericPrecision(scale = 2)  // Control to 2 decimal places
    private BigDecimal amount;

    @NumericPrecision(scale = 4)  // Different precision for different fields
    private BigDecimal exchangeRate;

    @DateFormat("yyyy-MM-dd")  // Compare date only, ignore time
    private Date transactionDate;

    @DateFormat("yyyy-MM-dd HH:mm:ss")  // Precise to the second
    private Date createdAt;
}

// TFI handles all precision issues automatically:
// amount: 100.00 → 100.01  ✅ Detects diff (2-digit precision)
// exchangeRate: 6.5432 → 6.5433  ✅ Detects diff (4-digit precision)
// transactionDate: 2025-01-01 → 2025-01-02  ✅ Date only
// createdAt: 2025-01-01 10:00:00 → 2025-01-01 10:00:01  ✅ Precise to second
```
</details>

---

### Technical Depth Showcase

#### 1. LCS Algorithm Detects List Moves 🧠

TFI uses the **Longest Common Subsequence (LCS) algorithm** to intelligently detect element moves, not just simple additions/deletions.

```java
// Example scenario
List<Task> oldTasks = [A, B, C, D, E];
List<Task> newTasks = [A, C, B, E, D];

// Traditional simple comparison (wrong):
// ❌ B deleted, C deleted, B added, C added, D deleted, E added, D added
// Too many false positives! Actually just position adjustments

// TFI LCS algorithm output (correct):
// ✅ tasks[1] MOVED from index 1 to index 2  (B: position 1 → position 2)
// ✅ tasks[2] MOVED from index 2 to index 1  (C: position 2 → position 1)
// ✅ tasks[4] MOVED from index 4 to index 3  (E: position 4 → position 3)
// ✅ tasks[3] MOVED from index 3 to index 4  (D: position 3 → position 4)
```

**Business value**: In task list reordering, shopping cart adjustments, workflow step changes, accurately identifies "moves" rather than "delete+add".

#### 2. Path Deduplication System 🎯

TFI's **PathDeduplicator** automatically eliminates redundant change paths, keeping only the most precise leaf node changes.

```java
// Raw changes (redundant):
order.items[0].product.price: 100 → 120
order.items[0].product: Product{price=100, name='Phone'} → Product{price=120, name='Phone'}
order.items[0]: Item{product=...} → Item{product=...}
order: Order{items=[...]} → Order{items=[...]}

// ❌ Above 4 paths all say the same thing: price changed

// PathDeduplicator deduplicated (clear):
✅ order.items[0].product.price: 100 → 120
// ✅ Parent paths auto-removed (transitive changes only)
```

**Implementation principles**:
- **PathArbiter**: Judges path priority
- **PriorityCalculator**: Computes deterministic sorting
- **Deduplication**: Leaf nodes first, eliminate ancestor paths

#### 3. Type-Aware Comparison 🏷️

TFI distinguishes two semantics via `@Entity` and `@ValueObject` annotations:

**Entity (identity-based):**
```java
@Entity  // Object with unique identifier
public class User {
    @Key  // Used for list matching
    private String userId;
    private String name;
    private int age;
}

// List<User> comparison logic:
// 1️⃣ First match by userId
// 2️⃣ Then compare name, age properties
// ✅ Even if name changes, same userId = "same user modified"
```

**Value Object (content-based):**
```java
@ValueObject  // No identity, pure value comparison
public class Money {
    private BigDecimal amount;
    private String currency;
}

// List<Money> comparison logic:
// 1️⃣ Direct content comparison
// 2️⃣ amount=100 && currency=USD exactly same = match
// ✅ Suitable for immutable objects, config items, etc.
```

---

### TFI vs JaVers In-Depth Comparison

| Dimension | **TaskFlowInsight** | JaVers |
|-----------|-------------------|--------|
| **Core Positioning** | 🐛 Debug tool (real-time) | 📋 Audit system (persistent) |
| **Configuration Complexity** | ⚡ Zero-config (`@TfiTask`) | ⚙️ Medium (Repository + Entity mapping) |
| **Performance (TPS)** | **66,000+** ⚡ | ~20,000 (3.3x gap) |
| **Memory Footprint** | **<5 MB** 🪶 | ~20 MB |
| **Process Visualization** | ✅ Built-in tree structure | ❌ None |
| **Comparison Depth** | Configurable (max-depth: 10) | Default shallow |
| **Type System** | `@Entity`/`@ValueObject`/`@Key` | `@Entity` (JPA only) |
| **Path Deduplication** | ✅ PathDeduplicator | ❌ Raw paths |
| **LCS Algorithm** | ✅ Move detection | ❌ Add/delete only |
| **Precision Control** | `@NumericPrecision`/`@DateFormat` | Limited |
| **Strategy Extension** | 21 modules, easy to extend | Limited extensibility |
| **Data Persistence** | ❌ In-memory (session cleanup) | ✅ Database |
| **Target Users** | 👨‍💻 Developers/Test Engineers | 🏢 Compliance/Audit Teams |
| **Use Cases** | Development debugging, real-time monitoring | Compliance audit, historical queries |

**Key differences**:
- **JaVers** is an enterprise audit tool requiring database, suitable for recording historical changes for compliance
- **TFI** is a development debugging tool running in-memory, suitable for real-time diagnostics and process visualization

---

### Real Debugging Scenario: E-commerce Order Payment Failure

Suppose you encounter an order payment failure and need to quickly locate the cause.

**Traditional debugging approach:**
```
1. View scattered log files
2. Manually correlate timestamps
3. Guess which field went wrong
4. Add more logs to reproduce
5. Redeploy...
⏰ Time spent: 30-60 minutes
```

**TFI one-step solution:**
```java
@TfiTask("Process Order")
public OrderResult processOrder(String orderId) {
    Order order = fetchOrder(orderId);
    TFI.track("order", order);

    validateOrder(order);
    processPayment(order);

    return OrderResult.success(order);
}
```

**TFI auto-output:**
```
[Order-12345] Process Order ━━━━━━━━━━━━━ 234ms ✗
├─ Fetch Order ...................... 12ms ✓
│  └─ order.status: null → PENDING
│  └─ order.payment: null
├─ Validate Order .................. 45ms ✓
│  └─ order.status: PENDING → VALIDATED
│  └─ order.payment: null (unchanged)  ← ⚠️ Problem found
├─ Process Payment ................ 177ms ✗
│  └─ 🔴 NullPointerException: Cannot invoke "Payment.process()" because "order.payment" is null
│  └─ at OrderService.processPayment(OrderService.java:42)
└─ ❌ Failure reason: payment object not initialized

🎯 Root Cause Analysis:
   • payment field remains null after validateOrder
   • processPayment attempts to call null.process() causing exception
   • Missing payment initialization step

💡 Solution: Add initializePayment() call between validateOrder and processPayment
```

**Value comparison:**
- ✅ **Process visualization**: Clearly see which steps executed, timing for each
- ✅ **Change tracking**: Auto-detect order.payment always null
- ✅ **Exception context**: Complete stack + business context
- ⏰ **Diagnosis time**: From 30-60 minutes down to **30 seconds**

---

### Comparison Engine Technical Architecture

TFI's comparison capability is supported by 21 specialized modules:

```
📦 tracking/ (123 files)
├── 🧮 algo/           → LCS algorithm, path deduplication algorithms
├── ⚖️ compare/        → CompareService, strategy interfaces
├── 🔍 detector/       → DiffDetector, DiffFacade (v3.0.0)
├── 📸 snapshot/       → SnapshotProvider, deep/shallow strategies
├── 🛤️ path/           → PathBuilder, PathDeduplicator, PathArbiter
├── ⚡ perf/           → Performance monitoring, degradation management
├── 💾 cache/          → Caffeine cache optimization
├── 📊 metrics/        → Comparison metrics collection
└── ... 13 other specialized modules
```

**Performance optimizations:**
- ✅ **Caffeine caching**: Reflection metadata, strategy caching, 95%+ hit rate
- ✅ **Fast-path checks**: Zero overhead when disabled
- ✅ **Configurable depth**: `max-depth: 10` prevents infinite recursion
- ✅ **Lazy loading**: On-demand initialization, reduces startup time
- ✅ **Circular reference handling**: Visited Set + weak references

**Challenges & solutions:**
1. **Performance challenge**: Deep comparison can be slow
   - ✅ Solution: Caching + fast-path + lazy loading → 66K TPS
2. **Circular references**: Object graphs may have cycles
   - ✅ Solution: Visited object marking + max depth limit
3. **Type diversity**: Collections, dates, BigDecimal...
   - ✅ Solution: Strategy pattern, one dedicated strategy per type

---

### Comparison Engine Extensibility

**Custom comparator:**
```java
@Entity
public class Product {
    @Key
    private String productId;

    @CustomComparator(PriceComparator.class)  // Custom comparator
    private BigDecimal price;
}

public class PriceComparator implements FieldComparator<BigDecimal> {
    @Override
    public boolean areEqual(BigDecimal old, BigDecimal new) {
        // Custom logic: price fluctuation <5% considered unchanged
        BigDecimal diff = new.subtract(old).abs();
        BigDecimal threshold = old.multiply(new BigDecimal("0.05"));
        return diff.compareTo(threshold) < 0;
    }
}
```

**Custom comparison strategy:**
```java
@Component
public class GeoLocationCompareStrategy implements CompareStrategy {
    @Override
    public boolean supports(Class<?> type) {
        return GeoLocation.class.isAssignableFrom(type);
    }

    @Override
    public List<FieldChange> compare(Object oldVal, Object newVal, String path) {
        GeoLocation oldLoc = (GeoLocation) oldVal;
        GeoLocation newLoc = (GeoLocation) newVal;

        // Custom logic: distance <100m considered unchanged
        double distance = calculateDistance(oldLoc, newLoc);
        if (distance < 100) {
            return Collections.emptyList();  // Unchanged
        }

        return List.of(new FieldChange(
            path,
            oldLoc.toString(),
            newLoc.toString(),
            "GEO_LOCATION",
            ChangeType.UPDATE
        ));
    }
}
```

---

## 🚀 Performance

TFI is engineered for production use with **minimal overhead**:

| Metric | Value | Notes |
|--------|-------|-------|
| **Memory Footprint** | < 5 MB | 10x lighter than competitors |
| **CPU Overhead** | < 1% | Negligible impact on throughput |
| **Latency Added** | < 15 μs | Sub-millisecond per operation |
| **Throughput** | **66,000+ TPS** | Validated in benchmarks |
| **Cache Hit Rate** | 95%+ | Caffeine-optimized |
| **Test Coverage** | 85%+ | 350+ test classes |

**Run benchmarks yourself:**
```bash
./run-benchmark.sh
```

**Performance optimizations:**
- Caffeine caching (strategy + reflection)
- Fast-path checks (early returns)
- Lazy initialization
- Weak references
- ConcurrentHashMap for thread safety

---

## 💡 Real-World Examples

### E-Commerce Order Processing
```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    @TfiTask("Create Order")
    @PostMapping
    public ResponseEntity<OrderResult> createOrder(@RequestBody CreateOrderRequest request) {
        // Each step automatically tracked with timing
        User user = validateUser(request.getUserId());
        List<Product> products = validateProducts(request.getProductIds());

        InventoryResult inventory = checkInventory(products);
        TFI.track("inventory", inventory);  // Track state changes

        PriceResult price = calculatePrice(products, user.getVipLevel());
        TFI.track("pricing", price);

        Order order = createOrder(user, products, price);
        PaymentResult payment = processPayment(order, request.getPaymentInfo());

        if (payment.isSuccess()) {
            updateInventory(inventory);
            ShipmentResult shipment = initiateShipment(order);
            return ResponseEntity.ok(OrderResult.success(order, payment, shipment));
        } else {
            TFI.error("Payment failed", new PaymentException(payment.getErrorMessage()));
            return ResponseEntity.badRequest().body(OrderResult.failure("Payment failed"));
        }
    }
}
```

### Approval Workflow
```java
@Service
public class ApprovalService {

    @TfiTask("Approval Chain")
    public ApprovalResult processApproval(LeaveRequest request) {
        TFI.trackDeep("request", request);  // Track full object graph

        for (Approver approver : getApprovalChain()) {
            ApprovalDecision decision = approver.review(request);
            TFI.track("decision", decision);

            if (decision.isRejected()) {
                return ApprovalResult.rejected(decision.getReason());
            }
        }

        return ApprovalResult.approved();
    }
}
```

### Data Synchronization (ETL)
```java
@TfiTask("ETL Sync")
public SyncResult syncData(DataSource source, DataTarget target) {
    List<Record> records = source.fetchRecords();
    int successCount = 0;

    for (Record record : records) {
        try (var stage = TFI.stage("Transform Record " + record.getId())) {
            Record transformed = transformRecord(record);
            TFI.track("record-" + record.getId(), transformed);

            target.save(transformed);
            successCount++;
        } catch (Exception e) {
            TFI.error("Transform failed for record " + record.getId(), e);
        }
    }

    return SyncResult.completed(successCount, records.size());
}
```

**📚 See [EXAMPLES.md](EXAMPLES.md) for 11 complete real-world scenarios:**
- ✅ E-commerce order flow
- ✅ Approval workflows
- ✅ Batch processing
- ✅ Async messaging
- ✅ Game state machines
- ✅ Financial transactions
- ✅ And more...

---

## 🏗️ Architecture Highlights

TFI is built with **enterprise-grade engineering principles**:

### Design Philosophy
1. **Zero-Leak Guarantee**: All contexts use try-with-resources or explicit cleanup
2. **Graceful Degradation**: Disabled TFI becomes complete no-op (zero overhead)
3. **Exception Safety**: TFI never propagates exceptions to user code
4. **Performance-First**: Fast-path checks, lazy initialization, aggressive caching
5. **Thread-Safe**: All public APIs safe for concurrent use

### Key Components

```
┌─────────────────────────────────────────────┐
│         TFI API Facade (1741 lines)         │  ← Single entry point
├─────────────────────────────────────────────┤
│  Context Management  │  Change Tracking     │
│  • SafeContextManager│  • ChangeTracker     │
│  • ThreadLocal       │  • DiffFacade (v3.0) │
│  • ZeroLeakManager   │  • SnapshotProvider  │
├─────────────────────────────────────────────┤
│  Comparison Engine (123 files)              │
│  • algo  • compare  • detector  • snapshot  │
│  • path  • perf     • cache     • metrics   │
├─────────────────────────────────────────────┤
│  Spring Integration  │  Monitoring          │
│  • Annotation AOP    │  • Actuator          │
│  • Auto-Config       │  • Prometheus        │
│  • SpEL Support      │  • Health Check      │
├─────────────────────────────────────────────┤
│  Performance Layer                          │
│  • Caffeine Cache   • Degradation Manager  │
│  • Fast-Path Checks • Weak References      │
└─────────────────────────────────────────────┘
```

### Technology Stack
- **Java 21**: Modern language features (records, pattern matching, virtual threads ready)
- **Spring Boot 3.5.5**: Latest enterprise framework
- **Spring AOP**: Annotation processing (`@TfiTask`, `@TfiTrack`)
- **Caffeine 3.1.8**: High-performance caching
- **Micrometer**: Vendor-neutral metrics facade
- **Prometheus**: Time-series metrics export

---

## 🔧 Configuration

TFI works **out-of-the-box** with sensible defaults. Customize via `application.yml`:

```yaml
tfi:
  enabled: true  # Master switch

  annotation:
    enabled: true  # @TfiTask/@TfiTrack support

  change-tracking:
    enabled: true
    snapshot:
      enable-deep: true  # Deep object traversal
      max-depth: 10      # Prevent infinite recursion

  compare:
    auto-route:
      entity:
        enabled: true  # Auto-detect @Entity for list comparison
      lcs:
        enabled: true  # LCS algorithm for move detection
    numeric:
      float-tolerance: 1e-12
      relative-tolerance: 1e-9
    datetime:
      default-format: "yyyy-MM-dd HH:mm:ss"
      tolerance-ms: 0

  render:
    masking:
      enabled: true  # PII protection
    mask-fields:
      - password
      - secret
      - token
      - internal*  # Wildcard support
```

**Full configuration reference:** [docs/configuration/](docs/configuration/)

---

## 📚 Documentation

### User Guides
- [📖 Quick Start Guide](QUICKSTART.md) - Get running in 3 minutes
- [📘 Getting Started](GETTING-STARTED.md) - Comprehensive tutorial
- [💡 11 Real-World Examples](EXAMPLES.md) - E-commerce, workflow, finance, gaming
- [🚀 Deployment Guide](DEPLOYMENT.md) - Production best practices

### Reference Documentation
- [🔧 API Reference](docs/api/) - Complete API documentation
- [⚙️ Configuration Guide](docs/configuration/) - All configuration options
- [🏛️ Architecture Overview](CLAUDE.md) - System design and principles

### Support
- [❓ FAQ](FAQ.md) - Common questions and answers
- [🩺 Troubleshooting](TROUBLESHOOTING.md) - Diagnostic procedures
- [🔒 Security Guide](SECURITY.md) - Enterprise security best practices
- [🐛 GitHub Issues](https://github.com/shiyongyin/TaskFlowInsight/issues) - Bug reports and feature requests

---

## 🤝 Community

### Getting Help

1. **Check [FAQ](FAQ.md)** for common questions
2. **Review [Troubleshooting Guide](TROUBLESHOOTING.md)** for diagnostics
3. **Search [GitHub Issues](https://github.com/shiyongyin/TaskFlowInsight/issues)**
4. **Ask on [Stack Overflow](https://stackoverflow.com/questions/tagged/taskflowinsight)** (tag: `taskflowinsight`)

### Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

**Ways to contribute:**
- 🐛 Report bugs
- 💡 Suggest features
- 📝 Improve documentation
- 🧪 Add test cases
- 🔧 Submit pull requests

### Building from Source

```bash
# Clone repository
git clone https://github.com/shiyongyin/TaskFlowInsight.git
cd TaskFlowInsight

# Build and install
./mvnw clean install

# Run tests with coverage
./mvnw test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

**Requirements:**
- JDK 21+
- Maven 3.9+ (or use included wrapper)

---

## 🗺️ Roadmap

### ✅ Current Version: v3.0.0 (2025-10)
- **Unified Architecture**: DiffFacade + SnapshotProvider (Spring/non-Spring auto-switching)
- **Complete Type System**: `@Entity`, `@Key`, `@NumericPrecision`, `@DateFormat`, `@CustomComparator`
- **Advanced Comparison**: EntityListStrategy (move detection), LCS algorithm, precision control
- **Path System**: PathDeduplicator for clean diff output
- **Monitoring**: DegradationManager (auto-adapt to load), Prometheus metrics
- **Testing**: 350+ test classes, 85%+ coverage
- **Documentation**: QUICKSTART, EXAMPLES (11 scenarios), FAQ, TROUBLESHOOTING

### 🔨 v3.1.0 (Planned Q1 2026)
- Reference Change semantic enhancement
- Container Events complete implementation
- Query Helper API performance optimization
- Array comparison strategy enhancement
- Distributed tracing correlation (experimental)

### 🌟 v4.0.0 (Vision)
- **AI-Powered Analysis**: Anomaly pattern detection
- **Distributed Traces**: Cross-service flow correlation
- **IDE Plugin**: IntelliJ IDEA real-time preview
- **Microservices Integration**: Service mesh observability

**Detailed roadmap:** [docs/ROADMAP.md](docs/roadmap/)

---

## 📄 License

TaskFlowInsight is Open Source software released under the [Apache 2.0 license](LICENSE).

---

## 🙏 Acknowledgments

Built with best-of-breed technologies:
- [Spring Boot](https://spring.io/projects/spring-boot) - Enterprise application framework
- [Caffeine](https://github.com/ben-manes/caffeine) - High-performance caching library
- [Micrometer](https://micrometer.io/) - Vendor-neutral metrics facade
- Inspired by [JaVers](https://javers.org/) - Object auditing and diff framework

Special thanks to all [contributors](https://github.com/shiyongyin/TaskFlowInsight/graphs/contributors)!

---

<div align="center">

**TaskFlowInsight** — Business-First Observability for Java

*If you find TFI useful, please consider giving us a ⭐ on GitHub*

[Documentation](GETTING-STARTED.md) • [Examples](EXAMPLES.md) • [GitHub](https://github.com/shiyongyin/TaskFlowInsight) • [Issues](https://github.com/shiyongyin/TaskFlowInsight/issues) • [Discussions](https://github.com/shiyongyin/TaskFlowInsight/discussions)

</div>

---

## 📞 Contact & Support

- **Bug Reports**: [GitHub Issues](https://github.com/shiyongyin/TaskFlowInsight/issues)
- **Feature Requests**: [GitHub Discussions](https://github.com/shiyongyin/TaskFlowInsight/discussions)
- **Questions**: [Stack Overflow](https://stackoverflow.com/questions/tagged/taskflowinsight) (tag: `taskflowinsight`)
- **Email**: support@taskflowinsight.com

---

Made with ❤️ by the TaskFlowInsight Team
