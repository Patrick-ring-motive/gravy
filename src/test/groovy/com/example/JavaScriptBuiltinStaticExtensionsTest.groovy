package com.example

import java.math.BigDecimal
import java.math.BigInteger
import java.util.ArrayList
import java.util.Date
import java.util.Map
import java.util.regex.Pattern

/** Covers static JavaScript APIs exposed on native JVM constructors. */
class JavaScriptBuiltinStaticExtensionsTest extends GravyTestCase {
    void testNativeArrayConstructorsExposeStaticArrayMethods() {
        assert List.from('A😄') == ['A', '😄'] as Object[]
        assert ArrayList.from([2, 3]) { value, index -> value * index } == [0, 3] as Object[]
        assert ArrayList.of('a', 2) == ['a', 2] as Object[]
        assert ArrayList.isArray(new JavaScriptArray())
    }

    void testNativeNumberConstructorsExposeStaticNumberMethods() {
        assert Number.parseInt('  -0xF') == -15
        assert Number.parseFloat('3.14px') == 3.14d
        assert Integer.isInteger(4L)
        assert Long.isFinite(4L)
        assert BigInteger.isSafeInteger(9_007_199_254_740_991L)
        assert !BigDecimal.isNaN('NaN')
    }

    void testNativeMapConstructorExposesGroupBy() {
        JavaScriptMap groups = Map.groupBy([1, 2, 3, 4]) { value -> value % 2 }

        assert groups.get(0) == [2, 4]
        assert groups.get(1) == [1, 3]
    }

    void testNativeDateAndPatternConstructorsExposeStaticMethods() {
        assert Date.now() > 0L
        assert Pattern.escape('a+b') == 'a\\+b'
    }
}
