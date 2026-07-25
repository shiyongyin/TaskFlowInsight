package com.syy.tfi.kernel.context;

import java.util.concurrent.Callable;

/**
 * 不可变的 Session 链接描述；每次执行 wrapper 都创建独立子 Session。
 */
public interface ContextHandle {

    /** 返回保持 callback 身份和执行次数的 Runnable wrapper。 */
    Runnable wrap(Runnable action);

    /** 返回保持受检异常身份和执行次数的 Callable wrapper。 */
    <T> Callable<T> wrap(Callable<T> action);
}
