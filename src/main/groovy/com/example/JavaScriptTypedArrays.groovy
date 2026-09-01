package com.example

import groovy.lang.Closure

import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayList
import java.util.Iterator
import java.util.List

/** Fixed-length, heap-backed approximation of JavaScript ArrayBuffer. */
final class JavaScriptArrayBuffer {
    private final ByteBuffer bytes

    JavaScriptArrayBuffer(Object byteLength = 0) {
        this.bytes = ByteBuffer.allocate(JavaScriptTypedArraySupport.toIndex(byteLength, 'ArrayBuffer byteLength'))
    }

    private JavaScriptArrayBuffer(ByteBuffer bytes) {
        this.bytes = bytes.slice()
    }

    int getByteLength() {
        bytes.capacity()
    }

    JavaScriptArrayBuffer slice(Object begin = 0, Object end = null) {
        int from = JavaScriptTypedArraySupport.relativeIndex(begin, byteLength, 0)
        int until = Math.max(from, JavaScriptTypedArraySupport.relativeEnd(end, byteLength))
        ByteBuffer result = ByteBuffer.allocate(until - from)
        ByteBuffer source = view(from, until - from)
        result.put(source)
        new JavaScriptArrayBuffer(result.rewind() as ByteBuffer)
    }

    static boolean isView(Object value) {
        value instanceof JavaScriptTypedArray || value instanceof JavaScriptDataView
    }

    ByteBuffer view(int offset = 0, int length = byteLength) {
        if (offset < 0 || length < 0 || offset > byteLength - length) {
            throw new JavaScriptRangeError('ArrayBuffer view exceeds buffer bounds')
        }
        ByteBuffer view = bytes.duplicate()
        view.position(offset)
        view.limit(offset + length)
        view.slice()
    }
}

enum JavaScriptTypedArrayKind {
    INT8(1),
    UINT8(1),
    UINT8_CLAMPED(1),
    INT16(2),
    UINT16(2),
    INT32(4),
    UINT32(4),
    FLOAT32(4),
    FLOAT64(8),
    BIGINT64(8),
    BIGUINT64(8)

    final int bytesPerElement

    JavaScriptTypedArrayKind(int bytesPerElement) {
        this.bytesPerElement = bytesPerElement
    }
}

/** Common fixed-length view behavior for concrete typed-array constructors. */
abstract class JavaScriptTypedArray implements Iterable<Object> {
    private static final Object OMITTED = new Object()

    final JavaScriptTypedArrayKind kind
    final JavaScriptArrayBuffer buffer
    final int byteOffset
    final int length

    protected JavaScriptTypedArray(JavaScriptTypedArrayKind kind, Object source = 0) {
        this.kind = kind
        if (source instanceof JavaScriptArrayBuffer) {
            JavaScriptArrayBuffer sourceBuffer = source as JavaScriptArrayBuffer
            if (sourceBuffer.byteLength % kind.bytesPerElement != 0) {
                throw new JavaScriptRangeError('ArrayBuffer byteLength must align with typed-array element size')
            }
            this.buffer = sourceBuffer
            this.byteOffset = 0
            this.length = sourceBuffer.byteLength.intdiv(kind.bytesPerElement)
            return
        }

        if (source == null) {
            this.length = 0
            this.byteOffset = 0
            this.buffer = new JavaScriptArrayBuffer(0)
            return
        }

        if (source instanceof Number || source instanceof JavaScriptNumber) {
            this.length = JavaScriptTypedArraySupport.toIndex(source, 'TypedArray length')
            this.byteOffset = 0
            this.buffer = new JavaScriptArrayBuffer(JavaScriptTypedArraySupport.checkedByteLength(length, kind.bytesPerElement))
            return
        }

        List<Object> values = JavaScriptTypedArraySupport.valuesFor(source)
        this.length = values.size()
        this.byteOffset = 0
        this.buffer = new JavaScriptArrayBuffer(JavaScriptTypedArraySupport.checkedByteLength(length, kind.bytesPerElement))
        values.eachWithIndex { Object value, int index -> write(index, value) }
    }

    protected JavaScriptTypedArray(JavaScriptTypedArrayKind kind, JavaScriptArrayBuffer buffer,
                                  Object byteOffset = 0, Object requestedLength = null) {
        if (buffer == null) {
            throw new JavaScriptTypeError('TypedArray buffer must be an ArrayBuffer')
        }
        this.kind = kind
        int resolvedOffset = JavaScriptTypedArraySupport.toIndex(byteOffset, 'TypedArray byteOffset')
        if (resolvedOffset % kind.bytesPerElement != 0) {
            throw new JavaScriptRangeError('TypedArray byteOffset must align with element size')
        }
        int available = buffer.byteLength - resolvedOffset
        if (available < 0) {
            throw new JavaScriptRangeError('TypedArray byteOffset exceeds buffer length')
        }
        int resolvedLength
        if (requestedLength == null) {
            if (available % kind.bytesPerElement != 0) {
                throw new JavaScriptRangeError('ArrayBuffer remainder must align with typed-array element size')
            }
            resolvedLength = available.intdiv(kind.bytesPerElement)
        } else {
            resolvedLength = JavaScriptTypedArraySupport.toIndex(requestedLength, 'TypedArray length')
            if (resolvedLength > available.intdiv(kind.bytesPerElement)) {
                throw new JavaScriptRangeError('TypedArray length exceeds buffer bounds')
            }
        }
        this.buffer = buffer
        this.byteOffset = resolvedOffset
        this.length = resolvedLength
    }

    int getByteLength() {
        length * kind.bytesPerElement
    }

