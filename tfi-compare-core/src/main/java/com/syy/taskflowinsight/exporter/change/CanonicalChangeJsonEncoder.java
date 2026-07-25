package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.projection.CompareProjection;
import com.syy.taskflowinsight.tracking.projection.ProjectionNode;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 将prebuilt canonical projection编码为compact schema v1 JSON。
 *
 * <p>实现不依赖Jackson，也不读取raw result；三个入口共享同一字段树、escaping与有界显式frame。</p>
 *
 * @since 4.0.0
 */
public final class CanonicalChangeJsonEncoder {

    /**
     * 编码为无尾换行的compact JSON字符串。
     *
     * @param projection 已完成脱敏与schema校验的不可变projection
     * @return 字段顺序固定的schema v1 JSON
     */
    public String encode(CompareProjection projection) {
        Objects.requireNonNull(projection, "projection");
        return encodeNode(projection.root());
    }

    /**
     * 为同包诊断格式编码projection子树，确保其值表示和escaping不会形成第二套规则。
     */
    String encodeNode(ProjectionNode node) {
        Objects.requireNonNull(node, "node");
        StringBuilder output = new StringBuilder();
        try {
            CanonicalProjectionJsonWriter.write(node, output);
        } catch (IOException exception) {
            throw new IllegalStateException("StringBuilder unexpectedly rejected JSON", exception);
        }
        return output.toString();
    }

    /**
     * 向调用方Writer写入同一compact JSON，成功时flush但不close。
     *
     * @param projection 已完成脱敏与schema校验的不可变projection
     * @param writer 调用方拥有生命周期的字符输出
     * @throws IOException Writer拒绝写入或flush时原样抛出
     */
    public void write(CompareProjection projection, Writer writer) throws IOException {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(writer, "writer");
        CanonicalProjectionJsonWriter.write(projection.root(), writer);
        writer.flush();
    }

    /**
     * 以UTF-8、无BOM写入同一compact JSON，成功时flush但不close。
     *
     * @param projection 已完成脱敏与schema校验的不可变projection
     * @param outputStream 调用方拥有生命周期的字节输出
     * @throws IOException stream拒绝写入或flush时原样抛出
     */
    public void write(CompareProjection projection, OutputStream outputStream) throws IOException {
        Objects.requireNonNull(outputStream, "outputStream");
        OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        write(projection, writer);
    }
}
