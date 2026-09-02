package com.example

import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.CompletableFuture

class JavaScriptArrayExtensionsTest extends GravyTestCase {
    void setUp() {
        JavaScriptArrayExtensions.install()
    }

    void testStaticMethods() {
        assert Object[].from([2, 3]) == [2, 3] as Object[]
        assert Object[].from([2, 3]) { value, index -> value * index } == [0, 3] as Object[]
        assert Object[].from('A😄') == ['A', '😄'] as Object[]
        assert Object[].fromAsync(CompletableFuture.completedFuture([1, 2])) { it * 2 }.join() == [2, 4] as Object[]
        assert Object[].isArray([1, 2] as int[])
        assert Object[].isArray(['a'] as Object[])
        assert !Object[].isArray([1, 2])
        assert Object[].of('a', 2) == ['a', 2] as Object[]
    }

    void testObjectAndPrimitiveArrayTargets() {
        List<Object> arrays = [
            ['a', 'b'] as Object[],
            [true, false] as boolean[],
            [1, 2] as byte[],
            [1, 2] as short[],
            ['a', 'b'] as char[],
            [1, 2] as int[],
            [1, 2] as long[],
            [1, 2] as float[],
            [1, 2] as double[]
        ]

        arrays.each { Object array ->
            assert array.at(0) != null
            assert array.at(-1) != null
            assert array.at(2) == null
            assert array.slice(0, 1).size() == 1
            assert array.values().toList().size() == 2
            assert array.length == 2
        }
    }

    void testListAndArrayListTargets() {
        List linkedList = new LinkedList([1, 2, 3])
        ArrayList arrayList = new ArrayList([1, 2, 3])

        [linkedList, arrayList].each { List values ->
            assert values.length == 3
            assert values.at(-1) == 3
            assert values.map { it * 2 } == [2, 4, 6]
            assert values.filter { it > 1 } == [2, 3]
            assert values.toJsString() == '1,2,3'
        }
    }

    void testAccessSearchAndIterators() {
        def values = [Double.NaN, 1, 2, 1] as Object[]

        assert values.at(-1) == 1
        assert values.at(-5) == null
        assert values.includes(Double.NaN)
        assert values.indexOf(Double.NaN) == -1
        assert values.indexOf(1, -2) == 3
        assert values.lastIndexOf(1) == 3
        assert values.lastIndexOf(1, -2) == 1
        assert values.keys().toList() == [0, 1, 2, 3]
        assert values.values().toList() == [Double.NaN, 1, 2, 1]
        assert values.entries().toList() == [[0, Double.NaN], [1, 1], [2, 2], [3, 1]]
    }

    void testCallbackMethods() {
        List values = [1, 2, 3, 4]
        List seen = []

        assert values.every { value, index, original -> value == original[index] }
        assert values.some { it > 3 }
        assert !values.some { it > 4 }
        assert values.find { it % 2 == 0 } == 2
        assert values.findIndex { it % 2 == 0 } == 1
        assert values.findLast { it % 2 == 0 } == 4
        assert values.findLastIndex { it % 2 == 0 } == 3
        assert values.filter { value, index -> value > 2 && index >= 2 } == [3, 4]
        assert values.map { value, index -> value + index } == [1, 3, 5, 7]
        assert values.forEach { value, index -> seen << value + index } == null
        assert seen == [1, 3, 5, 7]
    }

    void testConcatFlatAndFlatMap() {
        def values = [1, 2] as int[]

        assert values.concat([3, 4], [5] as int[], 6) == [1, 2, 3, 4, 5, 6]
        assert [[1], [2, [3]]].flat() == [1, 2, [3]]
        assert [[1], [2, [3]]].flat(2) == [1, 2, 3]
        assert [1, 2].flatMap { [it, it * 10] } == [1, 10, 2, 20]
    }

    void testMutatingFixedArrayMethods() {
        int[] values = [1, 2, 3, 4, 5]

        assert values.copyWithin(0, 3) == values
        assert values.toList() == [4, 5, 3, 4, 5]
        assert values.fill(9, 1, -1) == values
        assert values.toList() == [4, 9, 9, 9, 5]
        assert values.reverse() == values
        assert values.toList() == [5, 9, 9, 9, 4]
        assert values.sort() == values
        assert values.toList() == [4, 5, 9, 9, 9]

        int[] sameLength = [1, 2, 3]
        assert sameLength.splice(1, 1, 8) == [2]
        assert sameLength.toList() == [1, 8, 3]
        shouldFail(UnsupportedOperationException) { values.pop() }
        shouldFail(UnsupportedOperationException) { values.push(10) }
        shouldFail(UnsupportedOperationException) { values.shift() }
        shouldFail(UnsupportedOperationException) { values.unshift(10) }
        shouldFail(UnsupportedOperationException) { values.splice(1, 1, 8, 9) }
    }

    void testMutatingListMethods() {
        List values = [2, 3]

        assert values.push(4, 5) == 4
        assert values == [2, 3, 4, 5]
        assert values.pop() == 5
        assert values.shift() == 2
        assert values.unshift(0, 1) == 4
        assert values == [0, 1, 3, 4]
        assert values.splice(1, 2, 8, 9, 10) == [1, 3]
        assert values == [0, 8, 9, 10, 4]
        assert values.reverse() == values
        assert values == [4, 10, 9, 8, 0]
        assert values.sort { left, right -> left <=> right } == values
        assert values == [0, 4, 8, 9, 10]
    }

    void testNonMutatingCopyMethods() {
        List values = [3, 1, 2]

        assert values.slice(-2) == [1, 2]
        assert values.toReversed() == [2, 1, 3]
        assert values.toSorted() == [1, 2, 3]
        assert values.toSorted { left, right -> right <=> left } == [3, 2, 1]
        assert values.toSpliced(1, 1, 8, 9) == [3, 8, 9, 2]
        assert values.with(-1, 7) == [3, 1, 7]
        assert values == [3, 1, 2]
        shouldFail(IndexOutOfBoundsException) { values.with(3, 9) }
    }

    void testJoinToStringAndLocaleString() {
        def values = ['a', null, [2, 3] as int[], 'd'] as Object[]

        assert values.join('-') == 'a--2,3-d'
        assert values.toJsString() == 'a,,2,3,d'
        assert [1234.5].toLocaleString(Locale.US) == '1,234.5'
    }

    void testReduceMethods() {
        List values = [1, 2, 3]

        assert values.reduce { accumulator, value -> accumulator + value } == 6
        assert values.reduce({ accumulator, value -> accumulator + value }, 10) == 16
        assert values.reduceRight { accumulator, value -> accumulator - value } == 0
        assert values.reduceRight({ accumulator, value -> accumulator - value }, 10) == 4
        shouldFail(IllegalStateException) { [].reduce { left, right -> left + right } }
        shouldFail(IllegalStateException) { [].reduceRight { left, right -> left + right } }
    }
}
