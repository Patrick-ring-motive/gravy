package com.example

class JavaScriptStringExtensionsTest extends GravyTestCase {
    void setUp() {
        JavaScriptStringExtensions.install()
    }

    void testStaticMethods() {
        assert String.fromCharCode(65, 66) == 'AB'
        assert String.fromCodePoint(0x1F604) == '😄'
        assert String.raw([raw: ['first-', '-last']], 'middle') == 'first-middle-last'
    }

    void testSharedCharSequenceMethods() {
        List<CharSequence> values = ['groovy', new StringBuilder('groovy'), "groo${'vy'}"]

        values.each { value ->
            assert value.includes('roo')
            assert value.charAt(99) == ''
            assert value.slice(2) == 'oovy'
            assert value.replace('o', '0') == 'gr0ovy'
            assert value.padStart(8, '0') == '00groovy'
            assert value.toString() == 'groovy'
            assert value.valueOf() == 'groovy'
            assert value.iterator().toList() == ['g', 'r', 'o', 'o', 'v', 'y']
        }
    }

    void testHtmlWrappers() {
        assert 'text'.anchor('section') == '<a name="section">text</a>'
        assert 'text'.big() == '<big>text</big>'
        assert 'text'.blink() == '<blink>text</blink>'
        assert 'text'.bold() == '<b>text</b>'
        assert 'text'.fixed() == '<tt>text</tt>'
        assert 'text'.fontcolor('red') == '<font color="red">text</font>'
        assert 'text'.fontsize(3) == '<font size="3">text</font>'
        assert 'text'.italics() == '<i>text</i>'
        assert 'text'.link('/docs') == '<a href="/docs">text</a>'
        assert 'text'.small() == '<small>text</small>'
        assert 'text'.strike() == '<strike>text</strike>'
        assert 'text'.sub() == '<sub>text</sub>'
        assert 'text'.sup() == '<sup>text</sup>'
    }

    void testCharacterAndSearchMethods() {
        assert 'cat'.at(-1) == 't'
        assert 'cat'.at(5) == null
        assert 'cat'.charAt(1) == 'a'
        assert 'cat'.charAt(5) == ''
        assert 'A'.charCodeAt() == 65d
        assert Double.isNaN('A'.charCodeAt(1))
        assert '😄'.codePointAt(0) == 0x1F604
        assert 'abc'.concat('d', 1) == 'abcd1'
        assert 'groovy'.endsWith('ovy')
        assert 'groovy'.includes('oov')
        assert 'groovy'.indexOf('o', 3) == 3
        assert 'groovy'.lastIndexOf('o') == 3
        assert 'groovy'.startsWith('ovy', 3)
    }

    void testUnicodeAndCaseMethods() {
        assert 'valid 😄'.isWellFormed()
        assert !'\uD800'.isWellFormed()
        assert '\uD800'.toWellFormed() == '�'
        assert 'e\u0301'.normalize() == 'é'
        assert 'a'.localeCompare('b') < 0
        assert 'I'.toLocaleLowerCase('tr') == 'ı'
        assert 'i'.toLocaleUpperCase('tr') == 'İ'
        assert 'mixed'.toUpperCase() == 'MIXED'
        assert 'MIXED'.toLowerCase() == 'mixed'
        assert 'same'.toString() == 'same'
        assert 'same'.valueOf() == 'same'
        assert ['😄', 'a'] == '😄a'.iterator().toList()
    }

    void testExtractionPaddingAndWhitespaceMethods() {
        assert 'abcdef'.slice(-3, -1) == 'de'
        assert 'abcdef'.substr(-3, 2) == 'de'
        assert 'abcdef'.substring(4, 1) == 'bcd'
        assert 'x'.padEnd(3, 'ab') == 'xab'
        assert 'x'.padStart(3, 'ab') == 'abx'
        assert 'ha'.repeat(3) == 'hahaha'
        assert ' \u00A0text\uFEFF '.trim() == 'text'
        assert '  text  '.trimStart() == 'text  '
        assert '  text  '.trimEnd() == '  text'
    }

    void testPatternAndReplacementMethods() {
        assert 'a1b2'.match(/(\d)/) == ['1', '1']
        assert 'a1b2'.matchAll(/(\d)/).toList() == [['1', '1'], ['2', '2']]
        assert 'a1b2'.search(/\d/) == 1
        assert 'a.a'.replace('.', 'x') == 'axa'
        assert 'a-a'.replaceAll('-', '+') == 'a+a'
        assert 'abc'.replace(~/(b)/, '<$1>') == 'a<b>c'
        assert 'a1b2'.replaceAll(~/\d/, { match, offset, input -> "${offset}" }) == 'a1b3'
    }

    void testSplitMethods() {
        assert 'a.b.'.split('.') == ['a', 'b', '']
        assert 'a1b2'.split(~/(\d)/) == ['a', '1', 'b', '2', '']
        assert '😄'.split('') == ['\uD83D', '\uDE04']
        assert 'a,b,c'.split(',', 2) == ['a', 'b']
        assert 'a,b'.split() == ['a,b']
    }
}
