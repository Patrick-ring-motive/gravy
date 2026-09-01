package com.example

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.LinkedHashMap
import java.util.List
import java.util.Locale
import java.util.Map
import java.util.Set
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Supplier
import java.util.zip.Deflater
import java.util.zip.DeflaterInputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/** DOM-style event carrying type, target, cancellation, and timestamp metadata. */
class JavaScriptEvent {
    final String type
    final boolean bubbles
    final boolean cancelable
    final boolean composed
    final double timeStamp = System.nanoTime() / 1_000_000d
    private boolean defaultPrevented
    private boolean propagationStopped
    private boolean immediatePropagationStopped
    private Object target
    private Object currentTarget

    JavaScriptEvent(Object type, Object options = [:]) {
        if (type == null || String.valueOf(type).isEmpty()) {
            throw new JavaScriptTypeError('Event type must not be empty')
        }
        Map settings = options instanceof Map ? options as Map : [:]
        this.type = String.valueOf(type)
        this.bubbles = settings.bubbles == true
        this.cancelable = settings.cancelable == true
        this.composed = settings.composed == true
    }

    boolean getDefaultPrevented() { defaultPrevented }
    boolean isPropagationStopped() { propagationStopped }
    boolean isImmediatePropagationStopped() { immediatePropagationStopped }
    Object getTarget() { target }
    Object getCurrentTarget() { currentTarget }
    void preventDefault() { if (cancelable) defaultPrevented = true }
    void stopPropagation() { propagationStopped = true }
    void stopImmediatePropagation() {
        immediatePropagationStopped = true
        propagationStopped = true
    }

    void setDispatchTarget(Object value) { target = value }
    void setDispatchCurrentTarget(Object value) { currentTarget = value }
}

/** Event with arbitrary caller-supplied detail. */
final class JavaScriptCustomEvent extends JavaScriptEvent {
    final Object detail

    JavaScriptCustomEvent(Object type, Object options = [:]) {
        super(type, options)
        this.detail = options instanceof Map ? (options as Map).detail : null
    }
}

/** Shared callback adapter that honors Groovy closure arity. */
final class JavaScriptCallback {
    private JavaScriptCallback() { }

    static Object call(Object callback, Object... arguments) {
        if (callback instanceof Closure) {
            Closure closure = callback as Closure
            int count = Math.max(0, Math.min(closure.maximumNumberOfParameters, arguments.length))
            return closure.call(*(arguments.take(count) as Object[]))
        }
        if (callback != null && callback.metaClass.respondsTo(callback, 'handleEvent', JavaScriptEvent)) {
            return callback.handleEvent(arguments.length == 0 ? null : arguments[0])
        }
        if (callback != null && callback.metaClass.respondsTo(callback, 'call')) {
            return callback.call(*arguments)
        }
        throw new JavaScriptTypeError('Callback must be a Closure or event listener object')
    }
}

/** Listener registry compatible with addEventListener/removeEventListener. */
class JavaScriptEventTarget {
    private final Map<String, List<JavaScriptEventListenerRegistration>> listeners = new LinkedHashMap<>()
    private final Map<String, Object> eventHandlers = new LinkedHashMap<>()

    void addEventListener(Object type, Object listener, Object options = [:]) {
        if (listener == null) return
        String eventType = String.valueOf(type)
        Map settings = options instanceof Map ? options as Map : [:]
        synchronized (this) {
            List<JavaScriptEventListenerRegistration> registrations = listeners.computeIfAbsent(eventType) { [] }
            if (!registrations.any { JavaScriptEventListenerRegistration registration -> registration.listener.is(listener) }) {
                registrations << new JavaScriptEventListenerRegistration(listener, settings.once == true)
            }
        }
    }

    void removeEventListener(Object type, Object listener, Object options = [:]) {
        synchronized (this) {
            listeners[String.valueOf(type)]?.removeAll { JavaScriptEventListenerRegistration registration -> registration.listener.is(listener) }
        }
    }

    boolean dispatchEvent(JavaScriptEvent event) {
        if (event == null) throw new JavaScriptTypeError('dispatchEvent requires an Event')
        if (event.target == null) event.setDispatchTarget(this)
        event.setDispatchCurrentTarget(this)
        List<JavaScriptEventListenerRegistration> snapshot
        Object propertyHandler
        synchronized (this) {
            snapshot = new ArrayList<>(listeners[event.type] ?: [])
            propertyHandler = eventHandlers[event.type]
        }
        for (JavaScriptEventListenerRegistration registration : snapshot) {
            JavaScriptCallback.call(registration.listener, event)
            if (registration.once) removeEventListener(event.type, registration.listener)
            if (event.immediatePropagationStopped) break
        }
        if (propertyHandler != null && !event.immediatePropagationStopped) JavaScriptCallback.call(propertyHandler, event)
        !event.defaultPrevented
    }

