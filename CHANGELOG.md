# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

---

## [3.0.0] - 2025-10-10

### 🎉 Major Release - Architecture Refactoring

This is a major architectural upgrade introducing unified facade patterns, complete annotation system, and advanced comparison strategies. **542 files changed** across **13 modular commits**.

#### Added

##### 🏗️ **Unified Facade Pattern** (P0-T0)
- **`DiffFacade`**: Unified diff detection entry point with automatic fallback
  - Priority: Programmatic Service → Spring Bean → Static DiffDetector
  - Seamless Spring/non-Spring environment switching
- **`SnapshotProviders`**: Snapshot capture abstraction layer
  - `DirectSnapshotProvider`: Direct implementation (default)
  - `FacadeSnapshotProvider`: Facade-based implementation (opt-in)
  - Selection priority: Spring Bean → System property → Default
- **`ChangeTracker`**: Integrated with DiffFacade and SnapshotProviders

##### 📝 **Complete Annotation System** (P0-T1)
- **Type System Annotations**:
  - `@Entity`: Mark objects with unique identifiers (used for list matching)
  - `@ValueObject`: Mark value-based objects (compared by content)
  - `@Key`: Mark entity's unique identifier field
- **Comparison Annotations**:
  - `@NumericPrecision(scale, tolerance)`: Control numeric comparison precision
  - `@DateFormat(pattern)`: Date formatting output
  - `@CustomComparator(class)`: Field-level custom comparators
- **Filter Annotations**:
  - `@DiffIgnore` / `@DiffInclude`: Field-level inclusion/exclusion control
  - `@ShallowReference`: Shallow reference marking (compare ID only)
  - `@IgnoreDeclaredProperties` / `@IgnoreInheritedProperties`: Class-level filtering

##### 🔍 **Advanced Comparison Strategies** (P1-T3, P1-T4)
- **`EntityListStrategy`**: Entity matching based on `@Key` annotation + move detection
  - Output format: `items[0→2]: MOVED`, `items[+3]: ADDED`, `items[-1]: REMOVED`
- **`NumericCompareStrategy`**: Numeric precision comparison (BigDecimal/Float/Double)
  - Supports field-level `@NumericPrecision` annotation
  - Configurable tolerance: `tfi.compare.numeric.float-tolerance: 1e-12`
- **`EnhancedDateCompareStrategy`**: Timezone-aware date comparison
  - Format control: `tfi.compare.datetime.default-format: "yyyy-MM-dd HH:mm:ss"`
  - Tolerance: `tfi.compare.datetime.tolerance-ms: 0`
- **`MapCompareStrategy`**: Deep Map comparison with nested object support
- **`SetCompareStrategy`**: Set element change detection
- **`ArrayCompareStrategy`**: Array element comparison with container events

##### 🎨 **TFI API Extensions** (P0-T2)
- **Facade Methods**:
  - `TFI.compare(oldObj, newObj)`: Zero-config comparison
  - `TFI.render(result, style)`: Markdown rendering (simple/standard/detailed)
  - `TFI.comparator()`: Fluent builder for advanced configuration
- **`ComparatorBuilder`**: Chainable configuration builder
  - `withMaxDepth(int)`, `ignoring(String...)`, `withTemplate(Template)`
- **`ComparisonTemplate`**: Predefined comparison templates
  - `AUDIT`: Audit-focused (all fields, max depth)
  - `DEBUG`: Debug-focused (detailed output)
  - `PERFORMANCE`: Performance-focused (shallow comparison)
- **`TfiListDiff`**: List-specific diff facade with convenience methods

##### 🛤️ **Path System** (P1-T6)
- **`PathDeduplicator`**: Redundant path elimination
  - Problem: `order.items` (UPDATED) + `order.items[0].status` (UPDATED)
  - Solution: Keep only `order.items[0].status` (fine-grained path)
  - Fast path (<800 changes): ~1ms, Full dedup (>800): ~10ms
- **`PathArbiter`**: Deterministic path selection with priority calculation
- **`PathBuilder`**: Path construction utilities
- **`PathCollector`**: Path collection and filtering

