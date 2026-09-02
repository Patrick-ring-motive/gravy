package com.example

/** Groovy vectors derived from Test262 WeakRef and FinalizationRegistry argument requirements. */
class Test262DerivedWeakReferencesTest extends GravyTestCase {
    void testWeakRefRequiresObjectTargetAndDerefExposesReferentWhileLive() {
        def target = new Object()
        def reference = new JavaScriptWeakRef(target)

        assert reference.deref().is(target)
        assert shouldFail(JavaScriptTypeError) { new JavaScriptWeakRef(null) }
        assert shouldFail(JavaScriptTypeError) { new JavaScriptWeakRef(false) }
    }

    void testFinalizationRegistryUsesIdentityUnregisterTokens() {
        def registry = new JavaScriptFinalizationRegistry({ value -> })
        def first = new Object()
        def second = new Object()

        registry.register(new Object(), 'first', first)

        assert !registry.unregister(second)
        assert registry.unregister(first)
    }
}
