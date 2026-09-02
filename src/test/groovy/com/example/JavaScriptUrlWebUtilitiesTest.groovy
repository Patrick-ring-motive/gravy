package com.example

/** Local URL, encoding, clone, and crypto facade checks. */
class JavaScriptUrlWebUtilitiesTest extends GravyTestCase {
    void testUrlAndLiveSearchParamsResolveAndSerialize() {
        def url = new JavaScriptURL('https://user:secret@example.test:8443/a/b?tag=one#part')

        assert url.origin == 'https://example.test:8443'
        assert url.protocol == 'https:'
        assert url.username == 'user'
        assert url.password == 'secret'
        assert url.pathname == '/a/b'
        assert url.searchParams.get('tag') == 'one'

        url.searchParams.append('tag', 'two words')

        assert url.search == '?tag=one&tag=two+words'
        assert url.href == 'https://user:secret@example.test:8443/a/b?tag=one&tag=two+words#part'
        assert new JavaScriptURL('../next', url).href == 'https://user:secret@example.test:8443/next'

        def location = new JavaScriptLocation('https://example.test/base/page')
        location.assign('../next')
        assert location.href == 'https://example.test/next'
    }

    void testUrlSearchParamsPreserveDuplicateKeysAndSort() {
        def params = new JavaScriptURLSearchParams('a=1&a=2&space=two+words')

        assert params.getAll('a') == ['1', '2']
        assert params.get('space') == 'two words'
        params.set('a', '3')
        params.append('z', '!')
        params.sort()

        assert params.entries().collect() == [['a', '3'], ['space', 'two words'], ['z', '!']]
        assert params.toString() == 'a=3&space=two+words&z=%21'
    }

    void testLocationImplementsMdnPropertiesMethodsAndStringifier() {
        def location = new JavaScriptLocation('https://example.test:8443/base?old=1#old')

        assert location.ancestorOrigins == []
        assert location.origin == 'https://example.test:8443'

        location.protocol = 'http:'
        location.host = 'api.example.test:8080'
        location.hostname = 'cdn.example.test'
        location.port = '9090'
        location.pathname = '/items'
        location.search = 'q=gravy'
        location.hash = 'details'

        assert location.protocol == 'http:'
        assert location.host == 'cdn.example.test:9090'
        assert location.hostname == 'cdn.example.test'
        assert location.port == '9090'
        assert location.pathname == '/items'
        assert location.search == '?q=gravy'
        assert location.hash == '#details'
        assert location.href == 'http://cdn.example.test:9090/items?q=gravy#details'
        assert location.toString() == location.href

        location.href = '/other?via=href#fragment'
        assert location.href == 'http://cdn.example.test:9090/other?via=href#fragment'
        location.assign('../next')
        assert location.pathname == '/next'
        location.replace('/replacement')
        assert location.pathname == '/replacement'
        location.reload()
        assert location.href == 'http://cdn.example.test:9090/replacement'
    }

    void testTextCodecsAndBase64Utilities() {
        def encoder = new JavaScriptTextEncoder()
        def encoded = encoder.encode('A😄')
        def target = new JavaScriptUint8Array(5)

        assert encoder.encoding == 'utf-8'
        assert encoded.values().collect() == [65, 240, 159, 152, 132]
        assert new JavaScriptTextDecoder().decode(encoded) == 'A😄'
        assert encoder.encodeInto('A😄B', target) == [read: 3, written: 5]
        assert target.values().collect() == [65, 240, 159, 152, 132]
        assert JavaScriptWebUtilities.btoa('Groovy') == 'R3Jvb3Z5'
        assert JavaScriptWebUtilities.atob('R3Jvb3Z5') == 'Groovy'
        assert shouldFail(JavaScriptTypeError) { JavaScriptWebUtilities.btoa('😄') }
    }

    void testStructuredCloneAndCryptoDigestDoNotReuseMutableValues() {
        def source = [items: [1, 2]]
        def copied = JavaScriptWebUtilities.structuredClone(source)
        def digest = JavaScriptCrypto.subtle.digest('SHA-256', new JavaScriptTextEncoder().encode('abc')).await()

        copied.items << 3

        assert source == [items: [1, 2]]
        assert copied == [items: [1, 2, 3]]
        assert new JavaScriptUint8Array(digest).values().collect().take(4) == [186, 120, 22, 191]
        assert JavaScriptCrypto.randomUUID() ==~ /[0-9a-f-]{36}/
    }
}
