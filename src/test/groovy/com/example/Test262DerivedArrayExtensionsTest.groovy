package com.example

/**
 * Independent Groovy vectors derived from Test262 Array requirements.
 *
 * Covers static construction, index coercion, callback arguments, search
 * equality rules, iteration, flattening, reduction, copying, sorting, and
 * mutation. JavaScript holes, property descriptors, Proxy behavior, Symbols,
 * ArraySpeciesCreate, and asynchronous iterables have no direct Groovy
 * equivalent and are outside this metaClass adapter.
 */
class Test262DerivedArrayExtensionsTest extends GravyTestCase {
    void setUp() {
        JavaScriptArrayExtensions.install()
    }

    void testArrayFromMapsAfterReadingUnicodeIterable() {
        assert Object[].from('A😄', { value, index -> "${index}:${value}" }) == ['0:A', '1:😄'] as Object[]
        assert Object[].from([1, 2, 3].iterator(), { value, index -> value + index }) == [1, 3, 5] as Object[]
    }

    void testAtAndSliceCoerceAndClampRelativeIndexes() {
        List values = ['a', 'b', 'c']

        assert values.at('1.9') == 'b'
        assert values.at('not-a-number') == 'a'
        assert values.at(-1) == 'c'
        assert values.at(-4) == null
        assert values.slice(-100, '2.9') == ['a', 'b']
        assert values.slice(2, 1) == []
    }

    void testCopyWithinUsesOriginalValuesForOverlappingRanges() {
        int[] rightOverlap = [1, 2, 3, 4, 5]
        int[] leftOverlap = [1, 2, 3, 4, 5]

        rightOverlap.copyWithin(1, 0, 4)
        leftOverlap.copyWithin(0, 1, 5)

        assert rightOverlap.toList() == [1, 1, 2, 3, 4]
        assert leftOverlap.toList() == [2, 3, 4, 5, 5]
    }

    void testEverySomeAndForEachUseValueIndexAndOriginalArray() {
        List values = [3, 5, 7]
        List arguments = []

        assert values.every { value, index, original -> original.is(values) && value == index * 2 + 3 }
        assert values.some { value, index -> value == 5 && index == 1 }
        values.forEach { value, index, original -> arguments << [value, index, original.is(values)] }
        assert arguments == [[3, 0, true], [5, 1, true], [7, 2, true]]
    }

    void testCallbackTraversalUsesInitialLengthSnapshot() {
        List values = [1, 2]
        List visited = []

        values.forEach { value, index ->
            visited << value
            if (index == 0) {
                values.push(3)
            }
        }

        assert visited == [1, 2]
        assert values == [1, 2, 3]
    }

    void testIncludesUsesSameValueZeroWhileIndexOfDoesNotMatchNaN() {
        List values = [Double.NaN, -0.0d, 1]

        assert values.includes(Double.NaN)
        assert values.includes(0.0d)
        assert values.indexOf(Double.NaN) == -1
        assert values.indexOf(0.0d) == 1
        assert values.lastIndexOf(Double.NaN) == -1
    }

    void testFindMethodsReturnNullOrNegativeOneWhenNoElementMatches() {
        List values = [1, 2, 3, 2]

        assert values.find { it > 4 } == null
        assert values.findIndex { it > 4 } == -1
        assert values.findLast { it % 2 == 0 } == 2
        assert values.findLastIndex { it % 2 == 0 } == 3
    }

    void testFlatDepthIsConvertedToInteger() {
        List values = [1, [2, [3, [4]]]]

        assert values.flat(0) == [1, [2, [3, [4]]]]
        assert values.flat('1.9') == [1, 2, [3, [4]]]
        assert values.flat(2) == [1, 2, 3, [4]]
        assert values.flat(-1) == [1, [2, [3, [4]]]]
    }

    void testReduceUsesFirstOrLastElementWhenInitialValueOmitted() {
        List values = [1, 2, 3]
        List leftCalls = []
        List rightCalls = []

        assert values.reduce { accumulator, value, index -> leftCalls << index; accumulator + value } == 6
        assert values.reduceRight { accumulator, value, index -> rightCalls << index; accumulator - value } == 0
        assert leftCalls == [1, 2]
        assert rightCalls == [1, 0]
    }

    void testDefaultSortUsesStringOrderingAndComparatorUsesSignOnly() {
        List defaultSort = [10, 2, 1]
        List comparatorSort = [1, 2, 3]

        defaultSort.sort()
        comparatorSort.sort { left, right -> right - left > 0 ? 900 : -900 }

        assert defaultSort == [1, 10, 2]
        assert comparatorSort == [3, 2, 1]
    }

    void testSpliceConvertsStartAndDeleteCount() {
        List values = [0, 1, 2, 3]

        assert values.splice(-2, '1.9', 'x') == [2]
        assert values == [0, 1, 'x', 3]
        assert values.splice(2) == ['x', 3]
        assert values == [0, 1]
        assert values.splice(20, -1) == []
        assert values == [0, 1]
    }

    void testCopyMethodsDoNotMutateSourceAndWithBoundsAreChecked() {
        List values = [0, 1, 2]

        assert values.toReversed() == [2, 1, 0]
        assert values.toSorted { left, right -> right <=> left } == [2, 1, 0]
        assert values.toSpliced(1, 1, 'x') == [0, 'x', 2]
        assert values.with(0, 'x') == ['x', 1, 2]
        assert values == [0, 1, 2]
        shouldFail(IndexOutOfBoundsException) { values.with(-4, 'x') }
    }

    void testFixedJavaArraysRejectOnlyLengthChanges() {
        int[] values = [1, 2, 3]

        assert values.push() == 3
        assert values.unshift() == 3
        assert values.splice(1, 0) == []
        assert values.splice(1, 1, 8) == [2]
        assert values.toList() == [1, 8, 3]
        shouldFail(UnsupportedOperationException) { values.splice(1, 0, 9) }
    }
}