    int getBytesPerElement() {
        kind.bytesPerElement
    }

    Object getAt(Object index) {
        int resolved = JavaScriptTypedArraySupport.index(index, length)
        resolved < 0 ? null : read(resolved)
    }

    void putAt(Object index, Object value) {
        int resolved = JavaScriptTypedArraySupport.index(index, length)
        if (resolved >= 0) {
            write(resolved, value)
        }
    }

    Object at(Object index) {
        int resolved = JavaScriptTypedArraySupport.relativeAtIndex(index, length)
        resolved < 0 || resolved >= length ? null : read(resolved)
    }

    JavaScriptTypedArray set(Object source, Object offset = 0) {
        List<Object> values = JavaScriptTypedArraySupport.valuesFor(source)
        int resolvedOffset = JavaScriptTypedArraySupport.toIndex(offset, 'TypedArray set offset')
        if (resolvedOffset > length || values.size() > length - resolvedOffset) {
            throw new JavaScriptRangeError('TypedArray source exceeds target bounds')
        }
        values.eachWithIndex { Object value, int index -> write(resolvedOffset + index, value) }
        this
    }

    JavaScriptTypedArray subarray(Object begin = 0, Object end = null) {
        int from = JavaScriptTypedArraySupport.relativeIndex(begin, length, 0)
        int until = Math.max(from, JavaScriptTypedArraySupport.relativeEnd(end, length))
        newView(buffer, byteOffset + from * bytesPerElement, until - from)
    }

    JavaScriptTypedArray slice(Object begin = 0, Object end = null) {
        int from = JavaScriptTypedArraySupport.relativeIndex(begin, length, 0)
        int until = Math.max(from, JavaScriptTypedArraySupport.relativeEnd(end, length))
        newLike(snapshot().subList(from, until))
    }

    JavaScriptTypedArray map(Closure mapper) {
        List<Object> result = []
        snapshot().eachWithIndex { Object value, int index ->
            result << JavaScriptTypedArraySupport.invoke(mapper, value, index, this)
        }
        newLike(result)
    }

    JavaScriptTypedArray filter(Closure predicate) {
        List<Object> result = []
        snapshot().eachWithIndex { Object value, int index ->
            if (JavaScriptTypedArraySupport.truthy(JavaScriptTypedArraySupport.invoke(predicate, value, index, this))) {
                result << value
            }
        }
        newLike(result)
    }

    Object reduce(Closure reducer) {
        reduceInternal(reducer, OMITTED)
    }

    Object reduce(Closure reducer, Object initialValue) {
        reduceInternal(reducer, initialValue)
    }

    Object reduceRight(Closure reducer) {
        reduceRightInternal(reducer, OMITTED)
    }

    Object reduceRight(Closure reducer, Object initialValue) {
        reduceRightInternal(reducer, initialValue)
    }

    Object forEach(Closure callback) {
        snapshot().eachWithIndex { Object value, int index ->
            JavaScriptTypedArraySupport.invoke(callback, value, index, this)
        }
        null
    }

    boolean every(Closure predicate) {
        List<Object> values = snapshot()
        for (int index = 0; index < values.size(); index++) {
            if (!JavaScriptTypedArraySupport.truthy(JavaScriptTypedArraySupport.invoke(predicate, values[index], index, this))) {
                return false
            }
        }
        true
    }

    boolean some(Closure predicate) {
        List<Object> values = snapshot()
        for (int index = 0; index < values.size(); index++) {
            if (JavaScriptTypedArraySupport.truthy(JavaScriptTypedArraySupport.invoke(predicate, values[index], index, this))) {
                return true
            }
        }
        false
    }

    Object find(Closure predicate) {
        int index = findIndex(predicate)
        index < 0 ? null : read(index)
    }

    int findIndex(Closure predicate) {
        List<Object> values = snapshot()
        for (int index = 0; index < values.size(); index++) {
            if (JavaScriptTypedArraySupport.truthy(JavaScriptTypedArraySupport.invoke(predicate, values[index], index, this))) {
                return index
            }
        }
        -1
    }

    Object findLast(Closure predicate) {
        int index = findLastIndex(predicate)
        index < 0 ? null : read(index)
    }

    int findLastIndex(Closure predicate) {
        List<Object> values = snapshot()
        for (int index = values.size() - 1; index >= 0; index--) {
            if (JavaScriptTypedArraySupport.truthy(JavaScriptTypedArraySupport.invoke(predicate, values[index], index, this))) {
                return index
            }
        }
        -1
    }

    boolean includes(Object value, Object fromIndex = 0) {
        int from = JavaScriptTypedArraySupport.relativeIndex(fromIndex, length, 0)
        for (int index = from; index < length; index++) {
            if (JavaScriptTypedArraySupport.sameValueZero(read(index), value)) {
                return true
            }
        }
        false
    }

    int indexOf(Object value, Object fromIndex = 0) {
        int from = JavaScriptTypedArraySupport.relativeIndex(fromIndex, length, 0)
        for (int index = from; index < length; index++) {
            if (JavaScriptTypedArraySupport.strictEqual(read(index), value)) {
                return index
            }
        }
        -1
    }

    int lastIndexOf(Object value, Object fromIndex = null) {
        int from = fromIndex == null ? length - 1 : JavaScriptTypedArraySupport.relativeIndex(fromIndex, length, length - 1)
        for (int index = Math.min(from, length - 1); index >= 0; index--) {
            if (JavaScriptTypedArraySupport.strictEqual(read(index), value)) {
                return index
            }
        }
        -1
    }