    protected Object eventHandler(String type) {
        synchronized (this) { eventHandlers[type] }
    }

    protected void eventHandler(String type, Object listener) {
        if (listener != null && !(listener instanceof Closure) && !listener.metaClass.respondsTo(listener, 'handleEvent', JavaScriptEvent)) {
            throw new JavaScriptTypeError('Event handler must be a Closure or event listener object')
        }
        synchronized (this) { eventHandlers[type] = listener }
    }
}

final class JavaScriptEventListenerRegistration {
    final Object listener
    final boolean once

    JavaScriptEventListenerRegistration(Object listener, boolean once) {
        this.listener = listener
        this.once = once
    }
}

/** DOMException-style failure used when an AbortSignal has settled. */
final class JavaScriptAbortError extends JavaScriptError {
    JavaScriptAbortError(String message = 'This operation was aborted') { super(message) }
    @Override String getName() { 'AbortError' }
}

/** Signal that notifies listeners once its matching controller aborts. */
final class JavaScriptAbortSignal extends JavaScriptEventTarget {
    private boolean aborted
    private Object reason

    boolean getAborted() { aborted }
    Object getReason() { reason }

    void throwIfAborted() {
        if (!aborted) return
        if (reason instanceof Throwable) throw reason as Throwable
        throw new JavaScriptAbortError(String.valueOf(reason ?: 'This operation was aborted'))
    }

    static JavaScriptAbortSignal abort(Object reason = null) {
        JavaScriptAbortController controller = new JavaScriptAbortController()
        controller.abort(reason)
        controller.signal
    }

    static JavaScriptAbortSignal timeout(Object milliseconds) {
        JavaScriptAbortController controller = new JavaScriptAbortController()
        JavaScriptTimers.setTimeout({ controller.abort(new JavaScriptAbortError('Signal timed out')) }, milliseconds)
        controller.signal
    }

    static JavaScriptAbortSignal any(Object signals) {
        List<Object> candidates = JavaScriptCollectionSupport.valuesFor(signals)
        JavaScriptAbortController controller = new JavaScriptAbortController()
        candidates.each { Object candidate ->
            if (!(candidate instanceof JavaScriptAbortSignal)) {
                throw new JavaScriptTypeError('AbortSignal.any requires AbortSignal values')
            }
            JavaScriptAbortSignal signal = candidate as JavaScriptAbortSignal
            if (signal.aborted) {
                controller.abort(signal.reason)
            } else {
                signal.addEventListener('abort', { controller.abort(signal.reason) }, [once: true])
            }
        }
        controller.signal
    }

    void abortInternal(Object value) {
        if (aborted) return
        aborted = true
        reason = value == null ? new JavaScriptAbortError() : value
        dispatchEvent(new JavaScriptEvent('abort'))
    }

    Object getOnabort() { eventHandler('abort') }
    void setOnabort(Object listener) { eventHandler('abort', listener) }
}

/** Controller that provides one AbortSignal. */
final class JavaScriptAbortController {
    final JavaScriptAbortSignal signal = new JavaScriptAbortSignal()

    void abort(Object reason = null) { signal.abortInternal(reason) }
}

/** Daemon-backed implementations for timer globals and microtasks. */
final class JavaScriptTimers {
    private static final ThreadFactory THREAD_FACTORY = ({ Runnable task ->
        Thread thread = new Thread(task, 'gravy-web-runtime')
        thread.daemon = true
        thread
    } as ThreadFactory)
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2, THREAD_FACTORY)

    private JavaScriptTimers() { }

    static ScheduledFuture setTimeout(Object callback, Object delay = 0, Object... arguments) {
        validate(callback)
        EXECUTOR.schedule({ JavaScriptCallback.call(callback, *arguments) } as Runnable, delayMillis(delay), TimeUnit.MILLISECONDS)
    }

    static ScheduledFuture setInterval(Object callback, Object delay = 0, Object... arguments) {
        validate(callback)
        long millis = delayMillis(delay)
        EXECUTOR.scheduleAtFixedRate({ JavaScriptCallback.call(callback, *arguments) } as Runnable, millis, Math.max(1L, millis), TimeUnit.MILLISECONDS)
    }

    static void clearTimeout(Object handle) { cancel(handle) }
    static void clearInterval(Object handle) { cancel(handle) }

    static void queueMicrotask(Object callback) {
        validate(callback)
        EXECUTOR.execute({ JavaScriptCallback.call(callback) } as Runnable)
    }

    static <T> CompletableFuture<T> supplyAsync(Closure callback) {
        CompletableFuture.supplyAsync(({ callback.call() } as Supplier<T>), EXECUTOR)
    }

    private static void validate(Object callback) {
        if (!(callback instanceof Closure) && (callback == null || !callback.metaClass.respondsTo(callback, 'call'))) {
            throw new JavaScriptTypeError('Timer callback must be a Closure')
        }
    }

    private static long delayMillis(Object delay) {
        Math.max(0L, JavaScriptNumber.coerce(delay ?: 0).longValue())
    }

    private static void cancel(Object handle) {
        if (handle instanceof ScheduledFuture) (handle as ScheduledFuture).cancel(false)
    }
}

