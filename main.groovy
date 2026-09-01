import com.example.JavaScriptPrelude
import java.lang.Object as let
import java.lang.Object as function

// JavaScript-style prelude. Add application code below this line.
JavaScriptPrelude.install(this)

console.log(undefined);
console.log(globalThis.Map)
console.log(NaN)
console.log(self)
console.log('JavaScript prelude installed. Use console, Array, Promise, Symbol, Number, BigInt, Intl, Map, Set, WeakMap, WeakSet, WeakRef, FinalizationRegistry, URL, Headers, FormData, Blob, File, ReadableStream, Request, Response, TextEncoder, TextDecoder, Crypto, Reflect, Proxy, JSON, Math and other JavaScript constructors without imports.')
console.log(Intl.NumberFormat('en-US').format(1234));
console.log(atob);

let test = "gravy";

console.log(test);

console.debug([*new Set([*test])].reverse().join(''));

// External HTTPS uses platform trust in addition to JVM certificates.
let req = new Request('https://example.com', [method: 'GET', headers: new Headers([Accept: 'text/plain'])]);

console.log(req);

let res = await fetch(req);

console.log(res);

let text = await res.text();

console.log(text);


var stupidCompact = { messages ->
    messages = messages.toReversed()
    var roles = [:]
    for (message in messages) {
        var role = String(message.role)
        if (role == 'system') continue
        roles[role] ?= new Set();
        var shreds = [*new Set(message.content.split(' '))].filter { ! roles[role].has(it) }
    for(shred in shreds) { 
        roles[role].add(shred)
    }
    message.content = shreds.join(' ').trim()
    }
    return messages.reverse()
}

console.log(stupidCompact([
    [role: 'system', content: 'System message'],
    [role: 'user', content: 'Hello world! Hello everyone.'],
    [role: 'assistant', content: 'Hello user! How can I help you?'],
    [role: 'user', content: 'I need help with my code.'],
    [role: 'assistant', content: 'Sure! What seems to be the problem?'],
    [role: 'user', content: 'I am getting an error when I run my code.']
]));

console.log([
    Array.isArray(Array()),
    Array.isArray(new Array()),
    typeof(String(true)),
    typeof(new String(true)),
    typeof(Boolean(1)),
    typeof(new Boolean(1)),
    Number("3"),
    typeof(new Number("3")),
    Boolean(1),
    new Boolean(1),
    RegExp('^gravy$'),
    new RegExp('^gravy$'),
    new Date(0),
    new URL('https://example.test/'),
    new URLSearchParams(),
    new Headers(),
    new FormData(),
    new Blob(),
    Date.now(),
    Date()
]);