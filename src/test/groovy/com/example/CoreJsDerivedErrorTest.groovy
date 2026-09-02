package com.example

/** Groovy vectors derived from current core-js Error and AggregateError modules. */
class CoreJsDerivedErrorTest extends GravyTestCase {
    void testErrorCauseIsRetained() {
        def cause = new IllegalStateException('inner')
        def error = new JavaScriptError('outer', cause)

        assert error.message == 'outer'
        assert error.cause.is(cause)
    }

    void testAggregateErrorPreservesErrorSequence() {
        def errors = [new JavaScriptTypeError('first'), 'second']
        def aggregate = new JavaScriptAggregateError(errors, 'all failed')

        assert aggregate.name == 'AggregateError'
        assert aggregate.message == 'all failed'
        assert aggregate.errors == errors
        assert aggregate.toString() == 'AggregateError: all failed'
    }
}
