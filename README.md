# TaskFlowInsight

<div align="center">

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![CI](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml/badge.svg)](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml)

Business flow recording and object comparison for Java 21

[中文](README.zh-CN.md)

</div>

TaskFlowInsight records structured business execution flows and compares object state. The repository provides a complete, compatibility-oriented product line and a separate slim Kernel/Compare composition line.

The two lines solve related problems but are not dependency tiers of one runtime. Choose one line for an application; do not place both Compare implementations on the same classpath.

## Project status

- **Source version:** `4.0.0-SNAPSHOT`. Version 4.0 has not been published from this repository.
- **Runtime baseline:** Java 21. Spring integrations use Spring Boot 3.5.5.
- **Recommended line:** use the complete product line, either as the aggregate or as selected modules, for current integration work.
- **Preview line:** `tfi-kernel` is RC; the Kernel + Compare composition remains an internal technical preview until its release gates are complete.
- **Distribution:** build and install the snapshot from source before using it in another local project.

No benchmark number, compatibility baseline, or successful module build should be read as proof of a public release. The repository currently has CI and release-candidate gates, but no automated deploy or release workflow.

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
| Slim Kernel + Compare composition | Internal preview modules | Useful for evaluation, not a production dependency recommendation yet |

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

### Slim composition line

```text
tfi-kernel-compare-spring-starter
└── tfi-kernel-compare
    ├── tfi-kernel
    └── tfi-compare-core
```

The slim Spring starter also uses Spring Boot and has optional AOP support. It deliberately rejects the complete Flow, Compare, Starter, Ops, and aggregate artifacts at build or startup boundaries.

### Relationship rules

1. `tfi-compare` and `tfi-compare-core` are parallel artifacts with overlapping class names. They are mutually exclusive runtime choices.
2. `TaskFlowInsight` is the Maven artifactId of the `tfi-all` directory. The capital letters are significant.
3. `tfi-examples` is a runnable consumer and test fixture, not a library dependency for applications.
4. Do not run Kernel and Flow Core as two independent recorders unless your application defines ownership, export, sampling, and shutdown semantics for both.

## Module responsibilities

| Reactor module | Line | Responsibility and boundary | Status |
|---|---|---|---|
| `tfi-kernel` | Slim | Minimal plain-Java flow recorder with explicit stages, calls, records, synchronous sinks, and deterministic `tfi-flow/1` JSON | RC |
| `tfi-flow-core` | Complete | Session, Task, Message, Context, Provider, async context propagation, and Console/Map/JSON export | Current complete line |
| `tfi-compare-core` | Slim | Comparison truth, resource bounds, typed paths, canonical projection, and render models without Flow or Spring | Technical preview |
| `tfi-kernel-compare` | Slim | Maps an existing `CompareResult` into a Kernel summary and optional masked detail records; owns no business action or sink | Internal candidate |
| `tfi-kernel-compare-spring-starter` | Slim | Builds one Spring context for Kernel, Compare Core, and the bridge; programmatic use is primary and AOP is optional | Internal candidate |
| `tfi-compare` | Complete | Complete Compare runtime plus compatibility facade, SPI, list APIs, tracking, merge, query, summary, and rendering support | Current complete line |
| `tfi-flow-spring-starter` | Complete | Flow auto-configuration, `@TfiTask` AOP, SpEL evaluation, masking, and context configuration | Current complete line |
| `tfi-compare-spring-starter` | Complete | One Compare policy, runtime, engine, and masking graph per Spring ApplicationContext, with optional Flow tracking | Current complete line |
| `tfi-ops-spring` | Complete | Provides Actuator, REST, Micrometer, health, performance, and Caffeine store implementations; Compare is optional | Current complete line |
| `tfi-examples` | Consumer | Runnable Spring Boot and command-line examples plus benchmark fixtures | Development only |
| `tfi-all` | Complete | Builds artifact `TaskFlowInsight`, re-exports the complete line, and owns the unified `TFI` facade | Current aggregate |

`tfi-kernel` belongs to the TaskFlowInsight 4.0 RC train. It targets 1.0 as its first stable API baseline, but that API is not frozen until the real-service pilot and release decision are complete.

`tfi-compare-core` has implemented and verified core behavior, but its baseline and final composition release gates are incomplete. The bridge and slim Spring starter must not be treated as published or production-ready artifacts.

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

## Slim composition preview

The slim line is a new runtime model, not a drop-in replacement for the complete line. It does not carry the complete line's compatibility facades, global provider lookup, default background facilities, or Ops surface.

Before the Kernel and slim-composition release gates close, use this line only for source pilots. API changes and migration work are still possible.

### Kernel only

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-kernel</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

