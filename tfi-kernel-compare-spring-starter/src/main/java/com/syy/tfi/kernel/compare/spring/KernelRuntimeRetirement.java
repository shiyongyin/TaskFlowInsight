package com.syy.tfi.kernel.compare.spring;

import com.syy.tfi.kernel.KernelRuntime;
import java.util.Objects;
import org.springframework.beans.factory.DisposableBean;

/**
 * 将当前 ApplicationContext 独占的 Kernel Runtime 接入 Spring 销毁阶段。
 *
 * <p>该 Bean 不管理 Sink 或线程；销毁先后由显式 dependent-bean 关系保证，重复关闭由 Runtime 幂等合同吸收。</p>
 */
final class KernelRuntimeRetirement implements DisposableBean {

    /** 当前 context 独占且必须在 Sink 销毁前退役的 Runtime。 */
    private final KernelRuntime runtime;

    KernelRuntimeRetirement(KernelRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    /** 只关闭 Runtime，不引入 flush、executor 或额外 shutdown 协调。 */
    @Override
    public void destroy() {
        runtime.close();
    }
}
