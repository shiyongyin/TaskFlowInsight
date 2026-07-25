# TaskFlowInsight

<div align="center">

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![CI](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml/badge.svg)](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml)

Business flow recording and object comparison for Java 21

[中文](README.zh-CN.md)

</div>

TaskFlowInsight records structured business execution flows and compares object state. The repository provides a complete, compatibility-oriented product line and a separate Kernel RC line.

The two lines solve related problems but are not dependency tiers of one runtime. Choose one line for an application; do not place both Compare implementations on the same classpath.

## Project status

- **Source version:** `4.0.0-SNAPSHOT`. Version 4.0 has not been published from this repository.
- **Runtime baseline:** Java 21. Spring integrations use Spring Boot 3.5.5.
- **Recommended line:** use the complete product line, either as the aggregate or as selected modules, for current integration work.
- **Preview line:** `tfi-kernel` is RC; the Kernel + Compare composition remains an internal technical preview until its release gates are complete.
- **Distribution:** build and install the snapshot from source before using it in another local project.

No benchmark number, compatibility baseline, or successful module build should be read as proof of a public release. The repository currently has CI and release-candidate gates, but no automated deploy or release workflow.

## TaskFlowInsight, TFI, and Kernel

The project name, Maven artifact names, and Java facade names are similar, but they do not identify the same runtime.

### Names and exact meanings

| Name | Kind | Product line | Meaning |
|---|---|---|---|
| TaskFlowInsight / TFI | Project shorthand | Repository | The project family containing both the complete line and the Kernel RC line |
| `com.syy:TaskFlowInsight` | Maven artifact | Complete | The aggregate built from the `tfi-all` directory |
| `com.syy.taskflowinsight.api.TFI` | Java class | Complete | The uppercase unified facade supplied by the aggregate |
| `tfi-flow-core` / `TfiFlow` | Maven artifact / Java class | Complete | The current plain-Java Flow implementation and its Flow-only facade |
| `tfi-kernel` | Maven artifact | Kernel | An independent RC flow-recording runtime with the `Session -> Stage -> Record` model |
| `com.syy.tfi.kernel.Tfi` | Java class | Kernel | A static facade over Kernel's lazy default `KernelRuntime` |
| `KernelRuntime` | Java class | Kernel | The explicit instance owner for Kernel config, context, diagnostics, sinks, and shutdown |

`TFI` and `Tfi` are different Java types in different packages. The Kernel `Tfi` facade is not a compatibility alias for the complete-line `TFI` facade.

### Current relationship

```text
TaskFlowInsight repository and 4.0 release train
├── Complete product line                         current recommendation
│   ├── tfi-flow-core + tfi-flow-spring-starter
│   ├── tfi-compare + tfi-compare-spring-starter
│   ├── tfi-ops-spring
│   └── tfi-all -> com.syy:TaskFlowInsight -> TFI facade
└── Kernel RC line                                source pilot / internal preview
    ├── tfi-kernel -> KernelRuntime / Tfi
    ├── tfi-compare-core
    ├── tfi-kernel-compare
    └── tfi-kernel-compare-spring-starter
```

There is deliberately no dependency arrow between the two lines.

1. The complete `TaskFlowInsight` aggregate does not include `tfi-kernel` or any Kernel/Compare composition artifact.
2. `tfi-flow-core` and `tfi-kernel` currently do not depend on or delegate to each other, and no existing API has been migrated between them.
3. Kernel is not an internal engine hidden below `TFI`, a smaller configuration of Flow Core, or an announced next version of the complete line.
4. The two core flow artifacts use separate package roots and can be present at the classpath level, but running two recording owners requires explicit ownership, sampling, export, and shutdown rules.
   The Kernel Spring composition is not designed as a mixed-runtime compatibility layer; its startup guard detects duplicate Compare Core classes and the legacy tracking shell, not every complete-line artifact.
5. Long-term parallel operation, delegation, or a major-version replacement can be decided only after the Kernel real-service pilot. No such decision exists today.

Both lines inherit the reactor version `4.0.0-SNAPSHOT`. Kernel's separate “first stable API baseline 1.0” describes a future compatibility milestone for that module; it is not the current Maven artifact version.

### Why a separate Kernel exists

