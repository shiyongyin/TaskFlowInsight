package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;

/**
 * 变更值的“种类”分类工具（用于 {@code ChangeRecord.valueKind}）。
 *
 * <p>分类只用于把 canonical 结果映射到旧 {@code ChangeRecord} 展示字段，
 * 不参与比较或路径身份。
 * 独立为包内函数可以防止兼容投影再次渗入唯一 differ。</p>
 *
 * <p>包级可见即可：分类语义仅服务于 detector 内部建模，无需对外暴露。</p>
 */
final class ValueKinds {

    private ValueKinds() {
    }

    /**
     * 从canonical有界事实生成旧分类标签。
     *
     * <p>深层变化不会再持有原始业务对象，因此兼容投影必须读取同一个snapshot事实；
     * 回退到{@code null}会把已知类型错误发布为NULL。</p>
     *
     * @param snapshot canonical值事实，不能为空
     * @return 与旧{@code ChangeRecord.valueKind}闭集一致的分类标签
     */
    static String classifySnapshot(ValueSnapshot snapshot) {
        return switch (snapshot.typeCode()) {
            case "null" -> "NULL";
            case "string", "character" -> "STRING";
            case "byte", "short", "int", "long", "big-integer", "big-decimal", "float", "double" -> "NUMBER";
            case "boolean" -> "BOOLEAN";
            case "date", "instant", "local-date-time", "local-date", "duration" -> "DATE";
            case "enum" -> "ENUM";
            case "list", "set", "collection" -> "COLLECTION";
            case "map" -> "MAP";
            case "array" -> "ARRAY";
            default -> "OTHER";
        };
    }
}
