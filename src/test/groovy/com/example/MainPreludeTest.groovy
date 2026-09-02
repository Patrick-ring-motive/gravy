package com.example

import groovy.lang.GroovyShell

/** Verifies root main.groovy uses global constructor rewriting with no aliases. */
class MainPreludeTest extends GravyTestCase {
    void testPreludeInstallsGlobalsWithoutConstructorAliases() {
        String source = new File('main.groovy').getText('UTF-8')
        String install = 'JavaScriptPrelude.install(this)'
        int preludeEnd = source.indexOf(install)
        assert preludeEnd >= 0: 'main.groovy must install JavaScriptPrelude'
        String prelude = source.substring(0, preludeEnd + install.length())
        assert prelude.readLines().findAll { it.startsWith('import com.example.JavaScript') } == [
            'import com.example.JavaScriptPrelude'
        ]
        GroovyShell shell = new GroovyShell()

        assert shell.evaluate(prelude + '''
            assert Array.from('gravy').reverse().join('') == 'yvarg'
            assert new Array().empty
            assert String('gravy') == 'gravy'
            assert new String(true).toString() == 'true'
            assert typeof(String(true)) == 'string'
            assert typeof(new String(true)) == 'object'
            assert typeof(undefined) == 'undefined'
            assert String.fromCodePoint(0x1F600) == '😀'
            assert Boolean('false')
            assert !Boolean('')
            assert new Boolean(1).valueOf()
            assert new Number('3').intValue() == 3
            assert new Set([1, 1]).size == 1
            assert RegExp('^gravy$').test('gravy')
            assert new Request('data:text/plain,gravy').url == 'data:text/plain,gravy'
            assert new Headers([Accept: 'text/plain']).get('accept') == 'text/plain'
            assert globalThis.getVariable('Number').call(3).intValue() == 3
            assert globalThis.getVariable('Map').is(globalThis.Map)
            assert globalThis.getVariable('Uint8Array').simpleName == 'JavaScriptUint8Array'
            assert Symbol.create('sample').description == 'sample'
            assert btoa('gravy') == 'Z3Jhdnk='
            assert atob('Z3Jhdnk=') == 'gravy'
            assert console instanceof com.example.JavaScriptConsole
            assert crypto.randomUUID() ==~ /[0-9a-f-]{36}/
            assert location.href == 'http://localhost/'
            true
        ''')
    }
}
