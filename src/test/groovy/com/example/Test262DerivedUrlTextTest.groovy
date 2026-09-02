package com.example

/** Groovy vectors derived from URL and Encoding API observable requirements. */
class Test262DerivedUrlTextTest extends GravyTestCase {
    void testUrlRequiresAbsoluteInputOrBaseAndSerializesRelativeResolution() {
        assert shouldFail(JavaScriptTypeError) { new JavaScriptURL('/items') }
        assert new JavaScriptURL('/items', 'https://example.test/root/').href == 'https://example.test/items'
    }

    void testTextDecoderFatalModeRejectsMalformedUtf8() {
        def bytes = new JavaScriptUint8Array([0xC3, 0x28])

        assert new JavaScriptTextDecoder().decode(bytes).contains('\uFFFD')
        assert shouldFail(JavaScriptTypeError) { new JavaScriptTextDecoder('utf-8', [fatal: true]).decode(bytes) }
    }

    void testResponseRedirectAndErrorFactoriesExposeExpectedStatus() {
        assert JavaScriptResponse.redirect('/next').status == 302
        assert JavaScriptResponse.redirect('/next', 307).headers.get('location') == '/next'
        assert JavaScriptResponse.error().status == 0
    }
}
