package com.syy.taskflowinsight.spi.fixtures;

import com.syy.taskflowinsight.spi.PrioritizedProvider;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 验证 ServiceLoader 部分发现后失败时不得暴露未提交候选的测试 SPI。
 *
 * <p>该类型仅存在于 test scope；服务文件先构造合法实现，再通过后续实现的构造器确定性触发
 * partial failure。</p>
 *
 * @since 4.0.0
 */
public interface PartialFailureProvider extends PrioritizedProvider {

    /**
     * 返回 fixture 标识。
     *
     * @return 固定 fixture 标识
     */
    String id();

    /** 合法的首个 ServiceLoader 候选，用构造计数验证失败扫描不会在同一 epoch 重放。 */
    final class Valid implements PartialFailureProvider {

        private static final AtomicInteger constructions = new AtomicInteger();

        /** ServiceLoader 使用的公共无参构造器。 */
        public Valid() {
            constructions.incrementAndGet();
        }

        /** 清空成功构造计数，隔离失败缓存测试。 */
        public static void reset() {
            constructions.set(0);
        }

        /**
         * 返回成功进入构造器的次数。
         *
         * @return 自上次 reset 后的构造次数
         */
        public static int constructionCount() {
            return constructions.get();
        }

        @Override
        public String id() {
            return "valid-prefix";
        }
    }

    /** 构造阶段固定失败的后续候选，确保前缀候选不会被部分发布。 */
    final class Failing implements PartialFailureProvider {

        /** ServiceLoader 调用后固定失败。 */
        public Failing() {
            throw new IllegalStateException("Expected partial Provider construction failure");
        }

        @Override
        public String id() {
            return "unreachable";
        }
    }
}