/** Promise-aware dynamic chunk queue used by transform, compression, and decompression streams. */
final class JavaScriptStreamQueue {
    private final List<Object> values = []
    private final List<JavaScriptPromiseResolvers> readers = []
    private boolean closed
    private Object failure

    synchronized JavaScriptPromise<Map<String, Object>> read() {
        if (!values.isEmpty()) return JavaScriptPromise.resolve([value: values.remove(0), done: false])
        if (failure != null) return JavaScriptPromise.reject(failure)
        if (closed) return JavaScriptPromise.resolve([value: null, done: true])
        JavaScriptPromiseResolvers result = JavaScriptPromise.withResolvers()
        readers << result
        result.promise as JavaScriptPromise<Map<String, Object>>
    }

    synchronized void enqueue(Object value) {
        if (closed || failure != null) throw new JavaScriptTypeError('Cannot enqueue into a closed stream')
        if (!readers.isEmpty()) readers.remove(0).resolve.call([value: value, done: false])
        else values << value
    }

    synchronized void close() {
        if (closed) return
        closed = true
        readers.each { JavaScriptPromiseResolvers reader -> reader.resolve.call([value: null, done: true]) }
        readers.clear()
    }

    synchronized void error(Object reason) {
        if (closed || failure != null) return
        failure = reason ?: new JavaScriptError('Stream failed')
        readers.each { JavaScriptPromiseResolvers reader -> reader.reject.call(failure) }
        readers.clear()
    }
}

/** WritableStream facade with default writer, sink callbacks, and lock state. */
final class JavaScriptWritableStream {
    private final Object sink
    private boolean locked
    private boolean closed

    JavaScriptWritableStream(Object sink = [:], Object strategy = null) {
        if (sink != null && !(sink instanceof Map) && !(sink instanceof Closure)) {
            throw new JavaScriptTypeError('WritableStream sink must be a Map or Closure')
        }
        this.sink = sink ?: [:]
    }

    boolean getLocked() { locked }

    JavaScriptWritableStreamDefaultWriter getWriter() {
        if (locked) throw new JavaScriptTypeError('WritableStream is already locked')
        locked = true
        new JavaScriptWritableStreamDefaultWriter(this)
    }

    private JavaScriptPromise write(Object chunk) {
        if (closed) return JavaScriptPromise.reject(new JavaScriptTypeError('Cannot write to a closed stream'))
        callSink('write', chunk)
    }

    private JavaScriptPromise close() {
        if (closed) return JavaScriptPromise.resolve(null)
        callSink('close').then { Object ignored -> closed = true; null }
    }

    private JavaScriptPromise abort(Object reason) {
        closed = true
        callSink('abort', reason)
    }

    private void release() { locked = false }

    private JavaScriptPromise callSink(String method, Object... arguments) {
        try {
            Object callback = sink instanceof Map ? (sink as Map).get(method) : (method == 'write' ? sink : null)
            callback == null ? JavaScriptPromise.resolve(null) : JavaScriptPromise.resolve(JavaScriptCallback.call(callback, *arguments))
        } catch (Throwable error) {
            JavaScriptPromise.reject(error)
        }
    }

    static final class JavaScriptWritableStreamDefaultWriter {
        private JavaScriptWritableStream stream

        JavaScriptWritableStreamDefaultWriter(JavaScriptWritableStream stream) { this.stream = stream }
        JavaScriptPromise write(Object chunk) { requireStream().write(chunk) }
        JavaScriptPromise close() { requireStream().close() }
        JavaScriptPromise abort(Object reason = null) { requireStream().abort(reason) }
        JavaScriptPromise getReady() { JavaScriptPromise.resolve(null) }
        JavaScriptPromise getClosed() { JavaScriptPromise.resolve(null) }
        void releaseLock() { if (stream != null) stream.release(); stream = null }

