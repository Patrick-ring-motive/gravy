package com.example

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonOutput

import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.LinkedHashSet
import java.util.Locale

/**
 * Groovy vectors adapted from node-fetch v3 test sources at 8b3320d.
 *
 * Covers supported Headers, Request, Response, and local HTTP behavior.
 * Node agents, compression, abort signals, stream backpressure, raw headers,
 * and multipart FormData encoding are outside this JVM facade.
 */
class NodeFetchDerivedWebPlatformTest extends GravyTestCase {
    void testHeadersAcceptIterablePairsAndRejectInvalidNamesAndValues() {
        def headers = new JavaScriptHeaders([
            ['b', '2'],
            ['c', '4'],
            new LinkedHashSet(['b', '3']),
            ['a', '1']
        ])
        List<List<Object>> callbacks = []

        headers.forEach { String value, String name, JavaScriptHeaders owner ->
            callbacks << [name, value, owner.is(headers)]
        }

        assert headers.entries().collect() == [['a', '1'], ['b', '2, 3'], ['c', '4']]
        assert headers.keys().collect() == ['a', 'b', 'c']
        assert headers.values().collect() == ['1', '2, 3', '4']
        assert callbacks == [['a', '1', true], ['b', '2, 3', true], ['c', '4', true]]
        assert shouldFail(JavaScriptTypeError) { new JavaScriptHeaders('b2') }
        assert shouldFail(JavaScriptTypeError) { new JavaScriptHeaders(['He y': 'ok']) }
        assert shouldFail(JavaScriptTypeError) { new JavaScriptHeaders(['He-y': 'ăk']) }
        assert shouldFail(JavaScriptTypeError) { new JavaScriptHeaders(['He-y': "ok\u0007"]) }
        assert shouldFail(JavaScriptTypeError) { headers.get('Hé-y') }
        assert shouldFail(JavaScriptTypeError) { headers.delete('Hé-y') }
    }

    void testRequestSnapshotsUrlSearchParamsAndSupportsByteBodies() {
        def params = new JavaScriptURLSearchParams()
        def paramsRequest = new JavaScriptRequest('https://fetch.example/items', [method: 'post', body: params])
        byte[] bytes = 'a=1'.getBytes(StandardCharsets.UTF_8)
        def buffer = new JavaScriptArrayBuffer(bytes.length)
        new JavaScriptUint8Array(buffer).set(bytes)
        def typed = new JavaScriptUint8Array(bytes)
        def view = new JavaScriptDataView(buffer)

        params.append('a', '1')

        assert paramsRequest.text().await() == ''
        assert paramsRequest.headers.get('content-type') == 'application/x-www-form-urlencoded;charset=UTF-8'
        [buffer, typed, view, new JavaScriptBlob([bytes])].each { Object body ->
            assert new JavaScriptRequest('https://fetch.example/items', [method: 'post', body: body]).text().await() == 'a=1'
        }
        assert shouldFail(JavaScriptTypeError) { new JavaScriptRequest('https://john:pass@fetch.example/items') }
    }

    void testResponseDecodesBomPreservesRawBytesAndValidatesRedirects() {
        def textResponse = new JavaScriptResponse('\uFEFF{"a":1}')
        def bytesResponse = new JavaScriptResponse('\uFEFF{"a":1}')
        def jsonResponse = JavaScriptResponse.json([foo: 'bar'])
        def overridden = JavaScriptResponse.json(null, [
            status    : 301,
            statusText: 'node-fetch',
            headers   : ['Content-Type': 'text/plain']
        ])

        assert textResponse.text().await() == '{"a":1}'
        assert textResponse.json().await() == [a: 1]
        assert bytesResponse.arrayBuffer().await().byteLength == 10
        assert jsonResponse.status == 200
        assert jsonResponse.headers.get('content-type') == 'application/json'
        assert jsonResponse.text().await() == '{"foo":"bar"}'
        assert overridden.headers.get('content-type') == 'text/plain'
        assert overridden.status == 301
        assert overridden.statusText == 'node-fetch'
        assert JavaScriptResponse.redirect('https://fetch.example/next', 308).status == 308
        assert shouldFail(JavaScriptRangeError) { JavaScriptResponse.redirect('https://fetch.example/next', 200) }
    }

    void testFetchFollowsRedirectsAndPreservesSupportedRequests() {
        withServer { String baseUrl ->
            [301, 302, 303, 307, 308].each { int status ->
                def response = JavaScriptFetch.fetch("${baseUrl}/redirect/${status}").await()

                assert response.status == 200
                assert response.ok
                assert response.url == "${baseUrl}/inspect"
                assert response.json().await().method == 'GET'
            }
            def rewritten = JavaScriptFetch.fetch("${baseUrl}/redirect/301", [method: 'POST', body: 'a=1']).await().json().await()
            def preserved = JavaScriptFetch.fetch("${baseUrl}/redirect/307", [method: 'PATCH', body: 'a=1', headers: ['X-Trace': 'keep']]).await().json().await()
            def request = new JavaScriptRequest("${baseUrl}/inspect", [method: 'POST', headers: [a: '1']])
            def overridden = JavaScriptFetch.fetch(request, [method: 'GET', headers: [a: '2']]).await().json().await()

            assert rewritten.method == 'GET'
            assert rewritten.data == ''
            assert preserved.method == 'PATCH'
            assert preserved.data == 'a=1'
            assert preserved.headers.'x-trace' == 'keep'
            assert overridden.method == 'GET'
            assert overridden.headers.a == '2'
        }
    }

    private static void withServer(Closure action) {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext('/') { HttpExchange exchange -> handle(exchange) }
        server.start()
        try {
            action.call("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }

    private static void handle(HttpExchange exchange) {
        String path = exchange.requestURI.path
        if (path == '/inspect') {
            Map<String, String> headers = [:]
            exchange.requestHeaders.each { String name, List<String> values ->
                headers[name.toLowerCase(Locale.ROOT)] = values.join(', ')
            }
            sendJson(exchange, 200, [
                method : exchange.requestMethod,
                headers: headers,
                data   : new String(exchange.requestBody.bytes, StandardCharsets.UTF_8)
            ])
            return
        }
        if (path ==~ /\/redirect\/(301|302|303|307|308)/) {
            int status = path.tokenize('/').last().toInteger()
            send(exchange, status, [Location: '/inspect'])
            return
        }
        send(exchange, 404)
    }

    private static void sendJson(HttpExchange exchange, int status, Object value) {
        send(exchange, status, ['Content-Type': 'application/json'], JsonOutput.toJson(value).getBytes(StandardCharsets.UTF_8))
    }

    private static void send(HttpExchange exchange, int status, Map<String, String> headers = [:], byte[] body = new byte[0]) {
        headers.each { String name, String value -> exchange.responseHeaders.set(name, value) }
        exchange.sendResponseHeaders(status, body.length)
        exchange.responseBody.withCloseable { stream -> stream.write(body) }
    }
}
