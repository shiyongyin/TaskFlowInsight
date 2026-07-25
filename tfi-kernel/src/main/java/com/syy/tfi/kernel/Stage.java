package com.syy.tfi.kernel;

import com.syy.tfi.kernel.model.RecordType;
import java.util.Map;

/**
 * 线程封闭的阶段句柄；跨 owner 线程调用只诊断并保持 no-op。
 */
public interface Stage extends AutoCloseable {

    /** 固化一个阶段标量属性；根句柄写入 Session 属性，非标量对象只保留类型事实。 */
    Stage attr(String key, Object value);

    /** 记录一条人读消息。 */
    void message(String text);

    /** 记录一个显式业务变化。 */
    void change(String path, Object before, Object after);

    /** 记录一条错误事实。 */
    void error(String text);

    /** 记录一条带异常类型和消息的错误事实。 */
    void error(String text, Throwable error);

    /**
     * 接纳一条稳定机器事实。
     *
     * @return 已完整接纳时返回 true，输入或预算拒绝时返回 false
     */
    boolean record(RecordType type, String code, String text, Map<String, ?> data);

    /** 返回扣除合法终态预留后的编码预算安全下界。 */
    int remainingEncodedBytes();

    /** 同 owner 线程关闭阶段；重复关闭不产生额外状态变化。 */
    @Override
    void close();
}
