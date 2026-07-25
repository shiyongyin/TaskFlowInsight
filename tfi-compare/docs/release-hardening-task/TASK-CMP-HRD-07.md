# TASK-CMP-HRD-07：建立 final publishable artifact 闭集

> **定位**：把运行验证用 JAR/POM 提升为可离线审核的 final-version Maven2 primary/checksum 闭集。
> **deliveryStatus**：`COMPLETE`
> **reviewStatus**：`PASS`
> **依赖**：`TASK-CMP-HRD-06` review PASS
> **后续**：`TASK-CMP-HRD-08`
> **红队来源**：生产 MUST-3、MUST-5 的制品部分、HIGH-2

---

## 一、核心

### 目标（DoD）

- [x] release build 只接受 policy 注入的 fixed `finalVersion`；SNAPSHOT、range、LATEST/RELEASE、未解析属性立即失败。
- [x] root 与六个发布模块的 effective/flattened POM 具有非空 name/description/url/license/developer/SCM，坐标和版本一致。
- [x] 发布模块闭集固定为 parent POM，以及 Flow Core、Flow starter、Compare、Compare starter、Ops、All 六个 JAR GAV；
  Kernel 与 Examples 不进入 Compare 发布闭集。
- [x] 每个 JAR GAV 生成 POM/BINARY/SOURCES/JAVADOC primary；parent 只生成 POM。
- [x] BINARY 内含 root Apache-2.0 LICENSE；SOURCES 与 candidateRevision 生产源码双向一致；JAVADOC 覆盖 public API type。
- [x] `PublishArtifactAssembler` 只按 policy manifest 创建 Maven2 relative path、SHA256/SHA512 sidecar 和结构化 content manifest。
- [x] primary/checksum bytes 在进入 HRD-08 前冻结；不运行 deploy/publish，不读取凭据或远程仓库。
- [x] 3.0 -> final -> 3.0 rollback 对同一输入断言 exact outcome/completion/change paths；兼容矩阵覆盖全部直接 TFI 边。

### 固定边界

1. 默认开发版本可继续为 `4.0.0-SNAPSHOT`；只有 release 命令显式传入 `-Drevision=<policy finalVersion>`，不得提交临时改版 POM。
2. root project metadata 复用 `tfi-kernel` 已确认的 GitHub/Apache-2.0/developer/SCM 值，模块继承；不新增组织或虚构发布站点。
3. release profile 只负责 attach sources/javadocs、LICENSE 和 release flattening；签名、SBOM、scanner/provenance 归 HRD-08。
4. 目标仓库由 external policy 决定；本卡只构建离线 Maven2 layout，不配置 distributionManagement 凭据。

## 二、执行

### 文件与职责

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 修改 | `pom.xml` | 完整 project metadata、release profile、final version guard、LICENSE resource |
| 核对/最小修改 | 六个发布模块 `pom.xml` | artifact metadata/packaging 与 release inheritance |
| 新增 | `scripts/release-evidence/PublishArtifactAssembler.java` | manifest parser、Maven2 layout、checksum/content inventory |
| 新增 | `scripts/verify_tfi_compare_artifact_consumers.sh` | isolated repository 与 publish-layout fixture runner；HRD-05 只扩展编排 |
| 新增测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/architecture/PublishArtifactAssemblerContractTests.java` | SNAPSHOT/path/traversal/duplicate/content 负例 |
| 新增测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/architecture/ComparePublishabilityContractTests.java` | POM/profile/module/metadata/LICENSE 闭集 |
| 新增 fixtures | `tfi-compare/src/it/artifact-consumers/publish-layout/` | final primary/layout/content 验证 |
| 扩展 | `StableFacadeSmokeTests` 与 mixed fixtures | exact semantic rollback 与 compatibility matrix |
| 修改 SSOT | `tfi-compare/docs/design-doc.md` | 运行验证闭集与发布闭集分离、无发布授权边界 |

### 精确实现

1. root metadata 固定为：URL `https://github.com/shiyongyin/TaskFlowInsight`；license `Apache License, Version 2.0`/
   `https://www.apache.org/licenses/LICENSE-2.0.txt`/distribution=`repo`；developer id/name=`shiyongyin`；SCM 使用当前 GitHub HTTPS/SSH，
   tag=`HEAD`。禁止空节点。
2. `release-artifacts` profile 在六个 jar module attach source/javadoc，并把 root `LICENSE` 复制为 `META-INF/LICENSE`；release flatten POM
   必须解析 `${revision}` 和所有内部版本。源码编译失败或 Javadoc warning-as-error 时不得生成 partial publish set。
3. final version enforcer 同时校验 project、parent、六个内部 dependency version，不允许 runtime SNAPSHOT、systemPath、file repository 或
   test dependency 泄漏。`api-compat` 的 fixed local repository 只在该 profile 生效，不得进入 flattened release POM。
4. assembler 输入只接受 32-line production policy 的 `publishArtifactManifest` 封存副本和 build output manifest；它不执行 Maven、不签名、
   不推送。所有 path 使用 NOFOLLOW、POSIX relative、无 `..`/反斜杠，重复/额外/missing row 失败。
   CLI 固定为 `java scripts/release-evidence/PublishArtifactAssembler.java assemble <evidence-dir> <production-policy.tsv>`；未知 mode、
   参数数量错误或 evidence/policy 不可寻址必须非零退出。
