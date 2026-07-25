package com.syy.taskflowinsight.ops.compare;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.Objects;

/**
 * 当前 Spring context 的 Compare 对象图健康投影。
 *
 * <p>比较失败是一次业务执行结果，不代表组件失活。因此这里不保存 last result，也不读取历史错误率；
 * 只验证 Runtime、Operations 与已构造 policy 仍组成可用的当前对象图。</p>
 *
 * @since 4.0.0
 */
public final class CompareHealthIndicator implements HealthIndicator {

    /** 当前 context 已冻结且不会在线替换的 Runtime。 */
    private final CompareRuntime runtime;
    /** Spring 最终选择的基础或 observed Operations。 */
    private final CompareOperations operations;

    CompareHealthIndicator(CompareRuntime runtime, CompareOperations operations) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    /**
     * 返回对象图当前可构造性，不把最近一次比较结果解释为存活状态。
     *
     * @return 只含固定组件状态的健康结果
     */
    @Override
    public Health health() {
        if (runtime.engine() == null || runtime.policy() == null || operations == null) {
            return Health.down()
                    .withDetail("runtime", "unavailable")
                    .withDetail("operations", "unavailable")
                    .withDetail("policy", "invalid")
                    .build();
        }
        return Health.up()
                .withDetail("runtime", "available")
                .withDetail("operations", "available")
                .withDetail("policy", "valid")
                .build();
    }
}
