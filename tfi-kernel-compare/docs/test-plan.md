# TFI Kernel Compare Bridge Test Plan

> Status: `KCS-05 PASS / INTERNAL UNTIL RELEASE GATE`

## 1. Test Ownership

KCS-04/05 own the complete pure Java bridge closure. Tests live entirely under `tfi-kernel-compare/src/test` and use only
the two Core artifacts plus JUnit 5 and AssertJ. They do not depend on `tfi-compare`, Spring, Jackson, or old shell
fixtures.

| Test | Contract |
|---|---|
| `KernelCompareRecorderInputContractTest` | null names, operation grammar, policy bounds, capacity short-circuit, exactly-once Compare, result invariants |
| `KernelCompareRecorderSummaryContractTest` | ordered schema, sorted wire-code counts, optional pairing, truth matrix, data non-leakage |
| `KernelCompareSummaryBudgetContractTest` | worst legal summary is accepted by a real Kernel with the default 2 KiB Record limit |
| `KernelCompareArchitectureContractTest` | exactly four public types, exact signatures/components/statuses, immutable and framework-free main sources |
| `KernelCompareDetailContractTest` | zero/one projection calls, six-kind conversion, canonical ordering, integration and Kernel rejection prefixes |
| `KernelCompareFailureContractTest` | FAILED summary degradation, unchanged Core truth, non-fatal/fatal Error boundary, no Throwable leakage |
| `KernelCompareSecurityContractTest` | include-sensitive rejection, final Kernel JSON canaries, Summary/Change schema golden |

The Compare Port Javadoc change is independently owned by
`tfi-compare-core/src/test/java/com/syy/taskflowinsight/api/CompareOperationsContractTest.java`.

## 2. Worst-shape Budget Fixture

The budget contract intentionally combines the largest bounded encodings that can coexist on the summary path:

- 128-character operation;
- 1,000 retained changes, represented only by their count;
- 256 combined problem/limitation facts covering every current wire code;
- 128 applied algorithms, with 128-character root and similarity IDs;
- full sha256-v1 fingerprint;
- `Long.MAX_VALUE` diagnostic counters and `Double.MIN_VALUE` similarity;
- a real `KernelRuntime` configured with the default 12 KiB Session and 2 KiB Record limits.

The test requires `RECORDED_SUMMARY`, one frozen `KCOMPARE_SUMMARY_V1` Record, and no truncation reason. It does not
estimate JSON with a second encoder; successful Kernel admission is the owning budget proof.

## 3. Detail, Failure, and Security Fixtures

Detail tests verify the factory invocation count without adding a production test seam: a test-only list counts the real
`CompareProjectionFactory` source traversal. The output must preserve canonical projection order, stop at the first rejected
change, and distinguish integration truncation from full acceptance.

Failure tests make the real factory encounter a test-only failing change list. Ordinary RuntimeException and non-fatal Error
must produce `detailState=FAILED`; VirtualMachineError, ThreadDeath, and LinkageError must propagate by identity. The failure
message, cause, and stack markers are checked against final `Tfi.toJson(FlowSession)` output.

Security tests use password, token, dynamic map key, entity key, and raw value canaries. Assertions are made on final Kernel
JSON, not only the intermediate map. Golden resources freeze the post-Kernel canonical field order of
`KCOMPARE_SUMMARY_V1` and `KCOMPARE_CHANGE_V1`.

## 4. Verification Commands

Compare Port contract:

```bash
./mvnw -pl tfi-compare-core -Dtest=CompareOperationsContractTest test
```

KCS-04 focused bridge closure:

```bash
./mvnw -pl tfi-kernel-compare -am \
  -Dtest=KernelCompareRecorderSummaryContractTest,KernelCompareSummaryBudgetContractTest,KernelCompareRecorderInputContractTest,KernelCompareArchitectureContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

KCS-05 focused bridge closure:

```bash
./mvnw -pl tfi-kernel-compare -am \
  -Dtest=KernelCompareDetailContractTest,KernelCompareFailureContractTest,KernelCompareSecurityContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Module and upstream quality gates:

```bash
./mvnw -pl tfi-kernel-compare -am clean verify
```

Because the focused command permits upstream modules to have no matching specified test, acceptance additionally
requires all seven target-module Surefire XML files:

```text
TEST-com.syy.tfi.kernel.compare.KernelCompareRecorderSummaryContractTest.xml
TEST-com.syy.tfi.kernel.compare.KernelCompareSummaryBudgetContractTest.xml
TEST-com.syy.tfi.kernel.compare.KernelCompareRecorderInputContractTest.xml
TEST-com.syy.tfi.kernel.compare.KernelCompareArchitectureContractTest.xml
TEST-com.syy.tfi.kernel.compare.KernelCompareDetailContractTest.xml
TEST-com.syy.tfi.kernel.compare.KernelCompareFailureContractTest.xml
TEST-com.syy.tfi.kernel.compare.KernelCompareSecurityContractTest.xml
```

The two upstream Core artifacts are also verified independently after the Bridge reactor gate:

```bash
./mvnw -pl tfi-kernel clean verify
./mvnw -pl tfi-compare-core clean verify
```

## 5. Quality Gates

The module POM owns Maven Enforcer dependency bans, JaCoCo reporting/checks inherited from the reactor baseline,
blocking high-priority SpotBugs, and module-owned Checkstyle. PMD findings remain non-blocking according to the
owning parent POM and must be reported rather than reclassified as a repository-wide zero-finding gate. Passing the Bridge
gate authorizes handoff to KCS-06, not publication; release eligibility remains owned by the downstream integration and
KCS-10 gates.
