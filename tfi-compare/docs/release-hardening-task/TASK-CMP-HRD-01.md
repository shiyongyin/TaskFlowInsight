# TASK-CMP-HRD-01：退役 legacy snapshot 并关闭强 Class-key cache

> **定位**：删除 public legacy snapshot 第二执行图，并用两个精确动作关闭 XRT-05 的 ClassLoader retention。
> **deliveryStatus**：`COMPLETE`
> **reviewStatus**：`PASS`
> **依赖**：无
> **后续**：`TASK-CMP-HRD-02`
> **红队来源**：XRT-01、XRT-05、XRT-06

---

## 一、核心（设计时填）

### 背景

canonical `CompareEngine` 已只使用 `RequestLocalSnapshot`，但发布制品仍暴露
`SnapshotProviders -> DirectSnapshotProvider -> ObjectSnapshotDeep/ObjectSnapshot` 第二执行图。该图包含进程级计数、缓存、
运行期 system property 与 silent fallback，且旧 `Map<String,Object>` 无法表达 partial/failure。另有零生产消费者的
`PathNavigator` 和生产可达的 `EntityKeyUtils` 强 Class-key cache，可能保留可卸载 ClassLoader。

本卡删除 12 个无生产 owner 的 legacy 类型；保留 `EntityKeyUtils` 公共行为，只把 metadata cache 换成 `ClassValue`。
不得借机改变 entity key、query、normalization 或 canonical snapshot 算法。

### 目标（DoD）

- [x] 删除 12 个 legacy 顶层类型；本轮源码、`target/classes` 和 process-test-classes 阶段生成的最终 JAR 均无这些 FQN。
- [x] 保留 `RequestLocalSnapshot`、`SnapshotResult`、`SnapshotCaptureContext`、`PathPattern` 和 `PathPatternCompiler`。
- [x] `EntityKeyUtils.collectKeyFields(Class<?>)` 公共签名与字段顺序语义不变，强 `Map<Class<?>,...>` 改为唯一 `ClassValue`。
- [x] breaking manifest 使用固定 ID `CMP-BRK-API-0539..0550`，POM 使用 12 个 exact class exclusion；无 wildcard。
- [x] fixed 3.0 JAR、`.mvn/api-baseline/SHA256SUMS` 与 `current-api-inventory-v3.json` 未修改。
- [x] Compare static-analysis baseline 只通过 owner-scoped、zero-new/zero-increase refresh 吸收本卡删除造成的 finding/POM evidence
  减少；change ledger 与 current resource inventory 同步，禁止 `--write-baseline`。
- [x] Compare 自有 Checkstyle config 固定 4-space/120-char 且无 suppression，进入 static config authority；HRD-02 新 API 可在同一规则下
  0-new，无需再次 refresh。
- [x] 已完成的 C-02 legacy characterization 从 current resource inventory 删除；canonical long-tail 合同继续证明截断事实不能推出相等。
- [x] static-state scanner 从本轮最终 JAR 扫描完整 `tfi-compare`，结果严格等于 6 个 immutable-private exemption 与 7 个已知 debt。
- [x] canonical 并发隔离、deadline/budget、long-tail truth、deterministic ordering、query/entity consumer 和 manifest 合同通过。
- [x] owning-module focused tests、`clean verify` 与 fixed-baseline API compatibility 通过。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| 第二执行图清退 | 高 | 完整删除 owner、selector、fallback 和无法表达 completeness 的旧合同 |
| Class-key retention | 高 | 删除无消费者 navigator；生产 Entity key cache 改为 ClassValue |
| 兼容证据 | 高 | 12 个 public type removal 必须 exact 登记并由消费者测试持有 |
| canonical 回归 | 高 | 删除旧图不能误伤 request-local truth、path grammar 或 entity key 顺序 |
| 其他 static debt | 低 | 只建立 exact 可见性，不在本卡重构不同 owner 的行为 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| legacy snapshot | 删除 11 个类型 | 旧 Map 合同无法表达 typed failure/partial | canonicalize 旧 facade |
| `PathNavigator` | 作为第 12 个类型删除 | 全仓生产零消费者，保留只会留下无界强 Class cache | 新增全局 clear 或继续背债 |
| Entity metadata | `ClassValue<List<Field>>` | 保留 API/顺序并允许 ClassLoader 卸载 | 强 Class-key Map、行为重写 |
| C-02 | 从 current facts 删除 | owner 已完成，legacy JUnit/类型不再是当前事实 | 构造复杂 3.0 isolated runtime |
| static scanner | 候选集合与 exact 两类清单双向相等 | 同时防新增和防债务条目漂移 | 只检查几个字段或泛化“无状态”承诺 |

