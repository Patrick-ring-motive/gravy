package com.example

/** Derived Number constructor and prototype-method checks. */
class JavaScriptNumberTest extends GravyTestCase {
    void testCoercionPreservesPracticalJvmNumericTypes() {
        assert JavaScriptNumber.coerce(null) == 0
        assert JavaScriptNumber.coerce('') == 0
        assert JavaScriptNumber.coerce('0x10') == 16
        assert JavaScriptNumber.isNaN(JavaScriptNumber.coerce('-0b10'))
        assert JavaScriptNumber.coerce('12') instanceof Integer
        assert JavaScriptNumber.coerce('999999999999999999999') instanceof BigInteger
        assert JavaScriptNumber.isNaN(JavaScriptNumber.coerce('nope'))
    }

    void testStaticPredicatesAndParsers() {
        assert JavaScriptNumber.parseInt('  -0xF') == -15
        assert JavaScriptNumber.parseInt('11', 2) == 3
        assert JavaScriptNumber.isNaN(JavaScriptNumber.parseInt('z', 10))
        assert JavaScriptNumber.parseFloat('3.14px') == 3.14d
        assert JavaScriptNumber.isInteger(4L)
        assert !JavaScriptNumber.isFinite('4')
        assert JavaScriptNumber.isSafeInteger(9007199254740991L)
    }

    void testExtensionMethodsCoverDifferentNumericWrappers() {
        assert (12 as Integer).toFixed(2) == '12.00'
        assert (12L).toExponential(1) == '1.2e+1'
        assert (12.34f).toPrecision(3) == '12.3'
        assert (BigDecimal.valueOf(2.50d)).toJsString() == '2.5'
        assert shouldFail(JavaScriptRangeError) { 1.toFixed(101) }
    }

    void testInstantiatableNumberWrapsCoercedValue() {
        def value = new JavaScriptNumber('42')
        assert value.intValue() == 42
        assert value.toString() == '42'
    }
}