    JavaScriptTypedArray fill(Object value, Object start = 0, Object end = null) {
        int from = JavaScriptTypedArraySupport.relativeIndex(start, length, 0)
        int until = JavaScriptTypedArraySupport.relativeEnd(end, length)
        for (int index = from; index < until; index++) {
            write(index, value)
        }
        this
    }

    JavaScriptTypedArray copyWithin(Object target, Object start, Object end = null) {
        int destination = JavaScriptTypedArraySupport.relativeIndex(target, length, 0)
        int from = JavaScriptTypedArraySupport.relativeIndex(start, length, 0)
        int until = JavaScriptTypedArraySupport.relativeEnd(end, length)
        int count = Math.min(until - from, length - destination)
        if (count > 0) {
            List<Object> values = snapshot().subList(from, from + count)
            values.eachWithIndex { Object value, int index -> write(destination + index, value) }
        }
        this
    }

    JavaScriptTypedArray reverse() {
        List<Object> values = snapshot().reverse()
        values.eachWithIndex { Object value, int index -> write(index, value) }
        this
    }

    JavaScriptTypedArray sort(Closure comparator = null) {
        List<Object> values = snapshot()
        values.sort { Object left, Object right ->
            comparator == null ? JavaScriptTypedArraySupport.compare(left, right) :
                JavaScriptTypedArraySupport.toSign(JavaScriptTypedArraySupport.invoke(comparator, left, right))
        }
        values.eachWithIndex { Object value, int index -> write(index, value) }
        this
    }

    JavaScriptTypedArray with(Object index, Object value) {
        int resolved = JavaScriptTypedArraySupport.index(index, length)
        if (resolved < 0) {
            throw new JavaScriptRangeError('TypedArray index is outside bounds')
        }
        List<Object> values = snapshot()
        values[resolved] = value
        newLike(values)
    }

    JavaScriptTypedArray toReversed() {
        newLike(snapshot().reverse())
    }

    JavaScriptTypedArray toSorted(Closure comparator = null) {
        JavaScriptTypedArray result = newLike(snapshot())
        result.sort(comparator)
    }

    Iterator<Integer> keys() {
        (0..<length).iterator()
    }

    Iterator<Object> values() {
        snapshot().iterator()
    }

    Iterator<List<Object>> entries() {
        List<List<Object>> entries = []
        snapshot().eachWithIndex { Object value, int index -> entries << [index, value] }
        entries.iterator()
    }

    String join(Object separator = ',') {
        String resolved = separator == null ? 'null' : String.valueOf(separator)
        snapshot().collect { Object value -> value == null ? '' : String.valueOf(value) }.join(resolved)
    }

    String toLocaleString(Object locales = null, Object options = null) {
        snapshot().collect { Object value -> value instanceof Number ? JavaScriptNumberExtensions.toLocaleString(value as Number, locales, options) : String.valueOf(value) }.join(',')
    }

    @Override
    String toString() {
        join(',')
    }

    @Override
    Iterator<Object> iterator() {
        values()
    }

    protected Object read(int index) {
        JavaScriptTypedArraySupport.read(kind, bytes(), index * bytesPerElement)
    }

    protected void write(int index, Object value) {
        JavaScriptTypedArraySupport.write(kind, bytes(), index * bytesPerElement, value)
    }

    protected List<Object> snapshot() {
        List<Object> result = new ArrayList<>(length)
        for (int index = 0; index < length; index++) {
            result << read(index)
        }
        result
    }

    private ByteBuffer bytes() {
        buffer.view(byteOffset, byteLength).order(ByteOrder.LITTLE_ENDIAN)
    }

    private JavaScriptTypedArray newView(JavaScriptArrayBuffer source, int offset, int elements) {
        JavaScriptTypedArraySupport.create(kind, source, offset, elements)
    }

    private JavaScriptTypedArray newLike(List<Object> values) {
        JavaScriptTypedArraySupport.create(kind, values)
    }

    private Object reduceInternal(Closure reducer, Object initialValue) {
        List<Object> values = snapshot()
        if (values.isEmpty() && initialValue.is(OMITTED)) {
            throw new JavaScriptTypeError('Reduce of empty TypedArray with no initial value')
        }
        int start = initialValue.is(OMITTED) ? 1 : 0
        Object accumulator = initialValue.is(OMITTED) ? values[0] : initialValue
        for (int index = start; index < values.size(); index++) {
            accumulator = JavaScriptTypedArraySupport.invoke(reducer, accumulator, values[index], index, this)
        }
        accumulator
    }

    private Object reduceRightInternal(Closure reducer, Object initialValue) {
        List<Object> values = snapshot()
        if (values.isEmpty() && initialValue.is(OMITTED)) {
            throw new JavaScriptTypeError('Reduce of empty TypedArray with no initial value')
        }
        int start = initialValue.is(OMITTED) ? values.size() - 2 : values.size() - 1
        Object accumulator = initialValue.is(OMITTED) ? values[values.size() - 1] : initialValue
        for (int index = start; index >= 0; index--) {
            accumulator = JavaScriptTypedArraySupport.invoke(reducer, accumulator, values[index], index, this)
        }
        accumulator
    }
}

final class JavaScriptInt8Array extends JavaScriptTypedArray {
    static final int BYTES_PER_ELEMENT = 1
    JavaScriptInt8Array(Object source = 0) { super(JavaScriptTypedArrayKind.INT8, source) }
    JavaScriptInt8Array(JavaScriptArrayBuffer buffer, Object byteOffset = 0, Object length = null) { super(JavaScriptTypedArrayKind.INT8, buffer, byteOffset, length) }
    static JavaScriptInt8Array from(Object source, Closure mapper = null) { JavaScriptTypedArraySupport.from(JavaScriptTypedArrayKind.INT8, source, mapper) as JavaScriptInt8Array }
    static JavaScriptInt8Array of(Object... values) { new JavaScriptInt8Array(values) }
}

