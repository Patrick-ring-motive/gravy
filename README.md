# Gravy

Groovy library that exposes JavaScript-familiar methods and global-object facades over JVM and Groovy types.

## Use

Call `JavaScriptStringExtensions.install()` once during application startup:

```groovy
import com.example.JavaScriptStringExtensions

JavaScriptStringExtensions.install()
assert 'groovy'.includes('roo')
assert 'x'.padStart(3, '0') == '00x'
```

`install()` adds MDN's static methods `String.fromCharCode`, `String.fromCodePoint`, and `String.raw`; current instance methods; and deprecated HTML wrapper methods. Instance extensions are registered in one target loop for `CharSequence`, `String`, `GString`, and Groovy's runtime `GStringImpl`. `iterator()` is provided as Groovy equivalent of JavaScript `String.prototype[Symbol.iterator]()` and iterates Unicode code points.

JavaScript `undefined` maps to `null` when returned by a method. Groovy has no `undefined` value, so omitted arguments use JavaScript defaults. Java `Pattern` instances enable regular-expression behavior for `replace`, `replaceAll`, and `split`; string search values remain literal for those methods.

Groovy's list-spread syntax normally accepts only lists and JVM arrays. Gravy's global AST transformation lowers list spreads through `Array.from`, so strings are iterable by Unicode code point:

```groovy
assert [*'A😄'].toList() == ['A', '😄']
assert [0, *'go', 3].toList() == [0, 'g', 'o', 3]
```

Use Groovy `*` syntax; JavaScript `[...text]` is not valid Groovy grammar.

## JavaScript Array extensions

Call `JavaScriptArrayExtensions.install()` once during application startup:

```groovy
import com.example.JavaScriptArrayExtensions

JavaScriptArrayExtensions.install()

def values = [3, 1, 2]
assert values.map { it * 2 } == [6, 2, 4]
assert values.toSorted() == [1, 2, 3]
assert values.join(',') == '3,1,2'
```

Instance methods are registered in one target loop for `Object[]`, every Java primitive array type, `List`, and `ArrayList`. It includes current MDN Array methods: access/search, callback methods, iterators, reducers, mutators, flattening, copying, sorting, and locale conversion. Java's native `length` remains available on arrays; lists gain a `length` property. Existing Java `iterator()` supplies JavaScript's `Symbol.iterator` equivalent.

`JavaScriptArray` supplies global `Array` semantics, including `Array.from`, `Array.of`, `Array.isArray`, and `Array.fromAsync`. Its static methods also remain available on `Object[]` after installation:

```groovy
assert Array.from('A😄') == ['A', '😄'] as Object[]
assert Object[].of(1, 2) == [1, 2] as Object[]
assert Object[].isArray([1, 2] as int[])
assert Object[].fromAsync([1, 2]).join() == [1, 2] as Object[]
```

Array-returning instance methods return `ArrayList`, avoiding Java primitive-array and heterogeneous-result constraints. `pop`, `push`, `shift`, `splice`, and `unshift` mutate lists. Fixed Java arrays reject any call that changes length with `UnsupportedOperationException`; equal-length `splice`, `copyWithin`, `fill`, `reverse`, and `sort` work in place.

Java dispatch cannot replace inherited `Object.toString()` on arrays and lists. `toJsString()` provides `Array.prototype.toString()` semantics; `join(',')` is equivalent. Java's declared `List.push(Object)` can take precedence on non-`ArrayList` implementations, so use `ArrayList` for JavaScript `push` semantics.

`Test262DerivedArrayExtensionsTest` independently adapts selected TC39 Test262 Array requirements. JavaScript holes, Proxy invariants for native JVM operations, Symbols, `ArraySpeciesCreate`, and async iterables have no direct Groovy equivalent.

## JavaScript BigInt support

Installed bindings expose `BigInt(value)`, `BigInt.asIntN(bits, value)`, and `BigInt.asUintN(bits, value)`. Values use an immutable `JavaScriptBigInt` wrapper backed by `BigInteger`, so `typeof` returns `"bigint"` without reclassifying existing arbitrary-precision JVM `Number` values.

```groovy
assert BigInt('9007199254740993') + BigInt(2) == BigInt('9007199254740995')
assert BigInt.asUintN(8, BigInt(-1)).toString() == '255'
assert BigInt('255').toString(16) == 'ff'
```

