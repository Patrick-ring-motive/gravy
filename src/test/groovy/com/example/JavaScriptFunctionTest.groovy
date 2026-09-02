package com.example

import java.lang.invoke.LambdaMetafactory
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import java.util.function.Function

/** Derived checks for Function.call/apply/bind behavior. */
class JavaScriptFunctionTest extends GravyTestCase {
    void testFunctionMetaClassExtensionReachesNativeLambdaMetafactoryInstance() {
        def lookup = MethodHandles.lookup()
        def implementation = lookup.findStatic(String, 'valueOf', MethodType.methodType(String, Object))
        def site = LambdaMetafactory.metafactory(
            lookup,
            'apply',
            MethodType.methodType(Function),
            MethodType.methodType(Object, Object),
            implementation,
            MethodType.methodType(String, Object)
        )
        Function function = site.target.invokeWithArguments([]) as Function

        assert function.call(42) == '42'
    }

    void testFacadeAppliesAndBindsClosureDelegate() {
        Closure greeting = { String name -> "${prefix}, ${name}" }
        JavaScriptFunction function = JavaScriptFunction.of(greeting)

        assert function.call([prefix: 'Hello'], 'Groovy') == 'Hello, Groovy'
        assert function.apply([prefix: 'Hi'], ['Ada']) == 'Hi, Ada'
        assert function.bind([prefix: 'Welcome'], 'Lin').call(null) == 'Welcome, Lin'
    }

    void testFacadeSupportsReflectionAndRejectsInvalidApplyArguments() {
        Method substring = String.getMethod('substring', Integer.TYPE)
        JavaScriptFunction method = JavaScriptFunction.of(substring)

        assert method.call('groovy', 2) == 'oovy'
        assert shouldFail(JavaScriptTypeError) { method.apply('groovy', 2) }
    }
}
