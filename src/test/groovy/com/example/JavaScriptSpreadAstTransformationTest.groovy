package com.example

import groovy.lang.GroovyShell

/** Verifies Groovy list spread literals accept JavaScript-style string iterables. */
class JavaScriptSpreadAstTransformationTest extends GravyTestCase {
    void testStringSpreadCreatesUnicodeCodePointArray() {
        GroovyShell shell = new GroovyShell()

        assert shell.evaluate("""
            def text = 'A😄'
            [*text].toList()
        """) == ['A', '😄']
    }

    void testStringSpreadComposesWithOrdinaryListElements() {
        GroovyShell shell = new GroovyShell()

        assert shell.evaluate("""
            def text = 'go'
            [0, *text, 3].toList()
        """) == [0, 'g', 'o', 3]
    }

    void testArraySpreadRemainsSupported() {
        GroovyShell shell = new GroovyShell()

        assert shell.evaluate('[*([1, 2] as int[])].toList()') == [1, 2]
    }

    void testSpreadInsideClosureAcceptsJavaScriptSet() {
        GroovyShell shell = new GroovyShell()

        assert shell.evaluate('''
            def unique = { [*new com.example.JavaScriptSet([1, 2, 1])].toList() }
            unique()
        ''') == [1, 2]
    }

    void testImplicitAwaitUnwrapsJavaScriptPromises() {
        GroovyShell shell = new GroovyShell()

        assert shell.evaluate("await com.example.JavaScriptPromise.resolve('ready')") == 'ready'
    }

    void testImplicitAwaitPassesThroughNonPromiseValues() {
        GroovyShell shell = new GroovyShell()

        assert shell.evaluate('await 42') == 42
        assert shell.evaluate("await 'ready'") == 'ready'
    }

    void testTypeofUndefinedUsesJavaScriptUndefinedResult() {
        GroovyShell shell = new GroovyShell()

        assert shell.evaluate('typeof(undefined)') == 'undefined'
    }
}
