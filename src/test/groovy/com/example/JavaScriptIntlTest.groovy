package com.example

/** Local coverage for JDK-backed Intl facades. */
class JavaScriptIntlTest extends GravyTestCase {
    void testNamespaceCanonicalizationAndSupportedValues() {
        assert JavaScriptIntl.getCanonicalLocales(['EN-us', 'en-US', 'de-de']) == ['en-US', 'de-DE']
        assert JavaScriptIntl.supportedValuesOf('currency').contains('USD')
        assert JavaScriptIntl.supportedValuesOf('timeZone').first() == 'UTC'
        assert shouldFail(JavaScriptRangeError) { JavaScriptIntl.supportedValuesOf('invalid') }
        assert shouldFail(JavaScriptTypeError) { JavaScriptIntl.call() }
        assert shouldFail(JavaScriptTypeError) { new JavaScriptIntl() }
    }

    void testCollatorNumberAndDateTimeFormatting() {
        def collator = new JavaScriptIntlCollator('en-US')
        def number = new JavaScriptIntlNumberFormat('en-US')
        def date = new JavaScriptIntlDateTimeFormat('de-DE', [timeZone: 'utc'])

        assert collator.compare('a', 'b') < 0
        assert collator.compare.call('a', 'b') < 0
        assert number.format(1234) == '1,234'
        assert number.format(JavaScriptBigInt.call('9007199254740993')) == '9,007,199,254,740,993'
        assert number.formatToParts(12).collect { it.value }.join() == number.format(12)
        assert date.resolvedOptions().timeZone == 'UTC'
        assert date.format(new JavaScriptDate(0))
        assert date.formatRange(0, 1_000).contains('–')
    }

    void testBooleanOptionsUseJavaScriptTruthiness() {
        assert new JavaScriptIntlNumberFormat('en-US', [useGrouping: 'false']).resolvedOptions().useGrouping
        assert !new JavaScriptIntlNumberFormat('en-US', [useGrouping: '']).resolvedOptions().useGrouping
        assert !new JavaScriptIntlNumberFormat('en-US', [useGrouping: 0]).resolvedOptions().useGrouping
        assert new JavaScriptIntlNumberFormat('en-US', [useGrouping: 1]).resolvedOptions().useGrouping
        assert new JavaScriptIntlDateTimeFormat('en-US', [hour12: 'false']).resolvedOptions().hourCycle == 'h12'
    }

    void testLocaleListPluralRelativeSegmentDisplayAndDuration() {
        def locale = new JavaScriptIntlLocale('en-Latn-US-u-ca-gregory-nu-latn')
        def list = new JavaScriptIntlListFormat('en-US')
        def plural = new JavaScriptIntlPluralRules('en-US')
        def relative = new JavaScriptIntlRelativeTimeFormat('en-US', [numeric: 'auto'])
        def segmenter = new JavaScriptIntlSegmenter('en-US', [granularity: 'word'])
        def names = new JavaScriptIntlDisplayNames('en-US', [type: 'language'])
        def duration = new JavaScriptIntlDurationFormat('en-US')

        assert locale.baseName == 'en-Latn-US'
        assert locale.calendar == 'gregory'
        assert locale.numberingSystem == 'latn'
        assert locale.textInfo.direction == 'ltr'
        assert list.format(['apples', 'bananas', 'pears']) == 'apples, bananas, and pears'
        assert plural.select(1) == 'one'
        assert plural.select(2) == 'other'
        assert relative.format(-1, 'day') == 'yesterday'
        assert segmenter.segment('ready, set').collect { it.segment } == ['ready', ',', ' ', 'set']
        assert segmenter.segment('ready').containing(1).segment == 'ready'
        assert names.of('de') == 'German'
        assert duration.format([hours: 1, minutes: 2, seconds: 3]) == '1 hr, 2 min, 3 sec'
        assert new JavaScriptIntlDurationFormat('en-US', [style: 'digital']).format([hours: 1, minutes: 2, seconds: 3]) == '1:02:03'
    }
}
