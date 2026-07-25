# TASK-CMP-HRD-08：建立供应链安全与可信 provenance authority

> **定位**：把 SBOM/license、漏洞、secret/canary、toolchain 和签名 provenance 变成 raw-byte 可复算证据。
> **deliveryStatus**：`COMPLETE`
> **reviewStatus**：`PASS`
> **依赖**：`TASK-CMP-HRD-07` review PASS
> **后续**：`TASK-CMP-HRD-05` 最小最终集成验证
> **红队来源**：生产 MUST-4..7、HIGH-3，以及生产发布提示词 supply-chain/security gates

---

## 一、核心

### 目标（DoD）

- [x] 32-line production policy、五行 authorities 及全部引用 schema 由单一 JDK parser fail closed；未知/重复/缺字段失败。
- [x] scanner/generator/build toolchain 均绑定 expected bundle SHA、actual loaded bytes 和 raw process measurement；禁止只记录版本名。
- [x] exact runtime/bundled closure 生成唯一 CycloneDX 1.6 或 SPDX 2.3 SBOM，raw/components/summary 双向一致。
- [x] 每个 component 的 declared SPDX、actual LICENSE text SHA、NOTICE 和 bundled containing binary 命中 policy ALLOW row。
- [x] vulnerability raw JSON、signed database snapshot、suppression authority、normalized rows 和 summary 可重新派生；HIGH/CRITICAL/
  policy violation/analysis error 均阻断。
- [x] secret first/self scan 与 77-row canary x sink receipt/raw result 完整；finding/error 或未注入 canary 阻断且不上传敏感 raw output。
- [x] wrapper distribution SHA 与 workflow action full commit SHA 固定；mutable tag 和未校验下载由合同拒绝。
- [x] artifact provenance、secret process attestation、final evidence attestation 使用三层无环 Sigstore v0.3 profile并可离线验证。
- [x] 本卡不创建生产 trust root、签名私钥、发布凭据或 suppression；这些只能由 release owner 的外部 policy 提供。

### 固定边界

1. 使用 policy 指定的成熟 scanner/generator；仓库只实现 adapter、closure、normalizer 和 verifier，不实现 CVE/secret/SPDX 引擎。
2. production tool 不可用、数据库过期、签名材料缺失或网络不可达都 fail closed；测试 fake tool 不能进入 release evidence。
3. Sigstore signing operation 不进入被签 command ledger；artifact -> secret process -> final evidence 顺序不可交换。
4. failure staging 只做逻辑 INVALID/禁止消费，不移动或改写已签 bundle；secret finding 的 raw staging 不上传。

## 二、执行

### 文件与职责

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 新增 | `scripts/release-evidence/ReleaseEvidenceVerifier.java` | policy/authority/raw evidence/Sigstore/summary 单一 JDK verifier |
| 新增 | `scripts/collect_tfi_compare_supply_chain_evidence.sh` | 按 releaseExecutionPolicy 执行 external tools并冻结 raw evidence |
| 新增 fixture | `scripts/release-evidence/fixtures/production-policy/` | 无 secret 的完整 PASS/negative policy 与 test trust material |
| 新增测试 | `ReleasePolicyParserContractTests.java` | 32/5 行、path/TOCTOU/XXE/size/schema 负例 |
| 新增测试 | `SupplyChainEvidenceContractTests.java` | SBOM/license/CVE/tool closure/raw-summary full outer join |
| 新增测试 | `SecretFinalizeContractTests.java` | first/self scope、path metadata、closed finalize schema、hash 无环 |
| 新增集成测试 | `tfi-all/src/test/java/com/syy/taskflowinsight/api/SensitiveLogCanaryIntegrationTests.java` | 77 receipt、11 sink、零明文 |
| 修改 | `.mvn/wrapper/maven-wrapper.properties` | 官方 Maven distribution SHA-256 |
| 修改 | `.github/workflows/tfi-compare-ci.yml`、`.github/workflows/perf-gate.yml` | 所有 action full SHA、toolchain/provenance job 输入 |
| 修改 | `tfi-compare/src/main/resources/owasp-suppressions.xml` | 删除 accepted-risk/regex 示例，只允许 policy-generated exact suppression |
| 修改合同 | `CompareBuildConfigurationContractTests.java` | wrapper/action/tool/provenance pinning |
| 修改 SSOT | `tfi-compare/docs/design-doc.md` | supply-chain evidence authority 与 fail-closed 顺序 |

