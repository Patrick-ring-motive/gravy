package com.example

import com.example.JavaScriptAggregateError as AggregateError
import com.example.JavaScriptArray as Array
import com.example.JavaScriptArrayBuffer as ArrayBuffer
import com.example.JavaScriptBigInt64Array as BigInt64Array
import com.example.JavaScriptBigUint64Array as BigUint64Array
import com.example.JavaScriptBlob as Blob
import com.example.JavaScriptCrypto as Crypto
import com.example.JavaScriptDataView as DataView
import com.example.JavaScriptDate as Date
import com.example.JavaScriptError as Error
import com.example.JavaScriptEvalError as EvalError
import com.example.JavaScriptFile as File
import com.example.JavaScriptFinalizationRegistry as FinalizationRegistry
import com.example.JavaScriptFloat32Array as Float32Array
import com.example.JavaScriptFloat64Array as Float64Array
import com.example.JavaScriptFormData as FormData
import com.example.JavaScriptFunction as Function
import com.example.JavaScriptGenerator as Generator
import com.example.JavaScriptHeaders as Headers
import com.example.JavaScriptInt16Array as Int16Array
import com.example.JavaScriptInt32Array as Int32Array
import com.example.JavaScriptInt8Array as Int8Array
import com.example.JavaScriptJSON as JSON
import com.example.JavaScriptMap as Map
import com.example.JavaScriptMath as Math
import com.example.JavaScriptNumber as Number
import com.example.JavaScriptPromise as Promise
import com.example.JavaScriptProxy as Proxy
import com.example.JavaScriptRangeError as RangeError
import com.example.JavaScriptReadableStream as ReadableStream
import com.example.JavaScriptReflect as Reflect
import com.example.JavaScriptReferenceError as ReferenceError
import com.example.JavaScriptRegExp as RegExp
import com.example.JavaScriptRequest as Request
import com.example.JavaScriptResponse as Response
import com.example.JavaScriptSet as Set
import com.example.JavaScriptSymbol as Symbol
import com.example.JavaScriptSyntaxError as SyntaxError
import com.example.JavaScriptTextDecoder as TextDecoder
import com.example.JavaScriptTextEncoder as TextEncoder
import com.example.JavaScriptTypeError as TypeError
import com.example.JavaScriptUint16Array as Uint16Array
import com.example.JavaScriptUint32Array as Uint32Array
import com.example.JavaScriptUint8Array as Uint8Array
import com.example.JavaScriptUint8ClampedArray as Uint8ClampedArray
import com.example.JavaScriptURIError as URIError
import com.example.JavaScriptURL as URL
import com.example.JavaScriptURLSearchParams as URLSearchParams
import com.example.JavaScriptWeakMap as WeakMap
import com.example.JavaScriptWeakRef as WeakRef
import com.example.JavaScriptWeakSet as WeakSet
import groovy.lang.Binding
import org.codehaus.groovy.runtime.InvokerHelper

/** Executable smoke sample for every available Gravy global facade. */
class App {
    private static final Class crypto = Crypto
    private static final JavaScriptLocation location = new JavaScriptLocation()

    static void main(String[] args) {
        JavaScriptObjectExtensions.install()
        JavaScriptStringExtensions.install()
        JavaScriptArrayExtensions.install()

        Binding globals = JavaScriptGlobals.install(new Binding())
        // Binding lookup preserves global aliases shadowed by java.lang.Number and java.util.Map.
        samplePatchedTypes()
        sampleGlobalConstructors(globals)
        sampleGlobalUtilities(globals)
        sampleDirectGlobalAliases()

        Number boxed = new Number(2)
        java.lang.Number called = Number(2)
        check(boxed.intValue() == 2, 'new Number(value)')
        check(called.intValue() == 2, 'Number(value)')
        check(Symbol.is(JavaScriptSymbol), 'Symbol global alias')
        println(boxed)
        println(called)
        println(Symbol)
        println(Function)
        println(Map)
        println(Set)
    }

    /** Class methods cannot resolve Binding values as bare functions; mirror global Number(). */
    private static java.lang.Number Number(Object value) {
        JavaScriptNumber.call(value)
    }

