package com.example

/** Local WeakMap and WeakSet approximation checks. */
class JavaScriptWeakCollectionsTest extends GravyTestCase {
    void testWeakMapStoresObjectKeys() {
        def key = new Object()
        def map = new JavaScriptWeakMap().set(key, 'value')

        assert map.get(key) == 'value'
        assert map.has(key)
        assert map.delete(key)
        assert !map.has(key)
        assert shouldFail(JavaScriptTypeError) { map.set('key', 'value') }
    }

    void testWeakSetChainsObjectValues() {
        def value = new Object()
        def set = new JavaScriptWeakSet()

        assert set.add(value).is(set)
        assert set.has(value)
        assert set.delete(value)
        assert !set.has(value)
        assert shouldFail(JavaScriptTypeError) { set.add(1) }
    }
}