final class JavaScriptUint8Array extends JavaScriptTypedArray {
    static final int BYTES_PER_ELEMENT = 1
    JavaScriptUint8Array(Object source = 0) { super(JavaScriptTypedArrayKind.UINT8, source) }
    JavaScriptUint8Array(JavaScriptArrayBuffer buffer, Object byteOffset = 0, Object length = null) { super(JavaScriptTypedArrayKind.UINT8, buffer, byteOffset, length) }
    static JavaScriptUint8Array from(Object source, Closure mapper = null) { JavaScriptTypedArraySupport.from(JavaScriptTypedArrayKind.UINT8, source, mapper) as JavaScriptUint8Array }
    static JavaScriptUint8Array of(Object... values) { new JavaScriptUint8Array(values) }
}

final class JavaScriptUint8ClampedArray extends JavaScriptTypedArray {
    static final int BYTES_PER_ELEMENT = 1
    JavaScriptUint8ClampedArray(Object source = 0) { super(JavaScriptTypedArrayKind.UINT8_CLAMPED, source) }
    JavaScriptUint8ClampedArray(JavaScriptArrayBuffer buffer, Object byteOffset = 0, Object length = null) { super(JavaScriptTypedArrayKind.UINT8_CLAMPED, buffer, byteOffset, length) }
    static JavaScriptUint8ClampedArray from(Object source, Closure mapper = null) { JavaScriptTypedArraySupport.from(JavaScriptTypedArrayKind.UINT8_CLAMPED, source, mapper) as JavaScriptUint8ClampedArray }
    static JavaScriptUint8ClampedArray of(Object... values) { new JavaScriptUint8ClampedArray(values) }
}

final class JavaScriptInt16Array extends JavaScriptTypedArray {
    static final int BYTES_PER_ELEMENT = 2
    JavaScriptInt16Array(Object source = 0) { super(JavaScriptTypedArrayKind.INT16, source) }
    JavaScriptInt16Array(JavaScriptArrayBuffer buffer, Object byteOffset = 0, Object length = null) { super(JavaScriptTypedArrayKind.INT16, buffer, byteOffset, length) }
    static JavaScriptInt16Array from(Object source, Closure mapper = null) { JavaScriptTypedArraySupport.from(JavaScriptTypedArrayKind.INT16, source, mapper) as JavaScriptInt16Array }
    static JavaScriptInt16Array of(Object... values) { new JavaScriptInt16Array(values) }
}

final class JavaScriptUint16Array extends JavaScriptTypedArray {
    static final int BYTES_PER_ELEMENT = 2
    JavaScriptUint16Array(Object source = 0) { super(JavaScriptTypedArrayKind.UINT16, source) }
    JavaScriptUint16Array(JavaScriptArrayBuffer buffer, Object byteOffset = 0, Object length = null) { super(JavaScriptTypedArrayKind.UINT16, buffer, byteOffset, length) }
    static JavaScriptUint16Array from(Object source, Closure mapper = null) { JavaScriptTypedArraySupport.from(JavaScriptTypedArrayKind.UINT16, source, mapper) as JavaScriptUint16Array }
    static JavaScriptUint16Array of(Object... values) { new JavaScriptUint16Array(values) }
}

final class JavaScriptInt32Array extends JavaScriptTypedArray {
    static final int BYTES_PER_ELEMENT = 4
    JavaScriptInt32Array(Object source = 0) { super(JavaScriptTypedArrayKind.INT32, source) }
    JavaScriptInt32Array(JavaScriptArrayBuffer buffer, Object byteOffset = 0, Object length = null) { super(JavaScriptTypedArrayKind.INT32, buffer, byteOffset, length) }
    static JavaScriptInt32Array from(Object source, Closure mapper = null) { JavaScriptTypedArraySupport.from(JavaScriptTypedArrayKind.INT32, source, mapper) as JavaScriptInt32Array }
    static JavaScriptInt32Array of(Object... values) { new JavaScriptInt32Array(values) }
}

final class JavaScriptUint32Array extends JavaScriptTypedArray {
    static final int BYTES_PER_ELEMENT = 4
    JavaScriptUint32Array(Object source = 0) { super(JavaScriptTypedArrayKind.UINT32, source) }
    JavaScriptUint32Array(JavaScriptArrayBuffer buffer, Object byteOffset = 0, Object length = null) { super(JavaScriptTypedArrayKind.UINT32, buffer, byteOffset, length) }
    static JavaScriptUint32Array from(Object source, Closure mapper = null) { JavaScriptTypedArraySupport.from(JavaScriptTypedArrayKind.UINT32, source, mapper) as JavaScriptUint32Array }
    static JavaScriptUint32Array of(Object... values) { new JavaScriptUint32Array(values) }
}

final class JavaScriptFloat32Array extends JavaScriptTypedArray {
    static final int BYTES_PER_ELEMENT = 4
    JavaScriptFloat32Array(Object source = 0) { super(JavaScriptTypedArrayKind.FLOAT32, source) }
    JavaScriptFloat32Array(JavaScriptArrayBuffer buffer, Object byteOffset = 0, Object length = null) { super(JavaScriptTypedArrayKind.FLOAT32, buffer, byteOffset, length) }
    static JavaScriptFloat32Array from(Object source, Closure mapper = null) { JavaScriptTypedArraySupport.from(JavaScriptTypedArrayKind.FLOAT32, source, mapper) as JavaScriptFloat32Array }
    static JavaScriptFloat32Array of(Object... values) { new JavaScriptFloat32Array(values) }
}

