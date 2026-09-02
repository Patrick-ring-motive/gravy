package com.example

/**
 * Independent Groovy vectors derived from Test262 String requirements.
 *
 * Covers constructor helpers; character access; UTF-16 and Unicode operations;
 * search and extraction boundaries; padding, repetition, and trimming;
 * replacement substitutions and callbacks; matching; splitting; locale-aware
 * case conversion; and legacy HTML wrappers. JavaScript property descriptors,
 * Symbols, and custom RegExp protocol objects are outside this Groovy adapter.
 */
class Test262DerivedStringExtensionsTest extends GravyTestCase {
    void setUp() {
        JavaScriptStringExtensions.install()
    }

    void testAtCoercesIndexesAndReturnsUtf16CodeUnits() {
        assert '012'.at('1.8') == '1'
        assert '012'.at('not-a-number') == '0'
        assert '012'.at(-1) == '2'
        assert '012'.at(-4) == null
        assert '\uD800x'.at(0) == '\uD800'
    }

    void testFromCodePointCoercesValuesAndRejectsInvalidCodePoints() {
        assert String.fromCodePoint() == ''
        assert String.fromCodePoint(null, false, true, '42', '042') == '\u0000\u0000\u0001**'
        assert String.fromCodePoint(0x10FFFF) == '\uDBFF\uDFFF'

        shouldFail(IllegalArgumentException) { String.fromCodePoint(-1) }
        shouldFail(IllegalArgumentException) { String.fromCodePoint(0x110000) }
        shouldFail(IllegalArgumentException) { String.fromCodePoint(1.5) }
        shouldFail(IllegalArgumentException) { String.fromCodePoint(Double.NaN) }
    }

    void testReplaceAllReplacementTextSubstitutions() {
        assert 'ab'.replaceAll('a', '$&$&') == 'aab'
        assert 'ab'.replaceAll('a', '$$') == '$b'
        assert 'abc'.replaceAll('b', '$`') == 'aac'
        assert 'abc'.replaceAll('b', "\$'") == 'acc'
        assert 'abc'.replaceAll('b', '$1') == 'a$1c'
        assert 'abc'.replaceAll('', '-') == '-a-b-c-'
    }

    void testSplitUsesEmptySeparatorAndToUint32LimitRules() {
        assert ''.split('') == []
        assert ' '.split('') == [' ']
        assert 'a,b,c'.split(',', 0) == []
        assert 'a,b,c'.split(',', 2) == ['a', 'b']
        assert 'a,b,c'.split(',', -1) == ['a', 'b', 'c']
        assert 'a,b'.split(null) == ['a,b']
    }

    void testExtractionCoercesAndClampsIndexes() {
        assert 'abcdef'.slice('1.9', -1) == 'bcde'
        assert 'abcdef'.slice(-100) == 'abcdef'
        assert 'abcdef'.substring(-1, 1) == 'a'
        assert 'abcdef'.substring(4, 1) == 'bcd'
        assert 'abcdef'.substr(-2) == 'ef'
        assert 'abcdef'.substr(2, -1) == ''
    }

    void testFromCharCodeUsesUint16Coercion() {
        assert String.fromCharCode() == ''
        assert String.fromCharCode(null, false, true) == '\u0000\u0000\u0001'
        assert String.fromCharCode(65.9, -1, 0x10000) == 'A\uFFFF\u0000'
    }

    void testRawInterleavesRawPartsAndSubstitutions() {
        assert String.raw([raw: ['first', 'second', 'third']], 'X') == 'firstXsecondundefinedthird'
        assert String.raw([raw: 'ab']) == 'aundefinedb'
    }

    void testCharacterMethodsUseUtf16CodeUnits() {
        String emoji = '😄'
        assert emoji.charAt('0.9') == '\uD83D'
        assert emoji.charAt(2) == ''
        assert emoji.charCodeAt(0) == (double) 0xD83D
        assert emoji.charCodeAt(1) == (double) 0xDE04
        assert Double.isNaN(emoji.charCodeAt(-1))
        assert emoji.codePointAt(0) == 0x1F604
        assert emoji.codePointAt(1) == 0xDE04
        assert emoji.codePointAt(2) == null
    }

    void testConcatAndSearchCoerceArgumentsAndClampPositions() {
        assert 'x'.concat(null, true, 3) == 'xnulltrue3'
        assert 'abc'.includes('a', -10)
        assert !'abc'.includes('a', 1)
        assert 'abc'.indexOf('', 99) == 3
        assert 'abc'.indexOf('a', -1) == 0
        assert 'abc'.lastIndexOf('', -9) == 0
        assert 'abc'.lastIndexOf('a', 99) == 0
        assert 'abc'.startsWith('b', 1)
        assert !'abc'.startsWith('a', 1)
        assert 'abc'.endsWith('ab', 2)
        assert !'abc'.endsWith('bc', 2)
    }

