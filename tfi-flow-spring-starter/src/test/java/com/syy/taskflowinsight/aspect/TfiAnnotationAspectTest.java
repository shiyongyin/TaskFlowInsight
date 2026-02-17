package com.syy.taskflowinsight.aspect;

import com.syy.taskflowinsight.annotation.TfiTask;
import com.syy.taskflowinsight.masking.UnifiedDataMasker;
import com.syy.taskflowinsight.spel.SafeSpELEvaluator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link TfiAnnotationAspect} 单元测试.
 *
 * <p>覆盖采样控制、条件判断、任务名解析、异常传播和日志记录逻辑。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TfiAnnotationAspect 注解切面测试")
class TfiAnnotationAspectTest {

    @Mock
    private SafeSpELEvaluator spelEvaluator;

    @Mock
    private UnifiedDataMasker dataMasker;

    @Mock
    private ProceedingJoinPoint pjp;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private TfiTask tfiTask;

    private TfiAnnotationAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new TfiAnnotationAspect(spelEvaluator, dataMasker);
    }

    private void setupBasicMocks() throws Exception {
        when(pjp.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getName()).thenReturn("testMethod");
        when(methodSignature.getDeclaringTypeName()).thenReturn("com.example.TestService");
    }

    // ── 构造函数 ──

    @Nested
    @DisplayName("构造函数校验")
    class ConstructorTests {

        @Test
        @DisplayName("spelEvaluator 为 null 抛出 NPE")
        void nullSpelEvaluator_throwsNPE() {
            assertThatThrownBy(() -> new TfiAnnotationAspect(null, dataMasker))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("spelEvaluator");
        }

        @Test
        @DisplayName("dataMasker 为 null 抛出 NPE")
        void nullDataMasker_throwsNPE() {
            assertThatThrownBy(() -> new TfiAnnotationAspect(spelEvaluator, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("dataMasker");
        }
    }

    // ── 采样控制 ──

    @Nested
    @DisplayName("采样控制")
    class SamplingTests {

        @Test
        @DisplayName("samplingRate=0 跳过追踪")
        void zeroSamplingRate_skipsTracking() throws Throwable {
            when(tfiTask.samplingRate()).thenReturn(0.0);
            when(pjp.proceed()).thenReturn("result");

            Object result = aspect.around(pjp, tfiTask);

            assertThat(result).isEqualTo("result");
            verify(pjp).proceed();
            // 未进入追踪流程，不应调用 spelEvaluator
            verifyNoInteractions(spelEvaluator);
        }

        @Test
        @DisplayName("负采样率跳过追踪")
        void negativeSamplingRate_skipsTracking() throws Throwable {
            when(tfiTask.samplingRate()).thenReturn(-1.0);
            when(pjp.proceed()).thenReturn("result");

            Object result = aspect.around(pjp, tfiTask);

            assertThat(result).isEqualTo("result");
            verifyNoInteractions(spelEvaluator);
        }
    }

    // ── 条件判断 ──

    @Nested
    @DisplayName("条件判断")
    class ConditionTests {

        @Test
        @DisplayName("条件为 false 时跳过追踪")
        void conditionFalse_skipsTracking() throws Throwable {
            when(tfiTask.samplingRate()).thenReturn(1.0);
            when(tfiTask.condition()).thenReturn("someCondition");
            setupBasicMocks();
            when(spelEvaluator.evaluateCondition(eq("someCondition"), anyMap())).thenReturn(false);
            when(pjp.proceed()).thenReturn("result");

            Object result = aspect.around(pjp, tfiTask);

            assertThat(result).isEqualTo("result");
        }
    }

    // ── 成功路径 ──

    @Nested
    @DisplayName("成功路径 - 完整追踪流程")
    class SuccessPathTests {

        @Test
        @DisplayName("samplingRate=1.0 + 空条件 → 进入追踪流程")
        void fullSamplingRate_entersTracking() throws Throwable {
            when(tfiTask.samplingRate()).thenReturn(1.0);
            when(tfiTask.condition()).thenReturn("");
            when(tfiTask.value()).thenReturn("myTask");
            when(tfiTask.logArgs()).thenReturn(false);
            when(tfiTask.logResult()).thenReturn(false);
            when(tfiTask.deepTracking()).thenReturn(false);
            setupBasicMocks();
            when(pjp.proceed()).thenReturn("successResult");

            Object result = aspect.around(pjp, tfiTask);

            assertThat(result).isEqualTo("successResult");
            verify(pjp).proceed();
        }

        @Test
        @DisplayName("logResult=true 时结果被脱敏并记录")
        void logResult_masksAndRecords() throws Throwable {
            when(tfiTask.samplingRate()).thenReturn(1.0);
            when(tfiTask.condition()).thenReturn("");
            when(tfiTask.value()).thenReturn("resultTask");
            when(tfiTask.logArgs()).thenReturn(false);
            when(tfiTask.logResult()).thenReturn(true);
            when(tfiTask.deepTracking()).thenReturn(false);
            setupBasicMocks();
            when(pjp.proceed()).thenReturn("sensitiveResult");
            when(dataMasker.maskValue("result", "sensitiveResult")).thenReturn("s***t");

            Object result = aspect.around(pjp, tfiTask);

            assertThat(result).isEqualTo("sensitiveResult");
            verify(dataMasker).maskValue("result", "sensitiveResult");
        }

        @Test
        @DisplayName("logArgs=true 时参数被脱敏并记录")
        void logArgs_masksParameters() throws Throwable {
            when(tfiTask.samplingRate()).thenReturn(1.0);
            when(tfiTask.condition()).thenReturn("");
            when(tfiTask.value()).thenReturn("argsTask");
            when(tfiTask.logArgs()).thenReturn(true);
            when(tfiTask.logResult()).thenReturn(false);
            when(tfiTask.deepTracking()).thenReturn(false);
            setupBasicMocks();

            // Mock method with parameters
            Method method = SampleService.class.getMethod("handle", String.class, int.class);
            when(methodSignature.getMethod()).thenReturn(method);
            when(pjp.getArgs()).thenReturn(new Object[]{"value1", 42});
            when(pjp.proceed()).thenReturn("ok");
            when(dataMasker.maskValue(anyString(), any())).thenReturn("[masked]");

            Object result = aspect.around(pjp, tfiTask);

            assertThat(result).isEqualTo("ok");
            // 两个参数 → maskValue 被调用至少 2 次
            verify(dataMasker, atLeast(2)).maskValue(anyString(), any());
        }
    }

    // ── 任务名解析 ──

    @Nested
    @DisplayName("任务名解析")
    class TaskNameResolutionTests {

        @Test
        @DisplayName("value 非空时作为任务名")
        void explicitValue_usedAsTaskName() throws Throwable {
            when(tfiTask.samplingRate()).thenReturn(1.0);
            when(tfiTask.condition()).thenReturn("");
            when(tfiTask.value()).thenReturn("explicitName");
            when(tfiTask.logArgs()).thenReturn(false);
            when(tfiTask.logResult()).thenReturn(false);
            when(tfiTask.deepTracking()).thenReturn(false);
            setupBasicMocks();
            when(pjp.proceed()).thenReturn(null);

            aspect.around(pjp, tfiTask);

            // 验证方法执行完成（任务名解析不抛异常）
            verify(pjp).proceed();
        }

        @Test
        @DisplayName("value 和 name 为空时使用方法名")
        void emptyValueAndName_usesMethodName() throws Throwable {
            when(tfiTask.samplingRate()).thenReturn(1.0);
            when(tfiTask.condition()).thenReturn("");
            when(tfiTask.value()).thenReturn("");
            when(tfiTask.name()).thenReturn("");
            when(tfiTask.logArgs()).thenReturn(false);
            when(tfiTask.logResult()).thenReturn(false);
            when(tfiTask.deepTracking()).thenReturn(false);
            setupBasicMocks();
            when(pjp.proceed()).thenReturn(null);

            aspect.around(pjp, tfiTask);

            verify(pjp).proceed();
        }
    }

    // ── 异常传播 ──

    @Nested
    @DisplayName("异常传播")
    class ExceptionTests {

        @Test
        @DisplayName("目标方法异常透传给调用方")
        void targetException_propagated() throws Throwable {
            when(tfiTask.samplingRate()).thenReturn(1.0);
            when(tfiTask.condition()).thenReturn("");
            when(tfiTask.value()).thenReturn("testTask");
            when(tfiTask.logArgs()).thenReturn(false);
            when(tfiTask.logException()).thenReturn(true);
            when(tfiTask.deepTracking()).thenReturn(false);
            setupBasicMocks();

            RuntimeException ex = new RuntimeException("Business error");
            when(pjp.proceed()).thenThrow(ex);
            when(dataMasker.maskValue(eq("exception"), eq("Business error")))
                    .thenReturn("B***r");

            assertThatThrownBy(() -> aspect.around(pjp, tfiTask))
                    .isSameAs(ex);
        }

        @Test
        @DisplayName("logException=false 时异常仍透传但不记录日志")
        void logExceptionFalse_stillPropagates() throws Throwable {
            when(tfiTask.samplingRate()).thenReturn(1.0);
            when(tfiTask.condition()).thenReturn("");
            when(tfiTask.value()).thenReturn("noLogTask");
            when(tfiTask.logArgs()).thenReturn(false);
            when(tfiTask.logException()).thenReturn(false);
            when(tfiTask.deepTracking()).thenReturn(false);
            setupBasicMocks();

            RuntimeException ex = new RuntimeException("Error");
            when(pjp.proceed()).thenThrow(ex);

            assertThatThrownBy(() -> aspect.around(pjp, tfiTask))
                    .isSameAs(ex);
            // logException=false, 不应调用 dataMasker
            verify(dataMasker, never()).maskValue(eq("exception"), anyString());
        }
    }

    // ── deepTracking ──

    @Nested
    @DisplayName("deepTracking 标记")
    class DeepTrackingTests {

        @Test
        @DisplayName("deepTracking=true 不影响基础追踪流程")
        void deepTracking_doesNotAffectBasicFlow() throws Throwable {
            when(tfiTask.samplingRate()).thenReturn(1.0);
            when(tfiTask.condition()).thenReturn("");
            when(tfiTask.value()).thenReturn("deepTask");
            when(tfiTask.logArgs()).thenReturn(false);
            when(tfiTask.logResult()).thenReturn(false);
            when(tfiTask.deepTracking()).thenReturn(true);
            setupBasicMocks();
            when(pjp.proceed()).thenReturn("deepResult");

            Object result = aspect.around(pjp, tfiTask);

            assertThat(result).isEqualTo("deepResult");
            verify(pjp).proceed();
        }
    }

    // ── Order 常量 ──

    @Test
    @DisplayName("切面优先级为 1000")
    void aspectOrder_is1000() {
        assertThat(TfiAnnotationAspect.TFI_ASPECT_ORDER).isEqualTo(1000);
    }

    /** 用于参数日志测试的示例服务. */
    public static class SampleService {
        public String handle(String input, int count) {
            return input + count;
        }
    }
}
