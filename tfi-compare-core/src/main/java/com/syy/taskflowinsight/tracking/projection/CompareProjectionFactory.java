package com.syy.taskflowinsight.tracking.projection;

import com.syy.taskflowinsight.tracking.compare.ChangeSide;
import com.syy.taskflowinsight.tracking.compare.CompareDiagnostics;
import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.SimilarityScore;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.projection.internal.SensitiveValueDetector;
import com.syy.taskflowinsight.tracking.projection.internal.ProjectionFrame;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.EntityKeySegment;
import com.syy.taskflowinsight.tracking.path.IndexSegment;
import com.syy.taskflowinsight.tracking.path.MapKeySegment;
import com.syy.taskflowinsight.tracking.path.PathSegment;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import com.syy.taskflowinsight.tracking.path.SetMemberSegment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 将不可变比较事实一次性转换为canonical、已脱敏projection的唯一owner。
 *
 * <p>factory只读取结果模型的typed facts；formatter不得绕过该边界重新访问raw result或业务图。</p>
 *
 * @since 4.0.0
 */
public final class CompareProjectionFactory {

    /**
     * 构建单次发布使用的不可变projection。
     *
     * @param result 已通过canonical结果边界校验的比较事实
     * @param metadata 固定字段闭集，不允许任意labels
     * @param maskingPolicy 当前调用的immutable安全策略
     * @param options 只控制metadata预算的单次选项
     * @return schema v1的不可变、已脱敏字段树
     */
    public CompareProjection create(
            CompareResult result,
            ProjectionMetadata metadata,
            MaskingPolicy maskingPolicy,
            ProjectionOptions options) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(maskingPolicy, "maskingPolicy");
        Objects.requireNonNull(options, "options");