`BigInt()` accepts booleans, integral numbers, and decimal, hexadecimal, binary, or octal strings. Fractional and non-finite numbers raise `RangeError`; invalid strings raise `SyntaxError`; `null`, `undefined`, and Symbols raise `TypeError`. `new BigInt(...)` also raises `TypeError`. Groovy has no `1n` literal grammar, so create values with `BigInt(...)`. Arithmetic, bitwise operations, and shifts require two BigInt values, except string addition; `JSON.stringify` rejects BigInt unless a replacer converts it. `BigInt64Array`, `BigUint64Array`, and `DataView` BigInt accessors consume and return `JavaScriptBigInt`; legacy `BigInteger` inputs remain accepted for typed arrays.

## JavaScript Intl support

Installed bindings expose `Intl`, `getCanonicalLocales`, and `supportedValuesOf`, plus callable namespace constructors: `Collator`, `DateTimeFormat`, `DisplayNames`, `DurationFormat`, `ListFormat`, `Locale`, `NumberFormat`, `PluralRules`, `RelativeTimeFormat`, and `Segmenter`.

```groovy
assert Intl.NumberFormat('en-US').format(1234) == '1,234'
assert new Intl.ListFormat('en-US').format(['red', 'blue']) == 'red and blue'
assert new Intl.DateTimeFormat('de-DE', [timeZone: 'UTC']).resolvedOptions().timeZone == 'UTC'
assert Intl.getCanonicalLocales(['EN-us', 'en-US']) == ['en-US']
```

`Intl` itself cannot be called or constructed. The constructor transform supports `new Intl.Name(...)`; each child also works as a callable constructor property. Formatting uses JDK locale and time-zone data. Exact wording, locale negotiation, `formatToParts` tokenization, likely-subtag maximize/minimize, and complex CLDR plural and calendar behavior can differ from browser ICU implementations.

## Native static JavaScript APIs

Extension-module static methods also cover native JVM classes without an installer:

```groovy
assert List.from('gravy').reverse().join('') == 'yboorg'
assert Number.isInteger(4)
assert Number.parseInt('0x10') == 16
assert Map.groupBy([1, 2, 3]) { it % 2 }.get(1) == [1, 3]
assert Date.now() > 0L
assert Pattern.escape('a+b') == 'a\\+b'
```

This preserves a declared Java static method when one conflicts. For example, `Integer.parseInt` retains its Java signature; use `Number.parseInt` for JavaScript coercion semantics. Native static targets are `List`/its implementations for Array methods, `Number`/numeric wrappers for Number predicates, `Map` implementations for `groupBy`, `Date` for `now`, and `Pattern` for RegExp `escape`.

## Native Map and Set extensions

The Extension Module applies JavaScript-named methods to Groovy map literals and standard `java.util.Map` / `Set` implementations without an install call:

```groovy
def map = [a: 1]
assert map.set('b', 2).has('b')
assert map.getSize() == 2
assert map.entries().collect() == [['a', 1], ['b', 2]]

Set values = new LinkedHashSet(['x'])
assert values.has('x')
assert values.delete('x')
```

`set`, `has`, `delete`, `entries`, `keys`, `values`, `getSize`, and Closure-aware `forEach` are available on native collections. Java's declared `Set.add` retains its boolean return value; use `JavaScriptSet` when JavaScript-style chained `add` is required.

## JavaScript Object extensions

Call `JavaScriptObjectExtensions.install()` once during application startup:

```groovy
import com.example.JavaScriptObjectExtensions

JavaScriptObjectExtensions.install()

def object = Object.fromEntries([['language', 'Groovy']])
assert Object.entries(object) == [['language', 'Groovy']]
assert Object.groupBy([1, 2, 3]) { it % 2 }.get(0) == [2]
```

Static extensions are attached to Java `Object`: `assign`, `create`, `defineProperties`, `defineProperty`, `entries`, `freeze`, `fromEntries`, property descriptor and prototype helpers, `groupBy`, `hasOwn`, `is`, extensibility helpers, `keys`, `seal`, and `values`. `Map` is the closest plain-object equivalent. `Object.create()` and `Object.groupBy()` return `JavaScriptObject`, an insertion-ordered map with prototype-aware missing-property lookup.

