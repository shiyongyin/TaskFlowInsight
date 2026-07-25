# TaskFlowInsight

<div align="center">

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![CI](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml/badge.svg)](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml)

See what happened inside a business operation — and what changed in its data

[中文](README.zh-CN.md)

</div>

TaskFlowInsight (TFI) is an open-source component library embedded in Java 21 applications. It answers the two questions developers and operators ask every day:

1. **What happened inside this business operation?** It records one operation as a tree of business steps: each step's name, success/failure, duration, business messages, and custom attributes — the failing step is visible at a glance.
2. **What changed in the data?** It compares two states of the same object and reports field-level differences with paths (which field changed, from what to what), plus explicit metadata about whether the comparison is complete and trustworthy.

It is a **library, not a platform**: no separate service to deploy, and it does not replace logs, tracing, or APM — it supplies the business semantics those tools struggle to express. Output comes in both human-readable forms (console tree, Markdown diff report) and machine form (canonical JSON), so developers, testers, and operators can talk about the same recorded facts.

## What it looks like in 30 seconds

Wrap an order-submission flow in a few lines:

```java
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.api.TfiFlow;

TfiFlow.startSession("order.submit");
try {
    try (TaskContext validate = TfiFlow.stage("order.validate")) {
        validate.message("stock and purchase-limit check passed").success();
    }
    try (TaskContext pay = TfiFlow.stage("order.pay")) {
        pay.error("insufficient balance: need 99.00, got 12.50").fail();
    }
    TfiFlow.exportToConsole();
} finally {
    TfiFlow.endSession();
}
```

The console immediately shows this flow tree (session ID shortened; message-type labels such as `业务流程` "business process" and `⚠️异常提示` "alert" are currently rendered in Chinese):

```text
==================================================
TaskFlow Insight Report
==================================================
Session: a5fd...
Thread:  1 (main)
Status:  RUNNING

📋 order.submit [RUNNING]
├── 🔧 order.validate [COMPLETED] (1ms)
│   └── 💬 [业务流程] stock and purchase-limit check passed
└── 🔧 order.pay [FAILED] (0ms)
    ├── 💬 [⚠️异常提示] insufficient balance: need 99.00, got 12.50
    └── 💬 [⚠️异常提示] Task marked as failed
==================================================
```

Which step failed, why, and how long each step took — visible at a glance, with no log stitching. The same recording also exports as canonical JSON with a stable field layout (for programs and AI to consume), and object-comparison results can be rendered as Markdown diff reports.

## When to use it

| Your question | What TaskFlowInsight gives you |
|---|---|
| Why did this order fail? | The steps that ran, the failing step, per-step durations, and the failure reason — no manual log stitching |
| What does this release actually change? | A path-qualified list of field and collection-item differences between the old and new objects |
| Can I trust this diff? | `outcome + completion` metadata states whether the comparison is complete; an empty change list is never misread as "objects are equal" |
| Did the regression test take the expected branch? | A structured execution path you can assert on in tests |
| Does the flow survive crossing threads? | Context-propagation utilities attach async executions back to the same session |

Typical scenarios: troubleshooting order/approval/billing/inventory flows, reviewing price and configuration changes before publishing, verifying state transitions, and regression-test assertions.

**What it is not**: not a workflow engine (it does not schedule processes), not an APM/tracing backend (no cross-service tracing or historical search), and it ships no persistence, query, or alerting UI — your application exports and stores the records.

## Quick start

All examples below use the **complete edition** modules (the default choice for business applications; see "Complete edition vs. Kernel edition (RC)" below for how the two lines differ). The current version is `4.0.0-SNAPSHOT`, not yet published to Maven Central — install it from source into your local repository first:

```bash
git clone https://github.com/shiyongyin/TaskFlowInsight.git
cd TaskFlowInsight
./mvnw clean install
```

### 1. Record a business flow (pure Java)

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-flow-core</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

The usage is the 30-second example above; swap `exportToConsole()` for `exportToJson()` and the same tree becomes canonical JSON with a stable field layout (excerpt — ids, paths, threads, timestamps, durations, and statistics fields omitted):

```json
{
  "schemaVersion": 2,
  "session": {"name": "order.submit", "status": "RUNNING"},
  "rootTask": {
    "name": "order.submit",
    "status": "RUNNING",
    "children": [
      {
        "name": "order.validate",
        "status": "COMPLETED",
        "messages": [{"displayLabel": "业务流程", "content": "stock and purchase-limit check passed"}]
      },
      {
        "name": "order.pay",
        "status": "FAILED",
        "messages": [
          {"displayLabel": "⚠️异常提示", "content": "insufficient balance: need 99.00, got 12.50"},
          {"displayLabel": "⚠️异常提示", "content": "Task marked as failed"}
        ]
      }
    ]
  },
  "truncated": false
}
```