Flow Core already owns real contracts for providers, managed contexts, compatibility, asynchronous propagation, snapshots, and multiple exporters.
Removing those contracts in place would break the complete line. Kernel was created in a separate package and artifact to test whether a much narrower runtime model has independent value.

The reduction is primarily in model and runtime responsibility, not merely in dependency count: both plain-Java cores have `slf4j-api` as their only third-party runtime dependency.

| Dimension | Complete Flow (`tfi-flow-core` and integrations) | Kernel RC line |
|---|---|---|
| Current role | Current integration recommendation in this repository | Controlled source pilot for a narrower runtime boundary |
| Flow model | `Session -> TaskNode -> Message`, tags, attributes, typed messages | `Session -> Stage -> Record`; machine facts are `type + code + data` |
| Main Java entry | `TFI` through the aggregate, or Flow-only `TfiFlow` | Static `Tfi`, or an explicit `KernelRuntime` instance |
| Runtime ownership | Facade routes through Provider Registry and managed context facilities | Each `KernelRuntime` owns config, one ThreadLocal, diagnostics, sinks, and close state |
| Extension model | Provider Registry, ServiceLoader, Flow and Export providers | Four programmatic SPIs: `FlowSink`, `Sampler`, `IdGenerator`, `KernelClock` |
| Cross-thread model | Managed context snapshots and propagation facilities | `capture/wrap` creates an independent child Session linked by `parentSessionId`; trees are not merged |
| Output | Console, canonical JSON, Map, and replaceable Export Provider | Console and deterministic `tfi-flow/1` JSON; completed sessions go to synchronous sinks |
| Resource behavior | Broader compatibility and context contracts with module-specific quality and resource limits | Admission-time deep copy plus explicit Stage, Session-byte, Record-byte, and attribute budgets |
| Object comparison | Add complete `tfi-compare` and its integrations | Kernel alone records explicit scalar changes; Compare Core and the bridge are separate preview artifacts |
| Spring and operations | Dedicated Spring starters plus Ops implementations | Kernel Core has neither; the Kernel composition starter still has no Ops surface |
| Compatibility | Preserves current facades and module compatibility gates | RC API is not frozen and has no drop-in API or schema conversion layer |
| Migration cost | Existing TFI applications stay on this line | Requires application-level redesign of entry points, model, output, config, and operations |

## Choose a setup

| Requirement | Recommended choice | Reason |
|---|---|---|
| All current Flow, Compare, Spring, and Ops capabilities | `com.syy:TaskFlowInsight` | One dependency and the unified `TFI` facade |
| Flow recording only, without Spring | `tfi-flow-core` | Complete Session/Task model and exporters with a smaller dependency graph |
| Object comparison only | `tfi-compare` | Current complete Compare API, compatibility facade, SPI, query, and rendering support |
| Spring `@TfiTask` flow recording | `tfi-flow-spring-starter` | Flow auto-configuration and AOP without Compare or Ops |
| Spring-managed comparison | `tfi-compare-spring-starter` | One Compare runtime per Spring ApplicationContext; Flow tracking is optional and explicit |
| Actuator, metrics, health, REST, or in-memory storage | Add `tfi-ops-spring` and wire the required components | Operational implementations stay outside the core modules |
| Minimal explicit flow recording for a source pilot | `tfi-kernel` | Small `Session -> Stage -> Record` model and deterministic JSON; RC only |
| Kernel + Compare composition | Internal preview modules | Useful for evaluation, not a production dependency recommendation yet |

Start with the smallest current module that owns the capability you need. Use the aggregate when dependency convenience and the unified facade matter more than a narrow classpath.

## Module map

The root Maven reactor contains 11 reactor modules. The root project has `pom` packaging and is not a runnable Spring Boot application.

Each indented child below is a module that its parent depends on. Optional dependencies are marked and are not propagated as ordinary transitive dependencies.

### Complete product line

```text
TaskFlowInsight  (artifactId; source directory: tfi-all)
├── tfi-flow-core
├── tfi-flow-spring-starter
│   └── tfi-flow-core
├── tfi-compare
│   └── tfi-flow-core
├── tfi-compare-spring-starter
│   ├── tfi-compare
│   └── tfi-flow-spring-starter          optional
└── tfi-ops-spring
    ├── tfi-flow-core
    └── tfi-compare                      optional

tfi-examples
├── TaskFlowInsight
├── tfi-flow-spring-starter
├── tfi-compare
└── tfi-ops-spring
```

