package com.example

import groovy.json.JsonOutput

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.Iterator
import java.util.LinkedHashMap
import java.util.List
import java.util.Locale
import java.util.Map
import java.util.concurrent.CompletableFuture

/** Case-insensitive, multi-value HTTP header collection. */
final class JavaScriptHeaders implements Iterable<List<String>> {
    private final LinkedHashMap<String, String> values = new LinkedHashMap<>()

    JavaScriptHeaders(Object init = null) {
        if (init != null) addInitial(init)
    }

    void append(Object name, Object value) {
        String key = normalizeName(name)
        String content = normalizeValue(value)
        values.put(key, values.containsKey(key) ? "${values[key]}, ${content}" : content)
    }

    void set(Object name, Object value) {
        values.put(normalizeName(name), normalizeValue(value))
    }

    String get(Object name) { values.get(normalizeName(name)) }
    boolean has(Object name) { values.containsKey(normalizeName(name)) }
    void delete(Object name) { values.remove(normalizeName(name)) }

    Iterator<List<String>> entries() {
        values.keySet().sort().collect { String name -> [name, values[name]] }.iterator()
    }

    Iterator<String> keys() { entries().collect { List<String> pair -> pair[0] }.iterator() }
    Iterator<String> values() { entries().collect { List<String> pair -> pair[1] }.iterator() }

    void forEach(Closure callback, Object thisArg = null) {
        entries().each { List<String> pair ->
            Closure rebound = callback.clone() as Closure
            rebound.delegate = thisArg
            rebound.resolveStrategy = Closure.DELEGATE_FIRST
            rebound.call(pair[1], pair[0], this)
        }
    }

    Map<String, String> toMap() { new LinkedHashMap<>(values) }
    @Override Iterator<List<String>> iterator() { entries() }

    private void addInitial(Object init) {
        if (init instanceof JavaScriptHeaders) {
            (init as JavaScriptHeaders).values.each { String name, String value -> values.put(name, value) }
            return
        }
        if (init instanceof Map) {
            (init as Map).each { Object name, Object value ->
                Object normalized = value instanceof Iterable && !(value instanceof CharSequence) ?
                    (value as Iterable).collect { Object item -> String.valueOf(item) }.join(', ') : value
                set(name, normalized)
            }
            return
        }
        JavaScriptCollectionSupport.valuesFor(init).each { Object entry ->
            List<Object> pair
            if (entry instanceof Map.Entry) {
                pair = [(entry as Map.Entry).key, (entry as Map.Entry).value]
            } else if (entry instanceof Iterable && !(entry instanceof CharSequence)) {
                pair = JavaScriptCollectionSupport.valuesFor(entry)
            } else if (entry != null && entry.class.array) {
                pair = JavaScriptCollectionSupport.valuesFor(entry)
            } else {
                throw new JavaScriptTypeError('Headers entries must contain exactly two values')
            }
            if (pair.size() != 2) throw new JavaScriptTypeError('Headers entries must contain exactly two values')
            append(pair[0], pair[1])
        }
    }