        private JavaScriptWritableStream requireStream() {
            if (stream == null) throw new JavaScriptTypeError('Writer lock has been released')
            stream
        }
    }
}

/** Controller exposed to TransformStream transformer callbacks. */
final class JavaScriptTransformStreamDefaultController {
    private final JavaScriptStreamQueue queue

    JavaScriptTransformStreamDefaultController(JavaScriptStreamQueue queue) { this.queue = queue }
    void enqueue(Object chunk) { queue.enqueue(chunk) }
    void error(Object reason) { queue.error(reason) }
    void terminate() { queue.close() }
}

/** TransformStream facade that connects a writable transformer to a readable queue. */
final class JavaScriptTransformStream {
    final JavaScriptReadableStream readable
    final JavaScriptWritableStream writable

    JavaScriptTransformStream(Object transformer = [:], Object writableStrategy = null, Object readableStrategy = null) {
        if (transformer != null && !(transformer instanceof Map) && !(transformer instanceof Closure)) {
            throw new JavaScriptTypeError('TransformStream transformer must be a Map or Closure')
        }
        JavaScriptStreamQueue queue = new JavaScriptStreamQueue()
        JavaScriptTransformStreamDefaultController controller = new JavaScriptTransformStreamDefaultController(queue)
        Object transform = transformer instanceof Map ? (transformer as Map).get('transform') : transformer
        Object flush = transformer instanceof Map ? (transformer as Map).get('flush') : null
        Object cancel = transformer instanceof Map ? (transformer as Map).get('cancel') : null
        this.readable = JavaScriptReadableStream.fromQueue(queue)
        this.writable = new JavaScriptWritableStream([
            write: { Object chunk ->
                try {
                    if (transform == null) controller.enqueue(chunk)
                    else return JavaScriptCallback.call(transform, chunk, controller)
                } catch (Throwable error) {
                    controller.error(error)
                    throw error
                }
                null
            },
            close: {
                Object result = flush == null ? null : JavaScriptCallback.call(flush, controller)
                JavaScriptPromise.resolve(result).then { Object ignored -> queue.close(); null }
            },
            abort: { Object reason ->
                if (cancel != null) JavaScriptCallback.call(cancel, reason)
                queue.error(reason)
                null
            }
        ], writableStrategy)
    }
}

/** Queuing strategy that measures chunks by byte length. */
final class JavaScriptByteLengthQueuingStrategy {
    final double highWaterMark

    JavaScriptByteLengthQueuingStrategy(Object init = [:]) {
        Map settings = init instanceof Map ? init as Map : [highWaterMark: init]
        this.highWaterMark = JavaScriptNumber.coerce(settings.highWaterMark ?: 0).doubleValue()
    }

    int size(Object chunk) { JavaScriptWebBytes.bytesFor(chunk).length }
}

/** Queuing strategy that assigns every chunk a size of one. */
final class JavaScriptCountQueuingStrategy {
    final double highWaterMark

    JavaScriptCountQueuingStrategy(Object init = [:]) {
        Map settings = init instanceof Map ? init as Map : [highWaterMark: init]
        this.highWaterMark = JavaScriptNumber.coerce(settings.highWaterMark ?: 0).doubleValue()
    }

    int size(Object chunk) { 1 }
}

/** Buffered compression transform supporting gzip, deflate, and deflate-raw. */
final class JavaScriptCompressionStream {
    final String format
    final JavaScriptReadableStream readable
    final JavaScriptWritableStream writable

    JavaScriptCompressionStream(Object format) {
        this.format = JavaScriptCompressionSupport.normalizeFormat(format)
        JavaScriptStreamQueue queue = new JavaScriptStreamQueue()
        ByteArrayOutputStream source = new ByteArrayOutputStream()
        this.readable = JavaScriptReadableStream.fromQueue(queue)
        this.writable = new JavaScriptWritableStream([
            write: { Object chunk -> source.write(JavaScriptWebBytes.bytesFor(chunk)); null },
            close: {
                queue.enqueue(new JavaScriptUint8Array(JavaScriptCompressionSupport.compress(this.format, source.toByteArray())))
                queue.close()
                null
            },
            abort: { Object reason -> queue.error(reason); null }
        ])
    }
}

/** Buffered decompression transform supporting gzip, deflate, and deflate-raw. */
final class JavaScriptDecompressionStream {
    final String format
    final JavaScriptReadableStream readable
    final JavaScriptWritableStream writable

