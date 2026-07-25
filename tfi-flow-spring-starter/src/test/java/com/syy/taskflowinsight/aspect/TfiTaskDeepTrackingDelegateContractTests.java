package com.syy.taskflowinsight.aspect;

import com.syy.taskflowinsight.annotation.TfiTask;
import com.syy.taskflowinsight.api.TfiFlow;
import com.syy.taskflowinsight.masking.UnifiedDataMasker;
import com.syy.taskflowinsight.spel.SafeSpELEvaluator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Flow深度追踪hook的模块边界合同。
 *
 * <p>测试只从Flow advice公开入口观察业务调用次数，避免把Compare实现或stage内部状态变成Flow测试依赖。</p>
 */
class TfiTaskDeepTrackingDelegateContractTests {

    @AfterEach
    void restoreFlow() {
        TfiFlow.clear();
        TfiFlow.enable();
    }

    @Test
    void noDelegateProceedsExactlyOnceAndPreservesBusinessValue() throws Throwable {
        SafeSpELEvaluator evaluator = mock(SafeSpELEvaluator.class);
        UnifiedDataMasker masker = mock(UnifiedDataMasker.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<TfiTaskDeepTrackingDelegate> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenReturn(Stream.empty());

        TfiAnnotationAspect aspect = new TfiAnnotationAspect(evaluator, masker, provider);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        TfiTask annotation = mock(TfiTask.class);
        Object businessValue = new Object();
        when(annotation.samplingRate()).thenReturn(1.0);
        when(annotation.condition()).thenReturn("");
        when(annotation.value()).thenReturn("tracked-task");
        when(annotation.deepTracking()).thenReturn(true);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.proceed()).thenReturn(businessValue);
        TfiFlow.enable();

        Object result = aspect.around(joinPoint, annotation);

        assertThat(result).isSameAs(businessValue);
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void singleDelegateRunsInsideStageWithCopiedArgumentsAndOneInvocation() throws Throwable {
        SafeSpELEvaluator evaluator = mock(SafeSpELEvaluator.class);
        UnifiedDataMasker masker = mock(UnifiedDataMasker.class);
        AtomicInteger delegateCalls = new AtomicInteger();
        AtomicReference<Object[]> observedArguments = new AtomicReference<>();
        AtomicReference<Method> observedMethod = new AtomicReference<>();
        Object argument = new Object();
        Object[] originalArguments = {argument};
        Object businessValue = new Object();
        Method declaredMethod = SampleService.class.getDeclaredMethod("handle", Object.class);
        TfiTaskDeepTrackingDelegate delegate = (annotation, method, arguments, stage, invocation) -> {
            delegateCalls.incrementAndGet();
            observedMethod.set(method);
            observedArguments.set(arguments);
            assertThat(stage).isNotNull();
            assertThat(stage.isClosed()).isFalse();
            return invocation.proceed();
        };
        @SuppressWarnings("unchecked")
        ObjectProvider<TfiTaskDeepTrackingDelegate> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenReturn(Stream.of(delegate));

        TfiAnnotationAspect aspect = new TfiAnnotationAspect(evaluator, masker, provider);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        TfiTask annotation = mock(TfiTask.class);
        when(annotation.samplingRate()).thenReturn(1.0);
        when(annotation.condition()).thenReturn("");
        when(annotation.value()).thenReturn("tracked-task");
        when(annotation.deepTracking()).thenReturn(true);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(declaredMethod);
        when(joinPoint.getArgs()).thenReturn(originalArguments);
        when(joinPoint.proceed()).thenReturn(businessValue);
        TfiFlow.enable();

        Object result = aspect.around(joinPoint, annotation);

        assertThat(result).isSameAs(businessValue);
        assertThat(delegateCalls).hasValue(1);
        assertThat(observedMethod).hasValue(declaredMethod);
        assertThat(observedArguments.get())
                .isNotSameAs(originalArguments)
                .containsExactly(argument);
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void delegateCannotProceedBusinessInvocationTwice() throws Throwable {
        SafeSpELEvaluator evaluator = mock(SafeSpELEvaluator.class);
        UnifiedDataMasker masker = mock(UnifiedDataMasker.class);
        TfiTaskDeepTrackingDelegate delegate = (annotation, method, arguments, stage, invocation) -> {
            Object result = invocation.proceed();
            assertThatThrownBy(invocation::proceed)
                    .isInstanceOf(IllegalStateException.class);
            return result;
        };
        @SuppressWarnings("unchecked")
        ObjectProvider<TfiTaskDeepTrackingDelegate> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenReturn(Stream.of(delegate));
        TfiAnnotationAspect aspect = new TfiAnnotationAspect(evaluator, masker, provider);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        TfiTask annotation = mock(TfiTask.class);
        Method declaredMethod = SampleService.class.getDeclaredMethod("handle", Object.class);
        when(annotation.samplingRate()).thenReturn(1.0);
        when(annotation.condition()).thenReturn("");
        when(annotation.value()).thenReturn("tracked-task");
        when(annotation.deepTracking()).thenReturn(true);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(declaredMethod);
        when(joinPoint.getArgs()).thenReturn(new Object[]{new Object()});
        when(joinPoint.proceed()).thenReturn("value");
        TfiFlow.enable();

        Object result = aspect.around(joinPoint, annotation);

        assertThat(result).isEqualTo("value");
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void disabledFlowSkipsDelegateAndProceedsExactlyOnce() throws Throwable {
        SafeSpELEvaluator evaluator = mock(SafeSpELEvaluator.class);
        UnifiedDataMasker masker = mock(UnifiedDataMasker.class);
        AtomicInteger delegateCalls = new AtomicInteger();
        TfiTaskDeepTrackingDelegate delegate = (annotation, method, arguments, stage, invocation) -> {
            delegateCalls.incrementAndGet();
            return invocation.proceed();
        };
        @SuppressWarnings("unchecked")
        ObjectProvider<TfiTaskDeepTrackingDelegate> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenReturn(Stream.of(delegate));
        TfiAnnotationAspect aspect = new TfiAnnotationAspect(evaluator, masker, provider);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        TfiTask annotation = mock(TfiTask.class);
        Object businessValue = new Object();
        Method declaredMethod = SampleService.class.getDeclaredMethod("handle", Object.class);
        when(annotation.samplingRate()).thenReturn(1.0);
        when(annotation.condition()).thenReturn("");
        when(annotation.value()).thenReturn("tracked-task");
        when(annotation.deepTracking()).thenReturn(true);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(declaredMethod);
        when(joinPoint.getArgs()).thenReturn(new Object[]{new Object()});
        when(joinPoint.proceed()).thenReturn(businessValue);
        TfiFlow.disable();

        Object result = aspect.around(joinPoint, annotation);

        assertThat(result).isSameAs(businessValue);
        assertThat(delegateCalls).hasValue(0);
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void unsampledCallSkipsDelegateAndProceedsExactlyOnce() throws Throwable {
        SafeSpELEvaluator evaluator = mock(SafeSpELEvaluator.class);
        UnifiedDataMasker masker = mock(UnifiedDataMasker.class);
        AtomicInteger delegateCalls = new AtomicInteger();
        TfiTaskDeepTrackingDelegate delegate = (annotation, method, arguments, stage, invocation) -> {
            delegateCalls.incrementAndGet();
            return invocation.proceed();
        };
        @SuppressWarnings("unchecked")
        ObjectProvider<TfiTaskDeepTrackingDelegate> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenReturn(Stream.of(delegate));
        TfiAnnotationAspect aspect = new TfiAnnotationAspect(evaluator, masker, provider);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        TfiTask annotation = mock(TfiTask.class);
        when(annotation.samplingRate()).thenReturn(0.0);
        when(joinPoint.proceed()).thenReturn("value");
        TfiFlow.enable();

        Object result = aspect.around(joinPoint, annotation);

        assertThat(result).isEqualTo("value");
        assertThat(delegateCalls).hasValue(0);
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void falseConditionSkipsDelegateAndProceedsExactlyOnce() throws Throwable {
        SafeSpELEvaluator evaluator = mock(SafeSpELEvaluator.class);
        UnifiedDataMasker masker = mock(UnifiedDataMasker.class);
        AtomicInteger delegateCalls = new AtomicInteger();
        TfiTaskDeepTrackingDelegate delegate = (annotation, method, arguments, stage, invocation) -> {
            delegateCalls.incrementAndGet();
            return invocation.proceed();
        };
        @SuppressWarnings("unchecked")
        ObjectProvider<TfiTaskDeepTrackingDelegate> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenReturn(Stream.of(delegate));
        TfiAnnotationAspect aspect = new TfiAnnotationAspect(evaluator, masker, provider);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        TfiTask annotation = mock(TfiTask.class);
        when(annotation.samplingRate()).thenReturn(1.0);
        when(annotation.condition()).thenReturn("#enabled");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("handle");
        when(signature.getDeclaringTypeName()).thenReturn(SampleService.class.getName());
        when(evaluator.evaluateCondition(eq("#enabled"), anyMap())).thenReturn(false);
        when(joinPoint.proceed()).thenReturn("value");
        TfiFlow.enable();

        Object result = aspect.around(joinPoint, annotation);

        assertThat(result).isEqualTo("value");
        assertThat(delegateCalls).hasValue(0);
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void multipleDelegatesFailAspectConstructionInsteadOfChoosingOne() {
        TfiTaskDeepTrackingDelegate first = (annotation, method, arguments, stage, invocation) ->
                invocation.proceed();
        TfiTaskDeepTrackingDelegate second = (annotation, method, arguments, stage, invocation) ->
                invocation.proceed();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("firstDeepTrackingDelegate", first);
        beanFactory.addBean("secondDeepTrackingDelegate", second);
        ObjectProvider<TfiTaskDeepTrackingDelegate> provider =
                beanFactory.getBeanProvider(TfiTaskDeepTrackingDelegate.class);

        assertThatThrownBy(() -> new TfiAnnotationAspect(
                mock(SafeSpELEvaluator.class),
                mock(UnifiedDataMasker.class),
                provider))
                .isInstanceOf(NoUniqueBeanDefinitionException.class);
    }

    @Test
    void checkedBusinessFailureKeepsIdentityThroughDelegate() throws Throwable {
        SafeSpELEvaluator evaluator = mock(SafeSpELEvaluator.class);
        UnifiedDataMasker masker = mock(UnifiedDataMasker.class);
        TfiTaskDeepTrackingDelegate delegate = (annotation, method, arguments, stage, invocation) ->
                invocation.proceed();
        @SuppressWarnings("unchecked")
        ObjectProvider<TfiTaskDeepTrackingDelegate> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenReturn(Stream.of(delegate));
        TfiAnnotationAspect aspect = new TfiAnnotationAspect(evaluator, masker, provider);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        TfiTask annotation = mock(TfiTask.class);
        Method declaredMethod = SampleService.class.getDeclaredMethod("handle", Object.class);
        CheckedBusinessFailure failure = new CheckedBusinessFailure();
        when(annotation.samplingRate()).thenReturn(1.0);
        when(annotation.condition()).thenReturn("");
        when(annotation.value()).thenReturn("tracked-task");
        when(annotation.deepTracking()).thenReturn(true);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(declaredMethod);
        when(joinPoint.getArgs()).thenReturn(new Object[]{new Object()});
        when(joinPoint.proceed()).thenThrow(failure);
        TfiFlow.enable();

        assertThatThrownBy(() -> aspect.around(joinPoint, annotation))
                .isSameAs(failure);
        verify(joinPoint, times(1)).proceed();
    }

    private static final class SampleService {

        @SuppressWarnings("unused")
        private Object handle(Object input) {
            return input;
        }
    }

    private static final class CheckedBusinessFailure extends Exception {
    }
}