## 二、执行（设计时填）

### 前置准备

1. 记录任务开始提交，并执行 `(cd .mvn/api-baseline && shasum -a 256 -c SHA256SUMS)`；同时断言
   `SHA256SUMS` 文件摘要为 `3c2badbdb56559c6a1503a92e05e7f643c199c9eea2eb6ea5c702814cc635fa6`，
   Compare 3.0 JAR 摘要为 `f73ae87e7b141dc6ec290b89687ba5eccceebdc0e75135466c1256a378aa3423`。
2. 先新增 absence contract，并固定执行以下 RED；不得读取上轮残留 JAR：
   ```bash
   ./mvnw -pl tfi-flow-core -am -DskipTests install
   ./mvnw -pl tfi-compare clean \
     -Dtest=com.syy.taskflowinsight.compatibility.LegacySnapshotRemovalContractTests \
     test
   ```
3. 对表中 12 个 simple name 执行全仓 `*/src/main/**` 精确引用扫描。删除文件之间的内部引用随文件一并删除；任何位于非删除生产文件
   的引用都必须在本卡文件清单中逐项处理。当前已知外部命中恰有两处：
   `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/list/EntityListStrategy.java` 中的
   `ObjectSnapshotDeep` Javadoc，以及 `tfi-examples/src/main/resources/application.yml` 中的 `ObjectSnapshot` 注释。前者必须改为只描述
   canonical Engine 边界，后者改为 canonical Compare tracking 描述。若扫描出现其他生产命中，立即停止并回到设计评审，禁止只放宽
   absence test。
4. 不修改 `current-api-inventory-v3.json` 或 `.mvn/api-baseline/**`。`current-resource-inventory-v3.json` 的本卡允许 diff 恰为两类：
   删除 C-02 characterization；保持 static asset path 集不变，仅更新 baseline/enforcer 的 SHA 与 `targetTask=CMP-HRD-01`。其他 section、
   role、path 或 entry count 变化均失败。

### 精确删除与兼容 ID

| ID | 必须删除的 FQN |
|---|---|
| `CMP-BRK-API-0539` | `com.syy.taskflowinsight.tracking.snapshot.DirectSnapshotProvider` |
| `CMP-BRK-API-0540` | `com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot` |
| `CMP-BRK-API-0541` | `com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep` |
| `CMP-BRK-API-0542` | `com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig` |
| `CMP-BRK-API-0543` | `com.syy.taskflowinsight.tracking.snapshot.SnapshotFacade` |
| `CMP-BRK-API-0544` | `com.syy.taskflowinsight.tracking.snapshot.SnapshotProvider` |
| `CMP-BRK-API-0545` | `com.syy.taskflowinsight.tracking.snapshot.SnapshotProviders` |
| `CMP-BRK-API-0546` | `com.syy.taskflowinsight.tracking.snapshot.filter.ClassLevelFilterEngine` |
| `CMP-BRK-API-0547` | `com.syy.taskflowinsight.tracking.snapshot.filter.DefaultExclusionEngine` |
| `CMP-BRK-API-0548` | `com.syy.taskflowinsight.tracking.snapshot.filter.FilterDecision` |
| `CMP-BRK-API-0549` | `com.syy.taskflowinsight.tracking.snapshot.filter.FilterReason` |
| `CMP-BRK-API-0550` | `com.syy.taskflowinsight.tracking.ssot.path.PathNavigator` |