The aggregate includes the five complete-line modules shown above. It does not include `tfi-kernel`, `tfi-compare-core`, or either Kernel/Compare integration module.

### Kernel module dependency graph

```text
tfi-kernel-compare-spring-starter
└── tfi-kernel-compare
    ├── tfi-kernel
    └── tfi-compare-core
```

The Kernel Spring starter also uses Spring Boot and has optional AOP support. Its own POM excludes complete-line dependencies; its application startup guard separately detects duplicate Compare Core classes and the legacy tracking shell.

### Relationship rules

1. `tfi-compare` and `tfi-compare-core` are parallel artifacts with overlapping class names. They are mutually exclusive runtime choices.
2. `TaskFlowInsight` is the Maven artifactId of the `tfi-all` directory. The capital letters are significant.
3. `tfi-examples` is a runnable consumer and test fixture, not a library dependency for applications.
4. Do not run Kernel and Flow Core as two independent recorders unless your application defines ownership, export, sampling, and shutdown semantics for both.

## Module responsibilities

| Reactor module | Line | Responsibility and boundary | Status |
|---|---|---|---|
| `tfi-kernel` | Kernel | Minimal plain-Java flow recorder with explicit stages, calls, records, synchronous sinks, and deterministic `tfi-flow/1` JSON | RC |
| `tfi-flow-core` | Complete | Session, Task, Message, Context, Provider, async context propagation, and Console/Map/JSON export | Current complete line |
| `tfi-compare-core` | Kernel | Comparison truth, resource bounds, typed paths, canonical projection, and render models without Flow or Spring | Technical preview |
| `tfi-kernel-compare` | Kernel | Maps an existing `CompareResult` into a Kernel summary and optional masked detail records; owns no business action or sink | Internal candidate |
| `tfi-kernel-compare-spring-starter` | Kernel | Builds one Spring context for Kernel, Compare Core, and the bridge; programmatic use is primary and AOP is optional | Internal candidate |
| `tfi-compare` | Complete | Complete Compare runtime plus compatibility facade, SPI, list APIs, tracking, merge, query, summary, and rendering support | Current complete line |
| `tfi-flow-spring-starter` | Complete | Flow auto-configuration, `@TfiTask` AOP, SpEL evaluation, masking, and context configuration | Current complete line |
| `tfi-compare-spring-starter` | Complete | One Compare policy, runtime, engine, and masking graph per Spring ApplicationContext, with optional Flow tracking | Current complete line |
| `tfi-ops-spring` | Complete | Provides Actuator, REST, Micrometer, health, performance, and Caffeine store implementations; Compare is optional | Current complete line |
| `tfi-examples` | Consumer | Runnable Spring Boot and command-line examples plus benchmark fixtures | Development only |
| `tfi-all` | Complete | Builds artifact `TaskFlowInsight`, re-exports the complete line, and owns the unified `TFI` facade | Current aggregate |

`tfi-kernel` belongs to the TaskFlowInsight 4.0 RC train. It targets 1.0 as its first stable API baseline, but that API is not frozen until the real-service pilot and release decision are complete.

`tfi-compare-core` has implemented and verified core behavior, but its baseline and final composition release gates are incomplete. The bridge and Kernel Spring starter must not be treated as published or production-ready artifacts.

## Version and source build

### Prerequisites

- JDK 21
- Maven 3.9+, or the included Maven Wrapper 3.9.11
- Spring Boot only when a Spring module is selected

Install the current snapshot into the local Maven repository:

```bash
git clone https://github.com/shiyongyin/TaskFlowInsight.git
cd TaskFlowInsight
./mvnw clean install
```

The first build may download Maven plugins and dependencies. In another local project, define the version once:

```xml
<properties>
    <tfi.version>4.0.0-SNAPSHOT</tfi.version>
</properties>
```

All dependency snippets below use `${tfi.version}`. Replace it only with a version that is actually available in your configured repository.

## Full version

The full version is the broadest complete-line option. It includes Flow Core, both complete-line Spring starters, Compare, and Ops, while preserving the unified `TFI` facade.

Choose it for an existing TFI application, a migration that depends on the facade, or an application that genuinely uses most capabilities. The trade-off is a wider dependency and auto-configuration surface.

### Add the aggregate

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>TaskFlowInsight</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

The artifactId is case-sensitive. `tfi-all` is only the repository directory name.

