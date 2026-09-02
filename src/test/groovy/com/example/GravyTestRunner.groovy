package com.example

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** Executes this project's convention-based Groovy tests without external test libraries. */
final class GravyTestRunner {
    private static final List<String> TEST_CLASSES = [
        'com.example.JavaScriptStringExtensionsTest',
        'com.example.JavaScriptArrayExtensionsTest',
        'com.example.JavaScriptSpreadAstTransformationTest',
        'com.example.JavaScriptObjectExtensionsTest',
        'com.example.JavaScriptFunctionTest',
        'com.example.JavaScriptNumberTest',
        'com.example.JavaScriptBigIntTest',
        'com.example.JavaScriptBuiltinStaticExtensionsTest',
        'com.example.JavaScriptCollectionsTest',
        'com.example.JavaScriptErrorsTest',
        'com.example.JavaScriptGeneratorTest',
        'com.example.JavaScriptJsonMathTest',
        'com.example.JavaScriptRegExpTest',
        'com.example.JavaScriptDateTest',
        'com.example.JavaScriptIntlTest',
        'com.example.JavaScriptWeakCollectionsTest',
        'com.example.JavaScriptWeakReferencesTest',
        'com.example.JavaScriptTypedArraysTest',
        'com.example.JavaScriptUrlWebUtilitiesTest',
        'com.example.JavaScriptWebPlatformTest',
        'com.example.JavaScriptWebRuntimeTest',
        'com.example.FetchDerivedWebPlatformTest',
        'com.example.NodeFetchDerivedWebPlatformTest',
        'com.example.WebStreamsPolyfillDerivedTest',
        'com.example.JavaScriptReflectProxyTest',
        'com.example.JavaScriptGlobalsTest',
        'com.example.JavaScriptBaseScriptTest',
        'com.example.JavaScriptGlobalConstructorMatrixTest',
        'com.example.CoreJsDerivedStringTest',
        'com.example.CoreJsDerivedArrayTest',
        'com.example.CoreJsDerivedObjectTest',
        'com.example.CoreJsDerivedSymbolTest',
        'com.example.CoreJsDerivedBigIntTest',
        'com.example.CoreJsDerivedIntlTest',
        'com.example.CoreJsDerivedPromiseTest',
        'com.example.CoreJsDerivedFunctionTest',
        'com.example.CoreJsDerivedNumberTest',
        'com.example.CoreJsDerivedCollectionsTest',
        'com.example.CoreJsDerivedErrorTest',
        'com.example.CoreJsDerivedJsonMathTest',
        'com.example.CoreJsDerivedRegExpTest',
        'com.example.CoreJsDerivedDateTest',
        'com.example.CoreJsDerivedTypedArraysTest',
        'com.example.CoreJsDerivedReflectTest',
        'com.example.MdnDerivedPromiseTest',
        'com.example.Test262DerivedStringExtensionsTest',
        'com.example.Test262DerivedArrayExtensionsTest',
        'com.example.Test262DerivedObjectExtensionsTest',
        'com.example.Test262DerivedReflectProxyTest',
        'com.example.Test262DerivedFunctionTest',
        'com.example.Test262DerivedNumberTest',
        'com.example.Test262DerivedBigIntTest',
        'com.example.Test262DerivedIntlTest',
        'com.example.Test262DerivedCollectionsTest',
        'com.example.Test262DerivedErrorTest',
        'com.example.Test262DerivedJsonMathTest',
        'com.example.Test262DerivedRegExpTest',
        'com.example.Test262DerivedDateTest',
        'com.example.Test262DerivedTypedArraysTest',
        'com.example.Test262DerivedWeakReferencesTest',
        'com.example.Test262DerivedUrlTextTest',
        'com.example.MainPreludeTest',
        'com.example.AppSmokeTest'
    ].asImmutable()

    private GravyTestRunner() {
    }

    static void main(String[] args) {
        int runCount = 0
        List<String> failures = []

        TEST_CLASSES.each { String className ->
            Class<? extends GravyTestCase> testClass = Class.forName(className) as Class<? extends GravyTestCase>
            testClass.declaredMethods
                .findAll { Method method -> method.name.startsWith('test') && method.parameterCount == 0 }
                .sort { Method left, Method right -> left.name <=> right.name }
                .each { Method method ->
                    runCount++
                    GravyTestCase test = testClass.getDeclaredConstructor().newInstance()
                    try {
                        test.setUp()
                        method.invoke(test)
                    } catch (Throwable error) {
                        Throwable cause = unwrap(error)
                        failures << "${className}.${method.name}: ${cause.class.name}: ${cause.message ?: ''}"
                    }
                }
        }

        println "Ran ${runCount} tests"
        if (!failures.isEmpty()) {
            throw new AssertionError("${failures.size()} test(s) failed:\n${failures.join('\n')}")
        }
    }

    private static Throwable unwrap(Throwable error) {
        error instanceof InvocationTargetException && error.cause != null ? error.cause : error
    }
}