    JavaScriptDecompressionStream(Object format) {
        this.format = JavaScriptCompressionSupport.normalizeFormat(format)
        JavaScriptStreamQueue queue = new JavaScriptStreamQueue()
        ByteArrayOutputStream source = new ByteArrayOutputStream()
        this.readable = JavaScriptReadableStream.fromQueue(queue)
        this.writable = new JavaScriptWritableStream([
            write: { Object chunk -> source.write(JavaScriptWebBytes.bytesFor(chunk)); null },
            close: {
                queue.enqueue(new JavaScriptUint8Array(JavaScriptCompressionSupport.decompress(this.format, source.toByteArray())))
                queue.close()
                null
            },
            abort: { Object reason -> queue.error(reason); null }
        ])
    }
}

final class JavaScriptCompressionSupport {
    static final int MAX_DECOMPRESSED_BYTES = 64 * 1024 * 1024

    private JavaScriptCompressionSupport() { }

    static String normalizeFormat(Object candidate) {
        String format = String.valueOf(candidate).toLowerCase(Locale.ROOT)
        if (!(format in ['gzip', 'deflate', 'deflate-raw'])) {
            throw new JavaScriptTypeError("Unsupported compression format: ${candidate}")
        }
        format
    }

    static byte[] compress(String format, byte[] source) {
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        OutputStream stream = format == 'gzip' ? new GZIPOutputStream(output) :
            new DeflaterOutputStream(output, new Deflater(Deflater.DEFAULT_COMPRESSION, format == 'deflate-raw'))
        stream.write(source)
        stream.close()
        output.toByteArray()
    }

    static byte[] decompress(String format, byte[] source, int maximumBytes = MAX_DECOMPRESSED_BYTES) {
        if (maximumBytes < 0) {
            throw new IllegalArgumentException('maximumBytes must be non-negative')
        }
        InputStream stream = format == 'gzip' ? new GZIPInputStream(new ByteArrayInputStream(source)) :
            new InflaterInputStream(new ByteArrayInputStream(source), new Inflater(format == 'deflate-raw'))
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 8192))
        byte[] buffer = new byte[8192]
        try {
            int count
            while ((count = stream.read(buffer)) != -1) {
                if (output.size() > maximumBytes - count) {
                    throw new JavaScriptRangeError("Decompressed data exceeds ${maximumBytes} byte limit")
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } finally {
            stream.close()
        }
    }
}

/** Message event with cloned data and channel metadata. */
final class JavaScriptMessageEvent extends JavaScriptEvent {
    final Object data
    final String origin
    final String lastEventId
    final Object source
    final List<Object> ports

    JavaScriptMessageEvent(Object type, Object options = [:]) {
        super(type, options)
        Map settings = options instanceof Map ? options as Map : [:]
        this.data = settings.data
        this.origin = settings.origin == null ? '' : String.valueOf(settings.origin)
        this.lastEventId = settings.lastEventId == null ? '' : String.valueOf(settings.lastEventId)
        this.source = settings.source
        this.ports = JavaScriptCollectionSupport.valuesFor(settings.ports ?: []).asImmutable()
    }
}

/** Bidirectional endpoint used by MessageChannel. */
final class JavaScriptMessagePort extends JavaScriptEventTarget {
    private JavaScriptMessagePort peer
    private boolean closed

    void connect(JavaScriptMessagePort other) { peer = other }
    void start() { }
    void close() { closed = true }

    void postMessage(Object message, Object transfer = null) {
        if (closed) throw new JavaScriptTypeError('MessagePort is closed')
        JavaScriptMessagePort target = peer
        if (target == null || target.closed) return
        Object copied = JavaScriptWebUtilities.structuredClone(message)
        JavaScriptTimers.queueMicrotask { target.receive(copied) }
    }

    private void receive(Object message) {
        if (!closed) dispatchEvent(new JavaScriptMessageEvent('message', [data: message, source: peer]))
    }

    Object getOnmessage() { eventHandler('message') }
    void setOnmessage(Object listener) { eventHandler('message', listener) }
    Object getOnmessageerror() { eventHandler('messageerror') }
    void setOnmessageerror(Object listener) { eventHandler('messageerror', listener) }
}

/** Entangled pair of MessagePort instances. */
final class JavaScriptMessageChannel {
    final JavaScriptMessagePort port1 = new JavaScriptMessagePort()
    final JavaScriptMessagePort port2 = new JavaScriptMessagePort()

    JavaScriptMessageChannel() {
        port1.connect(port2)
        port2.connect(port1)
    }
}

