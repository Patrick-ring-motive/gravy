package com.example

import groovy.lang.Binding
import groovy.lang.GroovyShell

/**
 * End-to-end constructor matrix for globals exposed by JavaScriptPrelude.
 *
 * Uses root main.groovy's constructor-import-free prelude so every `new Name(...)`
 * form is compiled exactly as application scripts compile it.
 */
class JavaScriptGlobalConstructorMatrixTest extends GravyTestCase {
    void testBindingPublishesEveryConstructorAndUtility() {
        Binding binding = JavaScriptPrelude.install(new Binding())
        Map<String, Class> constructors = [
            Array: JavaScriptArray, String: JavaScriptString, Boolean: JavaScriptBoolean,
            Promise: JavaScriptPromise, Symbol: JavaScriptSymbol, Function: JavaScriptFunction,
            Number: JavaScriptNumber, BigInt: JavaScriptBigInt, Intl: JavaScriptIntl, Map: JavaScriptMap, Set: JavaScriptSet,
            Error: JavaScriptError, AggregateError: JavaScriptAggregateError, EvalError: JavaScriptEvalError,
            RangeError: JavaScriptRangeError, ReferenceError: JavaScriptReferenceError,
            SyntaxError: JavaScriptSyntaxError, TypeError: JavaScriptTypeError, URIError: JavaScriptURIError,
            Generator: JavaScriptGenerator, RegExp: JavaScriptRegExp, Date: JavaScriptDate,
            ArrayBuffer: JavaScriptArrayBuffer, DataView: JavaScriptDataView,
            Int8Array: JavaScriptInt8Array, Uint8Array: JavaScriptUint8Array,
            Uint8ClampedArray: JavaScriptUint8ClampedArray, Int16Array: JavaScriptInt16Array,
            Uint16Array: JavaScriptUint16Array, Int32Array: JavaScriptInt32Array,
            Uint32Array: JavaScriptUint32Array, Float32Array: JavaScriptFloat32Array,
            Float64Array: JavaScriptFloat64Array, BigInt64Array: JavaScriptBigInt64Array,
            BigUint64Array: JavaScriptBigUint64Array, URL: JavaScriptURL,
            URLSearchParams: JavaScriptURLSearchParams, Headers: JavaScriptHeaders,
            FormData: JavaScriptFormData, Blob: JavaScriptBlob, File: JavaScriptFile,
            ReadableStream: JavaScriptReadableStream, WritableStream: JavaScriptWritableStream,
            TransformStream: JavaScriptTransformStream,
            ByteLengthQueuingStrategy: JavaScriptByteLengthQueuingStrategy,
            CountQueuingStrategy: JavaScriptCountQueuingStrategy,
            CompressionStream: JavaScriptCompressionStream, DecompressionStream: JavaScriptDecompressionStream,
            Request: JavaScriptRequest, Response: JavaScriptResponse, WebSocket: JavaScriptWebSocket,
            Event: JavaScriptEvent, EventTarget: JavaScriptEventTarget, CustomEvent: JavaScriptCustomEvent,
            AbortController: JavaScriptAbortController, AbortSignal: JavaScriptAbortSignal,
            MessageChannel: JavaScriptMessageChannel, MessagePort: JavaScriptMessagePort,
            MessageEvent: JavaScriptMessageEvent, BroadcastChannel: JavaScriptBroadcastChannel,
            TextEncoder: JavaScriptTextEncoder, TextDecoder: JavaScriptTextDecoder, Crypto: JavaScriptCrypto,
            WeakMap: JavaScriptWeakMap, WeakSet: JavaScriptWeakSet, WeakRef: JavaScriptWeakRef,
            FinalizationRegistry: JavaScriptFinalizationRegistry, Reflect: JavaScriptReflect,
            Proxy: JavaScriptProxy, JSON: JavaScriptJSON, Math: JavaScriptMath
        ]

        constructors.each { String name, Class type ->
            assert binding.getVariable(name).is(type): "Missing or wrong ${name} global"
            assert binding.getVariable('globalThis').getVariable(name).is(type): "Missing ${name} on globalThis"
        }
        ['typeof', 'isNaN', 'isFinite', 'parseFloat', 'parseInt', 'btoa', 'atob', 'structuredClone', 'fetch',
         'setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'queueMicrotask'].each { String name ->
            assert binding.getVariable(name) instanceof Closure: "Missing ${name} global function"
        }
        assert binding.getVariable('console').is(JavaScriptConsole.INSTANCE)
        assert binding.getVariable('crypto').is(JavaScriptCrypto)
        assert binding.getVariable('performance').is(JavaScriptPerformance.INSTANCE)
        assert binding.getVariable('navigator').is(JavaScriptNavigator.INSTANCE)
        assert binding.getVariable('location') instanceof JavaScriptLocation
        assert binding.getVariable('globalThis').is(binding)
        assert binding.getVariable('self').is(binding)
        assert binding.getVariable('global').is(binding)
    }