```java
import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.Stage;

try (KernelRuntime runtime = KernelRuntime.create(KernelConfig.defaults());
     Stage flow = runtime.begin("order.submit")) {
    flow.attr("requestId", "req-1001");
    String state = runtime.call("inventory.reserve", () -> "RESERVED");
    flow.change("order.status", "CREATED", state);

    String activeSnapshot = runtime.currentToJson();
}
```

Resources close in reverse declaration order, so the stage closes before the runtime. The default configuration has no `FlowSink`; it does not emit logs, write files, publish to queues, or make network requests.

Use a configured synchronous `FlowSink` to receive a frozen completed session. The host application owns masking, timeout, retry, persistence, and data-egress policy.

### Compare Core only

After installing this checkout locally, use the preview artifact only in an isolated source pilot:

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-compare-core</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

Use `CompareRuntime.defaults().engine()` and keep business code typed to `CompareOperations`, as shown above.

Do not include `tfi-compare-core` with `tfi-compare`. Core intentionally omits the complete module's Flow dependency, compatibility facade, SPI integration, tracking adapters, query helpers, and other peripheral APIs.

### Kernel + Compare bridge

`tfi-kernel-compare` accepts a host-selected `CompareOperations` and records a bounded summary plus optional masked detail into the current Kernel `Stage`.

The bridge is for observability. Business decisions must use the returned `CompareResult` directly and must not depend on whether a record fit within the Kernel budget.

The slim Spring starter assembles `KernelRuntime`, Compare Core, and `KernelCompareRecorder`. Business code can inject the runtime, `CompareOperations`, and recorder:

```java
import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.compare.KernelCompareRecorder;

try (Stage flow = kernelRuntime.begin("order.update")) {
    CompareResult result = compare.compare(before, after);
    recorder.record(flow, "order.update", result);

    var outcome = result.getOutcome();
    var completion = result.getCompletion();
}
```

This starter is an internal release candidate. Build it only inside this reactor for evaluation.

Do not add it to a production dependency set until the [KCS-10 release gate](docs/task/tfi-kernel-compare-integration/TASK-KCS-10-consumer-release-and-reactor-gates.md) and owner decision are complete.

Optional AOP additionally requires `spring-boot-starter-aop` and is disabled by default:

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

The target must be non-null. The default record policy writes summaries only, and an AOP record is an in-memory observation, not proof that an enclosing transaction committed.

## Scope and trade-offs

| Dimension | Full aggregate | Selective complete line | Slim composition preview |
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

The slim line optimizes explicit boundaries and bounded behavior, at the cost of compatibility and maturity.

## Recommendations

1. **Existing TFI application:** stay on the complete line. Move from the aggregate to selected modules only when the dependency reduction is worth testing the new composition.
2. **New Spring application:** start with the specific Flow or Compare starter. Add Ops only for a concrete operational requirement.
3. **Pure Java flow recording:** use `tfi-flow-core` for the current complete model. Evaluate `tfi-kernel` only when its smaller explicit model is the actual requirement and RC change is acceptable.
4. **Pure object comparison:** use `tfi-compare` today. Evaluate `tfi-compare-core` only from source and only on a classpath that excludes `tfi-compare`.
5. **Need every complete-line capability:** use `TaskFlowInsight`; its convenience is valuable when most of the aggregate is used.
6. **Need slim Flow + Compare:** run a controlled source pilot. Do not present the bridge or slim starter as a released production option before the final gates pass.

When uncertain, select a module by ownership rather than by artifact count: Flow owns execution structure, Compare owns object truth, Spring starters own container wiring, and Ops owns exposure and storage.

## Configuration and operational boundaries

| Prefix or switch | Owner | Important behavior |
|---|---|---|
| `tfi.annotation.enabled` | Flow Spring Starter | Enables `@TfiTask` AOP; default is off |
| `tfi.context.*` | Flow Spring Starter | Context lifecycle and propagation settings |
| `tfi.security.*` | Flow Spring Starter | Flow-side masking and security settings |
| `tfi.compare.*` | Compare Spring Starter or slim starter | Immutable comparison policy and resource limits |
| `tfi.compare.tracking.enabled` | Compare Spring Starter | Connects Compare to Flow tracking; default is off and Flow Starter is required |
| `tfi.kernel.*` | Slim Spring starter | Kernel enablement and four resource budgets; SPI implementations and sinks are local beans |
| `tfi.kernel-compare.*` | Slim Spring starter | Bridge and optional AOP settings; AOP is off by default |
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
| Slim Spring composition | [Internal status](tfi-kernel-compare-spring-starter/docs/index.md), [migration boundary](tfi-kernel-compare-spring-starter/docs/migration.md) |

## Contributing and license

Keep changes focused and update matching tests and the owning architecture document when behavior changes. Run `./mvnw test` before submitting a pull request.

Include the exact verification command and result in the pull request description.

TaskFlowInsight is licensed under the [Apache License 2.0](LICENSE).
