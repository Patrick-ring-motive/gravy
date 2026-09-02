package com.example

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonOutput

import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Groovy vectors adapted from JakeChampion/fetch test/test.js at ba5cf1e.
 *
 * Exercises Headers, Request, Response, body extraction, and local HTTP fetch
 * behavior. Browser-only CORS, cookies, abort signals, and body locking do not
 * apply to this JVM facade.
 */
class FetchDerivedWebPlatformTest extends GravyTestCase {
    void testHeadersCopyPairsNormalizationAndIteration() {
        def original = new JavaScriptHeaders()
        original.append('Accept', 'application/json')
        original.append('Accept', 'text/plain')
        original.append('Content-Type', 'text/html')

        def headers = new JavaScriptHeaders(original)
        assert headers.get('accept') == 'application/json, text/plain'
        assert headers.get('Content-Type') == 'text/html'
        assert new JavaScriptHeaders([['X-Trace', 'one'], ['Accept', 'application/json']]).toMap() == [
            'x-trace': 'one',
            'accept': 'application/json'
        ]
        assert shouldFail(JavaScriptTypeError) { new JavaScriptHeaders([['Content-Type']]) }
        assert shouldFail(JavaScriptTypeError) { new JavaScriptHeaders([['Content-Type', 'a', 'b']]) }

        headers.set(1, 2)
        assert headers.has('1')
        assert headers.get(1) == '2'
        headers.delete('1')
        assert !headers.has(1)
        assert headers.collect() == [
            ['accept', 'application/json, text/plain'],
            ['content-type', 'text/html']
        ]
        assert shouldFail(JavaScriptTypeError) { new JavaScriptHeaders(['[Accept]': 'application/json']) }
    }

    void testRequestCopiesAndOverridesInitAndAssignsBodyContentTypes() {
        def request = new JavaScriptRequest('https://fetch.example/items', [
            method : 'post',
            body   : 'I work out',
            headers: [accept: 'application/json']
        ])
        def copy = new JavaScriptRequest(request)
        def override = new JavaScriptRequest(request, [
            body   : '{"wiggles":5}',
            headers: ['Content-Type': 'application/json']
        ])

        assert request.method == 'POST'
        assert request.url == 'https://fetch.example/items'
        assert request.headers.get('content-type') == 'text/plain;charset=UTF-8'
        assert copy.text().await() == 'I work out'
        assert override.headers.get('accept') == null
        assert override.json().await() == [wiggles: 5]
        assert new JavaScriptRequest(new JavaScriptURL('https://fetch.example/cors')).url == 'https://fetch.example/cors'
        assert new JavaScriptRequest('https://fetch.example/', [method: 'post', body: new JavaScriptBlob(['file'], [type: 'text/custom'])])
            .headers.get('content-type') == 'text/custom'
        assert new JavaScriptRequest('https://fetch.example/', [method: 'post', body: new JavaScriptURLSearchParams('a=1&b=2')])
            .headers.get('content-type') == 'application/x-www-form-urlencoded;charset=UTF-8'
        assert shouldFail(JavaScriptTypeError) { new JavaScriptRequest('https://fetch.example/', [method: 'get', body: 'invalid']) }
        assert shouldFail(JavaScriptTypeError) { new JavaScriptRequest('https://fetch.example/', [method: 'head', body: 'invalid']) }
    }

    void testResponseFactoriesHeadersBodiesAndContentTypes() {
        def suppliedHeaders = new JavaScriptHeaders(['X-Hello': 'world'])
        def response = new JavaScriptResponse('Hello World!', [headers: suppliedHeaders])
        def clone = response.clone()
        def blob = new JavaScriptBlob(['file'], [type: 'text/custom'])

        assert response.status == 200
        assert response.statusText == ''
        assert response.ok
        assert response.headers.get('content-type') == 'text/plain;charset=UTF-8'
        assert !response.headers.is(suppliedHeaders)
        assert response.text().await() == 'Hello World!'
        assert clone.text().await() == 'Hello World!'
        assert new JavaScriptResponse(blob).headers.get('content-type') == 'text/custom'
        assert new JavaScriptResponse(null).text().await() == ''
        assert new JavaScriptResponse(null).headers.get('content-type') == null
        assert JavaScriptResponse.error().type == 'error'
        assert !JavaScriptResponse.error().ok
        assert JavaScriptResponse.redirect('/next', 301).headers.get('location') == '/next'
        assert shouldFail(JavaScriptRangeError) { new JavaScriptResponse('', [status: 199]) }
        assert shouldFail(JavaScriptRangeError) { new JavaScriptResponse('', [status: 600]) }
    }