final class JavaScriptFloat64Array extends JavaScriptTypedArray {
    static final int BYTES_PER_ELEMENT = 8
    JavaScriptFloat64Array(Object source = 0) { super(JavaScriptTypedArrayKind.FLOAT64, source) }
    JavaScriptFloat64Array(JavaScriptArrayBuffer buffer, Object byteOffset = 0, Object length = null) { super(JavaScriptTypedArrayKind.FLOAT64, buffer, byteOffset, length) }
    static JavaScriptFloat64Array from(Object source, Closure mapper = null) { JavaScriptTypedArraySupport.from(JavaScriptTypedArrayKind.FLOAT64, source, mapper) as JavaScriptFloat64Array }
    static JavaScriptFloat64Array of(Object... values) { new JavaScriptFloat64Array(values) }
}

final class JavaScriptBigInt64Array extends JavaScriptTypedArray {
    static final int BYTES_PER_ELEMENT = 8
    JavaScriptBigInt64Array(Object source = 0) { super(JavaScriptTypedArrayKind.BIGINT64, source) }
    JavaScriptBigInt64Array(JavaScriptArrayBuffer buffer, Object byteOffset = 0, Object length = null) { super(JavaScriptTypedArrayKind.BIGINT64, buffer, byteOffset, length) }
    static JavaScriptBigInt64Array from(Object source, Closure mapper = null) { JavaScriptTypedArraySupport.from(JavaScriptTypedArrayKind.BIGINT64, source, mapper) as JavaScriptBigInt64Array }
    static JavaScriptBigInt64Array of(Object... values) { new JavaScriptBigInt64Array(values) }
}

final class JavaScriptBigUint64Array extends JavaScriptTypedArray {
    static final int BYTES_PER_ELEMENT = 8
    JavaScriptBigUint64Array(Object source = 0) { super(JavaScriptTypedArrayKind.BIGUINT64, source) }
    JavaScriptBigUint64Array(JavaScriptArrayBuffer buffer, Object byteOffset = 0, Object length = null) { super(JavaScriptTypedArrayKind.BIGUINT64, buffer, byteOffset, length) }
    static JavaScriptBigUint64Array from(Object source, Closure mapper = null) { JavaScriptTypedArraySupport.from(JavaScriptTypedArrayKind.BIGUINT64, source, mapper) as JavaScriptBigUint64Array }
    static JavaScriptBigUint64Array of(Object... values) { new JavaScriptBigUint64Array(values) }
}

/** ArrayBuffer byte-level accessor with JavaScript DataView default big-endian order. */
final class JavaScriptDataView {
    final JavaScriptArrayBuffer buffer
    final int byteOffset
    final int byteLength

    JavaScriptDataView(JavaScriptArrayBuffer buffer, Object byteOffset = 0, Object requestedLength = null) {
        if (buffer == null) {
            throw new JavaScriptTypeError('DataView buffer must be an ArrayBuffer')
        }
        int offset = JavaScriptTypedArraySupport.toIndex(byteOffset, 'DataView byteOffset')
        int available = buffer.byteLength - offset
        if (available < 0) {
            throw new JavaScriptRangeError('DataView byteOffset exceeds buffer length')
        }
        int length = requestedLength == null ? available : JavaScriptTypedArraySupport.toIndex(requestedLength, 'DataView byteLength')
        if (length > available) {
            throw new JavaScriptRangeError('DataView byteLength exceeds buffer bounds')
        }
        this.buffer = buffer
        this.byteOffset = offset
        this.byteLength = length
    }

    byte getInt8(Object offset) { checked(offset, 1).get(JavaScriptTypedArraySupport.toIndex(offset, 'DataView byteOffset')) }
    int getUint8(Object offset) { Byte.toUnsignedInt(getInt8(offset)) }
    short getInt16(Object offset, boolean littleEndian = false) { ordered(littleEndian, offset, 2).getShort(JavaScriptTypedArraySupport.toIndex(offset, 'DataView byteOffset')) }
    int getUint16(Object offset, boolean littleEndian = false) { Short.toUnsignedInt(getInt16(offset, littleEndian)) }
    int getInt32(Object offset, boolean littleEndian = false) { ordered(littleEndian, offset, 4).getInt(JavaScriptTypedArraySupport.toIndex(offset, 'DataView byteOffset')) }
    long getUint32(Object offset, boolean littleEndian = false) { Integer.toUnsignedLong(getInt32(offset, littleEndian)) }
    float getFloat32(Object offset, boolean littleEndian = false) { ordered(littleEndian, offset, 4).getFloat(JavaScriptTypedArraySupport.toIndex(offset, 'DataView byteOffset')) }
    double getFloat64(Object offset, boolean littleEndian = false) { ordered(littleEndian, offset, 8).getDouble(JavaScriptTypedArraySupport.toIndex(offset, 'DataView byteOffset')) }
    JavaScriptBigInt getBigInt64(Object offset, boolean littleEndian = false) { JavaScriptBigInt.from(BigInteger.valueOf(ordered(littleEndian, offset, 8).getLong(JavaScriptTypedArraySupport.toIndex(offset, 'DataView byteOffset')))) }
    JavaScriptBigInt getBigUint64(Object offset, boolean littleEndian = false) { JavaScriptBigInt.from(JavaScriptTypedArraySupport.unsignedLong(ordered(littleEndian, offset, 8).getLong(JavaScriptTypedArraySupport.toIndex(offset, 'DataView byteOffset')))) }