Note: export must happen before `endSession()`, so the session and root task are still `RUNNING` while closed steps are `COMPLETED`/`FAILED`. Steps can also carry custom attributes via `attribute(key, value)` and tags via `tag(...)`; both appear in the export.

### 2. Spring Boot: one annotation traces a method

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-flow-spring-starter</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

Annotation interception is off by default; enable it explicitly:

```yaml
tfi:
  annotation:
    enabled: true
```

```java
import com.syy.taskflowinsight.annotation.TfiTask;

@TfiTask(value = "order.submit", logArgs = false, logResult = false)
public OrderResult submit(OrderCommand command) {
    return orderService.submit(command);
}
```

`@TfiTask` supports attributes such as `condition` (SpEL), `samplingRate`, `tags`, and `deepTracking`. Place it on public methods invoked through a Spring proxy. Keep `logArgs`/`logResult` off when sensitive data is involved.

### 3. Compare two object states

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-compare</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

```java
import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;

CompareOperations compare = CompareRuntime.defaults().engine();
CompareResult result = compare.compare(before, after);

var outcome = result.getOutcome();       // EQUAL / DIFFERENT / INDETERMINATE
var completion = result.getCompletion(); // COMPLETE / PARTIAL / FAILED / DISABLED
result.getChanges().forEach(change ->
        System.out.println(change.getFieldPath() + " (" + change.kind() + ")"));
```

**Always read `outcome` and `completion` together**: when a comparison finishes partially, fails, or is disabled, an empty change list does not prove the two objects are equal.

### 4. Everything at once: the aggregate and the unified facade

For Flow + Compare + Spring + Ops in one coordinate, use the aggregate (the artifactId is case-sensitive) and the unified `TFI` facade:

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>TaskFlowInsight</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

```java
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

CompareResult diff = TFI.compare(before, after);
System.out.println(TFI.render(diff)); // Markdown diff report
```

## Complete edition vs. Kernel edition (RC): how to choose

The repository hosts two product lines that solve the same class of problems with opposite trade-offs:

- **Complete edition** — the full-featured, ready-to-use component family: flow tree + annotation-based auto-instrumentation + automatic deep object comparison + Spring/Actuator integration + multiple export formats. The default choice for business applications; every quick start above uses it.
- **Kernel edition (RC)** — the minimalist experimental line: a thread-confined micro kernel `tfi-kernel` (depends only on slf4j-api) with a bounded `Session -> Stage -> Record` model where everything is explicit (explicit instrumentation, explicit change records, explicit sinks) and output is deterministic `tfi-flow/1` JSON. Aimed at platform teams that run their own collection/masking/storage pipeline and are willing to trade explicit instrumentation for minimal dependencies and deterministic output — as a **controlled trial**.

### Why the Kernel edition is not a "slimmed-down" complete edition

A "slim edition" would keep the same API with some capabilities removed, and existing code would keep running. The Kernel edition is not that — it is a **ground-up rewrite with opposite trade-offs**:

- **It was rewritten, not subtracted.** To avoid breaking the complete edition's API compatibility commitments (the japicmp gate), the Kernel was distilled from scratch under a new package, `com.syy.tfi.kernel`. Entry points (`TfiFlow.startSession()` vs `Tfi.begin()`), model vocabulary (Session → Task/Stage tree vs Session → Stage → Record), status enums (`COMPLETED/FAILED` vs `OK/ERROR/ABANDONED`), and JSON contracts (`schemaVersion: 2` vs `tfi-flow/1`) all differ — there is no smooth migration path between the two.
- **The key semantics are inverted, not missing.** Cross-thread goes from "async executions attach back to the same session automatically" to "thread-confined sessions plus explicit hand-off producing linked child sessions"; change recording goes from "automatic deep comparison" to "explicit `change()` only, with automatic diff permanently excluded from the kernel by design"; data egress goes from "exporters hand you the result" to "no sink by default, with egress and masking explicitly owned by the host".
- **The Kernel even has capabilities the complete edition lacks.** Per-record UTF-8 byte budgeting with explicit over-budget rejection, a zero-reflection JSON writer with fixed field order, and cross-thread misuse diagnostics — a true slim edition would only have less, never more; these were built specifically for platform teams running their own collection pipeline.

