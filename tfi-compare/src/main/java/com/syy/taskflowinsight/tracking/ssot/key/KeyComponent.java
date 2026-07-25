package com.syy.taskflowinsight.tracking.ssot.key;

import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 动态地址中的单个exact scalar键分量。
 *
 * <p>分量只保留{@link ValueSnapshot}的封闭typed facts和length-prefix wire，避免地址身份调用业务
 * {@code toString/hashCode/equals}，也避免分隔符产生编码碰撞。</p>
 *
 * @since 4.0.0
 */
public final class KeyComponent implements Comparable<KeyComponent> {

    /** 不持有业务对象的exact scalar事实，供typed path复用。 */
    private final ValueSnapshot snapshot;

    /** type code与全部canonical facts组成的有界length-prefix bytes。 */
    private final byte[] wire;

    private KeyComponent(ValueSnapshot snapshot, byte[] wire) {
        this.snapshot = snapshot;
        this.wire = wire;
    }

    /**
     * 从业务值捕获有界地址分量。
     *
     * <p>未知对象只会被捕获为type metadata并返回empty，不执行展示、哈希或相等回调。</p>
     *
     * @param value closed scalar值；允许null
     * @param maxEncodedBytes 当前分量允许使用的最大wire字节数
     * @return 可寻址的exact scalar分量；不支持、编码非法或超限时为空
     */
    public static Optional<KeyComponent> tryCapture(Object value, int maxEncodedBytes) {
        validateLimit(maxEncodedBytes);
        ValueSnapshot snapshot = ValueSnapshot.captureSupported(value, maxEncodedBytes);
        return fromSnapshot(snapshot, maxEncodedBytes);
    }

    /**
     * 将已捕获的值事实转换为地址分量。
     *
     * @param snapshot 不持有业务对象的值事实
     * @param maxEncodedBytes 当前分量允许使用的最大wire字节数
     * @return exact scalar的有界分量；summary、omitted、非scalar或超限时为空
     */
    public static Optional<KeyComponent> fromSnapshot(
            ValueSnapshot snapshot,
            int maxEncodedBytes) {
        Objects.requireNonNull(snapshot, "snapshot");
        validateLimit(maxEncodedBytes);
        if (!snapshot.isExactScalar()) {
            return Optional.empty();
        }
        List<String> facts = new ArrayList<>(1 + snapshot.canonicalTextFacts().size());
        facts.add(snapshot.typeCode());
        facts.addAll(snapshot.canonicalTextFacts());
        return encodeFacts(facts, maxEncodedBytes)
                .map(bytes -> new KeyComponent(snapshot, bytes));
    }

    /** @return 不持有业务对象的exact scalar事实 */
    public ValueSnapshot snapshot() {
        return snapshot;
    }

    /** @return 当前分量实际占用的wire字节数 */
    public int encodedLength() {
        return wire.length;
    }

    /**
     * 按unsigned bytes比较地址分量，不受locale或JVM hash seed影响。
     *
     * @param other 待比较的键分量
     * @return unsigned canonical wire顺序
     */
    @Override
    public int compareTo(KeyComponent other) {
        return compareUnsigned(wire, Objects.requireNonNull(other, "other").wire);
    }

    /**
     * 仅按typed canonical wire判断分量相等，不触发原业务值的相等回调。
     *
     * @param other 待比较对象
     * @return 对方也是相同wire的键分量时为true
     */
    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof KeyComponent component && Arrays.equals(wire, component.wire);
    }

    /**
     * 从canonical wire生成哈希，保持与{@link #equals(Object)}一致且不调用业务对象。
     *
     * @return 键分量的稳定内容哈希
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(wire);
    }

    /** @return 不包含地址原始facts的安全结构摘要 */
    @Override
    public String toString() {
        return "KeyComponent{encodedLength=" + wire.length + '}';
    }

    byte[] wireCopy() {
        return wire.clone();
    }

    static Optional<byte[]> encodeFacts(List<String> facts, int maxEncodedBytes) {
        List<byte[]> encodedFacts = new ArrayList<>(facts.size());
        long encodedLength = Integer.BYTES;
        try {
            for (String fact : facts) {
                byte[] encoded = encodeUtf8(Objects.requireNonNull(fact, "fact"));
                encodedLength += Integer.BYTES + encoded.length;
                if (encodedLength > maxEncodedBytes) {
                    return Optional.empty();
                }
                encodedFacts.add(encoded);
            }
        } catch (CharacterCodingException exception) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.allocate((int) encodedLength);
        buffer.putInt(encodedFacts.size());
        for (byte[] encodedFact : encodedFacts) {
            buffer.putInt(encodedFact.length).put(encodedFact);
        }
        return Optional.of(buffer.array());
    }

    static int compareUnsigned(byte[] left, byte[] right) {
        return Arrays.compareUnsigned(left, right);
    }

    private static byte[] encodeUtf8(String value) throws CharacterCodingException {
        ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        return bytes;
    }

    private static void validateLimit(int maxEncodedBytes) {
        if (maxEncodedBytes < 1) {
            throw new IllegalArgumentException("maxEncodedBytes must be positive");
        }
    }
}