##### 🎭 **Rendering & Export** (P1-T7)
- **`MarkdownRenderer`**: Markdown diff report generator
  - Styles: `simple` (summary only), `standard` (recommended), `detailed` (full)
- **`MaskRuleMatcher`**: Sensitive information masking
  - Default patterns: `password`, `secret`, `token`, `internal*`
- **`ChangeReportRenderer`**: Structured change report

##### 📊 **Monitoring & Degradation** (P2-T7)
- **`DegradationManager`**: Adaptive degradation system (opt-in, default disabled)
  - Levels: NORMAL → SKIP_DEEP → SIMPLE → SUMMARY → DISABLED
  - Triggers: Memory >80%, CPU >70%, Slow operations >30%
  - Hysteresis: `min-level-change-duration: 30s` prevents flapping
- **`PerfGuard`**: Timeout protection (default: 5000ms)
- **`ResourceMonitor`**: System resource monitoring

##### ⚙️ **Configuration System** (VIP-003)
- **`ConfigurationResolver`**: Five-layer priority resolution
  - Priority: Method annotation → System property → Environment variable → YAML → Defaults
- **`ConfigMigrationMapper`**: Auto-migration of legacy config keys
  - `taskflow.*` → `tfi.*` automatic mapping
- **`TfiFeatureFlags`**: Feature toggle management
  - `tfi.api.facade.enabled: true` (controls facade API)
- **New Configuration Categories**:
  - Cache: `tfi.change-tracking.diff.cache.strategy.*` (10000 entries, 5min TTL)
  - Performance: `tfi.change-tracking.diff.perf.timeout-ms: 5000`
  - Numeric: `tfi.compare.numeric.*` (tolerance, comparison mode)
  - DateTime: `tfi.compare.datetime.*` (format, timezone, tolerance)
  - Monitoring: `tfi.compare.monitoring.slow-operation-ms: 200`

##### 🧪 **Comprehensive Testing** (350+ Test Classes)
- **Unit Tests**:
  - `AnnotationTests`, `CustomComparatorAnnotationTests`
  - `NumericCompareStrategyTest`, `EnhancedDateCompareStrategyTest`
  - `PathDeduplicatorTest`, `PathArbiterTest`
- **Integration Tests**:
  - `DiffFacadeIntegrationTest` (9 scenarios)
  - `SnapshotProviderSwitchTest` (12 scenarios)
  - `EntityValueObjectEndToEndTests`
  - `PathSyntaxEndToEndTest`
  - `ContainerEventsGoldenIntegrationTests`
- **Performance Tests**:
  - `ConcurrencyBenchmarkTest` (100 threads)
  - `PathSyntaxPerformanceTest`
  - `TypeSystemPerformanceTests`
  - `EntityListStrategyPerformanceTest`
- **JMH Benchmarks**:
  - `FilterBenchmarks`, `QueryApiBenchmarks`, `ReferenceChangeBenchmarks`

##### 📚 **Documentation System**
- **Top-level Docs**:
  - `README.md`: Project overview (reflects v3.0.0 features)
  - `QUICKSTART.md`: 3-minute quick start
  - `EXAMPLES.md`: 11 real-world scenarios
  - `FAQ.md`: Common questions
  - `TROUBLESHOOTING.md`: Problem diagnostics
  - `CHANGELOG.md`: Complete changelog
- **Internal Docs**:
  - `tracking/docs/INDEX.md`: Documentation index
  - `tracking/docs/QuickStart.md`: Quick start guide
  - `tracking/docs/Configuration.md`: Configuration guide
  - `tracking/docs/Performance-BestPractices.md`: Performance optimization
- **Demo Examples**:
  - `Demo01_BasicTypes` through `Demo07_MapCollectionEntities`
  - `ChangeTrackingComprehensiveDemo`, `ChangeTrackingBestPracticeDemo`

#### Changed

##### 🔧 **Core Components**
- **`ChangeTracker`**: Integrated with SnapshotProviders and DiffFacade
  - Lines 120-121: `SnapshotProviders.get().captureBaseline()`
  - Lines 244-256: `DiffFacade.diff(name, baseline, current)`
