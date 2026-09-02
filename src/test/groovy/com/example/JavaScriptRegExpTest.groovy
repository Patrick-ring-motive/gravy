package com.example

/** Local best-effort RegExp facade checks. */
class JavaScriptRegExpTest extends GravyTestCase {
    void testExecExposesCapturesAndMetadata() {
        def match = new JavaScriptRegExp('(gro)(ovy)', 'i').exec('Groovy')

        assert match == ['Groovy', 'Gro', 'ovy']
        assert match.index == 0
        assert match.input == 'Groovy'
    }

    void testGlobalAndStickyExpressionsTrackLastIndex() {
        def global = new JavaScriptRegExp('a', 'g')
        def sticky = new JavaScriptRegExp('a', 'y')

        assert global.test('aba')
        assert global.lastIndex == 1
        assert global.test('aba')
        assert global.lastIndex == 3
        assert !global.test('aba')
        assert global.lastIndex == 0
        sticky.lastIndex = 1
        assert !sticky.test('aba')
        assert sticky.lastIndex == 0
    }

    void testPatternBridgeAndFlags() {
        def expression = new JavaScriptRegExp('a.b', 's')

        assert 'a\nb'.match(expression) == ['a\nb']
        assert JavaScriptRegExp.escape('a+b?') == 'a\\+b\\?'
        assert shouldFail(JavaScriptSyntaxError) { new JavaScriptRegExp('a', 'gg') }
    }
}
