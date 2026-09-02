package com.example

import groovy.lang.Binding
import groovy.lang.GroovyShell

/** Ensures globals work through both Binding injection and @BaseScript inheritance. */
class JavaScriptBaseScriptTest extends GravyTestCase {
    void testBindingInjectionPublishesEveryGlobal() {
        Binding binding = JavaScriptPrelude.install(new Binding())

        JavaScriptGlobals.globalNames().each { String name ->
            assert binding.hasVariable(name): "Binding is missing ${name}"
        }
        assert binding.getVariable('globalThis').is(binding)
        assert binding.getVariable('self').is(binding)
        assert binding.getVariable('global').is(binding)
    }

    void testBaseScriptExposesEveryGlobalAndUtilityMethod() {
        GroovyShell shell = new GroovyShell()

        assert shell.evaluate('''
            import groovy.transform.BaseScript
            import com.example.JavaScriptBaseScript
            import com.example.JavaScriptGlobals

            @BaseScript JavaScriptBaseScript gravy

            JavaScriptGlobals.globalNames().each { String name ->
                Object value = this.getProperty(name)
                assert name == 'undefined' ? value == null : value != null: "Missing ${name}"
            }

            assert Array.from('gravy').reverse().join('') == 'yvarg'
            assert new Array().empty
            assert String('gravy') == 'gravy'
            assert BigInt('9007199254740993').toString() == '9007199254740993'
            assert Intl.NumberFormat('en-US').format(1234) == '1,234'
            assert Promise.resolve(3).then { it * 2 }.await() == 6
            assert typeof(BigInt(1)) == 'bigint'
            assert isNaN('not a number')
            assert isFinite('42')
            assert parseInt('0x10') == 16
            assert parseFloat('1.5px') == 1.5d
            assert btoa('gravy') == 'Z3Jhdnk='
            assert atob('Z3Jhdnk=') == 'gravy'
            assert structuredClone([ready: true]) == [ready: true]
            assert fetch('data:text/plain,gravy').await().text().await() == 'gravy'

            def timeout = setTimeout({ }, 60_000)
            clearTimeout(timeout)
            def interval = setInterval({ }, 60_000)
            clearInterval(interval)
            queueMicrotask({ })

            assert console.is(com.example.JavaScriptConsole.INSTANCE)
            assert crypto.is(com.example.JavaScriptCrypto)
            assert performance.is(com.example.JavaScriptPerformance.INSTANCE)
            assert navigator.is(com.example.JavaScriptNavigator.INSTANCE)
            assert location instanceof com.example.JavaScriptLocation
            assert Double.isNaN(NaN)
            assert Infinity == Double.POSITIVE_INFINITY
            assert undefined == null
            assert globalThis.is(binding)
            assert self.is(globalThis)
            assert global.is(globalThis)
            globalThis.answer = 42
            assert answer == 42
            true
        ''')
    }
}
