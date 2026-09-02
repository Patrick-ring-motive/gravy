package com.example

/** Derived JavaScript error hierarchy checks. */
class JavaScriptErrorsTest extends GravyTestCase {
    void testDistinctErrorTypesPreserveJsNamesAndCauses() {
        def cause = new IllegalArgumentException('cause')
        def error = new JavaScriptTypeError('invalid value', cause)

        assert error instanceof JavaScriptError
        assert error.name == 'TypeError'
        assert error.toString() == 'TypeError: invalid value'
        assert error.cause.is(cause)
        assert shouldFail(JavaScriptTypeError) { throw error }.is(error)
    }

    void testAggregateErrorExposesImmutableErrorList() {
        def error = new JavaScriptAggregateError(['first', new JavaScriptRangeError('second')])

        assert error.name == 'AggregateError'
        assert error.errors.size() == 2
        assert shouldFail(UnsupportedOperationException) { error.errors << 'third' }
    }

    void testNamedErrorClassesRemainDistinct() {
        assert new JavaScriptEvalError().name == 'EvalError'
        assert new JavaScriptRangeError().name == 'RangeError'
        assert new JavaScriptReferenceError().name == 'ReferenceError'
        assert new JavaScriptSyntaxError().name == 'SyntaxError'
        assert new JavaScriptURIError().name == 'URIError'
    }
}