So read the table below not as "which has more features" but as "which way of working do you want":

| Dimension | Complete edition | Kernel edition (RC) |
|---|---|---|
| One-line positioning | Ready-to-use business-flow visualization + change tracking | Minimal, deterministic, everything-explicit flow-recording kernel |
| Who it is for | Business application teams that want flow trees and data diffs out of the box | Platform/infrastructure teams that own collection, masking, and storage themselves |
| Flow recording | Session → Task/Stage tree; `@TfiTask` annotation auto-instrumentation | Bounded Session → Stage → Record model; explicit instrumentation only |
| Object comparison | Automatic deep comparison, entity/collection strategies, Markdown reports | The kernel only accepts explicit `change(path, before, after)`; automatic diff comes from a bridge module (still an internal candidate) |
| Cross-thread | ThreadLocal context + propagation utilities; async executions attach back to the session automatically | Sessions are thread-confined; cross-thread hand-off is explicit via `capture()`, producing linked child sessions |
| Dependencies | Core modules usable in pure Java; add Spring/Actuator modules as needed | slf4j-api only |
| Output | Console tree, canonical JSON, Map, Markdown diff report | Console tree + canonical `tfi-flow/1` JSON; no sink by default, no data leaves the process |
| API stability | Guarded by the japicmp compatibility gate (`./mvnw verify -Papi-compat`) | Not frozen; breaking changes possible before the 1.0 baseline |
| Current status | Default recommendation; install `4.0.0-SNAPSHOT` from source | RC controlled trial; real-service pilot and the 1.0 release decision are still in progress |

The decision in three sentences:

1. **Business applications should use the complete edition** — if you want flow trees, automatic object comparison, Spring annotations, or Actuator operations, or if you are unsure, pick it.
2. **Try the Kernel edition only if all three hold**: you need nothing but a near-zero-dependency explicit flow-recording kernel; you have your own collection, masking, and storage pipeline on the consuming side; and you can absorb API changes before the 1.0 freeze.
3. **Comparison only**: depend on the complete edition's `tfi-compare` alone — no other module required.

> **Status warning**: the Kernel line's `tfi-kernel-compare` and `tfi-kernel-compare-spring-starter` have not passed the release gate and are currently in-repo candidates only — do not use them in production. `tfi-kernel` itself is open to controlled trials; existing `tfi-flow-core` users should simply stay where they are and must not treat "migrating to the Kernel" as an upgrade path.

### Module overview

Complete edition (groupId is always `com.syy`, version `4.0.0-SNAPSHOT`, depend only on what you use):

| Your need | artifactId |
|---|---|
| Record business flows, pure Java | `tfi-flow-core` |
| Annotation-based flow recording in Spring Boot | `tfi-flow-spring-starter` |
| Object comparison | `tfi-compare` |
| Spring-managed object comparison | `tfi-compare-spring-starter` |
| Operations: Actuator endpoints, metrics, health checks | `tfi-ops-spring` |
| Everything + unified `TFI` facade | `TaskFlowInsight` (aggregate) |

Kernel line (RC / internal candidates):

| Module | Status | Description |
|---|---|---|
| `tfi-kernel` | RC, open to controlled trials | Thread-confined micro kernel, depends only on slf4j-api |
| `tfi-compare-core` | Implemented and verified; release pending baseline activation | Pure-Java comparison kernel (extracted from `tfi-compare`) |
| `tfi-kernel-compare` | Internal candidate, release gate not passed | Pure-Java bridge between Kernel and Compare Core |
| `tfi-kernel-compare-spring-starter` | Internal candidate, release gate not passed | Spring Boot composition; AOP convenience off by default |

### Trying the Kernel (pure Java, controlled trial)

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-kernel</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

```java
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.Tfi;

try (Stage root = Tfi.begin("order.submit")) {
    root.attr("requestId", "req-1001");

    Tfi.stage("order.validate", () -> validateOrder(order)); // callback runs exactly once
    Tfi.change("order.status", "CREATED", "PAID");           // one explicit change record

    System.out.println(Tfi.currentToConsole()); // human-readable snapshot before close; currentToJson() for tfi-flow/1 JSON
}
// closing the root Stage freezes the Session and hands it synchronously to the configured FlowSinks
```

This is what `currentToJson()` emits as `tfi-flow/1` (excerpt — time fields omitted, session ID shortened):

