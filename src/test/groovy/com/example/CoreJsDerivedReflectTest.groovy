package com.example

import java.lang.reflect.Constructor

/**
 * Groovy vectors derived from core-js unit globals:
 * es.reflect.apply.js, es.reflect.construct.js, es.reflect.define-property.js,
 * es.reflect.delete-property.js, es.reflect.get.js, es.reflect.has.js,
 * es.reflect.is-extensible.js, es.reflect.own-keys.js,
 * es.reflect.prevent-extensions.js, and es.reflect.set.js.
 * core-js does not polyfill Proxy, so Proxy vectors are Test262-derived.
 */
class CoreJsDerivedReflectTest extends GravyTestCase {
    static class Pair {
        final Object left
        final Object right
        Pair(Object left, Object right) { this.left = left; this.right = right }
    }

    void setUp() {
        JavaScriptObjectExtensions.install()
    }

    void testApplyUsesGivenThisAndArgumentsList() {
        Closure function = { Object left, Object right -> prefix + left + right }

        assert JavaScriptReflect.apply(function, [prefix: 'x'], ['y', 'z']) == 'xyz'
        shouldFail(JavaScriptTypeError) { JavaScriptReflect.apply(42, null, []) }
    }

    void testConstructInvokesReflectiveConstructor() {
        Constructor constructor = Pair.getConstructor(Object, Object)

        def pair = JavaScriptReflect.construct(constructor, ['left', 'right'])

        assert pair.left == 'left'
        assert pair.right == 'right'
    }

    void testDefinePropertyAndDescriptorReturnBooleanAndDescriptor() {
        JavaScriptObject target = Object.create(null)

        assert JavaScriptReflect.defineProperty(target, 'value', [value: 123, enumerable: true])
        assert JavaScriptReflect.getOwnPropertyDescriptor(target, 'value') == [
            value: 123, writable: false, enumerable: true, configurable: false
        ]
        assert !JavaScriptReflect.defineProperty(target, 'value', [value: 42])
    }

    void testDeletePropertyRespectsConfigurableDescriptor() {
        JavaScriptObject target = Object.create(null)
        Object.defineProperty(target, 'fixed', [value: 1])
        Object.defineProperty(target, 'open', [value: 2, configurable: true])

        assert !JavaScriptReflect.deleteProperty(target, 'fixed')
        assert JavaScriptReflect.deleteProperty(target, 'open')
        assert JavaScriptReflect.get(target, 'open') == null
    }

    void testGetHasSetAndOwnKeysForwardMapOperations() {
        Map target = [value: 1]

        assert JavaScriptReflect.get(target, 'value') == 1
        assert JavaScriptReflect.has(target, 'value')
        assert JavaScriptReflect.set(target, 'value', 2)
        assert target.value == 2
        assert JavaScriptReflect.ownKeys(target).contains('value')
    }

    void testPreventExtensionsMakesReflectSetReturnFalseForNewMapProperty() {
        Map target = [:]

        assert JavaScriptReflect.isExtensible(target)
        assert JavaScriptReflect.preventExtensions(target)
        assert !JavaScriptReflect.isExtensible(target)
        assert !JavaScriptReflect.set(target, 'newValue', 1)
    }
}
