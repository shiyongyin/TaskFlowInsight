package com.syy.taskflowinsight.spi.fixtures;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 为单次 ServiceLoader scan 提供 65 个不同声明的测试 fixture。
 *
 * @since 4.0.0
 */
public final class OverCapacityFlowProviders {

    private static final AtomicInteger constructions = new AtomicInteger();

    private OverCapacityFlowProviders() {
    }

    /** 清空构造计数，隔离各容量测试。 */
    public static void reset() {
        constructions.set(0);
    }

    /** @return 自上次 reset 后实际进入 Provider 构造器的次数 */
    public static int constructionCount() {
        return constructions.get();
    }

    private abstract static class Base implements OverCapacityLookupProvider {

        private Base() {
            constructions.incrementAndGet();
        }

        @Override
        public String startSession(String name) {
            return getClass().getSimpleName();
        }

        @Override
        public void endSession() {
        }

        @Override
        public TaskNode startTask(String name) {
            return null;
        }

        @Override
        public void endTask() {
        }

        @Override
        public Session currentSession() {
            return null;
        }

        @Override
        public TaskNode currentTask() {
            return null;
        }

        @Override
        public void message(String content, String label) {
        }
    }

    public static final class Provider01 extends Base {
    }

    public static final class Provider02 extends Base {
    }

    public static final class Provider03 extends Base {
    }

    public static final class Provider04 extends Base {
    }

    public static final class Provider05 extends Base {
    }

    public static final class Provider06 extends Base {
    }

    public static final class Provider07 extends Base {
    }

    public static final class Provider08 extends Base {
    }

    public static final class Provider09 extends Base {
    }

    public static final class Provider10 extends Base {
    }

    public static final class Provider11 extends Base {
    }

    public static final class Provider12 extends Base {
    }

    public static final class Provider13 extends Base {
    }

    public static final class Provider14 extends Base {
    }

    public static final class Provider15 extends Base {
    }

    public static final class Provider16 extends Base {
    }

    public static final class Provider17 extends Base {
    }

    public static final class Provider18 extends Base {
    }

    public static final class Provider19 extends Base {
    }

    public static final class Provider20 extends Base {
    }

    public static final class Provider21 extends Base {
    }

    public static final class Provider22 extends Base {
    }

    public static final class Provider23 extends Base {
    }

    public static final class Provider24 extends Base {
    }

    public static final class Provider25 extends Base {
    }

    public static final class Provider26 extends Base {
    }

    public static final class Provider27 extends Base {
    }

    public static final class Provider28 extends Base {
    }

    public static final class Provider29 extends Base {
    }

    public static final class Provider30 extends Base {
    }

    public static final class Provider31 extends Base {
    }

    public static final class Provider32 extends Base {
    }

    public static final class Provider33 extends Base {
    }

    public static final class Provider34 extends Base {
    }

    public static final class Provider35 extends Base {
    }

    public static final class Provider36 extends Base {
    }

    public static final class Provider37 extends Base {
    }

    public static final class Provider38 extends Base {
    }

    public static final class Provider39 extends Base {
    }

    public static final class Provider40 extends Base {
    }

    public static final class Provider41 extends Base {
    }

    public static final class Provider42 extends Base {
    }

    public static final class Provider43 extends Base {
    }

    public static final class Provider44 extends Base {
    }

    public static final class Provider45 extends Base {
    }

    public static final class Provider46 extends Base {
    }

    public static final class Provider47 extends Base {
    }

    public static final class Provider48 extends Base {
    }

    public static final class Provider49 extends Base {
    }

    public static final class Provider50 extends Base {
    }

    public static final class Provider51 extends Base {
    }

    public static final class Provider52 extends Base {
    }

    public static final class Provider53 extends Base {
    }

    public static final class Provider54 extends Base {
    }

    public static final class Provider55 extends Base {
    }

    public static final class Provider56 extends Base {
    }

    public static final class Provider57 extends Base {
    }

    public static final class Provider58 extends Base {
    }

    public static final class Provider59 extends Base {
    }

    public static final class Provider60 extends Base {
    }

    public static final class Provider61 extends Base {
    }

    public static final class Provider62 extends Base {
    }

    public static final class Provider63 extends Base {
    }

    public static final class Provider64 extends Base {
    }

    public static final class Provider65 extends Base {
    }
}
