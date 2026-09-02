package com.example

/** Groovy vectors derived from Test262 native-error name and string behavior. */
class Test262DerivedErrorTest extends GravyTestCase {
    void testErrorToStringUsesNameAndMessage() {
        assert new JavaScriptError('message').toString() == 'Error: message'
        assert new JavaScriptTypeError('message').toString() == 'TypeError: message'
        assert new JavaScriptRangeError().toString() == 'RangeError'
    }

    void testNativeErrorFamiliesRemainDistinctThrowableTypes() {
        assert new JavaScriptEvalError() instanceof JavaScriptError
        assert new JavaScriptReferenceError() instanceof JavaScriptError
        assert new JavaScriptSyntaxError() instanceof JavaScriptError
        assert new JavaScriptURIError() instanceof JavaScriptError
        assert !JavaScriptTypeError.isAssignableFrom(JavaScriptRangeError)
    }
}
