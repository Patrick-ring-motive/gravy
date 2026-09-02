package com.example

import groovy.lang.Binding
import groovy.lang.GroovyShell
import groovy.lang.Script

/** Verifies JavaScript constructor aliases in isolated Groovy script bindings. */
class JavaScriptGlobalsTest extends GravyTestCase {
    void testBindingExposesPromiseAndSymbolToScripts() {
        Binding binding = JavaScriptGlobals.install(new Binding())
        GroovyShell shell = new GroovyShell(binding)

        assert binding.getVariable('Array').is(JavaScriptArray)
        assert binding.getVariable('String').is(JavaScriptString)
        assert binding.getVariable('Boolean').is(JavaScriptBoolean)
        assert binding.getVariable('Promise').is(JavaScriptPromise)
        assert binding.getVariable('Symbol').is(JavaScriptSymbol)
        assert binding.getVariable('Number').is(JavaScriptNumber)
        assert binding.getVariable('BigInt').is(JavaScriptBigInt)
        assert binding.getVariable('Map').is(JavaScriptMap)
        assert binding.getVariable('JSON').is(JavaScriptJSON)
        assert binding.getVariable('Math').is(JavaScriptMath)
        assert binding.getVariable('ArrayBuffer').is(JavaScriptArrayBuffer)
        assert binding.getVariable('Uint8Array').is(JavaScriptUint8Array)
        assert binding.getVariable('DataView').is(JavaScriptDataView)
        assert binding.getVariable('WeakRef').is(JavaScriptWeakRef)
        assert binding.getVariable('FinalizationRegistry').is(JavaScriptFinalizationRegistry)
        assert binding.getVariable('URL').is(JavaScriptURL)
        assert binding.getVariable('Response').is(JavaScriptResponse)
        assert binding.getVariable('WebSocket').is(JavaScriptWebSocket)
        assert binding.getVariable('Event').is(JavaScriptEvent)
        assert binding.getVariable('EventTarget').is(JavaScriptEventTarget)
        assert binding.getVariable('CustomEvent').is(JavaScriptCustomEvent)
        assert binding.getVariable('AbortController').is(JavaScriptAbortController)
        assert binding.getVariable('AbortSignal').is(JavaScriptAbortSignal)
        assert binding.getVariable('WritableStream').is(JavaScriptWritableStream)
        assert binding.getVariable('TransformStream').is(JavaScriptTransformStream)
        assert binding.getVariable('CompressionStream').is(JavaScriptCompressionStream)
        assert binding.getVariable('DecompressionStream').is(JavaScriptDecompressionStream)
        assert binding.getVariable('MessageChannel').is(JavaScriptMessageChannel)
        assert binding.getVariable('MessagePort').is(JavaScriptMessagePort)
        assert binding.getVariable('MessageEvent').is(JavaScriptMessageEvent)
        assert binding.getVariable('BroadcastChannel').is(JavaScriptBroadcastChannel)
        assert binding.getVariable('TextEncoder').is(JavaScriptTextEncoder)
        assert binding.getVariable('typeof') instanceof Closure
        assert binding.getVariable('btoa').call('x') == 'eA=='
        assert binding.getVariable('atob').call('eA==') == 'x'
        assert binding.getVariable('setTimeout') instanceof Closure
        assert binding.getVariable('clearTimeout') instanceof Closure
        assert binding.getVariable('setInterval') instanceof Closure
        assert binding.getVariable('clearInterval') instanceof Closure
        assert binding.getVariable('queueMicrotask') instanceof Closure
        assert binding.getVariable('console').is(JavaScriptConsole.INSTANCE)
        assert binding.getVariable('performance').is(JavaScriptPerformance.INSTANCE)
        assert binding.getVariable('navigator').is(JavaScriptNavigator.INSTANCE)
        assert binding.getVariable('location') instanceof JavaScriptLocation
        assert binding.getVariable('undefined') == null
        assert Double.isNaN(binding.getVariable('NaN') as double)
        assert binding.getVariable('Infinity') == Double.POSITIVE_INFINITY
        assert binding.getVariable('globalThis').is(binding)
        assert binding.getVariable('self').is(binding)
        assert binding.getVariable('global').is(binding)
        assert shell.evaluate("""
            assert Array.from('gravy').reverse().join('') == 'yvarg'
            assert String('user') == 'user'
            assert String(null) == 'null'
            assert String.fromCharCode(103, 114, 97, 118, 121) == 'gravy'
            assert Boolean(1)
            assert !Boolean(0)
            assert BigInt('9007199254740993').toString() == '9007199254740993'
            assert BigInt.asIntN(8, BigInt(255)).toString() == '-1'
            assert Promise.resolve(3).then { it * 2 }.await() == 6
            assert Symbol.'for'('shared').is(Symbol.forKey('shared'))
            assert typeof('ready') == 'string'
            assert typeof(3) == 'number'
            assert typeof(true) == 'boolean'
            assert typeof(Symbol('sample')) == 'symbol'
            assert typeof(Array) == 'function'
            assert typeof({}) == 'function'
            assert typeof(new String('ready')) == 'object'
            assert typeof(null) == 'object'
            assert typeof(undefined) == 'undefined'
            assert undefined == null
            assert Double.isNaN(NaN)
            assert Infinity == Double.POSITIVE_INFINITY
            assert isNaN('not a number')
            assert isFinite('42')
            assert parseInt('0x10') == 16
            assert parseFloat('1.5px') == 1.5d
            assert self.is(globalThis)
            assert global.is(globalThis)
            assert globalThis.getVariable('Promise').is(binding.getVariable('Promise'))
            globalThis.answer = 42
            assert answer == 42
            def ArrayConstructor = binding.getVariable('Array')
            assert ArrayConstructor.getDeclaredConstructor(Object[].class).newInstance([[3] as Object[]] as Object[]).size() == 3
            def NumberConstructor = binding.getVariable('Number')
            assert NumberConstructor.coerce('0x10') == 16
            def MapConstructor = binding.getVariable('Map')
            def map = MapConstructor.getDeclaredConstructor(Object).newInstance([[['language', 'Groovy']]] as Object[])
            assert map.get('language') == 'Groovy'
            assert JSON.stringify([value: Math.pow(2, 3)]) == '{\"value\":8.0}'
            true
        """)
    }

    void testEveryPublishedConstructorIsBoundFromRegistry() {
        Binding binding = JavaScriptGlobals.install(new Binding())

        JavaScriptGlobals.constructors().each { String name, Class type ->
            assert binding.getVariable(name).is(type): "Missing or wrong ${name} global"
            assert binding.getVariable('globalThis').getVariable(name).is(type): "Missing ${name} on globalThis"
        }
    }

    void testPreludeInstallsExtensionsAndGlobals() {
        Binding binding = new Binding()

        assert JavaScriptPrelude.install(binding).is(binding)
        assert binding.getVariable('Promise').is(JavaScriptPromise)
        assert binding.getVariable('globalThis').is(binding)
        assert 'gravy'.padEnd(7, '!') == 'gravy!!'
        assert [1, 2].map { it * 2 } == [2, 4]
    }

    void testScriptInstallationUsesItsBinding() {
        Binding binding = new Binding()
        Script script = new GroovyShell(binding).parse('this')

        assert JavaScriptGlobals.install(script).is(script)
        assert binding.getVariable('Promise').is(JavaScriptPromise)
        assert binding.getVariable('Symbol').is(JavaScriptSymbol)
    }
}
