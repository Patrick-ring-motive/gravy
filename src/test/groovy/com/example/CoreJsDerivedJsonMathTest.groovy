package com.example

/** Groovy vectors derived from current core-js JSON and Math unit-global modules. */
class CoreJsDerivedJsonMathTest extends GravyTestCase {
    void testJsonParseReviverReceivesArrayIndexes() {
        List<List<Object>> calls = []
        def parsed = JavaScriptJSON.parse('[1,2]') { key, value ->
            calls << [key, value]
            value
        }

        assert parsed == [1, 2]
        assert calls == [[0, 1], [1, 2], ['', [1, 2]]]
    }

    void testJsonStringifyReplacerArrayFiltersObjectKeys() {
        assert JavaScriptJSON.stringify([first: 1, second: 2], ['second']) == '{"second":2}'
    }

    void testMathPolyfillOperationsMatchJvmResults() {
        assert Math.abs(JavaScriptMath.atanh(0.5d) - 0.5493061443340549d) < 0.00000000001d
        assert JavaScriptMath.cbrt(27) == 3d
        assert JavaScriptMath.sign(-0.0d).doubleValue() == -0.0d
        assert JavaScriptMath.trunc(1.9d) == 1d
    }
}
