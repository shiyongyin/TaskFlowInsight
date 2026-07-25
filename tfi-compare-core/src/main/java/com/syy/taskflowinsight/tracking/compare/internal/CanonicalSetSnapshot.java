package com.syy.taskflowinsight.tracking.compare.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 未标注复杂Set成员的请求内canonical分组事实。
 *
 * <p>成员签名来自完整typed snapshot，不进入公共path，也不保存业务对象。相同签名出现多次时，
 * 内容分组仍可证明数量差异，但一对一配对具有歧义，调用方必须同时发布W2201。</p>
 */
final class CanonicalSetSnapshot {

    /** 按canonical token排序的复杂成员完整签名。 */
    private final List<List<String>> memberSignatures;

    /** 所有复杂成员是否完整捕获，决定签名差异能否证明业务差异。 */
    private final boolean complete;

    /** 重复完整签名或不完整成员使一对一配对不可证明。 */
    private final boolean pairingAmbiguous;

    private CanonicalSetSnapshot(
            List<List<String>> memberSignatures,
            boolean complete,
            boolean pairingAmbiguous) {
        this.memberSignatures = memberSignatures;
        this.complete = complete;
        this.pairingAmbiguous = pairingAmbiguous;
    }

    static CanonicalSetSnapshot from(List<MemberSnapshot> members) {
        Objects.requireNonNull(members, "members");
        List<List<String>> signatures = new ArrayList<>(members.size());
        boolean complete = true;
        for (MemberSnapshot member : members) {
            signatures.add(member.signature());
            complete &= member.complete();
        }
        signatures.sort(CanonicalSetSnapshot::compareTokens);
        boolean duplicate = false;
        for (int index = 1; index < signatures.size(); index++) {
            if (signatures.get(index - 1).equals(signatures.get(index))) {
                duplicate = true;
                break;
            }
        }
        return new CanonicalSetSnapshot(
                List.copyOf(signatures),
                complete,
                duplicate || !complete);
    }

    boolean hasComplexMembers() {
        return !memberSignatures.isEmpty();
    }

    boolean complete() {
        return complete;
    }

    boolean pairingAmbiguous() {
        return pairingAmbiguous;
    }

    boolean canProveDifference(CanonicalSetSnapshot other) {
        Objects.requireNonNull(other, "other");
        return complete && other.complete
                && !memberSignatures.equals(other.memberSignatures);
    }

    List<String> canonicalTokens() {
        List<String> tokens = new ArrayList<>();
        tokens.add(Boolean.toString(complete));
        tokens.add(Boolean.toString(pairingAmbiguous));
        tokens.add(Integer.toString(memberSignatures.size()));
        for (List<String> signature : memberSignatures) {
            tokens.add(Integer.toString(signature.size()));
            tokens.addAll(signature);
        }
        return List.copyOf(tokens);
    }

    private static int compareTokens(List<String> left, List<String> right) {
        int commonSize = Math.min(left.size(), right.size());
        for (int index = 0; index < commonSize; index++) {
            int compared = Comparator.<String>naturalOrder()
                    .compare(left.get(index), right.get(index));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    /**
     * 单个复杂成员的完整签名与捕获状态。
     *
     * @param signature 不包含业务对象或公共staging path的canonical token
     * @param complete 当前成员及其嵌套复杂Set是否完整捕获
     */
    record MemberSnapshot(List<String> signature, boolean complete) {

        MemberSnapshot {
            signature = List.copyOf(signature);
        }
    }
}
