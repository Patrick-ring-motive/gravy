package com.example

/**
 * Independent Groovy vectors derived from current core-js Object module behavior.
 *
 * Covers iterable entries, callback indexes, descriptors, and prototype state.
 * Proxy invariants and direct Java-native writes remain outside this adapter.
 */
class CoreJsDerivedObjectTest extends GravyTestCase {
    void setUp() {
        JavaScriptObjectExtensions.install()
    }

    void testFromEntriesConsumesIteratorAndKeepsLastDuplicateEntry() {
        JavaScriptObject object = Object.fromEntries([['duplicate', 1], ['duplicate', 2], [null, 3]].iterator())

        assert Object.entries(object) == [['duplicate', 2], ['null', 3]]
    }

    void testGroupByReceivesValueAndIndexAndKeepsBucketOrder() {
        List<List<Object>> calls = []
        JavaScriptObject groups = Object.groupBy([10, 20, 30]) { value, index ->
            calls << [value, index]
            index % 2
        }

        assert calls == [[10, 0], [20, 1], [30, 2]]
        assert Object.entries(groups) == [['0', [10, 30]], ['1', [20]]]
    }

    void testDescriptorAccessorsReadAndWriteThroughObjectOperations() {
        JavaScriptObject object = Object.create(null)
        List<Object> received = []

        Object.defineProperty(object, 'answer', [get: { 42 }, enumerable: true, configurable: true])
        Object.defineProperty(object, 'captured', [set: { value -> received << value }, enumerable: true, configurable: true])
        Object.assign(object, [captured: 'value'])

        assert Object.entries(object) == [['answer', 42], ['captured', null]]
        assert object.__lookupGetter__('answer').call() == 42
        assert received == ['value']
    }

    void testNonExtensibleObjectRejectsNewPrototypeButAcceptsSamePrototype() {
        JavaScriptObject object = Object.create(null)
        Object.preventExtensions(object)

        assert Object.setPrototypeOf(object, null).is(object)
        shouldFail(IllegalStateException) { Object.setPrototypeOf(object, [parent: true]) }
    }
}