    void setInt8(Object offset, Object value) { checked(offset, 1).put(JavaScriptTypedArraySupport.toIndex(offset, 'DataView byteOffset'), JavaScriptTypedArraySupport.toSigned(value, 8).byteValue()) }
    void setUint8(Object offset, Object value) { setInt8(offset, value) }
    void setInt16(Object offset, Object value, boolean littleEndian = false) { ordered(littleEndian, offset, 2).putShort(JavaScriptTypedArraySupport.toIndex(offset, 'DataView byteOffset'), JavaScriptTypedArraySupport.toSigned(value, 16).shortValue()) }
    void setUint16(Object offset, Object value, boolean littleEndian = false) { setInt16(offset, value, littleEndian) }
    void setInt32(Object offset, Object value, boolean littleEndian = false) { ordered(littleEndian, offset, 4).putInt(JavaScriptTypedArraySupport.toIndex(offset, 'DataView byteOffset'), JavaScriptTypedArraySupport.toSigned(value, 32).intValue()) }
    void setUint32(Object offset, Object value, boolean littleEndian = false) { setInt32(offset, value, littleEndian) }
    void setFloat32(Object offset, Object value, boolean littleEndian = false) { ordered(littleEndian, offset, 4).putFloat(JavaScriptTypedArraySupport.toIndex(offset, 'DataView byteOffset'), JavaScriptTypedArraySupport.toNumber(value).floatValue()) }
    void setFloat64(Object offset, Object value, boolean littleEndian = false) { ordered(littleEndian, offset, 8).putDouble(JavaScriptTypedArraySupport.toIndex(offset, 'DataView byteOffset'), JavaScriptTypedArraySupport.toNumber(value).doubleValue()) }
    void setBigInt64(Object offset, Object value, boolean littleEndian = false) { ordered(littleEndian, offset, 8).putLong(JavaScriptTypedArraySupport.toIndex(offset, 'DataView byteOffset'), JavaScriptTypedArraySupport.toSignedBig(value, 64).longValue()) }
    void setBigUint64(Object offset, Object value, boolean littleEndian = false) { setBigInt64(offset, value, littleEndian) }

    private ByteBuffer checked(Object offset, int size) {
        int index = JavaScriptTypedArraySupport.toIndex(offset, 'DataView byteOffset')
        if (index > byteLength - size) {
            throw new JavaScriptRangeError('DataView access exceeds view bounds')
        }
        buffer.view(byteOffset, byteLength)
    }

    private ByteBuffer ordered(boolean littleEndian, Object offset, int size) {
        checked(offset, size).order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN)
    }
}

final class JavaScriptTypedArraySupport {
    private static final BigInteger TWO_8 = BigInteger.ONE.shiftLeft(8)
    private static final BigInteger TWO_16 = BigInteger.ONE.shiftLeft(16)
    private static final BigInteger TWO_32 = BigInteger.ONE.shiftLeft(32)
    private static final BigInteger TWO_64 = BigInteger.ONE.shiftLeft(64)

    private JavaScriptTypedArraySupport() {
    }

    static JavaScriptTypedArray create(JavaScriptTypedArrayKind kind, Object source) {
        switch (kind) {
            case JavaScriptTypedArrayKind.INT8: return new JavaScriptInt8Array(source)
            case JavaScriptTypedArrayKind.UINT8: return new JavaScriptUint8Array(source)
            case JavaScriptTypedArrayKind.UINT8_CLAMPED: return new JavaScriptUint8ClampedArray(source)
            case JavaScriptTypedArrayKind.INT16: return new JavaScriptInt16Array(source)
            case JavaScriptTypedArrayKind.UINT16: return new JavaScriptUint16Array(source)
            case JavaScriptTypedArrayKind.INT32: return new JavaScriptInt32Array(source)
            case JavaScriptTypedArrayKind.UINT32: return new JavaScriptUint32Array(source)
            case JavaScriptTypedArrayKind.FLOAT32: return new JavaScriptFloat32Array(source)
            case JavaScriptTypedArrayKind.FLOAT64: return new JavaScriptFloat64Array(source)
            case JavaScriptTypedArrayKind.BIGINT64: return new JavaScriptBigInt64Array(source)
            case JavaScriptTypedArrayKind.BIGUINT64: return new JavaScriptBigUint64Array(source)
        }
        throw new IllegalArgumentException("Unsupported typed-array kind: ${kind}")
    }

    static JavaScriptTypedArray create(JavaScriptTypedArrayKind kind, JavaScriptArrayBuffer buffer, int byteOffset, int length) {
        switch (kind) {
            case JavaScriptTypedArrayKind.INT8: return new JavaScriptInt8Array(buffer, byteOffset, length)
            case JavaScriptTypedArrayKind.UINT8: return new JavaScriptUint8Array(buffer, byteOffset, length)
            case JavaScriptTypedArrayKind.UINT8_CLAMPED: return new JavaScriptUint8ClampedArray(buffer, byteOffset, length)
            case JavaScriptTypedArrayKind.INT16: return new JavaScriptInt16Array(buffer, byteOffset, length)
            case JavaScriptTypedArrayKind.UINT16: return new JavaScriptUint16Array(buffer, byteOffset, length)
            case JavaScriptTypedArrayKind.INT32: return new JavaScriptInt32Array(buffer, byteOffset, length)
            case JavaScriptTypedArrayKind.UINT32: return new JavaScriptUint32Array(buffer, byteOffset, length)
            case JavaScriptTypedArrayKind.FLOAT32: return new JavaScriptFloat32Array(buffer, byteOffset, length)
            case JavaScriptTypedArrayKind.FLOAT64: return new JavaScriptFloat64Array(buffer, byteOffset, length)
            case JavaScriptTypedArrayKind.BIGINT64: return new JavaScriptBigInt64Array(buffer, byteOffset, length)
            case JavaScriptTypedArrayKind.BIGUINT64: return new JavaScriptBigUint64Array(buffer, byteOffset, length)
        }
        throw new IllegalArgumentException("Unsupported typed-array kind: ${kind}")
    }

