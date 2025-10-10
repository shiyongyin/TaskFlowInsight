package com.syy.taskflowinsight.spel;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SafeSpELEvaluatorTests {

    @Test
    void evaluatesAllowedCondition_onRootMap() {
        SafeSpELEvaluator eval = new SafeSpELEvaluator();
        boolean ok = eval.evaluateCondition("#methodName == 'doWork'", Map.of("methodName", "doWork"));
        assertThat(ok).isTrue();
    }

    @Test
    void blocksDangerousExpressions_andReturnsFalse() {
        SafeSpELEvaluator eval = new SafeSpELEvaluator();
        boolean allowed = eval.evaluateCondition("T(java.lang.Runtime).getRuntime().exec('whoami') != null", Map.of());
        // blocked -> evaluateExpression returns null, evaluateCondition returns false
        assertThat(allowed).isFalse();
    }

    @Test
    void evaluateString_invalidExpression_returnsEmpty() {
        SafeSpELEvaluator eval = new SafeSpELEvaluator();
        String s = eval.evaluateString("#{unknown}", Map.of());
        assertThat(s).isEmpty();
    }
}

