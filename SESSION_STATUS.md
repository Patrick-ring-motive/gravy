# Overnight session status

## Complete

- Foundation gate: Array, String, Object, Symbol, Promise green; `gradle test` passed before new work. Array, String, and Object installers now use synchronized sentinels so App smoke setup cannot re-register conflicting metaClass methods.
- Function: `JavaScriptFunction` facade supports `call`, `apply`, `bind`, reflection, constructors, and closure delegate rebinding. Native `LambdaMetafactory` `Function` instances receive extension-module `call`.
- String spread: Groovy hard-codes list-spread operands to List/array before metaClass dispatch. A narrowly scoped global AST transform lowers list literals containing `*` spreads through `Array.from`, enabling `[*'text']` and mixed list spreads without changing generator strategy.
- Top-level await: global AST transform blocks for `JavaScriptPromise` values and passes all other values through; it does not stub or bypass real `fetch` networking or TLS validation.
- Number: `JavaScriptNumber`, Number wrapper extensions, parser/predicate/formatting coverage. JVM numeric backing types remain intentional best-effort divergence from JS doubles.
- BigInt: global `BigInt(value)`, `asIntN`, and `asUintN` with an immutable `BigInteger`-backed wrapper; `typeof`, JSON rejection/replacer behavior, collections, array equality, DataView, and BigInt typed arrays preserve distinct BigInt semantics. Groovy has no `1n` literal, so scripts call `BigInt(...)`.
- Intl: global `Intl` namespace with callable `Collator`, `DateTimeFormat`, `DisplayNames`, `DurationFormat`, `ListFormat`, `Locale`, `NumberFormat`, `PluralRules`, `RelativeTimeFormat`, and `Segmenter`, plus `getCanonicalLocales` and `supportedValuesOf`. Binding injection and `JavaScriptBaseScript` expose `Intl`; AST rewriting supports `new Intl.Name(...)`. Output uses JDK locale data, so exact browser ICU text, full CLDR locale negotiation, rich `formatToParts`, and likely-subtag behavior remain best-effort.
- Map/Set: insertion-ordered `JavaScriptMap` and `JavaScriptSet`, native collection extensions, SameValueZero signed-zero handling, live `forEach` mutation traversal, and Unicode code-point iteration for String constructor sources.
- Errors: JS-named error hierarchy and immutable `AggregateError.errors`.
- Generators: one-shot `Stream.generate` / `Stream.iterate` approximation; no JVM `yield` syntax.
- JSON/Math: `JsonSlurper`/`JsonOutput` facade plus standard Math operations.
- RegExp: `Pattern`-backed constructor, flags, `exec`, `test`, `lastIndex`, and String bridge. Java named-group introspection, Unicode edge cases, and exact sticky/unicode advancement differ.
- Date: `Instant`-backed constructor, epoch conversion, ISO/JSON output, UTC construction, and basic accessors. Legacy parse and local-time quirks differ.
- WeakMap/WeakSet: `WeakHashMap` approximations. JVM equality-based weak keys differ from JS identity semantics.
- TypedArrays/ArrayBuffer/DataView: fixed-length `ByteBuffer`-backed `ArrayBuffer`, `DataView`, and all standard concrete typed-array facades. Typed arrays use little-endian storage; DataView defaults to big-endian. Resizable, transferable, and shared buffers remain unsupported.
- WeakRef/FinalizationRegistry: `WeakReference` and `Cleaner` best-effort facades with identity-based unregister tokens. Cleanup timing is GC-dependent and nondeterministic.
- Web-platform stretch: URI-backed `URL`, `URLSearchParams`, and local-only `Location`; `Headers`, `FormData`, `Blob`, `File`, `ReadableStream`, `Request`, `Response`; local `data:` and `HttpClient`-backed HTTP(S) `fetch`. `fetch` augments JVM TLS trust while retaining hostname verification: macOS system keychains, Windows `Windows-ROOT`, and Linux `SSL_CERT_FILE` plus common distribution CA bundles; text codecs, base64, `structuredClone`, random values/UUIDs, and SHA digest-only `crypto.subtle`.
- Reflect/Proxy: `Reflect` covers apply, construct, property descriptors, prototype, keys, extensibility, and mutation operations. `Proxy` wraps Groovy targets with best-effort get/set/has/delete/descriptor/prototype/key/extensibility/apply/construct traps plus `revocable`; JVM bytecode, Java-native operations, and ECMAScript Proxy invariants remain outside this adapter.
- Global binding: `JavaScriptPrelude.install(...)` installs Object/String/Array extensions plus constructors and utilities from `JavaScriptGlobals`, including `Array`, `String`, `Boolean`, `BigInt`, `Intl`, `globalThis`, `self`, and Node-compatible `global` (the installed `Binding`), `undefined`, `NaN`, `Infinity`, `typeof`, coercing numeric helpers, and a `console` instance with `log`, `info`, `debug`, `warn`, and `error`. `JavaScriptBaseScript` provides the equivalent `@BaseScript` path: every global resolves as an inherited property, and utility globals resolve as inherited methods while retaining the same per-script Binding. `typeof(undefined)` is lowered to preserve JS's distinct undefined result; other values use a global type helper. A conversion-phase AST transform rewrites bare `new Constructor(...)` and `new Intl.Constructor(...)` forms before Groovy resolves Java types, so root `main.groovy` needs no constructor aliases. Object retains Groovy's patched `java.lang.Object` static API.
- Native static APIs: Extension Module adds Array methods to `List`, Number predicates/parsers to `Number` and numeric wrappers, `Map.groupBy`, `Date.now`, and `Pattern.escape` without an install call. Declared Java static methods retain precedence.

