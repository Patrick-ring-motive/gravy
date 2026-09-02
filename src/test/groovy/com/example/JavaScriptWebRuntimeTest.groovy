package com.example

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Coverage for event, timer, stream, messaging, diagnostics, and navigator globals. */
class JavaScriptWebRuntimeTest extends GravyTestCase {
    void testEventsAndAbortSignalsDispatchAndCancel() {
        def target = new JavaScriptEventTarget()
        def seen = []
        Closure listener = { JavaScriptEvent event -> seen << [event.type, event.target] }

        target.addEventListener('ready', listener, [once: true])
        def event = new JavaScriptEvent('ready', [cancelable: true])
        event.preventDefault()

        assert !target.dispatchEvent(event)
        assert seen == [['ready', target]]
        assert target.dispatchEvent(new JavaScriptCustomEvent('ready', [detail: 'ignored']))

        def controller = new JavaScriptAbortController()
        def aborted = []
        controller.signal.addEventListener('abort', { aborted << controller.signal.reason })
        controller.abort('stop')

        assert controller.signal.aborted
        assert controller.signal.reason == 'stop'
        assert aborted == ['stop']
        assert shouldFail(JavaScriptAbortError) { controller.signal.throwIfAborted() }
        assert JavaScriptAbortSignal.any([controller.signal]).aborted
    }

    void testStopImmediatePropagationSkipsRemainingListenersAndHandler() {
        def target = new JavaScriptEventTarget()
        def calls = []
        target.addEventListener('ready', { JavaScriptEvent event -> calls << 'first'; event.stopImmediatePropagation() })
        target.addEventListener('ready', { calls << 'second' })
        target.eventHandler('ready', { calls << 'handler' })

        assert target.dispatchEvent(new JavaScriptEvent('ready'))
        assert calls == ['first']
    }

    void testTimersRunCallbacksAndCanBeCancelled() {
        def latch = new CountDownLatch(2)
        def values = []
        def timeout = JavaScriptTimers.setTimeout({ String value -> values << value; latch.countDown() }, 1, 'timeout')
        def ticks = new CountDownLatch(2)
        def interval = JavaScriptTimers.setInterval({ ticks.countDown() }, 10)

        try {
            JavaScriptTimers.queueMicrotask { values << 'microtask'; latch.countDown() }
            assert timeout instanceof java.util.concurrent.ScheduledFuture
            assert latch.await(5, TimeUnit.SECONDS): "Timed out waiting for callbacks; values=${values}"
            assert values as Set == ['timeout', 'microtask'] as Set
            assert ticks.await(5, TimeUnit.SECONDS): "Timed out waiting for interval; remaining=${ticks.count}"
        } finally {
            JavaScriptTimers.clearTimeout(timeout)
            JavaScriptTimers.clearInterval(interval)
        }
    }

    void testWritableTransformAndCompressionStreamsRoundTrip() {
        def written = []
        def writable = new JavaScriptWritableStream([write: { Object chunk -> written << chunk }])
        def writer = writable.getWriter()

        writer.write('one').await()
        writer.close().await()

        assert written == ['one']
        assert writable.locked

        def transform = new JavaScriptTransformStream([transform: { Object chunk, JavaScriptTransformStreamDefaultController controller ->
            controller.enqueue(String.valueOf(chunk).toUpperCase())
        }])
        def transformWriter = transform.writable.getWriter()
        transformWriter.write('gravy').await()
        transformWriter.close().await()
        def transformReader = transform.readable.getReader()

        assert transformReader.read().await() == [value: 'GRAVY', done: false]
        assert transformReader.read().await() == [value: null, done: true]

        def compressed = new JavaScriptCompressionStream('gzip')
        def compressedWriter = compressed.writable.getWriter()
        compressedWriter.write('gravy').await()
        compressedWriter.close().await()
        def compressedChunk = compressed.readable.getReader().read().await().value

        def decompressed = new JavaScriptDecompressionStream('gzip')
        def decompressedWriter = decompressed.writable.getWriter()
        decompressedWriter.write(compressedChunk).await()
        decompressedWriter.close().await()
        def text = new JavaScriptTextDecoder().decode(decompressed.readable.getReader().read().await().value)

        assert text == 'gravy'
        assert new JavaScriptByteLengthQueuingStrategy([highWaterMark: 16]).size('😄') == 4
        assert new JavaScriptCountQueuingStrategy([highWaterMark: 1]).size('ignored') == 1
    }

