package com.example

/**
 * Current core-js deliberately omits ECMA-402 Intl polyfills. This boundary
 * vector keeps Intl independent from core-js while exercising core-js-style
 * global access and callable constructor properties.
 */
class CoreJsDerivedIntlTest extends GravyTestCase {
    void testIntlNamespaceDoesNotRequireCoreJsPolyfills() {
        assert JavaScriptIntl.NumberFormat.call('en-US').format(1_000) == '1,000'
        assert JavaScriptIntl.Collator.supportedLocalesOf(['en-US']) == ['en-US']
        assert JavaScriptIntl.ListFormat.call('en-US').format(['a', 'b']) == 'a and b'
    }
}
