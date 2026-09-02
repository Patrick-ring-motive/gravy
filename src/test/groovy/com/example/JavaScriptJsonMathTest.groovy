package com.example

/** Local JSON and Math facade checks. */
class JavaScriptJsonMathTest extends GravyTestCase {
    void testJsonParseAndStringifySupportCallbacks() {
        def parsed = JavaScriptJSON.parse('{"count":2,"nested":{"value":3}}') { key, value ->
            key == 'value' ? value * 2 : value
        }

        assert parsed == [count: 2, nested: [value: 6]]
        assert JavaScriptJSON.stringify([keep: 1, skip: 2], { key, value -> key == 'skip' ? JavaScriptJSON.UNDEFINED : value }) ==
            '{"keep":1}'
        assert JavaScriptJSON.stringify(new JavaScriptSet([1, 2])) == '[1,2]'
    }

    void testMathDelegatesStandardOperations() {
        assert JavaScriptMath.abs(-2) == 2d
        assert JavaScriptMath.pow(2, 8) == 256d
        assert JavaScriptMath.sign(-3) == -1d
        assert JavaScriptMath.trunc(-1.8d) == -1d
        assert JavaScriptMath.imul(0xFFFFFFFFL, 5) == -5
        assert Math.abs(JavaScriptMath.log2(8) - 3d) < 0.00000001d
        assert JavaScriptMath.max() == Double.NEGATIVE_INFINITY
        assert JavaScriptMath.min() == Double.POSITIVE_INFINITY
    }
}
