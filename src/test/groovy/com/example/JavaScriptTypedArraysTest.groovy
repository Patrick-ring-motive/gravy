package com.example

import java.math.BigInteger

/** Local ArrayBuffer, typed-array, and DataView approximation checks. */
class JavaScriptTypedArraysTest extends GravyTestCase {
    void testTypedViewsShareBufferWhileSliceCopies() {
        def buffer = new JavaScriptArrayBuffer(8)
        def bytes = new JavaScriptUint8Array(buffer)
        def words = new JavaScriptInt16Array(buffer, 2, 2)

        words[0] = 0x0102
        assert bytes[2] == 2
        assert bytes[3] == 1
        assert words.buffer.is(buffer)
        assert words.byteOffset == 2
        assert words.byteLength == 4

        def shared = words.subarray(0, 1)
        shared[0] = 7
        assert words[0] == 7

        def copied = words.slice(0, 1)
        copied[0] = 8
        assert words[0] == 7
        assert copied[0] == 8
        assert JavaScriptArrayBuffer.isView(words)
        assert JavaScriptArrayBuffer.isView(new JavaScriptDataView(buffer))
    }

    void testNumericCoercionAndTypedOperations() {
        def unsigned = new JavaScriptUint8Array([-1, 256, 2.9d])
        def clamped = new JavaScriptUint8ClampedArray([1.5d, 2.5d, 255.6d, Double.NaN])

        assert unsigned.values().collect() == [255, 0, 2]
        assert unsigned.indexOf(255) == 0
        assert clamped.values().collect() == [2, 2, 255, 0]
        assert unsigned.map { value -> value + 1 }.values().collect() == [0, 1, 3]
        assert unsigned.filter { value -> value > 1 }.values().collect() == [255, 2]
        assert unsigned.reduce { total, value -> total + value } == 257

        unsigned.set(unsigned.subarray(0, 2), 1)
        assert unsigned.values().collect() == [255, 255, 0]
        assert unsigned.fill(9, 1).reverse().values().collect() == [9, 9, 255]
        assert unsigned.with(1, 2).values().collect() == [9, 2, 255]
        assert unsigned.values().collect() == [9, 9, 255]
    }

    void testDataViewUsesBigEndianByDefaultAndLittleEndianOnRequest() {
        def buffer = new JavaScriptArrayBuffer(16)
        def view = new JavaScriptDataView(buffer)
        def bytes = new JavaScriptUint8Array(buffer)

        view.setUint16(0, 0x1234)
        view.setUint16(2, 0x1234, true)
        view.setFloat64(4, 1.5d, true)

        assert bytes[0] == 0x12
        assert bytes[1] == 0x34
        assert bytes[2] == 0x34
        assert bytes[3] == 0x12
        assert view.getUint16(0) == 0x1234
        assert view.getUint16(2, true) == 0x1234
        assert view.getFloat64(4, true) == 1.5d
        assert shouldFail(JavaScriptRangeError) { view.getInt32(13) }
    }

    void testBigIntTypedArraysAndConstructorBounds() {
        def signed = new JavaScriptBigInt64Array([JavaScriptBigInt.call(-1)])
        def unsigned = new JavaScriptBigUint64Array([JavaScriptBigInt.call(-1)])

        assert signed[0] == JavaScriptBigInt.call(-1)
        assert unsigned[0] == JavaScriptBigInt.asUintN(64, JavaScriptBigInt.call(-1))
        assert shouldFail(JavaScriptTypeError) { new JavaScriptBigInt64Array([1]) }
        assert shouldFail(JavaScriptRangeError) { new JavaScriptInt16Array(new JavaScriptArrayBuffer(3)) }
        assert shouldFail(JavaScriptRangeError) { new JavaScriptInt16Array(new JavaScriptArrayBuffer(4), 1) }
    }

    void testTypedArrayAllocationRejectsByteLengthOverflow() {
        assert shouldFail(JavaScriptRangeError) { new JavaScriptInt16Array(Integer.MAX_VALUE) }
        assert shouldFail(JavaScriptRangeError) { new JavaScriptFloat64Array(Integer.MAX_VALUE) }
    }

    void testConstructorAndViewBoundariesWithoutLargeAllocations() {
        assert new JavaScriptUint8Array(Double.NaN).length == 0
        assert new JavaScriptUint8Array(2.9d).length == 2
        assert shouldFail(JavaScriptRangeError) { new JavaScriptUint8Array(-1) }
        assert shouldFail(JavaScriptRangeError) { new JavaScriptUint8Array(Double.POSITIVE_INFINITY) }

        def buffer = new JavaScriptArrayBuffer(8)
        assert new JavaScriptUint16Array(buffer, 8, 0).length == 0
        assert new JavaScriptDataView(buffer, 8, 0).byteLength == 0
        assert shouldFail(JavaScriptRangeError) { new JavaScriptUint16Array(buffer, 7) }
        assert shouldFail(JavaScriptRangeError) { new JavaScriptUint16Array(buffer, 6, 2) }
        assert shouldFail(JavaScriptRangeError) { new JavaScriptDataView(buffer, 7, 2) }

        def view = new JavaScriptDataView(buffer)
        view.setUint8(7, 255)
        assert view.getUint8(7) == 255
        assert shouldFail(JavaScriptRangeError) { view.getUint8(8) }
        assert shouldFail(JavaScriptRangeError) { view.getUint16(7) }

        def target = new JavaScriptUint8Array(2)
        target.set([1, 2], 0)
        assert target.values().collect() == [1, 2]
        assert shouldFail(JavaScriptRangeError) { target.set([1, 2, 3], 0) }
        assert shouldFail(JavaScriptRangeError) { target.set([1], 3) }
    }
}
