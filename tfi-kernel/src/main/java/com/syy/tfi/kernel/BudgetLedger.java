package com.syy.tfi.kernel;

import com.syy.tfi.kernel.model.RecordType;

/** 以最终 JSON UTF-8 bytes 为单位的保守账本，不在导出期回调业务值。 */
final class BudgetLedger {
    /** 非负 {@code long} 持续时长在 JSON 中可能占用的最大 ASCII 字节数。 */
    private static final int MAX_DURATION_BYTES = 19;
    /** 最长合法终态 {@code ABANDONED} 的 JSON 字符串字节数，用于保守预留状态空间。 */
    private static final int TERMINAL_STATUS_BYTES = DataCodec.stringBytes("ABANDONED");
    /** 全部不完整原因同时输出为 JSON 数组时的字节数，用于最坏情况预留。 */
    private static final int ALL_REASONS_BYTES = allReasonsBytes();

    /** 单个 Session 最终 canonical JSON 可占用的 UTF-8 字节上限。 */
    private final int maximum;
    /** 已原子提交或为合法终态预留的 canonical JSON UTF-8 字节数。 */
    private int reserved;
    /** 是否已经发生 Session 预算拒绝；置位后禁止继续接纳任何片段。 */
    private boolean exhausted;

    BudgetLedger(String sessionId, String parentSessionId, String name, long startMs, int maximum) {
        this.maximum = maximum;
        this.reserved = sessionBaseBytes(sessionId, parentSessionId, name, startMs);
        if (reserved > maximum) {
            throw new IllegalStateException("session budget cannot hold the fixed envelope");
        }
    }

    boolean accept(int fragmentBytes) {
        if (exhausted) {
            return false;
        }
        if (fragmentBytes <= maximum - reserved) {
            reserved += fragmentBytes;
            return true;
        }
        exhausted = true;
        return false;
    }

    boolean replace(int oldFragmentBytes, int newFragmentBytes) {
        if (exhausted) {
            return false;
        }
        int delta = newFragmentBytes - oldFragmentBytes;
        if (delta <= maximum - reserved) {
            reserved += delta;
            return true;
        }
        exhausted = true;
        return false;
    }

    int remaining() {
        return exhausted ? 0 : maximum - reserved;
    }

    boolean exhausted() {
        return exhausted;
    }

    static int stageBytes(String name, long startMs, boolean needsComma) {
        int bytes = needsComma ? 1 : 0;
        bytes += ascii("{\"name\":") + DataCodec.stringBytes(name);
        bytes += ascii(",\"status\":") + TERMINAL_STATUS_BYTES;
        bytes += ascii(",\"startMs\":") + numberBytes(startMs);
        bytes += ascii(",\"durMs\":") + MAX_DURATION_BYTES;
        bytes += ascii(",\"attrs\":{},\"records\":[],\"children\":[]}");
        return bytes;
    }

    static int recordBytes(
            RecordType type, String code, String text, int dataBytes, long atMs, boolean needsComma) {
        int bytes = needsComma ? 1 : 0;
        bytes += ascii("{\"type\":") + DataCodec.stringBytes(type.name());
        bytes += ascii(",\"code\":") + DataCodec.stringBytes(code);
        bytes += ascii(",\"text\":") + (text == null ? 4 : DataCodec.stringBytes(text));
        bytes += ascii(",\"data\":") + dataBytes;
        bytes += ascii(",\"atMs\":") + numberBytes(atMs) + 1;
        return bytes;
    }

    static int attrBytes(String key, int valueBytes, boolean needsComma) {
        return (needsComma ? 1 : 0) + DataCodec.stringBytes(key) + 1 + valueBytes;
    }

    private static int sessionBaseBytes(String id, String parentId, String name, long startMs) {
        int bytes = ascii("{\"schema\":\"tfi-flow/1\",\"sessionId\":") + DataCodec.stringBytes(id);
        bytes += ascii(",\"parentSessionId\":") + (parentId == null ? 4 : DataCodec.stringBytes(parentId));
        bytes += ascii(",\"name\":") + DataCodec.stringBytes(name);
        bytes += ascii(",\"status\":") + TERMINAL_STATUS_BYTES;
        bytes += ascii(",\"startMs\":") + numberBytes(startMs);
        bytes += ascii(",\"durMs\":") + MAX_DURATION_BYTES;
        bytes += ascii(",\"truncated\":false,\"incompleteReasons\":") + ALL_REASONS_BYTES;
        bytes += ascii(",\"attrs\":{},\"root\":");
        bytes += stageBytes(name, startMs, false) + 1;
        return bytes;
    }

    private static int allReasonsBytes() {
        int bytes = 2;
        IncompleteReason[] reasons = IncompleteReason.values();
        for (int index = 0; index < reasons.length; index++) {
            bytes += (index == 0 ? 0 : 1) + DataCodec.stringBytes(reasons[index].name());
        }
        return bytes;
    }

    private static int numberBytes(long value) {
        return Long.toString(value).length();
    }

    private static int ascii(String value) {
        return value.length();
    }
}