### 精确实现

1. verifier subcommand 固定为 `verify-policy`、`verify-supply-chain`、`verify-integrity`、`verify-all`；共用 bounded TSV/JSON/XML/path/hash
   parser，不按阶段建立 handler/plugin registry。DOCTYPE、external entity、symlink、absolute/`..`/backslash path、超限文件/字段全部失败。
2. policy parser 逐 byte 封存外部 policy/authority；build toolchain、release executions、tool executions 与 raw measurements 按生产提示词
   full outer join。argv 不得从 shell 字符串二次解释，command spec 使用 ordinal argv TSV。
   collector CLI 只允许 `collect <evidence-dir> <policy>`、`secret-finalize <evidence-dir> <policy>` 和
   `attest-final <evidence-dir> <policy>`；前两者进入 execution closure，attest-final 只生成 final signed attestation/receipt，明确不进入
   被签 command ledger。unknown mode/参数/path 全部非零退出。
   release evidence job 必须在 workflow input 指定的 `repository@sha256:<64-hex>` OCI container 中运行，input 与 policy runner image
   逐 byte 相等；tag/默认 hosted image 不可用于 release evidence。candidate build 使用 Maven `-X` retained log，并在 Maven 进程存活时
   读取 Linux `/proc/<pid>/exe` 与 `/proc/<pid>/maps`、JVM CodeSource、Maven ClassRealm URLs；verifier 对实际路径 bytes 复算 SHA。
   任何 measurement 竞争丢失、非 Linux runner、未列 plugin/dependency 或只复制 expected manifest 都阻断。
3. SBOM 只能由 policy tool对 HRD-07 exact runtime closure生成；JSON 使用 RFC 8785 canonical bytes。normalizer 保留 raw component identity、
   purl、declared expression、scope、artifact/containing binary SHA，生成 components/summary。
4. license verifier 直接打开 retained JAR/BINARY 读取 LICENSE/NOTICE；ALLOW 必须同时匹配 declared/detected SPDX 和 policy-pinned
   licenseTextSha256。BUNDLED 必须通过 nested archive 或 exact entry-copy manifest证明实际分发。
5. vulnerability DB manifest/signature/source/sequence/freshness 与四行 scan-inputs 对账；normalized classification/errorCode/count 映射使用
   production prompt 闭集。空或自签 database、宽泛 suppression、过期 suppression 均失败。
6. secret runner 先扫描 candidate/evidence bytes、scope.tsv 和路径字符串，再自扫 report/normalized/scope；之后只允许 fixed path、
   policy ID 和 closed grammar metadata。临时明文目录权限 0700，finally 验证删除。
7. canary harness 为七种 canary x 十一种 sink 生成 77 个高熵值；pre-redaction hook 只保留 SHA receipt。raw scanner result 必须绑定
   coverage/scope/canary set，0 findings 但 receipt 缺失仍失败。
8. workflow action pinning 合同解析 YAML `uses:`，拒绝 `@v*`/branch/tag，只接受 40-hex commit；wrapper SHA 必须从官方 distribution bytes
   计算并由 Maven wrapper实际验证。禁止在任务卡中臆造 digest。
9. test Sigstore fixtures使用仓库内专用非生产 key/cert并标记 TEST_ONLY；production verifier 只信 policy trust material。三份 predicate、
   subject、Rekor proof/checkpoint、issuer/SAN 和 signature profile严格按 release prompt验证，未知 JSON key 失败。

### 验证命令

```bash
./mvnw -pl tfi-compare clean \
  -Dtest=ReleasePolicyParserContractTests,SupplyChainEvidenceContractTests,SecretFinalizeContractTests,CompareBuildConfigurationContractTests \
  test

./mvnw -pl tfi-all \
  -Dtest=com.syy.taskflowinsight.api.SensitiveLogCanaryIntegrationTests \
  test

java scripts/release-evidence/ReleaseEvidenceVerifier.java verify-policy \
  scripts/release-evidence/fixtures/production-policy/policy.tsv

./mvnw -pl tfi-compare,tfi-all -am verify
```

