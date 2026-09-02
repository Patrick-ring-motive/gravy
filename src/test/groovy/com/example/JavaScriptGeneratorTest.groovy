package com.example

import java.util.stream.Stream

/** Derived Stream-backed generator checks. */
class JavaScriptGeneratorTest extends GravyTestCase {
    void testGenerateDefersCallableUntilNext() {
        int calls = 0
        def generator = JavaScriptGenerator.generate { ++calls }

        assert calls == 0
        assert generator.next() == [value: 1, done: false]
        assert generator.next() == [value: 2, done: false]
        assert calls == 2
    }

    void testIterateAndReturnFollowIteratorResultShape() {
        def generator = JavaScriptGenerator.iterate(1) { it * 2 }

        assert generator.next() == [value: 1, done: false]
        assert generator.next() == [value: 2, done: false]
        assert generator.'return'('complete') == [value: 'complete', done: true]
        assert generator.next() == [value: null, done: true]
    }

    void testStreamExtensionCreatesOneShotGenerator() {
        def generator = Stream.of('a', 'b').asJavaScriptGenerator()

        assert generator.collect() == ['a', 'b']
        assert generator.done
        assert generator.next() == [value: null, done: true]
    }
}