/** Same-process BroadcastChannel facade. */
final class JavaScriptBroadcastChannel extends JavaScriptEventTarget {
    private static final Map<String, Set<JavaScriptBroadcastChannel>> CHANNELS = new ConcurrentHashMap<>()
    final String name
    private boolean closed

    JavaScriptBroadcastChannel(Object name) {
        this.name = String.valueOf(name)
        CHANNELS.computeIfAbsent(this.name) { ConcurrentHashMap.newKeySet() }.add(this)
    }

    void postMessage(Object message) {
        if (closed) throw new JavaScriptTypeError('BroadcastChannel is closed')
        Object copied = JavaScriptWebUtilities.structuredClone(message)
        new ArrayList<>(CHANNELS[name] ?: Collections.emptySet()).each { JavaScriptBroadcastChannel target ->
            if (!target.is(this) && !target.closed) JavaScriptTimers.queueMicrotask { target.dispatchEvent(new JavaScriptMessageEvent('message', [data: JavaScriptWebUtilities.structuredClone(copied)])) }
        }
    }

    void close() {
        if (!closed) CHANNELS[name]?.remove(this)
        closed = true
    }

    Object getOnmessage() { eventHandler('message') }
    void setOnmessage(Object listener) { eventHandler('message', listener) }
    Object getOnmessageerror() { eventHandler('messageerror') }
    void setOnmessageerror(Object listener) { eventHandler('messageerror', listener) }
}

/** Browser-compatible WebSocket facade backed by java.net.http.WebSocket. */
final class JavaScriptWebSocket extends JavaScriptEventTarget implements java.net.http.WebSocket.Listener {
    static final int CONNECTING = 0
    static final int OPEN = 1
    static final int CLOSING = 2
    static final int CLOSED = 3

    final String url
    private volatile int readyState = CONNECTING
    private volatile long bufferedAmount
    private volatile String protocol = ''
    private volatile String binaryType = 'blob'
    private java.net.http.WebSocket socket
    private final StringBuilder textBuffer = new StringBuilder()
    private final ByteArrayOutputStream binaryBuffer = new ByteArrayOutputStream()

    JavaScriptWebSocket(Object input, Object protocols = null) {
        URI uri
        try {
            uri = URI.create(String.valueOf(input))
        } catch (IllegalArgumentException error) {
            throw new JavaScriptSyntaxError("Invalid WebSocket URL: ${input}", error)
        }
        if (!(uri.scheme?.equalsIgnoreCase('ws') || uri.scheme?.equalsIgnoreCase('wss'))) {
            throw new JavaScriptSyntaxError('WebSocket URL must use ws: or wss:')
        }
        this.url = uri.toString()
        java.net.http.WebSocket.Builder builder = HttpClient.newBuilder()
            .sslContext(JavaScriptSystemTrust.sslContext())
            .build()
            .newWebSocketBuilder()
        List<String> requested = protocols == null ? [] : JavaScriptCollectionSupport.valuesFor(protocols).collect { String.valueOf(it) }
        if (!requested.isEmpty()) builder.subprotocols(requested[0], requested.size() == 1 ? new String[0] : requested.subList(1, requested.size()) as String[])
        builder.buildAsync(uri, this).whenComplete { java.net.http.WebSocket value, Throwable error ->
            if (error != null && readyState != CLOSED) {
                readyState = CLOSED
                dispatchEvent(new JavaScriptEvent('error'))
                dispatchEvent(new JavaScriptEvent('close', [wasClean: false]))
            }
        }
    }

    int getReadyState() { readyState }
    long getBufferedAmount() { bufferedAmount }
    String getProtocol() { protocol }
    String getExtensions() { '' }
    String getBinaryType() { binaryType }
    void setBinaryType(Object value) { binaryType = normalizeBinaryType(value) }

    static String normalizeBinaryType(Object value) {
        String requested = String.valueOf(value).toLowerCase(Locale.ROOT)
        if (!(requested in ['blob', 'arraybuffer'])) {
            throw new JavaScriptTypeError("Unsupported WebSocket binaryType: ${value}")
        }
        requested
    }

    void send(Object data) {
        if (readyState != OPEN || socket == null) throw new JavaScriptTypeError('WebSocket is not open')
        byte[] bytes = data instanceof CharSequence ? null : JavaScriptWebBytes.bytesFor(data)
        bufferedAmount += bytes == null ? String.valueOf(data).getBytes(StandardCharsets.UTF_8).length : bytes.length
        CompletableFuture<java.net.http.WebSocket> sent = bytes == null ? socket.sendText(String.valueOf(data), true) : socket.sendBinary(ByteBuffer.wrap(bytes), true)
        sent.whenComplete { Object ignored, Throwable error ->
            bufferedAmount = Math.max(0L, bufferedAmount - (bytes == null ? String.valueOf(data).getBytes(StandardCharsets.UTF_8).length : bytes.length))
            if (error != null) dispatchEvent(new JavaScriptEvent('error'))
        }
    }