### Use the unified API

```java
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

TFI.startSession("order.submit");
try {
    try (TaskContext stage = TFI.stage("order.validate")) {
        stage.attribute("requestId", "req-1001")
                .message("validation completed")
                .success();
    }

    CompareResult difference = TFI.compare(before, after);
    String report = TFI.render(difference);
    String flowJson = TFI.exportToJson();
} finally {
    TFI.endSession();
}
```

Export the flow before `endSession()`. Comparison truth comes from `CompareResult`; rendering is a presentation step and does not change the result.

## Selective complete-line modules

Selective use keeps the current complete-line semantics while avoiding capabilities that the application does not need. This is the recommended “minimal” choice for current integrations.

### Flow Core only

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-flow-core</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

```java
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.api.TfiFlow;

TfiFlow.startSession("order.submit");
try {
    try (TaskContext stage = TfiFlow.stage("order.validate")) {
        stage.attribute("requestId", "req-1001")
                .message("validation completed")
                .success();
    }
    String json = TfiFlow.exportToJson();
} finally {
    TfiFlow.endSession();
}
```

`TfiFlow` is pure Java. In pooled-thread code, keep session cleanup in `finally`; `TfiFlow.clear()` is available for defensive cleanup at an integration boundary.

### Spring Flow

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-flow-spring-starter</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

Annotation interception is disabled by default. Enable it explicitly:

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

Place `@TfiTask` on a public method reached through a Spring proxy. Keep argument and result capture disabled unless the data classification and masking policy permit it.

### Compare only

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-compare</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

```java
import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;

CompareOperations compare = CompareRuntime.defaults().engine();
CompareResult result = compare.compare(before, after);

var outcome = result.getOutcome();
var completion = result.getCompletion();
```

Always read both `outcome` and `completion`. An empty change list does not prove equality when execution is partial, failed, disabled, or otherwise indeterminate.

`CompareService.defaults().compare(before, after)` remains a compatibility entry point. New direct integrations should depend on the narrower `CompareOperations` contract.

### Spring Compare

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-compare-spring-starter</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

The starter publishes one `CompareEngine` per Spring ApplicationContext. The engine implements `CompareOperations`:

```java
import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

public final class OrderDiffService {
    private final CompareOperations compare;

    public OrderDiffService(CompareOperations compare) {
        this.compare = compare;
    }

    public CompareResult compare(Order before, Order after) {
        return compare.compare(before, after);
    }
}
```

Plain comparison is enabled by the default policy. Deep tracking also requires Flow Starter, the Flow annotation aspect, an explicit Compare opt-in, and `deepTracking = true` on the method:

```yaml
tfi:
  annotation:
    enabled: true
  compare:
    tracking:
      enabled: true
```

```java
import com.syy.taskflowinsight.annotation.TfiTask;

@TfiTask(
        value = "order.submit",
        deepTracking = true,
        logArgs = false,
        logResult = false)
public OrderResult submit(OrderCommand command) {
    return orderService.submit(command);
}
```

### Ops

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-ops-spring</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

Ops depends on Flow Core and treats Compare as optional. For auto-configured Compare observation, also use `tfi-compare-spring-starter`, or provide one local Compare runtime and engine graph yourself.

The current snapshot auto-registers only the Compare version guard, observation decorator, and health composition. Plain `tfi-compare` does not create the Spring beans required by that composition.

Other endpoint, store, and performance classes require explicit application wiring. Adding the dependency alone does not expose those endpoints or create a store.

Actuator exposure and endpoint access still require Spring management settings and application security. Review both before exposing an endpoint outside a trusted network.

## Kernel RC line

The Kernel line is a separate runtime design, not a smaller dependency selection within the complete line.
It deliberately omits the complete line's compatibility facades, Provider Registry, managed context and exporter infrastructure, and Ops surface.

Use it only for controlled source pilots until the owning release gates close. `tfi-kernel` is RC, while Compare Core and both composition artifacts have stricter preview status.

### Kernel family modules

