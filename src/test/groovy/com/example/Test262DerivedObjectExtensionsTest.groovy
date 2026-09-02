package com.example

/**
 * Independent Groovy vectors derived from Test262 Object requirements.
 *
 * Covers property ordering, descriptor defaults, prototype chains, assignment,
 * SameValue comparison, grouped values, and extensibility state. Java Symbols,
 * Proxy invariants, strict-mode assignment behavior, and native Java map writes
 * are outside this adapter's descriptor model.
 */
class Test262DerivedObjectExtensionsTest extends GravyTestCase {
    void setUp() {
        JavaScriptObjectExtensions.install()
    }

    void testKeysOrdersCanonicalIndexesBeforeOtherPropertyNames() {
        Map object = ['100': 'a', '2': 'b', '7': 'c', zebra: 1, ant: 2]

        assert Object.keys(object) == ['2', '7', '100', 'zebra', 'ant']
        assert Object.values(object) == ['b', 'c', 'a', 1, 2]
        assert Object.entries(object) == [['2', 'b'], ['7', 'c'], ['100', 'a'], ['zebra', 1], ['ant', 2]]
    }

    void testDescriptorDefaultsAreFalseAndExcludedFromEnumeration() {
        JavaScriptObject object = Object.create(null)

        Object.defineProperty(object, 'property', [value: 1])

        assert Object.getOwnPropertyDescriptor(object, 'property') == [
            value: 1, writable: false, enumerable: false, configurable: false
        ]
        assert Object.keys(object) == []
        assert Object.getOwnPropertyNames(object) == ['property']
    }

    void testAssignCopiesOnlyEnumerableOwnPropertiesAndGetterValues() {
        JavaScriptObject source = Object.create(null)
        JavaScriptObject target = Object.create(null)

        Object.defineProperty(source, 'visible', [value: 1, enumerable: true, writable: true, configurable: true])
        Object.defineProperty(source, 'hidden', [value: 2])
        Object.defineProperty(source, 'computed', [get: { 3 }, enumerable: true, configurable: true])
        Object.assign(target, source)

        assert Object.entries(target) == [['visible', 1], ['computed', 3]]
        assert !Object.hasOwn(target, 'hidden')
    }

    void testFromEntriesUsesFirstTwoValuesFromEachEntry() {
        JavaScriptObject object = Object.fromEntries([
            ['first', 1, 'ignored'],
            ['second', 2] as Object[],
            new AbstractMap.SimpleEntry('third', 3)
        ])

        assert Object.entries(object) == [['first', 1], ['second', 2], ['third', 3]]
        shouldFail(IllegalArgumentException) { Object.fromEntries([[]]) }
    }

    void testGroupByConvertsGroupKeysToPropertyNames() {
        JavaScriptObject groups = Object.groupBy([0, 1, 2, 3]) { value -> value == 0 ? null : value % 2 == 0 }

        assert groups.'null' == [0]
        assert groups.true == [2]
        assert groups.false == [1, 3]
        assert Object.keys(groups) == ['null', 'false', 'true']
    }

    void testObjectIsUsesSameValueRulesForNaNAndSignedZero() {
        assert Object.is(Double.NaN, Double.NaN)
        assert Object.is(Float.NaN, Double.NaN)
        assert Object.is(0d, 0)
        assert !Object.is(-0d, 0d)
        assert Object.is(true, true)
        assert !Object.is(true, false)
    }

    void testCreateNullPrototypeAndInheritanceAreDistinctFromOwnProperties() {
        JavaScriptObject nullPrototype = Object.create(null)
        JavaScriptObject child = Object.create(nullPrototype)

        Object.assign(nullPrototype, [shared: 'value'])
        Object.assign(child, [own: 'value'])

        assert Object.getPrototypeOf(nullPrototype) == null
        assert child.shared == 'value'
        assert child.own == 'value'
        assert Object.keys(child) == ['own']
        assert nullPrototype.isPrototypeOf(child)
        assert !child.hasOwnProperty('shared')
    }

    void testPreventExtensionsSealAndFreezeChangeAdapterWrites() {
        JavaScriptObject object = Object.create(null)
        Object.assign(object, [value: 1])
        Object.preventExtensions(object)

        assert !Object.isExtensible(object)
        Object.assign(object, [value: 2])
        assert object.value == 2
        shouldFail(IllegalStateException) { Object.assign(object, [extra: 3]) }

        Object.seal(object)
        assert Object.isSealed(object)
        Object.freeze(object)
        assert Object.isFrozen(object)
        shouldFail(IllegalStateException) { Object.assign(object, [value: 4]) }
    }

    void testPrototypeSetterAndDeprecatedGetterSetterHelpersRoundTrip() {
        JavaScriptObject object = Object.create(null)
        List values = []

        object.__proto__ = [inherited: 1]
        object.__defineGetter__('answer', { 42 })
        object.__defineSetter__('captured', { value -> values << value })
        Object.assign(object, [captured: 'x'])

        assert object.__proto__ == [inherited: 1]
        assert object.inherited == 1
        assert object.answer == 42
        assert object.__lookupGetter__('answer').call() == 42
        assert object.__lookupSetter__('captured') instanceof Closure
        assert values == ['x']
    }
}
