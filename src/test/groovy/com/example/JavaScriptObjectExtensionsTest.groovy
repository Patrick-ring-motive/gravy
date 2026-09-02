package com.example

class JavaScriptObjectExtensionsTest extends GravyTestCase {
    static class Person {
        String name
        int age
    }

    void setUp() {
        JavaScriptObjectExtensions.install()
    }

    void testAssignEntriesKeysAndValues() {
        Map target = [first: 1]

        assert Object.assign(target, [second: 2], null, [third: 3]).is(target)
        assert target == [first: 1, second: 2, third: 3]
        assert Object.keys(target) == ['first', 'second', 'third']
        assert Object.values(target) == [1, 2, 3]
        assert Object.entries(target) == [['first', 1], ['second', 2], ['third', 3]]
    }

    void testCreateAndPrototypeOperations() {
        Map prototype = [inherited: 'parent']
        JavaScriptObject object = Object.create(prototype, [own: [value: 'child', enumerable: true, writable: true, configurable: true]])

        assert Object.getPrototypeOf(object).is(prototype)
        assert object.own == 'child'
        assert object.inherited == 'parent'
        assert object.hasOwnProperty('own')
        assert !object.hasOwnProperty('inherited')
        assert prototype.isPrototypeOf(object)
        assert Object.setPrototypeOf(object, [next: 'prototype']).is(object)
        assert object.next == 'prototype'
        assert Object.getPrototypeOf(object) == [next: 'prototype']
    }

    void testDefinePropertyAndDescriptors() {
        JavaScriptObject object = Object.create(null)

        assert Object.defineProperty(object, 'hidden', [value: 4]).is(object)
        assert Object.getOwnPropertyDescriptor(object, 'hidden') == [value: 4, writable: false, enumerable: false, configurable: false]
        assert Object.keys(object) == []
        assert Object.getOwnPropertyNames(object) == ['hidden']
        assert !object.propertyIsEnumerable('hidden')
        shouldFail(IllegalStateException) { Object.defineProperty(object, 'hidden', [value: 5]) }
    }

    void testAccessorDescriptorsAndDeprecatedHelpers() {
        JavaScriptObject object = Object.create(null)
        List<Integer> received = []

        object.__defineGetter__('computed', { 6 * 7 })
        object.__defineSetter__('received', { value -> received << value })
        assert object.__lookupGetter__('computed') instanceof Closure
        assert object.__lookupSetter__('received') instanceof Closure
        assert object.computed == 42
        Object.assign(object, [received: 9])
        assert received == [9]
        assert Object.entries(object) == [['computed', 42], ['received', null]]
    }

    void testDefinePropertiesAndDescriptorsCollection() {
        JavaScriptObject object = Object.create(null)

        Object.defineProperties(object, [
            visible: [value: 1, enumerable: true, writable: true, configurable: true],
            hidden : [value: 2]
        ])

        assert Object.entries(object) == [['visible', 1]]
        assert Object.getOwnPropertyDescriptors(object) == [
            visible: [value: 1, writable: true, enumerable: true, configurable: true],
            hidden : [value: 2, writable: false, enumerable: false, configurable: false]
        ]
        shouldFail(IllegalArgumentException) { Object.defineProperty(object, 'invalid', [value: 1, get: { 1 }]) }
    }

    void testFromEntriesAndGroupBy() {
        JavaScriptObject object = Object.fromEntries([['one', 1], ['two', 2]])
        JavaScriptObject groups = Object.groupBy([1, 2, 3, 4]) { value, index -> value % 2 == 0 ? 'even' : 'odd' }

        assert Object.getPrototypeOf(object) == null
        assert object.one == 1
        assert object.two == 2
        assert groups.odd == [1, 3]
        assert groups.even == [2, 4]
        assert Object.getPrototypeOf(groups) == null
    }

    void testObjectIsAndPropertyOrder() {
        Map object = ['b': 1, '2': 2, '1': 3, 'a': 4]

        assert Object.is(Double.NaN, Double.NaN)
        assert !Object.is(-0.0d, 0.0d)
        assert Object.is('value', new String('value'))
        assert !Object.is(new Object(), new Object())
        assert Object.keys(object) == ['1', '2', 'b', 'a']
    }

    void testExtensibilitySealingAndFreezing() {
        JavaScriptObject extensible = Object.create(null)
        Object.preventExtensions(extensible)
        assert !Object.isExtensible(extensible)
        shouldFail(IllegalStateException) { Object.assign(extensible, [newValue: 1]) }

        JavaScriptObject sealed = Object.create(null)
        Object.defineProperty(sealed, 'value', [value: 1, writable: true, enumerable: true, configurable: true])
        Object.seal(sealed)
        assert Object.isSealed(sealed)
        Object.assign(sealed, [value: 2])
        assert sealed.value == 2
        shouldFail(IllegalStateException) { Object.assign(sealed, [newValue: 3]) }

        JavaScriptObject frozen = Object.create(null)
        Object.defineProperty(frozen, 'value', [value: 1, writable: true, enumerable: true, configurable: true])
        Object.freeze(frozen)
        assert Object.isFrozen(frozen)
        shouldFail(IllegalStateException) { Object.assign(frozen, [value: 2]) }
    }

    void testArraysListsAndPogosExposeOwnProperties() {
        int[] array = [4, 5]
        List list = ['a', 'b']
        Person person = new Person(name: 'Ada', age: 37)

        assert Object.keys(array) == ['0', '1']
        assert Object.getOwnPropertyNames(array) == ['0', '1', 'length']
        assert Object.getOwnPropertyDescriptor(array, 'length').enumerable == false
        assert Object.entries(list) == [['0', 'a'], ['1', 'b']]
        assert Object.keys(person).sort() == ['age', 'name']
        assert Object.entries(person).sort { left, right -> left[0] <=> right[0] } == [['age', 37], ['name', 'Ada']]
    }

    void testPrototypeLookupForClosuresUsesStaticObjectMethod() {
        Closure callback = {}

        assert Object.getPrototypeOf(callback) == callback.class
    }

    void testClassObjectsReceiveInstanceTransforms() {
        assert String.constructor == Class
        assert String.get__proto__() == Class
        assert !String.hasOwnProperty('missing')
        assert String.toJsObjectString() == '[object Object]'
        assert String.toLocaleString() == '[object Object]'
    }

    void testInstanceConversions() {
        Object value = new Object()
        List<Object> values = []

        assert value.constructor == Object
        assert value.prototype.is(Object.metaClass)
        assert values.prototype.is(ArrayList.metaClass)
        assert [name: 'Groovy'].valueOf() == [name: 'Groovy']
        assert [name: 'Groovy'].toJsObjectString() == '[object Object]'
        assert ([1, 2] as int[]).toJsObjectString() == '[object Array]'
        assert 'value'.toJsObjectString() == '[object String]'
        assert [name: 'Groovy'].toLocaleString() == '[object Object]'
        assert Object.getOwnPropertySymbols([name: 'Groovy']) == []
    }
}