`constructor`, `prototype`, `hasOwnProperty`, `isPrototypeOf`, `propertyIsEnumerable`, deprecated getter/setter helpers, `get__proto__()`, `set__proto__()`, `toLocaleString`, and `valueOf` are added through `Object`, `Class`, `List`, and `ArrayList` metaClasses. `constructor` returns an object's Java runtime `Class`, the closest JVM equivalent to a JavaScript constructor; `prototype` returns its runtime class's Groovy `MetaClass`. `JavaScriptObject` also supports `__proto__` property access. For ordinary Groovy objects, especially closures, use `Object.getPrototypeOf()` and `Object.setPrototypeOf()`: Groovy's property dispatch can bypass inherited metaClass accessors. A class's native static method takes precedence when it has the same name as an extension, for example `String.valueOf()`. Use `toJsObjectString()` for `Object.prototype.toString()` behavior: JVM dispatch cannot safely replace `Object.toString()`.

Java has no native Symbol keys, JavaScript property descriptors, or mutable object prototypes. `JavaScriptSymbol` supplies identity-based symbol keys for `JavaScriptObject` and `Map`; descriptor, freezing, sealing, extensibility, and prototype metadata are enforced through this library's `Object` operations. Direct `Map.put`, direct property assignment, and reflection bypass that metadata.

## JavaScript Symbol support

Create unique symbols with `JavaScriptSymbol.create()`, or retrieve a process-wide registry symbol with `JavaScriptSymbol.forKey()` (also available as `JavaScriptSymbol.'for'()`). `JavaScriptSymbol.keyFor()` returns a registry key or `null`. Well-known symbols include `iterator`, `asyncIterator`, `match`, `replace`, `search`, `species`, `toPrimitive`, `toStringTag`, `dispose`, and related current symbols.

```groovy
import com.example.JavaScriptSymbol

def token = JavaScriptSymbol.create('token')
def object = Object.fromEntries([[token, 'value'], ['plain', 1]])

assert object.get(token) == 'value'
assert Object.getOwnPropertySymbols(object) == [token]
assert Object.keys(object) == ['plain']
```

`Object.assign`, `Object.fromEntries`, `Object.groupBy`, descriptors, freezing, and sealing preserve supported symbol keys. `Object.keys`, `Object.values`, and `Object.entries` exclude them, matching JavaScript. Symbol-driven JVM protocol dispatch for iterators, regular expressions, coercion, species, and disposal is outside this adapter.

`Test262DerivedObjectExtensionsTest` independently adapts selected TC39 Test262 Object requirements. `CoreJsDerivedSymbolTest` independently adapts current core-js Symbol behavior. `Proxy` provides best-effort traps for Groovy property/index access and `Reflect` calls; JVM bytecode, Java-native operations, Proxy invariants, and strict-mode assignment behavior remain outside this adapter.

## Reflect and Proxy

`Reflect` implements `apply`, `construct`, descriptor, property, prototype, key, and extensibility operations for Groovy maps, `JavaScriptObject`, and ordinary Groovy objects. `Proxy` forwards `get`, `set`, `has`, `deleteProperty`, `defineProperty`, descriptor, prototype, key, extensibility, `apply`, and `construct` traps, plus `Proxy.revocable`.

```groovy
import com.example.JavaScriptProxy as Proxy
import com.example.JavaScriptReflect as Reflect

def proxy = new Proxy([value: 1], [
    get: { target, property, receiver -> property == 'double' ? target.value * 2 : Reflect.get(target, property, receiver) }
])
assert proxy.double == 2
```

This is a facade around JVM objects, not an ECMAScript runtime: direct Java calls, bytecode field access, and Proxy invariants cannot be intercepted.

## JavaScript globals

`JavaScriptGlobals.install(binding)` adds every implemented JavaScript constructor and namespace, including `Array`, `Promise`, `Symbol`, `Number`, `BigInt`, `Intl`, `Map`, and `Set`, primitive globals (`undefined`, `NaN`, `Infinity`, `typeof`, `isNaN`, `isFinite`, `parseFloat`, `parseInt`), plus utility globals to one Groovy `Binding`. It exposes `globalThis`, browser-compatible `self`, and Node-compatible `global` as that `Binding`, so global properties are shared with script variables. In a script, pass `this`; each shell, application, or Pipeline binding must be installed separately.

