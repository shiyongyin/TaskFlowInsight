/**
 * 线程本地上下文管理.
 *
 * <p>提供 SafeContextManager、ManagedThreadContext、ZeroLeakThreadLocalManager 等组件，
 * 实现 ThreadLocal 的零泄漏保护与异步上下文传播，保障流程追踪在并发场景下的正确性。
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
package com.syy.taskflowinsight.context;