    private static void samplePatchedTypes() {
        String text = 'Hello, Groovy!'.replace('Groovy', 'JavaScript').padEnd(24, '!')
        List values = [1, 2, 3].map { it * 2 }
        Object object = Object.fromEntries([['language', 'Groovy']])
        Object[] nativeArray = java.util.List.from('gravy')
        JavaScriptMap nativeGroups = java.util.Map.groupBy([1, 2, 3, 4]) { value -> value % 2 }

        java.util.Map nativeMap = [a: 1]
        java.util.Set nativeSet = new LinkedHashSet(['x'])
        List mapCalls = []
        List setCalls = []
        nativeMap.set('b', 2)
        nativeSet.add('y')
        nativeMap.forEach { value, key, owner -> mapCalls << [key, value, owner.is(nativeMap)] }
        nativeSet.forEach { value, key, owner -> setCalls << [value, key, owner.is(nativeSet)] }

        check(text == 'Hello, JavaScript!!!!!!!', 'String extensions')
        check(values == [2, 4, 6], 'Array extensions')
        check(object.language == 'Groovy', 'Object extensions')
        check(([]).prototype != null, 'Object prototype extension')
        check(nativeArray.reverse().join('') == 'yvarg', 'native Array static extensions')
        check(java.lang.Number.isInteger(3) && java.lang.Number.parseInt('0x10') == 16, 'native Number static extensions')
        check(nativeGroups.get(0) == [2, 4] && nativeGroups.get(1) == [1, 3], 'native Map static extensions')
        check(java.util.Date.now() > 0L, 'native Date static extension')
        check(java.util.regex.Pattern.escape('a+b') == 'a\\+b', 'native RegExp static extension')
        check(nativeMap.has('b') && nativeMap.getSize() == 2, 'native Map extensions')
        check(nativeSet.has('y') && nativeSet.getSize() == 2, 'native Set extensions')
        check(mapCalls.toSet() == [['a', 1, true], ['b', 2, true]] as java.util.Set, 'native Map forEach')
        check(setCalls.toSet() == [['x', 'x', true], ['y', 'y', true]] as java.util.Set, 'native Set forEach')
        show('String', text)
        show('Array', values)
        show('Object', object)
        show('Object prototype', ([]).prototype)
        show('native Array', nativeArray)
        show('native Map.groupBy', nativeGroups)
        show('native Map', nativeMap)
        show('native Set', nativeSet)
    }

