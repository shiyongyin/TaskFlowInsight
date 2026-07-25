package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.projection.CompareProjection;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

/**
 * canonical Compare projection的JSON导出入口。
 *
 * <p>三个入口共享同一encoder；本类型不接收raw result、业务对象或可变配置，避免形成第二份schema与masking。</p>
 *
 * @since 4.0.0
 */
public class ChangeJsonExporter {

    /** 共享无状态encoder，字段顺序、escaping和hard budget只维护一份。 */
    private static final CanonicalChangeJsonEncoder ENCODER = new CanonicalChangeJsonEncoder();

    /**
     * 编码已经脱敏的canonical projection。
     *
     * @param projection schema v1字段树，不允许为null
     * @return compact、无尾换行的JSON
     */
    public String format(CompareProjection projection) {
        return ENCODER.encode(projection);
    }

    /**
     * 向调用方Writer写入compact JSON，成功时flush但不close。
     *
     * @param projection schema v1字段树，不允许为null
     * @param writer 调用方拥有生命周期的字符输出
     * @throws IOException 写入或flush失败时原样抛出
     */
    public void write(CompareProjection projection, Writer writer) throws IOException {
        ENCODER.write(projection, writer);
    }

    /**
     * 以UTF-8无BOM写入compact JSON，成功时flush但不close。
     *
     * @param projection schema v1字段树，不允许为null
     * @param outputStream 调用方拥有生命周期的字节输出
     * @throws IOException 写入或flush失败时原样抛出
     */
    public void write(CompareProjection projection, OutputStream outputStream) throws IOException {
        ENCODER.write(projection, outputStream);
    }
}
