package com.example

/**
 * Groovy vectors derived from Test262 Map and Set iterable-constructor and
 * SameValueZero requirements.
 */
class Test262DerivedCollectionsTest extends GravyTestCase {
    void testMapConstructorConsumesIterableKeyValuePairs() {
        def map = new JavaScriptMap([['attr', 1], ['foo', 2]])

        assert map.size == 2
        assert map.get('attr') == 1
        assert map.get('foo') == 2
        assert shouldFail(JavaScriptTypeError) { new JavaScriptMap([[1]]) }
    }

    void testSetConstructorConsumesIterableAndDeduplicatesSameValueZero() {
        def set = new JavaScriptSet([Double.NaN, Double.NaN, -0.0d, 0.0d, 'x'])

        assert set.size == 3
        assert set.has(Double.NaN)
        assert set.has(0.0d)
        assert set.entries().collect() == [[Double.NaN, Double.NaN], [0.0d, 0.0d], ['x', 'x']]
    }

    void testCollectionMethodsKeepObjectKeysByIdentity() {
        def first = new Object()
        def second = new Object()
        def map = new JavaScriptMap().set(first, 'first')
        def set = new JavaScriptSet().add(first)

        assert map.get(first) == 'first'
        assert map.get(second) == null
        assert set.has(first)
        assert !set.has(second)
    }
}