每个 entry 固定 `ownerTask=CMP-HRD-01`、
`consumerTest=com.syy.taskflowinsight.compatibility.LegacySnapshotRemovalContractTests#legacyTypesAndSourcesAreAbsent`，
`japicmpExclusion` 为表中完整 FQN。禁止重新使用旧 ID、重排历史 entry 或添加包级 exclusion。

### 文件与职责

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 删除 | 上表 FQN 映射到 `tfi-compare/src/main/java/` 的 12 个生产文件 | 删除第二执行图与零消费者 navigator |
| 修改 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/ssot/key/EntityKeyUtils.java` | `KEY_FIELDS_CACHE` 改为 `ClassValue<List<Field>>`，其余行为不变 |
| 修改 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/list/EntityListStrategy.java` | 删除指向 `ObjectSnapshotDeep` 的过期 Javadoc，只保留 canonical Engine/strategy 职责说明 |
| 修改 | `tfi-examples/src/main/resources/application.yml` | 删除 `ObjectSnapshot` 过期注释，改为 canonical Compare tracking internals 描述；不在本卡改变配置值 |
| 新增测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/compatibility/LegacySnapshotRemovalContractTests.java` | 12 FQN 的 source/classes/JAR/reference exact absence |
| 新增测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/architecture/CompareArtifactStaticStateContractTests.java` | 从本轮最终 JAR 建立 exact static candidate 集合 |
| 新增测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/tracking/ssot/key/EntityKeyClassCacheContractTests.java` | ClassValue 形状、继承顺序、不可修改与重复调用等价 |
| 修改测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/compatibility/CompareBreakingManifest.java` | ownerTask 只解析历史目录和本 hardening 目录；零/重复命中失败 |
| 修改 current facts | `tfi-compare/src/test/resources/compatibility/current-resource-inventory-v3.json`、`tfi-compare/src/test/java/com/syy/taskflowinsight/compatibility/CompareBehaviorCharacterizationTests.java` | 删除 C-02，characterization 固定为 C-05/C-06、size=2 |
| 修改兼容 | `tfi-compare/src/test/resources/compatibility/breaking-changes-v4.json`、`tfi-compare/pom.xml` | 12 个 exact type removal/exclusion |
| 新增质量配置 | `tfi-compare/config/checkstyle/checkstyle.xml` | module-owned 4-space/120-char Checkstyle authority；禁止 suppression |
| 修改质量工具 | `scripts/enforce_static_analysis_baseline.py`、`scripts/tests/test_enforce_static_analysis_baseline.py` | refresh 前强制 no-new/no-increase/after<=before，只更新目标 module evidence/config SHA |
| 审计式刷新 | `.mvn/static-analysis-baseline.json` | `CMP-HRD-01` owner change 记录 Compare 删除后的 finding/evidence；不触碰 fixed API baseline |
| 修改 current facts | `tfi-compare/src/test/resources/compatibility/current-resource-inventory-v3.json`、`tfi-compare/src/test/java/com/syy/taskflowinsight/compatibility/CompareResourceInventory.java` | static asset 新 SHA/owner；owner parser 首次允许 hardening task 唯一命中 |
| 删除测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/tracking/snapshot/{ObjectSnapshotDeepWhiteBoxTests,ObjectSnapshotTests,SnapshotBranchCoverageTests,SnapshotDeepCoverageTests,SnapshotMaxCoverageTests}.java`、`tfi-compare/src/test/java/com/syy/taskflowinsight/tracking/snapshot/filter/SnapshotFilterTests.java` | 删除只验证 legacy 图的测试 |
| 修改测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/tracking/EntityFilterBranchTests.java`、`tfi-compare/src/test/java/com/syy/taskflowinsight/perf/CompareModulePerformanceTests.java` | 只删 legacy suite/benchmark，保留其他合同 |
| 修改消费者 | `tfi-examples/src/test/java/com/syy/taskflowinsight/demo/Demo03Scenario7SortingTest.java`、`tfi-all/src/test/java/com/syy/taskflowinsight/api/TFIArchitectureTest.java` | 迁到 canonical Engine/FieldChange API |
| 修改架构合同 | `tfi-compare/src/test/java/com/syy/taskflowinsight/architecture/{CompareArchitectureContractTests,CompareDependencyBoundaryTests}.java`、`tfi-compare/src/test/java/com/syy/taskflowinsight/tracking/compare/internal/CompareDiffOwnerArchitectureTests.java`、`tfi-compare/src/test/java/com/syy/taskflowinsight/compatibility/CompareSpringRemovalContractTests.java`、`tfi-compare/src/test/java/com/syy/taskflowinsight/tracking/compare/CompareEngineTests.java` | 删除 legacy assertion/comment，保留 canonical owner 断言 |
| 修改文档 | `tfi-compare/docs/design-doc.md`、`docs/MIGRATION_GUIDE_v3_to_v4.md`、`README.md`、`README.zh-CN.md` | 4.0 不提供 public standalone snapshot/path navigator |

