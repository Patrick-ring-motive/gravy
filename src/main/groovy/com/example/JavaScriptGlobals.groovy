package com.example

import groovy.lang.Binding
import groovy.lang.Script

import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Map
import java.util.Set

/**
 * Binds JavaScript-style global constructors into a Groovy script {@link Binding}.
 *
 * Bindings are scoped to one script environment. Install these aliases into each
 * application, shell, or Pipeline binding that should expose {@code Promise} and
 * {@code Symbol} without imports.
 */
final class JavaScriptGlobals {
    private static final Map<String, Class> CONSTRUCTORS = Collections.unmodifiableMap(new LinkedHashMap<>([
        Array         : JavaScriptArray,
        String        : JavaScriptString,
        Boolean       : JavaScriptBoolean,
        Promise       : JavaScriptPromise,
        Symbol        : JavaScriptSymbol,
        Function      : JavaScriptFunction,
        Number        : JavaScriptNumber,
        BigInt        : JavaScriptBigInt,
        Intl          : JavaScriptIntl,
        Map           : JavaScriptMap,
        Set           : JavaScriptSet,
        Error         : JavaScriptError,
        AggregateError: JavaScriptAggregateError,
        EvalError     : JavaScriptEvalError,
        RangeError    : JavaScriptRangeError,
        ReferenceError: JavaScriptReferenceError,
        SyntaxError   : JavaScriptSyntaxError,
        TypeError     : JavaScriptTypeError,
        URIError      : JavaScriptURIError,
        Generator     : JavaScriptGenerator,
        RegExp        : JavaScriptRegExp,
        Date          : JavaScriptDate,
        ArrayBuffer   : JavaScriptArrayBuffer,
        DataView      : JavaScriptDataView,
        Int8Array     : JavaScriptInt8Array,
        Uint8Array    : JavaScriptUint8Array,
        Uint8ClampedArray: JavaScriptUint8ClampedArray,
        Int16Array    : JavaScriptInt16Array,
        Uint16Array   : JavaScriptUint16Array,
        Int32Array    : JavaScriptInt32Array,
        Uint32Array   : JavaScriptUint32Array,
        Float32Array  : JavaScriptFloat32Array,
        Float64Array  : JavaScriptFloat64Array,
        BigInt64Array : JavaScriptBigInt64Array,
        BigUint64Array: JavaScriptBigUint64Array,
        URL           : JavaScriptURL,
        URLSearchParams: JavaScriptURLSearchParams,
        Headers       : JavaScriptHeaders,
        FormData      : JavaScriptFormData,
        Blob          : JavaScriptBlob,
        File          : JavaScriptFile,
        ReadableStream: JavaScriptReadableStream,
        WritableStream: JavaScriptWritableStream,
        TransformStream: JavaScriptTransformStream,
        ByteLengthQueuingStrategy: JavaScriptByteLengthQueuingStrategy,
        CountQueuingStrategy: JavaScriptCountQueuingStrategy,
        CompressionStream: JavaScriptCompressionStream,
        DecompressionStream: JavaScriptDecompressionStream,
        Request       : JavaScriptRequest,
        Response      : JavaScriptResponse,
        WebSocket     : JavaScriptWebSocket,
        Event         : JavaScriptEvent,
        EventTarget   : JavaScriptEventTarget,
        CustomEvent   : JavaScriptCustomEvent,
        AbortController: JavaScriptAbortController,
        AbortSignal   : JavaScriptAbortSignal,
        MessageChannel: JavaScriptMessageChannel,
        MessagePort   : JavaScriptMessagePort,
        MessageEvent  : JavaScriptMessageEvent,
        BroadcastChannel: JavaScriptBroadcastChannel,
        TextEncoder   : JavaScriptTextEncoder,
        TextDecoder   : JavaScriptTextDecoder,
        Crypto        : JavaScriptCrypto,
        WeakMap       : JavaScriptWeakMap,
        WeakSet       : JavaScriptWeakSet,
        WeakRef       : JavaScriptWeakRef,
        FinalizationRegistry: JavaScriptFinalizationRegistry,
        Reflect       : JavaScriptReflect,
        Proxy         : JavaScriptProxy,
        JSON          : JavaScriptJSON,
        Math          : JavaScriptMath
    ]))
    private static final List<String> CALLABLE_CONSTRUCTORS = Collections.unmodifiableList([
        'Array', 'String', 'Boolean', 'Number', 'BigInt', 'Symbol', 'Function', 'Error', 'AggregateError',
        'EvalError', 'RangeError', 'ReferenceError', 'SyntaxError', 'TypeError', 'URIError', 'RegExp', 'Date'
    ])
    private static final Set<String> GLOBAL_NAMES = Collections.unmodifiableSet(new LinkedHashSet<String>(
        CONSTRUCTORS.keySet() + [
            'undefined', 'NaN', 'Infinity', 'typeof', 'isNaN', 'isFinite', 'parseFloat', 'parseInt',
            'btoa', 'atob', 'structuredClone', 'fetch', 'setTimeout', 'clearTimeout', 'setInterval',
            'clearInterval', 'queueMicrotask', 'console', 'crypto', 'performance', 'navigator', 'location',
            'globalThis', 'self', 'global'
        ]
    ))

