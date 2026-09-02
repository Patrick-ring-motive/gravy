package com.example

/**
 * Groovy vectors derived from supported Test262 BigInt constructor, fixed-width,
 * string-conversion, Set, TypedArray, and DataView requirements.
 */
class Test262DerivedBigIntTest extends GravyTestCase {
    void testStringToBigIntAndConstructorErrorVectors() {
        [
            '10': '10', '18446744073709551616': '18446744073709551616', '-10': '-10',
            '-18446744073709551616': '-18446744073709551616', '0xabcdef': '11259375',
            '0b101010': '42', '0o52': '42', '  +42\n': '42', '': '0'
        ].each { String input, String expected ->
            assert JavaScriptBigInt.call(input).toString() == expected
        }
        assert JavaScriptBigInt.call(true).toString() == '1'
        assert JavaScriptBigInt.call(false).toString() == '0'
        assert JavaScriptBigInt.call(42).toString() == '42'

        ['10n', '10x', '10b', '10.5', '0b', '-0x1', '-0XFFab', '0oa', '000 12', '0o', '0x', '00o', '00b', '00x'].each { String input ->
            assert shouldFail(JavaScriptSyntaxError) { JavaScriptBigInt.call(input) }
        }
        assert shouldFail(JavaScriptRangeError) { JavaScriptBigInt.call(1.5d) }
        assert shouldFail(JavaScriptRangeError) { JavaScriptBigInt.call(Double.NaN) }
        assert shouldFail(JavaScriptRangeError) { JavaScriptBigInt.call(Double.POSITIVE_INFINITY) }
        assert shouldFail(JavaScriptTypeError) { JavaScriptBigInt.call() }
        assert shouldFail(JavaScriptTypeError) { JavaScriptBigInt.call(null) }
        assert shouldFail(JavaScriptTypeError) { JavaScriptBigInt.call(JavaScriptSymbol.create('test262')) }
    }

    void testAsIntNAndAsUintNArithmeticVectors() {
        [
            [0, '-2', '0'], [0, '-1', '0'], [0, '0', '0'], [0, '1', '0'], [0, '2', '0'],
            [1, '-3', '-1'], [1, '-2', '0'], [1, '-1', '-1'], [1, '1', '-1'], [1, '2', '0'],
            [2, '-3', '1'], [2, '-2', '-2'], [2, '-1', '-1'], [2, '2', '-2'], [2, '3', '-1'],
            [8, '0xab', '-85'], [8, '0xabcd', '-51'], [8, '0xabcdef01', '1'],
            [8, '0xabcdef0123456789abcdef0123', '35'], [8, '0xabcdef0123456789abcdef0183', '-125'],
            [64, '0xabcdef0123456789abcdef', '0x0123456789abcdef'],
            [65, '0xabcdef0123456789abcdef', '-0xfedcba9876543211'],
            [200, '0xcffffffffffffffffffffffffffffffffffffffffffffffffff', '-1'],
            [201, '0xcffffffffffffffffffffffffffffffffffffffffffffffffff', '0xffffffffffffffffffffffffffffffffffffffffffffffffff']
        ].each { List<Object> vector ->
            assert JavaScriptBigInt.asIntN(vector[0], big(vector[1])).toString() == decimal(vector[2])
        }

        [
            [0, '-2', '0'], [0, '-1', '0'], [0, '0', '0'], [0, '1', '0'], [0, '2', '0'],
            [1, '-3', '1'], [1, '-2', '0'], [1, '-1', '1'], [1, '1', '1'], [1, '2', '0'],
            [2, '-3', '1'], [2, '-2', '2'], [2, '-1', '3'], [2, '2', '2'], [2, '3', '3'],
            [8, '0xab', '171'], [8, '0xabcd', '205'], [8, '0xabcdef01', '1'],
            [8, '0xabcdef0123456789abcdef0123', '35'], [8, '0xabcdef0123456789abcdef0183', '131'],
            [64, '0xabcdef0123456789abcdef', '0x0123456789abcdef'],
            [65, '0xabcdef0123456789abcdef', '0x10123456789abcdef'],
            [200, '0xbffffffffffffffffffffffffffffffffffffffffffffffffff', '0x0ffffffffffffffffffffffffffffffffffffffffffffffffff'],
            [201, '0xbffffffffffffffffffffffffffffffffffffffffffffffffff', '0x1ffffffffffffffffffffffffffffffffffffffffffffffffff']
        ].each { List<Object> vector ->
            assert JavaScriptBigInt.asUintN(vector[0], big(vector[1])).toString() == decimal(vector[2])
        }
    }