- **`DiffDetector`**: Reduced cyclomatic complexity
  - Extracted methods: `buildStandardChangeRecord()`, `applySortingIfNeeded()`, `applyDedupIfNeeded()`
  - Unified key union: `unionSortedKeys()`
  - Detailed change detection: `containsDetailedChanges()`
- **`CompareService`**: Extensible strategy system
  - Supports custom strategies via `CompareStrategy` interface
  - Auto-routing based on object types

##### ⚙️ **Configuration**
- **Configuration hierarchy**: 5-level priority resolution
- **Legacy key migration**: `taskflow.*` → `tfi.*` automatic mapping
- **Default values**: Consistent across all configuration methods
- **New config categories**: 178 configuration items added

#### Fixed

##### 🐛 **Bug Fixes**
- **`DiffDetectorService`**: Missing `valueKind` and `valueType` fields
  - Issue: `EnumChangeTrackingTest` failed
  - Root cause: Lines 149-158 only set oldValue/newValue
  - Fix: Added `getValueKind()` method and field population (lines 158-163, 390-417)
  - Verification: `valueKind` correctly set to "ENUM"

#### Backward Compatibility

##### ✅ **Zero Breaking Changes**
- **API Compatibility**:
  - `TFI.track()` method signature unchanged
  - `TFI.getChanges()` behavior consistent
  - All existing tests pass
- **Configuration Compatibility**:
  - Legacy config keys auto-migrate (`taskflow.*` → `tfi.*`)
  - Default values remain consistent
  - New config items are optional
- **Behavior Compatibility**:
  - Default: `DirectSnapshotProvider` + static `DiffDetector`
  - Facade pattern auto-fallback (transparent to users)
  - Degradation system default disabled

#### Performance

##### 📈 **Benchmarks**
| Operation | Time | Memory | Notes |
|-----------|------|--------|-------|
| Shallow snapshot | ~10ms | +2MB | Scalar fields only |
| Deep snapshot (depth=10) | ~50ms | +10MB | Nested objects + collections |
| Simple object comparison | ~5ms | +1MB | <10 fields |
| List comparison (1000 items) | ~100ms | +20MB | Entity matching |
| Path deduplication (<800) | ~1ms | +0.5MB | Fast path |
| Path deduplication (>800) | ~10ms | +2MB | Full dedup |

**Overall Impact**: Memory +5%, CPU +3% (caching overhead)

#### Deployment Recommendations

##### Production Environment
```yaml
tfi:
  change-tracking:
    snapshot:
      max-depth: 5  # Recommended (default 10 may be too deep)
    diff:
      cache:
        strategy.max-size: 10000
    degradation:
      enabled: false  # Unless high-concurrency scenario
```

##### High-Concurrency (>100 QPS)
```yaml
tfi:
  change-tracking:
    degradation:
      enabled: true
      memory-threshold: 0.75  # 75% triggers degradation
      cpu-threshold: 0.65
```

#### Migration Guide

##### For Existing Users
**No action required** - v3.0.0 is fully backward compatible:
- All existing APIs work unchanged
- Legacy configuration keys auto-migrate
- Default behavior preserved

##### For Advanced Users
Opt-in to new features:
```java
// Option 1: Switch snapshot provider via system property
System.setProperty("tfi.change-tracking.snapshot.provider", "facade");

// Option 2: Use new facade API
CompareResult result = TFI.compare(oldObj, newObj);
String markdown = TFI.render(result, "standard");

// Option 3: Use fluent builder
CompareResult result = TFI.comparator()
    .withMaxDepth(5)
    .ignoring("id", "createTime")
    .compare(obj1, obj2);
```

#### Related Issues & PRs
- PR #4: Feature/major refactoring (merged 2025-10-10)
- Commits: `0f67180` through `a8f8ec2` (13 modular commits)
- Files changed: 542
- Test coverage: >85%

#### Contributors
- @shiyongyin (architecture design, implementation, testing, documentation)

---

**Note**: This release represents 2 months of intensive refactoring work, establishing a solid foundation for future enhancements in v3.1.0 and beyond.
