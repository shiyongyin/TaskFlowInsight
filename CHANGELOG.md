# Changelog

All notable changes to this project will be documented in this file.

## [3.1.x] - Pending release

### Added
- Reference Change semantics: FieldChange now carries `referenceChange` and `ReferenceDetail` to represent shallow reference switches (including null transitions). Engines and list strategies mark this flag during build.
- Performance gate (verify): Failsafe IT `ReferenceSemanticPerfGateIT` with soft/hard threshold (default 1000ns/op). See `docs/performance/reference-change-baseline.md`.
- ArrayCompareStrategy: Adds container event support for Java arrays (ContainerType.ARRAY), enabling `getContainerChanges()` and `groupByContainerOperation()` on array diffs.
- JMH benchmarks (bench profile) for Query Helper APIs. See docs/performance/JMH-BENCHMARKS.md.

### Changed
- CompareResult: Deprecated `groupByContainerOperationAsString()`; use the typed `groupByContainerOperation()` instead. A migration guide is available at docs/api/QUERY-HELPER-MIGRATION-3.2.0.md.
- Rendering: ChangeAdapters no longer uses heuristics to derive reference_change events; it strictly reads `FieldChange.isReferenceChange()` and `ReferenceDetail`.

### Notes / Breaking Notice
- Rendering adaptors should read `FieldChange.referenceChange` and `ReferenceDetail` for reference changes. Heuristic detection has been removed to avoid false positives.

### Fixed
- Removed duplicate `groupByContainerOperationAsString()` definition that caused compilation failures.

### Planned (3.2.0)
- Remove `groupByContainerOperationAsString()` fully. Please migrate to the typed API before upgrading.