实现时不得移动未列出的 canonical 类型。

### 核心步骤

1. `LegacySnapshotRemovalContractTests#legacyTypesAndSourcesAreAbsent()` 精确使用上表 12 个 FQN，断言：
   - `src/main/java` 文件不存在；全仓所有 `*/src/main/**` production source/resource 文本没有 12 个 simple name/FQN 引用；
   - `target/classes` 和 `target/tfi-compare-<version>.jar` 没有 class entry；
   - `PathPattern`、`PathPatternCompiler` 仍存在且被 canonical owner 引用。
   该引用断言必须覆盖 Java/YAML 的注释和字符串，不得只检查 import；因此 `EntityListStrategy` Javadoc 与 examples YAML 注释必须同步
   删除。
2. `EntityKeyUtils` 使用：
   ```java
   private static final ClassValue<List<Field>> KEY_FIELDS = new ClassValue<>() {
       @Override
       protected List<Field> computeValue(Class<?> type) {
           // 原 collectKeyFields 的父类到子类、声明顺序和 @Key 过滤逻辑原样迁入。
       }
   };
   ```
   `collectKeyFields(type)` 只做 null 防御后返回 `KEY_FIELDS.get(type)`；不得改 normalization、escape、
   `REFERENCE_ID_CACHE`、异常语义或公共签名。
3. `EntityKeyClassCacheContractTests` 必须断言：无 static `Map<Class<?>,...>`；恰有一个 `ClassValue`；父类 key 在子类 key 前；
   重复获取内容等价；返回 List 不可修改；现有 Entity/query 输出合同不变。
4. 删除六组 legacy-only snapshot/filter 测试；`EntityFilterBranchTests` 只删 legacy filter suite；
   `CompareModulePerformanceTests` 删除 `ObjectSnapshotDeep` 段，不将其改名伪装为 canonical benchmark。
5. `Demo03Scenario7SortingTest` 固定使用 `CompareRuntime.defaults().engine().compare(...)`，用
   `FieldChange::getFieldPath` 投影路径；typed depth 使用
   `change.after().or(() -> change.before()).orElseThrow().path().segments().size()`。循环 20 次断言路径序列一致且 depth 非递减。
6. `CMP-BRK-BEHAVIOR-0018` 固定指向新增
   `com.syy.taskflowinsight.compatibility.CompareBehaviorCharacterizationTests#longCollectionTailDifferenceRemainsDifferent`；
   `0033/0034` 固定指向扩展后的
   `com.syy.taskflowinsight.api.TrackingOptionsCompatContractTests#shouldMapDepthAndCollectionTokensOneWay`，该方法必须同时断言 SUMMARY 比较成员、IGNORE
   保留 container nullness/kind/size 但排除成员。
7. static scanner 使用 `ClassFileImporter#importJar(new JarFile(findBuiltJar()))`。`tfi-compare/pom.xml` 已在
   `process-test-classes` 绑定主 JAR；所有命令必须带 `clean`。候选为 non-synthetic static field 且满足任一条件：
   - 非 `final`；
   - raw type 是数组；
   - raw type 可赋值给 `Map`、`Collection` 或 `ThreadLocal`；
   - raw type package 为 `java.util.concurrent.atomic`；
   - raw type 是已知内部可变 holder
     `com.syy.taskflowinsight.concurrent.ConcurrentRetryUtil$RetryStats` 或
     `com.syy.taskflowinsight.tracking.format.TfiDateTimeFormatter`。
