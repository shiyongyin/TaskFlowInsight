package com.syy.taskflowinsight.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** 固定嵌入式 Compare Port 的签名、默认实现和唯一 Runtime owner。 */
class CompareOperationsContractTest {

    @Test
    void portKeepsExactlyTwoCompareMethods() {
        Set<String> signatures = Arrays.stream(CompareOperations.class.getDeclaredMethods())
                .map(CompareOperationsContractTest::signature)
                .collect(Collectors.toSet());

        assertThat(signatures).containsExactlyInAnyOrder(
                "compare(java.lang.Object,java.lang.Object)->"
                        + "com.syy.taskflowinsight.tracking.compare.CompareResult",
                "compare(java.lang.Object,java.lang.Object,"
                        + "com.syy.taskflowinsight.tracking.compare.CompareOptions)->"
                        + "com.syy.taskflowinsight.tracking.compare.CompareResult");
    }

    @Test
    void runtimeOwnsTheDefaultFinalEngineImplementation() {
        CompareRuntime runtime = CompareRuntime.builder().build();

        assertThat(CompareOperations.class).isAssignableFrom(CompareEngine.class);
        assertThat(Modifier.isFinal(CompareEngine.class.getModifiers())).isTrue();
        assertThat(runtime.engine()).isInstanceOf(CompareOperations.class);
        assertThat(runtime.engine()).isSameAs(runtime.engine());
        assertThat(CompareOperationsDecorator.class.getInterfaces())
                .containsExactly(CompareOperations.class);
    }

    private static String signature(Method method) {
        String parameters = Arrays.stream(method.getParameterTypes())
                .map(Class::getName)
                .collect(Collectors.joining(","));
        return method.getName() + "(" + parameters + ")->" + method.getReturnType().getName();
    }
}
