package com.example

/** Groovy vectors derived from Test262 Date.parse, Date.UTC, and invalid-time behavior. */
class Test262DerivedDateTest extends GravyTestCase {
    void testDateParseAndValueOfUseEpochMilliseconds() {
        def date = new JavaScriptDate('1970-01-01T00:00:00Z')

        assert date.valueOf() == 0d
        assert JavaScriptDate.parse('1970-01-01T00:00:00Z') == 0d
    }

    void testDateUtcNormalizesMonthOffsets() {
        assert JavaScriptDate.UTC(1970, 12, 1) == JavaScriptDate.UTC(1971, 0, 1)
    }
}