8. 候选 ID 固定为 `FQN#fieldName`，实际集合必须严格等于以下两组并集；新增项或消失但未删清单项都失败：

   **immutable-private exemption（6）**
   - `com.syy.taskflowinsight.tracking.projection.MaskingPolicy#SAFE_RULES`
   - `com.syy.taskflowinsight.tracking.compare.ValueSnapshot#SCALAR_TYPE_CODES`
   - `com.syy.taskflowinsight.tracking.compare.CompareSemanticFingerprint#BUILT_IN_ALGORITHMS`
   - `com.syy.taskflowinsight.exporter.change.CanonicalProjectionJsonWriter#HEX`
   - `com.syy.taskflowinsight.tracking.compare.FieldChange$ReferenceDetail#JSON_HEX`
   - `com.syy.taskflowinsight.tracking.compare.internal.ValueSnapshotFormatter#HEX`

   **existing exact debt（7）**
   - `com.syy.taskflowinsight.concurrent.ConcurrentRetryUtil#defaultMaxAttempts`
   - `com.syy.taskflowinsight.concurrent.ConcurrentRetryUtil#defaultBaseDelayMs`
   - `com.syy.taskflowinsight.concurrent.ConcurrentRetryUtil#globalStats`
   - `com.syy.taskflowinsight.tracking.summary.CollectionSummary#instance`
   - `com.syy.taskflowinsight.tracking.query.ChangeAdapters#CUSTOMIZERS`
   - `com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils#REFERENCE_ID_CACHE`
   - `com.syy.taskflowinsight.tracking.format.ValueReprFormatter#dateFormatter`

   六个 exemption 还必须逐项断言 modifier 恰含 `private static final`；ID 集相同但可见性或 final 漂移同样失败。合同方法名固定为
   `knownMutableStaticHolderTypesAreExhaustivelyClassified`。不得把 exemption 表述为任意对象深度不可变证明。

### Static baseline 审计式 refresh

1. 在修改 legacy/runtime 源码前，先完成唯一 quality-infra slice：新增 Compare Checkstyle config，POM `configLocation` 精确指向它，
   并扩展 enforcer/tests/build contract。配置要求与 HRD-03 starter config 相同：UTF-8、4-space Indentation、120-char LineLength、
   naming/import/NeedBraces/whitespace/public Javadoc checks，禁止 suppression、source exclusion 或 severity ignore。把该 config path/SHA
   作为待原子登记的第四项，禁止手改 baseline JSON。
2. 强化 `--refresh-baseline`：读取 current Checkstyle/PMD reports 后，先按现有
   baseline `(module,path,rule)->count` 执行 normal zero-new check。任一 new fingerprint、retained count increase 或
   `afterFindingCount > beforeFindingCount` 立即 exit non-zero，且 baseline byte-for-byte 不变。
3. 本卡同时为 static baseline 建立 schema v2：root 必须新增按 `(module,path)` 排序的 `configAuthorities` 与按 module 排序的
   `moduleBootstraps`；HRD-01 初始值分别为一个 Compare config authority 和空数组。config authority schema 固定为
   `module/path/sha256/ownerTask/reason`。带 exact add-config 的本卡 refresh 是唯一 v1 -> v2 migration；默认 verify 在迁移后拒绝 v1、
   缺 root field 或未知 field。它还要求 authority 与 `moduleEvidence.configFiles` 同 path/SHA、当前文件 hash 三方一致。
4. refresh 新增一次性参数
   `--add-config-file tfi-compare/config/checkstyle/checkstyle.xml`。它只可与单 module `--refresh-baseline`、owner `CMP-HRD-01`、非空
   reason 同时使用；调用前该 path 必须不存在于 baseline，现有 Compare config path 集必须恰为三项，文件必须 regular/non-symlink。
   命令在内存中原子增加第四个 configFile + configAuthority，再替换 Compare tool entries/line-level evidence、刷新四个 config SHA，并
   追加 owner change；任一校验失败时 baseline byte-for-byte 不变。其他 module entries/evidence/change 历史必须结构化等价。
   普通 refresh 禁止新增/删除 config path、
   `--write-baseline`、多 module refresh 或空 reason。tests 必须覆盖 new fingerprint、count increase、finding decrease、POM SHA drift、
   duplicate/wrong/symlink add-config、authority mismatch、other-module preservation 与写入失败原子性。