    void testFixedWidthBitIndexAndRadixVectors() {
        def one = big('1')
        [
            [0, '0'], [1, '-1'], [-0.9d, '0'], [0.9d, '0'], [Double.NaN, '0'], [null, '0'],
            [false, '0'], [true, '-1'], ['0', '0'], ['1', '-1'], ['', '0'], ['foo', '0']
        ].each { List<Object> vector ->
            assert JavaScriptBigInt.asIntN(vector[0], one).toString() == vector[1]
        }
        assert JavaScriptBigInt.asIntN('3.9', big('10')).toString() == '2'
        assert JavaScriptBigInt.asIntN(3.9d, big('10')).toString() == '2'
        assert shouldFail(JavaScriptTypeError) { JavaScriptBigInt.asIntN(big('1'), one) }
        assert shouldFail(JavaScriptTypeError) { JavaScriptBigInt.asUintN(8, 1) }
        assert shouldFail(JavaScriptRangeError) { JavaScriptBigInt.asUintN(-1, one) }

        (2..36).each { int radix ->
            assert big('0').toString(radix) == '0'
            assert big('-1').toString(radix) == '-1'
            assert big('1').toString(radix) == '1'
        }
        assert big('255').toString(16.9d) == 'ff'
        assert big('255').toString(null) == '255'
        [0, 1, 37, Double.NaN, Double.POSITIVE_INFINITY].each { Object radix ->
            assert shouldFail(JavaScriptRangeError) { big('1').toString(radix) }
        }
        assert shouldFail(JavaScriptTypeError) { big('1').toString(big('10')) }
    }

    void testIntegralJvmNumbersRetainPrecisionDuringConversion() {
        assert JavaScriptBigInt.call(9_007_199_254_740_993L).toString() == '9007199254740993'
        assert JavaScriptBigInt.call(Long.MIN_VALUE).toString() == '-9223372036854775808'
        assert JavaScriptBigInt.call(Long.MAX_VALUE).toString() == '9223372036854775807'
    }

    void testBigIntRemainsDistinctInCollectionsAndBinaryViews() {
        def bigint = big('9007199254740991')
        def values = new JavaScriptSet([bigint, 9_007_199_254_740_991L, bigint])
        assert values.size == 2
        assert values.has(bigint)
        assert values.has(9_007_199_254_740_991L)
        assert values.delete(bigint)
        assert !values.has(bigint)
        assert values.has(9_007_199_254_740_991L)

        def buffer = new JavaScriptArrayBuffer(16)
        def view = new JavaScriptDataView(buffer)
        view.setBigInt64(0, big('-1'))
        view.setBigUint64(8, big('-1'))
        assert view.getBigInt64(0).toString() == '-1'
        assert view.getBigUint64(8).toString() == '18446744073709551615'

        def signed = new JavaScriptBigInt64Array([big('-1'), big('2')])
        assert signed.values().collect()*.toString() == ['-1', '2']
        assert signed.includes(big('-1'))
        assert !signed.includes(-1)
    }

    private static JavaScriptBigInt big(String value) {
        JavaScriptBigInt.call(value)
    }

    private static String decimal(String value) {
        if (value.startsWith('-0x')) {
            return new java.math.BigInteger(value.substring(3), 16).negate().toString()
        }
        if (value.startsWith('0x')) {
            return new java.math.BigInteger(value.substring(2), 16).toString()
        }
        new java.math.BigInteger(value, 10).toString()
    }
}
