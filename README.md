# TaskFlowInsight

<div align="center">

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![CI](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml/badge.svg)](https://github.com/shiyongyin/TaskFlowInsight/actions/workflows/tfi-all-ci.yml)

Explain business execution and object changes inside Java 21 applications

[中文](README.zh-CN.md)

</div>

TaskFlowInsight helps Java teams understand what happened inside a business operation, where it failed or slowed down, and what changed in its data. Developers, testers, and operators can work from the same recorded facts.

It is embedded as application components, so local recording and export do not require a separately deployed TFI service. Production persistence, query, and alerting still rely on application infrastructure or an external platform.

The complete TFI line is the default for business applications. Kernel is a separate release-candidate (RC) line whose APIs can still change.

[What it is](#what-this-project-is) · [Problems](#problems-it-solves) · [Scenarios](#application-scenarios) · [Examples](#usage-examples)

[Choosing](#choose-complete-tfi-or-kernel-rc) · [Contents](#what-is-included) · [Maintenance](#maintenance-cost) · [Quick start](#quick-start)

## What this project is

TaskFlowInsight is an open-source component suite embedded in Java 21 applications. It records business execution and explains changes between two object states. It is not a separately deployed service or a standalone operations UI.

Business code marks relevant processes through APIs or annotations and invokes comparison explicitly.

TFI produces separate flow records and object-comparison results: captured steps, status and timing, changed data, and whether a comparison result is complete.

Its purpose is to add business meaning that logs, traces, and APM tools do not express or reconstruct reliably by default. It complements those systems rather than replacing them, and it does not schedule or execute workflows.

The current source version is `4.0.0-SNAPSHOT` and has not been published. New projects should start with the complete TFI line. Kernel is only for a controlled RC pilot with a specific goal, named owners, and a rollback plan.

## Problems it solves

| What the user needs to know | Common difficulty today | What TaskFlowInsight provides |
|---|---|---|
| Which steps did one business request execute? | Logs are scattered across methods and threads and must be reconstructed manually | One record organized by the business steps that were captured |
| Where did work fail or become slow? | An exception or total duration lacks business-stage context | Status, timing, and business messages for each step |
| What changed between two states? | Handwritten field logs miss details and do not explain complex collections | Path-based differences plus completeness and incomplete reasons |
| Did a regression follow the expected branch? | Tests often assert only the final return value | An execution path and structured result that tests can inspect |
| How can development, QA, and operations share one account? | Each role uses a different log vocabulary | Human-readable and machine-readable views of the same facts |

## Application scenarios

| Business scenario | Question to answer | Recommended choice |
|---|---|---|
| Order creation and fulfillment | Did validation, inventory, pricing, payment, and persistence succeed, and where was time spent? | Complete TFI line |
| Approval and rule decisions | Which nodes ran, which branch reasons were recorded, and where was the request rejected? | Complete TFI line |
| Billing, settlement, and reconciliation | Which calculation steps were captured, and which bill or fee items changed? | Complete-line Flow + Compare |
| Inventory and state transitions | Were reserve, release, and recovery captured, and what changed in state? | Complete-line Flow + Compare |
| Configuration and price changes | Which fields or collection items changed, and was the comparison complete? | Selected complete-line Compare |
| Automated regression tests | Does the captured internal path match the test assertions when the final result is correct? | Selected complete-line Flow |
| Shared platform or SDK capture | Only a small fact set is needed, with hard limits on each record's count and size | Controlled Kernel RC pilot |

In Kernel, "bounded" means that each session and individual fact has hard limits on stage count, attribute count, and output size to protect host resources.

Content that exceeds a limit is rejected. Exhausting the session byte budget stops later additions, and the result is marked incomplete. This does not mean that the business flow itself is simpler.

## Usage examples

### Example 1: diagnose an order-submission failure

The team marks validation, inventory, pricing, payment, and persistence as business steps.

If business code marks failures and records their reasons correctly, a failed order leaves a record of the executed steps, failed stage, stage timings, and relevant business messages.

Developers do not have to reconstruct the entire log chain first. QA can check the failure path, and operations can use the captured status, timing, and reason to investigate.

TFI does not classify business rejections automatically or define what counts as slow.

### Example 2: review a price-configuration release

Before publication, compare the old and new price configurations. The result lists changed fields, list entries, and rules, and states whether the comparison completed.

The publisher confirms the actual change while the reviewer decides whether more inspection is needed.

TFI reports observed changes. It does not prove that those changes committed, and it does not make a release or approval decision for the business system.

### Example 3: pilot Kernel in a platform component

A platform already owns ingestion, access control, storage, and retention, and only wants services to submit fixed, size-bounded facts.

The platform team defines record conventions, sinks, masking, monitoring, and rollback before piloting Kernel in a few services.

Without those platform capabilities, the pilot owner must build and maintain the missing pieces.

For a normal business application, the complete line reduces custom work for flow recording, comparison, and export, but it still does not provide hosted persistence, query, or alerting.

## Choose complete TFI or Kernel RC

Choose the complete TFI line for a normal business application.

Evaluate Kernel RC when a platform already owns the downstream loop, or when one real service has a bounded question that existing tools cannot answer and accepts ownership of the missing capabilities.

In this README, "complete TFI" means the complete product line, not a requirement to use the full aggregate. New applications should still select modules first and use the aggregate only when they need most capabilities.

| Decision | Complete TFI line | Kernel RC line |
|---|---|---|
| Product role | Direct flow explanation and object-change capabilities for business applications | A bounded fact-recording foundation for platforms, SDKs, or infrastructure components |
| Primary users | Application developers, QA, operations, and existing TFI users | Platform, infrastructure, or controlled service teams with a bounded pilot question |
| Included value | Flow recording, object comparison, plain Java, and optional Spring and Ops | A bounded recording runtime; Compare and Spring composition remain internal previews, not current pilot entry points |
| Adopter still owns | Data boundaries, sensitive-data policy, and external persistence, query, alerting, authorization, and retention | Sinks, fact conventions, masking, transport, storage, query, monitoring, retention, and rollback |
| Current maturity | Recommended line, but 4.0 is still an unpublished source snapshot | RC; API is unfrozen and real-service value remains unproven |
| Adoption guidance | Default for new applications; select modules first, aggregate only when most capabilities are needed | Controlled pilots only; not the default entry for a business application |

### When the complete line fits

- You need to explain order, approval, billing, inventory, or another business flow directly.
- You need object comparison, Spring annotations, or operational integration without building the recording and comparison foundations yourself.
- You want to start with selected modules and retain a path to add capabilities later.
- Existing code already uses `TFI`, `TfiFlow`, or complete-line Compare.

### When the RC line fits

- A platform or SDK already owns ingestion, storage, access control, monitoring, and retention, or one service has a bounded question that existing tools cannot answer.
- You need only a small, predefined fact set and accept that the result can be incomplete when count or size limits are reached.
- The pilot question, owners, data boundary, disablement switch, and rollback evidence are explicit.
- The team accepts that RC APIs and schemas may still change.

Kernel is neither a lightweight edition of the complete TFI line nor a reduced aggregate tier. If the only goal is fewer dependencies, select complete-line modules instead; narrower responsibility does not imply a lower total cost of use.

## What is included

| Content | What it provides | Current guidance |
|---|---|---|
| Complete TFI line | Business-flow recording, object comparison, plain-Java APIs, optional Spring integration, and optional Ops implementations | Recommended entry for business applications |
| Full aggregate | Every complete-line capability and the unified `TFI` facade through one coordinate | Existing users or applications that need most capabilities |
| Selected complete-line modules | Only the Flow, Compare, Spring, or Ops capabilities actually used | Default recommendation for new applications |
| Kernel Core | Count- and size-limited stages and facts; the host owns the downstream data loop | Controlled platform or bounded-service pilot |
| Kernel + Compare | Maps independent comparison results into bounded records | Internal preview considered only after choosing Kernel |
| Examples, documentation, and quality gates | Runnable examples, module design references, tests, and CI evidence | Learning and maintenance support; not application dependencies |

## Maintenance cost

Maintenance cost is more than dependency count. It includes configuration, testing, security, data governance, upgrades, and cross-team coordination.

Responsibilities outside the Kernel boundary remain with the adopter; they do not disappear.

The ratings below are a qualitative, relative assessment of responsibility among these repository choices. They are not estimates of engineering time, budget, or runtime performance.

Kernel has no completed real-service pilot yet, so its actual total cost remains unproven.

| Choice | Initial adoption | Ongoing maintenance | What the team continues to own |
|---|---|---|---|
| Selected complete-line modules | Medium | Low to medium | Selected capabilities, instrumentation, upgrade regression, and data boundaries |
| Full aggregate | Low | Medium | A wider dependency and auto-configuration surface plus combination regression |
| Kernel Core | High | High; a shared platform can amortize it | Sinks, conventions, security, monitoring, retention, and rollback |
| Kernel + Compare | High | Highest | Kernel ownership plus two models, masking, composition tests, and version risk |

Cognitive cost differs as well. With selected complete-line modules, initial learning centers on the business APIs. Adding Spring, Ops, output, and security still requires understanding those parts of the chain.

Kernel has fewer local concepts but requires a broader understanding of external systems, failure boundaries, and ownership.

A platform team can centralize Kernel conventions and share that cost across services. A single-service pilot carries the full burden locally. Real-service evidence is still required to show that Kernel lowers total organizational cost.

## Quick start

The current version is the unpublished `4.0.0-SNAPSHOT`. Install it from source into the local Maven repository first:

```bash
git clone https://github.com/shiyongyin/TaskFlowInsight.git
cd TaskFlowInsight
./mvnw clean install
```

New applications should select only the capabilities they need. This pure-Java `tfi-flow-core` example creates the smallest useful flow-recording loop:

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-flow-core</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

```java
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.api.TfiFlow;

public final class FlowQuickStart {
    public static void main(String[] args) {
        TfiFlow.startSession("order.submit");
        try {
            try (TaskContext stage = TfiFlow.stage("order.validate")) {
                stage.attribute("requestId", "req-1001")
                        .message("validation completed")
                        .success();
            }

            System.out.println(TfiFlow.exportToJson());
        } finally {
            TfiFlow.endSession();
        }
    }
}
```

Export the flow before `endSession()`. A production integration must also define capture boundaries, sensitive-data rules, and the output destination.

The output has this shape after dynamic IDs, timestamps, and durations are omitted:

```json
{
  "schemaVersion": 2,
  "session": {
    "name": "order.submit",
    "status": "RUNNING"
  },
  "rootTask": {
    "name": "order.submit",
    "children": [
      {
        "name": "order.validate",
        "status": "COMPLETED",
        "attributes": {"requestId": "req-1001"}
      }
    ]
  },
  "truncated": false
}
```

Export occurs before the session ends, so the session is still `RUNNING` while the closed business stage is `COMPLETED`.

To run the Spring Boot example directly:

```bash
./mvnw -pl tfi-examples spring-boot:run
```

After startup, call this endpoint from another terminal:

```bash
curl http://localhost:19090/api/demo/hello/TFI
```

Continue with the [full aggregate](#full-tfi-through-the-aggregate), [Spring Flow](#spring-flow), [object comparison](#compare-only), or the [Kernel RC line](#kernel-rc-line).

## Project status

- **Source version:** `4.0.0-SNAPSHOT`. Version 4.0 has not been published from this repository.
- **Runtime baseline:** Java 21. Spring integrations use Spring Boot 3.5.5.
- **Recommended line:** use the complete product line, either as the aggregate or as selected modules, for current integration work.
- **Preview line:** `tfi-kernel` is RC; the Kernel + Compare composition remains an internal technical preview until its release gates are complete.
- **Distribution:** build and install the snapshot from source before using it in another local project.

No benchmark number, compatibility baseline, or successful module build should be read as proof of a public release. The repository currently has CI and release-candidate gates, but no automated deploy or release workflow.

<details>
<summary><strong>View the complete selection, usage-cost, and cognitive-cost analysis</strong></summary>

## Detailed selection and cost analysis

### The shortest answer

The complete TFI line is the application-facing product family. It has two adoption forms:

- **Full aggregate:** one `TaskFlowInsight` coordinate brings in every complete-line module and the unified `TFI` facade.
- **Selected modules:** adopt only the required Flow, Compare, Spring, or Ops capabilities while staying on the complete line.

**The complete TFI line gives application teams execution insight and object-change capabilities they can use directly. Kernel gives platform, component, or controlled-pilot service teams a bounded recording foundation.**

The complete line targets business applications such as order, approval, billing, and inventory services.

Within recorded flows and stages, it helps developers, testers, and operators answer which steps ran, where work failed or slowed down, and what changed in business objects.

Kernel records only the in-process facts that a host explicitly selects, using a bounded `Session -> Stage -> Record` model and deterministic JSON. The adopter owns masking, transport, storage, query, and presentation.

Kernel is neither a lightweight edition of the complete TFI line nor a reduced tier of the full aggregate. Its smaller scope does not imply simpler adoption or a lower total cost of use.

### Four choices

| Choice | Product meaning | Current guidance |
|---|---|---|
| Full aggregate | Adopt the whole complete line through one coordinate and facade | Use for existing TFI or when most capabilities are needed |
| Selected complete-line modules | Keep complete-line semantics while composing only required capabilities | Default recommendation for new applications |
| Kernel Core | Record bounded flow facts explicitly; the adopter owns the output loop | Controlled pilot with a real question and named owners only |
| Kernel + Compare | Map independent comparison results into bounded flow observations | Internal preview for a specific composition hypothesis only |

### What the user receives

| Product question | Complete TFI line | Kernel |
|---|---|---|
| Primary problem | Explain complex business execution, stage timing, and object changes | Capture a small, controlled, bounded set of facts; the host owns long-term semantics |
| Primary adopter | Business application teams, Spring teams, and existing TFI users | Platform, SDK, and component teams, or service teams with a specific pilot question |
| Recorded information | Session/Task details plus bounded Compare `outcome + completion` | Session/Stage/Record facts; Core does not compare objects |
| Adoption experience | Unified facade, plain-Java API, or explicitly enabled Spring annotations | Explicit `begin/stage/call/record` in Core, with host-owned wrappers if needed |
| Result | Human-readable flows, canonical JSON/Map, object differences, and optional Spring/Ops integration points | Completed Sessions go to synchronous sinks; the host can convert them to deterministic `tfi-flow/1` JSON |
| Missing product loop | No built-in persistent history search, hosted UI, or compliance audit system | No default Sink, object comparison, Ops, storage, search, alerting, or UI |
| Current guidance | Current recommended line, although 4.0 remains an unpublished source snapshot | Controlled RC pilot; product value and the 1.0 API still require real-service evidence |

The complete-line column lists capabilities available in that product family. Selected-module users receive only the capabilities they include.

"Full aggregate" describes its SDK module set. It does not mean every feature turns on automatically or that the aggregate is a hosted monitoring platform.

Spring AOP is off by default. Most Ops endpoints, stores, and performance components require explicit wiring and protection.

### Typical scenarios

| Problem to solve | Recommended choice | Why |
|---|---|---|
| Ordinary logs cannot reconstruct an order, approval, billing, or inventory flow | Complete TFI line | A richer execution tree, status, messages, and timing explain one business operation |
| Confirm what changed in a price, configuration, or order state | Complete TFI line plus Compare | Compare reports differences; Flow adds recorded stage context but does not prove causality |
| Add annotations, comparison, or operational implementations to a new Spring service | Selected complete-line starters and Ops | Preserve complete-line semantics while adding only the capabilities in use |
| Record flows in plain Java without an existing ingestion platform | Complete-line `tfi-flow-core` | Use the current complete Flow model and exporters without building the Kernel output loop |
| Compare objects without recording a flow | Complete-line `tfi-compare` | Use Compare `outcome + completion`, paths, and rendering directly |
| Existing code already uses `TFI`, `TfiFlow`, or complete-line Compare | Stay on the complete line | Kernel has no compatibility layer; migration changes APIs, model, schema, and operations |
| Reduce dependencies while retaining the current TFI experience | Selected complete-line modules | This is the recommended "smaller" setup; it does not change the product boundary |
| Embed fixed, size-bounded execution facts into a platform or SDK with an existing ingestion path | Controlled Kernel Core pilot | Centralize Sink, business Record conventions, security, retention, and runtime policy |
| Test one real-service question that current tools cannot answer | Controlled Kernel Core pilot | Use a controlled, approved output route, or stay outside the production data loop |
| Test bounded flow facts together with object-change summaries | Kernel + Compare internal preview | Evaluation must absorb dual-model, masking, classpath, and composition-test costs |

Do not select Kernel only because it has fewer dependencies, classes, or JAR bytes. If every service invents its own sinks, Record codes, masking, and retention rules, a small local runtime becomes repeated platform engineering.

### Selection outcome

1. Choose the complete TFI line for direct flow explanation, object comparison, or Spring/Ops integration.
2. Choose selected complete-line modules when the goal is only to reduce dependencies. Do not migrate to Kernel for that reason.
3. Evaluate Kernel Core when a platform loop exists, or when one bounded service problem justifies owning the missing capabilities.
4. Evaluate Kernel + Compare only after choosing a Kernel pilot that must add comparison summaries to the same flow facts. Use complete-line `tfi-compare` for ordinary object comparison.
5. When uncertain, start with selected complete-line modules. Kernel is not the default entry for a new application.

### Usage cost

Cost is measured against a real-service pilot that is safe, testable, observable, disableable, and covered by a rollback drill. Adding a Maven dependency or printing sample JSON is not the baseline.

- **Low:** the module owns the main responsibility; the application mostly calls and configures it.
- **Medium:** the application selects modules, composes explicit pieces, or adds focused tests without building a new product subsystem.
- **High:** the adopter must design missing capabilities, own a much wider change surface, or absorb RC change and cross-team approval.

High cost means responsibility has moved; it does not imply low implementation quality. Ratings compare the four choices in this repository. They are not estimates of engineering time, budget, or production performance.

| Cost dimension | Full aggregate | Selected complete line | Kernel Core | Kernel + Compare preview |
|---|---|---|---|---|
| First usable loop | Low: lifecycle/export/close exist; app sets data policy | Medium: choose and test modules | High: add Sink, Record code/data rules, security, pilot rollback | High: also own both cores and bridge |
| Instrumentation | Low-medium: starter vs explicit Core | Low-medium: chosen API decides | Medium: explicit Session/Stage/Record | Medium: separate comparison and records; optional AOP |
| Concept learning | Medium: unified entry, broad system | Medium: fewer domains, module boundaries | Medium: few terms, heavy ownership | High: two cores/bridge; Spring only with starter |
| Dependency governance | High: widest dependency and auto-config surface | Low to medium: depends on selected modules | Medium: narrow runtime, source snapshot, RC | High: exclude complete Compare and verify dependency trees |
| Output, security, operations | Medium: app owns access/retention | Medium: wire selected parts | High: host owns Record code/data rules and output | High: also own masking/budgets |
| Testing | Medium: broad composition | Medium: lock selection | High: success, failure, disablement, truncation, sinks, Runtime close, pilot rollback | High: also keep truth independent from records |
| Upgrade and migration | Low-medium: same line; test versions | Medium: facade/composition changes | High: unfrozen, incompatible API/schema | High: internal; no compatibility layer |
| Organizational coordination | Medium: app/security/operations | Medium: app or platform owner sets modules | High: service/data/Record/output/rollback owners | High: add Compare; Spring only with starter |
| Maturity risk | Medium: recommended line, but 4.0 is not released | Medium: the same complete product line | High: RC with real-service value still unproven | High: internal preview with open release gates |

The four choices distribute cost differently:

- **Full aggregate:** minimizes entry decisions but maximizes dependency and composition scope. Its convenience pays off when the application uses most of the included capabilities.
- **Selected complete line:** requires an upfront boundary and module decision, then limits daily work to chosen capabilities. It is the repository's default recommendation for new applications; actual cost depends on the composition.
- **Kernel Core:** the sample is simple; the production loop is not. An existing platform can share missing capabilities; otherwise a real-service pilot must justify the investment.
- **Kernel + Compare preview:** combines both core models with composition and release risk. A starter/AOP pilot also adds Spring ownership.

Runtime latency, allocation, bandwidth, and storage cost cannot be rated from dependency count or JAR size. Sampling, recorded content, object shape, sinks, and the ingestion path can dominate.

Measure these costs in the target service and report absent evidence as `NOT_MEASURED`.

Kernel uses synchronous sinks, so Sink latency and failure policy may dominate end-to-end runtime cost. Complete-line cost likewise depends on enabled modules, instrumentation scope, exporters, and operational configuration.

### Cognitive cost

Cognitive cost is not the number of public APIs or nouns. It also includes external systems, failure boundaries, and ownership decisions that adopters must understand to complete the product loop.

| Role | Full aggregate | Selected complete line | Kernel Core | Kernel + Compare preview |
|---|---|---|---|---|
| Application developer | Facade plus Flow/Compare semantics | Selected APIs, instrumentation, boundaries | Session/Stage/Record lifecycle and degradation | Keep `CompareResult` truth separate from Records |
| Architect or platform owner | Hidden modules, providers, auto-config | Selection, composition, dependencies | Runtime/thread owners, budgets, Record code/data, sinks | Two cores/bridge/classes; Spring only with starter |
| Security and operations | Capture, masking, endpoints, stores, auth, retention | Selected output surface | Define classification, masking, access, timeout, failure, monitoring, retention | Also govern Compare masking and Record budgets |
| Tester | Broad composition, async context, Compare completion | Lock module composition | Success/failure, disablement, truncation, sinks, Runtime close, pilot rollback | Prove truth and Record admission are independent; optional AOP |

The aggregate has low entry-point load and higher end-to-end load. Selected modules have higher selection load and lower daily load.

Kernel Core has low vocabulary load and high ownership load. Kernel + Compare adds dual-model and composition ownership.

Platform teams can move Kernel cognitive load out of application teams and reuse one standard across consumers.

A single-service pilot puts that load on the service team. Whether either organization lowers total cost still requires real-service evidence; API count cannot answer it.

Kernel has not completed the KNL-03 real-service pilot. Whether it reduces diagnosis, business-understanding, or audit-assistance cost is still a product hypothesis.

Passing tests, a smaller artifact, or a complete design cannot substitute for that evidence.

### What neither line directly solves

Neither line is a workflow engine or a cross-service distributed-tracing backend, historical search platform, or hosted visualization console.

Flow records and object differences are not compliance evidence by themselves. An audit use case still needs transaction-commit correlation, masking, tamper resistance, persistence, access control, and retention.

For turnkey storage, search, alerting, or an operations UI, evaluate a dedicated logging, tracing, APM, audit, or workflow product first. Then decide whether TFI business facts should feed that system.

</details>

<details>
<summary><strong>View module relationships and the complete technical integration reference</strong></summary>

## Names and technical relationship

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

## Choose modules after choosing the product line

The table below selects Maven modules; it does not decide the product line. To reduce dependencies while preserving current TFI semantics, use selected complete-line modules instead of switching to Kernel.

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

## Full TFI through the aggregate

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

## Selective complete-line adoption

Selective adoption preserves current complete-line semantics while avoiding capabilities that the application does not use. This is the recommended "smaller" setup, not Kernel.

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

## Technical scope reference

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

See the earlier [selection outcome](#selection-outcome) for adoption decisions. This technical table confirms module scope; it does not replace use-case, usage-cost, or cognitive-cost analysis.

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

</details>

## Example application

`tfi-examples` is the runnable Spring Boot module. Start it from the repository root:

```bash
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