    static JavaScriptTypedArray from(JavaScriptTypedArrayKind kind, Object source, Closure mapper) {
        List<Object> values = valuesFor(source)
        if (mapper != null) {
            values = values.withIndex().collect { Object value, int index -> invoke(mapper, value, index) }
        }
        create(kind, values)
    }

    static List<Object> valuesFor(Object source) {
        if (source == null) {
            throw new JavaScriptTypeError('TypedArray source must be array-like or iterable')
        }
        if (source instanceof JavaScriptTypedArray) {
            return (source as JavaScriptTypedArray).snapshot()
        }
        if (source.class.array || source instanceof Iterator || source instanceof Iterable) {
            return JavaScriptCollectionSupport.valuesFor(source)
        }
        if (source instanceof CharSequence) {
            return source.toString().collect { String character -> character }
        }
        throw new JavaScriptTypeError('TypedArray source must be array-like or iterable')
    }

    static int toIndex(Object value, String context) {
        Number number = toNumber(value)
        double raw = number.doubleValue()
        if (Double.isNaN(raw)) {
            return 0
        }
        if (!Double.isFinite(raw) || raw < 0d || raw > Integer.MAX_VALUE) {
            throw new JavaScriptRangeError("${context} must be a finite non-negative index")
        }
        (int) raw
    }

    static int checkedByteLength(int length, int bytesPerElement) {
        long byteLength = (long) length * bytesPerElement
        if (byteLength > Integer.MAX_VALUE) {
            throw new JavaScriptRangeError('TypedArray byteLength exceeds JVM buffer limits')
        }
        (int) byteLength
    }

    static int index(Object value, int length) {
        Number number = toNumber(value)
        double raw = number.doubleValue()
        if (!Double.isFinite(raw)) {
            return -1
        }
        int index = (int) raw
        index >= 0 && index < length && raw == index ? index : -1
    }

    static int relativeIndex(Object value, int length, int defaultValue) {
        if (value == null) {
            return defaultValue
        }
        Number number = toNumber(value)
        double raw = number.doubleValue()
        if (Double.isNaN(raw)) {
            return 0
        }
        if (raw == Double.NEGATIVE_INFINITY) {
            return 0
        }
        if (raw == Double.POSITIVE_INFINITY) {
            return length
        }
        int index = (int) raw
        index < 0 ? Math.max(length + index, 0) : Math.min(index, length)
    }

    static int relativeEnd(Object value, int length) {
        value == null ? length : relativeIndex(value, length, length)
    }

    static int relativeAtIndex(Object value, int length) {
        Number number = toNumber(value)
        double raw = number.doubleValue()
        if (Double.isNaN(raw)) return 0
        if (!Double.isFinite(raw)) return -1
        int index = (int) raw
        index < 0 ? length + index : index
    }

    static Number toNumber(Object value) {
        if (value instanceof JavaScriptBigInt) {
            throw new JavaScriptTypeError('Cannot convert a BigInt value to a number')
        }
        JavaScriptNumber.coerce(value) as Number
    }

    static Object read(JavaScriptTypedArrayKind kind, ByteBuffer bytes, int offset) {
        switch (kind) {
            case JavaScriptTypedArrayKind.INT8: return bytes.get(offset)
            case JavaScriptTypedArrayKind.UINT8:
            case JavaScriptTypedArrayKind.UINT8_CLAMPED: return Byte.toUnsignedInt(bytes.get(offset))
            case JavaScriptTypedArrayKind.INT16: return bytes.getShort(offset)
            case JavaScriptTypedArrayKind.UINT16: return Short.toUnsignedInt(bytes.getShort(offset))
            case JavaScriptTypedArrayKind.INT32: return bytes.getInt(offset)
            case JavaScriptTypedArrayKind.UINT32: return Integer.toUnsignedLong(bytes.getInt(offset))
            case JavaScriptTypedArrayKind.FLOAT32: return bytes.getFloat(offset)
            case JavaScriptTypedArrayKind.FLOAT64: return bytes.getDouble(offset)
            case JavaScriptTypedArrayKind.BIGINT64: return JavaScriptBigInt.from(BigInteger.valueOf(bytes.getLong(offset)))
            case JavaScriptTypedArrayKind.BIGUINT64: return JavaScriptBigInt.from(unsignedLong(bytes.getLong(offset)))
        }
        throw new IllegalArgumentException("Unsupported typed-array kind: ${kind}")
    }

