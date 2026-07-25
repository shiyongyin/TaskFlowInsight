package com.syy.tfi.kernel.model;

import java.util.Map;

/**
 * 已固化的只读事实；实现必须在接纳时切断对业务可变对象的引用。
 */
public interface Record {

    /** 返回事实分类。 */
    RecordType type();

    /** 返回稳定机器码。 */
    String code();

    /** 返回可选的人读文本。 */
    String text();

    /** 返回不可修改的结构化数据。 */
    Map<String, Object> data();

    /** 返回事实发生时的 epoch 毫秒。 */
    long atMs();
}
