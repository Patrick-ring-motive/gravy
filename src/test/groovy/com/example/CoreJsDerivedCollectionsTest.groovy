package com.example

/** Groovy vectors derived from current core-js Map and Set unit-global modules. */
class CoreJsDerivedCollectionsTest extends GravyTestCase {
    void testMapSetGetDeleteAndClearMaintainInsertionOrder() {
        def map = new JavaScriptMap().set(Double.NaN, 1).set(2, 1).set(3, 7).set(2, 5)

        assert map.size == 3
        assert map.get(Double.NaN) == 1
        assert map.get(2) == 5
        assert map.delete(Double.NaN)
        assert !map.delete(4)
        map.clear()
        assert map.size == 0
    }

    void testMapForEachHandlesCurrentEntryDeletionAndNewEntries() {
        def map = new JavaScriptMap([['0', 9], ['1', 9], ['2', 9], ['3', 9]])
        String visited = ''

        map.forEach { value, key ->
            visited += key
            if (key == '2') {
                map.delete('2')
                map.delete('3')
                map.delete('1')
                map.set('4', 9)
            }
        }

        assert visited == '0124'
    }

    void testSetAddChainsAndForEachTracksLiveValues() {
        def set = new JavaScriptSet([3, 4])
        String visited = ''

        assert set.add(2).add(1).is(set)
        set.forEach { value ->
            visited += value
            if (value == 2) {
                set.delete(2)
                set.delete(1)
                set.add(5)
            }
        }

        assert visited == '3425'
        assert set.values().collect() == [3, 4, 5]
    }
}