    private JavaScriptGlobals() {
    }

    static Map<String, Class> constructors() { CONSTRUCTORS }
    static List<String> callableConstructorNames() { CALLABLE_CONSTRUCTORS }
    static Set<String> globalNames() { GLOBAL_NAMES }

    static boolean isConstructorName(String name) {
        CONSTRUCTORS.containsKey(name)
    }

    static boolean isGlobalName(String name) {
        GLOBAL_NAMES.contains(name)
    }

    static Class constructorClass(String name) {
        Class constructor = CONSTRUCTORS[name]
        if (constructor == null) {
            throw new JavaScriptReferenceError("Unknown global constructor: ${name}")
        }
        constructor
    }

    static Binding install(Binding binding) {
        if (binding == null) {
            throw new IllegalArgumentException('Global binding must not be null')
        }
        CONSTRUCTORS.each { String name, Class constructor -> binding.setVariable(name, constructor) }
        binding.setVariable('undefined', null)
        binding.setVariable('NaN', Double.NaN)
        binding.setVariable('Infinity', Double.POSITIVE_INFINITY)
        binding.setVariable('typeof', { Object value -> JavaScriptTypeof.typeOf(value) })
        binding.setVariable('isNaN', { Object value -> JavaScriptNumber.isNaN(JavaScriptNumber.coerce(value)) })
        binding.setVariable('isFinite', { Object value -> JavaScriptNumber.isFinite(JavaScriptNumber.coerce(value)) })
        binding.setVariable('parseFloat', { Object value -> JavaScriptNumber.parseFloat(value) })
        binding.setVariable('parseInt', { Object value, Object radix = null -> JavaScriptNumber.parseInt(value, radix) })
        binding.setVariable('btoa', { Object value -> JavaScriptWebUtilities.btoa(value) })
        binding.setVariable('atob', { Object value -> JavaScriptWebUtilities.atob(value) })
        binding.setVariable('structuredClone', { Object value, Object options = null -> JavaScriptWebUtilities.structuredClone(value, options) })
        binding.setVariable('fetch', { Object input, Object init = [:] -> JavaScriptFetch.fetch(input, init) })
        binding.setVariable('setTimeout', { Object... values -> JavaScriptTimers.setTimeout(values[0], values.length > 1 ? values[1] : 0, values.length > 2 ? values[2..-1] as Object[] : new Object[0]) })
        binding.setVariable('clearTimeout', { Object handle -> JavaScriptTimers.clearTimeout(handle) })
        binding.setVariable('setInterval', { Object... values -> JavaScriptTimers.setInterval(values[0], values.length > 1 ? values[1] : 0, values.length > 2 ? values[2..-1] as Object[] : new Object[0]) })
        binding.setVariable('clearInterval', { Object handle -> JavaScriptTimers.clearInterval(handle) })
        binding.setVariable('queueMicrotask', { Object callback -> JavaScriptTimers.queueMicrotask(callback) })
        binding.setVariable('console', JavaScriptConsole.INSTANCE)
        binding.setVariable('crypto', JavaScriptCrypto)
        binding.setVariable('performance', JavaScriptPerformance.INSTANCE)
        binding.setVariable('navigator', JavaScriptNavigator.INSTANCE)
        binding.setVariable('location', new JavaScriptLocation())
        binding.setVariable('globalThis', binding)
        binding.setVariable('self', binding)
        binding.setVariable('global', binding)
        binding
    }

    static Script install(Script script) {
        if (script == null) {
            throw new IllegalArgumentException('Global script must not be null')
        }
        install(script.binding)
        script
    }
}
