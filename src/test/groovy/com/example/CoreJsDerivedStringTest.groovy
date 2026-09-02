package com.example

/**
 * Independent Groovy vectors derived from current core-js String module behavior.
 *
 * Covers literal replacement, replacement tokens, regular-expression splitting,
 * Unicode construction, and padding. Host RegExp protocol hooks are excluded.
 */
class CoreJsDerivedStringTest extends GravyTestCase {
    void setUp() {
        JavaScriptStringExtensions.install()
    }

    void testReplaceAllUsesLiteralStringSearchAndReplacementTokens() {
        assert 'a.a.a'.replaceAll('.', '-') == 'a-a-a'
        assert 'a'.replace(~/(a)/, '$10') == 'a0'
        assert 'abc'.replace(~/(b)/, '$`<$&>$\'') == 'aa<b>cc'
    }

    void testSplitRegularExpressionEmptyMatchesDoNotCreateTrailingElement() {
        assert 'ab'.split(~/(?:)/) == ['a', 'b']
        assert 'a1b2'.split(~/(\d)/, 3) == ['a', '1', 'b']
    }

    void testUnicodeFactoriesAndPaddingFollowCurrentBehavior() {
        assert String.fromCharCode(0x41, 0x1F604) == 'A\uF604'
        assert String.fromCodePoint(0x41, 0x1F604) == 'A😄'
        assert String.raw([raw: ['before-', '-after']], 'middle') == 'before-middle-after'
        assert 'x'.padStart(5, 'ab') == 'ababx'
        assert 'x'.padEnd(5, 'ab') == 'xabab'
    }

    void testMatchAllRetainsCapturesAndOffsets() {
        List<List<Object>> matches = 'a1b2'.matchAll(~/(\d)/).toList()

        assert matches == [['1', '1'], ['2', '2']]
        assert 'a1b2'.search(~/\d/) == 1
    }
}