5. quality config、生产删除与 POM exclusions 完成后，先生成 fresh reports，再执行一次且仅一次：
   ```bash
   ./mvnw -pl tfi-compare clean test checkstyle:checkstyle pmd:pmd spotbugs:spotbugs
   python3 scripts/enforce_static_analysis_baseline.py \
     --module tfi-compare --refresh-baseline \
     --add-config-file tfi-compare/config/checkstyle/checkstyle.xml \
     --owner-task CMP-HRD-01 \
     --reason "Remove legacy snapshot graph and refresh decreased Compare evidence"
   ```
   refresh 前的安全检查必须通过；随后更新 current resource inventory 中 baseline/enforcer SHA 与 `targetTask=CMP-HRD-01`。
6. `CompareResourceInventory.requireOwner` 从本卡开始只接受 `ssot-convergence-task` 或 `release-hardening-task` 中 task ID 的唯一命中；
   零命中、重复命中或任意 docs 全局搜索都失败。普通 verify 仍用只读模式校验 refreshed exact evidence。
7. 本节只授权 `.mvn/static-analysis-baseline.json` 的审计式 evidence refresh；`.mvn/api-baseline/**`、
   `current-api-inventory-v3.json` 和 fixed 3.0 checksums 仍绝对只读。

### 验证命令

```bash
python3 -m unittest scripts.tests.test_enforce_static_analysis_baseline

(cd .mvn/api-baseline && shasum -a 256 -c SHA256SUMS)
test "$(shasum -a 256 .mvn/api-baseline/SHA256SUMS | cut -d ' ' -f 1)" = \
  "3c2badbdb56559c6a1503a92e05e7f643c199c9eea2eb6ea5c702814cc635fa6"

./mvnw -pl tfi-compare -am -DskipTests install

./mvnw -pl tfi-compare clean \
  -Dtest=LegacySnapshotRemovalContractTests,CompareArtifactStaticStateContractTests,EntityKeyClassCacheContractTests,CompareBehaviorCharacterizationTests,CompareBreakingChangeManifestTests,CompareManifestCoverageTests,CompareResourceInventoryContractTests,CompareLegacyApiRemovalContractTests,TrackingOptionsCompatContractTests,CompareRequestIsolationTests,SnapshotPathBoundaryTests,MapListComparisonPropertyTests,QueryDeepCoverageTests \
  test

./mvnw -pl tfi-all,tfi-examples -am -DskipTests install
./mvnw -pl tfi-all -Dtest=com.syy.taskflowinsight.api.TFIArchitectureTest test
./mvnw -pl tfi-examples -Dtest=com.syy.taskflowinsight.demo.Demo03Scenario7SortingTest test

./mvnw -pl tfi-compare clean verify
python3 scripts/enforce_static_analysis_baseline.py --module tfi-compare
./mvnw -pl tfi-compare -Papi-compat verify -DskipTests
```

### 审核检查点

- [ ] CP-1：12 个 FQN/simple name 在 production source/resources、classes/JAR 中缺失；两个 PathPattern 类型保留。
- [ ] CP-2：`0539..0550`、POM exclusions 与 12 FQN 双向相等；fixed baseline/API inventory 无 diff。
- [ ] CP-3：C-02 从 current facts 删除，C-05/C-06 可执行；canonical long-tail truth 有直接合同。
- [ ] CP-4：PathNavigator 已删除；Entity key 只改 ClassValue ownership，query/entity 行为无变化。
- [ ] CP-5：scanner 候选严格等于 6+7 exact set，没有 ThreadLocal replacement、新已知 mutable holder 或隐藏 exclusion。
- [ ] CP-6：static refresh 只吸收 finding 减少/POM evidence SHA，owner change 可审计、normal ratchet 通过；fixed API baseline 零 diff。

### 禁止范围与回滚

