package com.example

/** Groovy vectors derived from Test262 ECMA-402 Intl namespace and constructors. */
class Test262DerivedIntlTest extends GravyTestCase {
    void testGetCanonicalLocalesCanonicalizesAndRemovesDuplicates() {
        // intl402/Intl/getCanonicalLocales/duplicates.js and to-string.js
        assert JavaScriptIntl.getCanonicalLocales(['EN-us', 'en-US', 'pt-br']) == ['en-US', 'pt-BR']
        assert JavaScriptIntl.getCanonicalLocales(new JavaScriptIntlLocale('de-DE')) == ['de-DE']
    }

    void testDateTimeFormatNormalizesUtcTimeZone() {
        // intl402/DateTimeFormat/timezone-utc.js
        ['UTC', 'utc'].each { String zone ->
            assert new JavaScriptIntlDateTimeFormat(['de-DE'], [timeZone: zone]).resolvedOptions().timeZone == 'UTC'
        }
    }

    void testIntlConstructorsAcceptStringAndSingleElementLocaleArray() {
        // intl402/constructors-string-and-single-element-array.js
        List constructors = [
            { Object locale -> new JavaScriptIntlCollator(locale) },
            { Object locale -> new JavaScriptIntlDateTimeFormat(locale) },
            { Object locale -> new JavaScriptIntlListFormat(locale) },
            { Object locale -> new JavaScriptIntlNumberFormat(locale) },
            { Object locale -> new JavaScriptIntlPluralRules(locale) },
            { Object locale -> new JavaScriptIntlRelativeTimeFormat(locale) },
            { Object locale -> new JavaScriptIntlSegmenter(locale) }
        ]

        constructors.each { Closure factory ->
            assert factory.call('de-DE').resolvedOptions().locale == 'de-DE'
            assert factory.call(['de-DE']).resolvedOptions().locale == 'de-DE'
        }
    }

    void testListRelativePluralAndSupportedValuesVectors() {
        // intl402/ListFormat, RelativeTimeFormat, PluralRules/selectRange, and Intl/supportedValuesOf vectors
        assert new JavaScriptIntlListFormat('en', [type: 'disjunction']).format(['a', 'b']) == 'a or b'
        assert new JavaScriptIntlRelativeTimeFormat('en', [style: 'short']).resolvedOptions().style == 'short'
        assert new JavaScriptIntlPluralRules('en').selectRange(1, 1) == 'one'
        assert new JavaScriptIntlPluralRules('en').selectRange(1, 2) == 'other'
        assert JavaScriptIntl.supportedValuesOf('numberingSystem').contains('latn')
        assert JavaScriptIntl.supportedValuesOf('currency').contains('USD')
    }
}