    static void write(JavaScriptTypedArrayKind kind, ByteBuffer bytes, int offset, Object value) {
        switch (kind) {
            case JavaScriptTypedArrayKind.INT8:
                bytes.put(offset, toSigned(value, 8).byteValue()); return
            case JavaScriptTypedArrayKind.UINT8:
                bytes.put(offset, toUnsigned(value, TWO_8).byteValue()); return
            case JavaScriptTypedArrayKind.UINT8_CLAMPED:
                bytes.put(offset, BigInteger.valueOf(toClampedByte(value)).byteValue()); return
            case JavaScriptTypedArrayKind.INT16:
                bytes.putShort(offset, toSigned(value, 16).shortValue()); return
            case JavaScriptTypedArrayKind.UINT16:
                bytes.putShort(offset, toUnsigned(value, TWO_16).shortValue()); return
            case JavaScriptTypedArrayKind.INT32:
                bytes.putInt(offset, toSigned(value, 32).intValue()); return
            case JavaScriptTypedArrayKind.UINT32:
                bytes.putInt(offset, toUnsigned(value, TWO_32).intValue()); return
            case JavaScriptTypedArrayKind.FLOAT32:
                bytes.putFloat(offset, toNumber(value).floatValue()); return
            case JavaScriptTypedArrayKind.FLOAT64:
                bytes.putDouble(offset, toNumber(value).doubleValue()); return
            case JavaScriptTypedArrayKind.BIGINT64:
                bytes.putLong(offset, toSignedBig(value, 64).longValue()); return
            case JavaScriptTypedArrayKind.BIGUINT64:
                bytes.putLong(offset, toUnsignedBig(value, TWO_64).longValue()); return
        }
        throw new IllegalArgumentException("Unsupported typed-array kind: ${kind}")
    }

    static BigInteger toSigned(Object value, int bits) {
        BigInteger modulus = BigInteger.ONE.shiftLeft(bits)
        BigInteger unsigned = toUnsigned(value, modulus)
        unsigned.testBit(bits - 1) ? unsigned.subtract(modulus) : unsigned
    }

    static BigInteger toSignedBig(Object value, int bits) {
        BigInteger modulus = BigInteger.ONE.shiftLeft(bits)
        BigInteger unsigned = toUnsignedBig(value, modulus)
        unsigned.testBit(bits - 1) ? unsigned.subtract(modulus) : unsigned
    }

    static BigInteger toUnsigned(Object value, BigInteger modulus) {
        integerFromNumber(toNumber(value)).mod(modulus)
    }

    static BigInteger toUnsignedBig(Object value, BigInteger modulus) {
        if (value instanceof JavaScriptBigInt) {
            return (value as JavaScriptBigInt).toBigInteger().mod(modulus)
        }
        if (value instanceof BigInteger) {
            return (value as BigInteger).mod(modulus)
        }
        throw new JavaScriptTypeError('BigInt typed arrays require BigInt values')
    }

    static BigInteger unsignedLong(long value) {
        BigInteger result = BigInteger.valueOf(value)
        value < 0L ? result.add(TWO_64) : result
    }

    static boolean truthy(Object value) {
        value != null && value != false && (!(value instanceof Number) || ((value as Number).doubleValue() != 0d && !Double.isNaN((value as Number).doubleValue()))) && String.valueOf(value) != ''
    }

    static boolean sameValueZero(Object left, Object right) {
        if (left instanceof JavaScriptBigInt || right instanceof JavaScriptBigInt) {
            return left instanceof JavaScriptBigInt && right instanceof JavaScriptBigInt && left == right
        }
        if (left instanceof Number && right instanceof Number) {
            double first = (left as Number).doubleValue()
            double second = (right as Number).doubleValue()
            return Double.isNaN(first) && Double.isNaN(second) || first == second
        }
        left == right
    }

    static boolean strictEqual(Object left, Object right) {
        if (left instanceof JavaScriptBigInt || right instanceof JavaScriptBigInt) {
            return left instanceof JavaScriptBigInt && right instanceof JavaScriptBigInt && left == right
        }
        if (left instanceof Number && right instanceof Number) {
            if (left instanceof BigInteger || right instanceof BigInteger) {
                return left instanceof BigInteger && right instanceof BigInteger && left == right
            }
            double first = (left as Number).doubleValue()
            double second = (right as Number).doubleValue()
            return !Double.isNaN(first) && !Double.isNaN(second) && first == second
        }
        left != null && right != null && left.class == right.class && left == right
    }

    static int compare(Object left, Object right) {
        if (left instanceof JavaScriptBigInt || right instanceof JavaScriptBigInt) {
            if (!(left instanceof JavaScriptBigInt) || !(right instanceof JavaScriptBigInt)) {
                throw new JavaScriptTypeError('Cannot compare BigInt and Number values')
            }
            return (left as JavaScriptBigInt).compareTo(right)
        }
        if (left instanceof Number && right instanceof Number) {
            double first = (left as Number).doubleValue()
            double second = (right as Number).doubleValue()
            if (Double.isNaN(first)) return Double.isNaN(second) ? 0 : 1
            if (Double.isNaN(second)) return -1
            return Double.compare(first, second)
        }
        (left as Comparable) <=> right
    }

    static int toSign(Object value) {
        double result = toNumber(value).doubleValue()
        Double.isNaN(result) || result == 0d ? 0 : result < 0d ? -1 : 1
    }

    static Object invoke(Closure callback, Object... arguments) {
        Closure rebound = callback.clone() as Closure
        rebound.delegate = null
        rebound.resolveStrategy = Closure.DELEGATE_FIRST
        rebound.call(*arguments.take(Math.min(rebound.maximumNumberOfParameters, arguments.length)))
    }

    private static BigInteger integerFromNumber(Number number) {
        if (number instanceof BigInteger) {
            return number as BigInteger
        }
        if (number instanceof BigDecimal) {
            return (number as BigDecimal).toBigInteger()
        }
        double value = number.doubleValue()
        if (!Double.isFinite(value)) {
            return BigInteger.ZERO
        }
        BigDecimal.valueOf(value).toBigInteger()
    }

    private static int toClampedByte(Object value) {
        double number = toNumber(value).doubleValue()
        if (Double.isNaN(number) || number <= 0d) return 0
        if (number >= 255d) return 255
        int floor = (int) Math.floor(number)
        double fraction = number - floor
        if (fraction > 0.5d || fraction == 0.5d && (floor & 1) == 1) floor + 1 else floor
    }
}