| Module | Direct internal dependencies | Adds | Deliberately does not add | Status |
|---|---|---|---|---|
| `tfi-kernel` | None | Bounded flow recording, deterministic JSON, instance runtime, static facade, four SPIs | Object comparison, Spring, persistence, built-in network output, Ops | RC |
| `tfi-compare-core` | None | Comparison truth, limits, typed paths, canonical projection, masking floor | Flow recording, Kernel records, Spring, complete-line compatibility APIs | Technical preview |
| `tfi-kernel-compare` | Both cores | Maps an existing `CompareResult` to a Kernel summary and optional safe detail prefix | Running business actions, changing CompareResult truth, Sink, threads, Spring | Internal candidate |
| `tfi-kernel-compare-spring-starter` | `tfi-kernel-compare` | ApplicationContext runtimes, lifecycle, config, guard, optional AOP | Actuator, metrics, Store, HTTP, queues, retries, async export | Internal candidate |

The two cores remain independently usable and never depend on each other. The bridge is an independent plain-Java integration above both cores; the starter is the separate Spring Boot integration above the bridge.

### Kernel only

After building this checkout locally, add the RC artifact:

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-kernel</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

#### Choose an entry point

| Entry | Ownership model | Use when |
|---|---|---|
| `Tfi` | Static facade over one lazy default Runtime; `Tfi.configure` affects only later Sessions | A small application intentionally owns one process-wide Kernel configuration |
| `KernelRuntime` | Explicit, isolated, `AutoCloseable` instance with configuration frozen at creation | Dependency injection, multiple instances, test isolation, explicit Sink ownership, or managed shutdown |

The Kernel `Tfi` class is only a convenience facade for Kernel APIs. It does not delegate to the complete-line `TFI` class.

#### Record and receive a completed flow

```java
import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.Tfi;
import com.syy.tfi.kernel.spi.FlowSink;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

AtomicReference<String> completedJson = new AtomicReference<>();
KernelConfig defaults = KernelConfig.defaults();
FlowSink sink = session -> completedJson.set(Tfi.toJson(session));
KernelConfig config = new KernelConfig(
        true,
        List.of(sink),
        defaults.sampler(),
        defaults.idGenerator(),
        defaults.clock(),
        defaults.maxStages(),
        defaults.maxSessionEncodedBytes(),
        defaults.maxRecordEncodedBytes(),
        defaults.maxAttrs());

try (KernelRuntime runtime = KernelRuntime.create(config)) {
    try (Stage flow = runtime.begin("order.submit")) {
        flow.attr("requestId", "req-1001");
        String state = runtime.call("inventory.reserve", () -> "RESERVED");
        flow.change("order.status", "CREATED", state);
        flow.message("order accepted");
    }
}

String json = completedJson.get();
```

Closing the root `Stage` freezes the Session and synchronously invokes configured sinks in order.
`runtime.currentToJson()` and `runtime.currentToConsole()` are active snapshots only: they neither close nor publish the Session. `Tfi.toJson(session)` is a pure conversion and does not authorize data egress.

The default configuration has no Sink, so it does not publish Sessions or business Records through a Sink, write files, publish messages, or make network requests.

Diagnostic paths such as invalid input, cross-thread use, abandoned Sessions, or facility failures may still emit rate-limited WARN logs.

A production Sink must own masking, destination authorization, timeout, persistence, retention, and failure policy.

#### Runtime contract

| Concern | Kernel behavior |
|---|---|
| Business transparency | `stage/call` executes the callback once despite disabled, unsampled, out-of-context, or ordinary facility failure paths; return and business exception identity are preserved |
| Lifecycle | `begin` opens the root Session; nested `begin` becomes a child Stage; closing the root freezes and publishes `OK` or `ERROR`; `clear` abandons incomplete state without publishing it |
| Thread ownership | A Session tree and its budget ledger can be changed only by the owner thread; cross-thread use of a Stage is diagnosed and becomes a no-op |
| Context handoff | `capture().wrap(...)` creates a new linked child Session for each execution; `parentSessionId` connects it to the source Session, but mutable Stage trees are never shared or merged |
| Data model | Machine consumers use `Record.type + code + data`; natural-language `text` is presentation only; accepted structured data is deep-copied into an immutable JSON-like value set |
| Default budgets | 64 Stages, 12 KiB encoded Session, 2 KiB encoded Record or attribute value, 32 attributes, and a fixed maximum stack depth of 64 |
| Truncation | Budgets use escaped UTF-8 bytes; a candidate is accepted atomically or rejected, and `truncated/incompleteReasons` preserves the loss of completeness |
| Shutdown | `KernelRuntime.close()` is idempotent and irreversible, stops new publication, and waits for already registered synchronous Sink calls to return |

