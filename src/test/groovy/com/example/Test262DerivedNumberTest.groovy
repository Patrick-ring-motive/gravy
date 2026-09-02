package com.example

/**
 * Groovy vectors derived from Test262 Number constructor, predicate, parser,
 * and formatting requirements. JVM-specific integer backing types are allowed.
 */
class Test262DerivedNumberTest extends GravyTestCase {
    void testNumberCoercionHandlesWhitespaceAndUnsignedRadixPrefixes() {
        assert JavaScriptNumber.coerce(' \t42\n') == 42
        assert JavaScriptNumber.coerce('0x2A') == 42
        assert JavaScriptNumber.coerce('0b101010') == 42
        assert JavaScriptNumber.coerce('0o52') == 42
        assert JavaScriptNumber.isNaN(JavaScriptNumber.coerce('+0x2A'))
        assert JavaScriptNumber.isNaN(JavaScriptNumber.coerce('-0b10'))
        assert JavaScriptNumber.isNaN(JavaScriptNumber.coerce('0xnot-a-number'))
    }

    void testNumberPredicatesDoNotCoerceNonNumbers() {
        assert JavaScriptNumber.isFinite(1)
        assert !JavaScriptNumber.isFinite(Double.POSITIVE_INFINITY)
        assert !JavaScriptNumber.isFinite('1')
        assert JavaScriptNumber.isInteger(-0.0d)
        assert !JavaScriptNumber.isInteger(1.25d)
        assert JavaScriptNumber.isNaN(Double.NaN)
        assert !JavaScriptNumber.isNaN('NaN')
        assert JavaScriptNumber.isSafeInteger(9_007_199_254_740_991L)
        assert !JavaScriptNumber.isSafeInteger(9_007_199_254_740_992d)
    }

    void testParsersConsumeValidPrefixesAndRejectInvalidRadixes() {
        assert JavaScriptNumber.parseInt('15px') == 15
        assert JavaScriptNumber.parseInt('0xF') == 15
        assert JavaScriptNumber.parseInt('z', 36) == 35
        assert JavaScriptNumber.isNaN(JavaScriptNumber.parseInt('10', 1))
        assert JavaScriptNumber.parseFloat('-1.25e2rest') == -125d
        assert JavaScriptNumber.isNaN(JavaScriptNumber.parseFloat('not-a-number'))
    }

    void testNumberFormattingRangeChecksAndSpecialValues() {
        assert (1.25d).toFixed(1) == '1.3'
        assert (42d).toExponential(0) == '4e+1'
        assert (1234d).toPrecision(3) == '1230'
        assert Double.NaN.toFixed(2) == 'NaN'
        assert shouldFail(JavaScriptRangeError) { 1.toPrecision(0) }
    }
}
