# TFI Compare Core Design Boundary

> Status: `IMPLEMENTED`

## Responsibility

The module owns its copied implementation of comparison truth, bounded resource decisions, typed paths,
canonical projection, and canonical JSON, map, Markdown, and console representations. It also owns the narrow
`TrackingBatchProvider` port used by its `TrackingExecutor`.

## Artifact Relationship

`tfi-compare-core` and `tfi-compare` are parallel artifacts. The original Compare module keeps its complete
implementation, tests, resources, SPI integration, and existing consumers. Core does not replace it in-place,
and neither artifact has a Maven dependency on the other.

The artifacts are mutually exclusive application choices. They may contain matching FQCNs because they are not
supported on the same runtime classpath. Building both as sibling modules in the repository reactor is a CI
operation, not a co-deployment model.

## Dependency Boundary

Core runtime code may depend only on the JDK and `slf4j-api`; Lombok is build-time `provided`. It must not depend
on Flow Core, Compare, Kernel, Spring, Micrometer, Caffeine, Jackson, or AspectJ. Jackson is permitted only in the
test scope and is checked by Maven Enforcer.

## Runtime Model

Core is a synchronous, request-scoped, stateless Java library. Construction seams are explicit. Runtime lookup,
fallback graphs, background workers, queues, retries, persistence, and shutdown flushing are excluded.

## Embedded Compare Port

`CompareOperations` is the host-selected embedded execution Port. `CompareEngine` remains its default implementation,
and `CompareRuntime` remains the sole owner that assembles policy, extensions, and the engine. Plain Java bridges and
host frameworks depend on this Port rather than internal implementation types. `CompareOperationsDecorator` remains
limited to the legacy Ops single-decorator contract and is not enabled by the new composition Starter.

The overall architecture and cross-card invariants are defined in
`tfi-compare/docs/compare-core-extraction-task/MASTER-PLAN.md`.