    void testEveryCallableGlobalWorksThroughConstructorFreePrelude() {
        assert JavaScriptGlobals.callableConstructorNames().every { String name -> JavaScriptGlobals.constructors().containsKey(name) }

        JavaScriptGlobals.callableConstructorNames().each { String name ->
            String argument = name == 'BigInt' ? "'0'" : ''
            Object result = new GroovyShell().evaluate(mainPreludeSource() + """
                def result = ${name}(${argument})
                assert result != null
                result
            """)
            assert result != null: "${name}() returned null"
        }
    }

    void testMainPreludeGlobalsCompileAndConstructEveryConstructableGlobal() {
        assert new GroovyShell().evaluate(mainPreludeSource() + '''
            def key = new java.lang.Object()
            def buffer = new ArrayBuffer(8)
            def values = [
                new Array(), new Object(), new String(), new Boolean(),
                new Promise({ resolve, reject -> resolve.call('ready') }), new Function(), new Number(),
                new Map(), new Set(),
                new Error(), new AggregateError([], 'aggregate'), new EvalError(), new RangeError(),
                new ReferenceError(), new SyntaxError(), new TypeError(), new URIError(),
                new RegExp(), new Date(0), buffer, new DataView(buffer),
                new Int8Array(), new Uint8Array(), new Uint8ClampedArray(), new Int16Array(),
                new Uint16Array(), new Int32Array(), new Uint32Array(), new Float32Array(),
                new Float64Array(), new BigInt64Array(), new BigUint64Array(),
                new URL('https://example.test/'), new URLSearchParams(), new Headers(), new FormData(),
                new Blob(), new File([], 'gravy.txt'), new ReadableStream(), new WritableStream(),
                new TransformStream(), new ByteLengthQueuingStrategy([highWaterMark: 1]),
                new CountQueuingStrategy([highWaterMark: 1]), new CompressionStream('gzip'),
                new DecompressionStream('gzip'), new Request('data:text/plain,gravy'), new Response(),
                new Event('ready'), new EventTarget(), new CustomEvent('ready'), new AbortController(),
                new AbortSignal(), new MessageChannel(), new MessagePort(), new MessageEvent('message'),
                new BroadcastChannel('constructor-test'), new TextEncoder(), new TextDecoder(),
                new WeakMap(), new WeakSet(), new WeakRef(key), new FinalizationRegistry({ value -> }),
                new Proxy([:], [:]), new Intl.Collator(), new Intl.DateTimeFormat(),
                new Intl.DisplayNames('en', [type: 'language']), new Intl.DurationFormat(),
                new Intl.ListFormat(), new Intl.Locale('en-US'), new Intl.NumberFormat(),
                new Intl.PluralRules(), new Intl.RelativeTimeFormat(), new Intl.Segmenter()
            ]

            assert values.every { it != null }
            assert values[4].await() == 'ready'
            assert Symbol('sample').description == 'sample'
            assert Function.of({ value -> value }).call(null, 'ready') == 'ready'
            assert Generator.iterate(1, { value -> value + 1 }).next() == [value: 1, done: false]
            assert Object.fromEntries([['ready', true]]).ready
            assert Crypto.randomUUID() ==~ /[0-9a-f-]{36}/
            assert Reflect.get([ready: true], 'ready')
            assert JSON.stringify([ready: true]) == '{"ready":true}'
            assert Math.sqrt(9) == 3d
            true
        ''')
    }

    void testExplicitlyQualifiedJvmConstructorsAreNotRewritten() {
        assert new GroovyShell().evaluate(mainPreludeSource() + '''
            def text = new java.lang.String('jvm')
            def date = new java.util.Date(123L)
            def number = new java.math.BigDecimal('1.25')

            assert text.class == java.lang.String
            assert text == 'jvm'
            assert date.class == java.util.Date
            assert date.time == 123L
            assert number.class == java.math.BigDecimal
            true
        ''')
    }

    private static String mainPreludeSource() {
        String source = new File('main.groovy').getText('UTF-8')
        String install = 'JavaScriptPrelude.install(this)'
        int preludeEnd = source.indexOf(install)
        assert preludeEnd >= 0: 'main.groovy must install JavaScriptPrelude'
        source.substring(0, preludeEnd + install.length())
    }
}