本卡不改 `RequestLocalSnapshot` 算法、不改 Entity key normalization/query 输出、不重构 XRT-11 热点，也不创建 public snapshot replacement。
回滚必须作为一个 batch 恢复源码、测试、manifest/POM exclusion、inventory 与迁移说明；禁止只恢复 selector/facade 形成残缺第二图。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只删除第二图与零消费者 navigator，并精确替换一个强 Class cache。
- [x] **认知负担**：减少 12 个 public 类型；新增的 ClassValue 与 exact test 直接对应真实 retention 风险。
- [x] **比例失调**：删除、兼容与 truth 回归占主体；其他 static debt 只分类不重构。
- [x] **ROI**：一次主版本清退关闭 XRT-01/XRT-05，不引入 adapter、ThreadLocal 或 cache manager。
- [x] **洁癖检测**：不移动 PathPattern，不整理 Entity/query 行为，不拆 canonical 热点。
- [x] **局部 vs 全局**：All/Examples/manifest/Japicmp/current facts 同卡闭合。
- [x] **过度设计**：ClassValue 替换真实强 Class-key Map，没有为假设场景增加通用 cache SPI。

**结论**：设计通过；12 个 exact deletion + 一个 ClassValue 是关闭 XRT-01/XRT-05 的最小充分边界。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| 跨模块 skip-tests 门禁 | `-DskipTests install` 用于消费者编译 | 初次因 Compare 使用残留 JaCoCo exec 数据检查 0.50 阈值而失败 | 增加 `skipTests=true` 精确激活 profile 及构建合同；默认 verify 仍执行覆盖率 |
| static baseline 普通 refresh | POM 变化后保持 zero-new/no-increase | 156/3615 与指纹均不变，仅 POM SHA 更新 | 使用 owner `CMP-HRD-01` 审计刷新，未重复一次性 add-config 迁移 |

### 检查点结果

- [x] CP-1：12 个 FQN 在 production source/resources、classes 和最终 JAR 均缺失；PathPattern 两类保留。
- [x] CP-2：`0539..0550`、12 个 exact exclusion 与 12 FQN 双向等价；fixed API baseline checksum 通过。
- [x] CP-3：C-02 已删除，current facts 仅 C-05/C-06；long-tail 合同直接证明 `DIFFERENT/COMPLETE`。
- [x] CP-4：PathNavigator 已删除；Entity key 只替换 ClassValue ownership，顺序、紧凑键和查询合同通过。
- [x] CP-5：最终 JAR scanner 候选集严格等于 6+7，无 ThreadLocal replacement 或隐藏 exclusion。
- [x] CP-6：审计 refresh 后 ratchet `3771/3771`，POM 证据 SHA 与 resource inventory 同步，fixed API baseline 未变。

### 需要人类确认

- 无。本卡的删除集、兼容 ID、基线刷新权限和延后技术债均已由任务卡冻结。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | Compare `clean verify` 934/934，All 5/5、Examples 1/1 与 canonical focused 合同通过 |
| 完整性 | 25 /25 | 12 个类型、manifest、POM exclusion、source/classes/JAR 和文档迁移双向闭合 |
| 可维护性 | 25 /25 | 唯一 ClassValue、最终 JAR static exact set 与 module-owned Checkstyle authority 均有可执行合同 |
| 风险控制 | 25 /25 | fixed 3.0 checksum/japicmp、zero-new static refresh、跨模块消费者和默认覆盖率门禁均通过 |

### 最终验证

- static enforcer 单测 21/21；build/resource 合同 22/22；normal ratchet `3771/3771`。
- 8 模块 `-DskipTests install` 全绿；All 架构消费者 5/5；Examples 排序消费者 1/1（内含 20 次循环）。
- Compare `clean verify` 934/934，JaCoCo、SpotBugs 0、Checkstyle/PMD ratchet 通过；`api-compat` 退出 0。

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| 结论 | HRD-01-R0 | 未发现未解决的 MUST/HIGH；XRT-11 是卡片固定的非阻断技术债 | 任务卡全范围 | 审核通过，允许激活 `CMP-HRD-02` |