    void testUnicodeNormalizationAndWellFormedStrings() {
        assert 'e\u0301'.normalize('NFC') == 'é'
        assert 'é'.normalize('NFD') == 'e\u0301'
        assert 'ﬀ'.normalize('NFKD') == 'ff'
        shouldFail(IllegalArgumentException) { 'x'.normalize('INVALID') }

        assert !'\uD800'.isWellFormed()
        assert !'\uDC00'.isWellFormed()
        assert '\uD800\uDC00'.isWellFormed()
        assert '\uD800x\uDC00'.toWellFormed() == '�x�'
        assert '😄a'.iterator().toList() == ['😄', 'a']
    }

    void testPaddingRepeatAndTrimBoundaries() {
        assert 'x'.padEnd(5, 'ab') == 'xabab'
        assert 'x'.padStart(5, 'ab') == 'ababx'
        assert 'abc'.padEnd(2) == 'abc'
        assert 'x'.padStart(3, '') == 'x'
        assert 'ab'.repeat('2.9') == 'abab'
        assert ''.repeat(0) == ''
        shouldFail(IllegalArgumentException) { 'x'.repeat(-1) }
        shouldFail(IllegalArgumentException) { 'x'.repeat(Double.POSITIVE_INFINITY) }
        assert '\uFEFF\u2028 text \u2029'.trim() == 'text'
        assert '\u00A0 text'.trimStart() == 'text'
        assert 'text \u00A0'.trimEnd() == 'text'
    }

    void testReplaceUsesFirstMatchAndGetSubstitutionRules() {
        assert 'aaaa'.replace('a', 'b') == 'baaa'
        assert 'abc'.replace(~/(a)(b)/, '$2$1') == 'bac'
        assert 'abc'.replace(~/(b)/, '<$1>') == 'a<b>c'
        assert 'abc'.replace('x', '$&') == 'abc'
        assert 'abc'.replaceAll('b', '$`') == 'aac'
        assert 'abc'.replaceAll('b', "\$'") == 'acc'

        List<List<Object>> calls = []
        assert 'a1b2'.replaceAll(~/(\d)/, { match, capture, offset, source ->
            calls << [match, capture, offset, source]
            "[${offset}]"
        }) == 'a[1]b[3]'
        assert calls == [['1', '1', 1, 'a1b2'], ['2', '2', 3, 'a1b2']]
    }

    void testMatchSearchAndMatchAllExposeCaptures() {
        assert 'a1'.match('(\\d)') == ['1', '1']
        assert 'abc'.match('z') == null
        assert 'a1b2'.matchAll('(\\d)').toList() == [['1', '1'], ['2', '2']]
        assert 'abc'.matchAll('z').toList() == []
        assert 'abc'.search('b') == 1
        assert 'abc'.search('z') == -1
    }

    void testSplitPreservesCapturesAndTrailingElements() {
        assert 'a1b2'.split(~/(\d)/) == ['a', '1', 'b', '2', '']
        assert 'a.b.'.split('.') == ['a', 'b', '']
        assert 'abc'.split() == ['abc']
        assert 'abc'.split(null) == ['abc']
        assert 'abc'.split('', 2) == ['a', 'b']
    }

    void testLocaleCaseComparisonAndPrimitiveMethods() {
        assert 'I'.toLocaleLowerCase('tr') == 'ı'
        assert 'i'.toLocaleUpperCase('tr') == 'İ'
        assert 'MIXED'.toLowerCase() == 'mixed'
        assert 'mixed'.toUpperCase() == 'MIXED'
        assert 'a'.localeCompare('b') < 0
        assert 'same'.toString() == 'same'
        assert 'same'.valueOf() == 'same'
    }

    void testDeprecatedHtmlWrappersEscapeAttributeQuotes() {
        assert 'text'.anchor('a"b') == '<a name="a&quot;b">text</a>'
        assert 'text'.fontcolor('a"b') == '<font color="a&quot;b">text</font>'
        assert 'text'.fontsize('a"b') == '<font size="a&quot;b">text</font>'
        assert 'text'.link('a"b') == '<a href="a&quot;b">text</a>'
        assert 'text'.big() == '<big>text</big>'
        assert 'text'.blink() == '<blink>text</blink>'
        assert 'text'.bold() == '<b>text</b>'
        assert 'text'.fixed() == '<tt>text</tt>'
        assert 'text'.italics() == '<i>text</i>'
        assert 'text'.small() == '<small>text</small>'
        assert 'text'.strike() == '<strike>text</strike>'
        assert 'text'.sub() == '<sub>text</sub>'
        assert 'text'.sup() == '<sup>text</sup>'
    }
}
