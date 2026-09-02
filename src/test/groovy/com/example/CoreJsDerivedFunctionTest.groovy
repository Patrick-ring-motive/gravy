package com.example

import java.lang.reflect.Constructor

/** Groovy vectors derived from current core-js Function bind behavior. */
class CoreJsDerivedFunctionTest extends GravyTestCase {
    static class Pair {
        final Object first
        final Object second

        Pair(Object first, Object second) {
            this.first = first
            this.second = second
        }
    }

    void testBindCallsFunctionWithBoundDelegate() {
        def function = JavaScriptFunction.of({ -> answer })

        assert function.bind([answer: 42]).call(null) == 42
    }

    void testBoundConstructorsRetainLeadingArguments() {
        Constructor constructor = Pair.getConstructor(Object, Object)
        def pair = JavaScriptFunction.of(constructor).bind(null, 1).construct(2)

        assert pair instanceof Pair
        assert pair.first == 1
        assert pair.second == 2
    }

    void testNonConstructableFacadeRejectsConstruct() {
        assert shouldFail(JavaScriptTypeError) {
            JavaScriptFunction.of({ -> null }).construct()
        }
    }
}