    private static void sampleGlobalConstructors(Binding globals) {
        def array = construct(globals, 'Array', 3)
        Object promise = construct(globals, 'Promise', { resolve, reject -> resolve.call('ready') }).await()
        JavaScriptSymbol symbol = (global(globals, 'Symbol') as Class).create('sample')
        def function = (global(globals, 'Function') as Class).of({ Object value -> "called:${value}" })
        java.lang.Number number = construct(globals, 'Number', 3)
        def map = construct(globals, 'Map', [['key', 'value']])
        def set = construct(globals, 'Set', [1, 2, 2])

        check(array instanceof JavaScriptArray && array.size() == 3 && array.every { it == null }, 'Array constructor')
        check((global(globals, 'Array') as Class).from('gravy').reverse().join('') == 'yvarg', 'Array.from')
        check(promise == 'ready', 'Promise executor')
        check(symbol.description == 'sample', 'Symbol constructor')
        check(function.call(null, 'sample') == 'called:sample', 'Function facade')
        check(number.intValue() == 3, 'Number constructor')
        check(map.get('key') == 'value' && map.size == 1, 'Map constructor')
        check(set.has(1) && set.size == 2, 'Set constructor')
        show('Array', array)
        show('Promise', promise)
        show('Symbol', symbol)
        show('Function', function.call(null, 'sample'))
        show('Number', number)
        show('Map', map)
        show('Set', set)

        ['Error', 'AggregateError', 'EvalError', 'RangeError', 'ReferenceError', 'SyntaxError', 'TypeError', 'URIError'].each { String name ->
            JavaScriptError value = name == 'AggregateError' ? construct(globals, name, [], 'sample') : construct(globals, name, 'sample')
            check(value.name == name && value.message == 'sample', "${name} constructor")
            show(name, value)
        }

        def generator = (global(globals, 'Generator') as Class).iterate(1, { Object value -> value + 1 })
        java.util.Map generatorValue = generator.next()
        def expression = construct(globals, 'RegExp', 'gro+', 'i')
        def date = construct(globals, 'Date', 0)
        check(generatorValue == [value: 1, done: false], 'Generator iterator')
        check(expression.test('GROOVY'), 'RegExp constructor')
        check(date.time == 0d, 'Date constructor')
        show('Generator', generatorValue)
        show('RegExp', expression)
        show('Date', date)

        def buffer = construct(globals, 'ArrayBuffer', 8)
        def dataView = construct(globals, 'DataView', buffer)
        dataView.setUint16(0, 0x1234)
        check(buffer.byteLength == 8, 'ArrayBuffer constructor')
        check(dataView.getUint16(0) == 0x1234, 'DataView read/write')
        show('ArrayBuffer', buffer)
        show('DataView', dataView)
        ['Int8Array', 'Uint8Array', 'Uint8ClampedArray', 'Int16Array', 'Uint16Array', 'Int32Array', 'Uint32Array',
         'Float32Array', 'Float64Array', 'BigInt64Array', 'BigUint64Array'].each { String name ->
            List input = name.startsWith('Big') ? [BigInteger.ONE] : [1, 2]
            def typed = construct(globals, name, input)
            check(typed.length == input.size() && typed[0] == input[0], "${name} constructor")
            show(name, typed)
        }

        Object key = new Object()
        def weakMap = construct(globals, 'WeakMap')
        def weakSet = construct(globals, 'WeakSet')
        def weakRef = construct(globals, 'WeakRef', key)
        def finalizationRegistry = construct(globals, 'FinalizationRegistry', { Object value -> })
        Object token = new Object()
        weakMap.set(key, 'value')
        weakSet.add(key)
        finalizationRegistry.register(key, 'held', token)
        check(weakMap.get(key) == 'value', 'WeakMap set/get')
        check(weakSet.has(key), 'WeakSet add/has')
        check(weakRef.deref().is(key), 'WeakRef deref')
        check(finalizationRegistry.unregister(token), 'FinalizationRegistry unregister')
        show('WeakMap', weakMap)
        show('WeakSet', weakSet)
        show('WeakRef', weakRef.deref())
        show('FinalizationRegistry', finalizationRegistry)

        Class reflect = global(globals, 'Reflect') as Class
        java.util.Map reflected = [language: 'Groovy']
        check(reflect.get(reflected, 'language') == 'Groovy', 'Reflect get')
        check(reflect.set(reflected, 'checked', true) && reflected.checked, 'Reflect set')
        def proxy = construct(globals, 'Proxy', [language: 'Groovy'], [:])
        check(proxy.language == 'Groovy' && reflect.set(proxy, 'checked', true), 'Proxy forwarding')
        String json = (global(globals, 'JSON') as Class).stringify([language: 'Groovy'])
        double squareRoot = (global(globals, 'Math') as Class).sqrt(9)
        check(json == '{"language":"Groovy"}', 'JSON stringify')
        check(squareRoot == 3d, 'Math sqrt')
        show('Reflect', reflected)
        show('Proxy', proxy)
        show('JSON', json)
        show('Math', squareRoot)

        def url = construct(globals, 'URL', 'https://example.test/path?tag=gravy')
        def params = construct(globals, 'URLSearchParams', 'tag=gravy')
        def headers = construct(globals, 'Headers', ['x-gravy': 'enabled'])
        def formData = construct(globals, 'FormData')
        formData.append('name', 'gravy')
        def blob = construct(globals, 'Blob', ['gravy'])
        def file = construct(globals, 'File', ['gravy'], 'gravy.txt')
        def stream = construct(globals, 'ReadableStream', ['chunk'])
        def request = construct(globals, 'Request', 'https://example.test/', [method: 'POST', body: 'request'])
        def response = construct(globals, 'Response', 'ready')
        def encoder = construct(globals, 'TextEncoder')
        def decoder = construct(globals, 'TextDecoder')
        check(url.searchParams.get('tag') == 'gravy', 'URL constructor')
        check(params.get('tag') == 'gravy', 'URLSearchParams constructor')
        check(headers.get('x-gravy') == 'enabled', 'Headers constructor')
        check(formData.get('name') == 'gravy', 'FormData append')
        check(blob.text().await() == 'gravy', 'Blob text')
        check(file.name == 'gravy.txt', 'File constructor')
        check(stream.getReader().read().await() == [value: 'chunk', done: false], 'ReadableStream reader')
        check(request.text().await() == 'request', 'Request body')
        check(response.text().await() == 'ready' && response.ok, 'Response body')
        check(encoder.encode('gravy').values().collect() == [103, 114, 97, 118, 121], 'TextEncoder')
        check(decoder.decode(new JavaScriptUint8Array([103, 114, 97, 118, 121])) == 'gravy', 'TextDecoder')
        show('URL', url)
        show('URLSearchParams', params)
        show('Headers', headers)
        show('FormData', formData)
        show('Blob', blob.text().await())
        show('File', file)
        show('ReadableStream', [value: 'chunk', done: false])
        show('Request', request)
        show('Response', response)
        show('TextEncoder', encoder.encode('gravy'))
        show('TextDecoder', 'gravy')

        Class crypto = global(globals, 'Crypto') as Class
        String uuid = crypto.randomUUID()
        def digest = crypto.subtle.digest('SHA-256', new JavaScriptUint8Array([1])).await()
        check(uuid ==~ /[0-9a-f-]{36}/, 'crypto.randomUUID')
        check(digest.byteLength == 32, 'crypto.subtle.digest')
        show('Crypto', uuid)
        show('SubtleCrypto', digest)
    }

