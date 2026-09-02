package com.example

/**
 * Independent Groovy vectors derived from current core-js Array module behavior.
 *
 * Covers construction, callback indexing, reducers, splice coercion, and copy
 * methods. JavaScript holes and ArraySpeciesCreate are outside this adapter.
 */
class CoreJsDerivedArrayTest extends GravyTestCase {
    void setUp() {
        JavaScriptArrayExtensions.install()
    }

    void testArrayFromReadsUnicodeIterableAndMapperIndexes() {
        assert Object[].from('A😄', { value, index -> "${index}:${value}" }) == ['0:A', '1:😄'] as Object[]
        assert Object[].of(1, 2, 3) == [1, 2, 3] as Object[]
        assert Object[].isArray([1, 2] as int[])
    }

    void testReduceAcceptsExplicitNullInitialValue() {
        List<List<Object>> calls = []

        assert [].reduce({ left, right -> calls << [left, right]; left }, null) == null
        assert calls == []
    }

    void testSpliceCoercesNullDeleteCountToZero() {
        List values = [1, 2, 3]

        assert values.splice(1, null, 'x') == []
        assert values == [1, 'x', 2, 3]
    }

    void testMapReadsValuesDuringCallbacksWithinInitialLength() {
        List values = [1, 2, 3]

        assert values.map { value, index ->
            if (index == 0) {
                values[1] = 20
            }
            value
        } == [1, 20, 3]
        assert values == [1, 20, 3]
    }

    void testCopyMethodsLeaveSourceUnchanged() {
        List values = [3, 1, 2]

        assert values.toSorted() == [1, 2, 3]
        assert values.toReversed() == [2, 1, 3]
        assert values.toSpliced(1, 1, 8, 9) == [3, 8, 9, 2]
        assert values == [3, 1, 2]
    }
}
