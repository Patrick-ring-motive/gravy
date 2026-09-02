package com.example

/** Groovy vectors derived from core-js typed-array method coverage. */
class CoreJsDerivedTypedArraysTest extends GravyTestCase {
    void testStaticFromAndOfReturnConcreteTypedArray() {
        def from = JavaScriptUint16Array.from([1, 2]) { value, index -> value + index }
        def of = JavaScriptUint16Array.of(3, 4)

        assert from instanceof JavaScriptUint16Array
        assert from.values().collect() == [1, 3]
        assert of.values().collect() == [3, 4]
    }

    void testCopyingMethodsPreserveConcreteConstructorAndLeaveSourceUntouched() {
        def values = new JavaScriptInt8Array([3, 1, 2])

        assert values.toReversed() instanceof JavaScriptInt8Array
        assert values.toReversed().values().collect() == [2, 1, 3]
        assert values.toSorted().values().collect() == [1, 2, 3]
        assert values.values().collect() == [3, 1, 2]
    }

    void testCallbackMethodsUseIndexAndTypedArrayReceiver() {
        def values = new JavaScriptInt8Array([2, 2, 3])
        List<Integer> indexes = []

        assert values.every { value, index, receiver ->
            indexes << index
            receiver.is(values) && value >= 2
        }
        assert indexes == [0, 1, 2]
        assert values.findLastIndex { value -> value == 2 } == 1
        assert values.entries().collect() == [[0, 2], [1, 2], [2, 3]]
    }
}
