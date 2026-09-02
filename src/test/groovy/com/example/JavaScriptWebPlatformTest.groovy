package com.example

/** Local fetch-adjacent HTTP, body, and stream facade checks without network access. */
class JavaScriptWebPlatformTest extends GravyTestCase {
    void testHeadersNormalizeNamesAndAppendValues() {
        def headers = new JavaScriptHeaders(['Content-Type': 'text/plain'])

        headers.append('X-Trace', 'one')
        headers.append('x-trace', 'two')

        assert headers.get('content-type') == 'text/plain'
        assert headers.get('X-TRACE') == 'one, two'
        assert headers.entries().collect() == [['content-type', 'text/plain'], ['x-trace', 'one, two']]
        assert shouldFail(JavaScriptTypeError) { headers.set('bad header', 'x') }
    }

    void testBlobFileAndFormDataPreserveBytesAndEntries() {
        def blob = new JavaScriptBlob(['gro', 'ovy'], [type: 'TEXT/PLAIN'])
        def file = new JavaScriptFile([blob], 'notes.txt', [lastModified: 12])
        def clone = JavaScriptWebUtilities.structuredClone(file)
        def form = new JavaScriptFormData()

        form.append('title', 'Gravy')
        form.append('file', file)
        form.append('title', 'replacement')

        assert blob.size == 6
        assert blob.type == 'text/plain'
        assert blob.text().await() == 'groovy'
        assert blob.slice(3).text().await() == 'ovy'
        assert file.name == 'notes.txt'
        assert file.lastModified == 12L
        assert clone instanceof JavaScriptFile
        assert clone.name == 'notes.txt'
        assert form.getAll('title') == ['Gravy', 'replacement']
        assert form.get('file').is(file)
    }

    void testRequestResponseAndReadableStreamUsePromiseBodies() {
        def request = new JavaScriptRequest('https://example.test/items', [method: 'POST', headers: ['x-id': 7], body: 'payload'])
        def response = JavaScriptResponse.json([ok: true], [status: 201])
        def reader = new JavaScriptReadableStream(['first', 'second']).getReader()

        assert request.method == 'POST'
        assert request.headers.get('x-id') == '7'
        assert request.text().await() == 'payload'
        assert request.clone().text().await() == 'payload'
        assert response.ok
        assert response.status == 201
        assert response.headers.get('content-type') == 'application/json'
        assert response.json().await() == [ok: true]
        assert reader.read().await() == [value: 'first', done: false]
        assert reader.read().await() == [value: 'second', done: false]
        assert reader.read().await() == [value: null, done: true]
    }

    void testFetchHandlesDataUrlsWithoutNetworkAccess() {
        def plain = JavaScriptFetch.fetch('data:text/plain,gravy').await()
        def encoded = JavaScriptFetch.fetch('data:text/plain;base64,Z3Jhdnk=').await()

        assert plain.ok
        assert plain.headers.get('content-type') == 'text/plain'
        assert plain.text().await() == 'gravy'
        assert encoded.text().await() == 'gravy'
    }

    void testFetchFiltersHttp2PseudoHeaders() {
        assert JavaScriptFetch.responseHeaders(['content-type': ['text/plain'], ':status': ['200']]) == ['content-type': ['text/plain']]
    }

    void testSystemTrustContextIsAvailable() {
        assert JavaScriptSystemTrust.sslContext() != null
        assert JavaScriptSystemTrust.systemCertificateCount() >= 0
        assert JavaScriptSystemTrust.trustSource() in ['macOS keychains', 'Windows-ROOT', 'Linux CA bundles', 'JVM default']
        if (JavaScriptSystemTrust.macOS()) {
            assert JavaScriptSystemTrust.macOSSystemCertificateCount() > 0
        }
    }
}
