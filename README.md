# TaskFlowInsight

<div align="center">

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Test Coverage](https://img.shields.io/badge/Coverage-85%25-brightgreen.svg)](.)
[![tfi-all CI](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml/badge.svg)](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml)
[![tfi-compare CI](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-compare-ci.yml/badge.svg)](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-compare-ci.yml)
[![tfi-flow-core CI](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-flow-core-ci.yml/badge.svg)](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-flow-core-ci.yml)

**Business-First Observability for Java**
Process Visualization + Change Tracking in One Lightweight Library

**[中文](README.zh-CN.md)** | [Quick Start](#-quick-start) | [Modules](#-module-structure) | [Features](#-core-features) | [Docs](#-documentation)

</div>

---

## What is TaskFlowInsight?

TaskFlowInsight (TFI) is a lightweight Java library that brings **X-ray vision** to your business logic. It automatically visualizes execution flows and intelligently tracks object changes — **zero configuration required**.

Think of it as **APM for business developers**: while traditional APM tools monitor infrastructure (CPU, memory, network), TFI focuses on what matters most — **understanding business logic execution**.

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
├─ Validate Order ...................... 45ms ✓
│  └─ order.status: PENDING → VALIDATED
└─ Process Payment .................... 189ms ✓
   └─ order.payment: null → PAID
```

---

## 📦 Module Structure

TFI uses a Maven multi-module architecture, split by responsibility into 6 modules:

```
TaskFlowInsight (parent)
├── tfi-flow-core           Core flow engine (Session/Task/Stage/Message)
├── tfi-flow-spring-starter Spring Boot auto-config + AOP annotation support
├── tfi-compare             Smart comparison engine (deep diff + change tracking)
├── tfi-ops-spring          Ops & monitoring (Actuator/Metrics/Store/Performance)
├── tfi-examples            Examples & demos (Demo/Benchmark)
└── tfi-all                 All-in-one aggregate module
```

**Module dependencies:**
```
tfi-flow-core  ←─  tfi-flow-spring-starter  ←─┐
      ↑                                        │
tfi-compare  ←──  tfi-ops-spring  ←────────────┤
                                               │
                  tfi-all (aggregates all)  ────┘
                  tfi-examples (depends on all)
```

---

## Why TFI?

### The Problem
Modern business applications have **complex workflows** that are hard to debug:
- ❓ Which steps executed? How long did each take?
- ❓ What changed in objects during processing?
- ❓ Why did the workflow fail?

**Traditional solutions fall short:**
- **Manual logging**: Tedious, scattered, unstructured
- **APM tools**: Expensive, infrastructure-focused, complex setup
- **JaVers**: Audit-only, no flow visualization, requires configuration

### The Solution
TFI provides **dual-core capabilities** in one lightweight package:

| Capability | What You Get |
|------------|-------------|
| **🎯 Flow Visualization** | Auto-generated hierarchical process trees with precise timing |
| **🔍 Change Tracking** | Smart deep object comparison and diff detection |
| **📊 Real-time Monitoring** | Spring Boot Actuator + Prometheus metrics |
| **🚀 Zero Config** | Just add `@TfiTask` and go |
| **⚡ Production Ready** | <5MB memory, <1% CPU, 66K+ TPS |

---

## How is TFI Different?

| Feature | TaskFlowInsight | JaVers | APM Tools | Manual Logging |
|---------|----------------|--------|-----------|----------------|
| **Setup Time** | < 2 min | ~1 hour | Hours/Days | N/A |
| **Flow Visualization** | ✅ Tree view | ❌ | ⚠️ Traces only | ❌ Scattered |
| **Change Tracking** | ✅ Deep diff | ✅ Basic audit | ❌ | ❌ |
| **Memory** | **<5 MB** | ~20 MB | 50-100 MB | ~0 |
| **CPU Overhead** | **<1%** | ~3% | 5-15% | ~0 |
| **Throughput** | **66,000+ TPS** | ~20,000 | N/A | N/A |
| **Config Complexity** | **Zero config** | Medium | Complex | None |
| **Spring Integration** | ✅ Deep | ⚠️ Basic | ✅ | N/A |
| **Business Context** | ✅ Built-in | ⚠️ Limited | ❌ Custom needed | ❌ |
| **Cost** | **Free & Open** | Free & Open | $$$$ | Free |

**TFI's unique position**: The **only** library combining flow visualization + change tracking with enterprise-grade performance.

---

## ⚡ Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+ (or use the included wrapper)
- Spring Boot 3.x (optional but recommended)

### 1. Add Dependency

**All-in-one (recommended):**
```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-all</artifactId>
    <version>3.0.0</version>
</dependency>
```

**Pick what you need:**
```xml
<!-- Flow tracking only -->
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-flow-spring-starter</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- Comparison engine only -->
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-compare</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- Ops & monitoring (Actuator + Metrics) -->
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-ops-spring</artifactId>
    <version>3.0.0</version>
</dependency>
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

**Option A: Annotation-driven (recommended)**
```java
@Service
public class OrderService {

    @TfiTask("Process Order")
    public OrderResult processOrder(String orderId) {
        Order order = fetchOrder(orderId);
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

**Option B: Programmatic API**
```java
public void processOrder() {
    TFI.start("Process Order");
    try {
        try (var stage = TFI.stage("Validate")) {
            // business logic
        }

        try (var stage = TFI.stage("Check Inventory")) {
            // business logic
        }

        TFI.exportToConsole();
    } finally {
        TFI.stop();
    }
}
```

### 4. Build from Source

```bash
git clone https://github.com/shiyongyin/TaskFlowInsight.git
cd TaskFlowInsight

# Build all modules
./mvnw clean install

# Run tests for a specific module
./mvnw test -pl tfi-all
./mvnw test -pl tfi-compare

# Tests + coverage report
./mvnw clean verify jacoco:report -pl tfi-all
# Report: tfi-all/target/site/jacoco/index.html

# Run demos
./mvnw spring-boot:run -pl tfi-examples
```

---

## 🎯 Core Features

### 1. Flow Visualization
Automatic hierarchical execution tracking:
- **Nested task tree**: Session → Task → Stage → Message
- **Precise timing**: Microsecond-level measurement
- **Exception capture**: Full context and stack traces
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

### 2. Smart Change Tracking
Deep object comparison with intelligent diff detection:
- **Snapshot strategies**: Shallow (scalars) + Deep (nested objects)
- **Type-aware**: Primitives, collections, dates, BigDecimal, custom objects
- **Entity vs ValueObject**: Type-system-based smart list comparison
- **Path deduplication**: Eliminates redundant change paths
- **Configurable precision**: Control numeric/date comparison precision

```java
TFI.track("order", orderObject);       // Shallow tracking
TFI.trackDeep("user", userObject);     // Deep tracking

List<ChangeRecord> changes = TFI.getChanges();
// Output: order.status: PENDING → PAID
//         order.amount: 1000.00 → 850.00
```

### 3. Advanced Compare API
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

### 4. Type System Annotations
Fine-grained control over comparison behavior:

```java
@Entity  // Object with unique identity
public class Order {
    @Key  // Used for list matching
    private String orderId;

    @NumericPrecision(scale = 2)  // Decimal precision control
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
curl http://localhost:19090/actuator/health
curl http://localhost:19090/actuator/taskflow
curl http://localhost:19090/actuator/prometheus | grep tfi
```

### 6. Thread Safety & Zero Leaks
Built for concurrent production environments:
- **ThreadLocal isolation**: Independent context per thread
- **AutoCloseable pattern**: `try-with-resources` auto-cleanup
- **Weak references**: Prevent memory retention
- **Leak detection**: `ZeroLeakThreadLocalManager` monitoring
- **Async propagation**: `TFIAwareExecutor` for thread pools

---

## 🔬 The Smartest Comparison Engine

TFI's **change tracking** is powered by a deep comparison engine. It's the **only** system combining type system, path deduplication, and algorithm optimization for smart diff detection.

### Three Pain Points → TFI Solutions

<details>
<summary>Pain Point 1: Manual field-by-field comparison is tedious 😫</summary>

**Traditional (painful):**
```java
// Write this for 50+ fields...
if (!Objects.equals(old.getStatus(), new.getStatus())) {
    log.info("status changed: {} -> {}", old.getStatus(), new.getStatus());
}
// ... repeat 47 more times ...
```

**TFI (elegant):**
```java
TFI.track("order", order);
// ✅ Auto-detect all changes, one line!
// Output: order.status: PENDING → VALIDATED
//         order.amount: 1000.00 → 850.00
```
</details>

<details>
<summary>Pain Point 2: Collection comparison is hard 🤯</summary>

```java
@Entity
public class Item {
    @Key  // Match list elements by this field
    private String itemId;
    private int quantity;
    private BigDecimal price;
}

// TFI handles automatically:
// ✅ Element matching (via @Key)
// ✅ Add/Remove detection
// ✅ Field change detection
// ✅ Position move detection (LCS algorithm)
```
</details>

<details>
<summary>Pain Point 3: Float/Date precision issues 🐛</summary>

```java
@Entity
public class Transaction {
    @NumericPrecision(scale = 2)  // Control to 2 decimal places
    private BigDecimal amount;

    @NumericPrecision(scale = 4)  // Different precision per field
    private BigDecimal exchangeRate;

    @DateFormat("yyyy-MM-dd")  // Compare date part only
    private Date transactionDate;

    @DateFormat("yyyy-MM-dd HH:mm:ss")  // Precise to seconds
    private Date createdAt;
}
```
</details>

---

### Technical Deep Dive

#### 1. LCS Algorithm for List Move Detection 🧠

TFI uses the **Longest Common Subsequence (LCS) algorithm** to intelligently detect list element moves, not just simple add/remove.

```java
List<Task> oldTasks = [A, B, C, D, E];
List<Task> newTasks = [A, C, B, E, D];

// TFI LCS output (correct):
// ✅ tasks[1] MOVED from index 1 to index 2  (B)
// ✅ tasks[2] MOVED from index 2 to index 1  (C)
```

#### 2. Path Deduplication System 🎯

TFI's **PathDeduplicator** automatically eliminates redundant change paths, keeping only the most precise leaf-node changes.

```java
// Raw changes (redundant):
order.items[0].product.price: 100 → 120
order.items[0].product: Product{...} → Product{...}
order.items[0]: Item{...} → Item{...}

// After PathDeduplicator (clean):
✅ order.items[0].product.price: 100 → 120
```

#### 3. Type-Aware Comparison 🏷️

```java
@Entity  // Identity-based: match by @Key in lists
public class User {
    @Key
    private String userId;
    private String name;
}

@ValueObject  // Content-based: full value comparison
public class Money {
    private BigDecimal amount;
    private String currency;
}
```

---

### TFI vs JaVers Deep Comparison

| Dimension | **TaskFlowInsight** | JaVers |
|-----------|-------------------|--------|
| **Core Purpose** | 🐛 Debug tool (real-time) | 📋 Audit system (persistent) |
| **Config Complexity** | ⚡ Zero config (`@TfiTask`) | ⚙️ Medium (Repository + Entity mapping) |
| **Performance (TPS)** | **66,000+** ⚡ | ~20,000 (3.3x gap) |
| **Memory** | **<5 MB** 🪶 | ~20 MB |
| **Flow Visualization** | ✅ Built-in tree | ❌ None |
| **Type System** | `@Entity`/`@ValueObject`/`@Key` | `@Entity` (JPA only) |
| **Path Deduplication** | ✅ PathDeduplicator | ❌ Raw paths |
| **LCS Algorithm** | ✅ Move detection | ❌ Add/Remove only |
| **Precision Control** | `@NumericPrecision`/`@DateFormat` | Limited |
| **Data Persistence** | ❌ In-memory (session cleanup) | ✅ Database |
| **Target Users** | 👨‍💻 Developers/QA | 🏢 Compliance/Audit teams |

---

### Real Debug Scenario: E-commerce Payment Failure

**TFI auto-output:**
```
[Order-12345] Process Order ━━━━━━━━━━━━━ 234ms ✗
├─ Fetch Order ...................... 12ms ✓
│  └─ order.status: null → PENDING
│  └─ order.payment: null
├─ Validate Order .................. 45ms ✓
│  └─ order.status: PENDING → VALIDATED
│  └─ order.payment: null (unchanged)  ← ⚠️ Found it
├─ Process Payment ................ 177ms ✗
│  └─ 🔴 NullPointerException: order.payment is null
└─ ❌ Root cause: payment object not initialized

🎯 Root cause: payment field still null after validateOrder
💡 Fix: add initializePayment() between validateOrder and processPayment
```

**Value:**
- ✅ **Flow visualization**: See exactly which steps ran and how long
- ✅ **Change tracking**: Auto-detect order.payment stayed null
- ⏰ **Diagnosis time**: From 30-60 minutes down to **30 seconds**

---

## 💡 Real-World Examples

### E-commerce Order Processing
```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    @TfiTask("Create Order")
    @PostMapping
    public ResponseEntity<OrderResult> createOrder(@RequestBody CreateOrderRequest request) {
        User user = validateUser(request.getUserId());
        List<Product> products = validateProducts(request.getProductIds());

        InventoryResult inventory = checkInventory(products);
        TFI.track("inventory", inventory);

        PriceResult price = calculatePrice(products, user.getVipLevel());
        TFI.track("pricing", price);

        Order order = createOrder(user, products, price);
        PaymentResult payment = processPayment(order, request.getPaymentInfo());

        if (payment.isSuccess()) {
            updateInventory(inventory);
            return ResponseEntity.ok(OrderResult.success(order, payment));
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
        TFI.trackDeep("request", request);

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

### Data Sync (ETL)
```java
@TfiTask("ETL Sync")
public SyncResult syncData(DataSource source, DataTarget target) {
    List<Record> records = source.fetchRecords();
    int successCount = 0;

    for (Record record : records) {
        try (var stage = TFI.stage("Transform " + record.getId())) {
            Record transformed = transformRecord(record);
            TFI.track("record-" + record.getId(), transformed);
            target.save(transformed);
            successCount++;
        } catch (Exception e) {
            TFI.error("Transform failed: " + record.getId(), e);
        }
    }

    return SyncResult.completed(successCount, records.size());
}
```

**📚 See [EXAMPLES.md](EXAMPLES.md) for 11 complete real-world scenarios**

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────┐
│                    tfi-all (aggregate)                 │
├──────────────────────────────────────────────────────┤
│  tfi-flow-spring-starter  │  tfi-ops-spring          │
│  • @TfiTask AOP aspect    │  • Actuator endpoints    │
│  • Spring auto-config     │  • Prometheus metrics     │
│  • SpEL support           │  • Caffeine Store        │
│                           │  • Performance monitor    │
├───────────────────────────┼──────────────────────────┤
│  tfi-flow-core            │  tfi-compare             │
│  • Session/Task/Stage     │  • CompareService        │
│  • SafeContextManager     │  • DiffDetector/Facade   │
│  • ZeroLeakThreadLocal    │  • SnapshotProvider      │
│  • TFI API facade         │  • PathDeduplicator      │
│  • Exporters (Console/JSON)│ • LCS/TypeSystem/Cache  │
└───────────────────────────┴──────────────────────────┘
```

### Design Principles
1. **Zero-leak guarantee**: All contexts use try-with-resources or explicit cleanup
2. **Graceful degradation**: Disabled TFI = complete no-op (zero overhead)
3. **Exception safety**: TFI never throws to user code
4. **Performance first**: Fast-path checks, lazy init, aggressive caching
5. **Thread-safe**: All public APIs are concurrent-safe

### Tech Stack
- **Java 21**: Modern language features (records, pattern matching, virtual-thread ready)
- **Spring Boot 3.5.5**: Latest enterprise framework
- **Spring AOP**: Annotation processing (`@TfiTask`, `@TfiTrack`)
- **Caffeine 3.1.8**: High-performance caching
- **Micrometer + Prometheus**: Vendor-neutral metrics facade

---

## 🚀 Performance

| Metric | Value | Notes |
|--------|-------|-------|
| **Memory** | < 5 MB | 10x lighter than competitors |
| **CPU Overhead** | < 1% | Negligible throughput impact |
| **Latency** | < 15 μs | Sub-millisecond per operation |
| **Throughput** | **66,000+ TPS** | Benchmark verified |
| **Cache Hit Rate** | 95%+ | Caffeine optimized |
| **Test Coverage** | 85%+ | 350+ test classes |

```bash
# Run JMH benchmarks
./mvnw -pl tfi-examples -P bench exec:java \
  -Dexec.mainClass=com.syy.taskflowinsight.benchmark.BenchmarkRunner
```

---

## 🔧 Configuration

TFI **works out of the box** with sensible defaults. Customize via `application.yml`:

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

  api:
    routing:
      enabled: false        # v4.0.0 Provider routing (experimental)
      provider-mode: auto

  render:
    masking:
      enabled: true  # PII protection
    mask-fields:
      - password
      - secret
      - token
      - internal*  # Wildcard support
```

Default port: **19090**

---

## 🧪 CI/CD

Each module has its own GitHub Actions CI workflow:

| Workflow | Module | Scope |
|----------|--------|-------|
| tfi-flow-core CI | tfi-flow-core | Test + JaCoCo + Static Analysis |
| tfi-flow-spring-starter CI | tfi-flow-spring-starter | Test + JaCoCo + Static Analysis |
| tfi-compare CI | tfi-compare | Test + JaCoCo + Static Analysis + OWASP |
| tfi-ops-spring CI | tfi-ops-spring | Test + JaCoCo + Static Analysis |
| tfi-examples CI | tfi-examples | Compile + Test |
| tfi-all CI | tfi-all | Test + JaCoCo + Static Analysis + API Compat |
| TFI Routing Perf Gate | tfi-examples + tfi-all | JMH Benchmark + Perf Gate |

---

## 📚 Documentation

### User Guides
- [📖 Quick Start Guide](QUICKSTART.md) - Get started in 3 minutes
- [📘 Getting Started](GETTING-STARTED.md) - Comprehensive tutorial
- [💡 11 Real-World Examples](EXAMPLES.md) - E-commerce, workflow, finance, gaming

### Reference
- [🔧 v3→v4 Migration Guide](docs/MIGRATION_GUIDE_v3_to_v4.md)
- [🏛️ Architecture Overview](CLAUDE.md) - System design & principles

### Support
- [❓ FAQ](FAQ.md) - Common questions
- [🩺 Troubleshooting](TROUBLESHOOTING.md) - Diagnostic procedures
- [🐛 GitHub Issues](https://github.com/shiyongyin/TaskFlowInsight/issues) - Bug reports & feature requests

---

## 🗺️ Roadmap

### ✅ v3.0.0 (Current Stable)
- **Unified architecture**: DiffFacade + SnapshotProvider (Spring/non-Spring auto-switch)
- **Full type system**: `@Entity`, `@Key`, `@NumericPrecision`, `@DateFormat`, `@CustomComparator`
- **Advanced comparison**: EntityListStrategy (move detection), LCS algorithm, precision control
- **Path system**: PathDeduplicator for clean diff output
- **Monitoring**: DegradationManager (adaptive load), Prometheus metrics
- **Testing**: 350+ test classes, 85%+ coverage

### 🔨 v4.0.0 (In Development)
- Provider routing mechanism (`tfi.api.routing`)
- Multi-module Maven architecture
- Per-module CI/CD pipelines
- Reference semantic enhancements

---

## 🤝 Contributing

```bash
git clone https://github.com/shiyongyin/TaskFlowInsight.git
cd TaskFlowInsight
./mvnw clean install           # Build all modules
./mvnw test -pl tfi-compare    # Run specific module tests
```

Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## 📄 License

TaskFlowInsight is open-source software released under the [Apache 2.0 License](LICENSE).

---

## 🙏 Acknowledgments

Built with best-in-class technologies:
- [Spring Boot](https://spring.io/projects/spring-boot) - Enterprise application framework
- [Caffeine](https://github.com/ben-manes/caffeine) - High-performance caching library
- [Micrometer](https://micrometer.io/) - Vendor-neutral metrics facade
- Inspired by [JaVers](https://javers.org/) - Object audit and diff framework

---

<div align="center">

**TaskFlowInsight** — Business-First Observability for Java

*If TFI helps you, please give us a ⭐ on GitHub*

[Docs](GETTING-STARTED.md) | [Examples](EXAMPLES.md) | [GitHub](https://github.com/shiyongyin/TaskFlowInsight) | [Issues](https://github.com/shiyongyin/TaskFlowInsight/issues)

</div>