```groovy
import com.example.JavaScriptPrelude

JavaScriptPrelude.install(this)
globalThis.answer = 42
assert answer == 42
assert typeof('gravy') == 'string'
assert typeof(undefined) == 'undefined'
assert Array.from('gravy').join('') == 'gravy'
assert Intl.NumberFormat('en-US').format(1234) == '1,234'
assert Promise.resolve(2).then { it * 2 }.await() == 4
assert Symbol.'for'('gravy').is(Symbol.forKey('gravy'))
```

Global methods and static APIs resolve through the installed `Binding`. An early Gravy AST transform rewrites `new Name(...)` and `new Intl.Name(...)` for published JS constructors before Groovy resolves Java classes, so `new Request(url)`, `new Number(3)`, `new Intl.Collator()`, and similar forms need no constructor imports. `main.groovy` demonstrates this constructor-import-free prelude.

Alternatively, use `JavaScriptBaseScript` with `@BaseScript`. It installs the same prelude into its assigned `Binding`, exposes every published global as an inherited property, and exposes utility globals such as `typeof`, `fetch`, `btoa`, `setTimeout`, and `queueMicrotask` as inherited methods:

```groovy
import com.example.JavaScriptBaseScript
import groovy.transform.BaseScript

@BaseScript JavaScriptBaseScript gravy

assert Array.from('gravy').join('') == 'gravy'
assert typeof(BigInt(1)) == 'bigint'
assert Intl.ListFormat('en').format(['a', 'b']) == 'a and b'
assert fetch('data:text/plain,ready').await().text().await() == 'ready'
```

`globalThis`, `self`, and `global` still resolve to script `Binding`, so assigning `globalThis.answer = 42` creates the normal script variable `answer`.

## JavaScript Promise support

`JavaScriptPromise` is a `CompletableFuture`-backed adapter behind global `Promise`, with `then`, `catch`, and `finally` chaining. It supports `resolve`, `reject`, `all`, `allSettled`, `any`, `race`, `tryCall` (and `Promise.'try'`), and `withResolvers`.

```groovy
def result = Promise.all([Promise.resolve(1), 2]).then { values -> values.sum() }
assert result.await() == 3

def deferred = Promise.withResolvers()
deferred.resolve.call('ready')
assert deferred.promise.await() == 'ready'
```

Handlers run asynchronously on the common ForkJoin pool. `await()` is a blocking Groovy convenience. `Promise` and `CompletionStage` results are adopted; custom thenables, subclass/species behavior, browser job queues, and cancellation are outside this adapter.

## Typed buffers and weak references

`JavaScriptArrayBuffer`, `JavaScriptDataView`, and concrete typed-array facades (`JavaScriptInt8Array` through `JavaScriptBigUint64Array`) provide fixed-length `ByteBuffer`-backed views, indexed access, `set`, `subarray`, copying methods, callbacks, and `DataView` byte access. Typed arrays use little-endian storage; `DataView` keeps JavaScript's big-endian default and accepts a `littleEndian` argument. Resizable, transferable, and shared buffers are unsupported.

`JavaScriptWeakRef` wraps `WeakReference`; `JavaScriptFinalizationRegistry` uses `Cleaner` for best-effort cleanup and identity-based unregister tokens. Cleanup scheduling is GC-dependent and nondeterministic, so tests validate registration and unregistering rather than forced collection.

`JavaScriptGlobals.install(binding)` exposes these facades as `ArrayBuffer`, `DataView`, `Int8Array` through `BigUint64Array`, `WeakRef`, and `FinalizationRegistry` alongside existing globals.

## Web-platform facades

Installed bindings expose browser and modern Node-compatible globals without imports: `fetch`, `Request`, `Response`, `Headers`, `WebSocket`, `URL`, `URLSearchParams`, `TextEncoder`, `TextDecoder`, `atob`, `btoa`, `Blob`, `File`, `FormData`, and `structuredClone`. `fetch` resolves `data:` URLs locally and delegates HTTP(S) requests to `java.net.http.HttpClient`; `WebSocket` delegates `ws:` and `wss:` connections to the JDK HTTP client.