```json
{
  "schema": "tfi-flow/1",
  "sessionId": "01KY...",
  "parentSessionId": null,
  "name": "order.submit",
  "status": "RUNNING",
  "truncated": false,
  "incompleteReasons": [],
  "attrs": {"requestId": "req-1001"},
  "root": {
    "name": "order.submit",
    "status": "RUNNING",
    "records": [
      {"type": "CHANGE", "code": "MANUAL_CHANGE", "data": {"path": "order.status", "before": "CREATED", "after": "PAID"}}
    ],
    "children": [{"name": "order.validate", "status": "OK", "records": [], "children": []}]
  }
}
```

Boundaries to know before a trial:

- **Thread-confined sessions**: records only take effect on the thread that created the session; calls from other threads stay no-op and produce diagnostics. For cross-thread scenarios, hand off explicitly with `Tfi.capture()` to create linked child sessions.
- **No sink by default**: the kernel never sends data anywhere on its own; the host implements `FlowSink` and owns masking and egress decisions.
- **Bounded recording**: deep copies and UTF-8 byte accounting happen at record time, and over-budget input is rejected explicitly; `truncated` and `incompleteReasons` flag incompleteness — never silent truncation.
- **Machine contract**: `tfi-flow/1` has a fixed field order and consumers read by field name — see the [schema document](tfi-kernel/docs/schema.md).

## Run the example application

`tfi-examples` is a runnable Spring Boot demo (default port `19090`, all TFI switches enabled, built on the complete edition):

```bash
./mvnw -pl tfi-examples spring-boot:run
```

Then try it from another terminal:

```bash
# @TfiTask annotation tracing
curl http://localhost:19090/api/demo/hello/TFI

# Nested stages + sampling + argument logging
curl -X POST http://localhost:19090/api/demo/process \
  -H 'Content-Type: application/json' -d '{"data":"test-payload"}'

# TFI runtime status (Actuator endpoint)
curl http://localhost:19090/actuator/taskflow
```

On startup the app also runs an automatic `@TfiTask` showcase (full output in the console). Command-line demos without Spring are available too:

```bash
# 10-chapter interactive tutorial (quick start, business scenarios, change
# tracking, async propagation, compare, annotations, ...)
./mvnw exec:java -pl tfi-examples            # interactive menu
./mvnw exec:java -pl tfi-examples -Dexec.args="all"   # run every chapter

# 7 compare-focused demos (scalars/dates/custom objects/collections/entity matching)
./mvnw exec:java -pl tfi-examples -Dexec.mainClass="com.syy.taskflowinsight.demo.Demo01_BasicTypes"
```

Note: the demo endpoints have no authentication or rate limiting — local exploration only, do not expose them.

## Project status

- **Source version**: the whole repository (Kernel line included) is `4.0.0-SNAPSHOT`, not yet formally released; install from source with `./mvnw clean install` before depending on it.
- **Runtime baseline**: Java 21; Spring Boot 3.5.5 only when you choose the Spring modules.
- **Quality**: each module enforces JaCoCo / SpotBugs / Checkstyle / PMD gates with dedicated CI workflows; complete-edition API changes are guarded by the japicmp compatibility check. The repository has no automated release workflow yet.
- **Kernel line progress**: at RC; until the real-service pilot and the 1.0 API-freeze decision complete, the per-module status in "Module overview" above is authoritative.

## Documentation

| Topic | Entry point |
|---|---|
| Compare quick starts (pure Java / Spring) | [non-spring-builder](docs/quickstart/non-spring-builder.md) · [spring-builder](docs/quickstart/spring-builder.md) |
| Manual API (Session/Task/Stage) | [docs/api/manual-api.md](docs/api/manual-api.md) |
| Compare deep dive (configuration, scenarios, troubleshooting) | [docs/comparison/INDEX.md](docs/comparison/INDEX.md) |
| Path-template and compare best practices | [docs/guides/path-template-compare-best-practices.md](docs/guides/path-template-compare-best-practices.md) |
| Example module runbook | [tfi-examples/docs/ops-doc.md](tfi-examples/docs/ops-doc.md) |
| Module design documents | [Flow Core](tfi-flow-core/docs/design-doc.md) · [Compare](tfi-compare/docs/design-doc.md) · [Kernel](tfi-kernel/docs/design-doc.md) · [tfi-flow/1 schema](tfi-kernel/docs/schema.md) |

## Contributing and license

Keep changes focused. When behavior changes, update the matching tests and the owning module's design document, run `./mvnw test` before opening a pull request, and include the exact verification command and result in the PR description.

TaskFlowInsight is licensed under the [Apache License 2.0](LICENSE).
