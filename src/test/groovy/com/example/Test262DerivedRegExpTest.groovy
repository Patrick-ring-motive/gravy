package com.example

/** Groovy vectors derived from Test262 RegExp constructor, exec, and flags requirements. */
class Test262DerivedRegExpTest extends GravyTestCase {
    void testConstructorFlagsAndDuplicateValidation() {
        def expression = new JavaScriptRegExp('a', 'gim')

        assert expression.global
        assert expression.ignoreCase
        assert expression.multiline
        assert expression.flags == 'gim'
        assert shouldFail(JavaScriptSyntaxError) { new JavaScriptRegExp('a', 'uv') }
    }

    void testExecResetsGlobalLastIndexAfterFailure() {
        def expression = new JavaScriptRegExp('a', 'g')

        assert expression.exec('a').index == 0
        assert expression.lastIndex == 1
        assert expression.exec('a') == null
        assert expression.lastIndex == 0
    }
}
