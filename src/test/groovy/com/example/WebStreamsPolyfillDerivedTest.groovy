package com.example

/**
 * Groovy vectors adapted from web-streams-polyfill unit tests at a73c482.
 *
 * Covers this facade's iterable-backed ReadableStream and default reader.
 * Controller sources, pipeTo, WritableStream, TransformStream, tee, and BYOB
 * readers are outside this JVM approximation.
 */
class WebStreamsPolyfillDerivedTest extends GravyTestCase {
    void testDefaultStreamImmediatelyReportsEndOfStream() {
        def stream = new JavaScriptReadableStream()
        def reader = stream.getReader()

        assert stream.locked
        assert reader.read().await() == [value: null, done: true]
    }

    void testDefaultReaderReadsIterableChunksInOrder() {
        def stream = new JavaScriptReadableStream(['a', 'b'])
        def reader = stream.getReader()

        assert reader.read().await() == [value: 'a', done: false]
        assert reader.read().await() == [value: 'b', done: false]
        assert reader.read().await() == [value: null, done: true]
    }

    void testDefaultReaderConsumesLargePrefilledIterable() {
        int count = 2_048
        def stream = new JavaScriptReadableStream((1..count).collect { int index -> "chunk-${index}" })
        def reader = stream.getReader()

        for (int index = 1; index <= count; index++) {
            assert reader.read().await() == [value: "chunk-${index}", done: false]
        }
        assert reader.read().await() == [value: null, done: true]
    }

    void testReaderLockPreventsConcurrentReadersAndCanBeReleased() {
        def stream = new JavaScriptReadableStream(['a'])
        def first = stream.getReader()

        assert shouldFail(JavaScriptTypeError) { stream.getReader() }
        first.releaseLock()
        assert !stream.locked
        assert shouldFail(JavaScriptTypeError) { first.read().await() }

        def second = stream.getReader()
        assert second.read().await() == [value: 'a', done: false]
        assert second.read().await() == [value: null, done: true]
    }

    void testCancelEndsReaderAndRejectsUnsupportedSources() {
        def stream = new JavaScriptReadableStream(['a', 'b'])
        def reader = stream.getReader()

        assert reader.cancel('no longer needed').await() == null
        assert reader.read().await() == [value: null, done: true]
        assert shouldFail(JavaScriptTypeError) { new JavaScriptReadableStream(42) }
    }
}
