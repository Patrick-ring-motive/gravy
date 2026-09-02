package com.example

/**
 * Groovy vectors derived from Test262 Function call and apply requirements.
 * Realm-specific primitive boxing has no direct JVM equivalent.
 */
class Test262DerivedFunctionTest extends GravyTestCase {
    void testCallRebindsClosureDelegateForThisLikePropertyLookup() {
        def function = JavaScriptFunction.of({ -> label })

        assert function.call([label: 'first']) == 'first'
        assert function.call([label: 'second']) == 'second'
    }

    void testApplyConsumesArraysAndIterablesAsArgumentLists() {
        def function = JavaScriptFunction.of({ first, second, third -> [first, second, third] })

        assert function.apply(null, [1, 2, 3]) == [1, 2, 3]
        assert function.apply(null, [4, 5, 6].iterator()) == [4, 5, 6]
        assert shouldFail(JavaScriptTypeError) { function.apply(null, 1) }
    }

    void testBindRetainsThisLikeDelegateAndLeadingArguments() {
        def function = JavaScriptFunction.of({ suffix -> "${prefix}-${suffix}" })
        def bound = function.bind([prefix: 'value'], 'tail')

        assert bound.call([prefix: 'ignored']) == 'value-tail'
    }
}