### 审核检查点

- [x] CP-1：policy parser 返回 `POLICY_OK`；unknown/duplicate/path/TOCTOU/XXE 负例均 fail closed。
- [x] CP-2：SupplyChain 合同 `17/17`，SBOM/license/runtime inventory 与 bundled bytes 双向闭合。
- [x] CP-3：database、suppression、tool loaded bytes、raw measurement 与 summary 可从 retained bytes 复算。
- [x] CP-4：真实 77-row harness 和 11 sink 集成合同 `2/2`；重复 canaryId 与未引用 sidecar 均 fail closed。
- [x] CP-5：Artifact/Sigstore 合同 `8/8 + 8/8`；三层 statement、message signature 与时间窗均离线验证。
- [x] CP-6：七模块 Reactor `verify` exit 0；仓库未引入生产 secret、私钥、凭据或发布动作。

### 禁止范围与回滚

本卡不得手写 scanner 判定、联网补报告、提交 production key/token、接受 risk 或为 PASS 生成空数据库。回滚必须恢复 verifier、adapter、
workflow/wrapper pin、fixtures 和合同；不得留下 completion audit 会误认的半套 schema。

## 三、自省

### 需要人类确认

- [ ] **PGP verifier authority**：固定 32-line policy 只封存 signer public key，没有封存成熟 OpenPGP verifier 的
  executable/dependency bytes、argv 和 raw measurement。请选择是否扩展 policy/toolchain/execution schema；确认前
  `PGP`/`PGP,SIGSTORE` 保持 fail closed，禁止手写 OpenPGP 引擎或使用 ambient `gpgv`。
- [ ] **`attest-final` signer authority**：当前 policy 没有位于 signed command ledger 之外的 signer command spec/tool closure。
  请选择增加独立 signer authority，或把该 mode 定义为只验证 trusted builder 已生成的 final bundle；确认前该 mode 保持 fail closed，
  禁止使用 ambient `cosign`、生产私钥或 OIDC 凭据。

- [x] 复用成熟工具，仓库只持有可验证 adapter 与证据 closure。
- [x] 三层 attestation 只解决真实信任边界，不进入 runtime middleware。
- [x] 外部 authority 缺失时 NO_GO，不由实施 AI 代替 release owner。

## 四、反馈

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| toolchain raw measurement | 绑定 expected 与 actual bytes | 增加 `/proc/maps`、JVM/ClassRealm URI 与 OCI RepoDigest 复算 | 只记录版本或 expected manifest 不能证明真实执行工具 |
| canary 真实性 | 77 receipt 与 11 sink 闭集 | 由 77 个 injection driver 真实驱动 Compare projection、masker 与 FIFO store | fixture 直接生成 PASS 行不能证明生产脱敏路径 |
| attestation 顺序 | artifact -> secret process -> final | publishable bytes 先进入 first secret scan，再生成三层无环 attestation | 提前生成签名会形成漏扫或 hash cycle |
| secret post-scope | 允许签名 sidecar 后置生成 | 仅允许 publishable manifest 精确引用的 `SIGNATURE` 路径 | 后缀通配会让未引用 sidecar 携带未扫描 bytes |
| KISS 审查 | 单一 JDK verifier | 5,423 行文件按八个领域内部隔离，登记非阻断 SHOULD，不在本卡拆分 | source-file mode 与单 parser/hash authority 是明确约束，当前拆文件会增加证据漂移面 |

## 五、总结

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | clean DoD `44/44`；Artifact/Sigstore `16/16`；policy `POLICY_OK` |
| 完整性 | 25 /25 | SupplyChain `17/17`；77-row/11-sink canary `2/2`；raw-normalized-summary 闭集 |
| 可维护性 | 23 /25 | 单 parser/hash/path authority且按领域内部隔离；超大 source-file verifier 保留为触发式 SHOULD |
| 风险控制 | 25 /25 | 精确 sidecar scope、NOFOLLOW、bounded parser、外部 authority 缺失 fail closed；七模块 verify exit 0 |

**总分：98/100。交付进度：100%。Code Review：PASS（0 MUST / 0 HIGH / 1 SHOULD）。**
