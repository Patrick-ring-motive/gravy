package com.example

/** Groovy vectors derived from Test262 JSON and Math global-object behavior. */
class Test262DerivedJsonMathTest extends GravyTestCase {
    void testJsonReviverWalksChildrenBeforeParents() {
        List<String> keys = []
        def parsed = JavaScriptJSON.parse('{"outer":{"inner":1}}') { key, value ->
            keys << key
            value
        }

        assert parsed == [outer: [inner: 1]]
        assert keys == ['inner', 'outer', '']
    }

    void testJsonReviverUndefinedRemovesObjectProperty() {
        def parsed = JavaScriptJSON.parse('{"keep":1,"drop":2}') { key, value ->
            key == 'drop' ? JavaScriptJSON.UNDEFINED : value
        }

        assert parsed == [keep: 1]
    }

    void testMathSpecialArgumentAndNoArgumentRules() {
        assert JavaScriptMath.max() == Double.NEGATIVE_INFINITY
        assert JavaScriptMath.min() == Double.POSITIVE_INFINITY
        assert Double.isNaN(JavaScriptMath.sqrt(-1))
        assert JavaScriptMath.clz32(1) == 31
        assert JavaScriptMath.clz32(0) == 32
    }
}