        List<ProjectionNode.Member> root = new ArrayList<>();
        root.add(member("schemaId", string(CompareProjection.SCHEMA_ID)));
        root.add(member("schemaVersion", ProjectionNode.number(CompareProjection.SCHEMA_VERSION)));
        root.add(member("outcome", string(result.getOutcome().name())));
        root.add(member("completion", string(result.getCompletion().name())));
        root.add(member("problems", problems(result.getProblems(), maskingPolicy)));
        root.add(member("limitations", limitations(result.getLimitations(), maskingPolicy)));
        root.add(member("diagnostics", diagnostics(result.getDiagnostics())));
        root.add(member("changes", changes(result.getChanges(), maskingPolicy)));
        metadata(metadata, options, maskingPolicy).ifPresent(node -> root.add(member("metadata", node)));
        result.similarity().ifPresent(score -> root.add(member("similarity", similarity(score))));
        ProjectionNode rootNode = ProjectionNode.object(root);
        ProjectionFrame.validate(
                rootNode,
                ProjectionFrame.MAX_SCHEMA_DEPTH,
                ProjectionFrame.MAX_PROJECTION_TEXT_CHARS);
        return new CompareProjection(rootNode);
    }

    private static ProjectionNode problems(
            List<CompareProblem> problems,
            MaskingPolicy maskingPolicy) {
        return ProjectionNode.array(problems.stream()
                .map(problem -> issue(
                        problem.code().wireCode(),
                        problem.stage().name(),
                        problem.path(),
                        maskingPolicy))
                .toList());
    }

    private static ProjectionNode limitations(
            List<CompareLimitation> limitations,
            MaskingPolicy maskingPolicy) {
        return ProjectionNode.array(limitations.stream()
                .map(limitation -> issue(
                        limitation.code().wireCode(),
                        limitation.stage().name(),
                        limitation.path(),
                        maskingPolicy))
                .toList());
    }

    private static ProjectionNode issue(
            String code,
            String stage,
            Optional<ComparePath> issuePath,
            MaskingPolicy maskingPolicy) {
        List<ProjectionNode.Member> fields = new ArrayList<>();
        fields.add(member("code", string(code)));
        fields.add(member("stage", string(stage)));
        issuePath.ifPresent(value -> fields.add(member("path", path(value, maskingPolicy))));
        return ProjectionNode.object(fields);
    }

    private static Optional<ProjectionNode> metadata(
            ProjectionMetadata metadata,
            ProjectionOptions options,
            MaskingPolicy maskingPolicy) {
        List<ProjectionNode.Member> fields = new ArrayList<>();
        metadata.sessionId().ifPresent(value -> fields.add(member(
                "sessionId", metadataValue("sessionId", value, options.maxMetadataChars(), maskingPolicy))));
        metadata.taskId().ifPresent(value -> fields.add(member(
                "taskId", metadataValue("taskId", value, options.maxMetadataChars(), maskingPolicy))));
        metadata.operationName().ifPresent(value -> fields.add(member(
                "operationName", metadataValue(
                        "operationName", value, options.maxMetadataChars(), maskingPolicy))));
        return fields.isEmpty() ? Optional.empty() : Optional.of(ProjectionNode.object(fields));
    }

    private static ProjectionNode metadataValue(
            String fieldName,
            String rawValue,
            int maxChars,
            MaskingPolicy maskingPolicy) {
        if (maxChars == 0 || rawValue.length() > maxChars) {
            return ProjectionNode.object(List.of(
                    member("representation", string("OMITTED")),
                    member("type", string("string")),
                    member("reason", string("VALUE_LIMIT"))));
        }
        ComparePath metadataPath = ComparePath.root().append(new PropertySegment(fieldName));
        return value(
                ValueSnapshot.ofString(rawValue, maxChars),
                maskingPolicy.shouldMask(metadataPath),
                maskingPolicy);
    }

    private static ProjectionNode diagnostics(CompareDiagnostics diagnostics) {
        return ProjectionNode.object(List.of(
                member("durationNanos", decimal(diagnostics.durationNanos())),
                member("rootAlgorithmId", diagnostics.rootAlgorithmId()
                        .map(id -> string(id.value())).orElseGet(ProjectionNode::nullNode)),
                member("appliedAlgorithmIds", ProjectionNode.array(diagnostics.appliedAlgorithmIds().stream()
                        .map(id -> string(id.value())).toList())),
                member("effectivePolicyFingerprint", diagnostics.effectivePolicyFingerprint()
                        .map(CompareProjectionFactory::string).orElseGet(ProjectionNode::nullNode)),
                member("comparedNodes", decimal(diagnostics.comparedNodes())),
                member("consumedElements", decimal(diagnostics.consumedElements())),
                member("retainedResultChars", decimal(diagnostics.retainedResultChars())),
                member("omittedPaths", decimal(diagnostics.omittedPaths())),
                member("omittedChanges", decimal(diagnostics.omittedChanges())),
                member("omittedProblems", decimal(diagnostics.omittedProblems())),
                member("omittedLimitations", decimal(diagnostics.omittedLimitations()))));
    }

    private static ProjectionNode changes(
            List<FieldChange> changes,
            MaskingPolicy maskingPolicy) {
        List<FieldChange> ordered = new ArrayList<>(changes);
        ordered.sort(CompareProjectionFactory::compareChanges);
        List<ChangeMaskIdentity> identities = ordered.stream()
                .map(change -> changeMaskIdentity(change, maskingPolicy))
                .toList();
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (ChangeMaskIdentity identity : identities) {
            if (identity.masked) {
                totals.merge(identity.key, 1, Integer::sum);
            }
        }
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        List<ProjectionNode> projected = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            ChangeMaskIdentity identity = identities.get(index);
            MaskedOccurrence occurrence = null;
            if (identity.masked && totals.getOrDefault(identity.key, 0) > 1) {
                occurrence = new MaskedOccurrence(occurrences.merge(identity.key, 1, Integer::sum) - 1);
            }
            projected.add(change(ordered.get(index), maskingPolicy, occurrence));
        }
        return ProjectionNode.array(projected);
    }

    private static int compareChanges(FieldChange left, FieldChange right) {
        ComparePath leftPath = primaryPath(left);
        ComparePath rightPath = primaryPath(right);
        int pathOrder = ComparePath.canonicalOrder().compare(leftPath, rightPath);
        if (pathOrder != 0) {
            return pathOrder;
        }
        return left.kind().name().compareTo(right.kind().name());
    }

    private static ComparePath primaryPath(FieldChange change) {
        return change.after().or(() -> change.before()).orElseThrow().path();
    }

    private static ChangeMaskIdentity changeMaskIdentity(
            FieldChange change,
            MaskingPolicy maskingPolicy) {
        StringBuilder key = new StringBuilder();
        appendFact(key, change.kind().name());
        boolean masked = false;
        if (change.before().isPresent()) {
            PathMaskIdentity before = pathMaskIdentity(change.before().orElseThrow().path(), maskingPolicy);
            appendFact(key, "before");
            appendFact(key, before.key);
            masked |= before.masked;
        }
        if (change.after().isPresent()) {
            PathMaskIdentity after = pathMaskIdentity(change.after().orElseThrow().path(), maskingPolicy);
            appendFact(key, "after");
            appendFact(key, after.key);
            masked |= after.masked;
        }
        return new ChangeMaskIdentity(key.toString(), masked);
    }

    private static PathMaskIdentity pathMaskIdentity(
            ComparePath path,
            MaskingPolicy maskingPolicy) {
        StringBuilder key = new StringBuilder();
        boolean masked = false;
        boolean pathRuleMatches = maskingPolicy.shouldMask(path);
        for (PathSegment segment : path.segments()) {
            appendFact(key, segment.kind().wireCode());
            if (segment instanceof PropertySegment property) {
                appendFact(key, property.name());
            } else if (segment instanceof IndexSegment index) {
                appendFact(key, Integer.toString(index.index()));
            } else if (segment instanceof MapKeySegment mapKey) {
                masked |= appendSnapshotIdentity(key, mapKey.key(), pathRuleMatches, maskingPolicy);
            } else if (segment instanceof SetMemberSegment setMember) {
                masked |= appendSnapshotIdentity(key, setMember.member(), pathRuleMatches, maskingPolicy);
            } else if (segment instanceof EntityKeySegment entityKey) {
                appendFact(key, entityKey.declaringType());
                for (ValueSnapshot component : entityKey.components()) {
                    masked |= appendSnapshotIdentity(key, component, pathRuleMatches, maskingPolicy);
                }
            }
        }
        return new PathMaskIdentity(key.toString(), masked);
    }

    private static boolean appendSnapshotIdentity(
            StringBuilder key,
            ValueSnapshot snapshot,
            boolean pathRuleMatches,
            MaskingPolicy maskingPolicy) {
        if (shouldMask(snapshot, pathRuleMatches, maskingPolicy)) {
            appendFact(key, "[REDACTED]");
            return true;
        }
        appendFact(key, snapshot.representation().name());
        appendFact(key, snapshot.typeCode());
        snapshot.canonicalTextFacts().forEach(fact -> appendFact(key, fact));
        snapshot.omissionReason().ifPresent(reason -> appendFact(key, reason.name()));
        return false;
    }

    private static void appendFact(StringBuilder target, String fact) {
        target.append(fact.length()).append(':').append(fact);
    }

    private static ProjectionNode change(
            FieldChange change,
            MaskingPolicy maskingPolicy,
            MaskedOccurrence maskedOccurrence) {
        List<ProjectionNode.Member> fields = new ArrayList<>();
        fields.add(member("kind", string(change.kind().name())));
        change.before().ifPresent(side -> fields.add(member("before", side(side, maskingPolicy))));
        change.after().ifPresent(side -> fields.add(member("after", side(side, maskingPolicy))));
        if (maskedOccurrence != null) {
            fields.add(member("maskedOccurrence", ProjectionNode.number(maskedOccurrence.value())));
        }
        return ProjectionNode.object(fields);
    }

    private static ProjectionNode side(ChangeSide side, MaskingPolicy maskingPolicy) {
        boolean pathRuleMatches = maskingPolicy.shouldMask(side.path());
        return ProjectionNode.object(List.of(
                member("path", path(side.path(), maskingPolicy)),
                member("value", value(side.value(), pathRuleMatches, maskingPolicy))));
    }

    private static ProjectionNode path(ComparePath path, MaskingPolicy maskingPolicy) {
        boolean pathRuleMatches = maskingPolicy.shouldMask(path);
        return ProjectionNode.array(path.segments().stream()
                .map(segment -> segment(segment, pathRuleMatches, maskingPolicy))
                .toList());
    }

    private static ProjectionNode segment(
            PathSegment segment,
            boolean pathRuleMatches,
            MaskingPolicy maskingPolicy) {
        List<ProjectionNode.Member> fields = new ArrayList<>();
        fields.add(member("kind", string(segment.kind().wireCode())));
        if (segment instanceof PropertySegment property) {
            fields.add(member("name", string(property.name())));
        } else if (segment instanceof IndexSegment index) {
            fields.add(member("index", ProjectionNode.number(index.index())));
        } else if (segment instanceof MapKeySegment mapKey) {
            fields.add(member("key", value(mapKey.key(), pathRuleMatches, maskingPolicy)));
        } else if (segment instanceof SetMemberSegment setMember) {
            fields.add(member("member", value(setMember.member(), pathRuleMatches, maskingPolicy)));
        } else if (segment instanceof EntityKeySegment entityKey) {
            fields.add(member("declaringType", string(entityKey.declaringType())));
            fields.add(member("components", ProjectionNode.array(entityKey.components().stream()
                    .map(component -> value(component, pathRuleMatches, maskingPolicy)).toList())));
        }
        return ProjectionNode.object(fields);
    }

    private static ProjectionNode value(
            ValueSnapshot snapshot,
            boolean pathRuleMatches,
            MaskingPolicy maskingPolicy) {
        return shouldMask(snapshot, pathRuleMatches, maskingPolicy) ? maskedValue() : value(snapshot);
    }

    private static boolean shouldMask(
            ValueSnapshot snapshot,
            boolean pathRuleMatches,
            MaskingPolicy maskingPolicy) {
        return !maskingPolicy.includesSensitiveValues()
                && snapshot.representation() == ValueSnapshot.Representation.EXACT
                && (pathRuleMatches || SensitiveValueDetector.isSensitive(snapshot));
    }

    private static ProjectionNode maskedValue() {
        return ProjectionNode.object(List.of(
                member("representation", string("EXACT")),
                member("type", string("masked")),
                member("value", string("[REDACTED]"))));
    }

    private static ProjectionNode value(ValueSnapshot snapshot) {
        List<ProjectionNode.Member> fields = new ArrayList<>();
        fields.add(member("representation", string(snapshot.representation().name())));
        fields.add(member("type", string(snapshot.typeCode())));
        if (snapshot.representation() == ValueSnapshot.Representation.OMITTED) {
            fields.add(member("reason", string(snapshot.omissionReason().orElseThrow().name())));
        } else if (snapshot.representation() == ValueSnapshot.Representation.SUMMARY) {
            fields.add(member("summary", summary(snapshot)));
        } else if (!snapshot.typeCode().equals("null")) {
            fields.add(member("value", exactValue(snapshot)));
        }
        return ProjectionNode.object(fields);
    }

    private static ProjectionNode exactValue(ValueSnapshot snapshot) {
        List<String> facts = snapshot.canonicalTextFacts();
        return switch (snapshot.typeCode()) {
            case "boolean" -> ProjectionNode.bool(Boolean.parseBoolean(facts.getFirst()));
            case "big-decimal" -> ProjectionNode.object(List.of(
                    member("unscaled", string(facts.get(0))),
                    member("scale", ProjectionNode.number(Integer.parseInt(facts.get(1))))));
            case "enum" -> ProjectionNode.object(List.of(
                    member("declaringType", string(facts.get(0))),
                    member("constant", string(facts.get(1)))));
            case "type-metadata" -> ProjectionNode.object(List.of(
                    member("kind", string(facts.get(0))),
                    member("binaryType", string(facts.get(1)))));
            case "array", "list", "set", "map", "collection" -> ProjectionNode.object(List.of(
                    member("size", string(facts.getFirst()))));
            default -> string(facts.getFirst());
        };
    }

    private static ProjectionNode summary(ValueSnapshot snapshot) {
        List<String> facts = snapshot.canonicalTextFacts();
        return switch (snapshot.typeCode()) {
            case "string" -> ProjectionNode.object(List.of(
                    member("length", string(facts.getFirst()))));
            case "big-integer" -> ProjectionNode.object(List.of(
                    member("precisionBits", string(facts.getFirst()))));
            case "big-decimal" -> ProjectionNode.object(List.of(
                    member("precision", string(facts.get(0))),
                    member("scale", ProjectionNode.number(Integer.parseInt(facts.get(1))))));
            default -> throw new IllegalArgumentException("unsupported ValueSnapshot summary type");
        };
    }

    private static ProjectionNode similarity(SimilarityScore score) {
        return ProjectionNode.object(List.of(
                member("algorithmId", string(score.algorithmId().value())),
                member("value", ProjectionNode.number(score.value()))));
    }

    private static ProjectionNode.Member member(String name, ProjectionNode value) {
        return ProjectionNode.member(name, value);
    }

    private static ProjectionNode string(String value) {
        return ProjectionNode.string(value);
    }

    private static ProjectionNode decimal(long value) {
        return string(Long.toString(value));
    }

    /** 脱敏后change路径分组事实；key只在当前factory调用内存中存活。 */
    private static final class ChangeMaskIdentity {

        /** kind与before/after脱敏路径组成的length-prefix分组键。 */
        private final String key;

        /** 至少一个动态路径fact已被安全token替换。 */
        private final boolean masked;

        private ChangeMaskIdentity(String key, boolean masked) {
            this.key = key;
            this.masked = masked;
        }
    }

    /** 单条typed path的临时脱敏身份，不会进入projection schema。 */
    private static final class PathMaskIdentity {

        /** 脱敏后路径facts的无歧义length-prefix编码。 */
        private final String key;

        /** 当前路径是否替换过动态key/member/component。 */
        private final boolean masked;

        private PathMaskIdentity(String key, boolean masked) {
            this.key = key;
            this.masked = masked;
        }
    }
}