    void testBodyExtractionSupportsBlobArrayBufferTypedArrayAndDataView() {
        byte[] bytes = 'Hello World!'.getBytes(StandardCharsets.UTF_8)
        def buffer = new JavaScriptArrayBuffer(bytes.length)
        new JavaScriptUint8Array(buffer).set(bytes)
        def typed = new JavaScriptUint8Array(bytes)
        def view = new JavaScriptDataView(buffer)

        [new JavaScriptBlob([bytes]), buffer, typed, view].each { Object body ->
            def response = new JavaScriptResponse(body)
            assert response.text().await() == 'Hello World!'
            assert response.blob().await().text().await() == 'Hello World!'
            assert new JavaScriptUint8Array(response.arrayBuffer().await()).collect() == bytes.collect { Byte.toUnsignedInt(it) }
        }
    }

    void testFetchResolvesHttpErrorsAndRejectsInvalidRequests() {
        withServer { String baseUrl ->
            def failure = JavaScriptFetch.fetch("${baseUrl}/boom").await()

            assert failure.status == 500
            assert !failure.ok
            assert failure.text().await() == 'boom'
            assert shouldFail(JavaScriptTypeError) {
                JavaScriptFetch.fetch("${baseUrl}/request", [method: 'GET', body: 'invalid']).await()
            }
        }
    }

    void testFetchSendsHeadersAndSupportedBodyTypes() {
        withServer { String baseUrl ->
            def stringRequest = JavaScriptFetch.fetch("${baseUrl}/request", [
                method : 'post',
                headers: [Accept: 'application/json', 'X-Test': '42'],
                body   : 'name=Hubot'
            ]).await().json().await()
            def paramsRequest = JavaScriptFetch.fetch("${baseUrl}/request", [
                method: 'post',
                body  : new JavaScriptURLSearchParams('a=1&b=2')
            ]).await().json().await()
            byte[] typedBytes = 'typed'.getBytes(StandardCharsets.UTF_8)
            def typedRequest = JavaScriptFetch.fetch("${baseUrl}/request", [
                method: 'post',
                body  : new JavaScriptUint8Array(typedBytes)
            ]).await().json().await()
            def reusable = new JavaScriptRequest("${baseUrl}/request", [headers: ['X-Test': 'again']])

            assert stringRequest.method == 'POST'
            assert stringRequest.data == 'name=Hubot'
            assert stringRequest.headers.accept == 'application/json'
            assert stringRequest.headers.'x-test' == '42'
            assert stringRequest.headers.'content-type' == 'text/plain;charset=UTF-8'
            assert paramsRequest.data == 'a=1&b=2'
            assert paramsRequest.headers.'content-type' == 'application/x-www-form-urlencoded;charset=UTF-8'
            assert typedRequest.data == 'typed'
            assert JavaScriptFetch.fetch(reusable).await().json().await().headers.'x-test' == 'again'
            assert JavaScriptFetch.fetch(reusable).await().json().await().headers.'x-test' == 'again'
        }
    }

    void testFetchReadsResponseHeadersAndFollowsRedirects() {
        withServer { String baseUrl ->
            def headers = JavaScriptFetch.fetch("${baseUrl}/headers").await()
            def redirect = JavaScriptFetch.fetch("${baseUrl}/redirect/302").await()

            assert headers.headers.get('content-type') == 'text/html; charset=utf-8'
            assert redirect.status == 200
            assert redirect.ok
            assert redirect.url == "${baseUrl}/hello"
            assert redirect.text().await() == 'hi'
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
        switch (exchange.requestURI.path) {
            case '/request':
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
            case '/boom':
                send(exchange, 500, ['Content-Type': 'text/plain'], 'boom'.getBytes(StandardCharsets.UTF_8))
                return
            case '/headers':
                send(exchange, 200, ['Content-Type': 'text/html; charset=utf-8'])
                return
            case '/hello':
                send(exchange, 200, ['Content-Type': 'text/plain'], 'hi'.getBytes(StandardCharsets.UTF_8))
                return
            case '/redirect/302':
                send(exchange, 302, [Location: '/hello'])
                return
            default:
                send(exchange, 404)
        }
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
