package com.example

import java.util.LinkedHashMap
import java.util.LinkedHashSet

/** Derived Map and Set checks. */
class JavaScriptCollectionsTest extends GravyTestCase {
    void testJavaScriptMapPreservesInsertionAndJsMethodNames() {
        def map = new JavaScriptMap([['one', 1], ['two', 2]])
        assert map.size == 2
        assert map.set('three', 3).is(map)
        assert map.get('two') == 2
        assert map.has('one')
        assert map.entries().collect() == [['one', 1], ['two', 2], ['three', 3]]
        assert map.delete('two')
        assert !map.delete('missing')
    }

    void testJavaScriptSetUsesSameValueZeroForSignedZero() {
        def set = new JavaScriptSet([-0.0d, 0.0d, 'x'])
        assert set.size == 2
        assert set.add('y').is(set)
        assert set.entries().collect() == [[0.0d, 0.0d], ['x', 'x'], ['y', 'y']]
        assert set.has(-0.0d)
        assert set.delete(0.0d)
        assert !set.has(-0.0d)
    }

    void testJavaScriptSetAcceptsUnicodeStringIterables() {
        def set = new JavaScriptSet('A😄A')

        assert set.values().collect() == ['A', '😄']
        assert set.has('😄')
    }

    void testCollectionExtensionsNeedNoInstall() {
        def map = new LinkedHashMap([a: 1])
        def set = new LinkedHashSet(['x'])

        assert map.set('b', 2).is(map)
        assert map.has('b')
        assert map.entries().collect() == [['a', 1], ['b', 2]]
        assert set.has('x')
        assert set.delete('x')
    }

    void testExtensionsCoverGroovyAndJavaMapSetImplementations() {
        List<java.util.Map> maps = [[a: 1], new HashMap([a: 1]), new TreeMap([a: 1])]
        List<java.util.Set> sets = [new LinkedHashSet(['x']), new HashSet(['x']), new TreeSet(['x'])]

        maps.each { java.util.Map map ->
            List calls = []
            assert map.set('b', 2).is(map)
            assert map.has('b')
            assert map.getSize() == 2
            assert map.forEach { value, key, owner -> calls << [key, value, owner.is(map)] } == null
            assert calls.toSet() == [['a', 1, true], ['b', 2, true]] as Set
            assert map.delete('b')
        }

        sets.each { java.util.Set set ->
            List calls = []
            assert set.has('x')
            assert set.getSize() == 1
            assert set.forEach { value, key, owner -> calls << [value, key, owner.is(set)] } == null
            assert calls == [['x', 'x', true]]
            assert set.delete('x')
        }
    }

    void testMapGroupByAndForEachCallbacks() {
        def grouped = JavaScriptMap.groupBy([1, 2, 3]) { it % 2 }
        List calls = []
        grouped.forEach { value, key, owner -> calls << [key, value, owner.is(grouped)] }

        assert grouped.get(1) == [1, 3]
        assert grouped.get(0) == [2]
        assert calls == [[1, [1, 3], true], [0, [2], true]]
    }
}