Kernel has no Registry, ServiceLoader, background thread, asynchronous queue, retry loop, or shutdown hook. Its four extension points are only `FlowSink`, `Sampler`, `IdGenerator`, and `KernelClock`, all supplied programmatically.

### Compare Core only

After installing this checkout locally, use the preview artifact only in an isolated source pilot:

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-compare-core</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

Use `CompareRuntime.defaults().engine()` and keep business code typed to `CompareOperations`, as shown in the complete-line Compare example.

Do not include `tfi-compare-core` with `tfi-compare`: the artifacts contain overlapping class names.
Compare Core deliberately omits the complete module's Flow dependency, compatibility facade, SPI integration, tracking adapters, query helpers, and other peripheral APIs.

### Kernel + Compare bridge

The bridge does not put comparison logic inside Kernel. It preserves the separation between business truth and observation:

```text
before / after
     |
     v
CompareOperations ----> CompareResult ----> business decision
                              |
                              v
                    KernelCompareRecorder
                              |
                              v
Kernel Stage ----> KCOMPARE_SUMMARY_V1 + optional safe detail prefix
                              |
                    root Stage closes
                              v
                         FlowSink
```

`tfi-kernel-compare` accepts host-selected `CompareOperations` and records a bounded summary plus optional masked detail into the current Kernel `Stage`.
It does not own the business action, re-evaluate comparison truth, create threads, or publish to a Sink.

Business decisions must read `CompareResult` directly and must not depend on whether a Record fit within the Kernel budget. When the result is required for business logic, compare first and then record it:

```java
import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.compare.KernelCompareRecorder;

try (Stage flow = kernelRuntime.begin("order.update")) {
    CompareResult result = compare.compare(before, after);
    recorder.record(flow, "order.update", result);

    var outcome = result.getOutcome();
    var completion = result.getCompletion();
}
```

The default record policy attempts at most one summary and no detail; Kernel can still reject that Record.
If detail is enabled, the bridge records at most 32 canonical, masked changes and stops at the first Kernel budget rejection.

### Kernel Spring composition

The Kernel starter assembles or adopts exactly one `KernelRuntime` and one Compare Runtime for the current ApplicationContext. It then completes the Compare, masking/projection, and recorder objects around them.

The three capabilities have separate switches:

```yaml
tfi:
  kernel:
    enabled: true
  compare:
    enabled: true
  kernel-compare:
    enabled: true
    max-recorded-changes: 0
```

Setting `tfi.kernel-compare.enabled=false` removes the Record policy and recorder but leaves both core runtimes available. Configuration is frozen when the ApplicationContext starts; dynamic refresh is not supported.

For Kernel, an application can provide individual SPIs, one complete `KernelConfig`, or one complete `KernelRuntime`.
For Compare, it can provide a custom `ComparePolicy` or one complete Compare Runtime. One local safe `MaskingPolicy` can replace the default.

Mixing configuration levels or replacing the Runtime-derived Engine or composition Recorder fails startup instead of producing a partial set of beans.

A custom `KernelRuntime` becomes owned by that ApplicationContext and is closed with it. The application must not reuse that Kernel Runtime instance across contexts or after its owning context closes.

Optional AOP additionally requires `spring-boot-starter-aop` and is disabled by default. Enabling the property without that dependency fails startup instead of silently backing off:

```yaml
tfi:
  kernel-compare:
    aop:
      enabled: true
```

```java
import com.syy.tfi.kernel.compare.spring.annotation.TfiTrackTarget;
import com.syy.tfi.kernel.compare.spring.annotation.TfiTracked;

@TfiTracked(operation = "order.update")
public void update(@TfiTrackTarget("order") Order order) {
    order.markPaid();
}
```

This AOP path uses fixed annotations rather than SpEL. `@TfiTracked` applies to public methods, and operation must match `[a-z][a-z0-9._-]{0,62}`.

Each method must declare at least one target, and every target argument must be non-null when invoked. Target names must be unique and match `[a-z][a-z0-9_-]{0,63}`. Interface and implementation declarations must agree.

The default policy remains summary-only, and the Record is an in-memory observation rather than proof that an enclosing transaction committed.

The starter module's own Maven build bans complete Flow, complete Compare, the complete-line starters, Ops, Examples, and the aggregate. That Enforcer rule is not inherited by consumer projects.

