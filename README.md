# TaskFlowInsight

<div align="center">

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
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

## Why TFI?

| Feature | TaskFlowInsight | JaVers | APM Tools | Manual Logging |
|---------|----------------|--------|-----------|----------------|
| **Setup Time** | < 2 min | ~1 hour | Hours/Days | N/A |
| **Flow Visualization** | ✅ Tree view | ❌ | ⚠️ Traces only | ❌ Scattered |
| **Change Tracking** | ✅ Deep diff | ✅ Basic audit | ❌ | ❌ |
| **Memory** | **<5 MB** | ~20 MB | 50-100 MB | ~0 |
| **CPU Overhead** | **<1%** | ~3% | 5-15% | ~0 |
| **Throughput** | **66,000+ TPS** | ~20,000 | N/A | N/A |
| **Spring Integration** | ✅ Deep | ⚠️ Basic | ✅ | N/A |
| **Cost** | **Free & Open** | Free & Open | $$$$ | Free |

---

## 🎯 Core Features

### 1. Flow Visualization
- **Nested task tree**: Session → Task → Stage → Message
- **Precise timing**: Microsecond-level measurement
- **Exception capture**: Full context and stack traces
- **Async support**: `TFIAwareExecutor` for ThreadLocal context propagation

### 2. Smart Change Tracking
- **Snapshot strategies**: Shallow (scalars) + Deep (nested objects)
- **Entity vs ValueObject**: `@Entity`/`@ValueObject` type system
- **Path deduplication**: PathDeduplicator eliminates redundant change paths
- **LCS algorithm**: Smart list element move detection

```java
TFI.track("order", orderObject);       // Shallow tracking
TFI.trackDeep("user", userObject);     // Deep tracking
List<ChangeRecord> changes = TFI.getChanges();
```

### 3. Advanced Compare API

```java
CompareResult result = TFI.compare(before, after);
String report = TFI.render(result, "standard");
```

### 4. Type System Annotations

```java
@Entity
public class Order {
    @Key                              // Primary key for list matching
    private String orderId;

    @NumericPrecision(scale = 2)      // Decimal precision control
    private BigDecimal amount;

    @DateFormat("yyyy-MM-dd HH:mm:ss") // Date formatting
    private Date createdAt;

    @DiffIgnore                        // Exclude from comparison
    private String internalNotes;
}
```

### 5. Enterprise Monitoring

```bash
curl http://localhost:19090/actuator/taskflow          # TFI status
curl http://localhost:19090/actuator/taskflow-context   # Context info
curl http://localhost:19090/actuator/prometheus | grep tfi  # Prometheus metrics
```

### 6. Thread Safety & Zero Leaks
- **ThreadLocal isolation**: Independent context per thread
- **AutoCloseable pattern**: `try-with-resources` auto-cleanup
- **Leak detection**: `ZeroLeakThreadLocalManager` monitoring
- **Async propagation**: `TFIAwareExecutor` for thread pools

---

## 🔬 Comparison Engine

TFI's comparison engine is the **only** system combining type system, path deduplication, and LCS algorithm for smart diff detection.

<details>
<summary>Pain Point 1: Manual field-by-field comparison</summary>

```java
// Traditional: compare 50+ fields manually...
if (!Objects.equals(old.getStatus(), new.getStatus())) {
    log.info("status changed: {} -> {}", old.getStatus(), new.getStatus());
}
// ... repeat 50 times ...

// TFI: one line
TFI.track("order", order);
// Auto output: order.status: PENDING → VALIDATED
```
</details>

<details>
<summary>Pain Point 2: Collection comparison is hard</summary>

```java
@Entity
public class Item {
    @Key
    private String itemId;
    private int quantity;
}

// TFI handles automatically:
// ✅ Element matching via @Key
// ✅ Add/Remove/Update detection
// ✅ LCS algorithm for move detection
```
</details>

<details>
<summary>Pain Point 3: Float/Date precision issues</summary>

```java
@NumericPrecision(scale = 2)   // Control to 2 decimal places
private BigDecimal amount;

@DateFormat("yyyy-MM-dd")      // Compare date part only
private Date transactionDate;
```
</details>

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

**Tech Stack:** Java 21, Spring Boot 3.5.5, Spring AOP, Caffeine 3.1.8, Micrometer + Prometheus

---

## 🚀 Performance

| Metric | Value |
|--------|-------|
| **Memory** | < 5 MB |
| **CPU Overhead** | < 1% |
| **Latency** | < 15 μs/op |
| **Throughput** | 66,000+ TPS |
| **Cache Hit Rate** | 95%+ |

```bash
# Run JMH benchmarks
./mvnw -pl tfi-examples -P bench exec:java \
  -Dexec.mainClass=com.syy.taskflowinsight.benchmark.BenchmarkRunner
```

---

## 🔧 Configuration

TFI works out of the box. Customize via `application.yml`:

```yaml
tfi:
  enabled: true
  annotation:
    enabled: true
  change-tracking:
    enabled: true
    snapshot:
      enable-deep: true
      max-depth: 10
  compare:
    auto-route:
      entity.enabled: true
      lcs.enabled: true
  api:
    routing:
      enabled: false        # v4.0.0 Provider routing (experimental)
      provider-mode: auto
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

- [Quick Start Guide](QUICKSTART.md)
- [Getting Started](GETTING-STARTED.md)
- [Real-World Examples](EXAMPLES.md) — 11 scenarios: e-commerce, workflow, finance, gaming
- [FAQ](FAQ.md)
- [Troubleshooting](TROUBLESHOOTING.md)
- [v3→v4 Migration Guide](docs/MIGRATION_GUIDE_v3_to_v4.md)
- [Contributing](CONTRIBUTING.md)

---

## 🗺️ Roadmap

### ✅ v3.0.0 (Current Stable)
- Unified architecture: DiffFacade + SnapshotProvider
- Full type system: `@Entity`/`@Key`/`@NumericPrecision`/`@DateFormat`
- LCS move detection + PathDeduplicator
- Spring Boot Actuator + Prometheus monitoring
- 350+ test classes, 85%+ coverage

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

[Apache 2.0](LICENSE)

---

<div align="center">

**TaskFlowInsight** — Business-First Observability for Java

*If TFI helps you, please give us a ⭐*

[GitHub](https://github.com/shiyongyin/TaskFlowInsight) | [Issues](https://github.com/shiyongyin/TaskFlowInsight/issues) | [Discussions](https://github.com/shiyongyin/TaskFlowInsight/discussions)

</div>