    void close(Object code = 1000, Object reason = '') {
        if (readyState in [CLOSING, CLOSED]) return
        int closeCode = JavaScriptNumber.coerce(code).intValue()
        if (!(closeCode == 1000 || closeCode in 3000..4999)) throw new JavaScriptRangeError('Invalid WebSocket close code')
        String text = String.valueOf(reason)
        if (text.getBytes(StandardCharsets.UTF_8).length > 123) throw new JavaScriptSyntaxError('WebSocket close reason exceeds 123 bytes')
        readyState = CLOSING
        if (socket != null) socket.sendClose(closeCode, text)
    }

    @Override
    void onOpen(java.net.http.WebSocket value) {
        socket = value
        protocol = value.subprotocol ?: ''
        readyState = OPEN
        dispatchEvent(new JavaScriptEvent('open'))
        value.request(1)
    }

    @Override
    CompletionStage<?> onText(java.net.http.WebSocket value, CharSequence data, boolean last) {
        textBuffer.append(data)
        if (last) {
            dispatchEvent(new JavaScriptMessageEvent('message', [data: textBuffer.toString(), origin: url]))
            textBuffer.setLength(0)
        }
        value.request(1)
        CompletableFuture.completedFuture(null)
    }

    @Override
    CompletionStage<?> onBinary(java.net.http.WebSocket value, ByteBuffer data, boolean last) {
        byte[] bytes = new byte[data.remaining()]
        data.get(bytes)
        binaryBuffer.write(bytes)
        if (last) {
            Object message = binaryType == 'arraybuffer' ? bytesToBuffer(binaryBuffer.toByteArray()) : new JavaScriptBlob([binaryBuffer.toByteArray()])
            dispatchEvent(new JavaScriptMessageEvent('message', [data: message, origin: url]))
            binaryBuffer.reset()
        }
        value.request(1)
        CompletableFuture.completedFuture(null)
    }

    @Override
    CompletionStage<?> onClose(java.net.http.WebSocket value, int statusCode, String reason) {
        readyState = CLOSED
        dispatchEvent(new JavaScriptCustomEvent('close', [detail: [code: statusCode, reason: reason, wasClean: true]]))
        CompletableFuture.completedFuture(null)
    }

    @Override
    void onError(java.net.http.WebSocket value, Throwable error) {
        readyState = CLOSED
        dispatchEvent(new JavaScriptEvent('error'))
    }

    Object getOnopen() { eventHandler('open') }
    void setOnopen(Object listener) { eventHandler('open', listener) }
    Object getOnmessage() { eventHandler('message') }
    void setOnmessage(Object listener) { eventHandler('message', listener) }
    Object getOnerror() { eventHandler('error') }
    void setOnerror(Object listener) { eventHandler('error', listener) }
    Object getOnclose() { eventHandler('close') }
    void setOnclose(Object listener) { eventHandler('close', listener) }

    private static JavaScriptArrayBuffer bytesToBuffer(byte[] bytes) {
        JavaScriptArrayBuffer buffer = new JavaScriptArrayBuffer(bytes.length)
        new JavaScriptUint8Array(buffer).set(bytes)
        buffer
    }
}

/** PerformanceEntry facade emitted by mark and measure. */
final class JavaScriptPerformanceEntry {
    final String name
    final String entryType
    final double startTime
    final double duration
    final Object detail

    JavaScriptPerformanceEntry(String name, String entryType, double startTime, double duration, Object detail = null) {
        this.name = name
        this.entryType = entryType
        this.startTime = startTime
        this.duration = duration
        this.detail = detail
    }
}

/** Monotonic Performance API implementation with mark and measure storage. */
final class JavaScriptPerformance {
    static final JavaScriptPerformance INSTANCE = new JavaScriptPerformance()
    private final long originNanos = System.nanoTime()
    private final double timeOrigin = System.currentTimeMillis() - now()
    private final List<JavaScriptPerformanceEntry> entries = new CopyOnWriteArrayList<>()

    private JavaScriptPerformance() { }

    double now() { (System.nanoTime() - originNanos) / 1_000_000d }
    double getTimeOrigin() { timeOrigin }