    private static String normalizeName(Object value) {
        String name = String.valueOf(value).trim().toLowerCase(Locale.ROOT)
        if (!(name ==~ /[!#$%&'*+.^_`|~0-9a-z-]+/)) {
            throw new JavaScriptTypeError("Invalid HTTP header name: ${value}")
        }
        name
    }

    private static String normalizeValue(Object value) {
        String content = String.valueOf(value).trim()
        if (!(content ==~ /[\t\x20-\x7E\x80-\xFF]*/)) {
            throw new JavaScriptTypeError("Invalid HTTP header value: ${value}")
        }
        content
    }
}

/** Immutable byte sequence facade modelled after Blob. */
class JavaScriptBlob {
    private final byte[] bytes
    final String type

    JavaScriptBlob(Object parts = [], Object options = [:]) {
        this(bytesFromParts(parts), contentType(options))
    }

    protected JavaScriptBlob(byte[] bytes, String type) {
        this.bytes = bytes.clone()
        this.type = type
    }

    int getSize() { bytes.length }
    JavaScriptPromise<JavaScriptArrayBuffer> arrayBuffer() {
        JavaScriptArrayBuffer result = new JavaScriptArrayBuffer(bytes.length)
        new JavaScriptUint8Array(result).set(bytes)
        JavaScriptPromise.resolve(result)
    }
    JavaScriptPromise<String> text() { JavaScriptPromise.resolve(new String(bytes, StandardCharsets.UTF_8)) }
    JavaScriptReadableStream stream() { new JavaScriptReadableStream([new JavaScriptUint8Array(bytes)]) }

    JavaScriptBlob slice(Object start = 0, Object end = null, Object type = '') {
        int from = JavaScriptWebPlatformSupport.relativeIndex(start, bytes.length, 0)
        int until = end == null ? bytes.length : JavaScriptWebPlatformSupport.relativeIndex(end, bytes.length, bytes.length)
        new JavaScriptBlob(Arrays.copyOfRange(bytes, from, until), contentType([type: type]))
    }

    byte[] bytesCopy() { bytes.clone() }

    private static byte[] bytesFromParts(Object parts) {
        List<Object> values
        if (parts == null) values = []
        else if (parts instanceof Iterable || parts.class.array) values = JavaScriptCollectionSupport.valuesFor(parts)
        else values = [parts]
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        values.each { Object part -> output.write(JavaScriptWebBytes.bytesFor(part)) }
        output.toByteArray()
    }

    private static String contentType(Object options) {
        Object candidate = options instanceof Map ? (options as Map).type : ''
        String type = candidate == null ? '' : String.valueOf(candidate).toLowerCase(Locale.ROOT)
        type ==~ /[\x20-\x7E]*/ ? type : ''
    }
}

/** Named Blob with browser-style metadata. */
final class JavaScriptFile extends JavaScriptBlob {
    final String name
    final long lastModified

    JavaScriptFile(Object parts, Object name, Object options = [:]) {
        super(parts, options)
        this.name = String.valueOf(name).replaceAll(/[\\/]/, ':')
        Object value = options instanceof Map ? (options as Map).lastModified : null
        this.lastModified = value == null ? System.currentTimeMillis() : JavaScriptNumber.coerce(value).longValue()
    }
}

/** Ordered multipart form entry list. Multipart encoding is delegated to HTTP clients. */
final class JavaScriptFormData implements Iterable<List<Object>> {
    private final List<List<Object>> values = []

    JavaScriptFormData() { }

    void append(Object name, Object value, Object filename = null) {
        values << [String.valueOf(name), normalizeValue(value), filename == null ? null : String.valueOf(filename)]
    }

    void set(Object name, Object value, Object filename = null) {
        String key = String.valueOf(name)
        int first = values.findIndexOf { List<Object> entry -> entry[0] == key }
        List<Object> replacement = [key, normalizeValue(value), filename == null ? null : String.valueOf(filename)]
        if (first < 0) {
            values << replacement
        } else {
            values[first] = replacement
            for (int index = values.size() - 1; index > first; index--) if (values[index][0] == key) values.remove(index)
        }
    }

    void delete(Object name) { values.removeAll { List<Object> entry -> entry[0] == String.valueOf(name) } }
    Object get(Object name) { List<Object> entry = values.find { List<Object> value -> value[0] == String.valueOf(name) }; entry == null ? null : entry[1] }
    List<Object> getAll(Object name) { values.findAll { List<Object> entry -> entry[0] == String.valueOf(name) }.collect { List<Object> entry -> entry[1] } }
    boolean has(Object name) { values.any { List<Object> entry -> entry[0] == String.valueOf(name) } }
    Iterator<List<Object>> entries() { values.collect { List<Object> entry -> [entry[0], entry[1]] }.iterator() }
    Iterator<String> keys() { values.collect { List<Object> entry -> entry[0] as String }.iterator() }
    Iterator<Object> values() { values.collect { List<Object> entry -> entry[1] }.iterator() }
    void forEach(Closure callback, Object thisArg = null) {
        values.collect { List<Object> entry -> [entry[0], entry[1]] }.each { List<Object> entry ->
            Closure rebound = callback.clone() as Closure
            rebound.delegate = thisArg
            rebound.resolveStrategy = Closure.DELEGATE_FIRST
            rebound.call(entry[1], entry[0], this)
        }
    }
    @Override Iterator<List<Object>> iterator() { entries() }

    private static Object normalizeValue(Object value) {
        value instanceof JavaScriptBlob ? value : String.valueOf(value)
    }
}

/** One-shot pull stream over an iterable, InputStream, or a sequence of chunks. */
final class JavaScriptReadableStream implements Iterable<Object> {
    private final Iterator<Object> source
    private final JavaScriptStreamQueue queue
    private boolean locked
    private boolean cancelled

    JavaScriptReadableStream(Object source = []) {
        if (source instanceof JavaScriptStreamQueue) {
            this.queue = source as JavaScriptStreamQueue
            this.source = null
        } else if (source instanceof InputStream) {
            InputStream input = source as InputStream
            this.source = [new JavaScriptUint8Array(input.bytes)].iterator()
            this.queue = null
        } else if (source instanceof Iterator) {
            this.source = source as Iterator<Object>
            this.queue = null
        } else if (source instanceof Iterable) {
            this.source = (source as Iterable).iterator()
            this.queue = null
        } else {
            throw new JavaScriptTypeError('ReadableStream source must be iterable, InputStream, or an internal queue')
        }
    }

    static JavaScriptReadableStream fromQueue(JavaScriptStreamQueue queue) {
        if (queue == null) throw new JavaScriptTypeError('ReadableStream queue must not be null')
        new JavaScriptReadableStream(queue)
    }

    boolean getLocked() { locked }
    JavaScriptReadableStreamDefaultReader getReader() {
        if (locked) throw new JavaScriptTypeError('ReadableStream is already locked')
        locked = true
        new JavaScriptReadableStreamDefaultReader(this)
    }
    JavaScriptPromise<Void> cancel(Object reason = null) {
        cancelled = true
        if (queue != null) queue.close()
        JavaScriptPromise.resolve(null)
    }

    @Override Iterator<Object> iterator() { source ?: java.util.Collections.emptyIterator() }

    private JavaScriptPromise<Map<String, Object>> read() {
        if (cancelled) return JavaScriptPromise.resolve([value: null, done: true])
        queue == null ? JavaScriptPromise.resolve(readNext()) : queue.read()
    }

    private Map<String, Object> readNext() {
        if (!source.hasNext()) return [value: null, done: true]
        [value: source.next(), done: false]
    }

    private void release() { locked = false }

    static final class JavaScriptReadableStreamDefaultReader {
        private JavaScriptReadableStream stream
        JavaScriptReadableStreamDefaultReader(JavaScriptReadableStream stream) { this.stream = stream }
        JavaScriptPromise<Map<String, Object>> read() {
            stream == null ? JavaScriptPromise.reject(new JavaScriptTypeError('Reader lock has been released')) : stream.read()
        }
        void releaseLock() { if (stream != null) stream.release(); stream = null }
        JavaScriptPromise<Void> cancel(Object reason = null) { stream == null ? JavaScriptPromise.resolve(null) : stream.cancel(reason) }
    }
}

/** Fetch-compatible request facade. */
final class JavaScriptRequest {
    final String url
    final String method
    final JavaScriptHeaders headers
    private final byte[] bodyBytes

    JavaScriptRequest(Object input, Object init = [:]) {
        Map settings = init instanceof Map ? init as Map : [:]
        JavaScriptRequest source = input instanceof JavaScriptRequest ? input as JavaScriptRequest : null
        this.url = source == null ? (input instanceof JavaScriptURL ? (input as JavaScriptURL).href : String.valueOf(input)) : source.url
        try {
            if (URI.create(url).rawUserInfo != null) {
                throw new JavaScriptTypeError('Request URL must not include credentials')
            }
        } catch (JavaScriptTypeError error) {
            throw error
        } catch (IllegalArgumentException error) {
            throw new JavaScriptTypeError("Invalid request URL: ${url}", error)
        }
        this.method = String.valueOf(settings.containsKey('method') ? settings.method : source?.method ?: 'GET').toUpperCase(Locale.ROOT)
        this.headers = new JavaScriptHeaders(settings.containsKey('headers') ? settings.headers : source?.headers)
        Object body = settings.containsKey('body') ? settings.body : source?.bodyBytes
        if ((method == 'GET' || method == 'HEAD') && body != null) throw new JavaScriptTypeError("${method} requests cannot have a body")
        if (body != null && !headers.has('content-type')) {
            String contentType = JavaScriptWebBytes.contentTypeFor(body)
            if (contentType) headers.set('content-type', contentType)
        }
        this.bodyBytes = body == null ? null : JavaScriptWebBytes.bytesFor(body)
    }

    JavaScriptReadableStream getBody() { bodyBytes == null ? null : new JavaScriptReadableStream([new JavaScriptUint8Array(bodyBytes)]) }
    JavaScriptPromise<String> text() { JavaScriptPromise.resolve(bodyBytes == null ? '' : new JavaScriptTextDecoder().decode(bodyBytes)) }
    JavaScriptPromise<Object> json() { text().then { String body -> JavaScriptJSON.parse(body) } }
    JavaScriptPromise<JavaScriptArrayBuffer> arrayBuffer() { bytesToBuffer(bodyBytes ?: new byte[0]) }
    JavaScriptRequest clone() { new JavaScriptRequest(this, [body: bodyBytes]) }

    byte[] bodyCopy() { bodyBytes == null ? null : bodyBytes.clone() }

    private static JavaScriptPromise<JavaScriptArrayBuffer> bytesToBuffer(byte[] bytes) {
        JavaScriptArrayBuffer buffer = new JavaScriptArrayBuffer(bytes.length)
        new JavaScriptUint8Array(buffer).set(bytes)
        JavaScriptPromise.resolve(buffer)
    }
}

/** Fetch-compatible response facade. */
final class JavaScriptResponse {
    final int status
    final String statusText
    final JavaScriptHeaders headers
    final String url
    private final byte[] bodyBytes

    JavaScriptResponse(Object body = null, Object init = [:]) {
        Map settings = init instanceof Map ? init as Map : [:]
        int resolvedStatus = settings.containsKey('status') ? JavaScriptNumber.coerce(settings.status).intValue() : 200
        if (resolvedStatus != 0 && (resolvedStatus < 200 || resolvedStatus > 599)) throw new JavaScriptRangeError('Response status must be 0 or 200 through 599')
        this.status = resolvedStatus
        this.statusText = settings.containsKey('statusText') ? String.valueOf(settings.statusText) : ''
        this.headers = new JavaScriptHeaders(settings.headers)
        if (body != null && !headers.has('content-type')) {
            String contentType = JavaScriptWebBytes.contentTypeFor(body)
            if (contentType) headers.set('content-type', contentType)
        }
        this.url = settings.containsKey('url') ? String.valueOf(settings.url) : ''
        this.bodyBytes = body == null ? null : JavaScriptWebBytes.bytesFor(body)
    }

    boolean getOk() { status >= 200 && status < 300 }
    String getType() { status == 0 ? 'error' : 'default' }
    JavaScriptReadableStream getBody() { bodyBytes == null ? null : new JavaScriptReadableStream([new JavaScriptUint8Array(bodyBytes)]) }
    JavaScriptPromise<String> text() { JavaScriptPromise.resolve(bodyBytes == null ? '' : new JavaScriptTextDecoder().decode(bodyBytes)) }
    JavaScriptPromise<Object> json() { text().then { String body -> JavaScriptJSON.parse(body) } }
    JavaScriptPromise<JavaScriptBlob> blob() { JavaScriptPromise.resolve(new JavaScriptBlob([bodyBytes ?: new byte[0]], [type: headers.get('content-type') ?: ''])) }
    JavaScriptPromise<JavaScriptArrayBuffer> arrayBuffer() {
        JavaScriptArrayBuffer buffer = new JavaScriptArrayBuffer((bodyBytes ?: new byte[0]).length)
        new JavaScriptUint8Array(buffer).set(bodyBytes ?: new byte[0])
        JavaScriptPromise.resolve(buffer)
    }
    JavaScriptResponse clone() { new JavaScriptResponse(bodyBytes, [status: status, statusText: statusText, headers: headers, url: url]) }

    static JavaScriptResponse json(Object data, Object init = [:]) {
        Map settings = new LinkedHashMap(init instanceof Map ? init as Map : [:])
        JavaScriptHeaders headers = new JavaScriptHeaders(settings.headers)
        if (!headers.has('content-type')) headers.set('content-type', 'application/json')
        settings.headers = headers
        new JavaScriptResponse(JsonOutput.toJson(data), settings)
    }

    static JavaScriptResponse redirect(Object url, Object status = 302) {
        int resolvedStatus = JavaScriptNumber.coerce(status).intValue()
        if (!(resolvedStatus in [301, 302, 303, 307, 308])) {
            throw new JavaScriptRangeError('Response redirect status must be 301, 302, 303, 307, or 308')
        }
        new JavaScriptResponse(null, [status: resolvedStatus, headers: ['location': String.valueOf(url)]])
    }

    static JavaScriptResponse error() { new JavaScriptResponse(null, [status: 0]) }
}

/** Java HttpClient bridge for fetch(). */
final class JavaScriptFetch {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .sslContext(JavaScriptSystemTrust.sslContext())
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private JavaScriptFetch() {
    }

    static JavaScriptPromise<JavaScriptResponse> fetch(Object input, Object init = [:]) {
        try {
            JavaScriptRequest request = new JavaScriptRequest(input, init)
            URI uri = URI.create(request.url)
            if (uri.scheme?.equalsIgnoreCase('data')) {
                return JavaScriptPromise.resolve(dataResponse(request))
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            request.headers.each { List<String> header -> builder.header(header[0], header[1]) }
            byte[] body = request.bodyCopy()
            builder.method(request.method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body))
            CompletableFuture<JavaScriptResponse> result = CLIENT.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
                .thenApply { HttpResponse<byte[]> response ->
                    new JavaScriptResponse(response.body(), [status: response.statusCode(), headers: responseHeaders(response.headers().map()), url: response.uri().toString()])
                }
            JavaScriptPromise.resolve(result)
        } catch (Throwable error) {
            JavaScriptPromise.reject(error)
        }
    }

    static Map<String, List<String>> responseHeaders(Map<String, List<String>> headers) {
        headers.findAll { String name, List<String> ignored -> !name.startsWith(':') } as Map<String, List<String>>
    }

    private static JavaScriptResponse dataResponse(JavaScriptRequest request) {
        if (request.method != 'GET') {
            throw new JavaScriptTypeError('data: fetch requests must use GET')
        }
        String source = request.url
        int separator = source.indexOf(',')
        if (separator < 0) {
            throw new JavaScriptTypeError('Invalid data URL')
        }
        String metadata = source.substring('data:'.length(), separator)
        String payload = source.substring(separator + 1)
        List<String> parts = metadata.tokenize(';')
        boolean base64 = parts.any { String part -> part.equalsIgnoreCase('base64') }
        String contentType = parts.find { String part -> !part.equalsIgnoreCase('base64') } ?: 'text/plain;charset=US-ASCII'
        String decoded = URLDecoder.decode(payload.replace('+', '%2B'), StandardCharsets.UTF_8)
        byte[] bytes = base64 ? Base64.decoder.decode(decoded) : decoded.getBytes(StandardCharsets.UTF_8)
        new JavaScriptResponse(bytes, [status: 200, headers: ['content-type': contentType], url: request.url])
    }
}

final class JavaScriptWebPlatformSupport {
    private JavaScriptWebPlatformSupport() { }
    static int relativeIndex(Object value, int length, int defaultValue) {
        if (value == null) return defaultValue
        int index = JavaScriptNumber.coerce(value).intValue()
        index < 0 ? Math.max(length + index, 0) : Math.min(index, length)
    }
}
