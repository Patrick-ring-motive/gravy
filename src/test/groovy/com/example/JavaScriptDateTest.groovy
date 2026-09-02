package com.example

/** Local best-effort Date facade checks. */
class JavaScriptDateTest extends GravyTestCase {
    void testEpochAndIsoSerialization() {
        def date = new JavaScriptDate(0)

        assert date.time == 0d
        assert date.toISOString() == '1970-01-01T00:00:00Z'
        assert date.toJSON() == '1970-01-01T00:00:00Z'
        assert date.UTCFullYear == 1970
    }

    void testParseAndUtcConstruction() {
        assert JavaScriptDate.parse('1970-01-01T00:00:01Z') == 1000d
        assert JavaScriptDate.UTC(1970, 0, 1, 0, 0, 1) == 1000L
        assert JavaScriptDate.UTC(99, 0, 1) == JavaScriptDate.UTC(1999, 0, 1)
    }

    void testInvalidDatesExposeNaNAndRejectIso() {
        def date = new JavaScriptDate('not-a-date')

        assert Double.isNaN(date.time)
        assert date.toJSON() == null
        assert shouldFail(JavaScriptRangeError) { date.toISOString() }
    }
}
