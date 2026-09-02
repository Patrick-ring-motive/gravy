package com.example

/** Groovy vectors derived from current core-js RegExp constructor, exec, sticky, and test modules. */
class CoreJsDerivedRegExpTest extends GravyTestCase {
    void testStickyMatchOnlySucceedsAtLastIndex() {
        def expression = new JavaScriptRegExp('a', 'y')
        expression.lastIndex = 1

        assert expression.exec('ba') == ['a']
        assert expression.lastIndex == 2
        assert expression.exec('ba') == null
        assert expression.lastIndex == 0
    }

    void testRegexToStringAndDotAll() {
        assert new JavaScriptRegExp('a.b', 's').toString() == '/a.b/s'
        assert new JavaScriptRegExp('a.b', 's').test('a\nb')
    }
}
