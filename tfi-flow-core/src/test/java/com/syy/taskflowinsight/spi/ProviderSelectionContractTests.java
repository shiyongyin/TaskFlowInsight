package com.syy.taskflowinsight.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Provider 优先级的 4.0 类型契约与来源内仲裁基线。
 *
 * <p>后续 mutation、trust 与 cache 卡都会改写 Registry 内部状态；本测试先锁定不应随实现结构变化的
 * 来源优先、数值降序、FIFO 和失败降级语义。
 */
class ProviderSelectionContractTests {

    @BeforeEach
    void setUp() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(List.of("com.syy.taskflowinsight.*"));
    }

    @AfterEach
    void tearDown() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
    }

    @Test
    void coreProviderTypesUseOneTypedPriorityContract() {
        assertThat(PrioritizedProvider.class).isAssignableFrom(FlowProvider.class);
        assertThat(PrioritizedProvider.class).isAssignableFrom(ExportProvider.class);

        assertThat(declaredMethodNames(FlowProvider.class)).doesNotContain("priority");
        assertThat(declaredMethodNames(ExportProvider.class)).doesNotContain("priority");
    }

    @Test
    void registeredNegativePriorityStillBeatsServiceLoaderDefault() {
        FlowProvider registered = new StubFlowProvider(-1_000, "registered");
        ProviderRegistry.register(FlowProvider.class, registered);

        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isSameAs(registered);
    }

    @Test
    void equalRegisteredPriorityIsFifo() {
        FlowProvider first = new StubFlowProvider(10, "first");
        FlowProvider second = new StubFlowProvider(10, "second");
        ProviderRegistry.register(FlowProvider.class, first);
        ProviderRegistry.register(FlowProvider.class, second);

        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isSameAs(first);
    }

    @Test
    void typedPriorityFailureUsesZeroWithoutRenderingTheFailure() {
        FlowProvider broken = new StubFlowProvider(0, "broken") {
            @Override
            public int priority() {
                throw new MessageSensitiveError();
            }
        };
        FlowProvider healthy = new StubFlowProvider(1, "healthy");

        assertThatCode(() -> {
            ProviderRegistry.register(FlowProvider.class, broken);
            ProviderRegistry.register(FlowProvider.class, healthy);
        }).doesNotThrowAnyException();
        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isSameAs(healthy);
    }

    @Test
    void untypedGenericProviderDoesNotGainPriorityFromMethodName() {
        UntypedGenericProvider first = new UntypedGenericProvider("first", -100);
        UntypedGenericProvider second = new UntypedGenericProvider("second", 100);
        ProviderRegistry.register(UntypedGenericProvider.class, first);
        ProviderRegistry.register(UntypedGenericProvider.class, second);

        assertThat(ProviderRegistry.lookup(UntypedGenericProvider.class)).isSameAs(first);
    }

    @Test
    void typedGenericProviderParticipatesInPriorityOrdering() {
        TypedGenericProvider first = new TypedGenericProvider("first", -100);
        TypedGenericProvider second = new TypedGenericProvider("second", 100);
        ProviderRegistry.register(TypedGenericProvider.class, first);
        ProviderRegistry.register(TypedGenericProvider.class, second);

        assertThat(ProviderRegistry.lookup(TypedGenericProvider.class)).isSameAs(second);
    }

    private static List<String> declaredMethodNames(Class<?> type) {
        return List.of(type.getDeclaredMethods()).stream().map(Method::getName).toList();
    }

    private static class StubFlowProvider implements FlowProvider {
        private final int priority;
        private final String name;

        StubFlowProvider(int priority, String name) {
            this.priority = priority;
            this.name = name;
        }

        @Override
        public String startSession(String sessionName) {
            return null;
        }

        @Override
        public void endSession() {
        }

        @Override
        public TaskNode startTask(String taskName) {
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

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final class UntypedGenericProvider {
        private final String name;
        private final int priority;

        UntypedGenericProvider(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }

        public int priority() {
            return priority;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class TypedGenericProvider implements PrioritizedProvider {
        private final String name;
        private final int priority;

        private TypedGenericProvider(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class MessageSensitiveError extends AssertionError {
        @Override
        public String getMessage() {
            throw new AssertionError("priority failure message must not be read");
        }

        @Override
        public String toString() {
            throw new AssertionError("priority failure must not be rendered");
        }
    }
}