    void testDecompressionRejectsOutputBeyondConfiguredLimit() {
        byte[] compressed = JavaScriptCompressionSupport.compress('gzip', new byte[4096])

        assert shouldFail(JavaScriptRangeError) {
            JavaScriptCompressionSupport.decompress('gzip', compressed, 1024)
        }
        assert JavaScriptCompressionSupport.decompress('gzip', compressed, 4096).length == 4096
    }

    void testChannelMessagingClonesMessagesAndExcludesBroadcastSender() {
        def channel = new JavaScriptMessageChannel()
        def received = []
        def delivered = new CountDownLatch(1)
        channel.port2.onmessage = { JavaScriptMessageEvent event -> received << event.data; delivered.countDown() }

        try {
            channel.port1.postMessage([value: 1])
            assert delivered.await(5, TimeUnit.SECONDS): 'Timed out waiting for MessageChannel delivery'
            assert received == [[value: 1]]
        } finally {
            channel.port1.close()
            channel.port2.close()
        }

        String channelName = "gravy-tests-${System.nanoTime()}"
        def first = new JavaScriptBroadcastChannel(channelName)
        def second = new JavaScriptBroadcastChannel(channelName)
        def broadcast = []
        def broadcastDelivered = new CountDownLatch(1)
        first.onmessage = { JavaScriptMessageEvent event -> broadcast << 'first' }
        second.onmessage = { JavaScriptMessageEvent event -> broadcast << event.data; broadcastDelivered.countDown() }

        try {
            first.postMessage('ready')
            assert broadcastDelivered.await(5, TimeUnit.SECONDS): 'Timed out waiting for BroadcastChannel delivery'
            assert broadcast == ['ready']
        } finally {
            first.close()
            second.close()
        }
    }

    void testWebSocketRejectsNonWebSocketUrlsWithoutConnecting() {
        assert shouldFail(JavaScriptSyntaxError) { new JavaScriptWebSocket('https://example.test/') }
    }

    void testWebSocketBinaryTypeValidationDoesNotRequireNetwork() {
        assert JavaScriptWebSocket.normalizeBinaryType('blob') == 'blob'
        assert JavaScriptWebSocket.normalizeBinaryType('arraybuffer') == 'arraybuffer'
        assert JavaScriptWebSocket.normalizeBinaryType('BLOB') == 'blob'
        assert shouldFail(JavaScriptTypeError) { JavaScriptWebSocket.normalizeBinaryType('bytes') }
    }

    void testPerformanceAndNavigatorLocksExposeNodeStyleMetadata() {
        def performance = JavaScriptPerformance.INSTANCE
        performance.clearMarks()
        performance.clearMeasures()
        def mark = performance.mark('before')
        def measure = performance.measure('elapsed', 'before')

        assert mark.entryType == 'mark'
        assert measure.entryType == 'measure'
        assert measure.duration >= 0d
        assert performance.getEntriesByName('before', 'mark') == [mark]
        assert performance.now() >= 0d

        def navigator = JavaScriptNavigator.INSTANCE
        assert navigator.hardwareConcurrency >= 1
        assert navigator.userAgent.startsWith('Gravy/')
        assert navigator.language
        assert navigator.locks.request('gravy-lock', [:], { JavaScriptLock lock -> lock.name }).await() == 'gravy-lock'
    }
}