    JavaScriptPerformanceEntry mark(Object name, Object options = [:]) {
        Map settings = options instanceof Map ? options as Map : [:]
        double start = settings.startTime == null ? now() : JavaScriptNumber.coerce(settings.startTime).doubleValue()
        JavaScriptPerformanceEntry entry = new JavaScriptPerformanceEntry(String.valueOf(name), 'mark', start, 0d, settings.detail)
        entries << entry
        entry
    }

    JavaScriptPerformanceEntry measure(Object name, Object startOrOptions = null, Object endMark = null) {
        Map settings = startOrOptions instanceof Map ? startOrOptions as Map : [:]
        Object startReference = startOrOptions instanceof Map ? settings.start : startOrOptions
        Object endReference = startOrOptions instanceof Map ? settings.end : endMark
        double start = resolveTime(startReference, 0d)
        double end = resolveTime(endReference, now())
        JavaScriptPerformanceEntry entry = new JavaScriptPerformanceEntry(String.valueOf(name), 'measure', start, Math.max(0d, end - start), settings.detail)
        entries << entry
        entry
    }

    List<JavaScriptPerformanceEntry> getEntries() { new ArrayList<>(entries) }
    List<JavaScriptPerformanceEntry> getEntriesByName(Object name, Object type = null) {
        entries.findAll { JavaScriptPerformanceEntry entry -> entry.name == String.valueOf(name) && (type == null || entry.entryType == String.valueOf(type)) }
    }
    List<JavaScriptPerformanceEntry> getEntriesByType(Object type) { entries.findAll { JavaScriptPerformanceEntry entry -> entry.entryType == String.valueOf(type) } }
    void clearMarks(Object name = null) { clearEntries('mark', name) }
    void clearMeasures(Object name = null) { clearEntries('measure', name) }

    private double resolveTime(Object reference, double fallback) {
        if (reference == null) return fallback
        if (reference instanceof Number) return (reference as Number).doubleValue()
        JavaScriptPerformanceEntry mark = entries.findLast { JavaScriptPerformanceEntry entry -> entry.entryType == 'mark' && entry.name == String.valueOf(reference) }
        mark == null ? fallback : mark.startTime
    }

    private void clearEntries(String type, Object name) {
        entries.removeAll { JavaScriptPerformanceEntry entry -> entry.entryType == type && (name == null || entry.name == String.valueOf(name)) }
    }
}

/** Lock passed to navigator.locks request callbacks. */
final class JavaScriptLock {
    final String name
    final String mode

    JavaScriptLock(String name, String mode) {
        this.name = name
        this.mode = mode
    }
}

/** Process-local LockManager implementation for navigator.locks. */
final class JavaScriptLockManager {
    private final Map<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>()

    JavaScriptPromise request(Object name, Object optionsOrCallback, Object callback = null) {
        Map options = callback == null ? [:] : (optionsOrCallback instanceof Map ? optionsOrCallback as Map : [:])
        Object handler = callback == null ? optionsOrCallback : callback
        if (!(handler instanceof Closure)) throw new JavaScriptTypeError('Lock request requires a callback')
        String lockName = String.valueOf(name)
        String mode = String.valueOf(options.mode ?: 'exclusive')
        if (!(mode in ['exclusive', 'shared'])) throw new JavaScriptTypeError('Lock mode must be exclusive or shared')
        ReentrantReadWriteLock lock = locks.computeIfAbsent(lockName) { new ReentrantReadWriteLock() }
        JavaScriptPromise.resolve(JavaScriptTimers.supplyAsync {
            java.util.concurrent.locks.Lock held = mode == 'shared' ? lock.readLock() : lock.writeLock()
            boolean acquired = options.ifAvailable == true ? held.tryLock() : acquire(held)
            if (!acquired) return JavaScriptCallback.call(handler, null)
            try {
                JavaScriptCallback.call(handler, new JavaScriptLock(lockName, mode))
            } finally {
                held.unlock()
            }
        })
    }

    JavaScriptPromise request(Object name, Closure callback) { request(name, callback as Object, null) }

    private static boolean acquire(java.util.concurrent.locks.Lock lock) {
        lock.lock()
        true
    }
}

/** Minimal Node-compatible navigator global. */
final class JavaScriptNavigator {
    static final JavaScriptNavigator INSTANCE = new JavaScriptNavigator()
    final JavaScriptLockManager locks = new JavaScriptLockManager()
    final int hardwareConcurrency = Math.max(1, Runtime.runtime.availableProcessors())
    final String userAgent = "Gravy/1.0 (${System.getProperty('java.vm.name', 'JVM')})"
    final String language = Locale.default.toLanguageTag() ?: 'en'

    private JavaScriptNavigator() { }
}
