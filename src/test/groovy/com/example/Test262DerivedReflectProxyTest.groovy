package com.example

/**
 * Groovy vectors derived from selected Test262 Proxy and Reflect tests:
 * Proxy/get/call-parameters.js, Proxy/set/call-parameters.js,
 * Proxy/revocable/revoke.js, Reflect/get/return-value.js, and
 * Reflect/ownKeys/return-on-corresponding-order.js.
 *
 * JVM bytecode interception and ECMAScript Proxy invariants remain unsupported.
 */
class Test262DerivedReflectProxyTest extends GravyTestCase {
    void setUp() {
        JavaScriptObjectExtensions.install()
    }

    void testGetTrapReceivesHandlerTargetPropertyAndProxyReceiver() {
        Map target = [attr: 1]
        Map handler = [:]
        List<Object> calls = []
        handler.get = { Object trappedTarget, Object property, Object receiver ->
            calls << [delegate, trappedTarget, property, receiver]
            null
        }
        def proxy = new JavaScriptProxy(target, handler)

        assert proxy.attr == null
        assert proxy['attr'] == null
        assert calls.size() == 2
        calls.each { List<Object> call ->
            assert call[0].is(handler)
            assert call[1].is(target)
            assert call[2] == 'attr'
            assert call[3].is(proxy)
        }
    }

    void testSetTrapReceivesHandlerTargetPropertyValueAndProxyReceiver() {
        Map target = [:]
        Map handler = [:]
        List<Object> call = []
        handler.set = { Object trappedTarget, Object property, Object value, Object receiver ->
            call = [delegate, trappedTarget, property, value, receiver]
            true
        }
        def proxy = new JavaScriptProxy(target, handler)

        proxy.attr = 'value'

        assert call[0].is(handler)
        assert call[1].is(target)
        assert call[2] == 'attr'
        assert call[3] == 'value'
        assert call[4].is(proxy)
        assert !target.containsKey('attr')
    }

    void testRevocableProxyRejectsOperationsAfterRevoke() {
        def revocable = JavaScriptProxy.revocable([value: 1], [:])

        assert revocable.proxy.value == 1
        assert revocable.revoke() == null
        assert revocable.revoke() == null
        shouldFail(JavaScriptTypeError) { revocable.proxy.value }
    }

    void testReflectGetReturnsOwnInheritedAccessorAndMissingValues() {
        JavaScriptObject prototype = Object.create(null)
        JavaScriptObject target = Object.create(prototype)
        Object.defineProperty(prototype, 'inherited', [value: 42, enumerable: true])
        Object.defineProperty(target, 'computed', [get: { 'computed' }, enumerable: true])
        target.put('own', 'value')

        assert JavaScriptReflect.get(target, 'own') == 'value'
        assert JavaScriptReflect.get(target, 'computed') == 'computed'
        assert JavaScriptReflect.get(target, 'inherited') == 42
        assert JavaScriptReflect.get(target, 'missing') == null
    }

    void testReflectOwnKeysIncludesHiddenStringsAndSymbolsButNotInheritedKeys() {
        JavaScriptObject prototype = Object.create(null)
        JavaScriptObject target = Object.create(prototype)
        JavaScriptSymbol symbol = JavaScriptSymbol.create('key')
        prototype.put('inherited', true)
        target.put('visible', 1)
        target.put(symbol, 3)
        Object.defineProperty(target, 'hidden', [value: 2])

        List<Object> keys = JavaScriptReflect.ownKeys(target)

        assert keys == ['visible', 'hidden', symbol]
    }
}
