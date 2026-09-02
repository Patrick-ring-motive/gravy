package com.example

/** Groovy vectors derived from current core-js Number unit-global modules. */
class CoreJsDerivedNumberTest extends GravyTestCase {
    void testConstructorFacadeConvertsPrimitiveInputs() {
        assert JavaScriptNumber.call() == 0
        assert JavaScriptNumber.call(null) == 0
        assert JavaScriptNumber.call(false) == 0
        assert JavaScriptNumber.call(true) == 1
        assert JavaScriptNumber.call('42.42') == 42.42d
        assert JavaScriptNumber.call('0X42') == 66
        assert JavaScriptNumber.isNaN(JavaScriptNumber.call('0xzzz'))
        assert JavaScriptNumber.isNaN(JavaScriptNumber.call('-0x1'))
    }

    void testNumberWrapperExposesItsPrimitiveValue() {
        def wrapped = new JavaScriptNumber('42')

        assert wrapped.valueOf() == 42
        assert wrapped.intValue() == 42
        assert wrapped.toFixed(2) == '42.00'
    }

    void testCoreStaticNumberMethodsUseNumberOnlyInputs() {
        assert JavaScriptNumber.parseInt('08') == 8
        assert JavaScriptNumber.parseFloat('1.5e2x') == 150d
        assert JavaScriptNumber.isFinite(1.5d)
        assert !JavaScriptNumber.isFinite(null)
        assert JavaScriptNumber.isInteger(1L)
        assert !JavaScriptNumber.isInteger(Double.NaN)
        assert JavaScriptNumber.isNaN(Double.NaN)
        assert !JavaScriptNumber.isNaN(null)
    }
}