5. primary coordinate/path 必须按发布提示词 Maven2 公式推导；checksum 内容是 lowercase digest + LF。assembler 输出
   `metadata/publishable-artifacts.tsv` 和 `metadata/publishable-content.tsv`，不得信文件名自报坐标。
6. content verifier 从 class access flag/source parser/Javadoc index 生成 type mapping；dummy `.java`/HTML、同名不同 source revision、
   sources extra/missing、public binary 无 Javadoc 都失败。
7. rollback fixture 固定输入包含 nested POJO/List/Map/Set，三阶段逐 byte 比较 canonical outcome/completion/change-path TSV；mixed matrix
   从 parent/Flow/Compare starter/Ops/All retained POM 枚举直接边，SUPPORTED/REJECTED 均有 row-specific evidence。

### 验证命令

```bash
./mvnw -Drevision=4.0.0 -Prelease-artifacts \
  -pl tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-compare-spring-starter,tfi-ops-spring,tfi-all \
  -am clean package

./mvnw -pl tfi-compare clean \
  -Dtest=ComparePublishabilityContractTests,PublishArtifactAssemblerContractTests,CompatibilityMatrixFixturePreparerContractTests \
  test

bash scripts/verify_tfi_compare_artifact_consumers.sh --fixture publish-layout --version 4.0.0
./mvnw -pl tfi-compare,tfi-compare-spring-starter,tfi-ops-spring,tfi-all -am verify
```

验收中的 `4.0.0` 只是固定非 SNAPSHOT 测试 fixture；05A 必须用 production policy finalVersion，不能硬编码该值。

### 审核检查点

- [x] CP-1：final version/POM metadata/dependency closure 均从 retained flattened bytes 重算。
- [x] CP-2：1 个 parent POM + 6 个 JAR GAV 的 primary role 无缺失/额外，Kernel/Examples 不在闭集。
- [x] CP-3：LICENSE、source revision、binary type、Javadoc type mapping 双向一致。
- [x] CP-4：Maven2 path/checksum sidecar exact，assembler 无 deploy/network/credential 能力。
- [x] CP-5：rollback exact semantics 与 compatibility matrix 每行都有 retained artifact/CodeSource 证据。
- [x] CP-6：默认 SNAPSHOT 开发构建未被冒充为 release candidate。

### 禁止范围与回滚

本卡不添加仓库凭据、deploy plugin、签名私钥、联网 scanner 或自签 provenance。回滚必须同时恢复 metadata/profile/assembler/fixtures；
不得保留能对 SNAPSHOT 生成 publishable manifest 的旁路。

## 三、自省

- [x] 发布闭集与运行验证闭集分离，避免 13 artifact hash 冒充 sources/javadocs/signature。
- [x] offline assembler 不拥有发布动作，符合最小权限。
- [x] 不把安全扫描和可信 builder 责任塞进 POM metadata 卡。

## 四、反馈

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| Maven 插件继承 | release guard 只在发布 profile 生效 | release Enforcer 可继承，根 reactor convergence execution 显式不可继承 | Maven 会按插件坐标合并 build/profile，必须同时固定两侧继承边界 |
| baseline POM authority | 根 POM 变更后保持 ratchet 可复算 | POM 整文件 SHA 仅保留历史 provenance，新增 DOM 合同锁定三类插件语义 | 发布 metadata 不应制造静态分析假红，插件配置也不能退化为无校验 |
| Javadoc 闭集 | public binary type 均有 source/Javadoc | 为 11 个 Lombok generated builder 增加同名 source 声明，保持既有 builder API | 不以排除 generated public API 绕过真实闭集 |
| assembler 输出原子性 | 只写 evidence-owned 路径 | 拒绝符号链接 output parent，metadata 先移动、repository 最后作为完成标记 | 防止越界写入及 partial output 被误判为完整仓库 |
| 兼容矩阵 | 覆盖全部直接 TFI 边 | 41 行 retained POM/CodeSource 证据；mixed 只对 Compare 3.0/4.0 convergence 失败 | 排除无关 SLF4J convergence，保留目标版本冲突证明 |
| KISS 审查 | 发布工具保持最小依赖 | 两个超大文件登记为非阻断 SHOULD，不在本卡拆分 | source-file mode 需要单文件无构建执行；当前职责已内部隔离且测试闭合 |

## 五、总结

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | focused contracts 12/12；fixed-final package 与最终 7 项 Reactor 全绿 |
| 完整性 | 25 /25 | 25 primary + 50 checksum；246 type rows；41 compatibility rows；rollback 三阶段 byte-identical |
| 可维护性 | 23 /25 | Policy/Manifest/Archive/Content/Assembly 分区明确；两个 >500 行脚本保留为 source-file mode SHOULD |
| 风险控制 | 25 /25 | NOFOLLOW、路径闭集、完成标记、只读 baseline；无 deploy/network/credential/publish 能力 |

**总分：98/100。交付进度：100%。Code Review：PASS（0 MUST / 0 HIGH / 2 SHOULD）。**
