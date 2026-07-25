package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PropertySegment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 比较内核发布的不可变真值与有界证据。
 *
 * <p>该类型位于 Compare 结果边界，只允许通过 {@link #canonical} 或唯一 reducer 构造。这样可以在对象诞生前
 * 一次性校验 outcome/completion 组合，并阻止 setter 或 builder 在构造后制造相互矛盾的第二套真值。</p>
 *
 * <p>结果真值与证据保留量有意分离：业务结论读取 outcome，changes 只表示当前仍保留的可审计明细。</p>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public class CompareResult {

    /** root fast path最多保留的类型元数据字符数，避免类型事实绕过结果边界。 */
    private static final int ROOT_METADATA_MAX_CHARS = 4_096;
  /** 同引用快路径的版本化算法标识. */
  private static final AlgorithmId IDENTITY_ID = AlgorithmId.of("tfi:identity:v1");
  /** 单边为空快路径的版本化算法标识. */
  private static final AlgorithmId NULLNESS_ID = AlgorithmId.of("tfi:nullness:v1");
  /** 运行时类型不一致快路径的版本化算法标识. */
  private static final AlgorithmId TYPE_MISMATCH_ID = AlgorithmId.of("tfi:type-mismatch:v1");

    /** 业务真值维度；不能从可能截断的changes列表反推。 */
    private final CompareOutcome outcome;

    /** 执行完整性维度；与outcome正交并由唯一reducer校验组合。 */
    private final CompareCompletion completion;

    /** 有界非预期故障事实，不保存Throwable或任意message。 */
    private final List<CompareProblem> problems;

    /** 有界policy/资源边界事实，与problem类型隔离。 */
    private final List<CompareLimitation> limitations;

    /** 解释本次执行所需的稳定ID与非负计数。 */
    private final CompareDiagnostics diagnostics;

    /** 仅完整结论允许发布的typed similarity。 */
    private final Optional<SimilarityScore> canonicalSimilarity;

    /** 已保留的有界差异明细；容量不足不改变outcome。 */
    private final List<FieldChange> changes;

    private CompareResult(
            CompareOutcome outcome,
            CompareCompletion completion,
            List<FieldChange> changes,
            List<CompareProblem> problems,
            List<CompareLimitation> limitations,
            CompareDiagnostics diagnostics,
            Optional<SimilarityScore> canonicalSimilarity) {
        this.outcome = outcome;
        this.completion = completion;
        this.changes = changes;
        this.problems = problems;
        this.limitations = limitations;
        this.diagnostics = diagnostics;
        this.canonicalSimilarity = canonicalSimilarity;
    }

    /**
     * 创建未执行对象遍历的同引用相等结果。
     *
     * @return {@code EQUAL + COMPLETE}且预算计数为零的canonical结果
     */
    public static CompareResult identical() {
        return identical(true);
    }

  /**
   * 按当前请求是否启用similarity构造identity快路径结果.
   *
   * <p>公开无参工厂保留显式identity证据；Engine必须使用本入口，避免默认
   * {@code computeSimilarity=false}的请求仍发布分数。</p>
   *
   * @param includeSimilarity 是否发布identity similarity
   * @return 与请求选项一致的canonical identity结果
   */
  /* default */ static CompareResult identical(final boolean includeSimilarity) {
    return canonical(
        CompareOutcome.EQUAL,
        CompareCompletion.COMPLETE,
        List.of(),
        List.of(),
        List.of(),
        fastPathDiagnostics(IDENTITY_ID, 0),
        includeSimilarity
            ? Optional.of(new SimilarityScore(IDENTITY_ID, 1.0))
            : Optional.empty());
  }

    /**
     * reducer写入结果的唯一canonical边界；所有集合在这里断开调用方可变引用。
     *
     * @param outcome 业务真值，不得从changes数量推导
     * @param completion 执行完整性，必须与outcome满足真值表
     * @param changes 已保留的有界差异事实
     * @param problems 已保留的非预期故障事实
     * @param limitations 已保留的policy或资源边界事实
     * @param diagnostics 非负、无业务对象的请求诊断
     * @param similarity 仅完整结论允许携带的typed分数
     * @return 校验并复制全部输入后的不可变结果
     */
    public static CompareResult canonical(
            CompareOutcome outcome,
            CompareCompletion completion,
            List<FieldChange> changes,
            List<CompareProblem> problems,
            List<CompareLimitation> limitations,
            CompareDiagnostics diagnostics,
            Optional<SimilarityScore> similarity) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(completion, "completion");
        List<FieldChange> immutableChanges = List.copyOf(changes);
        List<CompareProblem> immutableProblems = List.copyOf(problems);
        List<CompareLimitation> immutableLimitations = List.copyOf(limitations);
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(similarity, "similarity");
        validateTruth(
                outcome,
                completion,
                immutableChanges,
                immutableProblems,
                immutableLimitations,
                diagnostics,
                similarity);
        return new CompareResult(
                outcome,
                completion,
                immutableChanges,
                immutableProblems,
                immutableLimitations,
                diagnostics,
                similarity);
    }

    /**
     * 获取不依赖明细保留量的业务真值。
     *
     * @return canonical 比较结论
     */
    public CompareOutcome getOutcome() {
        return outcome;
    }

    /**
     * 获取与业务真值正交的执行完整性。
     *
     * @return canonical 完整性状态
     */
    public CompareCompletion getCompletion() {
        return completion;
    }

    /**
     * 获取不可变且受容量约束的 change 明细。
     *
     * @return 按 canonical 发现顺序冻结的明细
     */
    public List<FieldChange> getChanges() {
        return changes;
    }

    /**
     * 获取不保存 Throwable 或自由文本的 problem 明细。
     *
     * @return 不可变的非预期问题事实
     */
    public List<CompareProblem> getProblems() {
        return problems;
    }

    /**
     * 获取与 problem 类型隔离的预期边界明细。
     *
     * @return 不可变的 policy/预算边界事实
     */
    public List<CompareLimitation> getLimitations() {
        return limitations;
    }

    /**
     * 获取本次请求的有界执行与省略计数。
     *
     * @return 不含业务值的诊断事实
     */
    public CompareDiagnostics getDiagnostics() {
        return diagnostics;
    }

    /**
     * 只有完整执行并证明相等才返回true，旧boolean字段不再拥有结果真值。
     *
     * @return 是否为{@code EQUAL + COMPLETE}
     */
    public boolean isIdentical() {
        return outcome == CompareOutcome.EQUAL && completion == CompareCompletion.COMPLETE;
    }

    /**
     * 判断是否至少发现一条确定差异事实。
     *
     * @return outcome 为 {@link CompareOutcome#DIFFERENT} 时返回 {@code true}
     */
    public boolean isDifferent() {
        return outcome == CompareOutcome.DIFFERENT;
    }

    /**
     * 判断当前证据是否足以排除 {@link CompareOutcome#INDETERMINATE}。
     *
     * @return 结论为 EQUAL 或 DIFFERENT 时返回 {@code true}
     */
    public boolean isConclusive() {
        return outcome != CompareOutcome.INDETERMINATE;
    }

    /**
     * 判断是否在保留已有事实的同时存在未完成分支。
     *
     * @return completion 为 PARTIAL 时返回 {@code true}
     */
    public boolean isPartial() {
        return completion == CompareCompletion.PARTIAL;
    }

    /**
     * 判断是否保留或因容量省略过非预期 problem。
     *
     * @return 存在 problem 证据时返回 {@code true}
     */
    public boolean hasProblems() {
        return !problems.isEmpty() || diagnostics.omittedProblems() > 0;
    }

    /**
     * 判断当前有界结果是否仍保留至少一条 change 明细。
     *
     * @return change 列表非空时返回 {@code true}
     */
    public boolean hasChangeDetails() {
        return !changes.isEmpty();
    }

    /**
     * 获取合法完整结论的 typed similarity。
     *
     * @return 完整执行产生的分数；未请求或不合法时为空
     */
    public Optional<SimilarityScore> similarity() {
        return canonicalSimilarity;
    }

    /**
     * 仅输出结果形状，避免日志隐式遍历 change 或泄漏 exact value fact。
     *
     * @return 不包含路径和值内容的稳定诊断摘要
     */
    @Override
    public String toString() {
        return "CompareResult{" +
                "outcome=" + outcome +
                ", completion=" + completion +
                ", changeCount=" + changes.size() +
                ", problemCount=" + problems.size() +
                ", limitationCount=" + limitations.size() +
                ", hasSimilarity=" + canonicalSimilarity.isPresent() +
                '}';
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CompareResult that)) {
            return false;
        }
        return outcome == that.outcome
                && completion == that.completion
                && changes.equals(that.changes)
                && problems.equals(that.problems)
                && limitations.equals(that.limitations)
                && diagnostics.equals(that.diagnostics)
                && canonicalSimilarity.equals(that.canonicalSimilarity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                outcome,
                completion,
                changes,
                problems,
                limitations,
                diagnostics,
                canonicalSimilarity);
    }

    private static void validateTruth(
            CompareOutcome outcome,
            CompareCompletion completion,
            List<FieldChange> changes,
            List<CompareProblem> problems,
            List<CompareLimitation> limitations,
            CompareDiagnostics diagnostics,
            Optional<SimilarityScore> similarity) {
        boolean validCombination = switch (outcome) {
            case EQUAL -> completion == CompareCompletion.COMPLETE;
            case DIFFERENT -> completion == CompareCompletion.COMPLETE
                    || completion == CompareCompletion.PARTIAL;
            case INDETERMINATE -> completion == CompareCompletion.PARTIAL
                    || completion == CompareCompletion.FAILED
                    || completion == CompareCompletion.DISABLED;
        };
        if (!validCombination) {
            throw new IllegalArgumentException("illegal outcome and completion combination");
        }
        if (outcome == CompareOutcome.DIFFERENT && changes.isEmpty()) {
            throw new IllegalArgumentException("different result requires a change detail");
        }
        if (outcome == CompareOutcome.EQUAL && !changes.isEmpty()) {
            throw new IllegalArgumentException("equal result must not contain changes");
        }
        // change 是已确认的差异事实；若仍标记为无结论，会破坏 reducer 的单调真值语义。
        if (outcome == CompareOutcome.INDETERMINATE && !changes.isEmpty()) {
            throw new IllegalArgumentException("indeterminate result must not contain changes");
        }
        if (outcome == CompareOutcome.EQUAL
                && (!problems.isEmpty()
                || !limitations.isEmpty()
                || diagnostics.omittedPaths() > 0
                || diagnostics.omittedChanges() > 0
                || diagnostics.omittedProblems() > 0
                || diagnostics.omittedLimitations() > 0)) {
            throw new IllegalArgumentException("equal result requires complete issue evidence");
        }
        if (completion == CompareCompletion.DISABLED
                && (!changes.isEmpty()
                || !problems.isEmpty()
                || similarity.isPresent()
                || limitations.size() != 1
                || limitations.getFirst().code() != CompareLimitationCode.POLICY_DISABLED)) {
            throw new IllegalArgumentException("disabled result requires only policy-disabled limitation");
        }
        if (completion == CompareCompletion.FAILED
                && problems.isEmpty()
                && diagnostics.omittedProblems() == 0) {
            throw new IllegalArgumentException("failed result requires problem evidence");
        }
        // PARTIAL 必须携带可审计的不完整证据，否则消费者无法区分真实降级与错误拼装的状态。
        if (completion == CompareCompletion.PARTIAL
                && problems.isEmpty()
                && limitations.isEmpty()
                && diagnostics.omittedPaths() == 0
                && diagnostics.omittedChanges() == 0
                && diagnostics.omittedProblems() == 0
                && diagnostics.omittedLimitations() == 0) {
            throw new IllegalArgumentException("partial result requires incomplete evidence");
        }
        if (similarity.isPresent()) {
            SimilarityScore score = similarity.orElseThrow();
            if (completion != CompareCompletion.COMPLETE
                    || !diagnostics.appliedAlgorithmIds().contains(score.algorithmId())) {
                throw new IllegalArgumentException("similarity requires a complete applied algorithm");
            }
        }
    }
    
    /**
     * 为恰有一侧为 null 的输入创建确定差异结果。
     *
     * @param obj1 左侧根值
     * @param obj2 右侧根值
     * @return 完整的 NULLNESS 差异结果
     */
    public static CompareResult ofNullDiff(Object obj1, Object obj2) {
        if ((obj1 == null) == (obj2 == null)) {
            throw new IllegalArgumentException("null mismatch requires exactly one null input");
        }
        ChangeSide before = new ChangeSide(ComparePath.root(), rootValueFact(obj1));
        ChangeSide after = new ChangeSide(ComparePath.root(), rootValueFact(obj2));
        FieldChange change = FieldChange.canonical(
                ChangeKind.NULLNESS,
                Optional.of(before),
                Optional.of(after));
        return canonical(
                CompareOutcome.DIFFERENT,
                CompareCompletion.COMPLETE,
                List.of(change),
                List.of(),
                List.of(),
                fastPathDiagnostics(NULLNESS_ID, 1),
                Optional.empty());
    }
    
    /**
     * 为两个不同运行时类型的非 null 输入创建确定差异结果。
     *
     * @param obj1 非 null 左侧根值
     * @param obj2 非 null 右侧根值
     * @return 完整的 TYPE_MISMATCH 差异结果
     */
    public static CompareResult ofTypeDiff(Object obj1, Object obj2) {
        Objects.requireNonNull(obj1, "obj1");
        Objects.requireNonNull(obj2, "obj2");
        if (obj1.getClass().equals(obj2.getClass())) {
            throw new IllegalArgumentException("type mismatch requires different runtime types");
        }
        FieldChange change = FieldChange.canonical(
                ChangeKind.TYPE_MISMATCH,
                Optional.of(new ChangeSide(ComparePath.root(), rootValueFact(obj1))),
                Optional.of(new ChangeSide(ComparePath.root(), rootValueFact(obj2))));
        return canonical(
                CompareOutcome.DIFFERENT,
                CompareCompletion.COMPLETE,
                List.of(change),
                List.of(),
                List.of(),
                fastPathDiagnostics(TYPE_MISMATCH_ID, 1),
                Optional.empty());
    }

    private static ValueSnapshot rootValueFact(Object value) {
        return value == null
                ? ValueSnapshot.exactNull()
                : ValueSnapshot.ofTypeMetadata(value.getClass(), ROOT_METADATA_MAX_CHARS);
    }

    private static CompareDiagnostics fastPathDiagnostics(
            AlgorithmId algorithmId,
            long comparedNodes) {
        return new CompareDiagnostics(
                0,
                Optional.of(algorithmId),
                List.of(algorithmId),
                Optional.empty(),
                comparedNodes, 0, 0, 0, 0, 0, 0);
    }
    
    /**
     * 获取变更数量
     *
     * @return 当前保留的 change 明细数量
     */
    public int getChangeCount() {
        return changes != null ? changes.size() : 0;
    }
    
    // ========== P1-T3 新增查询方法 ==========

    /**
     * 按变更类型过滤（v3.1.0-P1）
     * <p>
     * 支持可变参数，可一次查询多个类型。
     * </p>
     *
     * <h4>使用示例</h4>
     * <pre>{@code
     * // 单类型查询
     * List<FieldChange> creates = result.getChangesByType(ChangeType.CREATE);
     *
     * // 多类型查询
     * List<FieldChange> mutations = result.getChangesByType(
     *     ChangeType.CREATE, ChangeType.DELETE
     * );
     *
     * // 无参数查询（返回所有变更）
     * List<FieldChange> all = result.getChangesByType();
     * }</pre>
     *
     * @param types 变更类型（可变参数），为空时返回所有变更
     * @return 匹配的变更列表（不可变副本）
     * @since v3.1.0-P1
     */
    public List<FieldChange> getChangesByType(ChangeType... types) {
        if (changes == null) {
            return Collections.emptyList();
        }
        if (types == null || types.length == 0) {
            return Collections.unmodifiableList(changes);
        }

        Set<ChangeType> typeSet = Set.of(types);
        return changes.stream()
            .filter(c -> typeSet.contains(c.getChangeType()))
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 获取所有引用变更（v3.1.0-P1）
     * <p>
     * 依赖 P1-T2 Reference Semantic，返回所有标记为 referenceChange=true 的变更。
     * </p>
     * <p>
     * <strong>注意</strong>：此方法依赖 P1-T2 尚未实现的 referenceChange 字段，
     * 当前返回空列表作为占位实现。
     * </p>
     *
     * @return 引用变更列表（不可变副本）
     * @since v3.1.0-P1
     */
    public List<FieldChange> getReferenceChanges() {
        return Collections.emptyList();
    }

    /**
     * 获取所有容器元素变更（v3.1.0-P1）
     * <p>
     * 依赖 P1-T1 Container Events，返回所有 elementEvent 非 null 的变更。
     * </p>
     * <p>
     * <strong>注意</strong>：此方法依赖 P1-T1 尚未实现的 elementEvent 字段，
     * 当前返回空列表作为占位实现。
     * </p>
     *
     * <p>容器身份来自canonical path segment；调用方应读取路径和kind，不能依赖已移除的旁路事件状态。</p>
     *
     * @return 容器变更列表（不可变副本）
     * @since v3.1.0-P1
     */
    public List<FieldChange> getContainerChanges() {
        if (changes == null) {
            return Collections.emptyList();
        }
        return changes.stream()
            .filter(this::hasContainerPath)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 按对象路径分组（v3.1.0-P1）
     * <p>
     * 将变更按对象路径前缀分组，便于查看每个对象的所有变更。
     * </p>
     *
     * <h4>使用示例</h4>
     * <pre>{@code
     * Map<String, List<FieldChange>> byObject = result.groupByObject();
     *
     * // 输出：
     * // "order" -> [order.status, order.amount]
     * // "order.customer" -> [order.customer.name]
     * // "items[0]" -> [items[0].price, items[0].quantity]
     *
     * byObject.forEach((obj, objChanges) -> {
     *     System.out.println("Object: " + obj + ", Changes: " + objChanges.size());
     * });
     * }</pre>
     *
     * @return {@code Map<对象路径, 变更列表>}（不可变副本）
     * @since v3.1.0-P1
     */
    public Map<String, List<FieldChange>> groupByObject() {
        if (changes == null) {
            return Collections.emptyMap();
        }
        Map<String, List<FieldChange>> grouped = changes.stream()
            .collect(Collectors.groupingBy(
                this::extractObjectPath,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    Collections::unmodifiableList
                )
            ));
        return Collections.unmodifiableMap(grouped);
    }

    /**
     * 按属性名分组（v3.1.0-P1）
     * <p>
     * 将变更按字段名分组，便于查看所有对象的同名字段变更。
     * </p>
     *
     * <h4>使用示例</h4>
     * <pre>{@code
     * Map<String, List<FieldChange>> byProperty = result.groupByProperty();
     *
     * // 输出：
     * // "price" -> [items[0].price, items[1].price]
     * // "status" -> [order.status]
     *
     * byProperty.get("price").forEach(change -> {
     *     System.out.println(change.getFieldPath() + ": " +
     *         change.beforeValue().map(ValueSnapshot::representation) + " -> " +
     *         change.afterValue().map(ValueSnapshot::representation));
     * });
     * }</pre>
     *
     * @return {@code Map<属性名, 变更列表>}（不可变副本）
     * @since v3.1.0-P1
     */
    public Map<String, List<FieldChange>> groupByProperty() {
        if (changes == null) {
            return Collections.emptyMap();
        }
        Map<String, List<FieldChange>> grouped = changes.stream()
            .collect(Collectors.groupingBy(
                FieldChange::getFieldName,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    Collections::unmodifiableList
                )
            ));
        return Collections.unmodifiableMap(grouped);
    }

    /**
     * 按容器操作分组（v3.1.0-P1）
     * <p>
     * 仅针对容器变更，按 ElementOperation 分组。
     * 依赖 P1-T1 Container Events。
     * </p>
     * <p>
     * <strong>注意</strong>：此方法依赖 P1-T1 尚未实现的容器事件体系，
     * 当前返回空 Map 作为占位实现。
     * </p>
     *
     * <h4>使用示例</h4>
     * <pre>{@code
     * Map<ElementOperation, List<FieldChange>> byOp = result.groupByContainerOperation();
     *
     * // 输出：
     * // ADD -> [items[2], items[3]]
     * // MODIFY -> [items[0].price, items[1].quantity]
     * // REMOVE -> [items[4]]
     *
     * long addedCount = byOp.getOrDefault(ElementOperation.ADD, Collections.emptyList()).size();
     * System.out.println("Added: " + addedCount);
     * }</pre>
     *
     * @return {@code Map<操作类型, 变更列表>}（不可变副本）
     * @since v3.1.0-P1
     */
    public Map<FieldChange.ElementOperation, List<FieldChange>> groupByContainerOperationTyped() {
        List<FieldChange> containerChanges = getContainerChanges();
        if (containerChanges.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<FieldChange.ElementOperation, List<FieldChange>> grouped = containerChanges.stream()
            .collect(Collectors.groupingBy(
                this::containerOperation,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    Collections::unmodifiableList
                )
            ));
        return Collections.unmodifiableMap(grouped);
    }

    /**
     * 按容器操作分组（强类型，主API）
     *
     * @return 按 typed 容器操作分组的不可变 Map
     */
    public Map<FieldChange.ElementOperation, List<FieldChange>> groupByContainerOperation() {
        return groupByContainerOperationTyped();
    }

    

    /**
     * 过滤指定容器类型的变更（强类型版）
     * @param types 容器类型可变参数（为空时返回所有容器变更）
     * @return 匹配容器类型的变更列表
     * @since v3.1.0-P1
     */
    public List<FieldChange> getContainerChangesByType(FieldChange.ContainerType... types) {
        List<FieldChange> container = getContainerChanges();
        if (container.isEmpty()) {
            return Collections.emptyList();
        }
        if (types == null || types.length == 0) {
            return container;
        }
        Set<FieldChange.ContainerType> set = Set.of(types);
        return container.stream()
            .filter(fc -> set.contains(containerType(fc)))
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 按变更类型统计数量（v3.1.0-P1）
     * <p>
     * 返回每种 ChangeType 的变更数量。
     * </p>
     *
     * <h4>使用示例</h4>
     * <pre>{@code
     * Map<ChangeType, Long> counts = result.getChangeCountByType();
     *
     * // 输出：
     * // CREATE -> 5
     * // UPDATE -> 8
     * // DELETE -> 2
     *
     * long createCount = counts.getOrDefault(ChangeType.CREATE, 0L);
     * System.out.println("Created: " + createCount);
     * }</pre>
     *
     * @return {@code Map<变更类型, 数量>}（不可变副本）
     * @since v3.1.0-P1
     */
    public Map<ChangeType, Long> getChangeCountByType() {
        if (changes == null) {
            return Collections.emptyMap();
        }
        return changes.stream()
            .collect(Collectors.groupingBy(
                FieldChange::getChangeType,
                Collectors.counting()
            ));
    }

    /**
     * 按容器操作分组（字符串键，向后兼容某些调用方）
     * <p>
     * 建议使用 {@link #groupByContainerOperation()} 获取强类型结果；
     * 本方法仅做兼容封装。
     * </p>
     * <p>
     * 迁移声明：计划于 v3.2.0 移除该方法，请迁移至强类型版本。
     * </p>
     * @return 以操作枚举名称为键的不可变兼容 Map
     * @since v3.1.0-P1
     */
    @Deprecated
    public Map<String, List<FieldChange>> groupByContainerOperationAsString() {
        Map<FieldChange.ElementOperation, List<FieldChange>> typed = groupByContainerOperation();
        if (typed.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<FieldChange>> out = new LinkedHashMap<>();
        typed.forEach((k, v) -> out.put(k.name(), v));
        return Collections.unmodifiableMap(out);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 从 FieldChange 提取对象路径
     *
     * @param change 字段变更
     * @return 对象路径（如 "order", "items[0]", "customer"）
     */
    private String extractObjectPath(FieldChange change) {
        // 使用 fieldPath（嵌套对象）
        String path = change.getFieldPath() != null
            ? change.getFieldPath() : change.getFieldName();

        // 提取第一级路径（如 "order.customer.name" -> "order"）
        int dotIndex = path.indexOf('.');
        return dotIndex > 0 ? path.substring(0, dotIndex) : path;
    }

    private boolean hasContainerPath(FieldChange change) {
        return change.before().or(() -> change.after()).orElseThrow().path().segments().stream()
                .anyMatch(segment -> !(segment instanceof PropertySegment));
    }

    private FieldChange.ElementOperation containerOperation(FieldChange change) {
        return switch (change.kind()) {
            case ADD -> FieldChange.ElementOperation.ADD;
            case REMOVE -> FieldChange.ElementOperation.REMOVE;
            case MOVE -> FieldChange.ElementOperation.MOVE;
            case MODIFY, NULLNESS, TYPE_MISMATCH -> FieldChange.ElementOperation.MODIFY;
        };
    }

    private FieldChange.ContainerType containerType(FieldChange change) {
        return change.before().or(() -> change.after()).orElseThrow().path().segments().stream()
                .filter(segment -> !(segment instanceof PropertySegment))
                .findFirst()
                .map(segment -> switch (segment.kind()) {
                    case INDEX -> FieldChange.ContainerType.LIST;
                    case MAP_KEY -> FieldChange.ContainerType.MAP;
                    case SET_MEMBER, ENTITY_KEY -> FieldChange.ContainerType.SET;
                    case PROPERTY -> throw new IllegalStateException("property is not a container segment");
                })
                .orElseThrow();
    }
}
