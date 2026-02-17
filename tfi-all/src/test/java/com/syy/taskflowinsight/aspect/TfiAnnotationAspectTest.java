package com.syy.taskflowinsight.aspect;

import com.syy.taskflowinsight.annotation.TfiTask;
import com.syy.taskflowinsight.masking.UnifiedDataMasker;
import com.syy.taskflowinsight.spel.SafeSpELEvaluator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TfiAnnotationAspectTest {
    
    private TfiAnnotationAspect aspect;
    
    @Mock private ProceedingJoinPoint pjp;
    @Mock private MethodSignature signature;
    @Mock private TfiTask tfiTask;
    @Mock private SafeSpELEvaluator spelEvaluator;
    @Mock private UnifiedDataMasker dataMasker;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        aspect = new TfiAnnotationAspect(spelEvaluator, dataMasker);
    }
    
    @Test
    void testZeroSampling_DirectPassThrough() throws Throwable {
        // Given: 0采样率
        when(tfiTask.samplingRate()).thenReturn(0.0);
        when(pjp.proceed()).thenReturn("result");
        
        // When: 执行切面
        Object result = aspect.around(pjp, tfiTask);
        
        // Then: 直通不拦截
        assertEquals("result", result);
        verify(pjp, times(1)).proceed();
    }
    
    @Test
    void testFullSampling_AlwaysExecute() throws Throwable {
        // Given: 100%采样率
        when(tfiTask.samplingRate()).thenReturn(1.0);
        when(tfiTask.condition()).thenReturn("");
        when(tfiTask.value()).thenReturn("testTask");
        when(tfiTask.logArgs()).thenReturn(false);
        when(tfiTask.logResult()).thenReturn(false);
        when(pjp.proceed()).thenReturn("result");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
        when(signature.getDeclaringTypeName()).thenReturn("TestClass");
        
        // When: 执行切面
        Object result = aspect.around(pjp, tfiTask);
        
        // Then: 全量拦截
        assertEquals("result", result);
    }
    
    @Test
    void testConditionFalse_SkipTracking() throws Throwable {
        // Given: 条件不满足
        when(tfiTask.samplingRate()).thenReturn(1.0);
        when(tfiTask.condition()).thenReturn("methodName == 'otherMethod'");
        when(pjp.proceed()).thenReturn("result");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
        when(signature.getDeclaringTypeName()).thenReturn("TestClass");
        when(spelEvaluator.evaluateCondition(anyString(), any())).thenReturn(false);
        
        // When: 执行切面
        Object result = aspect.around(pjp, tfiTask);
        
        // Then: 跳过追踪
        assertEquals("result", result);
    }
    
    @Test
    void testExceptionPropagation() throws Throwable {
        // Given: 目标方法抛异常
        RuntimeException exception = new RuntimeException("Test error");
        when(tfiTask.samplingRate()).thenReturn(1.0);
        when(tfiTask.condition()).thenReturn("");
        when(tfiTask.value()).thenReturn("errorTask");
        when(tfiTask.logException()).thenReturn(true);
        when(pjp.proceed()).thenThrow(exception);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("errorMethod");
        when(signature.getDeclaringTypeName()).thenReturn("TestClass");
        
        // When/Then: 异常不吞噬
        assertThrows(RuntimeException.class, () -> aspect.around(pjp, tfiTask));
    }
    
    @Test
    void testConditionTrue_WithRootObjectContext() throws Throwable {
        // Given: 条件满足（根对象属性）
        when(tfiTask.samplingRate()).thenReturn(1.0);
        when(tfiTask.condition()).thenReturn("methodName == 'testMethod'");
        when(tfiTask.value()).thenReturn("conditionalTask");
        when(tfiTask.logArgs()).thenReturn(false);
        when(tfiTask.logResult()).thenReturn(false);
        when(pjp.proceed()).thenReturn("result");
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
        when(signature.getDeclaringTypeName()).thenReturn("TestClass");
        when(spelEvaluator.evaluateCondition(eq("methodName == 'testMethod'"), any())).thenReturn(true);
        
        // When: 执行切面
        Object result = aspect.around(pjp, tfiTask);
        
        // Then: 执行追踪
        assertEquals("result", result);
    }
    
    @Test 
    void testLogResult_WhenEnabled() throws Throwable {
        // Given: logResult=true
        when(tfiTask.samplingRate()).thenReturn(1.0);
        when(tfiTask.condition()).thenReturn("");
        when(tfiTask.value()).thenReturn("logTask");
        when(tfiTask.logArgs()).thenReturn(false);
        when(tfiTask.logResult()).thenReturn(true);
        
        Object expectedResult = "testResult";
        when(pjp.proceed()).thenReturn(expectedResult);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
        when(signature.getDeclaringTypeName()).thenReturn("TestClass");
        when(dataMasker.maskValue("result", expectedResult)).thenReturn("***Result");
        
        // When: 执行切面
        Object result = aspect.around(pjp, tfiTask);
        
        // Then: 记录返回值
        assertEquals(expectedResult, result);
        verify(dataMasker, times(1)).maskValue("result", expectedResult);
    }
}