`location` implements [MDN's `Location` API](https://developer.mozilla.org/en-US/docs/Web/API/Location): read-only `ancestorOrigins` and `origin`; read/write `href`, `protocol`, `host`, `hostname`, `port`, `pathname`, `search`, and `hash`; plus `assign`, `replace`, `reload`, and stringification. It is URI-backed and local-only: navigation history and document reload side effects are unavailable, while `ancestorOrigins` is always empty.

`Event`, `EventTarget`, `CustomEvent`, `AbortController`, and `AbortSignal` provide listener, cancellation, timeout, and combined-signal semantics. `setTimeout`, `clearTimeout`, `setInterval`, `clearInterval`, and `queueMicrotask` run on daemon JVM scheduler threads.

`ReadableStream`, `WritableStream`, `TransformStream`, `ByteLengthQueuingStrategy`, `CountQueuingStrategy`, `CompressionStream`, and `DecompressionStream` cover iterable reads, sink writers, transforms, and buffered `gzip`, `deflate`, and `deflate-raw` codecs. `MessageChannel`, `MessagePort`, `MessageEvent`, and same-process `BroadcastChannel` provide cloned asynchronous messages.

`crypto` supplies `getRandomValues`, `randomUUID`, and `crypto.subtle.digest` for SHA digests. `performance` supplies `now`, marks, and measures; `console` provides log-level methods. `globalThis` aliases binding, while `navigator` exposes `hardwareConcurrency`, `userAgent`, `language`, and a process-local `navigator.locks` manager.

This is a JVM approximation: no browser navigation, CORS, cookies, service workers, transfer lists, multipart `FormData` request encoding, byte/BYOB stream controllers, streaming compression output, or non-digest SubtleCrypto operations.

## Jenkins shared-library bundle

Create a Jenkins Shared Library directory and ZIP archive:

```bash
./scripts/bundle-jenkins-library.sh
```

The script writes `build/jenkins-library/` with Jenkins-standard `src/` and `vars/` directories, plus `build/jenkins-library.zip`. Publish the directory contents in a Git repository, configure it as trusted Shared Library `gravy`, then load a tagged version dynamically:

```groovy
def libraryRoot = library('gravy@v1.0.0')
gravy.install()

assert 'groovy'.includes('roo')
assert Promise.resolve(2).then { it * 2 }.await() == 4
assert Symbol.'for'('gravy').is(Symbol.forKey('gravy'))
```

`gravy.install()` binds `Promise` and `Symbol` into the Pipeline binding. A dynamic `library` step occurs after Jenkinsfile compilation, so do not use `import` for bundle classes. Access a class through `library`'s returned namespace, for example `libraryRoot.com.example.JavaScriptStringExtensions.install()`. Gravy changes Groovy metaClasses, so use only a reviewed trusted library; untrusted Pipeline libraries can be blocked by Jenkins Script Security.

## Run

```bash
./run.sh
```

For JavaScript-style Groovy code, add code below prelude in root `main.groovy`, then run:

```bash
gradle runMain
```

The global AST transformation lowers `await value` to a blocking wait for `JavaScriptPromise` values and returns every other value unchanged:

```groovy
let response = await fetch(request)
let value = await 42 // 42
```

`await` does not alter network behavior: `fetch` still requires a reachable URL with a valid TLS certificate. `fetch` merges JVM trust with platform certificates while retaining hostname verification: macOS exports `SystemRootCertificates.keychain` and `System.keychain`, Windows reads `Windows-ROOT`, and Linux reads `SSL_CERT_FILE` when set plus common distribution CA bundles. If a platform source is unavailable, JVM trust remains active. `main.groovy` installs `JavaScriptPrelude`; its constructor transform supports bare `new` globals, while lower-case utilities include `btoa`, `atob`, `structuredClone`, `fetch`, `crypto`, and `location`.

## Test

```bash
gradle test
```

Test262 and core-js validation lives in versioned Groovy-derived vectors under `src/test/groovy/com/example/`, so validation is reproducible without downloading or executing external JavaScript suites. Test262-derived vectors cover String, Array, Object, Function, Number, BigInt, Intl, Map/Set, Errors, JSON/Math, RegExp, Date, TypedArrays, Reflect, Proxy traps/revocation, WeakRef argument behavior, and URL/text API behavior. core-js-derived vectors cover all applicable implemented surfaces including BigInt, TypedArrays, and Reflect; current core-js deliberately omits ECMA-402 Intl, so `CoreJsDerivedIntlTest` guards the independent namespace boundary. core-js also does not polyfill native `Proxy`, while GC-backed WeakRef/FinalizationRegistry and browser APIs have no compatible core-js polyfill vectors. `JavaScriptGlobalsTest` and `JavaScriptBaseScriptTest` verify Binding and inherited global exposure. The project uses Groovy with Gradle.
