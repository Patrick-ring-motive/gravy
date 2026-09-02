package com.example

import java.lang.reflect.Constructor

/** Reflect forwarding and best-effort Proxy trap checks. */
class JavaScriptReflectProxyTest extends GravyTestCase {
    static class Pair {
        final Object left
        final Object right
        Pair(Object left, Object right) { this.left = left; this.right = right }
    }

    void testReflectApplyGetSetAndConstruct() {
        def target = [value: 1]
        def closure = { value -> prefix + value }
        Constructor constructor = Pair.getConstructor(Object, Object)

        assert JavaScriptReflect.apply(closure, [prefix: 'x'], [2]) == 'x2'
        assert JavaScriptReflect.get(target, 'value') == 1
        assert JavaScriptReflect.set(target, 'value', 2)
        assert JavaScriptReflect.has(target, 'value')
        assert JavaScriptReflect.construct(constructor, [1, 2]).right == 2
    }

    void testReflectPropertyAndPrototypeOperations() {
        def target = [value: 1]
        def prototype = [inherited: true]

        assert JavaScriptReflect.defineProperty(target, 'extra', [value: 2, enumerable: true, configurable: true])
        assert JavaScriptReflect.getOwnPropertyDescriptor(target, 'extra').value == 2
        assert JavaScriptReflect.ownKeys(target) == ['value', 'extra']
        assert JavaScriptReflect.deleteProperty(target, 'extra')
        assert !JavaScriptReflect.has(target, 'extra')
        assert JavaScriptReflect.setPrototypeOf(target, prototype)
        assert JavaScriptReflect.getPrototypeOf(target).is(prototype)
        assert JavaScriptReflect.isExtensible(target)
        assert JavaScriptReflect.preventExtensions(target)
        assert !JavaScriptReflect.isExtensible(target)
    }

    void testReflectAccessorsUseExplicitReceiver() {
        def target = [:]
        def receiver = [value: 'receiver']
        JavaScriptObjectExtensions.install()
        Object.defineProperty(target, 'computed', [get: { delegate.value }, configurable: true])
        Object.defineProperty(target, 'assigned', [set: { Object value -> delegate.seen = value }, configurable: true])

        assert JavaScriptReflect.get(target, 'computed', receiver) == 'receiver'
        assert JavaScriptReflect.set(target, 'assigned', 42, receiver)
        assert receiver.seen == 42
        assert !target.containsKey('seen')
    }

    void testReflectConstructPropagatesProxyNewTargetAndRejectsUnsupportedJvmVariant() {
        def seenNewTarget
        def proxy = new JavaScriptProxy(Pair, [construct: { Object target, Object[] arguments, Object newTarget ->
            seenNewTarget = newTarget
            new Pair(arguments[0], arguments[1])
        }])
        def customNewTarget = JavaScriptFunction.of(Pair)

        assert JavaScriptReflect.construct(proxy, [1, 2], customNewTarget).right == 2
        assert seenNewTarget.is(customNewTarget)
        assert shouldFail(JavaScriptTypeError) { JavaScriptReflect.construct(Pair, [1, 2], customNewTarget) }
    }

    void testProxyTrapsPropertyAccessAndReflectForwarding() {
        List<String> events = []
        def proxy = new JavaScriptProxy([value: 1], [
            get: { Object target, Object property, Object receiver ->
                events << "get:${property}"
                property == 'double' ? target.value * 2 : JavaScriptReflect.get(target, property, receiver)
            },
            set: { Object target, Object property, Object value, Object receiver ->
                events << "set:${property}"
                JavaScriptReflect.set(target, property, value, receiver)
            },
            ownKeys: { Object target -> ['value', 'virtual'] }
        ])

        assert proxy.value == 1
        assert proxy.double == 2
        assert proxy['value'] == 1
        proxy.value = 3
        assert JavaScriptReflect.get(proxy, 'value') == 3
        assert JavaScriptReflect.set(proxy, 'value', 4)
        assert JavaScriptReflect.ownKeys(proxy) == ['value', 'virtual']
        assert events == ['get:value', 'get:double', 'get:value', 'set:value', 'get:value', 'set:value']
    }

    void testProxyForwardsDescriptorPrototypeAndExtensibilityTraps() {
        List<String> events = []
        def target = [value: 1]
        def prototype = [inherited: true]
        def proxy = new JavaScriptProxy(target, [
            has: { Object object, Object property -> events << 'has'; false },
            defineProperty: { Object object, Object property, Map descriptor ->
                events << 'defineProperty'
                JavaScriptReflect.defineProperty(object, property, descriptor)
            },
            getOwnPropertyDescriptor: { Object object, Object property ->
                events << 'getOwnPropertyDescriptor'
                JavaScriptReflect.getOwnPropertyDescriptor(object, property)
            },
            deleteProperty: { Object object, Object property ->
                events << 'deleteProperty'
                JavaScriptReflect.deleteProperty(object, property)
            },
            getPrototypeOf: { Object object -> events << 'getPrototypeOf'; JavaScriptReflect.getPrototypeOf(object) },
            setPrototypeOf: { Object object, Object value -> events << 'setPrototypeOf'; JavaScriptReflect.setPrototypeOf(object, value) },
            isExtensible: { Object object -> events << 'isExtensible'; JavaScriptReflect.isExtensible(object) },
            preventExtensions: { Object object -> events << 'preventExtensions'; JavaScriptReflect.preventExtensions(object) }
        ])

        assert !proxy.has('value')
        assert proxy.defineProperty('defined', [value: 2, configurable: true])
        assert proxy.getOwnPropertyDescriptor('defined').value == 2
        assert proxy.deleteProperty('defined')
        assert JavaScriptReflect.setPrototypeOf(proxy, prototype)
        assert JavaScriptReflect.getPrototypeOf(proxy).is(prototype)
        assert JavaScriptReflect.isExtensible(proxy)
        assert JavaScriptReflect.preventExtensions(proxy)
        assert !JavaScriptReflect.isExtensible(proxy)
        assert events == ['has', 'defineProperty', 'getOwnPropertyDescriptor', 'deleteProperty', 'setPrototypeOf', 'getPrototypeOf', 'isExtensible', 'preventExtensions', 'isExtensible']
    }

    void testProxyRevocableAndCallableTraps() {
        def callable = new JavaScriptProxy({ Object value -> value + 1 }, [
            apply: { Object target, Object thisArgument, Object[] arguments -> target.call(arguments[0] * 2) }
        ])
        def constructor = new JavaScriptProxy(Pair, [
            construct: { Class target, Object[] arguments, Object newTarget -> new Pair(arguments[1], arguments[0]) }
        ])
        def revocable = JavaScriptProxy.revocable([value: 1], [:])

        assert JavaScriptReflect.apply(callable, null, [2]) == 5
        assert JavaScriptReflect.construct(constructor, ['left', 'right']).left == 'right'
        assert revocable.proxy.value == 1
        revocable.revoke()
        shouldFail(JavaScriptTypeError) { revocable.proxy.value }
    }
}
