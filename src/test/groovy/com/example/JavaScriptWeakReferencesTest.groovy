package com.example

/** Local WeakRef and FinalizationRegistry approximation checks. */
class JavaScriptWeakReferencesTest extends GravyTestCase {
    void testWeakRefDerefReturnsLiveTarget() {
        def target = new Object()
        def reference = new JavaScriptWeakRef(target)

        assert reference.deref().is(target)
        assert shouldFail(JavaScriptTypeError) { new JavaScriptWeakRef('value') }
    }

    void testFinalizationRegistryUnregistersAllMatchingTokenRegistrations() {
        List<Object> cleaned = []
        def registry = new JavaScriptFinalizationRegistry({ heldValue -> cleaned << heldValue })
        def token = new Object()

        registry.register(new Object(), 'first', token)
        registry.register(new Object(), 'second', token)

        assert registry.unregister(token)
        assert !registry.unregister(token)
        assert cleaned.isEmpty()
        assert shouldFail(JavaScriptTypeError) { registry.unregister('not-an-object') }
    }

    void testFinalizationRegistryRejectsInvalidTargetAndCallback() {
        assert shouldFail(JavaScriptTypeError) { new JavaScriptFinalizationRegistry(null) }
        assert shouldFail(JavaScriptTypeError) { new JavaScriptFinalizationRegistry(1) }
        def registry = new JavaScriptFinalizationRegistry({ ignored -> })

        assert shouldFail(JavaScriptTypeError) { registry.register(1, 'held') }
        assert shouldFail(JavaScriptTypeError) { registry.register(new Object(), 'held', 'token') }
    }
}
