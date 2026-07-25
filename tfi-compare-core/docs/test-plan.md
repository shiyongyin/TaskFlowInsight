# TFI Compare Core Test Plan

> Status: `MODULE_GATE_PASS / REACTOR_ACCEPTED`

Core owns an independent test closure. Its tests, support classes, and golden resources live entirely under
`tfi-compare-core/src/test`; they do not use `tfi-compare` as a test dependency.

The suite covers comparison truth, boundary handling, resource budgets, typed paths, container events,
projection schemas, canonical output, Tracking contracts, and failure behavior. The current verified baseline is
457 tests with zero failures or errors, 86.71% instruction coverage, and 70.47% branch coverage.

The module quality gate requires instruction coverage 0.80 and branch coverage 0.70, blocking high-priority
SpotBugs findings, module-owned Checkstyle, PMD reporting according to the owning POM, and Maven Enforcer
dependency checks. Thresholds and exclusions must not be weakened to make the copied module pass.

Module verification:

```bash
./mvnw -pl tfi-compare-core clean verify
```

Repository acceptance additionally verifies the original Compare module, both runtime dependency trees, the
absence of an in-repo dual-artifact consumer, and the full Maven reactor. A transitive consumer scenario through
`tfi-compare` does not apply because the original artifact does not depend on Core.