    private static void sampleGlobalUtilities(Binding globals) {
        String encoded = (global(globals, 'btoa') as Closure).call('gravy')
        String decoded = (global(globals, 'atob') as Closure).call(encoded)
        java.util.Map source = [ready: true]
        java.util.Map copied = (global(globals, 'structuredClone') as Closure).call(source) as java.util.Map
        Closure fetch = global(globals, 'fetch') as Closure
        JavaScriptPromise rejectedFetch = fetch.call('not a valid URL') as JavaScriptPromise
        Object crypto = global(globals, 'crypto')
        def location = global(globals, 'location')
        check(encoded == 'Z3Jhdnk=' && decoded == 'gravy', 'btoa and atob')
        check(copied == source && !copied.is(source), 'structuredClone')
        check(rejectedFetch.toCompletableFuture().completedExceptionally, 'fetch rejection')
        check(crypto.is(JavaScriptCrypto), 'crypto global')
        check(location.href == 'http://localhost/', 'location global')
        show('btoa', encoded)
        show('atob', decoded)
        show('structuredClone', copied)
        show('fetch', rejectedFetch.toCompletableFuture().completedExceptionally)
        show('crypto', crypto)
        show('location', location)
    }

    /** Direct class aliases mirror every uppercase constructor exposed by JavaScriptGlobals. */
    private static void sampleDirectGlobalAliases() {
        List<Class> aliases = [
            Array, Promise, Symbol, Function, Number, Map, Set,
            Error, AggregateError, EvalError, RangeError, ReferenceError, SyntaxError, TypeError, URIError,
            Generator, RegExp, Date, ArrayBuffer, DataView,
            Int8Array, Uint8Array, Uint8ClampedArray, Int16Array, Uint16Array, Int32Array, Uint32Array,
            Float32Array, Float64Array, BigInt64Array, BigUint64Array,
            URL, URLSearchParams, Headers, FormData, Blob, File, ReadableStream, Request, Response,
            TextEncoder, TextDecoder, Crypto, WeakMap, WeakSet, WeakRef, FinalizationRegistry,
            Reflect, Proxy, JSON, Math
        ]
        check(aliases.every { Class alias -> alias != null }, 'direct class aliases')
        aliases.each { Class alias -> println(alias) }

        check(btoa('gravy') == 'Z3Jhdnk=', 'direct btoa')
        check(atob('Z3Jhdnk=') == 'gravy', 'direct atob')
        check(structuredClone([ready: true]) == [ready: true], 'direct structuredClone')
        check(fetch('not a valid URL').toCompletableFuture().completedExceptionally, 'direct fetch')
        check(crypto.is(Crypto), 'direct crypto')
        check(location.href == 'http://localhost/', 'direct location')
        println(crypto)
        println(location)
    }

    private static String btoa(Object value) {
        JavaScriptWebUtilities.btoa(value)
    }

    private static String atob(Object value) {
        JavaScriptWebUtilities.atob(value)
    }

    private static Object structuredClone(Object value) {
        JavaScriptWebUtilities.structuredClone(value)
    }

    private static JavaScriptPromise fetch(Object input, Object init = [:]) {
        JavaScriptFetch.fetch(input, init)
    }

    private static void check(boolean condition, String subject) {
        assert condition: "Failed App check: ${subject}"
    }

    private static Object construct(Binding globals, String name, Object... arguments) {
        InvokerHelper.invokeConstructorOf(global(globals, name) as Class, arguments)
    }

    private static Object global(Binding globals, String name) {
        globals.getVariable(name)
    }

    private static void show(String name, Object value) {
        println("${name}: ${value}")
    }
}