At application startup, the guard rejects duplicate `CompareRuntime.class` resources and the legacy `TrackingProvider.class` marker.

Consumers must still inspect their dependency tree and select one ecosystem. Migrating is an application redesign, not a Maven coordinate swap; old facades, tracking delegates, and property aliases are not loaded.

The bridge and starter remain internal candidates. Build them only inside this reactor for evaluation.
Do not add them to a production dependency set until the [KCS-10 release gate](docs/task/tfi-kernel-compare-integration/TASK-KCS-10-consumer-release-and-reactor-gates.md) and owner decision are complete.

## Scope and trade-offs

| Dimension | Full aggregate | Selective complete line | Kernel RC composition |
|---|---|---|---|
| Included scope | Flow, Compare, both Spring starters, Ops, unified facade | Only selected complete-line capabilities | Kernel, Compare Core, bridge, optional Spring composition |
| Flow model | Session, Task, Message, Provider, Context, async propagation | Same model when Flow is selected | Session, Stage, Record, explicit calls, synchronous sink |
| Compare scope | Complete Compare APIs and integrations | Same Compare module when selected | Core truth, bounds, typed paths, and canonical projection |
| Spring model | Broad auto-configuration surface | Only selected starters | One composition per Spring ApplicationContext; AOP optional and off by default |
| Operational capabilities | Ops module and implementation types included | Add and wire Ops only when needed | Not provided |
| API compatibility | Unified `TFI` facade and compatibility entry points | Current module APIs | New APIs; not an in-place replacement |
| Dependency cost | Widest | Narrower and controllable | Smallest intended runtime boundary |
| Migration cost | Lowest for existing TFI users | Low to moderate | High; application integration must be redesigned |
| Current maturity | Source snapshot, current complete feature line | Source snapshot, current recommended modular use | RC / internal technical preview |

The full aggregate optimizes integration convenience. Selective modules make dependency ownership explicit without changing the current model.

The Kernel line optimizes explicit boundaries and bounded behavior, at the cost of compatibility and maturity.

## Recommendations

1. **Existing TFI application:** stay on the complete line. Move from the aggregate to selected modules only when the dependency reduction is worth testing the new composition.
2. **New Spring application:** start with the specific Flow or Compare starter. Add Ops only for a concrete operational requirement.
3. **Pure Java flow recording:** use `tfi-flow-core` for the current complete model. Evaluate `tfi-kernel` only when its smaller explicit model is the actual requirement and RC change is acceptable.
4. **Pure object comparison:** use `tfi-compare` today. Evaluate `tfi-compare-core` only from source and only on a classpath that excludes `tfi-compare`.
5. **Need every complete-line capability:** use `TaskFlowInsight`; its convenience is valuable when most of the aggregate is used.
6. **Need Kernel + Compare:** run a controlled source pilot. Do not present the bridge or Kernel starter as a released production option before the final gates pass.

When uncertain, select a module by ownership rather than by artifact count: Flow owns execution structure, Compare owns object truth, Spring starters own container wiring, and Ops owns exposure and storage.

## Configuration and operational boundaries

| Prefix or switch | Owner | Important behavior |
|---|---|---|
| `tfi.annotation.enabled` | Flow Spring Starter | Enables `@TfiTask` AOP; default is off |
| `tfi.context.*` | Flow Spring Starter | Context lifecycle and propagation settings |
| `tfi.security.*` | Flow Spring Starter | Flow-side masking and security settings |
| `tfi.compare.*` | Compare Spring Starter or Kernel starter | Immutable comparison policy and resource limits |
| `tfi.compare.tracking.enabled` | Compare Spring Starter | Connects Compare to Flow tracking; default is off and Flow Starter is required |
| `tfi.kernel.*` | Kernel Spring starter | Kernel enablement and four resource budgets; SPI implementations and sinks are local beans |
| `tfi.kernel-compare.*` | Kernel Spring starter | Bridge and optional AOP settings; AOP is off by default |
| `tfi.store.*`, `tfi.actuator.*`, `tfi.endpoint.*` | Ops | Settings for explicitly wired stores and endpoints; review each component's own default |

Configuration cannot replace boundary design. Set finite comparison and sink budgets, keep sensitive values out of labels, and expose operational endpoints only through the application's authentication and network controls.

