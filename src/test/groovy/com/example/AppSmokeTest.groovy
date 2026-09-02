package com.example

import java.io.ByteArrayOutputStream
import java.io.PrintStream

/** Ensures executable sample constructs every supported global facade without errors. */
class AppSmokeTest extends GravyTestCase {
    void testAppPrintsGlobalFacadeSmokeValues() {
        PrintStream original = System.out
        ByteArrayOutputStream captured = new ByteArrayOutputStream()
        try {
            System.setOut(new PrintStream(captured, true, 'UTF-8'))
            App.main([] as String[])
        } finally {
            System.setOut(original)
        }

        String output = captured.toString('UTF-8')
        assert output.contains('Number: 3')
        assert output.contains('Promise: ready')
        assert output.contains('BigUint64Array: 1')
        assert output.contains('SubtleCrypto:')
        assert output.contains('URL: https://example.test/path?tag=gravy')
        assert output.contains('fetch: true')
        assert output.contains('location: http://localhost/')
        assert output.contains("${System.lineSeparator()}2${System.lineSeparator()}2${System.lineSeparator()}class com.example.JavaScriptSymbol")
    }
}
