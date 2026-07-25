package com.exampleevil;

import com.syy.taskflowinsight.spi.DefaultFlowProvider;

/**
 * 与 {@code com.example} 相邻但不属于其包树的测试 Provider。
 *
 * <p>该类型用于证明通配白名单按包段匹配，不能把相同字符串前缀误当成子包关系。
 *
 * @since 4.0.0
 */
public final class AdjacentPackageFlowProvider extends DefaultFlowProvider {

    @Override
    public int priority() {
        return 100;
    }
}