Performance depends on object shape, path rules, sampling, sinks, JDK, and hardware. Re-run the repository workloads on the target environment instead of copying a benchmark number into a service SLO.

## Example application

`tfi-examples` is the runnable Spring Boot module. Start it from the repository root:

```bash
JAVA_TOOL_OPTIONS="-Dspring.profiles.active=local" \
  ./mvnw -pl tfi-examples spring-boot:run
```

The example application listens on port `19090` by default. It is a consumer of the library modules and must not be added as an application dependency.

## Build, tests, and CI/CD

### Local commands

```bash
# Fast unit and slice-test loop
./mvnw test

# One module plus required upstream modules
./mvnw -pl tfi-flow-core -am test

# Full reactor tests, module quality gates, and packaging
./mvnw clean verify

# Build module artifacts without cleaning
./mvnw package
```

Each module owns its Maven quality configuration. In particular, `tfi-flow-core` has module-specific JaCoCo, SpotBugs, and Checkstyle gates.

PMD and reports from other modules must be interpreted against their owning POMs.

API compatibility checks use the owning module's `api-compat` profile and are run explicitly by the relevant CI workflows; they are not implied by the base command above.

### CI and release gates

| Workflow | Scope |
|---|---|
| [`tfi-kernel-ci.yml`](.github/workflows/tfi-kernel-ci.yml) | Kernel verification, reactor regression, example, benchmark report, and candidate artifact |
| [`tfi-kernel-perf-gate.yml`](.github/workflows/tfi-kernel-perf-gate.yml) | Manual `tfi-kernel Strict Perf Gate` on a fixed self-hosted runner |
| [`tfi-flow-core-ci.yml`](.github/workflows/tfi-flow-core-ci.yml) | Flow Core tests, coverage, consumers, compatibility, and static analysis |
| [`tfi-compare-ci.yml`](.github/workflows/tfi-compare-ci.yml) | Compare verification, dependency audit, compatibility, consumers, and release evidence |
| [`tfi-compare-allocation-gate.yml`](.github/workflows/tfi-compare-allocation-gate.yml) | Compare shared-source and slim-composition allocation budgets |
| [`tfi-flow-spring-starter-ci.yml`](.github/workflows/tfi-flow-spring-starter-ci.yml) | Flow Spring Starter checks |
| [`tfi-ops-spring-ci.yml`](.github/workflows/tfi-ops-spring-ci.yml) | Ops checks |
| [`tfi-all-ci.yml`](.github/workflows/tfi-all-ci.yml) | Aggregate tests, compatibility, and analysis |
| [`tfi-examples-ci.yml`](.github/workflows/tfi-examples-ci.yml) | Example compilation and tests |
| [`perf-gate.yml`](.github/workflows/perf-gate.yml) | Strict routing and legacy JMH regression gates |

Not every reactor module has a separate workflow. Some internal and Spring modules are covered through composition, consumer, allocation, or aggregate gates.

There is currently no CD workflow that deploys Maven artifacts, creates tags, or publishes releases. A successful `package` or CI run produces evidence and candidate artifacts only.

## Documentation

Use current module-owned documents as the source of truth. Files under `docs/product/architecture/` are historical background and must not drive implementation.

| Area | Current entry points |
|---|---|
| Flow Core | [Documentation index](tfi-flow-core/docs/index.md), [architecture SSOT](tfi-flow-core/docs/design-doc.md) |
| Complete Compare | [Documentation index](tfi-compare/docs/index.md), [architecture SSOT](tfi-compare/docs/design-doc.md) |
| Kernel | [Design](tfi-kernel/docs/design-doc.md), [JSON schema](tfi-kernel/docs/schema.md), [API inventory](tfi-kernel/docs/api-inventory.md) |
| Compare Core | [Design boundary](tfi-compare-core/docs/design-doc.md) |
| Kernel/Compare bridge | [Internal status and navigation](tfi-kernel-compare/docs/index.md) |
| Kernel Spring composition | [Internal status](tfi-kernel-compare-spring-starter/docs/index.md), [migration boundary](tfi-kernel-compare-spring-starter/docs/migration.md) |

## Contributing and license

Keep changes focused and update matching tests and the owning architecture document when behavior changes. Run `./mvnw test` before submitting a pull request.

Include the exact verification command and result in the pull request description.

TaskFlowInsight is licensed under the [Apache License 2.0](LICENSE).
