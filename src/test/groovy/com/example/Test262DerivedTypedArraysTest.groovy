package com.example

/** Groovy vectors derived from Test262 ArrayBuffer and TypedArray constructor/view requirements. */
class Test262DerivedTypedArraysTest extends GravyTestCase {
    void testConstructorsAllocateExpectedLengthsAndExposeMetadata() {
        def values = new JavaScriptInt32Array(3)

        assert values.length == 3
        assert values.byteOffset == 0
        assert values.byteLength == 12
        assert values.bytesPerElement == 4
        assert values.buffer.byteLength == 12
        assert values.values().collect() == [0, 0, 0]
    }

    void testSetSnapshotsOverlappingSourceBeforeWriting() {
        def values = new JavaScriptUint8Array([1, 2, 3, 4])

        values.set(values.subarray(0, 3), 1)

        assert values.values().collect() == [1, 1, 2, 3]
    }

    void testNaNUsesSameValueZeroForIncludesButNotIndexOf() {
        def values = new JavaScriptFloat64Array([Double.NaN, 2d])

        assert values.includes(Double.NaN)
        assert values.indexOf(Double.NaN) == -1
        assert values.indexOf(2d) == 1
    }

    void testArrayBufferSliceProducesIndependentCopy() {
        def original = new JavaScriptUint8Array([1, 2, 3, 4])
        def copied = original.buffer.slice(1, 3)
        def values = new JavaScriptUint8Array(copied)

        values[0] = 9

        assert original.values().collect() == [1, 2, 3, 4]
        assert values.values().collect() == [9, 3]
    }

    void testRelativeIndexesAndReversedRangesFollowTypedArraySemantics() {
        def values = new JavaScriptUint8Array([1, 2, 3])

        assert values.at(-1) == 3
        assert values.at(-4) == null
        assert values.at(Double.NEGATIVE_INFINITY) == null
        assert values.slice(2, 1).length == 0
        assert values.subarray(2, 1).length == 0
        assert values.buffer.slice(2, 1).byteLength == 0
    }
}