## Validation

- Test262-derived vectors added under `src/test/groovy/com/example/` for Function, Number, BigInt, Intl, Map/Set, Errors, JSON, Math, RegExp, Date, TypedArrays, Reflect, Proxy traps/revocation, WeakRef argument behavior, and URL/text API behavior.
- core-js-derived vectors added there for applicable implemented surfaces, including BigInt, TypedArrays, and Reflect. Current core-js deliberately omits ECMA-402 Intl; `CoreJsDerivedIntlTest` guards that independent namespace boundary. core-js does not polyfill native Proxy and cannot polyfill GC-backed WeakRef/FinalizationRegistry or browser APIs.
- Raw upstream JavaScript is not fetched or executed by Gradle; validation stays versioned, local, and JVM-runnable.
- `AppSmokeTest` invokes App's full global-facade sample without printing during tests; `./run.sh` prints every supported global facade. `MainPreludeTest` executes root `main.groovy` with appended JavaScript-style code and locks its no-constructor-alias import header. `JavaScriptGlobalConstructorMatrixTest` verifies every published constructor binding and compiles `new Name(...)` plus `new Intl.Name(...)` through root main's constructor-import-free prelude. `JavaScriptBaseScriptTest` verifies the complete global registry through both Binding injection and inherited `@BaseScript` properties/methods. Native Groovy and java.util Map/Set receiver coverage includes HashMap, TreeMap, HashSet, TreeSet, and collection `forEach` callbacks.
- Latest result: `gradle test` passed, 277 tests. Prior `gradle runMain` validation completed its external HTTPS fetch through macOS system-keychain trust.

## Deferred / unsupported

- Proxy invariants, interception of JVM bytecode/Java-native operations, and exact `new.target` behavior: unsupported.
- Browser navigation, CORS, cookies, service workers, body-locking semantics, multipart `FormData` request encoding, writable/transform streams, and non-digest `SubtleCrypto` operations: unsupported JVM/browser gaps.
- Atomics and SharedArrayBuffer: intentionally out of scope.
- Safe navigation (`?.`) and Elvis (`?:`) are already supplied by Groovy; Elvis is truthy rather than strictly nullish.
